# Kasus Uji Logika Takaran

Kasus uji untuk logika inti di lapisan `domain/` (lihat `CLAUDE.md`; implementasi di `app/src/test/.../domain` dan `app/src/androidTest`). Setiap baris dapat langsung menjadi satu uji unit JUnit (`app/src/test`). Semua angka sudah dihitung tangan; bila uji gagal, periksa implementasi lebih dulu.

Aturan umum: stok, resep, dan uang adalah `int`; konversi memakai aritmetika pecahan bilangan bulat (tanpa `double`) dengan pembulatan `HALF_UP` ke `int`.

## A. Konversi Satuan

| ID | Bahan dan faktor | Input | Hasil (satuan dasar) |
|---|---|---|---|
| A1 | Gula, 1 sendok makan = 12 g | 2,5 sendok makan | 30 g |
| A2 | Gula, satuan beli kg (1 kg = 1000 g) | 0,25 kg | 250 g |
| A3 | Susu, 1 cup = 240 ml | 1,5 cup | 360 ml |
| A4 | Kopi bubuk, 1 sachet = 25 g | 3 sachet | 75 g |
| A5 | Kopi bubuk, 1 sendok teh = 2,5 g | 3 sendok teh | 7,5 maka dibulatkan menjadi **8 g** |
| A6 | Kopi bubuk, 1 sendok teh = 2,5 g | 1 sendok teh | 2,5 maka dibulatkan menjadi **3 g** |
| A7 | Bahan bermassa (gula) | Input "liter" (satuan volume) | **Ditolak** (antardimensi) |
| A8 | Bahan apa pun | Jumlah 0 atau negatif | **Ditolak** |
| A9 | Gula, input 0,04 g | Dibulatkan menjadi 0; **ditolak** untuk resep (jumlah resep harus lebih dari 0 satuan dasar) |
| A10 | Gula, 1 sendok makan = 12 g | Teks "2,5" dan teks "2.5" | Keduanya diparse sebagai 5/2 dan menghasilkan **30 g** |
| A11 | Bahan apa pun | Teks "abc", "2,,5", "", "1e3" | **Ditolak** (bukan angka desimal valid) |

## B. Fixture A (dipakai kasus C sampai G)

| Bahan | Stok awal | Nilai stok awal | Rata-rata per satuan dasar |
|---|---|---|---|
| Kopi bubuk | 1000 g | Rp 200.000 | Rp 200 per g |
| Susu | 2000 ml | Rp 30.000 | Rp 15 per ml |
| Gula | 500 g | Rp 8.000 | Rp 16 per g |

Menu **Kopi Susu**, harga jual Rp 18.000. Resep per 1 porsi: kopi 15 g, susu 120 ml, gula 10 g.

## C. HPP dan Margin

| ID | Skenario | Hasil |
|---|---|---|
| C1 | HPP per porsi Kopi Susu | 15 x 200 + 120 x 15 + 10 x 16 = 3000 + 1800 + 160 = **Rp 4.960** |
| C2 | Margin per porsi | 18.000 - 4.960 = **Rp 13.040** (72,4%) |
| C3 | Menu tanpa resep (produk titipan) | HPP = 0, tidak pernah terblokir stok |
| C4 | Menu memakai bahan yang belum pernah dibeli (nilai stok 0, stok 0) | Ditandai **"HPP belum lengkap"** |

## D. Ketersediaan Menu

Memakai Fixture A setelah penjualan 3 porsi (stok kopi 955, susu 1640, gula 470; lihat E1).

| ID | Skenario | Hasil |
|---|---|---|
| D1 | Porsi tersedia | min(floor(955/15)=63, floor(1640/120)=13, floor(470/10)=47) = **13** |
| D2 | Kebijakan **blokir**, susu 100 ml (resep 120 ml) | Porsi tersedia 0, penambahan ke keranjang **ditolak** |
| D3 | Kebijakan **peringatkan**, kondisi D2, jual 1 porsi | Diizinkan; stok susu menjadi **-20 ml** dan ditandai untuk opname |
| D4 | Keranjang 5 Kopi Susu, susu 700 ml | 5 x 120 = 600 ml, **diizinkan** |
| D5 | Keranjang 6 Kopi Susu, susu 700 ml | 6 x 120 = 720 ml, **ditolak** (blokir) |
| D6 | Stok susu 600 ml. Keranjang: Kopi Susu x2 (240 ml) + Latte x2 (resep susu 200 ml, total 400 ml) | Total 640 ml > 600, **ditolak** walau tiap item sendirian cukup (pemakaian bersama dijumlahkan per bahan) |

## E. Penjualan (Fixture A)

Transaksi: 3 Kopi Susu, diskon 10%, dibayar tunai Rp 50.000.

| ID | Skenario | Hasil |
|---|---|---|
| E1 | Pengurangan stok | Kopi 1000 - 45 = **955**; susu 2000 - 360 = **1640**; gula 500 - 30 = **470** |
| E2 | Pengurangan nilai stok | Kopi 200.000 - 9.000 = **191.000**; susu 30.000 - 5.400 = **24.600**; gula 8.000 - 480 = **7.520** |
| E3 | Total HPP transaksi | 9.000 + 5.400 + 480 = **14.880** (sama dengan 3 x 4.960) |
| E4 | Subtotal, diskon, total | Subtotal **54.000**; diskon 10% = **5.400**; total **48.600** |
| E5 | Kembalian | 50.000 - 48.600 = **1.400** |
| E6 | Laba kotor transaksi | 48.600 - 14.880 = **33.720** |
| E7 | Pergerakan stok | 3 baris jenis `penjualan` (negatif), merujuk ke transaksi ini |
| E8 | Atomisitas | Bila pengurangan bahan ke-3 melempar galat, **tidak ada** perubahan: transaksi tidak tersimpan, stok dan nilai ketiga bahan tetap seperti awal |
| E9 | Keranjang kosong | Penyimpanan **ditolak** |
| E10 | Dibayar kurang dari total (tunai) | **Ditolak** |

