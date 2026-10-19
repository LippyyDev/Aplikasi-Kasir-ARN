import 'package:drift/drift.dart';

// Definisi tabel mengikuti PRD bagian 8. Identifier Dart berbahasa Inggris; nama tabel dan
// kolom SQL berbahasa Indonesia lewat `tableName` dan `.named(...)` (CLAUDE.md, Konvensi).
// Uang adalah INTEGER Rupiah, waktu adalah INTEGER epoch milidetik UTC, tanpa `double`.

@DataClassName('MenuCategoryRow')
class MenuCategories extends Table {
  @override
  String get tableName => 'kategori_menu';

  IntColumn get id => integer().autoIncrement()();
  TextColumn get name => text().named('nama').unique()();
  IntColumn get sortOrder =>
      integer().named('urutan').withDefault(const Constant(0))();
}

@DataClassName('MenuRow')
@TableIndex(name: 'idx_menu_kategori', columns: {#categoryId})
class Menus extends Table {
  @override
  String get tableName => 'menu';

  IntColumn get id => integer().autoIncrement()();
  TextColumn get name => text().named('nama')();
  IntColumn get categoryId => integer()
      .named('kategori_id')
      .nullable()
      .references(MenuCategories, #id, onDelete: KeyAction.setNull)();
  IntColumn get price => integer().named('harga_jual')();
  TextColumn get photoPath => text().named('foto_path').nullable()();
  BoolColumn get active =>
      boolean().named('aktif').withDefault(const Constant(true))();
  TextColumn get note => text().named('catatan').nullable()();
  IntColumn get createdAt => integer().named('dibuat')();
  IntColumn get updatedAt => integer().named('diubah')();

  /// Hapus lunak: tidak `null` berarti menu dihapus.
  IntColumn get deletedAt => integer().named('dihapus_pada').nullable()();
}

@DataClassName('IngredientRow')
class Ingredients extends Table {
  @override
  String get tableName => 'bahan_baku';

  IntColumn get id => integer().autoIncrement()();
  TextColumn get name => text().named('nama').unique()();

  /// `massa`, `volume`, atau `hitungan`.
  TextColumn get dimension => text().named('dimensi')();
  TextColumn get baseUnit => text().named('satuan_dasar')();
  TextColumn get purchaseUnit => text().named('satuan_beli').nullable()();
  IntColumn get baseStock =>
      integer().named('stok_dasar').withDefault(const Constant(0))();
  IntColumn get stockValue =>
      integer().named('nilai_stok').withDefault(const Constant(0))();
  IntColumn get minStock =>
      integer().named('stok_minimum').withDefault(const Constant(0))();

  /// Diarsipkan bila tidak `null`.
  IntColumn get deletedAt => integer().named('dihapus_pada').nullable()();
}

@DataClassName('IngredientUnitRow')
class IngredientUnits extends Table {
  @override
  String get tableName => 'takaran_bahan';

  IntColumn get id => integer().autoIncrement()();
  IntColumn get ingredientId => integer()
      .named('bahan_id')
      .references(Ingredients, #id, onDelete: KeyAction.cascade)();
  TextColumn get unitName => text().named('nama_satuan')();

  /// Faktor ke satuan dasar sebagai pecahan bilangan bulat (2,5 g = 5/2).
  IntColumn get factorNumerator => integer().named('faktor_pembilang')();
  IntColumn get factorDenominator => integer().named('faktor_penyebut')();

  @override
  List<Set<Column>> get uniqueKeys => [
        {ingredientId, unitName},
      ];
}

@DataClassName('RecipeItemRow')
class RecipeItems extends Table {
  @override
  String get tableName => 'resep_item';

  IntColumn get id => integer().autoIncrement()();
  IntColumn get menuId => integer()
      .named('menu_id')
      .references(Menus, #id, onDelete: KeyAction.cascade)();
  IntColumn get ingredientId =>
      integer().named('bahan_id').references(Ingredients, #id)();
  IntColumn get baseQuantity => integer().named('jumlah_dasar')();

  @override
  List<Set<Column>> get uniqueKeys => [
        {menuId, ingredientId},
      ];
}

@DataClassName('SaleRow')
@TableIndex(name: 'idx_transaksi_waktu', columns: {#time})
class SaleTransactions extends Table {
  @override
  String get tableName => 'transaksi';

  IntColumn get id => integer().autoIncrement()();
  TextColumn get number => text().named('nomor').unique()();
  IntColumn get time => integer().named('waktu')();
  IntColumn get subtotal => integer()();
  IntColumn get discount => integer().named('diskon')();
  IntColumn get total => integer()();

  /// `tunai` atau `non_tunai`.
  TextColumn get paymentMethod => text().named('metode_bayar')();
  IntColumn get paid => integer().named('dibayar')();
  IntColumn get change => integer().named('kembalian')();

  /// `selesai` atau `batal`.
  TextColumn get status => text().withDefault(const Constant('selesai'))();
  TextColumn get note => text().named('catatan').nullable()();
}

@DataClassName('SaleItemRow')
@TableIndex(name: 'idx_transaksi_item_transaksi', columns: {#saleId})
class SaleItems extends Table {
  @override
  String get tableName => 'transaksi_item';

  IntColumn get id => integer().autoIncrement()();
  IntColumn get saleId =>
      integer().named('transaksi_id').references(SaleTransactions, #id)();
  IntColumn get menuId => integer().named('menu_id').references(Menus, #id)();

  // Nama dan harga disalin sebagai snapshot (CLAUDE.md aturan 5).
  TextColumn get menuName => text().named('nama_menu')();
  IntColumn get unitPrice => integer().named('harga_satuan')();
  IntColumn get quantity => integer().named('kuantitas')();
  IntColumn get unitCost => integer().named('hpp_satuan')();
  TextColumn get note => text().named('catatan').nullable()();
}

@DataClassName('SaleUsageRow')
@TableIndex(name: 'idx_transaksi_bahan_item', columns: {#saleItemId})
class SaleIngredientUsages extends Table {
  @override
  String get tableName => 'transaksi_bahan';

  IntColumn get id => integer().autoIncrement()();
  IntColumn get saleItemId =>
      integer().named('transaksi_item_id').references(SaleItems, #id)();
  IntColumn get ingredientId =>
      integer().named('bahan_id').references(Ingredients, #id)();
  IntColumn get baseQuantity => integer().named('jumlah_dasar')();
  IntColumn get cost => integer().named('biaya')();
}

@DataClassName('StockMovementRow')
@TableIndex(name: 'idx_pergerakan_bahan_waktu', columns: {#ingredientId, #time})
class StockMovements extends Table {
  @override
  String get tableName => 'pergerakan_stok';

  IntColumn get id => integer().autoIncrement()();
  IntColumn get ingredientId =>
      integer().named('bahan_id').references(Ingredients, #id)();
  IntColumn get time => integer().named('waktu')();

  /// `restok`, `penjualan`, `batal`, atau `penyesuaian`.
  TextColumn get type => text().named('jenis')();

  /// Bertanda: negatif untuk pengeluaran stok.
  IntColumn get baseQuantity => integer().named('jumlah_dasar')();
  IntColumn get value => integer().named('nilai')();

  /// Id transaksi untuk `penjualan` dan `batal`.
  IntColumn get referenceId => integer().named('referensi_id').nullable()();

  /// Alasan penyesuaian (rusak, tumpah, salah hitung) atau catatan restok.
  TextColumn get reason => text().named('alasan').nullable()();
}

@DataClassName('ExpenseCategoryRow')
class ExpenseCategories extends Table {
  @override
  String get tableName => 'kategori_pengeluaran';

  IntColumn get id => integer().autoIncrement()();
  TextColumn get name => text().named('nama').unique()();
}

@DataClassName('ExpenseRow')
@TableIndex(name: 'idx_pengeluaran_tanggal', columns: {#date})
class Expenses extends Table {
  @override
  String get tableName => 'pengeluaran';

  IntColumn get id => integer().autoIncrement()();
  IntColumn get date => integer().named('tanggal')();
  IntColumn get categoryId =>
      integer().named('kategori_id').references(ExpenseCategories, #id)();
  IntColumn get amount => integer().named('nominal')();
  TextColumn get note => text().named('catatan').nullable()();

  /// `manual`, `restok`, atau `susut`.
  TextColumn get source => text().named('sumber').withDefault(const Constant('manual'))();
  IntColumn get referenceId => integer().named('referensi_id').nullable()();
}

@DataClassName('NotificationRow')
class AppNotifications extends Table {
  @override
  String get tableName => 'notifikasi';

  IntColumn get id => integer().autoIncrement()();
  TextColumn get type => text().named('jenis')();
  TextColumn get title => text().named('judul')();
  TextColumn get body => text().named('isi')();
  IntColumn get time => integer().named('waktu')();
  BoolColumn get isRead =>
      boolean().named('dibaca').withDefault(const Constant(false))();
  IntColumn get referenceId => integer().named('referensi_id').nullable()();
}

@DataClassName('BusinessProfileRow')
class BusinessProfiles extends Table {
  @override
  String get tableName => 'profil_usaha';

  /// Selalu 1: hanya ada satu profil usaha.
  IntColumn get id => integer()();
  TextColumn get name => text().named('nama').withDefault(const Constant(''))();
  TextColumn get logoPath => text().named('logo_path').nullable()();
  TextColumn get address => text().named('alamat').nullable()();
  TextColumn get phone => text().named('telepon').nullable()();
  TextColumn get currency => text().named('mata_uang').withDefault(const Constant('Rp'))();

  @override
  Set<Column> get primaryKey => {id};
}
