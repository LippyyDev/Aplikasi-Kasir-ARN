# PRD: Aplikasi Kasir ARN

| Atribut | Isi |
|---|---|
| Nama produk | ARN Kasir |
| Platform | Android (Flutter, Dart) |
| Penyimpanan data | Lokal di memori ponsel (tanpa server) |
| Versi dokumen | 0.2 (tumpukan teknologi diganti dari Kotlin native ke Flutter) |
| Tanggal | 17 Oktober 2026 |
| Status | Menunggu konfirmasi pertanyaan terbuka (bagian 15) |

---

## 1. Ringkasan

ARN Kasir adalah aplikasi kasir untuk usaha makanan atau minuman skala kecil. Seluruh data tersimpan di ponsel dan aplikasi berjalan sepenuhnya *offline*. Pembeda utamanya adalah **manajemen bahan baku berbasis takaran**: setiap menu memiliki resep (bahan dan takarannya), dan setiap penjualan otomatis mengurangi stok bahan baku sesuai takaran tersebut. Dari situ aplikasi dapat menghitung modal (HPP), laba kotor, dan memberi peringatan sebelum bahan habis.

## 2. Tujuan dan Metrik Keberhasilan

### 2.1 Tujuan

1. Mencatat penjualan dengan cepat (target 3 ketukan atau kurang untuk menambah satu menu ke keranjang).
2. Stok bahan baku berkurang otomatis berdasarkan takaran resep.
3. Pemilik dapat melihat pemasukan, pengeluaran, dan laba kapan saja.
4. Data aman dari kehilangan melalui fitur cadangan (*backup*) dan pemulihan (*restore*).

### 2.2 Metrik

| Metrik | Target |
|---|---|
| Waktu dari ikon diketuk sampai Halaman Utama tampil | 2 detik atau kurang pada ponsel kelas menengah |
| Waktu menyelesaikan satu transaksi (3 item) | 15 detik atau kurang |
| Selisih stok sistem dengan stok fisik setelah satu minggu pemakaian | Dapat diukur lewat fitur penyesuaian stok (lihat 7.4) |
| Restore berhasil mengembalikan seluruh data | 100% pada uji pencocokan jumlah baris |

### 2.3 Di Luar Lingkup (Versi 1)

* Sinkronisasi *cloud* dan multiperangkat.
* Banyak akun kasir atau hak akses bertingkat.
* Integrasi pembayaran digital (QRIS dinamis, EDC).
* Cetak struk lewat printer *Bluetooth* (dimasukkan ke versi 1.1, lihat bagian 14).
* Versi iOS. Flutter memungkinkannya di masa depan, tetapi cadangan, pemilih berkas, dan notifikasi terjadwal perlu ditinjau ulang.

## 3. Pengguna

| Peran | Kebutuhan |
|---|---|
| Pemilik sekaligus kasir (utama) | Input transaksi cepat, pantau stok, lihat laba, kelola menu dan resep |
| Karyawan (sekunder) | Hanya input transaksi pada perangkat yang sama |

Versi 1 mengasumsikan **satu perangkat dan satu pengguna aktif**.

## 4. Stack Teknis (Usulan)

| Lapisan | Pilihan | Alasan |
|---|---|---|
| Kerangka kerja | Flutter (kanal stabil), Dart; target Android saja pada v1 | Sesuai permintaan; satu basis kode, dan iOS terbuka untuk masa depan |
| UI | Flutter dengan Material 3 | Tema terang/gelap; *dynamic color* lewat `dynamic_color` |
| Arsitektur | Berlapis (`domain`, `data`, `ui`), Riverpod, `go_router` | Logika inti di Dart murni sehingga mudah diuji tanpa emulator |
| *Database* | Drift di atas SQLite (`sqlite3_flutter_libs`) | Kueri bertipe, migrasi terkelola dan dapat diuji, mendukung *transaction* atomik |
| Pengaturan | `shared_preferences` | Tema, bahasa, status notifikasi |
| Asinkron | `Future` dan `Stream` Dart | Layar reaktif terhadap perubahan data (kueri `watch` Drift) |
| Gambar menu | `image_picker`, `flutter_image_compress`, `path_provider`; berkas di penyimpanan internal aplikasi | Tanpa izin penyimpanan luas |
| Cadangan | `archive` (ZIP), `crypto` (SHA 256), `file_picker` (pemilih lokasi sistem) | Pengguna memilih lokasi; perilaku pada Android perlu diverifikasi di perangkat nyata |
| Notifikasi dan jadwal | `flutter_local_notifications` dan `workmanager` | Peringatan stok dan pengingat cadangan |
| *Splash screen* | `flutter_native_splash` | *Splash* native tanpa kedipan putih, ditahan selama inisialisasi |
| Lokalisasi | `flutter_localizations` dan `intl` (berkas ARB) | Indonesia dan Inggris |
| Grafik | `fl_chart` | Grafik laporan |
| Versi Android | minSdk 26, targetSdk terbaru | Jangkauan perangkat luas |