## F. Pembatalan Transaksi (Void)

Lanjutan E. Sebelum *void*, resep Kopi Susu diubah: kopi 15 g menjadi 20 g.

| ID | Skenario | Hasil |
|---|---|---|
| F1 | Stok setelah *void* | Kopi **1000**, susu **2000**, gula **500** (dibalik berdasarkan snapshot 45/360/30, **bukan** resep baru 20 g) |
| F2 | Nilai stok setelah *void* | **200.000**, **30.000**, **8.000** |
| F3 | Status dan jejak | Transaksi berstatus `batal`, tidak dihapus; ada pergerakan stok jenis `batal` |
| F4 | Laporan | Transaksi batal **tidak** dihitung pada penjualan dan HPP |
| F5 | *Void* dua kali | Kedua kalinya **ditolak** |

## G. Rata-rata Tertimbang dan Pembulatan

| ID | Skenario | Hasil |
|---|---|---|
| G1 | Gula: restok 1 = 1000 g senilai Rp 15.000; restok 2 = 500 g senilai Rp 9.000 | Stok **1500 g**, nilai **24.000**, rata-rata Rp 16 per g |
| G2 | Lanjutan G1, pakai 300 g | Biaya 300 x 24.000 / 1500 = **4.800**; sisa nilai **19.200**; sisa stok **1200 g** |
| G3 | Stok 3, nilai Rp 10. Pakai 1 | 10/3 = 3,33 maka biaya **3**; sisa nilai **7**; stok 2 |
| G4 | Lanjutan G3, pakai 1 | 7/2 = 3,5 maka (HALF_UP) biaya **4**; sisa nilai **3**; stok 1 |
| G5 | Lanjutan G4, pakai 1 (stok habis) | Biaya **3**; sisa nilai **0**; stok 0 |
| G6 | Properti | G3 + G4 + G5: 3 + 4 + 3 = **10** = nilai awal. Jumlah biaya tidak boleh selisih akibat pembulatan |
| G7 | Restok dari satuan beli | Beli 2 kg gula Rp 30.000 menambah stok **+2000 g**, nilai **+30.000**, dan tercatat sebagai pengeluaran kategori bahan baku **30.000** |

## H. Penyesuaian Stok (Opname)

Memakai stok gula setelah E (470 g, nilai 7.520, rata-rata Rp 16 per g).

| ID | Skenario | Hasil |
|---|---|---|
| H1 | Stok fisik 450 g | Selisih **-20 g**; pergerakan jenis `penyesuaian` dengan alasan |
| H2 | Nilai susut (fitur susut aktif) | 20 x 16 = **Rp 320**, tercatat sebagai pengeluaran sumber `susut` |
| H3 | Fitur susut dimatikan | Stok tetap disesuaikan, **tidak** ada pengeluaran susut |
| H4 | Stok fisik lebih besar dari sistem | Selisih positif; tidak ada pengeluaran |

## I. Laporan Periode (tidak menghitung ganda)

Data: penjualan bersih E4 = 48.600; HPP E3 = 14.880; restok bahan G7 = 30.000; listrik (operasional) = 5.000.

| ID | Metrik | Hasil |
|---|---|---|
| I1 | Laba kotor | 48.600 - 14.880 = **33.720** |
| I2 | Laba bersih operasional | 33.720 - 5.000 = **28.720** (pembelian bahan **tidak** dikurangkan lagi) |
| I3 | Arus kas | 48.600 - 30.000 - 5.000 = **13.600** (pembelian bahan dihitung sebagai kas keluar) |

## J. Penghapusan dan Integritas

| ID | Skenario | Hasil |
|---|---|---|
| J1 | Hapus bahan yang dipakai resep | **Ditolak**, tampil daftar menu yang memakainya |
| J2 | Hapus menu yang pernah terjual | **Hapus lunak**; hilang dari Halaman Utama, tetap muncul di laporan lama |
| J3 | Nama bahan atau kategori duplikat | **Ditolak** (unik) |
| J4 | Hapus kategori berisi menu | Pengguna memilih pindah ke kategori lain atau "Tanpa kategori"; menu tidak ikut terhapus |
| J5 | Ubah faktor satuan kustom | Stok tersimpan (satuan dasar) **tidak berubah**, hanya tampilan |

## K. Cadangan dan Pemulihan (uji integrasi)

| ID | Skenario | Hasil |
|---|---|---|
| K1 | Cadangan lalu pulihkan di instalasi bersih | Jumlah baris **tiap tabel** sama; semua gambar tampil |
| K2 | Satu bita pada berkas cadangan diubah | **Ditolak** oleh *checksum*; data lama utuh |
| K3 | Cadangan dari versi skema lebih baru | **Ditolak** dengan pesan memperbarui aplikasi |
| K4 | Cadangan dari versi skema lebih lama | Migrasi Room dijalankan, data utuh |
| K5 | Proses dihentikan di tengah pemulihan | Data lama **tidak rusak** (dikembalikan dari cadangan pengaman) |
| K6 | Cadangan saat database sedang menerima tulisan | Berkas cadangan **konsisten** (tidak setengah tertulis) |
