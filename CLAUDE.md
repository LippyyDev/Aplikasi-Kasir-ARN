# CLAUDE.md

Panduan untuk Claude Code di repo ini. Baca bagian "Aturan Domain Wajib" sebelum menyentuh logika stok, resep, atau biaya.

## Proyek

Aplikasi kasir Android (Kotlin, Jetpack Compose) untuk usaha makanan atau minuman skala kecil. Data 100% lokal di ponsel, tanpa server, tanpa izin `INTERNET`. Pembeda utama: manajemen bahan baku berbasis takaran (resep per menu, stok bahan berkurang otomatis saat penjualan, HPP otomatis).

Target platform hanya Android (minSdk 26, targetSdk 36). Proyek sudah berjalan lengkap; PRD semula menuliskan Flutter, lalu diganti Kotlin native (lihat `docs/IMPLEMENTATION_PLAN.md` Tahap 0 untuk pemetaan).

Sumber kebenaran:
* `docs/PRD.md`: spesifikasi produk. Bila kode dan PRD bertentangan, tanyakan dulu, jangan memilih sendiri. Dua penyimpangan sengaja: navigasi empat mode (bukan laci burger) dan orientasi bebas.
* `docs/TEST_CASES_TAKARAN.md`: kasus uji logika inti beserta angka yang diharapkan. Jadikan uji unit sebelum menulis implementasinya.
* `docs/IMPLEMENTATION_PLAN.md`: tahap dan checklist yang sudah dikerjakan, termasuk audit bug.
* `docs/BACKLOG.md`: daftar pekerjaan berurutan dan statusnya.

## Stack

Kotlin 2.2, Jetpack Compose + Material 3, Navigation Compose, Room (KSP, skema di `app/schemas`), DataStore, WorkManager, `core-splashscreen`. DI manual lewat `AppContainer` (tanpa Hilt). Tanpa pustaka untuk grafik, gambar, cadangan, dan notifikasi. Versi di `gradle/libs.versions.toml`.

## Struktur Folder

```
app/src/main/java/com/example/kasirarn/
  domain/   Kotlin murni, TANPA import android.*. Fraction, satuan, StockMath, Cart, peringatan stok.
  data/     db (Room: entitas, DAO), repo (repositori), backup, notify, settings, dummy (data contoh).
  ui/       Layar per fitur: home, menu, ingredient, report, misc, backup; components, nav, theme, format.
app/src/main/res/        strings.xml (id) dan values-en/strings.xml (en), tema, ikon.
app/src/main/assets/data_contoh/   Tiga skenario data contoh (JSON).
app/src/test/            Uji unit domain (JUnit, tanpa emulator).
app/src/androidTest/     Uji instrumented (alur inti, cadangan, data contoh).
app/schemas/             Ekspor skema Room per versi (di-commit).
tools/                   generate_data_contoh.py
docs/                    PRD, rencana implementasi, kasus uji, navigasi, data contoh, tangkapan layar.
```

Logika takaran, biaya, dan ketersediaan menu hidup di `domain/` agar bisa diuji dengan `./gradlew testDebugUnitTest`, cepat, tanpa emulator.

## Aturan Domain Wajib

