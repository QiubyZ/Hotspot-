package com.example.hotspotmanager;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/** Menjalankan perintah sebagai root lewat su. */
public final class Shell {
    private Shell() {}

    public static String su(String cmd) {
        Process p = null;
        try {
            p = new ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            p.waitFor();
            return sb.toString();
        } catch (Exception e) {
            return "ERR: " + e.getMessage();
        } finally {
            if (p != null) p.destroy();
        }
    }

    public static boolean hasRoot() {
        return su("id").contains("uid=0");
    }
}
