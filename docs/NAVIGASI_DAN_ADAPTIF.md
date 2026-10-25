# Navigasi dan Tata Letak Adaptif

## Peta aplikasi

```
Kasir  ........ pencarian, kategori, grid menu, keranjang  ->  Pembayaran  ->  Sukses
  |- lonceng  -> Notifikasi
  |- avatar   -> Profil usaha

Kelola ........ [Menu | Bahan baku | Kategori]
  |- Menu          -> Editor menu (foto, harga, resep, HPP)
  |- Bahan baku    -> Detail bahan (restok, opname, riwayat) -> Editor bahan
  |- Kategori      (urutan, ubah nama, hapus dengan pemindahan menu)
  |- menu titik tiga (tab Bahan baku): kebijakan stok kurang

Laporan ....... [Pemasukan | Pengeluaran]  + pemilih periode bersama
  |- Pemasukan     -> Rincian transaksi (batalkan, bagikan struk)
  |- Pengeluaran   -> kelola kategori pengeluaran

Lainnya ....... profil usaha, notifikasi, cadangan dan pulihkan (termasuk data contoh dan hapus semua data),
                tema, bahasa, tentang
```

Aturan di balik pembagian ini:
1. Jalur kasir tidak berisi apa pun selain menjual.
2. Satu tujuan, satu tempat: semua yang mengubah katalog ada di Kelola; semua angka usaha ada di Laporan.
3. Pengaturan dan pemeliharaan data ada di Lainnya agar tidak tersentuh saat melayani pembeli.
4. Layar turunan (editor, detail, pembayaran) menutup bilah navigasi supaya fokus pada satu tugas.

## Tata letak menurut ukuran jendela

| Jendela | Navigasi | Kasir | Lainnya |
|---|---|---|---|
| Ponsel potret (kurang dari 700 dp) | Bilah bawah | Grid menu, bilah keranjang melayang, keranjang di lembar bawah | Daftar penuh lebar |
| Ponsel lanskap (700 dp ke atas, tinggi kurang dari 480 dp) | Rail kiri | Dua panel: menu dan keranjang tetap | Judul dan tab satu baris |
| Tablet potret dan lanskap (700 dp ke atas) | Rail kiri | Dua panel dengan lebar keranjang sepertiga jendela (300 sampai 420 dp) | Grid dua kolom atau lebih |

Pembayaran memakai dua kolom (ringkasan di kiri, cara bayar di kanan) pada jendela lebar. Formulir dan layar detail dibatasi lebar 640 dp dan ditengahkan.

Ukuran jendela diambil dari `LocalWindowInfo` (ukuran jendela, bukan layar), sehingga ikut berubah pada layar terbagi dan ponsel lipat.
