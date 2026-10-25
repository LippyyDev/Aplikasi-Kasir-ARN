"""
Membangkitkan app/src/main/assets/data_contoh/data_contoh.json

Data contoh sebuah kedai kopi dalam tiga skenario (sedikit: 3 hari, sedang: 14 hari, banyak: 45 hari).
Hasilnya deterministik (seed tetap) dan dibuat dengan
simulasi stok sederhana: pemilik "belanja" saat stok menipis dan menu yang bahannya habis tidak dijual,
sehingga file ini bisa dimuat dengan kebijakan stok bawaan (blokir) tanpa satu pun penjualan ditolak.

Jalankan:  python tools/generate_data_contoh.py
"""
import json
import os
import random

SEED = 20261024

# ---------------------------------------------------------------- bahan baku
# satuan dasar: MASS=g, VOLUME=ml, COUNT=buah. Semua angka di simulasi dalam satuan dasar.
BAHAN = [
    dict(nama="Biji kopi arabika", dimensi="MASS", satuanBeli="kg", min=500, takaran=[],
         paket=(1000, "kg", 1, 185_000), titikBeli=600, target=3000),
    dict(nama="Susu cair", dimensi="VOLUME", satuanBeli="liter", min=2000, takaran=[],
         paket=(1000, "liter", 1, 18_500), titikBeli=3000, target=9000, berhentiBeli=3),
    dict(nama="Gula aren cair", dimensi="VOLUME", satuanBeli="liter", min=500,
         takaran=[dict(nama="sendok makan", setara="15")],
         paket=(1000, "liter", 1, 46_000), titikBeli=600, target=2000),
    dict(nama="Gula pasir", dimensi="MASS", satuanBeli="kg", min=500,
         takaran=[dict(nama="sendok makan", setara="12"), dict(nama="sendok teh", setara="4")],
         paket=(1000, "kg", 1, 17_500), titikBeli=500, target=2000,
         stokAwal=dict(jumlah="2", satuan="kg", nilai=34_000)),
    dict(nama="Bubuk cokelat", dimensi="MASS", satuanBeli="kg", min=300,
         takaran=[dict(nama="sendok makan", setara="7")],
         paket=(500, "g", 500, 62_000), titikBeli=300, target=1500),
    dict(nama="Teh celup", dimensi="COUNT", satuanBeli="dus", min=20,
         takaran=[dict(nama="dus", setara="50")],
         paket=(50, "dus", 1, 24_000), titikBeli=20, target=100),
    dict(nama="Cup plastik 16 oz", dimensi="COUNT", satuanBeli="buah", min=50,
         takaran=[dict(nama="sleeve", setara="50")],
         paket=(100, "buah", 100, 72_000), titikBeli=60, target=300),
    dict(nama="Es batu", dimensi="MASS", satuanBeli="kg", min=3000, takaran=[],
         paket=(10000, "kg", 10, 16_000), titikBeli=4000, target=20000),
    dict(nama="Roti tawar", dimensi="COUNT", satuanBeli="bungkus", min=12,
         takaran=[dict(nama="bungkus", setara="12")],
         paket=(12, "bungkus", 1, 15_000), titikBeli=12, target=48),
    dict(nama="Selai cokelat", dimensi="MASS", satuanBeli="kg", min=200, takaran=[],
         paket=(350, "g", 350, 38_000), titikBeli=200, target=350, berhentiBeli=12),
    dict(nama="Mentega", dimensi="MASS", satuanBeli="kg", min=200, takaran=[],
         paket=(200, "g", 200, 14_000), titikBeli=200, target=800,
         stokAwal=dict(jumlah="400", satuan="g", nilai=28_000)),
]
B = {b["nama"]: b for b in BAHAN}

# ---------------------------------------------------------------------- menu
# resep: (bahan, jumlah dalam satuan dasar, satuan tampil, jumlah tampil)
def r(bahan, dasar, satuan=None, tampil=None):
    base = {"MASS": "g", "VOLUME": "ml", "COUNT": "buah"}[B[bahan]["dimensi"]]
    return dict(bahan=bahan, dasar=dasar, satuan=satuan or base, jumlah=str(tampil if tampil is not None else dasar))

