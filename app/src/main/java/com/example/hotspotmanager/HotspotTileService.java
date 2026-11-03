package com.example.hotspotmanager;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.annotation.RequiresApi;

import java.lang.reflect.Method;

@RequiresApi(api = Build.VERSION_CODES.N)
public class HotspotTileService extends TileService {

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTileState();
    }

    @Override
    public void onClick() {
        super.onClick();
        boolean isEnabled = isHotspotEnabled(this);
        setHotspotEnabled(this, !isEnabled);

        // Berikan penundaan singkat agar status sistem sempat berubah
        mainHandler.postDelayed(this::updateTileState, 600);
    }

    private void updateTileState() {
        Tile tile = getQsTile();
        if (tile == null) return;
        boolean isEnabled = isHotspotEnabled(this);
        tile.setState(isEnabled ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        tile.setLabel(isEnabled ? "Hotspot ON" : "Hotspot OFF");
        tile.updateTile();
    }

    public static boolean isHotspotEnabled(Context context) {
        try {
            WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                Method method = wm.getClass().getDeclaredMethod("isWifiApEnabled");
                method.setAccessible(true);
                Boolean result = (Boolean) method.invoke(wm);
                if (result != null) return result;
            }
        } catch (Exception ignored) {}

        try {
            WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                Method method = wm.getClass().getDeclaredMethod("getWifiApState");
                method.setAccessible(true);
                Integer state = (Integer) method.invoke(wm);
                if (state != null) {
                    return state == 12 || state == 13; // 12: ENABLING, 13: ENABLED
                }
            }
        } catch (Exception ignored) {}

        return false;
    }

    @SuppressWarnings({"deprecation", "WrongConstant"})
    public static void setHotspotEnabled(Context context, boolean enable) {
        // 1. Refleksi TetheringManager pada Android 11+ (API 30+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Object tm = context.getSystemService("tethering");
                if (tm != null) {
                    if (!enable) {
                        Method stopMethod = tm.getClass().getDeclaredMethod("stopTethering", int.class);
                        stopMethod.setAccessible(true);
                        stopMethod.invoke(tm, 0); // 0 = TETHERING_WIFI
                        return;
                    }
                }
            } catch (Throwable ignored) {}
        }

        // 2. Refleksi ConnectivityManager pada Android 8.0 - 10 (API 26 - 29)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                if (cm != null) {
                    if (!enable) {
                        Method stopMethod = cm.getClass().getDeclaredMethod("stopTethering", int.class);
                        stopMethod.setAccessible(true);
                        stopMethod.invoke(cm, 0); // 0 = TETHERING_WIFI
                        return;
                    }
                }
            } catch (Throwable ignored) {}
        }

        // 3. Refleksi WifiManager pada Android 5.0 - 7.1 (API 21 - 25)
        try {
            WifiManager wm = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                Method method = wm.getClass().getMethod("setWifiApEnabled", android.net.wifi.WifiConfiguration.class, boolean.class);
                method.setAccessible(true);
                method.invoke(wm, null, enable);
                return;
            }
        } catch (Throwable ignored) {}

        // Fallback: Jika permission API dibatasi oleh sistem Android, jalankan perintah shell root
        try {
            if (enable) {
                Shell.su("cmd connectivity tether start wifi || service call connectivity 24 i32 0 || svc wifi enable");
            } else {
                Shell.su("cmd connectivity tether stop wifi || service call connectivity 25 i32 0 || svc wifi disable");
            }
        } catch (Throwable ignored) {}
    }
}
