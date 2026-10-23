import 'package:flutter/widgets.dart';

/// Membangun ulang seluruh pohon aplikasi dengan state baru.
///
/// Setelah pemulihan, berkas database sudah diganti dan koneksi lama ditutup. Memulai
/// ulang pohon (termasuk `ProviderScope` dan pembukaan database) lebih aman daripada
/// mencoba menyegarkan setiap provider satu per satu.
class RestartScope extends StatefulWidget {
  const RestartScope({required this.child, super.key});

  final Widget child;

  /// Memulai ulang aplikasi dari bootstrap. Aman dipanggil dari mana pun dalam pohon.
  static void restart(BuildContext context) {
    context.findAncestorStateOfType<_RestartScopeState>()?._restart();
  }

  @override
  State<RestartScope> createState() => _RestartScopeState();
}

class _RestartScopeState extends State<RestartScope> {
  Key _key = UniqueKey();

  void _restart() => setState(() => _key = UniqueKey());

  @override
  Widget build(BuildContext context) => KeyedSubtree(key: _key, child: widget.child);
}
