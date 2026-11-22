# FALDY VIEW

Fondasi aplikasi Android native untuk melihat layar perangkat lain dengan persetujuan pemiliknya. Versi ini hanya menyediakan dashboard dan belum mengimplementasikan koneksi, berbagi layar, atau kontrol jarak jauh.

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

## Status keamanan dan cakupan

Belum ada izin MediaProjection atau Accessibility Service, pairing, WebRTC, maupun mekanisme kendali. Fitur berbagi layar nantinya harus memerlukan persetujuan eksplisit, menampilkan indikator sesi aktif, dan menyediakan tombol berhenti. Tidak ada akses layar tersembunyi.

## Langkah berikutnya

Uji sinkronisasi Gradle dan build debug dengan Android SDK yang tersedia, lalu rancang alur izin Receiver, pairing, dan persetujuan sebelum menambahkan transport WebRTC atau kontrol.