Daftar paket adalah usulan; versi dikunci saat proyek dibuat. Dart tidak punya `BigDecimal` bawaan, sehingga konversi takaran memakai aritmetika pecahan bilangan bulat buatan sendiri di lapisan `domain` (lihat 7.1).

## 5. Alur Navigasi

```
Ikon ARN diketuk
   -> Splash Screen (logo ARN)
   -> Halaman Utama
        |- Top Bar: [Burger] [Pencarian menu] [Profil]
        |- Baris Kategori (geser horizontal)
        |- Daftar Menu (grid)
        |- Bilah Keranjang (muncul bila keranjang berisi)
        |
        |- Burger Menu (laci samping)
             |- Notifikasi
             |- Tema
             |- Bahasa
             |- Penyetelan Menu
             |- Bahan Baku
             |- Info Pemasukan
             |- Info Pengeluaran
             |- Cadangan dan Pulihkan (usulan tambahan)
             |- Tentang Aplikasi
```

## 6. Spesifikasi Fitur

### 6.1 *Splash Screen*

* Menampilkan logo ARN di tengah layar dengan latar sesuai tema.
* Langsung masuk ke Halaman Utama setelah aplikasi siap. Tidak ada *timer* tetap; layar ditahan hanya selama inisialisasi *database* dan pengaturan.
* Aplikasi tidak memiliki layar *login* pada versi 1.

**Kriteria penerimaan**
* *Splash* native dibuat dengan `flutter_native_splash` (termasuk perilaku Android 12 ke atas), ditahan selama inisialisasi lalu dilepas sebelum layar Flutter pertama tampil.
* Tidak ada layar putih sebelum logo tampil.

### 6.2 Halaman Utama

Terdiri dari tiga bagian sesuai permintaan, ditambah keranjang karena ini aplikasi kasir.

**A. *Top Bar***

| Elemen | Perilaku |
|---|---|
| Ikon burger | Membuka laci samping (6.3) |
| Kolom pencarian | Mencari menu berdasarkan nama, hasil tampil langsung saat mengetik, mengabaikan huruf besar/kecil. Pencarian menyaring daftar menu pada layar yang sama |
| Ikon profil | Membuka halaman profil usaha (lihat pertanyaan terbuka no. 4): nama usaha, logo, alamat, nomor telepon, dan mata uang. Data ini dipakai pada struk dan tentang aplikasi |

**B. Kategori Menu**

* Baris *chip* yang dapat digeser horizontal.
* Isi berasal dari kategori yang dibuat pengguna di Penyetelan Menu.
* Selalu ada *chip* "Semua" di posisi pertama.
* Bila belum ada kategori: tampil *empty state* dengan tombol menuju Penyetelan Menu.

**C. Menu**

* Tampil sebagai kartu (foto, nama, harga) dalam *grid* 2 kolom pada ponsel.
* Hanya menampilkan menu yang sudah ditempatkan pada suatu kategori dan berstatus aktif.
* Menu yang bahannya tidak cukup untuk satu porsi tampil redup dengan label "Stok habis" dan tidak dapat ditambahkan ke keranjang (perilaku dapat diubah, lihat 7.3).
* Ketuk kartu: tambah 1 ke keranjang. Tahan atau ketuk ikon catatan: tambah catatan per item.

**D. Keranjang dan Pembayaran (tambahan, dibutuhkan agar kasir berfungsi)**

