# CLAUDE.md

Panduan untuk Claude Code di repo ini. Baca bagian "Aturan Domain Wajib" sebelum menyentuh logika stok, resep, atau biaya.

## Proyek

Aplikasi kasir Android (Kotlin) untuk usaha makanan atau minuman skala kecil. Data 100% lokal di ponsel, tanpa server, tanpa izin `INTERNET`. Pembeda utama: manajemen bahan baku berbasis takaran (resep per menu, stok bahan berkurang otomatis saat penjualan, HPP otomatis).

Sumber kebenaran:
* `docs/PRD.md`: spesifikasi lengkap. Bila kode dan PRD bertentangan, tanyakan dulu, jangan memilih sendiri.
* `docs/TEST_CASES_TAKARAN.md`: kasus uji logika inti beserta angka yang diharapkan. Jadikan uji unit sebelum menulis implementasinya.
* `docs/BACKLOG.md`: daftar pekerjaan berurutan. Kerjakan sesuai urutan dan dependensinya.

## Status

Belum ada proyek Gradle. Tugas pertama adalah backlog E0 (inisialisasi proyek). Perintah di bawah berlaku setelah proyek dibuat.

## Stack

Kotlin, Jetpack Compose (Material 3), Navigation Compose, Room, DataStore (Preferences), Hilt, Coroutines dan Flow, WorkManager, Coil, `androidx.core:core-splashscreen`. minSdk 26. Versi pustaka mengikuti rilis stabil terbaru saat proyek dibuat; kunci lewat `gradle/libs.versions.toml`.

## Struktur Paket (usulan)

```
app/src/main/java/id/arn/kasir/
  domain/    Kotlin murni, tanpa import android.*. Model, aturan takaran, use case.
  data/      Room (entity, DAO, migrasi), repository, DataStore, backup.
  ui/        Compose per fitur: home, keranjang, menu, bahan, laporan, pengaturan.
  di/        Modul Hilt.
```

Logika takaran, biaya, dan ketersediaan menu hidup di `domain/` agar bisa diuji dengan JUnit biasa, cepat, tanpa emulator.

## Aturan Domain Wajib

1. **Stok dan resep selalu `Long` dalam satuan dasar** (gram, mililiter, buah). Dilarang memakai `Float` atau `Double` untuk stok, resep, atau uang. Satuan lain (kg, liter, sendok makan) hanya untuk tampilan dan input.
2. **Konversi memakai `BigDecimal`**, hasil dibulatkan ke `Long` dengan `RoundingMode.HALF_UP`. Konversi antardimensi (massa ke volume) ditolak.
3. **Uang `Long` dalam Rupiah**, tanpa desimal. Waktu `Long` epoch milidetik UTC.
4. **Satu transaksi penjualan = satu `@Transaction` Room**: simpan transaksi, item, snapshot pemakaian bahan, pengurangan stok, dan pergerakan stok. Gagal di tengah berarti tidak ada yang berubah.
5. **Snapshot saat transaksi**: nama menu, harga satuan, HPP satuan, dan pemakaian bahan disalin ke baris transaksi. *Void* membalik berdasarkan snapshot, bukan resep terbaru.
6. **HPP dengan rata-rata tertimbang bergerak.** Biaya pemakaian = `round_half_up(jumlah x nilai_stok / stok)`; nilai stok baru = nilai lama dikurangi biaya itu (bukan dihitung ulang), sehingga jumlah seluruh biaya sama persis dengan nilai awal saat stok habis.
7. **Hapus lunak** untuk menu dan bahan yang punya riwayat. Transaksi tidak pernah dihapus, hanya berstatus batal.
8. **Laba kotor dan arus kas dipisah** (PRD 9.3). Pembelian bahan adalah pengeluaran kas, pemakaiannya adalah HPP. Jangan menjumlahkan keduanya.
9. **Cadangan** berisi salinan database yang konsisten (`VACUUM INTO` atau *checkpoint* WAL), bukan menyalin berkas yang sedang terbuka. Pemulihan wajib: validasi *checksum* dan versi skema, buat cadangan pengaman otomatis, lalu ganti secara atomik.

## Konvensi Kode

* Identifier Kotlin (kelas, fungsi, variabel) dalam bahasa Inggris. Nama tabel dan kolom SQL mengikuti PRD bagian 8 (bahasa Indonesia) lewat `@Entity(tableName = "...")` dan `@ColumnInfo(name = "...")`. Ini usulan; ubah bila pemilik proyek memilih lain.
* Semua teks UI di `strings.xml` (Indonesia dan Inggris). Tidak ada teks tertanam di kode.
* Satu `Activity`, navigasi lewat Navigation Compose, `ViewModel` mengekspos `StateFlow`.
* Setiap perubahan skema Room: naikkan versi, tulis migrasi eksplisit, tulis uji migrasi. Ekspor skema ke `app/schemas/` dan *commit*.
* Hindari dependensi baru tanpa alasan jelas; target ukuran aplikasi di bawah 25 MB.
* `allowBackup` Android diatur eksplisit agar tidak berbenturan dengan fitur cadangan sendiri.

## Perintah (setelah proyek dibuat)

```
./gradlew testDebugUnitTest       uji unit (domain dan data)
./gradlew connectedDebugAndroidTest   uji instrumentasi (migrasi, backup/restore)
./gradlew lintDebug               lint
./gradlew assembleDebug           build debug
```

## Alur Kerja

1. Ambil satu isu dari `docs/BACKLOG.md` sesuai urutan.
2. Untuk logika domain: tulis uji dari `docs/TEST_CASES_TAKARAN.md` dulu, lihat gagal, baru implementasi.
3. Satu cabang per isu, *commit* kecil, pesan *commit* ringkas dalam bahasa Indonesia.
4. Jalankan uji dan lint sebelum menyatakan selesai.

## Pertanyaan Terbuka (PRD bagian 15)

Belum dijawab pemilik proyek. Selama belum, pakai asumsi bawaan yang tertulis di PRD, tetapi **jangan menambah fitur yang bergantung pada jawabannya** (varian menu, pajak, PIN, multipengguna). Bila pekerjaan menyentuh salah satunya, berhenti dan tanyakan.
