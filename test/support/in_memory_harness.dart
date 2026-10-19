import 'package:arn_kasir/domain/inventory/inventory_store.dart';
import 'package:arn_kasir/domain/inventory/stock_state.dart';
import 'package:arn_kasir/domain/menu/menu_item.dart';
import 'package:arn_kasir/domain/menu/recipe.dart';
import 'package:arn_kasir/domain/sales/sale_record.dart';
import 'package:arn_kasir/domain/sales/sale_store.dart';

import 'fixtures.dart';
import 'in_memory_store.dart';
import 'scenario_harness.dart';

final class InMemoryHarness extends ScenarioHarness {
  final InMemoryStore store = InMemoryStore();

  @override
  SaleStore get saleStore => store;

  @override
  InventoryStore get inventoryStore => store;

  @override
  Future<void> seedFixtureA() async {
    for (final ingredient in FixtureA.ingredients()) {
      store.ingredients[ingredient.id] = ingredient;
    }
    for (final menu in FixtureA.menus()) {
      store.menus[menu.id] = menu;
    }
  }

  @override
  Future<int> ingredientId(String name) async =>
      store.ingredients.values.firstWhere((i) => i.name == name).id;

  @override
  Future<int> menuId(String name) async =>
      store.menus.values.firstWhere((m) => m.name == name).id;

  @override
  Future<StockState> stockOf(String ingredientName) async =>
      store.ingredients.values
          .firstWhere((i) => i.name == ingredientName)
          .stock;

  @override
  Future<void> overrideStock(String ingredientName, StockState state) async {
    final ingredient =
        store.ingredients.values.firstWhere((i) => i.name == ingredientName);
    store.ingredients[ingredient.id] = ingredient.copyWith(stock: state);
  }

  @override
  Future<void> changeRecipe(String menuName, Map<String, int> recipe) async {
    final menu = store.menus.values.firstWhere((m) => m.name == menuName);
    store.menus[menu.id] = MenuItem(
      id: menu.id,
      name: menu.name,
      price: menu.price,
      categoryId: menu.categoryId,
      active: menu.active,
      recipe: <RecipeLine>[
        for (final entry in recipe.entries)
          RecipeLine(
            ingredientId: store.ingredients.values
                .firstWhere((i) => i.name == entry.key)
                .id,
            quantity: entry.value,
          ),
      ],
    );
  }

  @override
  Future<SaleRecord> loadSale(int saleId) async =>
      store.sales.firstWhere((s) => s.id == saleId);

  @override
  Future<int> saleCount() async => store.sales.length;

  @override
  Future<List<MovementView>> movements() async => <MovementView>[
        for (final m in store.movements)
          MovementView(
            ingredientName: store.ingredients[m.ingredientId]!.name,
            type: m.type,
            quantityDelta: m.quantityDelta,
            valueDelta: m.valueDelta,
            referenceId: m.referenceId,
            reason: m.reason,
          ),
      ];

  @override
  Future<List<ExpenseView>> expenses() async => <ExpenseView>[
        for (final e in store.expenses)
          ExpenseView(
            categoryId: e.categoryId,
            amount: e.amount,
            source: e.source,
            referenceId: e.referenceId,
          ),
      ];

  @override
  void failOnStockUpdate(int updateNumber) =>
      store.failOnStockUpdate(updateNumber);

  @override
  Future<void> dispose() async {}
}
