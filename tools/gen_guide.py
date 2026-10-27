# -*- coding: utf-8 -*-
"""Membangkitkan string sambutan, panduan, tutorial, dan GuideTopics.kt (Indonesia dan Inggris)."""
import re

import os
ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'app', 'src', 'main'))
J = os.path.join(ROOT, 'java', 'com', 'example', 'kasirarn')

# --------------------------------------------------------------------------- teks umum
COMMON = {
    'id': dict(
        ob_skip='Lewati', ob_skip_all='Lewati semua', ob_next='Lanjut', ob_start='Mulai', ob_done='Selesai',
        ob_start_tour='Mulai tutorial',
        ob_brand_a='ARN', ob_brand_b='Kasir',
        ob_head_1='Jualan beres,', ob_head_2='stok ikut', ob_head_3='terhitung.',
        ob_welcome_body='Aplikasi kasir yang bisa dipakai tanpa internet. Setiap kali ada penjualan, stok bahan berkurang sendiri dan modalnya langsung terhitung.',
        ob_name_title='Apa nama toko Anda?', ob_name_hint='Nama ini akan tampil di struk dan di Halaman Utama.',
        ob_logo_title='Mau pasang logo atau foto toko?', ob_logo_body='Boleh dilewati. Fotonya akan tampil di Halaman Utama.',
        ob_logo_pick='Pilih foto', ob_logo_change='Ganti foto',
        ob_contact_title='Alamat dan nomor telepon', ob_contact_body='Boleh dikosongkan. Kalau diisi, keduanya ikut tercetak di struk.',
        ob_demo_title='Struk contoh', ob_demo_pay='Tunai', ob_demo_stamp='LUNAS',
        ob_demo_line='2× Kopi susu gula aren', ob_demo_total='Rp 44.000',
        ob_demo_stock='Stok berkurang otomatis',
        ob_demo_s1='Susu cair', ob_demo_s1v='−240 ml',
        ob_demo_s2='Gula aren cair', ob_demo_s2v='−60 ml',
        ob_demo_s3='Biji kopi arabika', ob_demo_s3v='−36 g',
        ob_demo_cost='Modal Rp 15.402', ob_demo_profit='Laba Rp 28.598',
        ob_step_of='Langkah %1$d dari %2$d',
        ob_profile_title='Kenalkan toko Anda',
        ob_profile_body='Nama dan alamat ini tercetak di struk dan tampil di Halaman Utama. Bisa diubah kapan saja di Lainnya.',
        ob_profile_save='Simpan dan lanjut', ob_profile_later='Isi nanti',
        ob_profile_need_name='Isi nama toko untuk melanjutkan.',
        ob_features_kicker='FITUR %1$d DARI %2$d',
        ob_features_hint='Penjelasan yang lebih rinci untuk tiap fitur ada di Lainnya, Panduan penggunaan.',
        userguide_title='Panduan penggunaan',
        guide_summary='Tutorial untuk setiap fitur',
        guide_interactive_title='Tutorial interaktif',
        guide_interactive_body='Bagian-bagian penting disorot satu per satu, langsung di aplikasi. Hanya sekitar satu menit.',
        guide_topics_label='Tutorial per fitur',
        guide_steps_label='Langkah-langkah', guide_tips_label='Tips', guide_try='Coba sekarang',
        guide_topic_steps='%1$d langkah',
        guide_sec_sell='Berjualan', guide_sec_manage='Kelola usaha', guide_sec_reports='Laporan', guide_sec_system='Data dan pengaturan',
        tour_step='%1$d dari %2$d',
        tour_cashier_title='Mode Kasir', tour_cashier_text='Di sinilah Anda melayani pembeli. Semua yang dibutuhkan saat berjualan ada di sini.',
        tour_search_title='Cari menu', tour_search_text='Ketik nama menu untuk langsung menemukannya.',
        tour_categories_title='Kategori', tour_categories_text='Pilih kategori untuk menyaring menu. Pilihan Semua menampilkan seluruh menu.',
        tour_menu_title='Daftar menu', tour_menu_text='Tekan tombol + untuk memasukkan menu ke keranjang. Bilah keranjang akan muncul di bawah, tekan untuk membayar.',
        tour_manage_title='Kelola', tour_manage_text='Tempat mengatur menu, resep, bahan baku, dan stok. Titik merah berarti ada bahan yang perlu diperhatikan.',
        tour_reports_title='Laporan', tour_reports_text='Lihat pemasukan, laba, dan pengeluaran untuk periode yang Anda pilih.',
        tour_more_title='Lainnya', tour_more_text='Profil toko, notifikasi, cadangan, tema, dan bahasa ada di sini. Kita buka sebentar.',
        tour_guide_title='Panduan penggunaan', tour_guide_text='Selesai. Kalau ingin penjelasan yang lebih rinci untuk tiap fitur, buka Panduan penggunaan kapan saja.',
    ),
    'en': dict(
        ob_skip='Skip', ob_skip_all='Skip all', ob_next='Next', ob_start='Get started', ob_done='Done',
        ob_start_tour='Start tutorial',
        ob_brand_a='ARN', ob_brand_b='Kasir',
        ob_head_1='Sell first,', ob_head_2='stock', ob_head_3='follows.',
        ob_welcome_body='A cashier app that works without internet. Every time you make a sale, ingredient stock goes down by itself and the cost is worked out right away.',
        ob_name_title='What is your shop called?', ob_name_hint='This name will show on receipts and on the home screen.',
        ob_logo_title='Want to add a logo or shop photo?', ob_logo_body='You can skip this. The photo will show on the home screen.',
        ob_logo_pick='Choose photo', ob_logo_change='Change photo',
        ob_contact_title='Address and phone number', ob_contact_body='You can leave these empty. If you fill them in, both are printed on receipts.',
        ob_demo_title='Sample receipt', ob_demo_pay='Cash', ob_demo_stamp='PAID',
        ob_demo_line='2× Palm sugar iced latte', ob_demo_total='Rp 44,000',
        ob_demo_stock='Stock goes down by itself',
        ob_demo_s1='Fresh milk', ob_demo_s1v='−240 ml',
        ob_demo_s2='Palm sugar syrup', ob_demo_s2v='−60 ml',
        ob_demo_s3='Arabica beans', ob_demo_s3v='−36 g',
        ob_demo_cost='Cost Rp 15,402', ob_demo_profit='Profit Rp 28,598',
        ob_step_of='Step %1$d of %2$d',
        ob_profile_title='Introduce your shop',
        ob_profile_body='This name and address are printed on receipts and shown on the home screen. You can change them any time under More.',
        ob_profile_save='Save and continue', ob_profile_later='Fill in later',
        ob_profile_need_name='Enter the shop name to continue.',
        ob_features_kicker='FEATURE %1$d OF %2$d',
        ob_features_hint='More detailed explanations for each feature are under More, User guide.',
        userguide_title='User guide',
        guide_summary='A tutorial for every feature',
        guide_interactive_title='Interactive tour',
        guide_interactive_body='Key parts are highlighted one by one, right in the app. It only takes about a minute.',
        guide_topics_label='Tutorials by feature',
        guide_steps_label='Steps', guide_tips_label='Tips', guide_try='Try it now',
        guide_topic_steps='%1$d steps',
        guide_sec_sell='Selling', guide_sec_manage='Running the business', guide_sec_reports='Reports', guide_sec_system='Data and settings',
        tour_step='%1$d of %2$d',
        tour_cashier_title='Cashier mode', tour_cashier_text='This is where you serve customers. Everything you need while selling is here.',
        tour_search_title='Search menus', tour_search_text='Type a menu name to find it right away.',
        tour_categories_title='Categories', tour_categories_text='Pick a category to filter menus. All shows every menu.',
        tour_menu_title='Menu list', tour_menu_text='Tap + to put a menu in the cart. The cart bar appears below; tap it to pay.',
        tour_manage_title='Manage', tour_manage_text='Set up menus, recipes, ingredients and stock here. A red dot means an ingredient needs attention.',
        tour_reports_title='Reports', tour_reports_text='See income, profit and expenses for the period you choose.',
        tour_more_title='More', tour_more_text='Shop profile, notifications, backup, theme and language are here. Let us open it for a moment.',
        tour_guide_title='User guide', tour_guide_text='All done. For more detailed explanations of each feature, open the User guide any time.',
    ),
}

