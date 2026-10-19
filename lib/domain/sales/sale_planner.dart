import 'package:arn_kasir/domain/core/failures.dart';
import 'package:arn_kasir/domain/core/rounding.dart';
import 'package:arn_kasir/domain/inventory/ingredient.dart';
import 'package:arn_kasir/domain/inventory/stock_change.dart';
import 'package:arn_kasir/domain/inventory/stock_state.dart';
import 'package:arn_kasir/domain/menu/availability.dart';
import 'package:arn_kasir/domain/menu/stock_catalog.dart';
import 'package:arn_kasir/domain/sales/cart.dart';
import 'package:arn_kasir/domain/sales/payment.dart';
import 'package:arn_kasir/domain/sales/sale_record.dart';

/// Rencana penyimpanan sebuah penjualan: seluruh nilai sudah dihitung, tinggal ditulis
/// ke penyimpanan dalam satu transaksi database (CLAUDE.md aturan 4).
final class SalePlan {
  const SalePlan({
    required this.sale,
    required this.stockChanges,
    required this.alerts,
    required this.overdrawnIngredientIds,
  });

  /// Transaksi lengkap dengan snapshot; `id` masih `null`.
  final SaleRecord sale;

  /// Perubahan stok per bahan, berurutan menurut pemakaian pertama.
  final List<StockChange> stockChanges;

  /// Bahan yang memburuk ke tingkat menipis atau habis akibat penjualan ini.
  final List<StockAlert> alerts;

  /// Bahan yang stoknya menjadi negatif (kebijakan peringatkan): perlu opname (TC D3).
  final List<int> overdrawnIngredientIds;
}

/// Rencana pembatalan: stok dikembalikan dari snapshot transaksi.
final class VoidPlan {
  const VoidPlan({required this.saleId, required this.stockChanges});

  final int saleId;
  final List<StockChange> stockChanges;
}

abstract final class SalePlanner {
  /// Menyusun rencana penjualan. Fungsi murni: tidak menyentuh penyimpanan.
  ///
  /// Urutan pemakaian bahan deterministik (baris keranjang, lalu urutan resep) karena
  /// pembulatan biaya bergantung pada keadaan stok pada saat tiap pemakaian.
  static SalePlan plan({
    required StockCatalog catalog,
    required List<CartLine> lines,
    required Discount discount,
    required PaymentMethod method,
    required StockPolicy policy,
    required DateTime time,
    required String number,
    int? paid,
    String? note,
  }) {
    if (lines.isEmpty) throw const EmptyCart();
    for (final line in lines) {
      if (line.quantity <= 0) throw const NonPositiveQuantity();
      final menu = catalog.menus[line.menuId];
      if (menu == null || !menu.active) throw MenuUnavailable(line.menuId);
    }

    final shortages = findShortages(
      lines.map((l) => DemandLine(menuId: l.menuId, quantity: l.quantity)),
      catalog,
    );
    if (policy == StockPolicy.block && shortages.isNotEmpty) {
      throw InsufficientStock(shortages);
    }

    final totals = Cart(lines: lines, discount: discount).totals();
    final payment = settlePayment(method: method, total: totals.total, paid: paid);

    // Map literal berurutan menurut penyisipan, jadi urutan perubahan stok stabil.
    final before = <int, StockState>{};
    final working = <int, StockState>{};
    final items = <SaleItemRecord>[];

    for (final line in lines) {
      final menu = catalog.menus[line.menuId]!;
      final usages = <UsageRecord>[];
      var itemCost = 0;

      for (final recipeLine in menu.recipe) {
        final ingredient = catalog.ingredients[recipeLine.ingredientId];
        if (ingredient == null) {
          throw IngredientNotFound(recipeLine.ingredientId);
        }
        before.putIfAbsent(ingredient.id, () => ingredient.stock);
        final current = working[ingredient.id] ?? ingredient.stock;
        final usage = recipeLine.quantity * line.quantity;
        final result = StockLedger.consume(current, usage);
        working[ingredient.id] = result.after;
        usages.add(
          UsageRecord(
            ingredientId: ingredient.id,
            baseQuantity: usage,
            cost: result.cost,
          ),
        );
        itemCost += result.cost;
      }

      items.add(
        SaleItemRecord(
          menuId: menu.id,
          menuName: line.menuName,
          unitPrice: line.unitPrice,
          quantity: line.quantity,
          unitCost: divRoundHalfUp(itemCost, line.quantity),
          usages: List.unmodifiable(usages),
          note: line.note,
        ),
      );
    }

    final changes = <StockChange>[];
    final alerts = <StockAlert>[];
    final overdrawn = <int>[];
    working.forEach((ingredientId, after) {
      changes.add(
        StockChange(
          ingredientId: ingredientId,
          before: before[ingredientId]!,
          after: after,
        ),
      );
      final Ingredient ingredient = catalog.ingredients[ingredientId]!;
      final alert = detectAlert(ingredient, after);
      if (alert != null) alerts.add(alert);
      if (after.quantity < 0) overdrawn.add(ingredientId);
    });

    return SalePlan(
      sale: SaleRecord(
        number: number,
        time: time,
        subtotal: totals.subtotal,
        discount: totals.discount,
        total: totals.total,
        method: method,
        paid: payment.paid,
        change: payment.change,
        status: SaleStatus.completed,
        note: note,
        items: List.unmodifiable(items),
      ),
      stockChanges: List.unmodifiable(changes),
      alerts: List.unmodifiable(alerts),
      overdrawnIngredientIds: List.unmodifiable(overdrawn),
    );
  }

  /// Menyusun rencana pembatalan dari snapshot [sale] (TC F1 sampai F5).
  ///
  /// Pemakaian dijumlahkan per bahan dan dikembalikan persis seperti tercatat pada
  /// snapshot (jumlah dan biaya), bukan dihitung ulang dari resep terbaru.
  static VoidPlan planVoid({
    required SaleRecord sale,
    required Map<int, Ingredient> ingredients,
  }) {
    final saleId = sale.id;
    if (saleId == null) throw ArgumentError('Transaksi belum tersimpan');
    if (sale.status == SaleStatus.voided) throw SaleAlreadyVoided(saleId);

    final quantities = <int, int>{};
    final costs = <int, int>{};
    for (final item in sale.items) {
      for (final usage in item.usages) {
        quantities.update(
          usage.ingredientId,
          (value) => value + usage.baseQuantity,
          ifAbsent: () => usage.baseQuantity,
        );
        costs.update(
          usage.ingredientId,
          (value) => value + usage.cost,
          ifAbsent: () => usage.cost,
        );
      }
    }

    final changes = <StockChange>[];
    quantities.forEach((ingredientId, quantity) {
      final ingredient = ingredients[ingredientId];
      if (ingredient == null) throw IngredientNotFound(ingredientId);
      final after = StockLedger.reverseConsume(
        ingredient.stock,
        quantity: quantity,
        cost: costs[ingredientId]!,
      );
      changes.add(
        StockChange(
          ingredientId: ingredientId,
          before: ingredient.stock,
          after: after,
        ),
      );
    });
    return VoidPlan(saleId: saleId, stockChanges: List.unmodifiable(changes));
  }
}