* Bilah keranjang di bawah menampilkan jumlah item dan total.
* Lembar keranjang: ubah jumlah, hapus item, catatan, diskon (nominal atau persen) pada tingkat transaksi.
* Pembayaran: tunai (hitung kembalian otomatis) dan non tunai (dicatat saja sebagai metode, tanpa integrasi).
* Setelah dibayar: transaksi disimpan, stok bahan dikurangi dalam satu *transaction* database yang atomik, tampil layar sukses dan opsi bagikan struk sebagai gambar atau teks.

### 6.3 Burger Menu

#### 6.3.1 Notifikasi

* Daftar riwayat notifikasi di dalam aplikasi (tabel `notifikasi`), terbaru di atas, dengan penanda sudah/belum dibaca.
* Jenis notifikasi:
  1. Bahan hampir habis (stok kurang dari atau sama dengan batas minimum).
  2. Bahan habis.
  3. Pengingat cadangan (bila terakhir *backup* lebih dari N hari, bawaan 7).
  4. Hasil cadangan atau pemulihan (berhasil/gagal).
* Pengaturan: aktif/nonaktif per jenis, serta izin notifikasi sistem (Android 13 ke atas meminta izin `POST_NOTIFICATIONS` saat pertama kali pengguna mengaktifkan, diminta lewat `flutter_local_notifications`).
* Notifikasi stok dibuat saat transaksi disimpan, dan hanya sekali per bahan per kondisi (tidak berulang pada setiap transaksi sampai stok dipulihkan di atas batas).

#### 6.3.2 Tema

* Pilihan: Terang, Gelap, Ikuti Sistem (bawaan).
* Opsi *dynamic color* (Android 12 ke atas), bawaan nonaktif agar warna merek ARN konsisten.
* Berlaku seketika tanpa memulai ulang aplikasi. Disimpan di `shared_preferences`.

#### 6.3.3 Bahasa

* Bahasa awal: Indonesia (bawaan) dan Inggris.
* Bahasa dipilih di dalam aplikasi (`locale` pada `MaterialApp`) dan disimpan di `shared_preferences`; pengaturan bahasa per aplikasi milik sistem tidak dipakai pada v1.
* Seluruh teks di berkas ARB (`lib/l10n/`), dihasilkan lewat `flutter gen-l10n`; tidak ada teks yang ditulis langsung di kode.
* Format angka dan tanggal mengikuti bahasa terpilih; mata uang tetap Rupiah kecuali diubah di profil.

#### 6.3.4 Penyetelan Menu

Pusat pengelolaan data penjualan, terdiri dari tiga sub bagian.

**a. Kategori**
* Tambah, ubah nama, urutkan (seret), hapus.
* Menghapus kategori yang masih berisi menu: tampil peringatan dan pilihan memindahkan menu ke kategori lain atau menjadikannya "Tanpa kategori" (tidak tampil di Halaman Utama).
* Nama kategori unik.

**b. Menu**
* Bidang: nama (wajib), foto (opsional, dikompres otomatis), kategori, harga jual (wajib, bilangan bulat Rupiah), status aktif, catatan.
* **Resep**: daftar bahan baku beserta jumlah takaran per satu porsi (lihat bagian 8). Resep boleh kosong untuk menu tanpa bahan terlacak (misalnya produk titipan), dan menu tersebut tidak pernah terblokir oleh stok.
* Layar menampilkan **HPP per porsi** dan **margin** secara langsung saat resep diubah.
* Duplikat menu (untuk varian), hapus (lihat aturan 11).

**c. Pengaturan Takaran Kustom** (ditautkan dari Bahan Baku, lihat 6.3.5)

#### 6.3.5 Bahan Baku

* Daftar bahan: nama, stok saat ini (dalam satuan tampil pilihan), indikator warna (aman, menipis, habis), nilai stok.
* Tambah/ubah bahan:

| Bidang | Keterangan |
|---|---|
| Nama | Wajib, unik |
| Dimensi | Massa, Volume, atau Hitungan. Menentukan **satuan dasar** (gram, mililiter, buah) |
| Satuan beli | Satuan saat belanja, misalnya kg, liter, karton |
| Takaran kustom | Daftar satuan buatan sendiri dengan faktor konversi ke satuan dasar, misalnya "1 sendok makan = 12 g", "1 cup = 240 ml", "1 sachet = 25 g" |
| Stok minimum | Batas peringatan, dalam satuan dasar |
| Stok awal | Opsional |