# --------------------------------------------------------------------------- lima halaman penjelasan pembuka
PAGES = {
    'id': [
        ('Cara berjualan di Kasir', 'Pilih menu, atur jumlahnya, lalu terima pembayaran.',
         ['Pilih menu dengan tombol +, atau cari namanya di kolom pencarian.',
          'Buka keranjang di bawah untuk mengubah jumlah, menambah catatan, atau memberi diskon.',
          'Tekan Bayar, pilih tunai atau non tunai, lalu bagikan struk ke pembeli.']),
        ('Membuat menu dan resep', 'Dari resep, modal dan margin setiap menu dihitung otomatis.',
         ['Buka Kelola, pilih tab Menu, lalu tekan Tambah menu.',
          'Isi nama, harga jual, kategori, dan foto kalau ada.',
          'Tambahkan bahan beserta takarannya untuk satu porsi. Modal per porsi langsung muncul.']),
        ('Mencatat bahan baku dan stok', 'Stok berkurang sendiri setiap ada penjualan, sesuai resep.',
         ['Di Kelola, buka tab Bahan baku, lalu tambahkan bahan beserta satuan belinya.',
          'Setiap belanja bahan, catat lewat Restok supaya harga rata-rata dan modal tetap akurat.',
          'Cocokkan stok dengan kondisi di dapur lewat Opname. Anda akan diberi tahu saat bahan hampir habis.']),
        ('Melihat laporan', 'Pantau pemasukan, laba, dan pengeluaran untuk periode tertentu.',
         ['Buka Laporan, lalu pilih periode: Hari ini, 7 hari, Bulan ini, atau Kustom.',
          'Tab Pemasukan menampilkan penjualan, laba kotor, dan menu terlaris.',
          'Tab Pengeluaran dipakai untuk mencatat biaya seperti sewa dan gaji, serta melihat arus kas.']),
        ('Mengamankan data', 'Data hanya tersimpan di ponsel ini, jadi buatlah cadangan secara berkala.',
         ['Buka Lainnya, pilih Cadangan dan pulihkan, lalu tekan Buat cadangan.',
          'Simpan berkasnya di luar aplikasi, misalnya di penyimpanan ponsel atau kartu memori.',
          'Tema, bahasa, dan Panduan penggunaan juga ada di menu Lainnya.']),
    ],
    'en': [
        ('How to sell at the Cashier', 'Pick menus, set the quantity, then take payment.',
         ['Pick a menu with the + button, or search for it by name.',
          'Open the cart at the bottom to change quantities, add notes or give a discount.',
          'Tap Pay, choose cash or non-cash, then share the receipt with the customer.']),
        ('Creating menus and recipes', 'From the recipe, the cost and margin of each menu are worked out automatically.',
         ['Open Manage, choose the Menus tab, then tap Add menu.',
          'Enter the name, selling price, category and a photo if you have one.',
          'Add the ingredients and amounts for one serving. The cost per serving shows right away.']),
        ('Tracking ingredients and stock', 'Stock goes down by itself with every sale, following the recipe.',
         ['In Manage, open the Ingredients tab and add each ingredient with its purchase unit.',
          'Every time you buy ingredients, record it with Restock so the average price and cost stay accurate.',
          'Match stock with what is in the kitchen using Stock count. You are notified when an ingredient runs low.']),
        ('Viewing reports', 'Keep track of income, profit and expenses for a given period.',
         ['Open Reports, then choose a period: Today, 7 days, This month or Custom.',
          'The Income tab shows sales, gross profit and best sellers.',
          'The Expenses tab is for recording costs such as rent and wages, and for seeing cash flow.']),
        ('Backing up your data', 'Your data is stored only on this phone, so back it up regularly.',
         ['Open More, choose Backup and restore, then tap Create backup.',
          'Keep the file outside the app, for example in phone storage or on a memory card.',
          'Theme, language and the User guide are also in the More menu.']),
    ],
}

