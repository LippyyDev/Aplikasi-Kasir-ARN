import 'dart:ui';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:arn_kasir/app/services/notification_center.dart';
import 'package:arn_kasir/data/backup/backup_service.dart';
import 'package:arn_kasir/data/backup/restore_service.dart';
import 'package:arn_kasir/data/database/app_database.dart';
import 'package:arn_kasir/data/images/image_store.dart';
import 'package:arn_kasir/data/notifications/system_notifier.dart';
import 'package:arn_kasir/data/paths/app_paths.dart';
import 'package:arn_kasir/data/repositories/category_repository.dart';
import 'package:arn_kasir/data/repositories/drift_inventory_store.dart';
import 'package:arn_kasir/data/repositories/drift_sale_store.dart';
import 'package:arn_kasir/data/repositories/expense_repository.dart';
import 'package:arn_kasir/data/repositories/ingredient_repository.dart';
import 'package:arn_kasir/data/repositories/menu_repository.dart';
import 'package:arn_kasir/data/repositories/notification_repository.dart';
import 'package:arn_kasir/data/repositories/profile_repository.dart';
import 'package:arn_kasir/data/repositories/report_repository.dart';
import 'package:arn_kasir/data/settings/settings_repository.dart';
import 'package:arn_kasir/domain/inventory/ingredient.dart';
import 'package:arn_kasir/domain/inventory/inventory_store.dart';
import 'package:arn_kasir/domain/menu/menu_item.dart';
import 'package:arn_kasir/domain/menu/stock_catalog.dart';
import 'package:arn_kasir/domain/models/app_notification.dart';
import 'package:arn_kasir/domain/models/business_profile.dart';
import 'package:arn_kasir/domain/models/menu_category.dart';
import 'package:arn_kasir/domain/sales/sale_store.dart';
import 'package:arn_kasir/domain/settings/app_settings.dart';

// --- Infrastruktur yang diisi saat bootstrap (override pada ProviderScope) ----------

final appPathsProvider = Provider<AppPaths>(
  (ref) => throw UnimplementedError('appPathsProvider harus di-override'),
);

final databaseProvider = Provider<AppDatabase>(
  (ref) => throw UnimplementedError('databaseProvider harus di-override'),
);

final sharedPreferencesProvider = Provider<SharedPreferences>(
  (ref) => throw UnimplementedError('sharedPreferencesProvider harus di-override'),
);

final systemNotifierProvider = Provider<SystemNotifier>(
  (ref) => throw UnimplementedError('systemNotifierProvider harus di-override'),
);

final appVersionProvider = Provider<String>(
  (ref) => throw UnimplementedError('appVersionProvider harus di-override'),
);

final buildNumberProvider = Provider<String>(
  (ref) => throw UnimplementedError('buildNumberProvider harus di-override'),
);

// --- Penyimpanan dan repositori --------------------------------------------------

final settingsRepositoryProvider = Provider<SettingsRepository>(
  (ref) => SettingsRepository(ref.watch(sharedPreferencesProvider)),
);

final imageStoreProvider = Provider<ImageStore>(
  (ref) => ImageStore(ref.watch(appPathsProvider)),
);

final categoryRepositoryProvider = Provider<CategoryRepository>(
  (ref) => CategoryRepository(ref.watch(databaseProvider)),
);

final menuRepositoryProvider = Provider<MenuRepository>((ref) {
  final images = ref.watch(imageStoreProvider);
  return MenuRepository(
    ref.watch(databaseProvider),
    releasePhoto: images.delete,
  );
});

final ingredientRepositoryProvider = Provider<IngredientRepository>(
  (ref) => IngredientRepository(ref.watch(databaseProvider)),
);

final expenseRepositoryProvider = Provider<ExpenseRepository>(
  (ref) => ExpenseRepository(ref.watch(databaseProvider)),
);

final reportRepositoryProvider = Provider<ReportRepository>(
  (ref) => ReportRepository(ref.watch(databaseProvider)),
);

final notificationRepositoryProvider = Provider<NotificationRepository>(
  (ref) => NotificationRepository(ref.watch(databaseProvider)),
);

final profileRepositoryProvider = Provider<ProfileRepository>(
  (ref) => ProfileRepository(ref.watch(databaseProvider)),
);

final saleStoreProvider = Provider<SaleStore>(
  (ref) => DriftSaleStore(ref.watch(databaseProvider)),
);

final inventoryStoreProvider = Provider<InventoryStore>(
  (ref) => DriftInventoryStore(ref.watch(databaseProvider)),
);

// --- Use case --------------------------------------------------------------------

