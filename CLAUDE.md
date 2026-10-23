# CLAUDE.md

Panduan untuk Claude Code di repo ini. Baca bagian "Aturan Domain Wajib" sebelum menyentuh logika stok, resep, atau biaya.

## Proyek

Aplikasi kasir Android (Flutter, Dart) untuk usaha makanan atau minuman skala kecil. Data 100% lokal di ponsel, tanpa server, tanpa izin `INTERNET`. Pembeda utama: manajemen bahan baku berbasis takaran (resep per menu, stok bahan berkurang otomatis saat penjualan, HPP otomatis).

Target platform hanya Android pada v1 (`flutter create --platforms=android`). Jangan menambah target web: `int` Dart di web hanya 53 bit dan merusak asumsi aritmetika di bawah.

Sumber kebenaran:
* `docs/PRD.md`: spesifikasi lengkap. Bila kode dan PRD bertentangan, tanyakan dulu, jangan memilih sendiri.
* `docs/TEST_CASES_TAKARAN.md`: kasus uji logika inti beserta angka yang diharapkan. Jadikan uji unit sebelum menulis implementasinya.
* `docs/BACKLOG.md`: daftar pekerjaan berurutan. Kerjakan sesuai urutan dan dependensinya.

## Status

Seluruh fitur MVP sudah ditulis, tetapi **belum pernah dikompilasi atau dijalankan** (ditulis tanpa SDK). Baca `docs/STATUS.md` lebih dulu: berisi langkah pertama, deviasi dari PRD, dan risiko versi paket. Setelah clone jalankan `dart tool/bootstrap.dart`, lalu `flutter gen-l10n`.

## Stack

Flutter (kanal stabil) dengan Dart, Material 3. Paket yang diusulkan, versi dikunci saat proyek dibuat (`pubspec.yaml` dan `pubspec.lock` di-*commit*):

| Kebutuhan | Paket |
|---|---|
| State dan DI | `flutter_riverpod` |
| Navigasi | `go_router` |
| Database | `drift`, `drift_dev`, `build_runner`, `sqlite3_flutter_libs` |
| Pengaturan | `shared_preferences` |
| Gambar | `image_picker`, `flutter_image_compress`, `path_provider` |
| Cadangan | `archive` (ZIP), `crypto` (SHA 256), `file_picker` |
| Notifikasi | `flutter_local_notifications` (`workmanager` tidak dipakai; pengingat cadangan dicek saat aplikasi dibuka, lihat `docs/STATUS.md`) |
| *Splash* | `flutter_native_splash` |
| Lokalisasi | `flutter_localizations`, `intl` (berkas ARB) |
| Grafik | `fl_chart` |
| Tema dinamis | `dynamic_color` |

minSdk 26 diatur di `android/app/build.gradle`.

## Struktur Folder (usulan)

```
lib/
  main.dart Titik masuk: splash, bootstrap, RestartScope.
  app/      Komposisi aplikasi: bootstrap, provider Riverpod, router, tema, layanan lintas fitur.
  domain/   Dart murni, TANPA import package:flutter. Model, aturan takaran, use case.
  data/     Drift (tabel, repositori, migrasi), pengaturan, gambar, cadangan, notifikasi sistem.
  ui/       Layar per fitur: home, cart, menu_setup, ingredient, report, profile, backup, shell, shared.
  l10n/     Berkas ARB (id, en); hasil generator ada di l10n/generated (tidak di-commit).
test/              Uji unit (domain dan data dengan database dalam memori).
integration_test/  Uji integrasi di perangkat (migrasi, cadangan, pulihkan).
drift_schemas/     Ekspor skema Drift per versi (di-commit).
```

Logika takaran, biaya, dan ketersediaan menu hidup di `lib/domain/` agar bisa diuji dengan `flutter test` biasa, cepat, tanpa emulator.

## Aturan Domain Wajib