# --------------------------------------------------------------------------- tutorial rinci per fitur
# id, icon, bagian, rute tujuan, {id/en: (judul, ringkas, [langkah], [tips])}
TOPICS = [
    ('sell', 'PointOfSale', 'sell', 'HOME', {
        'id': ('Menjual di Kasir', 'Dari memilih menu sampai struk dibagikan.',
               ['Buka mode Kasir. Cari menu lewat kolom pencarian, atau saring dengan chip kategori di bawahnya.',
                'Ketuk tombol + pada kartu menu untuk memasukkannya ke keranjang. Ketuk lagi untuk menambah jumlah, atau pakai tombol − dan + yang muncul di kartu.',
                'Tahan kartu menu untuk menambah catatan khusus, misalnya "kurang manis".',
                'Ketuk bilah keranjang di bawah untuk membuka rincian. Di sini Anda bisa mengubah jumlah, menghapus item, dan menambah diskon (nominal atau persen).',
                'Ketuk Bayar. Untuk tunai, isi Uang diterima atau ketuk Uang pas; kembalian dihitung otomatis. Untuk non tunai, cukup pilih Non tunai.',
                'Ketuk Selesaikan pembayaran. Di layar berhasil, bagikan struk sebagai teks atau gambar, lalu ketuk Transaksi baru.'],
               ['Menu berlabel Stok habis tidak bisa dijual sampai bahannya direstok (kebijakan ini bisa diubah di Kelola, Bahan baku, menu titik tiga).',
                'Salah memasukkan transaksi? Buka Laporan, ketuk transaksinya, lalu Batalkan transaksi. Stok dikembalikan otomatis.']),
        'en': ('Selling at the cashier', 'From picking a menu to sharing the receipt.',
               ['Open Cashier mode. Find a menu with the search box, or filter with the category chips below it.',
                'Tap + on a menu card to put it in the cart. Tap again to add more, or use the − and + buttons that appear on the card.',
                'Press and hold a menu card to add a note, for example "less sweet".',
                'Tap the cart bar at the bottom to open the details. You can change quantities, remove items and add a discount (amount or percent) there.',
                'Tap Pay. For cash, enter Cash received or tap Exact amount; change is calculated for you. For non-cash, just choose Non-cash.',
                'Tap Complete payment. On the success screen, share the receipt as text or an image, then tap New transaction.'],
               ['A menu marked Out of stock cannot be sold until its ingredients are restocked (you can change this policy in Manage, Ingredients, the three-dot menu).',
                'Entered a sale by mistake? Open Reports, tap the transaction, then Void transaction. Stock is returned automatically.']),
    }),
    ('menu', 'MenuBook', 'manage', 'MANAGE0', {
        'id': ('Membuat menu dan resep', 'Resep membuat stok dan modal terhitung sendiri.',
               ['Buka Kelola, tab Menu, lalu ketuk Tambah menu.',
                'Isi nama dan harga jual. Tambahkan foto bila ada (opsional, otomatis dikecilkan) dan pilih kategori.',
                'Di bagian Resep per porsi, ketuk Tambah bahan. Pilih bahan, isi jumlah untuk satu porsi, dan pilih satuannya (misalnya 2 sendok makan).',
                'Ulangi untuk semua bahan. Kartu Modal dan margin menampilkan modal per porsi dan persentase margin saat Anda mengisi.',
                'Ketuk Simpan. Menu langsung tampil di Kasir bila sudah punya kategori dan Aktif dijual menyala.'],
               ['Menu tanpa resep (misalnya air kemasan) tetap bisa dijual; stok tidak dilacak dan modalnya dihitung nol.',
                'Matikan Aktif dijual untuk menyembunyikan menu musiman tanpa menghapus riwayatnya.']),
        'en': ('Creating menus and recipes', 'A recipe lets stock and cost work themselves out.',
               ['Open Manage, the Menus tab, then tap Add menu.',
                'Enter the name and selling price. Add a photo if you like (optional, resized automatically) and choose a category.',
                'Under Recipe per serving, tap Add ingredient. Pick the ingredient, enter the amount for one serving and choose its unit (for example 2 tablespoons).',
                'Repeat for every ingredient. The Cost and margin card shows cost per serving and margin percent as you fill in.',
                'Tap Save. The menu appears at the cashier right away if it has a category and Active for sale is on.'],
               ['A menu without a recipe (for example bottled water) can still be sold; stock is not tracked and its cost counts as zero.',
                'Turn off Active for sale to hide a seasonal menu without deleting its history.']),
    }),
    ('ingredients', 'Inventory2', 'manage', 'MANAGE1', {
        'id': ('Mengelola bahan baku', 'Bahan, satuan, dan takaran yang dipakai di resep.',
               ['Buka Kelola, tab Bahan baku, lalu ketuk Tambah bahan.',
                'Isi nama bahan dan pilih Jenis satuan: Massa (gram, kg), Volume (ml, liter), atau Hitungan (buah). Jenis ini terkunci setelah disimpan.',
                'Pilih Satuan beli, yaitu satuan yang biasa Anda pakai saat membeli (misalnya kg atau liter).',
                'Isi Stok minimum. Saat stok mencapai batas ini, Anda mendapat notifikasi.',
                'Bila resep memakai takaran rumahan, ketuk Tambah takaran, misalnya "sendok makan = 12 g". Setelah itu takaran muncul sebagai pilihan satuan di resep.',
                'Bila bahan sudah ada stoknya, isi stok awal beserta nilainya agar modal terhitung benar sejak awal.'],
               ['Bahan yang sudah punya riwayat tidak bisa dihapus, hanya diarsipkan, supaya laporan lama tetap benar.',
                'Penanda Aman, Menipis, dan Habis di daftar membantu Anda melihat bahan yang perlu dibeli.']),
        'en': ('Managing ingredients', 'The ingredients, units and measures used in recipes.',
               ['Open Manage, the Ingredients tab, then tap Add ingredient.',
                'Enter the ingredient name and choose a Unit type: Mass (grams, kg), Volume (ml, litres) or Count (pieces). The type is locked once saved.',
                'Choose the Purchase unit, the unit you normally buy in (for example kg or litre).',
                'Set the Minimum stock. When stock reaches this level you get a notification.',
                'If your recipes use kitchen measures, tap Add measure, for example "tablespoon = 12 g". It then appears as a unit choice in recipes.',
                'If the ingredient already has stock, enter the opening stock and its value so cost is right from the start.'],
               ['An ingredient with history cannot be deleted, only archived, so past reports stay correct.',
                'The OK, Low and Out markers in the list show which ingredients need buying.']),
    }),
    ('stock', 'AddShoppingCart', 'manage', 'MANAGE1', {
        'id': ('Restok dan opname stok', 'Catat pembelian dan cocokkan stok dengan kenyataan.',
               ['Buka Kelola, tab Bahan baku, lalu ketuk bahan yang ingin diperbarui.',
                'Ketuk Restok untuk mencatat pembelian. Isi jumlah dibeli, satuannya, dan total harga yang dibayar.',
                'Ketuk Simpan restok. Stok bertambah, harga rata-rata bahan diperbarui, dan pembelian tercatat sebagai pengeluaran kategori Bahan baku.',
                'Untuk mencocokkan stok, ketuk Opname. Isi stok fisik yang sebenarnya dan pilih alasan selisihnya.',
                'Bila selisihnya berupa bahan terbuang, rusak, atau tumpah, aktifkan pencatatan susut agar tercatat sebagai kerugian.',
                'Riwayat stok di bawahnya mencatat setiap penjualan, restok, pembatalan, dan opname. Saring dengan 7 hari, 30 hari, atau Semua.'],
               ['Selalu isi total harga saat restok. Tanpa harga, modal per porsi tidak bisa dihitung akurat.',
                'Lakukan opname berkala, misalnya seminggu sekali, untuk menjaga stok di aplikasi tetap sama dengan di dapur.']),
        'en': ('Restock and stock count', 'Record purchases and match stock with reality.',
               ['Open Manage, the Ingredients tab, then tap the ingredient you want to update.',
                'Tap Restock to record a purchase. Enter the quantity bought, its unit and the total price paid.',
                'Tap Save restock. Stock goes up, the ingredient average price is updated and the purchase is recorded as an Ingredients expense.',
                'To match stock, tap Stock count. Enter the real physical stock and choose a reason for the difference.',
                'If the difference is waste, damage or spillage, turn on loss recording so it is counted as a loss.',
                'The stock history below logs every sale, restock, void and stock count. Filter by 7 days, 30 days or All.'],
               ['Always enter the total price when restocking. Without a price, cost per serving cannot be calculated accurately.',
                'Do a stock count regularly, say weekly, to keep the stock in the app equal to what is in the kitchen.']),
    }),
    ('categories', 'Category', 'manage', 'MANAGE2', {
        'id': ('Mengatur kategori menu', 'Kategori menyusun chip di Halaman Utama.',
               ['Buka Kelola, tab Kategori, lalu ketuk Tambah kategori dan isi namanya.',
                'Atur urutan dengan tombol panah naik dan turun. Urutan di sini sama dengan urutan chip di Kasir.',
                'Ketuk titik tiga pada kategori untuk mengubah nama atau menghapusnya.',
                'Saat menghapus kategori yang berisi menu, pilih kategori tujuan agar menunya tidak hilang dari Kasir.'],
               ['Menu tanpa kategori tidak tampil di Kasir. Aplikasi memberi peringatan bila ada menu seperti itu.']),
        'en': ('Organising menu categories', 'Categories build the chips on the home screen.',
               ['Open Manage, the Categories tab, then tap Add category and enter its name.',
                'Set the order with the up and down arrows. The order here matches the chip order at the cashier.',
                'Tap the three dots on a category to rename or delete it.',
                'When deleting a category that has menus, choose a destination category so those menus do not disappear from the cashier.'],
               ['A menu without a category does not show at the cashier. The app warns you if there are any.']),
    }),
    ('income', 'BarChart', 'reports', 'REPORTS0', {
        'id': ('Membaca laporan pemasukan', 'Penjualan, laba kotor, dan menu terlaris.',
               ['Buka Laporan, tab Pemasukan. Pilih periode di bagian atas: Hari ini, 7 hari, Bulan ini, atau Kustom (pilih tanggal sendiri).',
                'Kartu besar menampilkan Penjualan bersih (setelah diskon dan tanpa transaksi yang dibatalkan).',
                'Ubin di bawahnya: Penjualan kotor, Diskon, Modal bahan (HPP), dan Laba kotor.',
                'Grafik menunjukkan penjualan per jam (hari ini) atau per hari (periode lebih panjang).',
                'Gulir ke bawah untuk Menu terlaris, penjualan per kategori, dan per metode bayar.',
                'Daftar transaksi di bagian akhir. Ketuk salah satu untuk melihat rincian, laba transaksi itu, membagikan struk, atau membatalkannya.'],
               ['Laba kotor = penjualan bersih dikurangi modal bahan yang terpakai. Biaya lain seperti sewa dan gaji dihitung di tab Pengeluaran.']),
        'en': ('Reading the income report', 'Sales, gross profit and best sellers.',
               ['Open Reports, the Income tab. Choose a period at the top: Today, 7 days, This month or Custom (pick your own dates).',
                'The big card shows Net sales (after discounts and without voided transactions).',
                'The tiles below show Gross sales, Discounts, Ingredient cost (COGS) and Gross profit.',
                'The chart shows sales per hour (today) or per day (longer periods).',
                'Scroll down for Top menus, sales by category and by payment method.',
                'The transaction list is at the end. Tap one to see its details, the profit on that sale, share the receipt or void it.'],
               ['Gross profit = net sales minus the cost of ingredients used. Other costs such as rent and wages are counted in the Expenses tab.']),
    }),
    ('expenses', 'Payments', 'reports', 'REPORTS1', {
        'id': ('Mencatat pengeluaran dan arus kas', 'Biaya operasional, laba bersih, dan uang masuk keluar.',
               ['Buka Laporan, tab Pengeluaran, lalu ketuk Catat pengeluaran.',
                'Pilih tanggal dan kategori (Sewa, Listrik dan air, Gaji, Kemasan, atau Lain-lain), isi nominal, dan tambah catatan bila perlu.',
                'Ketuk Simpan. Pengeluaran tampil di daftar dan ikut dihitung.',
                'Kartu Laba bersih = laba kotor dikurangi biaya operasional. Bagian Arus kas menunjukkan uang yang benar-benar masuk dan keluar.',
                'Ketuk ikon kategori di pojok kanan atas untuk menambah atau mengubah kategori pengeluaran Anda sendiri.'],
               ['Pembelian bahan (restok) otomatis masuk sebagai pengeluaran Bahan baku dan memengaruhi arus kas, tetapi tidak mengurangi laba karena sudah dihitung sebagai modal.']),
        'en': ('Recording expenses and cash flow', 'Operating costs, net profit and money in and out.',
               ['Open Reports, the Expenses tab, then tap Add expense.',
                'Choose the date and category (Rent, Utilities, Wages, Packaging or Other), enter the amount and add a note if needed.',
                'Tap Save. The expense appears in the list and is counted.',
                'The Net profit card = gross profit minus operating costs. The Cash flow section shows money that actually came in and went out.',
                'Tap the category icon at the top right to add or edit your own expense categories.'],
               ['Ingredient purchases (restocks) are recorded automatically as Ingredients expenses and affect cash flow, but do not reduce profit because they are already counted as cost.']),
    }),
    ('notifications', 'Notifications', 'system', 'NOTIFICATIONS', {
        'id': ('Notifikasi stok dan pengingat', 'Tahu bahan yang menipis sebelum kehabisan.',
               ['Ketuk ikon lonceng di Kasir, atau buka Lainnya, Notifikasi. Titik di tab Lainnya berarti ada yang belum dibaca.',
                'Anda akan diberi tahu saat bahan hampir habis (mencapai stok minimum) dan saat habis, masing-masing sekali per kondisi.',
                'Ketuk ikon pengaturan di pojok kanan atas untuk menyalakan atau mematikan tiap jenis notifikasi.',
                'Di Android 13 ke atas, izinkan notifikasi saat diminta agar peringatan muncul juga di luar aplikasi.'],
               ['Atur Stok minimum tiap bahan dengan wajar supaya Anda sempat membeli sebelum kehabisan, tetapi tidak diganggu terlalu sering.']),
        'en': ('Stock notifications and reminders', 'Know about low ingredients before they run out.',
               ['Tap the bell icon at the cashier, or open More, Notifications. A dot on the More tab means something is unread.',
                'You are told when an ingredient runs low (reaches minimum stock) and when it runs out, once per condition.',
                'Tap the settings icon at the top right to turn each type of notification on or off.',
                'On Android 13 and up, allow notifications when asked so alerts also appear outside the app.'],
               ['Set each ingredient Minimum stock sensibly so you have time to buy before running out, without being nagged too often.']),
    }),
    ('backup', 'SettingsBackupRestore', 'system', 'BACKUP', {
        'id': ('Cadangan dan pulihkan data', 'Amankan data agar tidak hilang bersama ponsel.',
               ['Buka Lainnya, Cadangan dan pulihkan, lalu ketuk Buat cadangan.',
                'Pilih lokasi penyimpanan di luar aplikasi, misalnya penyimpanan ponsel atau kartu memori. Berkas berakhiran .arnbak berisi semua data dan foto.',
                'Pilih pengingat (tiap 3, 7, 14, atau 30 hari) agar tidak lupa mencadangkan.',
                'Untuk memulihkan, ketuk Pulihkan dari berkas dan pilih berkas .arnbak. Aplikasi memeriksa keutuhan berkas dan menampilkan ringkasan sebelum Anda mengonfirmasi.',
                'Sebelum mengganti data, aplikasi otomatis menyimpan salinan pengaman. Setelah selesai, aplikasi dimulai ulang.'],
               ['Berkas cadangan tidak dienkripsi dan berisi data penjualan usaha. Simpan di tempat aman dan jangan dibagikan sembarangan.',
                'Hapus semua data di halaman yang sama mengosongkan aplikasi (ada salinan pengaman otomatis).']),
        'en': ('Backup and restore', 'Keep data safe even if the phone is lost.',
               ['Open More, Backup and restore, then tap Create backup.',
                'Choose a storage location outside the app, for example phone storage or a memory card. The file ending in .arnbak holds all your data and photos.',
                'Choose a reminder (every 3, 7, 14 or 30 days) so you do not forget to back up.',
                'To restore, tap Restore from file and choose an .arnbak file. The app checks the file is intact and shows a summary before you confirm.',
                'Before replacing data, the app automatically keeps a safety copy. When done, the app restarts.'],
               ['The backup file is not encrypted and contains your sales data. Keep it somewhere safe and do not share it casually.',
                'Delete all data on the same page empties the app (a safety copy is made automatically).']),
    }),
    ('profile', 'Storefront', 'system', 'PROFILE', {
        'id': ('Profil toko, tema, dan bahasa', 'Sesuaikan identitas dan tampilan aplikasi.',
               ['Buka Lainnya, lalu ketuk kartu profil di bagian atas untuk mengisi nama usaha, alamat, telepon, logo, dan simbol mata uang. Data ini tercetak di struk.',
                'Ketuk Tema untuk memilih Ikuti sistem, Terang, atau Gelap. Warna dinamis (Android 12 ke atas) mengambil warna dari wallpaper.',
                'Ketuk Bahasa untuk beralih antara Bahasa Indonesia dan English. Perubahan langsung berlaku tanpa memulai ulang.'],
               ['Logo tampil di Halaman Utama dan bisa diganti kapan saja.']),
        'en': ('Shop profile, theme and language', 'Set up the identity and look of the app.',
               ['Open More, then tap the profile card at the top to enter the business name, address, phone, logo and currency symbol. This is printed on receipts.',
                'Tap Theme to choose Follow system, Light or Dark. Dynamic colour (Android 12 and up) takes colours from your wallpaper.',
                'Tap Language to switch between Bahasa Indonesia and English. It takes effect right away without restarting.'],
               ['The logo shows on the home screen and can be changed any time.']),
    }),
    ('sample', 'Science', 'system', 'BACKUP', {
        'id': ('Mencoba dengan data contoh', 'Isi aplikasi dengan data kedai kopi fiktif.',
               ['Saat aplikasi masih kosong, ketuk Coba dengan data contoh di Halaman Utama. Atau buka Lainnya, Cadangan dan pulihkan, bagian Data contoh.',
                'Pilih ukuran: Sedikit (3 hari), Sedang (14 hari), atau Banyak (45 hari dan ribuan baris riwayat).',
                'Tunggu sampai selesai. Skenario Banyak butuh sekitar satu sampai dua menit.',
                'Jelajahi Kasir, Kelola, dan Laporan dengan data yang sudah terisi.',
                'Untuk memakai data sungguhan, buka Cadangan dan pulihkan, lalu Hapus semua data.'],
               ['Data contoh hanya bisa dimuat saat aplikasi masih kosong, jadi tidak akan menimpa data usaha Anda.']),
        'en': ('Trying the app with sample data', 'Fill the app with a fictional coffee shop.',
               ['While the app is still empty, tap Try with sample data on the home screen. Or open More, Backup and restore, the Sample data section.',
                'Choose a size: Small (3 days), Medium (14 days) or Large (45 days and thousands of history rows).',
                'Wait until it finishes. The Large scenario takes about one to two minutes.',
                'Explore Cashier, Manage and Reports with the data filled in.',
                'To use real data, open Backup and restore, then Delete all data.'],
               ['Sample data can only be loaded while the app is empty, so it will never overwrite your business data.']),
    }),
]

