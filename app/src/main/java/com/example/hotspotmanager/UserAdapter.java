package com.example.hotspotmanager;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class UserAdapter extends RecyclerView.Adapter<UserAdapter.VH> {

    public interface Listener {
        void onKill(HotspotUser u);
        void onSpeed(HotspotUser u);
        void onQuota(HotspotUser u);
        void onReset(HotspotUser u);
    }

    private final List<HotspotUser> items = new ArrayList<>();
    private final Listener listener;

    public UserAdapter(Listener l) {
        this.listener = l;
    }

    public void submit(List<HotspotUser> list) {
        items.clear();
        items.addAll(list);
        notifyDataSetChanged();
    }

    public static String fmt(long b) {
        if (b < 1024) return b + " B";
        double kb = b / 1024.0;
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb);
        return String.format(Locale.US, "%.2f GB", mb / 1024.0);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        HotspotUser u = items.get(position);
        h.ip.setText(u.ip);
        h.mac.setText(u.mac + "  (" + u.dev + ")");
        h.state.setText(u.blocked ? "DIBLOKIR" : "Aktif (" + u.state + ")");
        h.state.setTextColor(u.blocked ? Color.parseColor("#C62828") : Color.parseColor("#2E7D32"));
        h.speed.setText("Kecepatan: " + (u.speedKbps > 0 ? u.speedKbps + " kbit/s" : "tanpa limit"));
        if (u.quotaBytes > 0) {
            h.quota.setText("Kuota: " + fmt(u.usedBytes) + " terpakai / " + fmt(u.quotaBytes)
                    + " total, sisa " + fmt(u.remainingBytes()));
        } else {
            h.quota.setText("Kuota: tanpa batas, terpakai " + fmt(u.usedBytes));
        }
        h.kill.setText(u.blocked ? "Unblock" : "Kill");
        h.kill.setOnClickListener(v -> listener.onKill(u));
        h.speedBtn.setOnClickListener(v -> listener.onSpeed(u));
        h.quotaBtn.setOnClickListener(v -> listener.onQuota(u));
        h.reset.setOnClickListener(v -> listener.onReset(u));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView ip, mac, state, speed, quota;
        final Button kill, speedBtn, quotaBtn, reset;

        VH(@NonNull View v) {
            super(v);
            ip = v.findViewById(R.id.tvIp);
            mac = v.findViewById(R.id.tvMac);
            state = v.findViewById(R.id.tvState);
            speed = v.findViewById(R.id.tvSpeed);
            quota = v.findViewById(R.id.tvQuota);
            kill = v.findViewById(R.id.btnKill);
            speedBtn = v.findViewById(R.id.btnSpeed);
            quotaBtn = v.findViewById(R.id.btnQuota);
            reset = v.findViewById(R.id.btnReset);
        }
    }
}