1. **Stok dan resep selalu `Long` dalam satuan dasar** (gram, mililiter, buah). Dilarang memakai `Double` atau `Float` untuk stok, resep, atau uang. Satuan lain (kg, liter, sendok makan) hanya untuk tampilan dan input.
2. **Konversi memakai aritmetika pecahan bilangan bulat** (`Fraction`), bukan `Double`. Faktor satuan disimpan sebagai pembilang dan penyebut (2,5 g = 5/2). Input teks pengguna ("2,5" atau "2.5") diparse persis menjadi pecahan. Pembulatan ke satuan dasar `HALF_UP`; untuk nilai positif: `(2 * n + d) / (2 * d)`. Konversi antardimensi (massa ke volume) ditolak. Jangan memakai `BigInteger.TWO` atau `longValueExact` (butuh API 33 atau 31, minSdk 26).
3. **Uang `Long` dalam Rupiah**, tanpa desimal. Waktu `Long` epoch milidetik UTC.
4. **Satu transaksi penjualan = satu `db.withTransaction { ... }` Room**: simpan transaksi, item, snapshot pemakaian bahan, pengurangan stok, dan pergerakan stok. Gagal di tengah berarti tidak ada yang berubah.
5. **Snapshot saat transaksi**: nama menu, harga satuan, HPP satuan, dan pemakaian bahan disalin ke baris transaksi. *Void* membalik berdasarkan snapshot, bukan resep terbaru.
6. **HPP dengan rata-rata tertimbang bergerak.** Biaya pemakaian = `round_half_up(jumlah x nilai_stok / stok)`; nilai stok baru = nilai lama dikurangi biaya itu (bukan dihitung ulang), sehingga jumlah seluruh biaya sama persis dengan nilai awal saat stok habis.
7. **Hapus lunak** untuk menu dan bahan yang punya riwayat. Transaksi tidak pernah dihapus, hanya berstatus batal.
8. **Laba kotor dan arus kas dipisah** (PRD 9.3). Pembelian bahan adalah pengeluaran kas, pemakaiannya adalah HPP. Jangan menjumlahkan keduanya.
9. **Cadangan** berisi salinan database yang konsisten (`VACUUM INTO` atau *checkpoint* WAL), bukan menyalin berkas yang sedang terbuka. Pemulihan wajib: validasi *checksum* dan versi skema, buat cadangan pengaman otomatis, lalu ganti secara atomik.

## Konvensi Kode

* Gaya Kotlin resmi. Identifier (kelas, fungsi, variabel) dalam bahasa Inggris. Nama tabel dan kolom SQL mengikuti PRD bagian 8 (bahasa Indonesia) lewat `@Entity(tableName = ...)` dan `@ColumnInfo(name = ...)`.
* Semua teks UI di `strings.xml` (Indonesia) dan `values-en/strings.xml` (Inggris). Tidak ada teks tertanam di kode. Dalam callback, ambil string lewat `LocalResources`, bukan `LocalContext` (bahasa dipilih di dalam aplikasi).
* State lewat `ViewModel` + `StateFlow` (`stateIn(WhileSubscribed)`); composable tidak mengakses database langsung.
* Setiap perubahan skema Room: naikkan `version`, tulis migrasi eksplisit, ekspor skema ke `app/schemas/`, tulis uji migrasi.
* Pekerjaan berat (gambar, berkas, database) di `Dispatchers.IO`, bukan thread utama (StrictMode aktif di build debug).
* Hindari pustaka baru tanpa alasan jelas. Perilaku API sistem (pemilih berkas, notifikasi terjadwal, WorkManager) tetap perlu diverifikasi di perangkat nyata dan Android 8 sampai 12, bukan hanya emulator Android 15.
* `android:allowBackup` bernilai `false` di `AndroidManifest.xml` agar tidak berbenturan dengan fitur cadangan sendiri.
* Warna dan bentuk lewat tema (`ui/theme`); pakai warna dari `MaterialTheme.colorScheme` agar benar di terang, gelap, dan warna dinamis. Hindari tinggi tetap pada teks; pakai `heightIn(min = ...)`.

## Perintah

```
./gradlew assembleDebug                 build debug
./gradlew installDebug                  pasang ke emulator atau perangkat
./gradlew testDebugUnitTest             uji unit (domain)
./gradlew connectedDebugAndroidTest     uji instrumented (butuh emulator atau perangkat)
./gradlew lintDebug                     Android Lint
python tools/generate_data_contoh.py    bangkitkan ulang tiga berkas data contoh
```

## Alur Kerja

1. Ambil satu butir dari `docs/BACKLOG.md` sesuai urutan.
2. Untuk logika domain: tulis uji dari `docs/TEST_CASES_TAKARAN.md` dulu, lihat gagal, baru implementasi.
3. Satu cabang per butir, *commit* kecil, pesan *commit* ringkas dalam bahasa Indonesia.
4. Jalankan `lintDebug` dan `testDebugUnitTest` sebelum menyatakan selesai.

## Pertanyaan Terbuka (PRD bagian 15)

Belum dijawab pemilik proyek. Selama belum, pakai asumsi bawaan yang tertulis di PRD, tetapi **jangan menambah fitur yang bergantung pada jawabannya** (varian menu, pajak, PIN, multipengguna). Bila pekerjaan menyentuh salah satunya, berhenti dan tanyakan.