from guide_tours import TOURS, ROUTES as TOUR_ROUTES  # noqa: E402

ROUTES = {
    'HOME': 'Routes.HOME', 'MANAGE0': 'Routes.manage(ManageTab.MENU)', 'MANAGE1': 'Routes.manage(ManageTab.INGREDIENT)',
    'MANAGE2': 'Routes.manage(ManageTab.CATEGORY)', 'REPORTS0': 'Routes.reports(ReportTab.INCOME)',
    'REPORTS1': 'Routes.reports(ReportTab.EXPENSES)', 'NOTIFICATIONS': 'Routes.NOTIFICATIONS',
    'BACKUP': 'Routes.BACKUP', 'PROFILE': 'Routes.PROFILE',
}


def esc(v):
    return v.replace('&', '&amp;').replace('<', '&lt;').replace("'", "\\'").replace('"', '\\"')


def build(lang):
    out = ['    <!-- Sambutan, panduan, tutorial (dibangkitkan oleh tools gen_guide) -->']
    for k, v in COMMON[lang].items():
        out.append('    <string name="%s">%s</string>' % (k, esc(v)))
    for i, (t, sm, steps) in enumerate(PAGES[lang], 1):
        out.append('    <string name="gp%d_title">%s</string>' % (i, esc(t)))
        out.append('    <string name="gp%d_sum">%s</string>' % (i, esc(sm)))
        out.append('    <string-array name="gp%d_steps">' % i)
        out += ['        <item>%s</item>' % esc(x) for x in steps]
        out.append('    </string-array>')
    for tid, _icon, _sec, _route, texts in TOPICS:
        t, sm, _steps, _tips = texts[lang]
        out.append('    <string name="gt_%s_title">%s</string>' % (tid, esc(t)))
        out.append('    <string name="gt_%s_sum">%s</string>' % (tid, esc(sm)))
    for tid, steps in TOURS.items():
        for n, (_route, _target, _opt, texts) in enumerate(steps):
            title, text = texts[lang]
            out.append('    <string name="gs_%s_%d_title">%s</string>' % (tid, n, esc(title)))
            out.append('    <string name="gs_%s_%d_text">%s</string>' % (tid, n, esc(text)))
    return '\n'.join(out) + '\n'


