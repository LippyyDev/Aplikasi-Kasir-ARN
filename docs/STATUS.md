# Status Implementasi

Dokumen ini jujur tentang apa yang sudah ada, apa yang belum pernah dijalankan, dan di mana kode menyimpang dari PRD. Baca sebelum mempercayai hasil apa pun.

## 1. Peringatan utama: kode belum pernah dikompilasi

Seluruh kode di repo ini ditulis tanpa Flutter SDK dan tanpa Dart SDK. Yang sudah diperiksa hanyalah:

* sintaks Dart (pemeriksa buatan sendiri, nol masalah pada seluruh berkas `lib/` dan `test/`);
* konsistensi impor (simbol proyek terpakai tanpa impor, impor yang tidak ada, bentrok nama tipe);
* kecocokan kunci lokalisasi: 365 kunci yang dipakai di kode sama persis dengan kunci di kedua ARB, placeholder cocok.

Yang **belum** diverifikasi oleh kompilator: tipe, nullability, tanda tangan API paket, kode hasil generator Drift dan gen-l10n, dan seluruh uji. Angka-angka dalam uji (misalnya TC E, G, I) diturunkan dari `docs/TEST_CASES_TAKARAN.md`, tetapi belum ada satu pun uji yang dijalankan. Anggap hasil pertama `flutter analyze` akan memunculkan galat nyata, kemungkinan puluhan, terutama di UI.

## 2. Langkah pertama Anda

```
dart tool/bootstrap.dart      # flutter create, tambal Gradle, pub get, build_runner, ikon, splash
flutter gen-l10n
dart format .
flutter analyze
flutter test
```

Urutan perbaikan yang disarankan bila banyak galat: (1) `lib/data/database` (kode Drift), (2) `lib/app/providers.dart`, (3) layar UI satu per satu. Domain (`lib/domain`) ditulis paling hati-hati dan paling mungkin langsung lulus.

Setelah `flutter test` hijau: ekspor skema (`dart run drift_dev schema dump lib/data/database/app_database.dart drift_schemas/`), aktifkan langkah pemeriksa format di `.github/workflows/ci.yml`, lalu uji di perangkat nyata (item di bagian 6).

## 3. Peta backlog

| # | Butir | Keadaan |
|---|---|---|
| 1 | Inisialisasi proyek | Kerangka siap; `android/` dibuat oleh `tool/bootstrap.dart` |
| 2 | Tema, navigasi, splash | Ditulis; splash bergantung hasil bootstrap |
| 3 | CI | Ditulis (`.github/workflows/ci.yml`), belum pernah jalan |
| 4 sampai 7 | Domain takaran, biaya, HPP, penjualan, void | Ditulis dengan uji TC A sampai H |
| 8 | Skema dan migrasi awal Drift | Ditulis; `drift_schemas/` masih kosong |
| 9 | Repositori dan transaksi atomik | Ditulis; skenario kontrak yang sama dijalankan pada in-memory dan Drift |
| 10 | Pengaturan | Ditulis dengan uji |
| 11 sampai 13 | Bahan baku, restok, opname | Ditulis |
| 14, 15 | Kategori, menu, resep | Ditulis |
| 16 sampai 19 | Beranda, keranjang, pembayaran, struk, profil | Ditulis |
| 20, 21 | Info pemasukan dan pengeluaran | Ditulis dengan uji TC I |
| 22 sampai 24 | Cadangan, pulihkan, antarmuka | Ditulis dengan uji TC K1, K2, K3, K5, K6 |
| 25 sampai 27 | Notifikasi, bahasa, tentang | Ditulis |
| 28 | Uji migrasi dan integrasi menyeluruh | **Belum**: lihat bagian 6 |
| 29 | Aksesibilitas dan kinerja | **Belum diukur** |
| 30 | Uji di perangkat nyata, rilis | **Belum** |

## 4. Deviasi dari PRD dan CLAUDE.md