MENU = [
    dict(nama="Americano", kategori="Kopi", harga=18_000, bobot=14,
         resep=[r("Biji kopi arabika", 18), r("Es batu", 120), r("Cup plastik 16 oz", 1)]),
    dict(nama="Kopi susu gula aren", kategori="Kopi", harga=22_000, bobot=30,
         resep=[r("Biji kopi arabika", 18), r("Susu cair", 120), r("Gula aren cair", 30, "sendok makan", 2),
                r("Es batu", 100), r("Cup plastik 16 oz", 1)]),
    dict(nama="Latte", kategori="Kopi", harga=24_000, bobot=14,
         resep=[r("Biji kopi arabika", 18), r("Susu cair", 180), r("Gula pasir", 8, "sendok teh", 2),
                r("Es batu", 80), r("Cup plastik 16 oz", 1)]),
    dict(nama="Cappuccino", kategori="Kopi", harga=24_000, bobot=9,
         resep=[r("Biji kopi arabika", 18), r("Susu cair", 150), r("Bubuk cokelat", 2),
                r("Es batu", 80), r("Cup plastik 16 oz", 1)]),
    dict(nama="Cokelat dingin", kategori="Non kopi", harga=22_000, bobot=11,
         resep=[r("Bubuk cokelat", 14, "sendok makan", 2), r("Susu cair", 200), r("Gula pasir", 12, "sendok makan", 1),
                r("Es batu", 100), r("Cup plastik 16 oz", 1)]),
    dict(nama="Teh manis dingin", kategori="Non kopi", harga=10_000, bobot=16,
         resep=[r("Teh celup", 1), r("Gula pasir", 24, "sendok makan", 2), r("Es batu", 120), r("Cup plastik 16 oz", 1)]),
    dict(nama="Susu manis dingin", kategori="Non kopi", harga=15_000, bobot=8,
         resep=[r("Susu cair", 200), r("Gula pasir", 12, "sendok makan", 1), r("Es batu", 100), r("Cup plastik 16 oz", 1)]),
    dict(nama="Roti bakar cokelat", kategori="Roti dan camilan", harga=16_000, bobot=12,
         resep=[r("Roti tawar", 3), r("Selai cokelat", 30), r("Mentega", 10)]),
    dict(nama="Roti bakar mentega gula", kategori="Roti dan camilan", harga=14_000, bobot=7,
         resep=[r("Roti tawar", 3), r("Mentega", 15), r("Gula pasir", 10)]),
    dict(nama="Air mineral (titipan)", kategori="Non kopi", harga=6_000, bobot=9, resep=[]),
    dict(nama="Kopi tubruk (musiman)", kategori="Kopi", harga=12_000, bobot=0, aktif=False,
         catatan="Dijual saat musim hujan.",
         resep=[r("Biji kopi arabika", 15), r("Gula pasir", 12, "sendok makan", 1)]),
    dict(nama="Es krim vanila (belum dikategorikan)", kategori=None, harga=13_000, bobot=0,
         catatan="Belum ditempatkan di kategori, jadi tidak tampil di Halaman Utama.",
         resep=[]),
]
M = {m["nama"]: m for m in MENU}


