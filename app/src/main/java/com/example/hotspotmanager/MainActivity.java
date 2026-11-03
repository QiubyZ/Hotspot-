package com.example.hotspotmanager;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements UserAdapter.Listener {

    private interface LongCb { void on(long v); }

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            refresh();
            ui.postDelayed(this, 5000);
        }
    };

    private Store store;
    private HotspotManager mgr;
    private UserAdapter adapter;
    private EditText etNdc, etTc, etGlobalMb, etTbf;
    private TextView tvStatus, tvCount;
    private volatile boolean busy = false;
    private Boolean rootOk = null;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);

        store = new Store(this);
        mgr = new HotspotManager(store);

        etNdc = findViewById(R.id.etNdc);
        etTc = findViewById(R.id.etTc);
        etGlobalMb = findViewById(R.id.etGlobalMb);
        etTbf = findViewById(R.id.etTbf);
        tvStatus = findViewById(R.id.tvStatus);
        tvCount = findViewById(R.id.tvCount);

        etNdc.setText(store.getString("ndc", "wlan1"));
        etTc.setText(store.getString("tc", "ap0"));

        adapter = new UserAdapter(this);
        RecyclerView rv = findViewById(R.id.rvUsers);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        findViewById(R.id.btnRefresh).setOnClickListener(v -> refresh());

        findViewById(R.id.btnSetQuota).setOnClickListener(v -> {
            long mb = parseLong(etGlobalMb.getText().toString(), 500);
            String ifc = ndcIf();
            bg(() -> mgr.setGlobalQuota(ifc, mb * 1024 * 1024), "Kuota global " + mb + " MB di " + ifc);
        });
        findViewById(R.id.btnRemoveQuota).setOnClickListener(v -> {
            String ifc = ndcIf();
            bg(() -> mgr.removeGlobalQuota(ifc), "Kuota global dihapus di " + ifc);
        });
        findViewById(R.id.btnTbf).setOnClickListener(v -> {
            int mbit = (int) parseLong(etTbf.getText().toString(), 1);
            String ifc = tcIf();
            bg(() -> mgr.limitAll(ifc, mbit), "Limit semua " + mbit + " Mbit di " + ifc);
        });
        findViewById(R.id.btnClearTc).setOnClickListener(v -> {
            String ifc = tcIf();
            bg(() -> mgr.clearTc(ifc), "TC di " + ifc + " direset");
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        ui.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(tick);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    // ------------------------------------------------------------ helpers

    private String ndcIf() { return etNdc.getText().toString().trim(); }
    private String tcIf() { return etTc.getText().toString().trim(); }

    private long parseLong(String s, long def) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return def; }
    }

    /** Jalankan aksi di background, tampilkan hasil, lalu refresh. */
    private void bg(Callable<String> job, String okMsg) {
        io.execute(() -> {
            String out;
            try { out = job.call(); } catch (Exception e) { out = "ERR: " + e.getMessage(); }
            String msg = (out != null && !out.trim().isEmpty()) ? okMsg + "\n" + out.trim() : okMsg;
            ui.post(() -> {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                refresh();
            });
        });
    }

    private void refresh() {
        if (busy) return;
        busy = true;
        final String ndc = ndcIf(), tc = tcIf();
        store.putString("ndc", ndc);
        store.putString("tc", tc);
        io.execute(() -> {
            try {
                if (rootOk == null || !rootOk) rootOk = Shell.hasRoot();
                if (!rootOk) {
                    ui.post(() -> tvStatus.setText("Akses root tidak tersedia. Aplikasi ini butuh root (su)."));
                    return;
                }
                List<HotspotUser> users = mgr.refresh(ndc, tc);
                ui.post(() -> {
                    adapter.submit(users);
                    tvCount.setText("User terkoneksi: " + users.size());
                    tvStatus.setText("Root OK | ndc: " + ndc + " | tc: " + tc + " | auto-refresh 5 detik");
                });
            } finally {
                busy = false;
            }
        });
    }

    private void ask(String title, String hint, String def, LongCb cb) {
        EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setHint(hint);
        et.setText(def);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(et)
                .setPositiveButton("OK", (d, w) -> cb.on(parseLong(et.getText().toString(), 0)))
                .setNegativeButton("Batal", null)
                .show();
    }

    // ------------------------------------------------------------ aksi per user

    @Override
    public void onKill(HotspotUser u) {
        if (u.blocked) {
            bg(() -> { mgr.unblock(u); return ""; }, u.ip + " di-unblock");
        } else {
            bg(() -> { mgr.kill(u); return ""; }, u.ip + " di-kill");
        }
    }

    @Override
    public void onSpeed(HotspotUser u) {
        String tc = tcIf();
        ask("Limit kecepatan " + u.ip, "kbit/s (0 = tanpa limit)",
                String.valueOf(u.speedKbps > 0 ? u.speedKbps : 1024), kbps ->
                        bg(() -> mgr.setUserSpeed(u, tc, (int) kbps),
                                kbps > 0 ? u.ip + " dibatasi " + kbps + " kbit/s" : u.ip + " limit dihapus"));
    }

    @Override
    public void onQuota(HotspotUser u) {
        ask("Total kuota " + u.ip, "MB (0 = tanpa batas)",
                String.valueOf(u.quotaBytes > 0 ? u.quotaBytes / 1024 / 1024 : 500), mb ->
                        bg(() -> { mgr.setUserQuota(u, mb * 1024 * 1024); return ""; },
                                mb > 0 ? "Kuota " + u.ip + " = " + mb + " MB" : "Kuota " + u.ip + " dihapus"));
    }

    @Override
    public void onReset(HotspotUser u) {
        new AlertDialog.Builder(this)
                .setTitle("Reset kuota " + u.ip + "?")
                .setMessage("Pemakaian dikembalikan ke 0 dan blokir (jika ada) dibuka.")
                .setPositiveButton("Reset", (d, w) ->
                        bg(() -> { mgr.resetUserQuota(u); return ""; }, "Kuota " + u.ip + " direset"))
                .setNegativeButton("Batal", null)
                .show();
    }
}
