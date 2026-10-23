import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import 'package:arn_kasir/app/routes.dart';
import 'package:arn_kasir/app/services/sales_actions.dart';
import 'package:arn_kasir/domain/sales/payment.dart';
import 'package:arn_kasir/ui/cart/cart_controller.dart';
import 'package:arn_kasir/ui/shared/l10n_context.dart';
import 'package:arn_kasir/ui/shared/shared_providers.dart';
import 'package:arn_kasir/ui/shared/widgets.dart';

/// Pembayaran (PRD 6.2 D): tunai dengan kembalian otomatis atau non tunai (dicatat saja).
class CheckoutScreen extends ConsumerStatefulWidget {
  const CheckoutScreen({super.key});

  @override
  ConsumerState<CheckoutScreen> createState() => _CheckoutScreenState();
}

class _CheckoutScreenState extends ConsumerState<CheckoutScreen> {
  PaymentMethod _method = PaymentMethod.cash;
  final TextEditingController _paid = TextEditingController();
  final TextEditingController _note = TextEditingController();
  bool _saving = false;

  @override
  void dispose() {
    _paid.dispose();
    _note.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final cart = ref.watch(cartProvider);
    final fmt = ref.watch(formattersProvider);
    final theme = Theme.of(context);

    if (cart.isEmpty && !_saving) {
      return Scaffold(
        appBar: AppBar(title: Text(l10n.checkoutTitle)),
        body: EmptyState(icon: Icons.shopping_cart_outlined, title: l10n.checkoutEmptyCart),
      );
    }

    final total = cart.totals().total;
    final paid = parseMoney(_paid.text);
    final cash = _method == PaymentMethod.cash;
    final change = cash && paid != null ? paid - total : null;
    final canPay = !_saving && (!cash || (paid ?? total) >= total);

    return Scaffold(
      appBar: AppBar(title: Text(l10n.checkoutTitle)),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: <Widget>[
          Card(
            child: Padding(
              padding: const EdgeInsets.all(20),
              child: Column(
                children: <Widget>[
                  Text(l10n.cartTotal, style: theme.textTheme.titleMedium),
                  const SizedBox(height: 4),
                  Text(
                    fmt.money(total),
                    style: theme.textTheme.displaySmall?.copyWith(
                      fontWeight: FontWeight.bold,
                      color: theme.colorScheme.primary,
                    ),
                  ),
                  Text(l10n.cartItemCount(cart.itemCount), style: theme.textTheme.bodyMedium),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),
          SegmentedButton<PaymentMethod>(
            segments: <ButtonSegment<PaymentMethod>>[
              ButtonSegment<PaymentMethod>(
                value: PaymentMethod.cash,
                icon: const Icon(Icons.payments_outlined),
                label: Text(l10n.paymentCash),
              ),
              ButtonSegment<PaymentMethod>(
                value: PaymentMethod.nonCash,
                icon: const Icon(Icons.qr_code_2),
                label: Text(l10n.paymentNonCash),
              ),
            ],
            selected: <PaymentMethod>{_method},
            onSelectionChanged: (value) => setState(() => _method = value.first),
          ),
          const SizedBox(height: 16),
          if (cash) ...<Widget>[
            TextField(
              controller: _paid,
              keyboardType: TextInputType.number,
              inputFormatters: digitsOnly,
              onChanged: (_) => setState(() {}),
              decoration: InputDecoration(
                labelText: l10n.checkoutAmountPaid,
                prefixText: '${fmt.currency} ',
                helperText: l10n.checkoutPaidHelper,
              ),
            ),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: <Widget>[
                ActionChip(
                  label: Text(l10n.checkoutExactAmount),
                  onPressed: () => setState(() => _paid.text = '$total'),
                ),
                for (final value in _suggestions(total))
                  ActionChip(
                    label: Text(fmt.integer(value)),
                    onPressed: () => setState(() => _paid.text = '$value'),
                  ),
              ],
            ),
            const SizedBox(height: 16),
            if (change != null)
              Card(
                color: change < 0
                    ? theme.colorScheme.errorContainer
                    : theme.colorScheme.secondaryContainer,
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Row(
                    children: <Widget>[
                      Text(
                        change < 0 ? l10n.checkoutShortBy : l10n.checkoutChange,
                        style: theme.textTheme.titleMedium,
                      ),
                      const Spacer(),
                      Text(
                        fmt.money(change.abs()),
                        style: theme.textTheme.titleLarge?.copyWith(fontWeight: FontWeight.bold),
                      ),
                    ],
                  ),
                ),
              ),
          ] else
            Text(l10n.checkoutNonCashHint, style: theme.textTheme.bodyMedium),
          const SizedBox(height: 16),
          TextField(
            controller: _note,
            maxLength: 160,
            decoration: InputDecoration(labelText: l10n.checkoutNoteLabel),
          ),
        ],
      ),
      bottomNavigationBar: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
          child: FilledButton.icon(
            icon: _saving
                ? const SizedBox.square(
                    dimension: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : const Icon(Icons.check),
            label: Text(l10n.checkoutConfirm),
            onPressed: canPay ? _submit : null,
          ),
        ),
      ),
    );
  }

  /// Pecahan uang yang lazim sebagai pintasan, di atas total.
  List<int> _suggestions(int total) {
    const steps = <int>[5000, 10000, 20000, 50000, 100000];
    final values = <int>{};
    for (final step in steps) {
      final rounded = ((total + step - 1) ~/ step) * step;
      if (rounded > total) values.add(rounded);
    }
    final sorted = values.toList()..sort();
    return sorted.take(4).toList();
  }

  Future<void> _submit() async {
    setState(() => _saving = true);
    try {
      final result = await ref.read(salesActionsProvider).checkout(
            method: _method,
            paid: _method == PaymentMethod.cash ? parseMoney(_paid.text) : null,
            note: _note.text.trim().isEmpty ? null : _note.text.trim(),
          );
      if (!mounted) return;
      context.pushReplacement(AppRoutes.receiptOf(result.sale.id!));
    } on Object catch (e) {
      if (!mounted) return;
      setState(() => _saving = false);
      showFailure(context, ref, e);
    }
  }
}
