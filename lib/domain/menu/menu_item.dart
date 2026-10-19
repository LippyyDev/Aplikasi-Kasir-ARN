import 'package:arn_kasir/domain/menu/recipe.dart';

/// Menu yang dijual beserta resep per satu porsi.
final class MenuItem {
  const MenuItem({
    required this.id,
    required this.name,
    required this.price,
    this.categoryId,
    this.active = true,
    this.recipe = const <RecipeLine>[],
    this.photoPath,
    this.note,
  });

  final int id;
  final String name;

  /// Harga jual dalam Rupiah (bilangan bulat).
  final int price;

  /// `null` berarti "Tanpa kategori": tidak tampil di Halaman Utama.
  final int? categoryId;
  final bool active;
  final List<RecipeLine> recipe;

  /// Nama berkas relatif terhadap folder gambar aplikasi.
  final String? photoPath;
  final String? note;

  /// Menu tanpa resep (produk titipan) tidak pernah terblokir stok.
  bool get hasRecipe => recipe.isNotEmpty;

  @override
  String toString() => 'MenuItem($id, $name, Rp$price)';
}