# ---- tambahan khusus skenario "banyak"
BAHAN_EXTRA = [
    dict(nama="Sirup vanila", dimensi="VOLUME", satuanBeli="liter", min=200, takaran=[],
         paket=(750, "ml", 750, 95_000), titikBeli=200, target=1500),
    dict(nama="Sirup karamel", dimensi="VOLUME", satuanBeli="liter", min=200, takaran=[],
         paket=(750, "ml", 750, 95_000), titikBeli=200, target=1500),
    dict(nama="Bubuk matcha", dimensi="MASS", satuanBeli="kg", min=100, takaran=[],
         paket=(100, "g", 100, 85_000), titikBeli=100, target=500),
    dict(nama="Krimer", dimensi="MASS", satuanBeli="kg", min=200, takaran=[],
         paket=(500, "g", 500, 38_000), titikBeli=300, target=1500),
    dict(nama="Whipped cream", dimensi="VOLUME", satuanBeli="liter", min=200, takaran=[],
         paket=(250, "ml", 250, 32_000), titikBeli=250, target=1000),
    dict(nama="Sedotan", dimensi="COUNT", satuanBeli="buah", min=50, takaran=[],
         paket=(200, "buah", 200, 12_000), titikBeli=100, target=800),
    dict(nama="Tepung terigu", dimensi="MASS", satuanBeli="kg", min=500, takaran=[],
         paket=(1000, "kg", 1, 13_000), titikBeli=800, target=4000),
    dict(nama="Telur", dimensi="COUNT", satuanBeli="tray", min=30,
         takaran=[dict(nama="tray", setara="30")],
         paket=(30, "tray", 1, 52_000), titikBeli=30, target=120),
    dict(nama="Keju parut", dimensi="MASS", satuanBeli="kg", min=150, takaran=[],
         paket=(250, "g", 250, 45_000), titikBeli=150, target=750),
    dict(nama="Pisang", dimensi="COUNT", satuanBeli="buah", min=12, takaran=[],
         paket=(12, "buah", 12, 24_000), titikBeli=12, target=36),
    dict(nama="Kentang beku", dimensi="MASS", satuanBeli="kg", min=500, takaran=[],
         paket=(1000, "kg", 1, 38_000), titikBeli=1000, target=4000),
    dict(nama="Ayam fillet", dimensi="MASS", satuanBeli="kg", min=500, takaran=[],
         paket=(1000, "kg", 1, 48_000), titikBeli=1000, target=4000),
    dict(nama="Beras", dimensi="MASS", satuanBeli="kg", min=1000, takaran=[],
         paket=(5000, "kg", 5, 70_000), titikBeli=1500, target=10000),
    dict(nama="Minyak goreng", dimensi="VOLUME", satuanBeli="liter", min=500, takaran=[],
         paket=(1000, "liter", 1, 19_000), titikBeli=600, target=3000),
]
B.update({b["nama"]: b for b in BAHAN_EXTRA})
MENU_EXTRA = [
    dict(nama="Vanilla latte", kategori="Kopi", harga=26_000, bobot=12,
         resep=[r("Biji kopi arabika", 18), r("Susu cair", 180), r("Sirup vanila", 20), r("Es batu", 80),
                r("Cup plastik 16 oz", 1), r("Sedotan", 1)]),
    dict(nama="Caramel macchiato", kategori="Kopi", harga=28_000, bobot=9,
         resep=[r("Biji kopi arabika", 18), r("Susu cair", 160), r("Sirup karamel", 20), r("Whipped cream", 15),
                r("Es batu", 80), r("Cup plastik 16 oz", 1), r("Sedotan", 1)]),
    dict(nama="Es kopi mocha", kategori="Kopi", harga=26_000, bobot=8,
         resep=[r("Biji kopi arabika", 18), r("Susu cair", 150), r("Bubuk cokelat", 14, "sendok makan", 2),
                r("Es batu", 80), r("Cup plastik 16 oz", 1), r("Sedotan", 1)]),
    dict(nama="Matcha latte", kategori="Non kopi", harga=26_000, bobot=8,
         resep=[r("Bubuk matcha", 6), r("Susu cair", 180), r("Gula pasir", 12, "sendok makan", 1), r("Es batu", 80),
                r("Cup plastik 16 oz", 1), r("Sedotan", 1)]),
    dict(nama="Teh susu krimer", kategori="Non kopi", harga=14_000, bobot=10,
         resep=[r("Teh celup", 1), r("Krimer", 20), r("Gula pasir", 12, "sendok makan", 1), r("Es batu", 100),
                r("Cup plastik 16 oz", 1)]),
    dict(nama="Roti bakar keju", kategori="Roti dan camilan", harga=17_000, bobot=8,
         resep=[r("Roti tawar", 3), r("Keju parut", 20), r("Mentega", 10)]),
    dict(nama="Kentang goreng", kategori="Roti dan camilan", harga=18_000, bobot=9,
         resep=[r("Kentang beku", 150), r("Minyak goreng", 50)]),
    dict(nama="Pisang goreng", kategori="Dessert", harga=12_000, bobot=8,
         resep=[r("Pisang", 2), r("Tepung terigu", 40), r("Minyak goreng", 60)]),
    dict(nama="Waffle pisang", kategori="Dessert", harga=24_000, bobot=5,
         resep=[r("Tepung terigu", 80), r("Telur", 1), r("Pisang", 1), r("Mentega", 15), r("Whipped cream", 20)]),
    dict(nama="Nasi ayam goreng", kategori="Makanan berat", harga=28_000, bobot=9,
         resep=[r("Beras", 150), r("Ayam fillet", 120), r("Minyak goreng", 40)]),
    dict(nama="Nasi goreng spesial", kategori="Makanan berat", harga=25_000, bobot=8,
         resep=[r("Beras", 150), r("Telur", 1), r("Ayam fillet", 40), r("Minyak goreng", 30)]),
    dict(nama="Omelet keju", kategori="Makanan berat", harga=22_000, bobot=5,
         resep=[r("Telur", 2), r("Keju parut", 20), r("Mentega", 10), r("Roti tawar", 2)]),
]
M.update({m["nama"]: m for m in MENU_EXTRA})

