import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:arn_kasir/app/routes.dart';
import 'package:arn_kasir/ui/backup/backup_screen.dart';
import 'package:arn_kasir/ui/cart/checkout_screen.dart';
import 'package:arn_kasir/ui/cart/receipt_screen.dart';
import 'package:arn_kasir/ui/home/home_screen.dart';
import 'package:arn_kasir/ui/ingredient/ingredient_detail_screen.dart';
import 'package:arn_kasir/ui/ingredient/ingredient_edit_screen.dart';
import 'package:arn_kasir/ui/ingredient/ingredient_list_screen.dart';
import 'package:arn_kasir/ui/menu_setup/menu_edit_screen.dart';
import 'package:arn_kasir/ui/menu_setup/menu_setup_screen.dart';
import 'package:arn_kasir/ui/profile/about_screen.dart';
import 'package:arn_kasir/ui/profile/profile_screen.dart';
import 'package:arn_kasir/ui/report/expense_screen.dart';
import 'package:arn_kasir/ui/report/income_screen.dart';
import 'package:arn_kasir/ui/report/sale_detail_screen.dart';
import 'package:arn_kasir/ui/shell/notifications_screen.dart';

/// Router aplikasi. Daftar path ada di [AppRoutes]; parameter yang tidak valid
/// (id bukan angka) dialihkan ke beranda, bukan membuat layar dengan data kosong.
final routerProvider = Provider<GoRouter>((ref) {
  final router = GoRouter(
    initialLocation: AppRoutes.home,
    routes: <RouteBase>[
      GoRoute(path: AppRoutes.home, builder: (_, __) => const HomeScreen()),
      GoRoute(path: AppRoutes.checkout, builder: (_, __) => const CheckoutScreen()),
      GoRoute(
        path: '${AppRoutes.receipt}/:id',
        redirect: _requireIntParam('id'),
        builder: (_, state) => ReceiptScreen(saleId: _intParam(state, 'id')!),
      ),
      GoRoute(
        path: AppRoutes.notifications,
        builder: (_, __) => const NotificationsScreen(),
      ),
      GoRoute(path: AppRoutes.menuSetup, builder: (_, __) => const MenuSetupScreen()),
      GoRoute(
        path: AppRoutes.menuEdit,
        builder: (_, state) =>
            MenuEditScreen(menuId: int.tryParse(state.uri.queryParameters['id'] ?? '')),
      ),
      GoRoute(
        path: AppRoutes.ingredients,
        builder: (_, __) => const IngredientListScreen(),
      ),
      GoRoute(
        path: AppRoutes.ingredientEdit,
        builder: (_, state) => IngredientEditScreen(
          ingredientId: int.tryParse(state.uri.queryParameters['id'] ?? ''),
        ),
      ),
      GoRoute(
        path: '${AppRoutes.ingredientDetail}/:id',
        redirect: _requireIntParam('id'),
        builder: (_, state) =>
            IngredientDetailScreen(ingredientId: _intParam(state, 'id')!),
      ),
      GoRoute(path: AppRoutes.incomeReport, builder: (_, __) => const IncomeScreen()),
      GoRoute(
        path: '${AppRoutes.saleDetail}/:id',
        redirect: _requireIntParam('id'),
        builder: (_, state) => SaleDetailScreen(saleId: _intParam(state, 'id')!),
      ),
      GoRoute(path: AppRoutes.expenseReport, builder: (_, __) => const ExpenseScreen()),
      GoRoute(path: AppRoutes.backup, builder: (_, __) => const BackupScreen()),
      GoRoute(path: AppRoutes.profile, builder: (_, __) => const ProfileScreen()),
      GoRoute(path: AppRoutes.about, builder: (_, __) => const AboutScreen()),
    ],
    errorBuilder: (context, state) => const HomeScreen(),
  );
  ref.onDispose(router.dispose);
  return router;
});

int? _intParam(GoRouterState state, String name) =>
    int.tryParse(state.pathParameters[name] ?? '');

GoRouterRedirect _requireIntParam(String name) {
  return (BuildContext context, GoRouterState state) =>
      _intParam(state, name) == null ? AppRoutes.home : null;
}
