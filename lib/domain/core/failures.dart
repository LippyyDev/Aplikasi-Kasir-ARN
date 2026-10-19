import 'package:arn_kasir/domain/units/dimension.dart';

/// Galat aturan bisnis yang dapat dipulihkan dan ditampilkan kepada pengguna.
///
/// Seluruh subkelas berada dalam satu berkas agar `switch` pada lapisan UI bersifat
/// eksaustif (kelas `sealed`). Kesalahan pemrograman memakai [ArgumentError] atau
/// [StateError], bukan kelas di sini.
sealed class DomainFailure implements Exception {
  const DomainFailure();

  @override
  String toString() => '$runtimeType';
}

// --- Angka dan satuan -------------------------------------------------------

/// Teks bukan angka desimal yang valid (mis. "abc", "2,,5", "", "1e3").
final class InvalidNumberFormat extends DomainFailure {
  const InvalidNumberFormat(this.input);
  final String input;

  @override
  String toString() => 'InvalidNumberFormat("$input")';
}

/// Jumlah nol atau negatif pada operasi yang mensyaratkan jumlah positif.
final class NonPositiveQuantity extends DomainFailure {
  const NonPositiveQuantity();
}

/// Jumlah positif yang setelah dibulatkan ke satuan dasar menjadi nol
/// (mis. 0,04 g). Ditolak untuk resep dan restok.
final class QuantityRoundsToZero extends DomainFailure {
  const QuantityRoundsToZero();
}

/// Konversi antardimensi (massa ke volume dan sebagainya) tidak diizinkan.
final class DimensionMismatch extends DomainFailure {
  const DimensionMismatch({
    required this.ingredientDimension,
    required this.unitName,
    required this.unitDimension,
  });

  final Dimension ingredientDimension;
  final String unitName;
  final Dimension unitDimension;

  @override
  String toString() =>
      'DimensionMismatch($unitName: ${unitDimension.name} bukan ${ingredientDimension.name})';
}

final class UnknownUnit extends DomainFailure {
  const UnknownUnit(this.name);
  final String name;

  @override
  String toString() => 'UnknownUnit("$name")';
}

enum InvalidUnitReason { emptyName, nameCollision, nonPositiveFactor }

final class InvalidUnitDefinition extends DomainFailure {
  const InvalidUnitDefinition(this.name, this.reason);
  final String name;
  final InvalidUnitReason reason;

  @override
  String toString() => 'InvalidUnitDefinition("$name", ${reason.name})';
}

/// Hasil antara tidak muat dalam `int` 64 bit.
final class ArithmeticOverflow extends DomainFailure {
  const ArithmeticOverflow();
}

// --- Resep dan menu ---------------------------------------------------------

enum InvalidRecipeReason { duplicateIngredient, nonPositiveQuantity }

final class InvalidRecipe extends DomainFailure {
  const InvalidRecipe(this.reason);
  final InvalidRecipeReason reason;

  @override
  String toString() => 'InvalidRecipe(${reason.name})';
}

/// Harga atau nominal uang tidak valid (negatif).
final class InvalidMoneyAmount extends DomainFailure {
  const InvalidMoneyAmount(this.amount);
  final int amount;

  @override
  String toString() => 'InvalidMoneyAmount($amount)';
}

// --- Stok dan penjualan -----------------------------------------------------

final class StockShortage {
  const StockShortage({
    required this.ingredientId,
    required this.ingredientName,
    required this.required,
    required this.available,
  });

  final int ingredientId;
  final String ingredientName;

  /// Kebutuhan total dalam satuan dasar (dijumlahkan seluruh item keranjang).
  final int required;

  /// Stok tersedia dalam satuan dasar (bisa nol atau negatif).
  final int available;

  @override
  String toString() =>
      'StockShortage($ingredientName: perlu $required, ada $available)';
}

/// Stok bahan tidak cukup dan kebijakan stok adalah blokir.
final class InsufficientStock extends DomainFailure {
  const InsufficientStock(this.shortages);
  final List<StockShortage> shortages;

  @override
  String toString() => 'InsufficientStock($shortages)';
}

/// Menu tidak ditemukan, sudah dihapus, atau tidak aktif.
final class MenuUnavailable extends DomainFailure {
  const MenuUnavailable(this.menuId);
  final int menuId;

  @override
  String toString() => 'MenuUnavailable($menuId)';
}

final class EmptyCart extends DomainFailure {
  const EmptyCart();
}

enum InvalidDiscountReason { negative, exceedsSubtotal, percentOutOfRange }

final class InvalidDiscount extends DomainFailure {
  const InvalidDiscount(this.reason);
  final InvalidDiscountReason reason;

  @override
  String toString() => 'InvalidDiscount(${reason.name})';
}

final class InsufficientPayment extends DomainFailure {
  const InsufficientPayment({required this.total, required this.paid});
  final int total;
  final int paid;

  @override
  String toString() => 'InsufficientPayment(total $total, dibayar $paid)';
}

final class SaleNotFound extends DomainFailure {
  const SaleNotFound(this.saleId);
  final int saleId;

  @override
  String toString() => 'SaleNotFound($saleId)';
}

final class SaleAlreadyVoided extends DomainFailure {
  const SaleAlreadyVoided(this.saleId);
  final int saleId;

  @override
  String toString() => 'SaleAlreadyVoided($saleId)';
}

final class IngredientNotFound extends DomainFailure {
  const IngredientNotFound(this.ingredientId);
  final int ingredientId;

  @override
  String toString() => 'IngredientNotFound($ingredientId)';
}

// --- Integritas data --------------------------------------------------------

/// Bahan masih dipakai resep menu aktif (PRD 9.1).
final class IngredientInUse extends DomainFailure {
  const IngredientInUse(this.menuNames);
  final List<String> menuNames;

  @override
  String toString() => 'IngredientInUse($menuNames)';
}

enum NameSubject { ingredient, menu, menuCategory, expenseCategory, unit }

/// Nama harus unik (bahan, kategori menu, kategori pengeluaran, satuan).
final class DuplicateName extends DomainFailure {
  const DuplicateName(this.subject, this.name);
  final NameSubject subject;
  final String name;

  @override
  String toString() => 'DuplicateName(${subject.name}, "$name")';
}

final class EmptyName extends DomainFailure {
  const EmptyName(this.subject);
  final NameSubject subject;

  @override
  String toString() => 'EmptyName(${subject.name})';
}

/// Kategori pengeluaran bawaan sistem tidak dapat diubah atau dihapus.
final class ProtectedRecord extends DomainFailure {
  const ProtectedRecord();
}

/// Dimensi bahan tidak dapat diubah setelah bahan punya riwayat atau dipakai resep,
/// karena satuan dasar tempat stok dan resep disimpan akan berubah makna.
final class DimensionLocked extends DomainFailure {
  const DimensionLocked();
}

/// Kategori pengeluaran masih dipakai catatan pengeluaran dan tidak dapat dihapus.
final class CategoryInUse extends DomainFailure {
  const CategoryInUse();
}

/// Data tidak ditemukan (id tidak ada atau sudah dihapus).
final class RecordNotFound extends DomainFailure {
  const RecordNotFound();
}