* **Restok (pembelian)**: pilih bahan, jumlah, satuan, total harga. Menambah stok, memperbarui nilai stok (rata rata tertimbang), dan otomatis tercatat sebagai pengeluaran kategori "Bahan baku" (6.3.7).
* **Penyesuaian stok (opname)**: masukkan stok fisik; selisih dicatat sebagai pergerakan stok jenis "penyesuaian" beserta alasan (rusak, tumpah, salah hitung). Selisih bernilai kerugian dicatat sebagai pengeluaran jenis "susut" (opsional, dapat dimatikan).
* Riwayat pergerakan stok per bahan (masuk, keluar karena penjualan, penyesuaian) dengan penyaring tanggal.

#### 6.3.6 Info Pemasukan

* Ringkasan berdasarkan periode: Hari ini, 7 hari, Bulan ini, Rentang kustom.
* Kartu ringkasan: total penjualan kotor, diskon, penjualan bersih, jumlah transaksi, rata rata per transaksi, **HPP**, **laba kotor**.
* Grafik: tren per hari/jam, menu terlaris (jumlah dan pendapatan), pembagian per kategori dan per metode bayar.
* Daftar transaksi dapat diketuk untuk melihat rincian. Pada rincian tersedia **Batalkan transaksi** (*void*) yang mengembalikan stok dan menandai transaksi dibatalkan (tidak dihapus, demi jejak audit).
* Ekspor ringkasan ke CSV lewat pemilih lokasi sistem (`file_picker`) (usulan, prioritas sedang).

#### 6.3.7 Info Pengeluaran

* Sumber pengeluaran:
  1. Otomatis dari restok bahan baku.
  2. Manual: sewa, listrik, gaji, kemasan, lain lain, dengan kategori pengeluaran yang dapat dikelola pengguna.
* Bidang input manual: tanggal, kategori, nominal, catatan.
* Ringkasan dan grafik berdasarkan periode dan kategori, sama seperti pemasukan.
* Tampilan **Laba bersih** = penjualan bersih dikurangi HPP dikurangi pengeluaran operasional (tidak menghitung ganda pembelian bahan; lihat 9.3).

#### 6.3.8 Cadangan dan Pulihkan

Diminta pengguna; ditempatkan sebagai item tersendiri di burger menu. Rincian pada bagian 10.

#### 6.3.9 Tentang Aplikasi

* Logo, nama, versi (nama dan kode versi), nomor versi skema database.
* Informasi pengembang, lisensi pustaka pihak ketiga, tautan umpan balik.
* Pernyataan: seluruh data tersimpan hanya di perangkat ini.

## 7. Aturan Bisnis Inti Berbasis Takaran

### 7.1 Satuan Dasar dan Konversi

* Setiap bahan memiliki satu **satuan dasar** menurut dimensinya: gram (massa), mililiter (volume), buah (hitungan).
* Semua stok dan semua resep disimpan sebagai **bilangan bulat dalam satuan dasar** (tipe `int` Dart, 64 bit pada Android). Ini menghindari galat pembulatan bilangan pecahan yang akan menumpuk pada ribuan transaksi. Dilarang memakai `double` untuk stok, resep, atau uang.
* Satuan lain (kg, liter, sendok makan, cup) hanya lapisan tampilan dan input: nilai dikalikan faktor konversi lalu dibulatkan ke satuan dasar saat disimpan. Faktor disimpan sebagai pecahan bilangan bulat (2,5 g = 5/2), input teks pengguna ("2,5" atau "2.5") diparse persis menjadi pecahan, dan pembulatan memakai *half up*.
* Konversi **antardimensi tidak otomatis**. Gula dalam "sendok makan" membutuhkan faktor yang ditentukan pengguna per bahan karena massa jenis berbeda tiap bahan.

### 7.2 Resep dan Pengurangan Stok

* Resep menu = daftar `(bahan, jumlah dalam satuan dasar)` untuk **1 porsi**.
* Saat transaksi disimpan: untuk setiap item, `pengurangan bahan = jumlah resep x kuantitas item`. Seluruh pengurangan, penyimpanan transaksi, dan pencatatan pergerakan stok berjalan dalam **satu transaksi database** (semua berhasil atau semua dibatalkan).
* Saat transaksi dibatalkan (*void*): pengurangan dibalik memakai **resep saat transaksi terjadi** (tersimpan sebagai *snapshot*), bukan resep terbaru.

