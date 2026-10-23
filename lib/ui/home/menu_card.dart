import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:arn_kasir/app/providers.dart';
import 'package:arn_kasir/ui/cart/cart_controller.dart';
import 'package:arn_kasir/ui/home/home_providers.dart';
import 'package:arn_kasir/ui/shared/l10n_context.dart';
import 'package:arn_kasir/ui/shared/shared_providers.dart';
import 'package:arn_kasir/ui/shared/widgets.dart';

/// Foto menu dari penyimpanan internal, atau ikon pengganti bila belum ada.
class MenuPhoto extends ConsumerWidget {
  const MenuPhoto({required this.photoPath, this.size, super.key});

  final String? photoPath;
  final double? size;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final file = ref.watch(imageStoreProvider).resolve(photoPath);
    final scheme = Theme.of(context).colorScheme;
    final placeholder = ColoredBox(
      color: scheme.surfaceContainerHighest,
      child: Center(
        child: Icon(Icons.restaurant_menu, color: scheme.outline, size: size == null ? 40 : size! / 2),
      ),
    );
    if (file == null) return SizedBox(width: size, height: size, child: placeholder);
    return SizedBox(
      width: size,
      height: size,
      child: Image.file(
        file,
        fit: BoxFit.cover,
        cacheWidth: 480,
        errorBuilder: (_, __, ___) => placeholder,
      ),
    );
  }
}

/// Kartu menu (foto, nama, harga). Ketuk menambah satu porsi; tahan menambah catatan.
class MenuCard extends ConsumerWidget {
  const MenuCard({required this.tile, super.key});

  final MenuTile tile;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final menu = tile.menu;
    final fmt = ref.watch(formattersProvider);
    final inCart = ref.watch(cartProvider.select((c) => c.quantityOf(menu.id)));
    final theme = Theme.of(context);
    final portions = tile.portions;
    final lowStock = tile.sellable && portions != null && portions <= 5;

    return Semantics(
      button: true,
      enabled: tile.sellable,
      label: '${menu.name}, ${fmt.money(menu.price)}',
      child: Opacity(
        opacity: tile.sellable ? 1 : 0.5,
        child: Card(
          child: InkWell(
            onTap: tile.sellable ? () => _add(context, ref) : null,
            onLongPress: tile.sellable ? () => _addWithNote(context, ref) : null,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: <Widget>[
                Expanded(
                  child: Stack(
                    fit: StackFit.expand,
                    children: <Widget>[
                      MenuPhoto(photoPath: menu.photoPath),
                      if (!tile.sellable)
                        Align(
                          alignment: Alignment.center,
                          child: Container(
                            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                            decoration: BoxDecoration(
                              color: theme.colorScheme.errorContainer,
                              borderRadius: BorderRadius.circular(8),
                            ),
                            child: Text(
                              context.l10n.homeOutOfStock,
                              style: theme.textTheme.labelLarge?.copyWith(
                                color: theme.colorScheme.onErrorContainer,
                              ),
                            ),
                          ),
                        ),
                      if (inCart > 0)
                        Positioned(
                          top: 8,
                          right: 8,
                          child: CircleAvatar(
                            radius: 14,
                            backgroundColor: theme.colorScheme.primary,
                            child: Text(
                              '$inCart',
                              style: TextStyle(
                                color: theme.colorScheme.onPrimary,
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                          ),
                        ),
                    ],
                  ),
                ),
                Padding(
                  padding: const EdgeInsets.fromLTRB(12, 8, 12, 10),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: <Widget>[
                      Text(
                        menu.name,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: theme.textTheme.titleSmall,
                      ),
                      const SizedBox(height: 2),
                      Row(
                        children: <Widget>[
                          Text(
                            fmt.money(menu.price),
                            style: theme.textTheme.bodyMedium?.copyWith(
                              color: theme.colorScheme.primary,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                          if (lowStock) ...<Widget>[
                            const Spacer(),
                            Text(
                              context.l10n.homePortionsLeft(portions),
                              style: theme.textTheme.labelSmall?.copyWith(
                                color: theme.colorScheme.error,
                              ),
                            ),
                          ],
                        ],
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  void _add(BuildContext context, WidgetRef ref) {
    try {
      ref.read(cartProvider.notifier).add(tile.menu);
    } on Object catch (e) {
      showFailure(context, ref, e);
    }
  }

  Future<void> _addWithNote(BuildContext context, WidgetRef ref) async {
    final controller = ref.read(cartProvider.notifier);
    try {
      if (ref.read(cartProvider).quantityOf(tile.menu.id) == 0) {
        controller.add(tile.menu);
      }
    } on Object catch (e) {
      showFailure(context, ref, e);
      return;
    }
    if (!context.mounted) return;
    await editCartNote(context, ref, tile.menu.id, tile.menu.name);
  }
}

/// Dialog catatan per item keranjang.
Future<void> editCartNote(
  BuildContext context,
  WidgetRef ref,
  int menuId,
  String menuName,
) async {
  final cart = ref.read(cartProvider);
  final current = cart.lines.where((l) => l.menuId == menuId).firstOrNull?.note;
  final textController = TextEditingController(text: current);
  final saved = await showDialog<String>(
    context: context,
    builder: (context) => AlertDialog(
      title: Text(menuName),
      content: TextField(
        controller: textController,
        autofocus: true,
        maxLength: 120,
        textCapitalization: TextCapitalization.sentences,
        decoration: InputDecoration(labelText: context.l10n.cartNoteLabel),
      ),
      actions: <Widget>[
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: Text(context.l10n.actionCancel),
        ),
        FilledButton(
          onPressed: () => Navigator.of(context).pop(textController.text),
          child: Text(context.l10n.actionSave),
        ),
      ],
    ),
  );
  if (saved != null) ref.read(cartProvider.notifier).setNote(menuId, saved);
}
