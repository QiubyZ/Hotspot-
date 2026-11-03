# Hotspot Manager

Aplikasi Android (Java 17, Gradle Groovy) untuk memantau & mengatur user hotspot. **Butuh root.**

- Daftar user: `ip neigh show`
- Kill / Unblock: iptables chain `HS_BLOCK`
- Limit kecepatan per user: `tc` htb + filter u32 (arah download klien)
- Limit semua: `tc qdisc add dev ap0 root tbf rate 1mbit burst 32kbit latency 400ms`
- Kuota global interface: `ndc bandwidth setiquota wlan1 524288000` / `removeiquota wlan1`
- Kuota per user (total/terpakai/sisa/reset): hitung byte via iptables chain `HS_ACCT`

## Build
    gradle wrapper --gradle-version 8.10.2 --distribution-type all   # sekali, untuk membuat gradle-wrapper.jar
    ./gradlew assembleDebug

APK: `app/build/outputs/apk/debug/app-debug.apk`

## GitHub
Push ke `main` -> Actions build debug + release (debug key) dan upload sebagai artifact.
Push tag `v1.0` -> otomatis dibuat GitHub Release berisi APK.
