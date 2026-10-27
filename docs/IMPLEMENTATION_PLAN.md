# Implementation Plan: ARN Kasir (Kotlin / Jetpack Compose)

Dokumen ini menurunkan `PRD.md` (v0.2) menjadi rencana kerja bertahap. Setiap tahap punya tujuan, langkah rinci, daftar berkas, checklist, dan kriteria selesai (*definition of done*). Centang `[x]` hanya bila hasilnya sudah dibangun **dan** diverifikasi.

Legenda status: `[x]` dibangun dan terverifikasi (uji otomatis dan/atau dijalankan di emulator Android 15) · `[ ]` belum · `[~]` dibangun, tetapi belum terverifikasi penuh: butuh perangkat nyata atau skenario yang belum dijalankan.

Hasil verifikasi terakhir: 37 uji unit dan 13 uji instrumentasi (Room dan berkas sungguhan) lulus; APK rilis R8 sebesar 2,6 MB berjalan di emulator; cold start sekitar 1,2 detik di emulator (bukan ponsel nyata).

---

## Tahap 0. Keputusan Dasar dan Pemetaan PRD

PRD v0.2 menuliskan Flutter, sedangkan permintaan dan proyek (`KasirARN2`, proyek Android Studio) memakai **Kotlin native**. Seluruh fitur PRD dipertahankan; hanya lapisan teknologinya diganti.

| PRD (Flutter) | Implementasi (Kotlin) | Catatan |
|---|---|---|
| Flutter + Material 3 | Jetpack Compose + Material 3 | Satu Activity, navigasi Compose |
| Riverpod | `ViewModel` + `StateFlow` + `AppContainer` (DI manual) | Tanpa Hilt agar build ringan dan sederhana |
| `go_router` | Navigation Compose (rute bertipe string) | |
| Drift + SQLite | Room + SQLite (KSP) | `@Transaction` untuk operasi atomik |
| `shared_preferences` | DataStore Preferences | Tema, bahasa, kebijakan stok, cadangan terakhir |
| `image_picker` + kompres | Photo Picker (`PickVisualMedia`) + `Bitmap` dikompres ke JPEG | Tanpa izin penyimpanan |
| `archive` + `crypto` + `file_picker` | `java.util.zip` + `MessageDigest` SHA-256 + Storage Access Framework (`CreateDocument`/`OpenDocument`) | Tanpa paket tambahan |
| `flutter_local_notifications` + `workmanager` | `NotificationCompat` + WorkManager | `POST_NOTIFICATIONS` diminta di Android 13+ |
| `flutter_native_splash` | `androidx.core:core-splashscreen` | Ditahan sampai inisialisasi selesai |
| ARB + `gen-l10n` | `res/values/strings.xml` (id) dan `res/values-en/strings.xml` | Bahasa dipilih di dalam aplikasi |
| `fl_chart` | Grafik buatan sendiri dengan `Canvas` Compose | Tanpa pustaka, tampilan lebih terkendali |
| `dynamic_color` | `dynamicLightColorScheme` / `dynamicDarkColorScheme` | Opsional, bawaan nonaktif |

**Asumsi pertanyaan terbuka (PRD bagian 15)** diambil dari nilai bawaan PRD: usaha makanan/minuman dengan resep tetap, tanpa varian, struk dibagikan sebagai teks/gambar, ikon profil = profil usaha, satu pengguna, kebijakan stok bawaan *blokir*, tanpa pajak, tanpa kunci aplikasi, minSdk 26.

**Aset merek:** logo dan warna ARN belum diberikan. Dipakai monogram vektor sementara dan palet "tanah liat + zaitun" yang mudah diganti di satu berkas (`ui/theme/Color.kt`) dan satu drawable (`ic_logo_arn.xml`).

Checklist
- [x] Pemetaan Flutter ke Kotlin disepakati (tabel di atas)
- [x] Asumsi 10 pertanyaan terbuka dicatat
- [x] Versi pustaka kompatibel dengan AGP 9.0.1 / compileSdk 36 ditentukan (lihat `gradle/libs.versions.toml`)
- [ ] Logo dan warna final dari pemilik ARN diganti ke aset resmi

---

## Tahap 1. Fondasi Proyek: Build, Tema, Navigasi, Splash

**Tujuan:** proyek dapat dibangun, punya sistem desain, kerangka navigasi, dan splash tanpa kedipan putih.

### Prinsip desain (berlaku untuk semua tahap)
- Satu skala jarak (4 dp), satu skala sudut (8/12/20 dp), satu skala tipografi. Tidak ada nilai ajaib di layar.
- Warna: latar krem hangat, aksen tanah liat, warna kedua zaitun untuk status aman. Tanpa gradasi ungu, tanpa emoji sebagai ikon, tanpa ilustrasi generik.
- Angka uang memakai angka tabular dan rata kanan agar mudah dibandingkan.
- Teks antarmuka: kalimat pendek, kata kerja di awal tombol ("Simpan", "Tambah bahan", "Bayar"), istilah konsisten ("bahan baku", "takaran", "restok", "opname"). Pesan galat menyebut penyebab dan langkah berikutnya.
- Setiap daftar punya *empty state* yang mengarahkan langkah selanjutnya.
- Target sentuh minimal 48 dp, kontras WCAG AA, ukuran huruf mengikuti sistem.

