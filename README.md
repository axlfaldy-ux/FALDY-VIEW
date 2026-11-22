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

URL backend diatur melalui property Gradle `faldyApiBaseUrl`, misalnya `gradle -PfaldyApiBaseUrl=https://api.example.test :app:assembleDebug` atau property pada `~/.gradle/gradle.properties`. Jangan commit URL privat atau kredensial. Client menolak HTTP untuk server publik; HTTP hanya diizinkan untuk loopback. Android Network Security Config juga menolak cleartext secara default.

Workflow GitHub Actions membangun APK debug dan menjalankan unit test pada push ke `main`, pull request, dan pemicu manual. APK diunggah sebagai artifact workflow. Job terpisah menyalakan emulator Android 14 (API 34) dan menjalankan `:app:connectedDebugAndroidTest`; hasil emulator baru dapat diklaim setelah job tersebut selesai.

## Integrasi pairing Android

Controller membuat kode lewat API, Receiver mengirim kode, lalu kedua sisi memantau status persetujuan. UI menyediakan aksi setujui/tolak, batal, dan putus. Client memakai timeout koneksi/baca delapan detik, menampilkan error, dan melakukan retry/backoff hanya untuk polling GET status. Mutasi POST tidak diulang otomatis setelah timeout.

Token bearer disimpan di memori `PairingViewModel` selama proses aplikasi hidup. Jika proses mati, token hilang dan sesi server akan kedaluwarsa setelah lima menit; pemulihan sesi lintas process death belum tersedia. Kode pairing bukan autentikasi identitas. Persetujuan pairing belum memberi izin MediaProjection atau memulai berbagi layar. Belum ada uji dua perangkat.

## MediaProjection Receiver

Setelah Receiver menyetujui dialog pairing, tombol berbagi layar meminta persetujuan sistem Android MediaProjection secara terpisah. Capture berjalan sebagai foreground service bertipe `mediaProjection`, memiliki notifikasi dengan aksi berhenti, indikator di UI, penanganan resize/orientasi, dan dihentikan ketika sesi pairing berakhir. Frame dibaca lalu langsung dibuang secara lokal; tahap ini belum mengirim video ke Controller. Instrumented tests mencakup persetujuan/penolakan sistem, status capture aktif, resize setelah rotasi, aksi Stop, dan callback stop sistem saat layar dikunci/dimatikan.

### Layanan untuk pairing lintas perangkat

Fondasi API pairing self-hosted tersedia di `backend/`, dan client Android kini dapat memakai endpoint yang dikonfigurasi. Belum ada host publik yang dipilih atau dideploy, sehingga pairing antarperangkat belum diuji. Implementasi deployment nyata memerlukan komponen berikut:

- API HTTPS untuk meminta kode pairing, menukar kode dengan permintaan sesi, polling status, persetujuan/penolakan, dan pembatalan sesi. Route dan format request ada di `backend/README.md`.
- Penyimpanan sesi bersama sementara (misalnya Redis) jika service dijalankan lebih dari satu instance; kode sekali pakai yang kedaluwarsa, penyimpanan kode sebagai HMAC, serta pembatasan percobaan dan laju permintaan.
- Kanal realtime terautentikasi (misalnya WebSocket atau layanan push) agar permintaan Receiver, persetujuan Controller, dan pemutusan tersinkron antarperangkat.
- Identitas/perangkat terautentikasi dan token sesi berumur pendek dengan cakupan minimum; API sekarang memakai token kapabilitas acak per-sesi, tetapi belum memverifikasi identitas akun/perangkat.
- Proses persetujuan yang jelas di Receiver sebelum berbagi layar dimulai, indikator sesi aktif, dan cara berhenti yang selalu tersedia.
- Saat tahap WebRTC dimulai: signaling server, STUN, dan TURN. Secret TURN harus diterbitkan oleh backend secara terbatas dan berumur pendek.

Backend masih memakai memori proses tunggal; polling API menggunakan interval dua detik dan retry/backoff hanya untuk GET status yang aman diulang. Request perubahan state tidak di-retry otomatis setelah timeout. Token disimpan hanya selama ViewModel aktif di memori proses aplikasi; belum diuji saat process death.

Source backend dan dependency Node.js yang digunakan gratis/open source; biaya hosting, domain, TLS, database, STUN/TURN, dan bandwidth bergantung pada provider serta trafik. Tidak ada layanan eksternal atau berbayar yang digunakan atau dipilih. Kebutuhan dan estimasi biaya provider perlu dibahas sebelum deployment.

## Status keamanan dan cakupan

Belum ada Accessibility Service, WebRTC, transmisi video, maupun mekanisme kendali. Alur system consent/foreground service belum diuji di perangkat fisik. Android pairing API belum diverifikasi terhadap server yang dideploy dan dua perangkat. Tidak ada akses layar tersembunyi.

## Langkah berikutnya

Uji sinkronisasi Gradle dan build debug dengan Android SDK yang tersedia, lalu rancang alur izin Receiver, pairing, dan persetujuan sebelum menambahkan transport WebRTC atau kontrol.
