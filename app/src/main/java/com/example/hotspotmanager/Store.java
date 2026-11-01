package com.example.hotspotmanager;

import android.content.Context;
import android.content.SharedPreferences;

/** Penyimpanan pengaturan per user (kunci = MAC). */
public class Store {
    private final SharedPreferences sp;

    public Store(Context c) {
        sp = c.getSharedPreferences("hs", Context.MODE_PRIVATE);
    }

    public long getQuota(String mac) { return sp.getLong("q_" + mac, 0); }
    public void setQuota(String mac, long v) { sp.edit().putLong("q_" + mac, v).apply(); }

    public long getBase(String mac) { return sp.getLong("b_" + mac, 0); }
    public void setBase(String mac, long v) { sp.edit().putLong("b_" + mac, v).apply(); }

    public int getSpeed(String mac) { return sp.getInt("s_" + mac, 0); }
    public void setSpeed(String mac, int v) { sp.edit().putInt("s_" + mac, v).apply(); }

    public boolean isBlocked(String mac) { return sp.getBoolean("k_" + mac, false); }
    public void setBlocked(String mac, boolean v) { sp.edit().putBoolean("k_" + mac, v).apply(); }

    public String getString(String k, String d) { return sp.getString(k, d); }
    public void putString(String k, String v) { sp.edit().putString(k, v).apply(); }
}