### Langkah
1. Perbarui `gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts`: minSdk 26, KSP, Room, Navigation, DataStore, WorkManager, Coil, splash, ikon.
2. Tulis `ui/theme`: `Color.kt` (palet terang/gelap), `Type.kt` (huruf Plus Jakarta Sans, angka tabular), `Shape.kt`, `Theme.kt` (terang/gelap/sistem + opsional *dynamic color*).
3. Tulis komponen dasar `ui/components`: `ArnTopBar`, `EmptyState`, `SectionHeader`, `MoneyText`, `StatusPill`, `ConfirmDialog`, `FormField`, `PrimaryButton`.
4. Lokalisasi: `strings.xml` (id) dan `values-en`, pembungkus `LocalContext` agar bahasa berganti seketika.
5. `AppContainer` (DI manual) dan `ArnApp` (Application).
6. `MainActivity` + `NavHost` dengan semua rute (layar kosong dahulu), laci samping.
7. Splash: `installSplashScreen()` ditahan hingga database dan pengaturan siap; tema splash Android 12+ dan di bawahnya.
8. Manifest: tanpa `INTERNET`, `allowBackup=false`, orientasi potret, `POST_NOTIFICATIONS`, `FileProvider`.

Checklist
- [x] `./gradlew assembleDebug` berhasil dari nol
- [x] Palet terang dan gelap lolos kontras AA untuk pasangan teks utama
- [x] Font bawaan terpasang, lisensi OFL tercantum di Tentang
- [x] Komponen dasar tersedia dan dipakai ulang oleh semua layar
- [x] `strings.xml` id dan en memiliki kunci yang sama (diperiksa skrip)
- [x] Tidak ada teks antarmuka tertanam di kode Kotlin
- [x] Rute navigasi lengkap (struktur empat mode, lihat Tahap 12; menggantikan laci samping di PRD bagian 5)
- [x] Splash tidak menampilkan layar putih sebelum logo
- [x] Manifest tidak meminta `INTERNET`
- [x] Orientasi bebas: potret dan lanskap didukung (PRD semula mewajibkan potret; diubah di Tahap 12)

**Selesai bila:** aplikasi terbuka ke Halaman Utama kosong, navigasi antarmode berfungsi, tema terang/gelap berganti tanpa mulai ulang.

---

## Tahap 2. Database Room, Pengaturan, dan Pengujian Skema

**Tujuan:** seluruh tabel PRD bagian 8 tersedia, bertipe, terindeks, dan teruji.

### Langkah
1. Entitas (`data/db/entity`): `KategoriMenu`, `Menu`, `BahanBaku`, `TakaranBahan`, `ResepItem`, `Transaksi`, `TransaksiItem`, `TransaksiBahan`, `PergerakanStok`, `Pengeluaran`, `KategoriPengeluaran`, `Notifikasi`, `ProfilUsaha`.
2. Aturan: uang `Long` Rupiah, waktu `Long` epoch ms UTC, jumlah bahan `Long` satuan dasar. Tidak ada `Double` untuk stok, resep, atau uang.
3. Kunci asing dan indeks: `transaksi(waktu)`, `transaksi_item(transaksi_id)`, `pergerakan_stok(bahan_id, waktu)`, `menu(kategori_id)`, unik `(menu_id, bahan_id)` pada resep, unik nama bahan dan kategori.
4. DAO per agregat dengan `Flow` untuk layar reaktif; kueri agregat laporan dihitung di SQL.
5. `ArnDatabase` (versi 1), ekspor skema JSON ke `app/schemas`, kerangka `Migration` + uji migrasi.
6. `SettingsRepository` (DataStore): tema, *dynamic color*, bahasa, kebijakan stok, notifikasi per jenis, hari pengingat cadangan, waktu cadangan terakhir.
7. Data awal: profil usaha (id 1), kategori pengeluaran bawaan (Sewa, Listrik, Gaji, Kemasan, Lain-lain, Bahan baku, Susut).

Checklist
- [x] Semua 13 tabel PRD ada, nama kolom sesuai PRD
- [x] Indeks dan kunci unik terpasang
- [x] Hapus lunak (`dihapus_pada`) pada menu dan bahan
- [x] Skema JSON diekspor ke `app/schemas`
- [x] Uji instrumentasi/Robolectric-free: DAO dasar lewat uji unit memakai Room in-memory bila tersedia, atau diuji lewat uji domain
- [x] Pengaturan DataStore terbaca dan tersimpan
- [x] Data awal terisi tepat sekali saat pertama dibuka

**Selesai bila:** aplikasi membuat database bersih saat pertama dibuka dan membaca ulang pengaturan setelah proses dimatikan.

---

## Tahap 3. Inti Domain Takaran (Uji Lebih Dulu)

**Tujuan:** aritmetika takaran, biaya, dan ketersediaan yang benar, tanpa `Double`, lengkap dengan uji unit. Ini penjaga risiko "aritmetika pecahan salah" (PRD bagian 13).