for lang, rel in (('id', 'values'), ('en', 'values-en')):
    p = os.path.join(ROOT, 'res', rel, 'strings.xml')
    s = open(p, encoding='utf-8').read()
    i = s.index('    <!-- Sambutan, panduan, tutorial')
    s = s[:i] + build(lang) + '</resources>\n'
    open(p, 'w', encoding='utf-8', newline='\n').write(s)

# --------------------------------------------------------------------------- Kotlin
kt = ['package com.example.kasirarn.ui.onboarding', '',
      'import androidx.compose.material.icons.Icons',
      'import androidx.compose.material.icons.outlined.AddShoppingCart',
      'import androidx.compose.material.icons.outlined.BarChart',
      'import androidx.compose.material.icons.outlined.Category',
      'import androidx.compose.material.icons.outlined.Inventory2',
      'import androidx.compose.material.icons.outlined.MenuBook',
      'import androidx.compose.material.icons.outlined.Notifications',
      'import androidx.compose.material.icons.outlined.Payments',
      'import androidx.compose.material.icons.outlined.PointOfSale',
      'import androidx.compose.material.icons.outlined.Science',
      'import androidx.compose.material.icons.outlined.SettingsBackupRestore',
      'import androidx.compose.material.icons.outlined.Storefront',
      'import androidx.compose.ui.graphics.vector.ImageVector',
      'import com.example.kasirarn.R',
      'import com.example.kasirarn.ui.nav.ManageTab',
      'import com.example.kasirarn.ui.nav.ReportTab',
      'import com.example.kasirarn.ui.nav.Routes', '',
      '// Dibangkitkan oleh gen_guide.py; ubah isinya di skrip (tools/guide_tours.py), bukan di sini.', '',
      'enum class GuideSection(val labelRes: Int) {']
