/// Path rute aplikasi. Satu tempat agar tidak ada string path tersebar di widget.
abstract final class AppRoutes {
  static const String home = '/';
  static const String checkout = '/checkout';
  static const String receipt = '/receipt'; // /receipt/:id
  static const String notifications = '/notifications';
  static const String menuSetup = '/setup/menu';
  static const String menuEdit = '/setup/menu/edit'; // ?id=
  static const String ingredients = '/ingredients';
  static const String ingredientEdit = '/ingredients/edit'; // ?id=
  static const String ingredientDetail = '/ingredients/detail'; // /:id
  static const String incomeReport = '/reports/income';
  static const String saleDetail = '/reports/income/sale'; // /:id
  static const String expenseReport = '/reports/expense';
  static const String backup = '/backup';
  static const String profile = '/profile';
  static const String about = '/about';

  static String receiptOf(int saleId) => '$receipt/$saleId';
  static String saleDetailOf(int saleId) => '$saleDetail/$saleId';
  static String ingredientDetailOf(int id) => '$ingredientDetail/$id';
  static String menuEditOf(int? id) => id == null ? menuEdit : '$menuEdit?id=$id';
  static String ingredientEditOf(int? id) =>
      id == null ? ingredientEdit : '$ingredientEdit?id=$id';
}
