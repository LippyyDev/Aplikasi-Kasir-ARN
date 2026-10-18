# Backlog Aplikasi Kasir ARN

Daftar pekerjaan berurutan, diturunkan dari `docs/PRD.md` bagian 14. Setiap butir ditulis seperti satu isu GitHub (judul, label, kriteria penerimaan, dependensi) sehingga bisa disalin langsung ke GitHub Issues. Rujukan "PRD x.y" menunjuk bagian PRD; "TC" menunjuk `docs/TEST_CASES_TAKARAN.md`.

Label usulan: `setup`, `domain`, `data`, `ui`, `laporan`, `backup`, `qa`. Milestone: `MVP v1.0`.

Ringkasan urutan: E0 → E1 → E2 → E3 → E4 → E5 → E6 → E7 → E8 → E9. E8 sebagian bisa paralel setelah E5.

---

## E0. Fondasi Proyek

### #1 Inisialisasi proyek Android
`setup` · tanpa dependensi
* Buat proyek Flutter hanya untuk Android (`flutter create --platforms=android`), Material 3, minSdk 26 di `android/app/build.gradle`, `flutter_lints` pada `analysis_options.yaml`.
* Pasang `flutter_riverpod`, `go_router`, `drift` (+ `drift_dev`, `build_runner`, `sqlite3_flutter_libs`), `shared_preferences`, `image_picker`, `flutter_image_compress`, `path_provider`, `archive`, `crypto`, `file_picker`, `flutter_local_notifications`, `workmanager`, `flutter_native_splash`, `fl_chart`, `dynamic_color`, `intl`.
* Struktur `lib/domain`, `lib/data`, `lib/ui`, `lib/l10n`. Kunci versi lewat `pubspec.lock`.
* Atur `allowBackup` secara eksplisit; tanpa izin `INTERNET`.

Kriteria penerimaan: `flutter analyze`, `flutter test`, dan `flutter build apk --debug` berhasil pada proyek kosong.

### #2 Tema, navigasi, dan *splash screen*
`setup`, `ui` · butuh #1
* Tema terang, gelap, ikuti sistem (PRD 6.3.2) dengan warna merek sementara.
* Kerangka navigasi: Halaman Utama dan laci burger dengan semua tujuan sebagai layar kosong.
* *Splash* native dengan `flutter_native_splash`, ditahan hanya selama inisialisasi lalu dilepas (PRD 6.1). Logo ARN sementara bila aset belum ada.

Kriteria penerimaan: aplikasi terbuka ke Halaman Utama tanpa layar putih; semua entri burger menu dapat dibuka.

### #3 CI dasar (opsional di awal)
`setup` · butuh #1
* GitHub Actions: `flutter analyze` dan `flutter test` pada setiap *push* dan *pull request* (termasuk `build_runner` bila berkas hasil generator tidak di-*commit*).

---

## E1. Inti Domain Takaran (Dart murni, TDD)

### #4 Model dan konversi satuan
`domain` · butuh #1
* Model dimensi (massa, volume, hitungan), satuan dasar, satuan kustom dengan faktor.
* Konversi dengan aritmetika pecahan bilangan bulat (tanpa `double`), pembulatan `HALF_UP`, parse input teks berkoma atau bertitik, penolakan antardimensi dan nilai nol atau negatif (PRD 7.1).

Kriteria penerimaan: seluruh TC A1 sampai A11 lulus sebagai uji unit.

### #5 Perhitungan biaya rata-rata tertimbang
`domain` · butuh #4
* Restok menambah stok dan nilai; pemakaian mengurangi nilai dengan aturan pembulatan di `CLAUDE.md` butir 6 (PRD 7.4).

Kriteria penerimaan: TC G1 sampai G6 lulus, termasuk properti jumlah biaya = nilai awal.

### #6 HPP menu dan ketersediaan porsi
`domain` · butuh #5
* HPP per porsi, margin, penanda "HPP belum lengkap", porsi tersedia, kebijakan blokir atau peringatkan, pemeriksaan keranjang gabungan per bahan (PRD 7.3).

