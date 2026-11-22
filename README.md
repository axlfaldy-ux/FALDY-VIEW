# FALDY VIEW

Fondasi aplikasi Android native untuk melihat layar perangkat lain dengan persetujuan pemiliknya. Tahap ini menyediakan prototipe alur pairing lokal; belum ada koneksi antarperangkat, berbagi layar, atau kontrol jarak jauh.

## Teknologi

- Kotlin dan Jetpack Compose
- Minimum Android SDK 26
- Target Android 14 (API 34), compile SDK 35
- Android Gradle Plugin 8.6.1, Gradle 8.7, Kotlin 2.0.20

## Menjalankan

1. Buka repository ini di Android Studio dengan Android SDK 35 terpasang.
2. Sinkronkan Gradle.
3. Jalankan konfigurasi `app` pada emulator atau perangkat Android.
4. Untuk APK debug, jalankan `gradle :app:assembleDebug` (atau gunakan wrapper Gradle jika tersedia).

Workflow GitHub Actions membangun APK debug pada push ke `main`, pull request, dan pemicu manual. APK diunggah sebagai artifact workflow.

## Tahap 2: prototipe pairing

Controller dapat membuat kode enam digit menggunakan `SecureRandom`; Receiver dapat memasukkan kode, mengirim permintaan, lalu menunggu persetujuan eksplisit. UI menampilkan status menunggu, permintaan persetujuan, status lokal tersambung, serta aksi batalkan dan putuskan.

Alur ini hanya memakai state dalam memori pada satu instalasi aplikasi. Kode bukan kredensial autentikasi dan status “terhubung” hanya menandai persetujuan Controller dalam prototipe, bukan koneksi ke perangkat lain. Tindakan Receiver memasukkan dan mengirim kode memulai permintaan pairing; ini belum memberi izin MediaProjection atau memulai berbagi layar. Kode tidak memiliki masa berlaku atau pembatasan percobaan pada prototipe ini. Jangan gunakan untuk sesi nyata.

### Layanan untuk pairing lintas perangkat

Implementasi nyata memerlukan backend dan transport berikut sebelum pairing dapat menghubungkan dua perangkat:

- API HTTPS untuk meminta kode pairing, menukar kode dengan permintaan sesi, dan membatalkan sesi.
- Penyimpanan sesi sementara dengan kode sekali pakai yang kedaluwarsa, penyimpanan kode secara aman, serta pembatasan percobaan dan laju permintaan.
- Kanal realtime terautentikasi (misalnya WebSocket atau layanan push) agar permintaan Receiver, persetujuan Controller, dan pemutusan tersinkron antarperangkat.
- Autentikasi perangkat/pengguna dan token sesi berumur pendek dengan cakupan minimum; kode enam digit saja tidak cukup sebagai autentikasi.
- Proses persetujuan yang jelas di Receiver sebelum berbagi layar dimulai, indikator sesi aktif, dan cara berhenti yang selalu tersedia.
- Saat tahap WebRTC dimulai: signaling server, STUN, dan TURN. Secret TURN harus diterbitkan oleh backend secara terbatas dan berumur pendek.

Belum ada endpoint atau penyedia backend yang dipilih maupun dikonfigurasi. Keputusan pemilihan layanan, kebijakan retensi, rate limit, dan model autentikasi diperlukan sebelum mengaktifkan koneksi lintas perangkat.

## Status keamanan dan cakupan

Belum ada izin MediaProjection atau Accessibility Service, pairing jaringan, WebRTC, maupun mekanisme kendali. Fitur berbagi layar nantinya harus memerlukan persetujuan eksplisit, menampilkan indikator sesi aktif, dan menyediakan tombol berhenti. Tidak ada akses layar tersembunyi.

## Langkah berikutnya

Uji sinkronisasi Gradle dan build debug dengan Android SDK yang tersedia, lalu rancang alur izin Receiver, pairing, dan persetujuan sebelum menambahkan transport WebRTC atau kontrol.