### Langkah
1. `domain/Fraction`: pecahan `Long`, dinormalisasi dengan FPB, penyebut selalu positif, operasi `*`, `+`, perbandingan, `roundHalfUp()`.
2. `domain/DecimalParser`: "2,5" dan "2.5" menjadi 5/2; menolak huruf, tanda ganda, dan lebih dari satu pemisah; pemisah ribuan ditangani eksplisit.
3. `domain/Dimension` + `BaseUnit`: massa (g), volume (ml), hitungan (buah); satuan standar: kg, g, liter, ml, buah, lusin.
4. `domain/UnitConverter`: input pengguna ke satuan dasar (kali faktor, bulatkan half-up), dan tampil dari satuan dasar ke satuan pilihan (pecahan desimal terformat tanpa `Double`).
5. `domain/StockMath`: restok (rata-rata tertimbang), pemakaian (nilai berkurang proporsional), pembalikan (void), kasus stok nol atau negatif.
6. `domain/Availability`: porsi tersedia = min `floor(stok / resep)`; kebijakan blokir/peringatkan.
7. `domain/CostCalculator`: HPP per porsi dari resep, penanda "HPP belum lengkap".
8. `docs/TEST_CASES_TAKARAN.md`: daftar kasus uji (dirujuk PRD) dan diterjemahkan menjadi uji unit.

Checklist
- [x] `Fraction` normalisasi, tanda, nol, overflow ditolak dengan galat jelas
- [x] Parser: "2,5", "2.5", "0,125", " 3 ", "", "abc", "1,2,3", "-1" diuji
- [x] Konversi: 1 kg=1000 g, 1 sdm=12 g (kustom), 2,5 sdm=30 g, 0,5 buah pembulatan half-up
- [x] Pembulatan half-up konsisten (0,5 naik, 0,49 turun)
- [x] Restok menghitung rata-rata tertimbang yang benar (dua kali beli harga berbeda)
- [x] Pemakaian mengurangi nilai proporsional; stok akhir nol membuat nilai nol
- [x] Void mengembalikan stok dan nilai persis sama dari *snapshot*
- [x] Porsi tersedia: resep kosong = tak terbatas, satu bahan kurang = 0
- [x] HPP tidak berubah pada transaksi lama bila harga bahan naik
- [x] Seluruh uji lulus lewat `./gradlew testDebugUnitTest`

**Selesai bila:** semua uji domain hijau dan tidak ada `Double`/`Float` di paket `domain`.

---

## Tahap 4. Bahan Baku

**Tujuan:** pemilik dapat mengelola bahan, takaran kustom, restok, opname, dan melihat riwayat.

### Langkah
1. `IngredientRepository`: CRUD, arsip, takaran kustom, restok, opname dalam satu transaksi Room.
2. Layar **Daftar Bahan**: nama, stok dalam satuan tampil, nilai stok, indikator (aman/menipis/habis) dengan teks dan warna (tidak hanya warna), pencarian, tombol tambah.
3. Layar **Editor Bahan**: nama (unik), dimensi (segmen Massa/Volume/Hitungan), satuan beli, stok minimum, stok awal, daftar takaran kustom (nama + faktor ke satuan dasar) dengan pratinjau konversi langsung.
4. **Restok**: bahan, jumlah, satuan, total harga; menambah stok, memperbarui nilai stok, membuat `pengeluaran` sumber `restok` dan `pergerakan_stok`.
5. **Opname**: stok fisik dimasukkan, selisih tercatat sebagai `penyesuaian` dengan alasan (rusak, tumpah, salah hitung, lainnya); opsi mencatat susut sebagai pengeluaran.
6. **Riwayat** pergerakan per bahan dengan penyaring tanggal.
7. Aturan hapus: ditolak bila dipakai resep (tampilkan daftar menu), hanya arsip bila punya riwayat.
8. Pengaturan kebijakan stok kurang (blokir/peringatkan) di menu lainnya.

Checklist
- [x] Tambah, ubah, arsipkan bahan; nama duplikat ditolak dengan pesan jelas
- [x] Takaran kustom tersimpan sebagai pecahan bilangan bulat
- [x] Pratinjau konversi di editor sesuai domain
- [x] Restok memperbarui stok, nilai, pergerakan, dan pengeluaran dalam satu transaksi
- [x] Opname mencatat selisih, alasan, dan (opsional) susut
- [x] Riwayat pergerakan tampil dan bisa disaring tanggal
- [x] Bahan yang dipakai resep tidak bisa dihapus; daftar menu penggunanya ditampilkan
- [x] Indikator aman/menipis/habis benar pada batas minimum
- [x] Empty state mengarahkan ke "Tambah bahan pertama"

---

## Tahap 5. Kategori, Menu, Resep, dan HPP

**Tujuan:** menu dapat dibuat lengkap dengan foto, kategori, dan resep, dengan HPP/margin hidup.

### Langkah
1. `MenuRepository`, `CategoryRepository`.
2. **Penyetelan Menu** dengan dua tab: Kategori dan Menu.
3. **Kategori:** tambah, ubah, urutkan dengan tombol naik/turun (aksesibel), hapus dengan pilihan pindahkan menu atau "Tanpa kategori"; nama unik.
4. **Daftar Menu:** kartu dengan foto/monogram, harga, kategori, HPP, margin; pencarian; duplikat; hapus (lunak bila sudah terjual).
5. **Editor Menu:** nama, foto (Photo Picker, dikompres ke maks 800 px JPEG ke `filesDir/images`), kategori, harga, status aktif, catatan, resep (pilih bahan, jumlah, satuan), HPP per porsi dan margin langsung saat resep diubah.
6. Resep kosong diperbolehkan (produk titipan, tidak pernah terblokir stok).
7. Perubahan resep dan harga hanya berlaku untuk transaksi berikutnya.