Kriteria penerimaan: TC C1 sampai C4 dan D1 sampai D6 lulus.

### #7 Use case penjualan dan pembatalan (murni, repositori palsu)
`domain` · butuh #6
* Hitung pemakaian bahan, snapshot, total, diskon, kembalian; operasi *void* dari snapshot.

Kriteria penerimaan: TC E1 sampai E10 dan F1 sampai F5 lulus terhadap repositori dalam memori.

---

## E2. Lapisan Data (Drift)

### #8 Tabel, DAO, dan migrasi awal
`data` · butuh #1
* Semua tabel PRD bagian 8, indeks yang disarankan, kunci unik (nama bahan, nama kategori, `menu_id + bahan_id` pada resep).
* Uang `int`, waktu `int` epoch UTC, faktor satuan sebagai pembilang dan penyebut, hapus lunak.

Kriteria penerimaan: skema terekspor ke `drift_schemas/`; uji dengan database dalam memori memasukkan dan membaca setiap tabel.

### #9 Repositori dan transaksi atomik penjualan
`data` · butuh #7, #8
* Implementasi repositori dengan satu `database.transaction(...)` untuk penjualan dan *void*.

Kriteria penerimaan: TC E8 (atomisitas) lulus pada Drift (SQLite) sungguhan; TC E1 sampai F5 lulus ulang di lapisan data.

### #10 Pengaturan (`shared_preferences`)
`data` · butuh #1
* Tema, bahasa, kebijakan stok, jadwal cadangan, waktu cadangan terakhir, preferensi notifikasi.

---

## E3. Bahan Baku

### #11 Daftar dan form bahan baku
`ui` · butuh #8, #4
* Tambah, ubah, arsipkan bahan; dimensi, satuan beli, takaran kustom, stok minimum, stok awal (PRD 6.3.5).
* Indikator aman, menipis, habis.

Kriteria penerimaan: TC J1, J3, J5 lulus; bahan dengan riwayat hanya dapat diarsipkan.

### #12 Restok (pembelian)
`ui`, `data` · butuh #5, #11
* Input jumlah, satuan, total harga; menambah stok dan nilai; otomatis membuat pengeluaran kategori bahan baku.

Kriteria penerimaan: TC G7 lulus.

### #13 Penyesuaian stok (opname) dan riwayat pergerakan
`ui`, `data` · butuh #11
* Input stok fisik, alasan, pengeluaran susut opsional; riwayat per bahan dengan penyaring tanggal.

Kriteria penerimaan: TC H1 sampai H4 lulus.

---

## E4. Kategori, Menu, dan Resep

### #14 Kategori menu
`ui` · butuh #8
* Tambah, ubah, urutkan (seret), hapus dengan penanganan menu di dalamnya (PRD 6.3.4a).

Kriteria penerimaan: TC J3 dan J4 lulus.

### #15 Menu dan resep
`ui` · butuh #6, #11, #14
* Form menu (nama, foto terkompres, kategori, harga, status aktif, catatan), editor resep dengan pilihan satuan, HPP dan margin langsung, duplikat menu, hapus lunak.

Kriteria penerimaan: TC C1 sampai C3 dan J2 lulus; foto tersimpan di penyimpanan internal aplikasi.

---

## E5. Halaman Utama, Keranjang, Pembayaran

### #16 Halaman Utama
`ui` · butuh #14, #15
* *Top bar* (burger, pencarian langsung, profil), *chip* kategori dengan "Semua", *grid* menu, status "Stok habis", *empty state*.

### #17 Keranjang
`ui` · butuh #16, #6
* Bilah keranjang, ubah jumlah, hapus, catatan per item, diskon nominal atau persen, validasi stok gabungan.

Kriteria penerimaan: TC D4 sampai D6 lulus lewat UI atau ViewModel.