### 7.3 Ketersediaan Menu

* Porsi tersedia = nilai minimum dari `floor(stok bahan / jumlah resep)` untuk semua bahan resep.
* Kebijakan saat stok tidak cukup, dapat dipilih di pengaturan:
  1. **Blokir** (bawaan): menu tidak dapat dijual.
  2. **Peringatkan**: tetap dapat dijual, stok dapat menjadi negatif dan ditandai untuk dikoreksi lewat opname.

  Alasan opsi 2 ada: stok sistem sering berbeda dari stok fisik, dan kasir tidak boleh macet karena data stok yang belum diperbarui.

### 7.4 Biaya dan HPP

* Metode: **rata rata tertimbang bergerak**. Tiap bahan menyimpan `jumlah stok` dan `nilai stok` (Rupiah, `int`). Saat restok: nilai dan jumlah ditambahkan. Saat pemakaian: nilai berkurang proporsional terhadap rata rata saat itu.
* HPP per porsi menu = jumlah biaya semua bahan resep pada saat transaksi. Nilai ini **disimpan pada baris transaksi** agar laporan laba tidak berubah bila harga bahan kemudian naik atau turun.
* Menu dengan bahan yang belum pernah dibeli (rata rata belum ada) ditandai "HPP belum lengkap".

## 8. Model Data (Drift/SQLite)

Seluruh uang disimpan sebagai `int` (INTEGER 64 bit) dalam Rupiah (tanpa desimal). Seluruh waktu disimpan sebagai `int` *epoch* milidetik UTC dan ditampilkan sesuai zona waktu perangkat.

| Tabel | Kolom utama | Catatan |
|---|---|---|
| `kategori_menu` | id, nama (unik), urutan | |
| `menu` | id, nama, kategori_id (boleh kosong), harga_jual, foto_path, aktif, catatan, dibuat, diubah | Hapus lunak (`dihapus_pada`) |
| `bahan_baku` | id, nama (unik), dimensi, satuan_dasar, satuan_beli, stok_dasar, nilai_stok, stok_minimum, dihapus_pada | |
| `takaran_bahan` | id, bahan_id, nama_satuan, faktor_pembilang, faktor_penyebut | Untuk satuan kustom dan satuan beli. Faktor berupa pecahan bilangan bulat (2,5 g = 5/2), tanpa `double` |
| `resep_item` | id, menu_id, bahan_id, jumlah_dasar | Kunci unik (menu_id, bahan_id) |
| `transaksi` | id, nomor, waktu, subtotal, diskon, total, metode_bayar, dibayar, kembalian, status (selesai/batal), catatan | Nomor urut harian |
| `transaksi_item` | id, transaksi_id, menu_id, nama_menu, harga_satuan, kuantitas, hpp_satuan, catatan | Nama dan harga disalin sebagai *snapshot* |
| `transaksi_bahan` | id, transaksi_item_id, bahan_id, jumlah_dasar, biaya | *Snapshot* pemakaian bahan, dasar pembatalan |
| `pergerakan_stok` | id, bahan_id, waktu, jenis (restok, penjualan, batal, penyesuaian), jumlah_dasar, nilai, referensi_id | Jejak audit |
| `pengeluaran` | id, tanggal, kategori_id, nominal, catatan, sumber (manual/restok/susut), referensi_id | |
| `kategori_pengeluaran` | id, nama | |
| `notifikasi` | id, jenis, judul, isi, waktu, dibaca, referensi_id | |
| `profil_usaha` | id (tetap 1), nama, logo_path, alamat, telepon, mata_uang | |

Pengaturan ringan (tema, bahasa, kebijakan stok, jadwal cadangan, waktu cadangan terakhir) di `shared_preferences`. Bila ingin seluruh pengaturan ikut dalam cadangan, simpan juga ke berkas `settings.json` di dalam arsip (lihat 10.2).

Indeks yang disarankan: `transaksi(waktu)`, `transaksi_item(transaksi_id)`, `pergerakan_stok(bahan_id, waktu)`, `menu(kategori_id)`.

## 9. Aturan Konsistensi dan Kasus Tepi

### 9.1 Penghapusan

