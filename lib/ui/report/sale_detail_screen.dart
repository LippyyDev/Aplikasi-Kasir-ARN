import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:arn_kasir/app/providers.dart';
import 'package:arn_kasir/app/services/sales_actions.dart';
import 'package:arn_kasir/domain/models/business_profile.dart';
import 'package:arn_kasir/domain/sales/sale_record.dart';
import 'package:arn_kasir/ui/cart/receipt_screen.dart';
import 'package:arn_kasir/ui/shared/l10n_context.dart';
import 'package:arn_kasir/ui/shared/widgets.dart';

/// Rincian satu transaksi dari laporan: struk, bagikan, dan pembatalan (*void*).
///
/// Pembatalan membalik stok dari snapshot pemakaian (PRD 6.3.5) dan hanya dapat
/// dilakukan sekali; transaksi yang sudah batal tetap tampil sebagai riwayat.
class SaleDetailScreen extends ConsumerWidget {
  const SaleDetailScreen({required this.saleId, super.key});

  final int saleId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = context.l10n;
    final sale = ref.watch(saleProvider(saleId));
    final profile = ref.watch(businessProfileProvider).value ?? const BusinessProfile();

    return Scaffold(
      appBar: AppBar(title: Text(l10n.saleDetailTitle)),
      body: AsyncBody(
        value: sale,
        builder: (record) {
          if (record == null) {
            return EmptyState(icon: Icons.receipt_long_outlined, title: l10n.errSaleNotFound);
          }
          return Column(
            children: <Widget>[
              if (record.status == SaleStatus.voided) const _VoidedBanner(),
              Expanded(child: ReceiptBody(sale: record, profile: profile)),
              if (record.status == SaleStatus.completed)
                SafeArea(
                  top: false,
                  child: Padding(
                    padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                    child: SizedBox(
                      width: double.infinity,
                      child: OutlinedButton.icon(
                        style: OutlinedButton.styleFrom(
                          foregroundColor: Theme.of(context).colorScheme.error,
                        ),
                        icon: const Icon(Icons.undo),
                        label: Text(l10n.saleVoidAction),
                        onPressed: () => _void(context, ref, record),
                      ),
                    ),
                  ),
                ),
            ],
          );
        },
      ),
    );
  }

  Future<void> _void(BuildContext context, WidgetRef ref, SaleRecord record) async {
    final l10n = context.l10n;
    final confirmed = await confirmDialog(
      context,
      title: l10n.saleVoidConfirmTitle(record.number),
      message: l10n.saleVoidConfirmMessage,
      confirmLabel: l10n.saleVoidAction,
      destructive: true,
    );
    if (!confirmed || !context.mounted) return;
    try {
      await ref.read(salesActionsProvider).voidSale(saleId);
      ref.invalidate(saleProvider(saleId));
      if (context.mounted) showMessage(context, l10n.saleVoidDone);
    } on Object catch (e) {
      if (context.mounted) showFailure(context, ref, e);
    }
  }
}

class _VoidedBanner extends StatelessWidget {
  const _VoidedBanner();

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      width: double.infinity,
      color: theme.colorScheme.errorContainer,
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
      child: Text(
        context.l10n.saleVoidedBanner,
        style: TextStyle(color: theme.colorScheme.onErrorContainer),
      ),
    );
  }
}