1. **Stok dan resep selalu `int` dalam satuan dasar** (gram, mililiter, buah). Dilarang memakai `double` atau `num` untuk stok, resep, atau uang. Satuan lain (kg, liter, sendok makan) hanya untuk tampilan dan input.
2. **Konversi memakai aritmetika pecahan bilangan bulat**, bukan `double`. Faktor satuan disimpan sebagai pembilang dan penyebut (2,5 g = 5/2). Input teks pengguna ("2,5" atau "2.5") diparse persis menjadi pecahan. Pembulatan ke satuan dasar `HALF_UP`; untuk nilai positif: `(2 * n + d) ~/ (2 * d)`. Konversi antardimensi (massa ke volume) ditolak.
3. **Uang `int` dalam Rupiah**, tanpa desimal. Waktu `int` epoch milidetik UTC.
4. **Satu transaksi penjualan = satu `database.transaction(...)` Drift**: simpan transaksi, item, snapshot pemakaian bahan, pengurangan stok, dan pergerakan stok. Gagal di tengah berarti tidak ada yang berubah.
5. **Snapshot saat transaksi**: nama menu, harga satuan, HPP satuan, dan pemakaian bahan disalin ke baris transaksi. *Void* membalik berdasarkan snapshot, bukan resep terbaru.
6. **HPP dengan rata-rata tertimbang bergerak.** Biaya pemakaian = `round_half_up(jumlah x nilai_stok / stok)`; nilai stok baru = nilai lama dikurangi biaya itu (bukan dihitung ulang), sehingga jumlah seluruh biaya sama persis dengan nilai awal saat stok habis.
7. **Hapus lunak** untuk menu dan bahan yang punya riwayat. Transaksi tidak pernah dihapus, hanya berstatus batal.
8. **Laba kotor dan arus kas dipisah** (PRD 9.3). Pembelian bahan adalah pengeluaran kas, pemakaiannya adalah HPP. Jangan menjumlahkan keduanya.
9. **Cadangan** berisi salinan database yang konsisten (`VACUUM INTO` atau *checkpoint* WAL), bukan menyalin berkas yang sedang terbuka. Pemulihan wajib: validasi *checksum* dan versi skema, buat cadangan pengaman otomatis, lalu ganti secara atomik.

## Konvensi Kode

* Ikuti gaya Dart resmi dan `flutter_lints`; format dengan `dart format`.
* Identifier Dart (kelas, fungsi, variabel) dalam bahasa Inggris. Nama tabel dan kolom SQL mengikuti PRD bagian 8 (bahasa Indonesia) lewat `tableName` dan `.named('...')` di Drift, misalnya `IntColumn get baseStock => integer().named('stok_dasar')();`. Ini usulan; ubah bila pemilik proyek memilih lain.
* Semua teks UI di berkas ARB (Indonesia dan Inggris), dihasilkan lewat `flutter gen-l10n`. Tidak ada teks tertanam di kode.
* Navigasi lewat `go_router`; state lewat Riverpod (`Notifier` atau `AsyncNotifier`); widget tidak mengakses database langsung.
* Setiap perubahan skema Drift: naikkan `schemaVersion`, tulis migrasi eksplisit, ekspor skema ke `drift_schemas/`, tulis uji migrasi.
* Berkas hasil generator (`*.g.dart`) tidak diedit tangan. Putuskan bersama pemilik proyek apakah di-*commit*; bila tidak, pastikan CI menjalankan `build_runner`.
* Hindari paket baru tanpa alasan jelas. Perilaku plugin Android (pemilih berkas, notifikasi terjadwal, `workmanager`) harus diverifikasi di perangkat nyata, bukan hanya emulator.
* `android:allowBackup` diatur eksplisit di `AndroidManifest.xml` agar tidak berbenturan dengan fitur cadangan sendiri.

## Perintah (setelah proyek dibuat)

```
dart tool/bootstrap.dart                 sekali setelah clone (android/, pub get, generator, ikon, splash)
flutter pub get
dart run build_runner build --delete-conflicting-outputs   generate kode Drift
flutter gen-l10n                         generate AppLocalizations dari lib/l10n/*.arb
flutter analyze                          analisis statis
flutter test                             uji unit (domain dan data)
flutter test integration_test            uji integrasi (butuh perangkat/emulator)
dart run drift_dev schema dump lib/data/database/app_database.dart drift_schemas/   ekspor skema
flutter build apk --debug                build debug
flutter build apk --split-per-abi        build rilis per ABI (cek ukuran)
```

## Alur Kerja

1. Ambil satu isu dari `docs/BACKLOG.md` sesuai urutan.
2. Untuk logika domain: tulis uji dari `docs/TEST_CASES_TAKARAN.md` dulu, lihat gagal, baru implementasi.
3. Satu cabang per isu, *commit* kecil, pesan *commit* ringkas dalam bahasa Indonesia.
4. Jalankan `flutter analyze` dan `flutter test` sebelum menyatakan selesai.

## Pertanyaan Terbuka (PRD bagian 15)

Belum dijawab pemilik proyek. Selama belum, pakai asumsi bawaan yang tertulis di PRD, tetapi **jangan menambah fitur yang bergantung pada jawabannya** (varian menu, pajak, PIN, multipengguna). Bila pekerjaan menyentuh salah satunya, berhenti dan tanyakan.