PROFIL = dict(nama="Kedai Arang Kopi", alamat="Jl. Melati No. 12, Bandung", telepon="0812-3456-7890", mataUang="Rp")

SKENARIO = {
    "sedikit": dict(
        berkas="data_contoh_sedikit.json", seed=SEED + 1, hari=3, kategori=["Kopi", "Non kopi"],
        bahan=["Biji kopi arabika", "Susu cair", "Gula aren cair", "Gula pasir", "Es batu", "Cup plastik 16 oz"],
        menu=["Americano", "Kopi susu gula aren", "Latte", "Air mineral (titipan)"],
        ramai=(5, 8), sepi=(4, 6), batal=[], opname=[],
        pengeluaran=[dict(hariLalu=2, kategori="Lain-lain", nominal=25_000, catatan="Parkir dan kebersihan")],
        profil=dict(nama="Kedai Contoh Mini", alamat="Jl. Kenanga No. 3, Bandung", telepon="0811-2222-3333", mataUang="Rp"),
        ket="3 hari, 6 bahan, 4 menu",
    ),
    "sedang": dict(
        berkas="data_contoh.json", seed=SEED, hari=14, berhenti=True, kategori=["Kopi", "Non kopi", "Roti dan camilan"],
        bahan=[b["nama"] for b in BAHAN], menu=[m["nama"] for m in MENU],
        ramai=(11, 17), sepi=(8, 13), batal=[9, 5, 2],
        opname=[(6, "Susu cair", -450, "ml", "TUMPAH", True), (3, "Es batu", 600, "g", "SALAH_HITUNG", False)],
        pengeluaran=[
            dict(hariLalu=12, kategori="Sewa", nominal=800_000, catatan="Sewa lapak bulan ini"),
            dict(hariLalu=9, kategori="Gaji", nominal=600_000, catatan="Gaji satu karyawan paruh waktu"),
            dict(hariLalu=7, kategori="Listrik dan air", nominal=275_000, catatan="Tagihan listrik"),
            dict(hariLalu=5, kategori="Kemasan", nominal=45_000, catatan="Sedotan dan kantong kertas"),
            dict(hariLalu=2, kategori="Lain-lain", nominal=25_000, catatan="Parkir dan kebersihan"),
        ],
        profil=PROFIL, ket="14 hari, 11 bahan, 12 menu",
    ),
    "banyak": dict(
        berkas="data_contoh_banyak.json", seed=SEED + 2, hari=45,
        kategori=["Kopi", "Non kopi", "Roti dan camilan", "Dessert", "Makanan berat"],
        bahan=[b["nama"] for b in BAHAN + BAHAN_EXTRA], menu=[m["nama"] for m in MENU + MENU_EXTRA],
        ramai=(28, 42), sepi=(20, 30), batal=[40, 33, 27, 19, 12, 8, 5, 2],
        opname=[(38, "Susu cair", -600, "ml", "TUMPAH", True), (30, "Es batu", 800, "g", "SALAH_HITUNG", False),
                (21, "Telur", -4, "buah", "PECAH", True), (14, "Kentang beku", -300, "g", "RUSAK", True),
                (7, "Whipped cream", -150, "ml", "TUMPAH", True), (3, "Es batu", 700, "g", "SALAH_HITUNG", False)],
        pengeluaran=[
            dict(hariLalu=d, kategori=k, nominal=n, catatan=c) for d, k, n, c in [
                (43, "Sewa", 800_000, "Sewa lapak bulan lalu"), (13, "Sewa", 800_000, "Sewa lapak bulan ini"),
                (40, "Gaji", 1_800_000, "Gaji dua karyawan"), (10, "Gaji", 1_800_000, "Gaji dua karyawan"),
                (35, "Listrik dan air", 310_000, "Tagihan listrik"), (5, "Listrik dan air", 295_000, "Tagihan listrik"),
                (32, "Kemasan", 85_000, "Kantong kertas dan kotak makan"), (18, "Kemasan", 62_000, "Stiker dan kantong"),
                (4, "Kemasan", 48_000, "Sedotan kertas"), (26, "Lain-lain", 120_000, "Servis blender"),
                (15, "Lain-lain", 35_000, "Parkir dan kebersihan"), (1, "Lain-lain", 25_000, "Gas dan tisu"),
            ]
        ],
        profil=dict(nama="Kedai Arang Kopi & Dapur", alamat="Jl. Melati No. 12, Bandung", telepon="0812-3456-7890", mataUang="Rp"),
        ket="45 hari, 25 bahan, 24 menu",
    ),
}