final checkoutUseCaseProvider = Provider<CheckoutUseCase>(
  (ref) => CheckoutUseCase(ref.watch(saleStoreProvider)),
);

final voidSaleUseCaseProvider = Provider<VoidSaleUseCase>(
  (ref) => VoidSaleUseCase(ref.watch(saleStoreProvider)),
);

final restockUseCaseProvider = Provider<RestockUseCase>(
  (ref) => RestockUseCase(ref.watch(inventoryStoreProvider)),
);

final adjustStockUseCaseProvider = Provider<AdjustStockUseCase>(
  (ref) => AdjustStockUseCase(ref.watch(inventoryStoreProvider)),
);

// --- Pengaturan ------------------------------------------------------------------

/// Pengaturan aplikasi. Perubahan langsung berlaku pada tampilan dan disimpan.
final settingsProvider = NotifierProvider<SettingsController, AppSettings>(
  SettingsController.new,
);

class SettingsController extends Notifier<AppSettings> {
  @override
  AppSettings build() => ref.watch(settingsRepositoryProvider).read();

  Future<void> update(AppSettings Function(AppSettings current) change) async {
    state = change(state);
    await ref.read(settingsRepositoryProvider).write(state);
  }

  Future<void> markBackupDone(DateTime time) =>
      update((s) => s.copyWith(lastBackupAt: time));

  /// Memuat ulang dari penyimpanan setelah `settings.json` hasil pemulihan diterapkan.
  void reload() => state = ref.read(settingsRepositoryProvider).read();
}

// --- Layanan aplikasi -------------------------------------------------------------

final notificationCenterProvider = Provider<NotificationCenter>(
  (ref) => NotificationCenter(
    repository: ref.watch(notificationRepositoryProvider),
    system: ref.watch(systemNotifierProvider),
    // Dibaca saat dipakai, bukan saat dibuat, supaya pengaturan terbaru yang berlaku.
    settings: () => ref.read(settingsProvider),
    platformLocale: () => PlatformDispatcher.instance.locale,
  ),
);

final backupServiceProvider = Provider<BackupService>(
  (ref) => BackupService(
    database: ref.watch(databaseProvider),
    paths: ref.watch(appPathsProvider),
    appVersion: ref.watch(appVersionProvider),
    exportSettings: ref.watch(settingsRepositoryProvider).export,
  ),
);

final restoreServiceProvider = Provider<RestoreService>(
  (ref) => RestoreService(
    paths: ref.watch(appPathsProvider),
    backupService: ref.watch(backupServiceProvider),
    closeDatabase: ref.watch(databaseProvider).close,
    applySettings: ref.watch(settingsRepositoryProvider).import,
  ),
);

// --- Data tampilan (aliran dari database) ----------------------------------------

final categoriesProvider = StreamProvider<List<MenuCategory>>(
  (ref) => ref.watch(categoryRepositoryProvider).watchAll(),
);

final menusProvider = StreamProvider<List<MenuItem>>(
  (ref) => ref.watch(menuRepositoryProvider).watchAll(),
);

/// Bahan yang belum diarsipkan, urut nama. Dipakai daftar dan pemilih resep.
final ingredientsProvider = StreamProvider<List<Ingredient>>(
  (ref) => ref.watch(ingredientRepositoryProvider).watchAll(),
);

/// Termasuk bahan terarsip: perhitungan biaya dan stok harus tetap menemukan bahan yang
/// masih dirujuk riwayat.
final allIngredientsProvider = StreamProvider<List<Ingredient>>(
  (ref) => ref.watch(ingredientRepositoryProvider).watchAll(includeArchived: true),
);

final notificationsProvider = StreamProvider<List<AppNotification>>(
  (ref) => ref.watch(notificationRepositoryProvider).watchAll(),
);

final unreadNotificationCountProvider = StreamProvider<int>(
  (ref) => ref.watch(notificationRepositoryProvider).watchUnreadCount(),
);

final businessProfileProvider = StreamProvider<BusinessProfile>(
  (ref) => ref.watch(profileRepositoryProvider).watch(),
);

/// Menu dan bahan yang dibutuhkan untuk memeriksa ketersediaan stok di keranjang.
final stockCatalogProvider = Provider<StockCatalog>((ref) {
  final menus = ref.watch(menusProvider).value ?? const <MenuItem>[];
  final ingredients = ref.watch(allIngredientsProvider).value ?? const <Ingredient>[];
  return StockCatalog(
    menus: <int, MenuItem>{for (final m in menus) m.id: m},
    ingredients: <int, Ingredient>{for (final i in ingredients) i.id: i},
  );
});
