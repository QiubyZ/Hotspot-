# 📱 Hotspot Manager

Aplikasi Android (Java 17, Material 3) modern untuk memantau, mengontrol, dan membatasi penggunaan jaringan pengguna hotspot Wi-Fi (Tethering) secara *real-time*.

> ⚠️ **Persyaratan Utama**: Aplikasi ini **membutuhkan akses Root (`su`)** karena memanfaatkan perintah tingkat rendah Linux (`iptables`, `tc`, `ip neigh`, dan `ndc`).

---

## 🎨 Tampilan & Tema

Aplikasi ini menggunakan tema **Monokai Modern** (Dark Mode khas Monokai) dengan kartu kontras tinggi, penanda warna indikator status, dan layout yang responsif untuk kenyamanan pemantauan live.

---

## ✨ Fitur Utama

- 🔘 **Quick Settings Tile (Panel Setelan Cepat)**:
  - **Tekan Biasa (Tap)**: Menyalakan atau mematikan Hotspot secara langsung dari panel Quick Settings menggunakan Android API yang disesuaikan untuk `minSdk 21` hingga `targetSdk 35`.
  - **Tekan Lama (Long Press)**: Membuka aplikasi **Hotspot Manager** secara langsung.

- 🔍 **Monitoring Real-Time (Auto-Refresh 5 Detik)**:
  - Deteksi otomatis perangkat yang terhubung melalui `ip neigh show`.
  - Menampilkan Alamat IP, Alamat MAC, nama Interface (`wlan1`, `ap0`), serta status koneksi.

- 🛑 **Kontrol Akses Per-User (Kill / Unblock)**:
  - Blokir akses internet perangkat tertentu secara instan via `iptables` chain (`HS_BLOCK`) dan penghapusan tabel ARP (`ip neigh del`).
  - Membuka kembali blokir (Unblock) hanya dengan satu klik.

- ⚡ **Pembatasan Kecepatan Per-User (Limit Speed)**:
  - Atur batas kecepatan download/upload (kbit/s) untuk masing-masing user menggunakan Linux Traffic Control (`tc` HTB & filter `u32`).

- 📊 **Manajemen Kuota Per-User**:
  - Pelacakan akumulasi penggunaan data (Bytes) per-user via `iptables` accounting chain (`HS_ACCT`).
  - Batasi kuota (MB) per user. Pengguna akan ter-blokir otomatis saat kuota habis.
  - Fitur **Reset Kuota** untuk mengembalikan hitungan data ke 0 dan mengaktifkan kembali perangkat.

- 🌐 **Kontrol Jaringan Global**:
  - **Global Interface Quota (`ndc`)**: Tetapkan batas kuota total pada interface hotspot (`ndc bandwidth setiquota / removeiquota`).
  - **Limit Semua User (`tc` TBF)**: Batasi kecepatan seluruh user hotspot sekaligus (Mbit) menggunakan Token Bucket Filter (`tbf`) atau reset konfigurasi `tc`.

---

## 🛠️ Persyaratan Sistem

- **OS Android**: Android 5.0 (API 21) atau yang lebih baru (Fitur Quick Settings Tile aktif pada Android 7.0+ API 24+).
- **Akses Root**: Superuser (`su` / Magisk / KernelSU / APatch) wajib diizinkan untuk aplikasi.
- **Interface Tethering**: Dukungan interface tethering standar Android (contoh: `wlan1`, `ap0`).

---

## 🏗️ Cara Build & Install

### Menggunakan Gradle Wrapper
```bash
# Clone repository
git clone https://github.com/QiubyZ/Hotspot-.git
cd Hotspot-

# Build Debug & Release APK
./gradlew assembleDebug assembleRelease
```
Hasil file APK berada di:
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release.apk`

---

## 🚀 GitHub Actions & Rilis Otomatis

Project ini telah dikonfigurasi dengan **GitHub Actions Workflow**:
- **Push ke `main`**: Otomatis menjalankan build untuk APK Debug & Release dan mengunggahnya sebagai *Build Artifacts*.
- **Push Tag Versi (`v1.0`, `v1.0.0-pre1`, dst.)**: Otomatis mempublikasikan rilis/pra-rilis baru di halaman **GitHub Releases** beserta file APK bertanda tangan resmi **QiubyZhukhi**.

```bash
# Contoh membuat tag pra-rilis baru
git tag v1.0.0-pre1
git push origin v1.0.0-pre1
```

---

## 📄 Lisensi

Project ini dikembangkan untuk penggunaan pribadi dan manajemen hotspot lokal di Android.
