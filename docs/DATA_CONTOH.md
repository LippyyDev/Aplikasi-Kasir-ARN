# Data Contoh

Berkas: `app/src/main/assets/data_contoh/data_contoh.json`, dibangkitkan oleh `python tools/generate_data_contoh.py` (seed tetap, hasilnya selalu sama).

Isi: kedai kopi fiktif "Kedai Arang Kopi", 14 hari.

| Bagian | Jumlah |
|---|---|
| Bahan baku | 11 (massa, volume, hitungan; sebagian punya takaran kustom dan stok awal) |
| Kategori menu | 3 |
| Menu | 12 (satu nonaktif, satu tanpa kategori, satu titipan tanpa resep) |
| Restok | sekitar 17 (harga bervariasi, supaya rata-rata tertimbang terlihat) |
| Penjualan | sekitar 146, termasuk diskon, catatan item, tunai dan non tunai |
| Penjualan dibatalkan | 3 |
| Opname | 2 (satu susut, satu kelebihan) |
| Pengeluaran manual | 5 |

## Cara memasukkan ke aplikasi

Data hanya bisa dimuat saat aplikasi masih kosong (tidak ada bahan, kategori, menu, dan transaksi):
- Halaman Utama, kartu "Mulai dari sini", tombol **Coba dengan data contoh**; atau
- Menu samping, **Cadangan dan pulihkan**, bagian **Data contoh**.

Untuk mengosongkan lagi: **Cadangan dan pulihkan**, **Hapus semua data**. Salinan pengaman dibuat otomatis di penyimpanan aplikasi sebelum penghapusan.

## Format JSON

```json
{
  "versi": 1,
  "profil": { "nama": "...", "alamat": "...", "telepon": "...", "mataUang": "Rp" },
  "kategori": ["Kopi", "Non kopi"],
  "bahan": [
    { "nama": "Gula pasir", "dimensi": "MASS", "satuanBeli": "kg", "stokMinimum": "500",
      "takaran": [ { "nama": "sendok makan", "setara": "12" } ],
      "stokAwal": { "jumlah": "2", "satuan": "kg", "nilai": 34000 } }
  ],
  "menu": [
    { "nama": "Latte", "kategori": "Kopi", "harga": 24000, "aktif": true, "catatan": "",
      "resep": [ { "bahan": "Gula pasir", "jumlah": "2", "satuan": "sendok teh" } ] }
  ],
  "pengeluaran": [ { "hariLalu": 7, "kategori": "Listrik dan air", "nominal": 275000, "catatan": "..." } ],
  "kejadian": [
    { "jenis": "restok", "hariLalu": 13, "jam": 7, "menit": 0, "bahan": "Gula pasir", "jumlah": "1", "satuan": "kg", "harga": 17500 },
    { "jenis": "jual", "hariLalu": 5, "jam": 9, "menit": 30,
      "item": [ { "menu": "Latte", "qty": 2, "catatan": "kurang manis" } ],
      "bayar": "TUNAI", "uang": 50000, "diskon": { "jenis": "PERSEN", "nilai": "10" }, "dibatalkan": false },
    { "jenis": "opname", "hariLalu": 3, "jam": 21, "menit": 30, "bahan": "Es batu", "fisik": "5000", "satuan": "g",
      "alasan": "SALAH_HITUNG", "catatSusut": false }
  ]
}
```

- `dimensi`: `MASS` (g), `VOLUME` (ml), `COUNT` (buah). `stokMinimum` dalam satuan dasar.
- Angka takaran ditulis sebagai teks supaya diurai persis (koma atau titik), tanpa `double`.
- `kejadian` diproses berurutan waktu lewat logika aplikasi yang sama dengan pemakaian nyata; waktu setiap kejadian diatur dari `hariLalu`, `jam`, `menit` dan tidak pernah melewati waktu sekarang.
- `bayar`: `TUNAI` atau `NON_TUNAI`. `diskon.jenis`: `PERSEN` atau `NOMINAL`.

## Tiga skenario ukuran

Saat mengisi data contoh (Halaman Utama atau Lainnya, Cadangan dan pulihkan) pengguna memilih ukuran:

| Skenario | Berkas | Isi |
|---|---|---|
| Sedikit | `data_contoh_sedikit.json` | 3 hari, 6 bahan, 4 menu, 2 kategori, 13 transaksi |
| Sedang (bawaan) | `data_contoh.json` | 14 hari, 11 bahan, 12 menu, 3 kategori, 146 transaksi |
| Banyak | `data_contoh_banyak.json` | 45 hari, 25 bahan, 24 menu, 5 kategori, sekitar 1.190 transaksi, 12 pengeluaran, 6 opname; pemuatan sekitar 1,5 menit |

Ketiganya dibangkitkan oleh `python tools/generate_data_contoh.py` (deterministik, seed tetap) dan dimuat lewat repositori yang sama dengan pemakaian sungguhan.