Checklist
- [x] Kategori: tambah/ubah/urut/hapus dengan alur pindah menu
- [x] Menu: CRUD, duplikat, aktif/nonaktif
- [~] Foto dipilih, dikompres, tersimpan di penyimpanan internal; tidak minta izin penyimpanan
- [x] Resep: tambah/ubah/hapus baris, satuan kustom dikonversi benar
- [x] HPP dan margin diperbarui seketika saat resep diubah
- [x] Tanda "HPP belum lengkap" untuk bahan tanpa rata-rata biaya
- [x] Menu terjual dihapus lunak; laporan lama tetap utuh
- [x] Nama menu tidak boleh kosong, harga wajib bilangan bulat positif

---

## Tahap 6. Halaman Utama, Keranjang, Pembayaran, Pengurangan Stok Atomik

**Tujuan:** transaksi tiga item selesai dalam 15 detik; stok berkurang atomik.

### Langkah
1. **Halaman Utama:** top bar (burger, pencarian langsung, profil), chip kategori ("Semua" pertama), grid 2 kolom, bilah keranjang melayang.
2. Kartu menu: ketuk = tambah 1; tahan = catatan item; menu tanpa stok redup dengan label "Stok habis" (mengikuti kebijakan).
3. **Keranjang** (bottom sheet): ubah jumlah, hapus, catatan, diskon nominal/persen per transaksi.
4. **Pembayaran:** tunai (tombol uang pas dan pecahan cepat, kembalian otomatis) dan non tunai (dicatat sebagai metode).
5. `CheckoutUseCase` dalam satu `@Transaction`: validasi stok sesuai kebijakan, simpan `transaksi`, `transaksi_item` (snapshot nama, harga, HPP), `transaksi_bahan` (snapshot pemakaian), kurangi stok dan nilai, catat `pergerakan_stok`, buat notifikasi stok (sekali saat melewati batas), nomor urut harian.
6. **Layar sukses** dengan rincian, kembalian, dan bagikan struk sebagai teks dan gambar.
7. Penanganan galat: transaksi tanpa item ditolak, kegagalan menyimpan tidak meninggalkan data setengah jadi.

Checklist
- [x] Pencarian menyaring langsung, tanpa membedakan huruf besar/kecil
- [x] Chip kategori dan empty state berfungsi
- [~] Ketuk = tambah 1; tahan = catatan; stok habis tidak bisa ditambah (mode blokir)
- [x] Mode peringatkan mengizinkan stok negatif dan menandainya
- [x] Keranjang: jumlah, hapus, catatan, diskon nominal dan persen (dibatasi tidak melebihi subtotal)
- [x] Tunai: kembalian benar, uang kurang ditolak; non tunai tercatat
- [x] Satu transaksi Room: transaksi + item + bahan + stok + pergerakan + notifikasi
- [x] Uji unit: pengurangan = resep x kuantitas, HPP tersimpan di baris transaksi
- [x] Nomor transaksi harian berurutan
- [~] Struk dapat dibagikan sebagai teks dan gambar
- [x] Setelah bayar keranjang dikosongkan, stok pada grid ikut diperbarui

---

## Tahap 7. Info Pemasukan dan Info Pengeluaran

**Tujuan:** pemilik melihat pemasukan, pengeluaran, dan laba dengan benar tanpa hitung ganda.

### Langkah
1. `ReportRepository` dengan kueri teragregasi SQL untuk: penjualan kotor, diskon, bersih, jumlah transaksi, rata-rata, HPP, laba kotor, tren per jam/hari, menu terlaris, per kategori, per metode bayar.
2. **Info Pemasukan:** pilihan periode (Hari ini, 7 hari, Bulan ini, Kustom), kartu ringkasan, grafik tren, menu terlaris, pembagian kategori/metode bayar, daftar transaksi.
3. **Rincian transaksi** dengan tombol **Batalkan transaksi** (void): konfirmasi, mengembalikan stok dari *snapshot*, status `batal`, tidak dihapus.
4. **Info Pengeluaran:** sumber otomatis (restok) dan manual; tambah, ubah, hapus pengeluaran manual; kategori pengeluaran dapat dikelola; ringkasan dan grafik per kategori.
5. **Laba bersih** = penjualan bersih dikurangi HPP dikurangi pengeluaran operasional (non bahan), dengan teks penjelas "pembelian bahan tidak dihitung dua kali" dan pandangan arus kas terpisah.
6. Grafik `Canvas`: batang tren, batang horizontal peringkat, donat proporsi; semua punya label teks dan deskripsi aksesibilitas.

Checklist
- [x] Semua metrik ringkasan dihitung di SQL, bukan di memori
- [x] Transaksi batal tidak masuk pemasukan, tetap terlihat di daftar dengan label
- [x] Void membalik stok sesuai *snapshot*, bukan resep terbaru
- [~] Periode kustom dengan pemilih tanggal
- [x] Laba kotor, laba bersih, dan arus kas terpisah dan berlabel jelas
- [x] Pengeluaran restok tampil otomatis dan tidak bisa diubah dari layar manual
- [x] Pengeluaran manual: tambah/ubah/hapus, kategori dapat dikelola
- [x] Grafik terbaca di mode gelap dan memiliki alternatif teks
- [x] Empty state bila periode tanpa data

---

## Tahap 8. Cadangan dan Pulihkan

**Tujuan:** data aman; pemulihan tidak pernah merusak data lama.