| Objek | Aturan |
|---|---|
| Menu yang sudah pernah terjual | Hapus lunak. Hilang dari Halaman Utama, tetap muncul pada laporan lama |
| Bahan yang dipakai resep | Ditolak, dengan daftar menu yang memakainya; pengguna melepas dari resep dahulu atau mengarsipkan bahan |
| Bahan yang sudah punya riwayat | Hanya dapat diarsipkan |
| Kategori | Lihat 6.3.4a |

### 9.2 Perubahan Resep dan Harga

* Perubahan resep atau harga hanya berlaku untuk transaksi berikutnya.
* Perubahan faktor konversi satuan kustom tidak mengubah stok yang sudah tersimpan (tersimpan dalam satuan dasar), hanya cara tampilnya.

### 9.3 Menghindari Perhitungan Ganda

* Pembelian bahan baku muncul sebagai **pengeluaran kas**, sedangkan pemakaiannya muncul sebagai **HPP**. Laporan menyajikan dua pandangan terpisah:
  * **Laba kotor** (akrual sederhana) = penjualan bersih dikurangi HPP.
  * **Arus kas** = pemasukan dikurangi seluruh pengeluaran (termasuk pembelian bahan).
* Laba bersih operasional hanya mengurangkan pengeluaran non bahan dari laba kotor. Label dan penjelasan singkat ditampilkan agar pengguna tidak menjumlahkan keduanya.

### 9.4 Lainnya

* Dua perangkat atau dua proses menulis bersamaan tidak didukung pada versi 1.
* Penyimpanan penuh: tampilkan galat yang jelas dan jangan menyimpan transaksi setengah jadi.
* Transaksi tanpa item tidak dapat disimpan.
* Tanggal perangkat yang diubah mundur dapat mengacaukan laporan; nomor transaksi memakai urutan penyimpanan, bukan waktu.

## 10. Cadangan dan Pulihkan (*Backup* dan *Restore*)

### 10.1 Mengapa Fitur Ini Kritis

Karena data hanya ada di ponsel, hilangnya ponsel, penghapusan aplikasi, atau penyimpanan rusak berarti seluruh riwayat usaha hilang. Cadangan harus dapat disimpan **di luar** penyimpanan internal aplikasi (karena data ini ikut terhapus saat aplikasi dihapus).

### 10.2 Format Berkas Cadangan

Berekstensi `.arnbak`, berupa arsip ZIP berisi:

| Berkas | Isi |
|---|---|
| `manifest.json` | Nama aplikasi, versi aplikasi, **versi skema database**, waktu dibuat, jumlah baris per tabel, *checksum* SHA 256 setiap berkas |
| `arn.db` | Salinan konsisten database (dibuat dengan `VACUUM INTO` atau menutup WAL dengan *checkpoint* terlebih dahulu; **bukan** menyalin berkas yang sedang terbuka) |
| `images/` | Foto menu dan logo |
| `settings.json` | Tema, bahasa, kebijakan stok, jadwal |

### 10.3 Cadangan Manual

1. Pengguna membuka Cadangan dan Pulihkan lalu mengetuk **Buat cadangan**.
2. Sistem membuka pemilih lokasi simpan (`file_picker`; pada Android memakai *Storage Access Framework*, perilaku diverifikasi pada versi paket yang dipakai), nama bawaan `ARN_Backup_<tanggal>_<jam>.arnbak`.
3. Aplikasi membuat arsip di berkas sementara, memverifikasi (membuka ulang dan mencocokkan *checksum*), lalu menyalin ke lokasi pilihan.
4. Hasil ditampilkan, waktu cadangan terakhir diperbarui.

### 10.4 Cadangan Terjadwal (Opsional, Prioritas Sedang)

* Pengguna memilih folder tujuan sekali lewat `OpenDocumentTree`; izin URI dipertahankan (*persistable permission*).
* Jadwal harian atau mingguan melalui `workmanager`; menyimpan paling banyak N berkas terakhir (bawaan 5) dan menghapus yang lebih lama.
* Bila folder tidak lagi dapat diakses, kirim notifikasi dan minta pengguna memilih ulang.

### 10.5 Pulihkan