def bangun(nama_skenario, cfg):
    HARI = cfg["hari"]
    rnd = random.Random(cfg["seed"])
    JUAL = [M[n] for n in cfg["menu"] if M[n]["bobot"] > 0]
    # ------------------------------------------------------------- simulasi stok
    stok = {n: 0 for n in cfg["bahan"]}
    for b in [B[n] for n in cfg["bahan"]]:
        if "stokAwal" in b:
            sa = b["stokAwal"]
            faktor = {"kg": 1000, "g": 1, "liter": 1000, "ml": 1}.get(sa["satuan"], 1)
            stok[b["nama"]] = int(float(sa["jumlah"]) * faktor)

    kejadian = []


    def tambah(hari_lalu, jam, menit, **kw):
        kejadian.append(dict(hariLalu=hari_lalu, jam=jam, menit=menit, **kw))


    def beli(hari_lalu, jam, menit, nama):
        b = B[nama]
        isi, satuan_paket, isi_tampil, harga = b["paket"]
        n = 0
        while stok[nama] < b["target"]:
            stok[nama] += isi
            n += 1
        harga_total = int(round(harga * n * rnd.uniform(0.96, 1.05), -2))
        jumlah_tampil = n * isi_tampil
        satuan = b["paket"][1]
        tambah(hari_lalu, jam, menit, jenis="restok", bahan=nama, jumlah=str(jumlah_tampil), satuan=satuan, harga=harga_total)


    def cukup(nama_menu, qty):
        return all(stok[x["bahan"]] >= x["dasar"] * qty for x in M[nama_menu]["resep"])


    def pakai(nama_menu, qty, arah=-1):
        for x in M[nama_menu]["resep"]:
            stok[x["bahan"]] += arah * x["dasar"] * qty


    def bulat_uang(total):
        if rnd.random() < 0.25:
            return total
        for langkah in (5_000, 10_000, 20_000, 50_000, 100_000):
            kandidat = ((total + langkah - 1) // langkah) * langkah
            if kandidat > total and rnd.random() < 0.55:
                return kandidat
        return ((total + 99_999) // 100_000) * 100_000


    CATATAN = ["kurang manis", "tanpa es", "es sedikit", "dibungkus", "tambah susu", "extra panas"]
    JAM_RAMAI = [8, 8, 9, 9, 10, 11, 12, 12, 13, 13, 14, 15, 16, 16, 17, 17, 18, 19, 20]

    for hari_lalu in range(HARI - 1, -1, -1):
        # Belanja pagi: hari pertama semua bahan, sesudahnya hanya yang menipis.
        menit_beli = 0
        for b in [B[n] for n in cfg["bahan"]]:
            nama = b["nama"]
            pertama = hari_lalu == HARI - 1
            if cfg.get("berhenti") and "berhentiBeli" in b and hari_lalu < b["berhentiBeli"]:
                continue
            if pertama and "stokAwal" in b:
                continue
            if pertama or stok[nama] < b["titikBeli"]:
                beli(hari_lalu, 7, menit_beli, nama)
                menit_beli += 2

        akhir_pekan = ((hari_lalu + 6) % 7) in (5, 6)  # perkiraan: variasi ramai, bukan kalender sungguhan
        jumlah_transaksi = rnd.randint(*cfg["ramai"]) if akhir_pekan else rnd.randint(*cfg["sepi"])
        if hari_lalu == 0:
            jumlah_transaksi = 3  # hari ini: sedikit saja, supaya waktu muat tidak melewati jam sekarang
        jam_list = sorted(rnd.choice(JAM_RAMAI) for _ in range(jumlah_transaksi))
        if hari_lalu == 0:
            jam_list = [7, 7, 8]
        for i, jam in enumerate(jam_list):
            menit = rnd.randint(0, 59)
            if hari_lalu == 0:
                menit = [5, 20, 40][i]
            n_baris = rnd.choices([1, 2, 3], weights=[55, 33, 12])[0]
            baris = []
            for _ in range(n_baris):
                menu = rnd.choices(JUAL, weights=[m["bobot"] for m in JUAL])[0]["nama"]
                qty = rnd.choices([1, 2, 3], weights=[78, 18, 4])[0]
                if any(b["menu"] == menu for b in baris):
                    continue
                if not cukup(menu, qty):
                    if qty > 1 and cukup(menu, 1):
                        qty = 1
                    else:
                        continue
                pakai(menu, qty)
                item = dict(menu=menu, qty=qty)
                if rnd.random() < 0.12 and M[menu]["resep"]:
                    item["catatan"] = rnd.choice(CATATAN)
                baris.append(item)
            if not baris:
                continue
            subtotal = sum(M[b["menu"]]["harga"] * b["qty"] for b in baris)
            trx = dict(jenis="jual", item=baris)
            diskon = 0
            roll = rnd.random()
            if roll < 0.05:
                trx["diskon"] = dict(jenis="PERSEN", nilai="10")
                diskon = int(round(subtotal * 0.10))
            elif roll < 0.09 and subtotal > 20_000:
                trx["diskon"] = dict(jenis="NOMINAL", nilai="5000")
                diskon = 5_000
            total = subtotal - diskon
            if rnd.random() < 0.30:
                trx["bayar"] = "NON_TUNAI"
            else:
                trx["bayar"] = "TUNAI"
                trx["uang"] = bulat_uang(total)
            if hari_lalu in cfg["batal"] and i == 1:
                trx["dibatalkan"] = True
                for b in baris:
                    pakai(b["menu"], b["qty"], arah=+1)
            tambah(hari_lalu, jam, menit, **trx)

        for (h, nama_op, delta, satuan_op, alasan_op, susut_op) in cfg["opname"]:
            if h != hari_lalu:
                continue
            fisik = max(0, stok[nama_op] + delta)
            stok[nama_op] = fisik
            tambah(hari_lalu, 21, 30, jenis="opname", bahan=nama_op, fisik=str(fisik), satuan=satuan_op,
                   alasan=alasan_op, catatSusut=susut_op)

    pengeluaran = cfg["pengeluaran"]

    kejadian.sort(key=lambda e: (-e["hariLalu"], e["jam"], e["menit"]))

    data = {
        "versi": 1,
        "keterangan": "Data contoh ARN Kasir (%s): %s. Dibangkitkan oleh tools/generate_data_contoh.py (seed %d)." % (nama_skenario, cfg["ket"], cfg["seed"]),
        "profil": cfg["profil"],
        "kategori": cfg["kategori"],
        "bahan": [
            {k: v for k, v in dict(
                nama=b["nama"], dimensi=b["dimensi"], satuanBeli=b["satuanBeli"],
                stokMinimum=str(b["min"]), takaran=b["takaran"],
                stokAwal=b.get("stokAwal"),
            ).items() if v is not None}
            for b in [B[n] for n in cfg["bahan"]]
        ],
        "menu": [
            {k: v for k, v in dict(
                nama=m["nama"], kategori=m["kategori"], harga=m["harga"], aktif=m.get("aktif", True),
                catatan=m.get("catatan", ""),
                resep=[dict(bahan=x["bahan"], jumlah=x["jumlah"], satuan=x["satuan"]) for x in m["resep"]],
            ).items() if v is not None or k == "kategori"}
            for m in [M[n] for n in cfg["menu"]]
        ],
        "pengeluaran": pengeluaran,
        "kejadian": kejadian,
    }
    return data, stok, kejadian

tujuan = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "assets", "data_contoh")
os.makedirs(tujuan, exist_ok=True)
for nama, cfg in SKENARIO.items():
    data, stok, kejadian = bangun(nama, cfg)
    with open(os.path.join(tujuan, cfg["berkas"]), "w", encoding="utf-8", newline="\n") as fh:
        json.dump(data, fh, ensure_ascii=False, indent=1)
        fh.write("\n")
    jual = [e for e in kejadian if e["jenis"] == "jual"]
    print(nama, "| bahan:", len(cfg["bahan"]), "menu:", len(cfg["menu"]), "| penjualan:", len(jual),
          "dibatalkan:", sum(1 for e in jual if e.get("dibatalkan")),
          "| restok:", sum(1 for e in kejadian if e["jenis"] == "restok"))