### Langkah
1. `BackupManager.createBackup(uri)`: checkpoint WAL, salin `arn.db` ke berkas sementara, kumpulkan `images/`, tulis `settings.json`, hitung SHA-256 setiap berkas, tulis `manifest.json` (versi aplikasi, versi skema, waktu, jumlah baris per tabel, checksum), zip menjadi `.arnbak`, **verifikasi dengan membuka ulang**, lalu salin ke lokasi pilihan lewat SAF.
2. `RestoreManager`: validasi berurutan (arsip dan manifest valid, checksum cocok, versi skema berkas <= versi aplikasi), ringkasan isi, konfirmasi penggantian data, **cadangan pengaman otomatis** di penyimpanan internal, penggantian atomik (tulis ke direktori sementara lalu tukar), mulai ulang aplikasi; bila gagal, kembalikan dari cadangan pengaman.
3. Layar **Cadangan dan Pulihkan:** waktu cadangan terakhir, tombol Buat cadangan, Pulihkan data, peringatan bahwa berkas tidak dienkripsi dan berisi data penjualan.
4. Pengingat cadangan: WorkManager periodik harian memeriksa `terakhir cadangan > N hari` lalu membuat notifikasi.
5. Manifest `allowBackup=false` dan `data_extraction_rules` mengecualikan seluruh data.

Checklist
- [x] Berkas `.arnbak` berisi `manifest.json`, `arn.db`, `images/`, `settings.json`
- [x] Cadangan diverifikasi sebelum disalin ke lokasi pilihan
- [x] Restore menolak berkas bukan arsip, manifest rusak, checksum tidak cocok (diubah satu bita)
- [x] Restore menolak cadangan dari skema lebih baru dengan pesan memperbarui aplikasi
- [x] Ringkasan dan konfirmasi sebelum mengganti data
- [x] Cadangan pengaman dibuat otomatis sebelum mengganti
- [~] Kegagalan di tengah penggantian mengembalikan data lama
- [~] Cadangan lalu pulihkan di ponsel lain menghasilkan jumlah baris yang sama (uji perangkat nyata)
- [x] Uji unit: manifest, checksum, penolakan versi, pembuatan dan pembacaan arsip
- [x] Pengingat cadangan terjadwal terdaftar

---

## Tahap 9. Notifikasi, Tema, Bahasa, Profil, Tentang

**Tujuan:** melengkapi menu burger sesuai bagian 6.3.

### Langkah
1. **Notifikasi:** daftar riwayat (terbaru di atas, penanda dibaca), tandai semua dibaca, pengaturan aktif/nonaktif per jenis, minta izin `POST_NOTIFICATIONS` saat pertama diaktifkan; notifikasi sistem untuk stok menipis/habis dan pengingat cadangan; kanal notifikasi.
2. **Tema:** Terang, Gelap, Ikuti Sistem (bawaan), opsi *dynamic color*; berlaku seketika.
3. **Bahasa:** Indonesia (bawaan) dan Inggris, berlaku seketika; format angka dan tanggal mengikuti bahasa.
4. **Profil usaha:** nama, logo, alamat, telepon, mata uang; dipakai pada struk dan Tentang.
5. **Tentang:** logo, nama, versi, versi skema database, lisensi pustaka dan huruf, pernyataan data hanya di perangkat.
6. Panduan awal saat data kosong: 1) tambah bahan, 2) buat kategori, 3) buat menu dan resep, 4) mulai berjualan.
7. Indikator "terakhir dicadangkan" di laci samping.

Checklist
- [x] Notifikasi stok dibuat sekali per bahan per kondisi sampai stok pulih di atas batas
- [~] Pengaturan per jenis memengaruhi pembuatan notifikasi sistem
- [~] Izin Android 13+ diminta dan penolakan ditangani
- [x] Tema berganti seketika dan tersimpan
- [x] Bahasa berganti seketika dan tersimpan
- [x] Profil usaha tersimpan dan muncul di struk
- [x] Tentang menampilkan versi aplikasi dan versi skema
- [x] Panduan awal muncul hanya saat data kosong
- [x] Indikator terakhir cadangan di mode Lainnya (semula laci samping)

---

## Tahap 10. Penyempurnaan, Pengujian, dan Rilis

**Tujuan:** kualitas rilis v1.0.

### Langkah
1. Aksesibilitas: `contentDescription`, urutan fokus, target 48 dp, uji dengan huruf besar 200%.
2. Kinerja: daftar memakai `LazyColumn/LazyVerticalGrid` dengan kunci, kueri terindeks, gambar dikecilkan, uji dengan 50 ribu transaksi sintetis (alat pembangkit data uji debug).
3. Keamanan: tanpa `INTERNET`, tanpa log data sensitif, pengaturan R8 rilis.
4. Uji otomatis: uji unit domain dan use case; uji instrumentasi migrasi dan restore (dijalankan di perangkat/emulator).
5. Uji manual perangkat nyata (Android 8, 13, terbaru): cadangan/restore, notifikasi, izin, pemilih foto, rotasi dikunci.
6. Bangun APK rilis, periksa ukuran (< 25 MB), tanda tangan.

Checklist
- [x] Semua layar memiliki deskripsi aksesibilitas pada ikon aksi
- [~] Teks tidak terpotong pada huruf sistem besar (diperiksa tata letak adaptif)
- [x] Daftar menu dan laporan memakai kueri agregat/terindeks
- [x] Build rilis dengan R8 berhasil
- [~] Uji 50 ribu transaksi di perangkat kelas menengah
- [~] Uji Android 8, 13, dan terbaru di perangkat nyata
- [~] Waktu buka ke Halaman Utama <= 2 detik di perangkat nyata
- [x] Ukuran APK rilis < 25 MB
- [ ] Logo dan warna resmi dipasang (menunggu aset)