1. Pengguna mengetuk **Pulihkan data** dan memilih berkas lewat `OpenDocument`.
2. Validasi berurutan; gagal pada salah satu langkah berarti pemulihan dibatalkan tanpa mengubah data yang ada:
   * Berkas arsip valid dan `manifest.json` terbaca.
   * *Checksum* semua berkas cocok.
   * Versi skema di berkas **kurang dari atau sama dengan** versi skema aplikasi (cadangan dari versi lebih baru ditolak dengan pesan meminta pembaruan aplikasi). Bila lebih lama, jalankan migrasi Drift.
3. Tampilkan ringkasan (tanggal cadangan, jumlah menu, bahan, transaksi) dan **peringatan bahwa data saat ini akan diganti**; pengguna harus mengonfirmasi.
4. Aplikasi membuat **cadangan pengaman otomatis** dari data saat ini di penyimpanan internal (disimpan untuk pemulihan darurat).
5. Database dan gambar diganti secara atomik (tulis ke direktori sementara lalu tukar), kemudian aplikasi dimulai ulang.
6. Bila langkah 5 gagal, kembalikan dari cadangan pengaman dan tampilkan galat.

### 10.6 Keamanan Cadangan

* Versi 1: cadangan tidak dienkripsi, dengan peringatan jelas bahwa berkas berisi data penjualan.
* Versi 1.1 (usulan): enkripsi opsional dengan kata sandi dari pengguna (AES GCM, kunci diturunkan dengan PBKDF2 atau Argon2). Catatan: kata sandi yang lupa tidak dapat dipulihkan.
* Nonaktifkan atau atur *Auto Backup* bawaan Android (`allowBackup` dan aturan ekstraksi data) secara eksplisit agar tidak berbenturan dengan mekanisme ini.

### 10.7 Kriteria Penerimaan

* Cadangan lalu pulihkan pada ponsel lain menghasilkan jumlah baris tiap tabel yang sama dan semua gambar tampil.
* Berkas yang diubah satu bita ditolak oleh pemeriksaan *checksum*.
* Memutus proses di tengah pemulihan tidak merusak data lama.

## 11. Persyaratan Non Fungsional

| Aspek | Persyaratan |
|---|---|
| *Offline* | Seluruh fitur berfungsi tanpa internet. Aplikasi tidak meminta izin `INTERNET` pada versi 1 |
| Kinerja | Daftar menu dan laporan memakai paginasi atau *query* teragregasi di SQL; layar tetap lancar dengan 50 ribu transaksi |
| Keandalan | Setiap operasi yang mengubah beberapa tabel memakai satu *transaction* Drift. Migrasi skema memiliki uji otomatis |
| Keamanan dan privasi | Data hanya di perangkat, ruang penyimpanan privat aplikasi. Opsi kunci aplikasi (PIN atau biometrik) dicatat sebagai pertanyaan terbuka |
| Aksesibilitas | Target sentuh minimal 48 dp, kontras sesuai WCAG AA, dukungan *TalkBack*, ukuran huruf mengikuti sistem |
| Lokalisasi | Indonesia dan Inggris; tanpa teks tertanam di kode |
| Orientasi | Potret wajib; lanskap dan tablet pada versi berikutnya |
| Ukuran aplikasi | Target di bawah 25 MB per APK (`--split-per-abi`). Flutter menambah ukuran dasar; tinjau ulang setelah build rilis pertama |
| Pengujian | Uji unit untuk konversi takaran, pengurangan stok, HPP, dan pembatalan; uji integrasi (`integration_test`) untuk migrasi dan pulihkan |

## 12. Desain Antarmuka (Garis Besar)

* Material 3, warna merek mengikuti logo ARN (nilai warna menunggu aset).
* Halaman Utama: *top bar* tetap, *chip* kategori menempel di bawahnya, menu dalam *grid*, bilah keranjang melayang di bawah.
* Setiap layar daftar memiliki *empty state* yang mengarahkan langkah berikutnya (misalnya "Belum ada kategori. Buat kategori pertama").
* Urutan awal pemakaian yang disarankan, ditampilkan sebagai panduan singkat saat data kosong: 1) tambah bahan baku, 2) buat kategori, 3) buat menu dan resep, 4) mulai berjualan.

## 13. Risiko dan Mitigasi