1. **`workmanager` tidak dipakai.** Pengingat cadangan diperiksa saat aplikasi dibuka (`NotificationCenter.remindBackupIfDue`), dengan jeda antar pengingat. Konsekuensi: pengingat tidak muncul bila aplikasi tidak dibuka sama sekali. Alasan: dependensi dan perilaku latar belakang per pabrikan ponsel menambah risiko yang tidak sebanding untuk v1. Putuskan apakah ini cukup.
2. **Definisi arus kas tanpa susut.** `arus kas = penjualan bersih - pembelian bahan - pengeluaran manual`. Susut hasil opname tidak mengurangi kas karena bukan uang keluar, tetapi mengurangi laba operasional. TC I3 hanya menguji kasus tanpa susut, jadi definisi ini adalah keputusan saya; ubah di `PeriodFigures.from` bila PRD menghendaki lain.
3. **Kolom `alasan` tambahan** pada tabel `pergerakan_stok` (TC H1 mensyaratkan alasan pada penyesuaian). Skema ini perlu dicocokkan dengan PRD bagian 8.
4. **Stok awal bahan dicatat sebagai penyesuaian**, bukan restok, sehingga tidak menjadi pengeluaran.
5. **Warna dinamis nonaktif secara bawaan**, sesuai PRD; dapat diaktifkan di dialog tema.
6. **Struk selalu berlatar putih** agar gambar yang dibagikan terbaca di tema gelap.
7. **Hasil pemulihan tidak masuk riwayat notifikasi.** Basis data sudah ditutup dan diganti saat pemulihan selesai, jadi aplikasi meminta mulai ulang lewat dialog. Hasil cadangan (bukan pemulihan) tercatat normal.
8. **Berkas hasil generator tidak di-*commit*** (`*.g.dart`, `lib/l10n/generated/`). CI menghasilkannya.
9. **Jalur skema Drift** di CLAUDE.md diperbarui menjadi `lib/data/database/app_database.dart` sesuai struktur nyata.

## 5. Pertanyaan terbuka PRD bagian 15 dan default yang dipakai

Tidak ada fitur yang bergantung pada jawabannya yang dibangun. Yang dipakai hanyalah asumsi bawaan PRD:

| Pertanyaan | Default di kode |
|---|---|
| Jenis usaha | Umum (makanan atau minuman); tanpa istilah khusus |
| Varian dan topping | Tidak ada. Satu menu satu resep |
| Struk Bluetooth | Tidak ada. Struk dibagikan sebagai teks atau gambar |
| Arti ikon profil | Membuka Profil Usaha |
| Multipengguna | Tidak ada. Satu pengguna, tanpa login |
| Kebijakan stok kurang | Blokir; pilihan "beri peringatan" ada di Pengaturan stok (PRD 7.3) |
| Pajak | Tidak ada |
| Kunci aplikasi | Tidak ada |
| Aset logo ARN | Logo sementara di `assets/branding/`; ganti berkasnya, tidak perlu mengubah kode |

## 6. Risiko dan pekerjaan yang tersisa

**Versi dan API paket.** Kode ditulis terhadap rentang mayor berikut, dari ingatan dokumentasi, bukan dari kompilasi. Periksa bila ada galat API:

* `flutter_riverpod` 3 (`Notifier`, `StreamProvider`, `.family`), `go_router` 16
* `drift` 2.28 dengan `sqlite3` dibatasi `>=2.4.6 <3.0.0` (pemanggilan sqlite3 langsung terkumpul di `SqliteInspector`)
* `archive` 3.x, `file_picker` 10 (`FilePicker.platform`), `share_plus` 11 (`SharePlus.instance.share`)
* `flutter_local_notifications` 19 (API posisional), `fl_chart` 1.x (`SideTitleWidget(meta:, child:)`, `BarTouchTooltipData`)
* Flutter stabil 3.35 ke atas (`DropdownButtonFormField.initialValue`)

**Uji yang belum ada.**

* Uji migrasi Drift (baru ada skema versi 1; **TC K4 belum dapat diuji** karena belum ada skema yang lebih lama). Wajib ditulis begitu `schemaVersion` naik.
* `integration_test/` kosong. TC K1 pada perangkat (gambar tampil setelah pulihkan di instalasi bersih) dan TC K5 pada proses yang benar-benar dimatikan di tengah penukaran hanya diuji pada tingkat berkas, bukan pada perangkat.
* Tidak ada uji widget. Kriteria #29 (48 dp, kontras, *TalkBack*, ukuran huruf sistem) belum diperiksa.
* TC K2 membalik satu bit di tengah arsip. Bila bit itu jatuh di metadata ZIP, kegagalan muncul sebagai arsip tidak valid, bukan ketidakcocokan *checksum*. Keduanya ditolak, tetapi jalur *checksum* murni belum tentu terpancing oleh uji ini.

**Perilaku plugin yang harus dicoba di perangkat nyata:** pemilih lokasi simpan (`saveFile` dengan `bytes`), pemilih berkas `.arnbak` (tipe MIME tidak dikenal, jadi memakai `FileType.any` lalu memeriksa ekstensi), izin notifikasi Android 13 (izin hanya diminta saat pengguna menyalakan notifikasi di pengaturan, bukan saat pertama kali ada peringatan stok), kompresi gambar, dan ukuran APK per ABI (target di bawah 25 MB).

**Keamanan data.** Berkas `.arnbak` tidak dienkripsi (PRD 10; layar cadangan menyatakannya). Cadangan pengaman otomatis di penyimpanan internal dipangkas ke tiga terbaru.
