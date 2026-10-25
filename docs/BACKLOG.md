# Backlog Aplikasi Kasir ARN

Daftar pekerjaan berurutan, diturunkan dari `docs/PRD.md` bagian 14. Setiap butir ditulis seperti satu isu GitHub (judul, label, kriteria penerimaan, dependensi) sehingga bisa disalin langsung ke GitHub Issues. Rujukan "PRD x.y" menunjuk bagian PRD; "TC" menunjuk `docs/TEST_CASES_TAKARAN.md`.

Label usulan: `setup`, `domain`, `data`, `ui`, `laporan`, `backup`, `qa`. Milestone: `MVP v1.0`.

**Status (Oktober 2026):** semua epik E0 sampai E9 sudah dikerjakan di Kotlin native, kecuali CI (#3) dan uji di perangkat fisik serta Android 8 sampai 12 (#30), yang masih terbuka. Rincian per tahap ada di [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md).

Ringkasan urutan: E0 → E1 → E2 → E3 → E4 → E5 → E6 → E7 → E8 → E9. E8 sebagian bisa paralel setelah E5.

---

## E0. Fondasi Proyek

### #1 Inisialisasi proyek Android (selesai)
`setup` · tanpa dependensi
* Buat proyek Android Studio (Kotlin, Jetpack Compose), Material 3, minSdk 26 di `app/build.gradle.kts`, Android Lint aktif.
* Pasang Room (+ KSP), DataStore, Navigation Compose, WorkManager, `core-splashscreen`. Tanpa pustaka tambahan untuk cadangan, gambar, grafik, dan notifikasi.
* Struktur paket `domain`, `data`, `ui`. Kunci versi lewat `gradle/libs.versions.toml`.
* Atur `allowBackup` secara eksplisit; tanpa izin `INTERNET`.

Kriteria penerimaan: `./gradlew lintDebug`, `testDebugUnitTest`, dan `assembleDebug` berhasil pada proyek kosong.

### #2 Tema, navigasi, dan *splash screen*
`setup`, `ui` · butuh #1
* Tema terang, gelap, ikuti sistem (PRD 6.3.2) dengan warna merek sementara.
* Kerangka navigasi: Halaman Utama dan semua tujuan sebagai layar kosong (di implementasi akhir: empat mode Kasir, Kelola, Laporan, Lainnya).
* *Splash* native dengan `core-splashscreen`, ditahan hanya selama inisialisasi lalu dilepas (PRD 6.1). Logo ARN sementara bila aset belum ada.

Kriteria penerimaan: aplikasi terbuka ke Halaman Utama tanpa layar putih; semua tujuan navigasi dapat dibuka.

### #3 CI dasar (belum dikerjakan)
`setup` · butuh #1
* GitHub Actions: `./gradlew lintDebug testDebugUnitTest` pada setiap *push* dan *pull request*.

---

## E1. Inti Domain Takaran (Kotlin murni, TDD)

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

## E2. Lapisan Data (Room)

### #8 Entitas, DAO, dan migrasi awal
`data` · butuh #1
* Semua tabel PRD bagian 8, indeks yang disarankan, kunci unik (nama bahan, nama kategori, `menu_id + bahan_id` pada resep).
* Uang `Long`, waktu `Long` epoch UTC, faktor satuan sebagai pembilang dan penyebut, hapus lunak.

Kriteria penerimaan: skema terekspor ke `app/schemas/`; uji dengan database dalam memori memasukkan dan membaca setiap tabel.

### #9 Repositori dan transaksi atomik penjualan
`data` · butuh #7, #8
* Implementasi repositori dengan satu `db.withTransaction { ... }` untuk penjualan dan *void*.

Kriteria penerimaan: TC E8 (atomisitas) lulus pada Room (SQLite) sungguhan; TC E1 sampai F5 lulus ulang di lapisan data.

### #10 Pengaturan (DataStore)
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
* ZIP berisi `manifest.json`, `arn.db` (salinan konsisten), `images/`, `settings.json`; *checksum* SHA 256; verifikasi sebelum menyalin ke lokasi pilihan lewat pemilih lokasi sistem (Storage Access Framework) (PRD 10.2 dan 10.3).

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
* Riwayat dalam aplikasi, pemicu bahan hampir habis dan habis (sekali per kondisi), pengingat cadangan lewat WorkManager, izin `POST_NOTIFICATIONS` di Android 13 ke atas (PRD 6.3.1).

### #26 Bahasa Indonesia dan Inggris
`ui` · butuh #2
* Bahasa dipilih di dalam aplikasi, seluruh teks di `strings.xml` (id dan en), format angka dan tanggal mengikuti bahasa (PRD 6.3.3).

### #27 Tentang aplikasi
`ui` · butuh #19
* Logo, versi, versi skema, lisensi pustaka, pernyataan data hanya di perangkat.

---

## E9. Penutup MVP

### #28 Uji migrasi dan uji integrasi menyeluruh
`qa` · butuh #23
* Uji migrasi Room untuk setiap kenaikan versi (skema tersimpan di `app/schemas/`); uji alur lengkap: bahan, resep, penjualan, *void*, laporan, cadangan, pulihkan.

### #29 Aksesibilitas dan kinerja
`qa` · butuh #20
* Target sentuh minimal 48 dp, kontras WCAG AA, *TalkBack*, ukuran huruf sistem; waktu buka dan waktu transaksi sesuai metrik PRD 2.2.

### #30 Uji di perangkat nyata dan rilis internal
`qa` · butuh semua
* Uji di ponsel kelas menengah dan Android 8; periksa ukuran aplikasi di bawah 25 MB per APK (`./gradlew bundleRelease`); APK/AAB rilis bertanda tangan (kunci tidak boleh masuk repo).
