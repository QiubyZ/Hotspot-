package com.example.hotspotmanager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HotspotManager {
    private final Store store;

    public HotspotManager(Store store) {
        this.store = store;
    }

    // ---------------------------------------------------------------- daftar user

    public List<HotspotUser> refresh(String ndcIf, String tcIf) {
        List<HotspotUser> users = new ArrayList<>();
        Set<String> seenIp = new HashSet<>();

        String neigh = Shell.su("ip neigh show");
        for (String line : neigh.split("\n")) {
            String[] t = line.trim().split("\\s+");
            if (t.length < 5 || !t[0].contains(".")) continue;
            String dev = null, mac = null;
            for (int i = 0; i < t.length - 1; i++) {
                if (t[i].equals("dev")) dev = t[i + 1];
                if (t[i].equals("lladdr")) mac = t[i + 1];
            }
            String state = t[t.length - 1];
            if (dev == null || mac == null) continue;
            if (state.equals("FAILED") || state.equals("INCOMPLETE")) continue;
            if (!dev.equals(ndcIf) && !dev.equals(tcIf)) continue;
            if (!seenIp.add(t[0])) continue;

            HotspotUser u = new HotspotUser();
            u.ip = t[0];
            u.mac = mac.toLowerCase();
            u.dev = dev;
            u.state = state;
            u.blocked = store.isBlocked(u.mac);
            u.speedKbps = store.getSpeed(u.mac);
            u.quotaBytes = store.getQuota(u.mac);
            users.add(u);
        }

        // iptables: chain accounting + blokir (idempotent)
        StringBuilder sb = new StringBuilder();
        sb.append("iptables -w -N HS_ACCT 2>/dev/null\n");
        sb.append("iptables -w -C FORWARD -j HS_ACCT 2>/dev/null || iptables -w -I FORWARD 1 -j HS_ACCT\n");
        sb.append("iptables -w -N HS_BLOCK 2>/dev/null\n");
        sb.append("iptables -w -C FORWARD -j HS_BLOCK 2>/dev/null || iptables -w -I FORWARD 1 -j HS_BLOCK\n");
        for (HotspotUser u : users) {
            sb.append(ensureRule("HS_ACCT", "-s " + u.ip, ""));
            sb.append(ensureRule("HS_ACCT", "-d " + u.ip, ""));
            if (u.blocked) sb.append(blockCmd(u.ip));
        }
        sb.append("iptables -w -L HS_ACCT -n -v -x\n");
        String out = Shell.su(sb.toString());

        for (String line : out.split("\n")) {
            String[] t = line.trim().split("\\s+");
            if (t.length < 8) continue;
            long bytes;
            try {
                Long.parseLong(t[0]);
                bytes = Long.parseLong(t[1]);
            } catch (NumberFormatException e) {
                continue;
            }
            String src = t[t.length - 2];
            String dst = t[t.length - 1];
            for (HotspotUser u : users) {
                if (src.equals(u.ip) || dst.equals(u.ip)) u.totalBytes += bytes;
            }
        }

        for (HotspotUser u : users) {
            long base = store.getBase(u.mac);
            if (u.totalBytes < base) { // counter ter-reset (hotspot/iptables restart)
                base = 0;
                store.setBase(u.mac, 0);
            }
            u.usedBytes = u.totalBytes - base;

            // enforce kuota
            if (u.quotaBytes > 0 && u.usedBytes >= u.quotaBytes && !u.blocked) {
                kill(u);
            }
        }

        // pulihkan limit kecepatan bila class tc hilang (hotspot di-restart)
        String classes = Shell.su("tc class show dev " + tcIf);
        for (HotspotUser u : users) {
            if (u.speedKbps > 0 && !classes.contains("htb 1:" + minor(u.ip) + " ")) {
                applySpeed(tcIf, u.ip, u.speedKbps);
            }
        }
        return users;
    }

    // ---------------------------------------------------------------- kill / unblock

    public void kill(HotspotUser u) {
        store.setBlocked(u.mac, true);
        u.blocked = true;
        Shell.su(blockCmd(u.ip) + "ip neigh del " + u.ip + " dev " + u.dev + " 2>/dev/null\n");
    }

    public void unblock(HotspotUser u) {
        store.setBlocked(u.mac, false);
        u.blocked = false;
        Shell.su("iptables -w -D HS_BLOCK -s " + u.ip + " -j DROP 2>/dev/null\n"
                + "iptables -w -D HS_BLOCK -d " + u.ip + " -j DROP 2>/dev/null\n");
    }

    private String blockCmd(String ip) {
        return ensureRule("HS_BLOCK", "-s " + ip, "-j DROP")
                + ensureRule("HS_BLOCK", "-d " + ip, "-j DROP");
    }

    private String ensureRule(String chain, String match, String target) {
        return "iptables -w -C " + chain + " " + match + " " + target + " 2>/dev/null || "
                + "iptables -w -A " + chain + " " + match + " " + target + "\n";
    }

    // ---------------------------------------------------------------- kuota per user

    public void setUserQuota(HotspotUser u, long bytes) {
        store.setQuota(u.mac, bytes);
        u.quotaBytes = bytes;
        if (u.blocked && (bytes == 0 || u.usedBytes < bytes)) unblock(u);
    }

    /** Reset pemakaian ke 0 dan buka blokir. */
    public void resetUserQuota(HotspotUser u) {
        store.setBase(u.mac, u.totalBytes);
        if (u.blocked) unblock(u);
    }

    // ---------------------------------------------------------------- kecepatan (tc htb per user)

    private String minor(String ip) {
        String last = ip.substring(ip.lastIndexOf('.') + 1);
        return String.valueOf(100 + Integer.parseInt(last));
    }

    public String setUserSpeed(HotspotUser u, String tcIf, int kbps) {
        store.setSpeed(u.mac, kbps);
        u.speedKbps = kbps;
        if (kbps <= 0) return removeSpeed(tcIf, u.ip);
        return applySpeed(tcIf, u.ip, kbps);
    }

    private String applySpeed(String dev, String ip, int kbps) {
        String m = minor(ip);
        String cmd = "tc qdisc show dev " + dev + " | grep -q htb || "
                + "tc qdisc replace dev " + dev + " root handle 1: htb default 9999\n"
                + "tc class replace dev " + dev + " parent 1: classid 1:" + m
                + " htb rate " + kbps + "kbit ceil " + kbps + "kbit\n"
                + "tc filter del dev " + dev + " parent 1: prio " + m + " 2>/dev/null\n"
                + "tc filter add dev " + dev + " protocol ip parent 1: prio " + m
                + " u32 match ip dst " + ip + "/32 flowid 1:" + m + "\n";
        return Shell.su(cmd);
    }

    private String removeSpeed(String dev, String ip) {
        String m = minor(ip);
        return Shell.su("tc filter del dev " + dev + " parent 1: prio " + m + " 2>/dev/null\n"
                + "tc class del dev " + dev + " classid 1:" + m + " 2>/dev/null\n");
    }

    /** Limit semua klien sekaligus (tbf). Menggantikan qdisc root (hapus limit per-user htb). */
    public String limitAll(String dev, int mbit) {
        return Shell.su("tc qdisc del dev " + dev + " root 2>/dev/null\n"
                + "tc qdisc add dev " + dev + " root tbf rate " + mbit
                + "mbit burst 32kbit latency 400ms\n");
    }

    public String clearTc(String dev) {
        return Shell.su("tc qdisc del dev " + dev + " root 2>&1\n");
    }

    // ---------------------------------------------------------------- kuota global (ndc)

    public String setGlobalQuota(String iface, long bytes) {
        return Shell.su("ndc bandwidth setiquota " + iface + " " + bytes);
    }

    public String removeGlobalQuota(String iface) {
        return Shell.su("ndc bandwidth removeiquota " + iface);
    }
}