### #18 Pembayaran dan struk
`ui` · butuh #9, #17
* Tunai (kembalian otomatis) dan non tunai (dicatat saja), layar sukses, bagikan struk sebagai gambar atau teks.

Kriteria penerimaan: TC E4 sampai E6 dan E10 lulus; transaksi nyata mengurangi stok sesuai E1 dan E2.

### #19 Profil usaha
`ui` · butuh #10
* Nama usaha, logo, alamat, telepon, mata uang; dipakai pada struk dan layar Tentang.

---

## E6. Laporan

### #20 Info pemasukan
`laporan` · butuh #18
* Periode (hari ini, 7 hari, bulan ini, kustom), kartu ringkasan, grafik tren dan menu terlaris, rincian transaksi, tombol *void*.
* Agregasi di SQL, bukan di memori.

Kriteria penerimaan: TC F4 dan I1 lulus; tetap lancar dengan data uji 50 ribu transaksi.

### #21 Info pengeluaran
`laporan` · butuh #12
* Kategori pengeluaran, input manual, ringkasan dan grafik, tampilan laba bersih dan arus kas terpisah dengan penjelasan singkat (PRD 9.3).

Kriteria penerimaan: TC I2 dan I3 lulus.

---

## E7. Cadangan dan Pulihkan (kerjakan sebelum rilis, bukan sesudah)

### #22 Pembuat berkas `.arnbak`
`backup` · butuh #8, #10
* ZIP berisi `manifest.json`, `arn.db` (salinan konsisten), `images/`, `settings.json`; *checksum* SHA 256; verifikasi sebelum menyalin ke lokasi pilihan lewat pemilih lokasi sistem (`file_picker`) (PRD 10.2 dan 10.3).

Kriteria penerimaan: TC K1 dan K6 lulus.

### #23 Pulihkan data
`backup` · butuh #22
* Validasi arsip, *checksum*, versi skema; ringkasan dan konfirmasi; cadangan pengaman otomatis; penggantian atomik; pemulihan balik bila gagal; mulai ulang aplikasi (PRD 10.5).

Kriteria penerimaan: TC K1 sampai K5 lulus.

### #24 Antarmuka cadangan dan pengingat
`backup`, `ui` · butuh #22, #23
* Layar Cadangan dan Pulihkan, indikator "terakhir dicadangkan" di burger menu, peringatan berkas tidak terenkripsi.

---

## E8. Pelengkap Burger Menu

### #25 Notifikasi
`ui`, `data` · butuh #12, #24
* Riwayat dalam aplikasi, pemicu bahan hampir habis dan habis (sekali per kondisi), pengingat cadangan lewat `workmanager`, izin `POST_NOTIFICATIONS` di Android 13 ke atas (PRD 6.3.1).

### #26 Bahasa Indonesia dan Inggris
`ui` · butuh #2
* *Per-app language*, seluruh teks di berkas ARB, format angka dan tanggal mengikuti bahasa (PRD 6.3.3).

### #27 Tentang aplikasi
`ui` · butuh #19
* Logo, versi, versi skema, lisensi pustaka, pernyataan data hanya di perangkat.

---

## E9. Penutup MVP

### #28 Uji migrasi dan uji integrasi menyeluruh
`qa` · butuh #23
* Uji migrasi Drift untuk setiap kenaikan versi (skema tersimpan di `drift_schemas/`); uji alur lengkap: bahan, resep, penjualan, *void*, laporan, cadangan, pulihkan.

### #29 Aksesibilitas dan kinerja
`qa` · butuh #20
* Target sentuh minimal 48 dp, kontras WCAG AA, *TalkBack*, ukuran huruf sistem; waktu buka dan waktu transaksi sesuai metrik PRD 2.2.

### #30 Uji di perangkat nyata dan rilis internal
`qa` · butuh semua
* Uji di ponsel kelas menengah dan Android 8; periksa ukuran aplikasi di bawah 25 MB per APK (`flutter build apk --split-per-abi`); APK/AAB rilis bertanda tangan (kunci tidak boleh masuk repo).