kt += ['    SELL(R.string.guide_sec_sell),', '    MANAGE(R.string.guide_sec_manage),',
       '    REPORTS(R.string.guide_sec_reports),', '    SYSTEM(R.string.guide_sec_system),', '}', '',
       'class GuideTopic(',
       '    val id: String,', '    val icon: ImageVector,', '    val section: GuideSection,',
       '    val titleRes: Int,', '    val summaryRes: Int,', ')', '',
       'val GuideTopics = listOf(']
for tid, icon, sec, route, _ in TOPICS:
    kt.append('    GuideTopic("%s", Icons.Outlined.%s, GuideSection.%s, R.string.gt_%s_title, R.string.gt_%s_sum),'
              % (tid, icon, sec.upper(), tid, tid))
kt.append(')')
kt += ['', '/** Langkah tutorial interaktif per fitur: layar tujuan, bagian yang disorot, dan teksnya. */',
       'val GuideScripts: Map<String, List<CoachStep>> = mapOf(']
for tid, steps in TOURS.items():
    kt.append('    "%s" to listOf(' % tid)
    for n, (route, target, opt, _texts) in enumerate(steps):
        kt.append('        CoachStep(%s, %s, R.string.gs_%s_%d_title, R.string.gs_%s_%d_text%s),'
                  % ('CoachId.' + target if target else 'null', TOUR_ROUTES[route], tid, n, tid, n, ', optional = true' if opt else ''))
    kt.append('    ),')
kt.append(')')
open(os.path.join(J, 'ui', 'onboarding', 'GuideTopics.kt'), 'w', encoding='utf-8', newline='\n').write('\n'.join(kt) + '\n')
print('ok', len(TOPICS), 'topik', sum(len(v) for v in TOURS.values()), 'langkah')