---

## Tahap 11. Data Contoh dan Reset (Tambahan di Luar PRD)

**Tujuan:** pemilik atau penguji dapat mencoba semua fitur tanpa mengetik data satu per satu, dan dapat mengembalikan aplikasi ke keadaan kosong.

Berkas: `app/src/main/assets/data_contoh/data_contoh.json` (dibangkitkan oleh `tools/generate_data_contoh.py`), `data/dummy/DummyDataLoader.kt`. Format berkas dijelaskan di `docs/DATA_CONTOH.md`.

Checklist
- [x] Berkas JSON data contoh: 11 bahan (takaran kustom, stok awal), 3 kategori, 12 menu (termasuk menu nonaktif, tanpa kategori, dan titipan tanpa resep), 14 hari kejadian
- [x] Pembangkit data deterministik dengan simulasi stok (tidak ada penjualan yang melanggar kebijakan blokir)
- [x] Pemuat memakai repositori asli (restok, penjualan atomik, pembatalan, opname) dengan jam sendiri, sehingga stok, HPP, pengeluaran, pergerakan stok, dan notifikasi terbentuk seperti pemakaian nyata
- [x] Pemuat menolak berjalan bila data tidak kosong dan menolak versi berkas yang tidak dikenal
- [x] Tombol "Coba dengan data contoh" di panduan awal Halaman Utama dan di layar Cadangan dan pulihkan
- [x] "Hapus semua data" dengan konfirmasi, salinan pengaman otomatis, lalu mulai ulang
- [x] Uji instrumentasi: jumlah bahan/menu/transaksi/batal/pengeluaran, stok tidak negatif, laporan terisi, nomor harian berurutan, penolakan saat data sudah ada
- [x] Diverifikasi di emulator: muat data contoh, laporan, daftar bahan, riwayat notifikasi, reset lalu muat ulang

---

## Audit Kelengkapan Fitur terhadap PRD v1.0 (MVP)

| Fitur PRD | Status |
|---|---|
| Splash, Halaman Utama, kategori, grid menu, keranjang, pembayaran, struk | Ada |
| Kategori dan menu (foto, resep, HPP, duplikat, hapus lunak) | Ada; urutan kategori memakai tombol naik/turun, bukan seret |
| Bahan baku (takaran kustom, restok, opname, riwayat, arsip) | Ada |
| Pengurangan stok atomik, void dari snapshot, kebijakan blokir/peringatkan | Ada, teruji |
| Info pemasukan dan pengeluaran, grafik, laba kotor/bersih, arus kas | Ada |
| Cadangan dan pulihkan manual dengan checksum dan salinan pengaman | Ada, teruji |
| Notifikasi (riwayat, pengaturan per jenis, izin Android 13+) | Ada |
| Tema, bahasa, profil usaha, tentang | Ada |
| Ekspor CSV ringkasan pemasukan | **Belum** (di PRD: usulan prioritas sedang, jadwal v1.1) |
| Tautan umpan balik di Tentang | **Belum** (alamat tujuan belum diketahui) |
| Laporan selisih opname tersendiri | **Belum** (selisih tercatat di riwayat stok dan sebagai susut) |
| Uji migrasi skema | **Belum** (skema masih versi 1, belum ada migrasi) |
| Cadangan terjadwal, enkripsi, struk Bluetooth, kunci aplikasi, tablet, impor CSV, varian | Di luar v1.0 sesuai PRD |

---

## Tahap 12. Navigasi Empat Mode dan Tata Letak Adaptif (Penyempurnaan Setelah Tinjauan)

**Tujuan:** pengguna selalu tahu sedang berada di mode apa, fitur yang jarang dipakai tidak mengganggu kasir, dan aplikasi nyaman di ponsel (potret dan lanskap) maupun tablet.

Struktur baru (menggantikan laci samping/burger di PRD bagian 5). Peta lengkap: `docs/NAVIGASI_DAN_ADAPTIF.md`.

| Mode | Isi | Alasan |
|---|---|---|
| **Kasir** | Pencarian, kategori, menu, keranjang, pembayaran | Dipakai terus-menerus; tidak ada hal lain di jalur ini |
| **Kelola** | Tab Menu, Bahan baku, Kategori | Semua yang membentuk katalog di satu tempat; tombol tambah mengikuti tab |
| **Laporan** | Tab Pemasukan dan Pengeluaran dengan satu pemilih periode | Berpindah tab tidak mengubah rentang tanggal; laba, arus kas, dan riwayat transaksi berdampingan |
| **Lainnya** | Profil usaha, Notifikasi, Cadangan dan pulihkan, Tema, Bahasa, Tentang | Pengaturan dan pemeliharaan data, jauh dari kasir |

Penanda kecil di tab: titik di **Kelola** bila ada bahan menipis atau habis, titik di **Lainnya** bila ada notifikasi belum dibaca. Lonceng notifikasi tetap ada di bilah atas Kasir.

