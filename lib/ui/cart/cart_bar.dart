import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:arn_kasir/app/providers.dart';
import 'package:arn_kasir/app/routes.dart';
import 'package:arn_kasir/domain/sales/cart.dart';
import 'package:arn_kasir/ui/cart/cart_controller.dart';
import 'package:arn_kasir/ui/home/menu_card.dart';
import 'package:arn_kasir/ui/shared/l10n_context.dart';
import 'package:arn_kasir/ui/shared/shared_providers.dart';
import 'package:arn_kasir/ui/shared/widgets.dart';

/// Bilah keranjang di bawah Halaman Utama: jumlah item dan total (PRD 6.2 D).
class CartBar extends ConsumerWidget {
  const CartBar({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final cart = ref.watch(cartProvider);
    if (cart.isEmpty) return const SizedBox.shrink();

    final fmt = ref.watch(formattersProvider);
    final theme = Theme.of(context);
    return SafeArea(
      child: Padding(
        padding: const EdgeInsets.fromLTRB(12, 4, 12, 12),
        child: Material(
          color: theme.colorScheme.primary,
          borderRadius: BorderRadius.circular(16),
          child: InkWell(
            borderRadius: BorderRadius.circular(16),
            onTap: () => showCartSheet(context),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
              child: Row(
                children: <Widget>[
                  Icon(Icons.shopping_cart_outlined, color: theme.colorScheme.onPrimary),
                  const SizedBox(width: 12),
                  Text(
                    context.l10n.cartItemCount(cart.itemCount),
                    style: theme.textTheme.titleMedium?.copyWith(
                      color: theme.colorScheme.onPrimary,
                    ),
                  ),
                  const Spacer(),
                  Text(
                    fmt.money(cart.totals().total),
                    style: theme.textTheme.titleMedium?.copyWith(
                      color: theme.colorScheme.onPrimary,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  Icon(Icons.keyboard_arrow_up, color: theme.colorScheme.onPrimary),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

Future<void> showCartSheet(BuildContext context) {
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    showDragHandle: true,
    builder: (context) => const CartSheet(),
  );
}

/// Lembar keranjang: ubah jumlah, hapus, catatan, dan diskon transaksi.
class CartSheet extends ConsumerWidget {
  const CartSheet({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = context.l10n;
    final cart = ref.watch(cartProvider);
    final fmt = ref.watch(formattersProvider);
    final theme = Theme.of(context);

    if (cart.isEmpty) {
      // Keranjang kosong (misalnya baru dibayar): tutup lembar setelah frame ini.
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (context.mounted && Navigator.of(context).canPop()) {
          Navigator.of(context).pop();
        }
      });
      return const SizedBox(height: 120);
    }

    final totals = cart.totals();
    return ConstrainedBox(
      constraints: BoxConstraints(maxHeight: MediaQuery.sizeOf(context).height * 0.85),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Row(
              children: <Widget>[
                Text(l10n.cartTitle, style: theme.textTheme.titleLarge),
                const Spacer(),
                TextButton.icon(
                  icon: const Icon(Icons.delete_sweep_outlined),
                  label: Text(l10n.cartClear),
                  onPressed: () async {
                    final ok = await confirmDialog(
                      context,
                      title: l10n.cartClearTitle,
                      message: l10n.cartClearMessage,
                      confirmLabel: l10n.cartClear,
                      destructive: true,
                    );
                    if (ok) ref.read(cartProvider.notifier).clear();
                  },
                ),
              ],
            ),
          ),
          Flexible(
            child: ListView.separated(
              shrinkWrap: true,
              padding: const EdgeInsets.symmetric(horizontal: 16),
              itemCount: cart.lines.length,
              separatorBuilder: (_, __) => const Divider(height: 1),
              itemBuilder: (context, index) => _CartLineTile(line: cart.lines[index]),
            ),
          ),
          const Divider(height: 1),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 16),
            child: Column(
              children: <Widget>[
                _AmountRow(label: l10n.cartSubtotal, value: fmt.money(totals.subtotal)),
                Row(
                  children: <Widget>[
                    Expanded(
                      child: TextButton.icon(
                        style: TextButton.styleFrom(
                          alignment: Alignment.centerLeft,
                          padding: EdgeInsets.zero,
                        ),
                        icon: const Icon(Icons.sell_outlined, size: 18),
                        label: Text(l10n.cartDiscount),
                        onPressed: () => _editDiscount(context, ref, cart),
                      ),
                    ),
                    Text(
                      totals.discount == 0 ? '-' : '-${fmt.money(totals.discount)}',
                      style: theme.textTheme.bodyLarge,
                    ),
                  ],
                ),
                _AmountRow(
                  label: l10n.cartTotal,
                  value: fmt.money(totals.total),
                  emphasized: true,
                ),
                const SizedBox(height: 12),
                SizedBox(
                  width: double.infinity,
                  child: FilledButton.icon(
                    icon: const Icon(Icons.payments_outlined),
                    label: Text(l10n.cartPay),
                    onPressed: () {
                      Navigator.of(context).pop();
                      context.push(AppRoutes.checkout);
                    },
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _editDiscount(BuildContext context, WidgetRef ref, Cart cart) async {
    final result = await showDialog<Discount>(
      context: context,
      builder: (context) => _DiscountDialog(initial: cart.discount),
    );
    if (result == null) return;
    try {
      ref.read(cartProvider.notifier).setDiscount(result);
    } on Object catch (e) {
      if (context.mounted) showFailure(context, ref, e);
    }
  }
}

class _AmountRow extends StatelessWidget {
  const _AmountRow({required this.label, required this.value, this.emphasized = false});

  final String label;
  final String value;
  final bool emphasized;

  @override
  Widget build(BuildContext context) {
    final style = emphasized
        ? Theme.of(context).textTheme.titleLarge
        : Theme.of(context).textTheme.bodyLarge;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: <Widget>[
          Text(label, style: style),
          const Spacer(),
          Text(value, style: style?.copyWith(fontWeight: emphasized ? FontWeight.bold : null)),
        ],
      ),
    );
  }
}

class _CartLineTile extends ConsumerWidget {
  const _CartLineTile({required this.line});

  final CartLine line;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = context.l10n;
    final fmt = ref.watch(formattersProvider);
    final theme = Theme.of(context);
    final menu = ref.watch(stockCatalogProvider).menus[line.menuId];
    final controller = ref.read(cartProvider.notifier);

    void change(int quantity) {
      if (menu == null) return;
      try {
        controller.setQuantity(menu, quantity);
      } on Object catch (e) {
        showFailure(context, ref, e);
      }
    }

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          if (menu != null)
            ClipRRect(
              borderRadius: BorderRadius.circular(8),
              child: MenuPhoto(photoPath: menu.photoPath, size: 48),
            ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Text(line.menuName, style: theme.textTheme.titleSmall),
                Text(
                  '${fmt.money(line.unitPrice)}  x  ${line.quantity} = ${fmt.money(line.lineTotal)}',
                  style: theme.textTheme.bodySmall,
                ),
                if (line.note != null)
                  Text(
                    line.note!,
                    style: theme.textTheme.bodySmall?.copyWith(
                      fontStyle: FontStyle.italic,
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                TextButton.icon(
                  style: TextButton.styleFrom(
                    padding: EdgeInsets.zero,
                    minimumSize: const Size(0, 32),
                    tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                  ),
                  icon: const Icon(Icons.edit_note, size: 18),
                  label: Text(line.note == null ? l10n.cartAddNote : l10n.cartEditNote),
                  onPressed: () => editCartNote(context, ref, line.menuId, line.menuName),
                ),
              ],
            ),
          ),
          Row(
            mainAxisSize: MainAxisSize.min,
            children: <Widget>[
              IconButton.outlined(
                tooltip: l10n.cartDecrease,
                icon: const Icon(Icons.remove),
                onPressed: () => change(line.quantity - 1),
              ),
              SizedBox(
                width: 32,
                child: Text(
                  '${line.quantity}',
                  textAlign: TextAlign.center,
                  style: theme.textTheme.titleMedium,
                ),
              ),
              IconButton.outlined(
                tooltip: l10n.cartIncrease,
                icon: const Icon(Icons.add),
                onPressed: () => change(line.quantity + 1),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _DiscountDialog extends StatefulWidget {
  const _DiscountDialog({required this.initial});

  final Discount initial;

  @override
  State<_DiscountDialog> createState() => _DiscountDialogState();
}

class _DiscountDialogState extends State<_DiscountDialog> {
  late bool _percent = widget.initial is PercentDiscount;
  late final TextEditingController _controller = TextEditingController(
    text: switch (widget.initial) {
      AmountDiscount(:final amount) => '$amount',
      PercentDiscount(:final percent) => '$percent',
      NoDiscount() => '',
    },
  );

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    return AlertDialog(
      title: Text(l10n.cartDiscount),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          SegmentedButton<bool>(
            segments: <ButtonSegment<bool>>[
              ButtonSegment<bool>(value: false, label: Text(l10n.discountAmount)),
              ButtonSegment<bool>(value: true, label: Text(l10n.discountPercent)),
            ],
            selected: <bool>{_percent},
            onSelectionChanged: (value) => setState(() => _percent = value.first),
          ),
          const SizedBox(height: 16),
          TextField(
            controller: _controller,
            autofocus: true,
            keyboardType: TextInputType.number,
            inputFormatters: digitsOnly,
            decoration: InputDecoration(
              labelText: _percent ? l10n.discountPercentLabel : l10n.discountAmountLabel,
              suffixText: _percent ? '%' : null,
            ),
          ),
        ],
      ),
      actions: <Widget>[
        TextButton(
          onPressed: () => Navigator.of(context).pop(const NoDiscount()),
          child: Text(l10n.discountRemove),
        ),
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: Text(l10n.actionCancel),
        ),
        FilledButton(
          onPressed: () {
            final value = int.tryParse(_controller.text) ?? 0;
            Navigator.of(context).pop(
              value == 0
                  ? const NoDiscount()
                  : (_percent ? PercentDiscount(value) : AmountDiscount(value)),
            );
          },
          child: Text(l10n.actionApply),
        ),
      ],
    );
  }
}
