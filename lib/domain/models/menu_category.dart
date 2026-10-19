/// Kategori menu yang dibuat pengguna (PRD 6.3.4a).
final class MenuCategory {
  const MenuCategory({
    required this.id,
    required this.name,
    required this.sortOrder,
  });

  final int id;
  final String name;
  final int sortOrder;
}