Tata letak adaptif
- Ponsel potret: bilah navigasi bawah; keranjang berupa bilah melayang dan lembar bawah.
- Tablet dan ponsel lanskap (lebar jendela 700 dp ke atas): rail navigasi di kiri; Kasir menjadi dua panel (menu di kiri, keranjang tetap di kanan); pembayaran dua kolom.
- Layar pendek (ponsel lanskap): judul dan tab digabung dalam satu baris.
- Daftar dan kartu laporan memakai grid adaptif (satu kolom di ponsel, dua atau lebih di tablet); layar formulir dan detail dibatasi lebar 640 dp dan ditengahkan.

Checklist
- [x] Empat mode utama dengan bilah bawah (ponsel) dan rail (lebar), status tab tersimpan saat berpindah
- [x] Kelola: tab Menu, Bahan baku, Kategori dalam satu layar, FAB mengikuti tab
- [x] Laporan: Pemasukan dan Pengeluaran berbagi pemilih periode
- [x] Lainnya: profil, notifikasi, cadangan, tema, bahasa, tentang
- [x] Penanda tab untuk stok perlu perhatian dan notifikasi belum dibaca
- [x] Kunci potret dicabut; potret dan lanskap berfungsi (status tersimpan saat rotasi, keranjang tidak hilang)
- [x] Kasir dua panel dengan keranjang tetap pada lebar 700 dp ke atas
- [x] Pembayaran dua kolom pada layar lebar
- [x] Kepala layar ringkas pada layar pendek
- [x] Grid adaptif untuk daftar menu, bahan, kategori, dan kartu laporan
- [x] Diverifikasi di emulator: ponsel potret, ponsel lanskap, tablet potret, tablet lanskap
- [~] Tablet fisik dan ponsel lipat (ukuran jendela berubah saat dilipat) belum diuji

### Audit bug dan kebocoran memori

Alat: StrictMode (debug), LeakCanary (hanya build debug), uji monkey 2 x 1.500 kejadian acak dengan rotasi layar bergantian, `dumpsys meminfo`, Android Lint.

- [x] Tidak ada crash atau ANR dalam dua kali uji monkey; satu Activity tersisa, tumpukan Java stabil sekitar 17 MB
- [x] LeakCanary tidak melaporkan kebocoran Activity maupun ViewModel
- [x] **Temuan dan perbaikan:** "Bagikan gambar" menggambar bitmap, mengompres PNG, dan menulis berkas di thread utama (StrictMode) dan tidak mendaur ulang bitmap; dipindah ke thread IO dan bitmap didaur ulang
- [x] **Temuan dan perbaikan (serius):** `BigInteger.TWO` (API 33) dan `longValueExact` (API 31) dipakai di perhitungan HPP padahal minSdk 26; di Android 8 sampai 12 aplikasi akan crash saat menjual. Terdeteksi Lint, tidak terlihat di emulator Android 15. Diganti dengan padanan yang jalan di API 26
- [x] **Temuan dan perbaikan:** Lint melaporkan 21 galat `getString` lewat `LocalContext` di callback; diganti `LocalResources`
- [x] Dua variabel yang ditulis dari aliran data dan dibaca dari thread utama ditandai `@Volatile`
- [x] Pembersihan Lint: sumber daya tak terpakai, label ganda di manifest, pengecekan SDK_INT usang, folder `mipmap-anydpi-v26`, pemisahan APK per bahasa dimatikan (bahasa dipilih di dalam aplikasi)
- [x] Lint: 0 galat. Sisa peringatan hanya pembaruan versi pustaka, saran KTX, dan saran kata jamak
- [~] Pengujian di perangkat Android 8 sampai 12 sungguhan tetap disarankan karena emulator uji memakai Android 15

---

## Tahap 13. Penyegaran Desain dan Verifikasi Terang/Gelap

**Tujuan:** tampilan lebih modern dan rapi, tanpa kesan "template AI", konsisten di tema terang, gelap, dan warna dinamis, dan tidak ada komponen yang terpotong.

Prinsip desain
- Kartu tonal dengan sudut 12/18/24/32 dp; tidak ada bayangan berlebihan, tidak ada gradien dekoratif.
- Hierarki huruf lebih tegas (Plus Jakarta Sans): judul tebal, angka uang besar dan rapat, label kecil redup.
- Warna aksen terra-cotta untuk aksi utama; warna ringkasan memakai wadah warna skema (`primaryContainer`, `secondaryContainer`, `errorContainer`) sehingga otomatis benar di terang, gelap, dan warna dinamis.
- Warna monogram menu diturunkan dari skema aktif (bukan warna tetap), jadi ikut warna dinamis dan kontras aman.
- Stepper jumlah dipakai bersama di kartu menu dan keranjang (`QuantityStepper`); ikon aksi tonal (`TonalIconAction`).
- Angka uang memakai `MoneyText(shrinkToFit)`; tinggi tetap yang bisa memotong teks diganti `heightIn(min = ...)`.

