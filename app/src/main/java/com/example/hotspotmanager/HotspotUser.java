package com.example.hotspotmanager;

public class HotspotUser {
    public String ip;
    public String mac;
    public String dev;
    public String state;
    public boolean blocked;
    public int speedKbps;      // 0 = tanpa limit
    public long quotaBytes;    // 0 = tanpa batas
    public long totalBytes;    // counter mentah iptables
    public long usedBytes;     // total - baseline (sejak reset)

    public long remainingBytes() {
        return quotaBytes > 0 ? Math.max(0, quotaBytes - usedBytes) : -1;
    }
}