| Risiko | Dampak | Mitigasi |
|---|---|---|
| Pengguna tidak pernah mencadangkan | Kehilangan seluruh data | Pengingat berkala, cadangan terjadwal, indikator "terakhir dicadangkan" di burger menu |
| Resep tidak akurat atau tidak diisi | Stok sistem melenceng | Opname mudah diakses, laporan selisih, kebijakan stok "peringatkan" |
| Beban input awal besar (banyak bahan dan resep) | Pengguna menyerah sebelum mulai | Penambahan cepat, duplikat menu, templat satuan umum; impor CSV dipertimbangkan versi 1.1 |
| Migrasi skema gagal pada pembaruan | Data korup | Uji migrasi otomatis, cadangan pengaman otomatis sebelum migrasi besar |
| Ponsel kelas bawah | Lambat | Gambar dikompres dan di-*cache*, *query* beragregasi di SQL |
| Plugin pihak ketiga (pemilih berkas, notifikasi terjadwal, `workmanager`) berperilaku berbeda antarversi Android | Cadangan atau pengingat gagal diam diam | Kunci versi paket, uji di perangkat nyata (Android 8, 13, dan terbaru), uji integrasi untuk alur cadangan |
| Aritmetika pecahan buatan sendiri salah | Stok atau HPP meleset | Dikunci oleh `docs/TEST_CASES_TAKARAN.md` sebagai uji unit sebelum implementasi |

## 14. Rencana Rilis

| Tahap | Cakupan |
|---|---|
| **MVP (v1.0)** | *Splash*, Halaman Utama, kategori dan menu, resep dan takaran, bahan baku (restok, opname), keranjang dan pembayaran tunai, pengurangan stok otomatis, info pemasukan, info pengeluaran, tema, bahasa, notifikasi stok, cadangan dan pulihkan manual, tentang |
| v1.1 | Cadangan terjadwal, enkripsi cadangan, ekspor CSV, cetak struk *Bluetooth*, kunci aplikasi PIN/biometrik |
| v1.2 | Tata letak tablet dan lanskap, impor bahan dan menu dari CSV, varian menu (ukuran, tambahan/*topping*) |

### Urutan Pengerjaan Teknis yang Disarankan

1. Proyek, tema, navigasi, *splash*.
2. Drift: tabel, DAO, migrasi dan uji.
3. Bahan baku dan konversi takaran (inti domain; uji unit lebih dulu).
4. Kategori, menu, resep, perhitungan HPP.
5. Halaman Utama, keranjang, pembayaran, pengurangan stok atomik.
6. Laporan pemasukan dan pengeluaran.
7. Cadangan dan pulihkan (kerjakan **sebelum** rilis, bukan sesudah).
8. Notifikasi, bahasa, penyempurnaan, pengujian di perangkat nyata.

## 15. Pertanyaan Terbuka

Jawaban atas pertanyaan ini mengubah cakupan dokumen. Asumsi bawaan dicetak miring.

1. **Jenis usaha**: kafe, warung makan, atau toko kue? *Asumsi: makanan atau minuman dengan resep tetap per porsi.*
2. **Varian dan tambahan**: perlu ukuran (S/M/L) atau tambahan (*topping*) yang mengubah takaran dan harga? *Asumsi: tidak pada v1.0.*
3. **Struk**: perlu cetak ke printer *Bluetooth* atau cukup bagikan sebagai gambar? *Asumsi: bagikan gambar atau teks pada v1.0.*
4. **Profil**: apakah ikon profil untuk profil usaha, atau akun pengguna dengan PIN? *Asumsi: profil usaha.*
5. **Pengguna ganda**: apakah ada karyawan yang perlu dibatasi aksesnya (misalnya tidak boleh melihat laba)? *Asumsi: tidak; satu pengguna.*
6. **Kebijakan stok kurang**: blokir penjualan atau tetap boleh dan ditandai? *Asumsi: blokir, dapat diubah di pengaturan.*
7. **Pajak atau biaya layanan**: perlu PPN atau biaya layanan pada transaksi? *Asumsi: tidak ada.*
8. **Kunci aplikasi**: perlu PIN atau biometrik saat membuka aplikasi? *Asumsi: tidak pada v1.0.*
9. **Aset merek**: logo ARN (format vektor jika ada) dan warna utama. *Dibutuhkan sebelum desain akhir.*
10. **Perangkat target**: versi Android paling lama yang masih dipakai calon pengguna? *Asumsi: Android 8.0 (API 26) ke atas.*