Checklist
- [x] Sistem desain baru: bentuk, tipografi, kartu tonal, chip tonal, tombol sekunder tonal
- [x] Kasir: kartu menu baru dengan stepper, bilah keranjang melayang, lembar keranjang dan panel keranjang
- [x] Laporan: kartu ringkasan utama berwarna (Penjualan bersih, Laba bersih), ubin statistik, grafik berwarna skema
- [x] Lainnya: ikon dalam ubin tonal
- [x] Label bilah navigasi dibatasi pembesaran 1,3x agar tidak terpotong
- [x] Diverifikasi di emulator: terang, gelap, warna dinamis terang dan gelap (potret)
- [x] Diverifikasi: ukuran huruf sistem 2,0 (gelap), bahasa Inggris, ponsel lanskap, tablet potret
- [x] **Temuan dan perbaikan:** teks "2 x" di ringkasan pembayaran terpecah baris pada huruf besar; sekarang tidak boleh membungkus
- [x] **Temuan dan perbaikan:** subjudul "Cadangan dan pulihkan" terpotong "..." pada huruf besar; sekarang boleh dua baris
- [x] **Temuan dan perbaikan:** harga di kartu menu yang sudah di keranjang terpotong satu digit di kartu sempit (lanskap) dan mengecil berlebihan pada huruf 2,0; harga dan stepper kini disusun bertumpuk saat lebar kartu kurang dari 180 dp atau huruf lebih dari 1,3x
- [x] Uji unit 37 lulus, uji instrumented 16 lulus, Lint 0 galat
- [~] Belum diuji: tablet lanskap pada tema gelap, ukuran huruf 2,0 di layar lanskap/tablet, perangkat fisik

---

## Tahap 14. Sambutan, Profil Awal, Panduan Fitur, dan Tutorial

**Tujuan:** pengguna baru langsung tahu apa fitur aplikasi dan cara memakainya, dan profil toko terisi sejak awal.

Alur saat aplikasi pertama kali dipasang (tahap disimpan di DataStore: 0 sambutan, 1 tutorial, 2 selesai)
1. **Sambutan** minimalis: wordmark, satu judul, satu kalimat, pilihan bahasa ID/EN; teks muncul bertahap.
2. **Nama toko** (satu pertanyaan per layar): monogram beranimasi di atas kolom, huruf awal bergulir saat diketik. Nama wajib, atau Isi nanti.
3. **Logo atau foto toko** (opsional, bisa Lewati).
4. **Alamat dan telepon** (opsional) lalu profil tersimpan.
5. **Lima penjelasan fitur**, satu per layar (judul, satu kalimat, tiga langkah). Satu garis kemajuan tipis di atas.
6. **Tutorial sorot** di layar asli. Langkah yang bagiannya belum ada dilewati. Berakhir di Lainnya dengan menyorot baris Panduan penggunaan.

Setiap halaman punya adegan bertokoh yang berbeda dan bergerak mengulang (digambar dengan Canvas dari warna tema, tanpa berkas gambar): kedai dengan pemilik yang melambai (sambutan), papan nama berlampu yang tulisannya mengikuti ketikan, tokoh memotret dengan kilat kamera dan foto pilihan tampil di bingkai, tokoh menelepon dengan peta, transaksi pembeli dan penjual dengan struk keluar, koki mengaduk panci, penjaga gudang dengan rak toples, tokoh menunjuk grafik, dan lemari arsip dengan perisai. Teks Indonesia ditulis dengan kalimat yang jelas, tidak kaku dan tidak terlalu santai.

Lewati di tahap mana pun tersimpan; pengenalan tidak muncul lagi saat aplikasi dibuka.

**Panduan penggunaan** (Lainnya): kartu Tutorial interaktif, lalu 11 tutorial per fitur langsung terlihat dan dikelompokkan (Berjualan, Kelola usaha, Laporan, Data dan pengaturan). Mengetuk satu topik langsung memulai **tutorial sorot langkah demi langkah di layar asli** (54 langkah): layar digelapkan, tombol atau kolom yang dimaksud disorot, dan kartu penjelasan memberi tahu apa yang ditekan atau diisi. Tutorial berpindah layar sendiri (misalnya dari Kelola ke form menu baru), menggulir daftar ke bagian yang disorot, dan di akhir kembali ke Panduan. Bagian yang bergantung pada data (misalnya kartu menu saat belum ada menu) tampil sebagai kartu penjelasan di tengah. Langkah ditulis di `tools/guide_tours.py`; `tools/gen_guide.py` membangkitkan string dan `GuideTopics.kt`.

Checklist
- [x] Sambutan tampil hanya saat pertama kali; Lewati ada di setiap tahap
- [x] Profil memakai komponen yang sama dengan layar Profil (`ProfileFields`)
- [x] Tutorial sorot lintas tab, dilanjutkan bila aplikasi ditutup di tengah jalan
- [x] Panduan: daftar topik langsung tampil, detail per topik, dapat diulang kapan saja
- [x] Teks Indonesia dan Inggris
- [x] **Temuan dan perbaikan:** tab Lainnya memulihkan layar terakhirnya (daftar Panduan) sehingga tutorial salah mendarat; pindah tab untuk tutorial kini tanpa memulihkan layar turunan
- [x] Diverifikasi di emulator (potret, data kosong): alur lengkap, Lewati, ulangi dari Panduan
- [~] Belum diuji: tutorial dengan data terisi (langkah menu), tablet, lanskap, huruf sistem 2,0

---

## Di Luar Cakupan v1.0 (Sesuai PRD 14)

Cadangan terjadwal ke folder pilihan, enkripsi cadangan, ekspor CSV, cetak struk Bluetooth, kunci aplikasi PIN/biometrik, tata letak tablet/lanskap, impor CSV, varian menu.

## Urutan Ketergantungan

```
Tahap 1 -> Tahap 2 -> Tahap 3 -> Tahap 4 -> Tahap 5 -> Tahap 6 -> Tahap 7 -> Tahap 8 -> Tahap 9 -> Tahap 10
                         \________ uji domain menjaga seluruh tahap setelahnya ________/
```
