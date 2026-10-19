import 'package:arn_kasir/domain/core/clock.dart';
import 'package:arn_kasir/domain/core/failures.dart';
import 'package:arn_kasir/domain/inventory/ingredient.dart';
import 'package:arn_kasir/domain/inventory/stock_change.dart';
import 'package:arn_kasir/domain/menu/availability.dart';
import 'package:arn_kasir/domain/menu/stock_catalog.dart';
import 'package:arn_kasir/domain/sales/cart.dart';
import 'package:arn_kasir/domain/sales/payment.dart';
import 'package:arn_kasir/domain/sales/sale_planner.dart';
import 'package:arn_kasir/domain/sales/sale_record.dart';

/// Penyimpanan yang dibutuhkan use case penjualan. Implementasi nyata ada di lapisan
/// data (Drift); uji domain memakai implementasi dalam memori.
abstract interface class SaleStore {
  /// Menjalankan [action] secara atomik: bila melempar galat, tidak ada perubahan yang
  /// tersimpan (TC E8).
  Future<T> transaction<T>(Future<T> Function() action);

  /// Memuat menu (beserta resep) untuk [menuIds] dan semua bahan yang dirujuk resepnya,
  /// termasuk bahan yang diarsipkan.
  Future<StockCatalog> loadCatalog(Iterable<int> menuIds);

  /// Mengalokasikan nomor transaksi berikutnya untuk hari pada [localTime]
  /// (nomor urut harian berdasarkan urutan penyimpanan, bukan jam; PRD 9.4).
  Future<String> allocateReceiptNumber(DateTime localTime);

  /// Menulis transaksi, item, snapshot bahan, pengurangan stok, dan pergerakan stok
  /// jenis `penjualan`. Mengembalikan id transaksi.
  Future<int> insertSale(SalePlan plan);

  Future<SaleRecord?> findSale(int saleId);

  /// Memuat bahan menurut id, termasuk yang diarsipkan.
  Future<Map<int, Ingredient>> loadIngredients(Iterable<int> ids);

  /// Menandai transaksi batal, mengembalikan stok, dan mencatat pergerakan jenis `batal`.
  Future<void> applyVoid(VoidPlan plan, DateTime time);
}

final class CheckoutRequest {
  const CheckoutRequest({
    required this.lines,
    required this.method,
    required this.policy,
    this.discount = const NoDiscount(),
    this.paid,
    this.note,
  });

  final List<CartLine> lines;
  final Discount discount;
  final PaymentMethod method;

  /// Jumlah dibayar (tunai). `null` berarti uang pas.
  final int? paid;
  final StockPolicy policy;
  final String? note;
}

final class CheckoutResult {
  const CheckoutResult({
    required this.sale,
    required this.alerts,
    required this.overdrawnIngredientIds,
  });

  final SaleRecord sale;
  final List<StockAlert> alerts;
  final List<int> overdrawnIngredientIds;
}

/// Menyimpan satu penjualan dalam satu transaksi database (CLAUDE.md aturan 4).
final class CheckoutUseCase {
  CheckoutUseCase(this._store, {Clock? clock}) : _clock = clock ?? systemClock;

  final SaleStore _store;
  final Clock _clock;

  Future<CheckoutResult> call(CheckoutRequest request) async {
    if (request.lines.isEmpty) throw const EmptyCart();
    final now = _clock();

    return _store.transaction(() async {
      final catalog = await _store.loadCatalog(
        request.lines.map((line) => line.menuId),
      );
      final number = await _store.allocateReceiptNumber(now.toLocal());
      final plan = SalePlanner.plan(
        catalog: catalog,
        lines: request.lines,
        discount: request.discount,
        method: request.method,
        paid: request.paid,
        policy: request.policy,
        time: now,
        number: number,
        note: request.note,
      );
      final id = await _store.insertSale(plan);
      return CheckoutResult(
        sale: plan.sale.copyWith(id: id),
        alerts: plan.alerts,
        overdrawnIngredientIds: plan.overdrawnIngredientIds,
      );
    });
  }
}

/// Membatalkan transaksi (void): stok dikembalikan dari snapshot, transaksi tidak
/// dihapus (PRD 6.3.6, TC F1 sampai F5).
final class VoidSaleUseCase {
  VoidSaleUseCase(this._store, {Clock? clock}) : _clock = clock ?? systemClock;

  final SaleStore _store;
  final Clock _clock;

  Future<void> call(int saleId) {
    final now = _clock();
    return _store.transaction(() async {
      final sale = await _store.findSale(saleId);
      if (sale == null) throw SaleNotFound(saleId);
      if (sale.status == SaleStatus.voided) throw SaleAlreadyVoided(saleId);

      final ingredientIds = <int>{
        for (final item in sale.items)
          for (final usage in item.usages) usage.ingredientId,
      };
      final ingredients = await _store.loadIngredients(ingredientIds);
      final plan = SalePlanner.planVoid(sale: sale, ingredients: ingredients);
      await _store.applyVoid(plan, now);
    });
  }
}
