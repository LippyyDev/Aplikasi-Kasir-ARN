import 'package:flutter/material.dart';

import 'package:arn_kasir/domain/models/business_profile.dart';
import 'package:arn_kasir/domain/sales/payment.dart';
import 'package:arn_kasir/domain/sales/sale_record.dart';
import 'package:arn_kasir/l10n/generated/app_localizations.dart';
import 'package:arn_kasir/ui/shared/formatters.dart';

/// Tampilan struk. Dipakai di layar sukses, rincian transaksi, dan sebagai sumber gambar
/// untuk dibagikan (dibungkus `RepaintBoundary` oleh pemanggil).
class ReceiptView extends StatelessWidget {
  const ReceiptView({
    required this.sale,
    required this.profile,
    required this.l10n,
    required this.fmt,
    super.key,
  });

  final SaleRecord sale;
  final BusinessProfile profile;
  final AppLocalizations l10n;
  final AppFormatters fmt;

  @override
  Widget build(BuildContext context) {
    // Struk selalu terang agar hasil gambar terbaca di aplikasi mana pun.
    const ink = Color(0xFF1B1B1B);
    const muted = Color(0xFF5F5F5F);
    final base = Theme.of(context).textTheme.bodyMedium?.copyWith(color: ink);
    final small = Theme.of(context).textTheme.bodySmall?.copyWith(color: muted);
    final bold = base?.copyWith(fontWeight: FontWeight.bold);

    Widget row(String left, String right, {TextStyle? style}) => Padding(
          padding: const EdgeInsets.symmetric(vertical: 2),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Expanded(child: Text(left, style: style ?? base)),
              const SizedBox(width: 8),
              Text(right, style: style ?? base),
            ],
          ),
        );

    return Container(
      color: Colors.white,
      padding: const EdgeInsets.all(20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          Text(
            profile.name.isEmpty ? l10n.appName : profile.name,
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.titleMedium?.copyWith(
                  color: ink,
                  fontWeight: FontWeight.bold,
                ),
          ),
          if (profile.address != null)
            Text(profile.address!, textAlign: TextAlign.center, style: small),
          if (profile.phone != null)
            Text(profile.phone!, textAlign: TextAlign.center, style: small),
          const SizedBox(height: 12),
          row(l10n.receiptNumber, sale.number, style: small),
          row(l10n.receiptDate, fmt.dateTime(sale.time), style: small),
          if (sale.status == SaleStatus.voided)
            Padding(
              padding: const EdgeInsets.only(top: 6),
              child: Text(
                l10n.receiptVoided,
                textAlign: TextAlign.center,
                style: bold?.copyWith(color: const Color(0xFFB3261E)),
              ),
            ),
          const Divider(height: 24, color: muted),
          for (final item in sale.items) ...<Widget>[
            Text(item.menuName, style: bold),
            row(
              '${item.quantity} x ${fmt.money(item.unitPrice)}',
              fmt.money(item.lineTotal),
            ),
            if (item.note != null) Text(item.note!, style: small),
            const SizedBox(height: 6),
          ],
          const Divider(height: 24, color: muted),
          row(l10n.cartSubtotal, fmt.money(sale.subtotal)),
          if (sale.discount > 0) row(l10n.cartDiscount, '-${fmt.money(sale.discount)}'),
          row(l10n.cartTotal, fmt.money(sale.total), style: bold),
          const SizedBox(height: 6),
          row(
            l10n.receiptMethod,
            sale.method == PaymentMethod.cash ? l10n.paymentCash : l10n.paymentNonCash,
          ),
          if (sale.method == PaymentMethod.cash) ...<Widget>[
            row(l10n.checkoutAmountPaid, fmt.money(sale.paid)),
            row(l10n.checkoutChange, fmt.money(sale.change)),
          ],
          if (sale.note != null) ...<Widget>[
            const SizedBox(height: 8),
            Text(sale.note!, style: small),
          ],
          const SizedBox(height: 16),
          Text(l10n.receiptThanks, textAlign: TextAlign.center, style: small),
        ],
      ),
    );
  }
}

/// Struk sebagai teks polos untuk dibagikan (PRD 6.2 D).
String buildReceiptText({
  required SaleRecord sale,
  required BusinessProfile profile,
  required AppLocalizations l10n,
  required AppFormatters fmt,
}) {
  final b = StringBuffer()
    ..writeln(profile.name.isEmpty ? l10n.appName : profile.name);
  if (profile.address != null) b.writeln(profile.address);
  if (profile.phone != null) b.writeln(profile.phone);
  b
    ..writeln()
    ..writeln('${l10n.receiptNumber}: ${sale.number}')
    ..writeln('${l10n.receiptDate}: ${fmt.dateTime(sale.time)}');
  if (sale.status == SaleStatus.voided) b.writeln(l10n.receiptVoided);
  b.writeln('--------------------------------');
  for (final item in sale.items) {
    b
      ..writeln(item.menuName)
      ..writeln('  ${item.quantity} x ${fmt.money(item.unitPrice)} = ${fmt.money(item.lineTotal)}');
    if (item.note != null) b.writeln('  (${item.note})');
  }
  b
    ..writeln('--------------------------------')
    ..writeln('${l10n.cartSubtotal}: ${fmt.money(sale.subtotal)}');
  if (sale.discount > 0) b.writeln('${l10n.cartDiscount}: -${fmt.money(sale.discount)}');
  b.writeln('${l10n.cartTotal}: ${fmt.money(sale.total)}');
  if (sale.method == PaymentMethod.cash) {
    b
      ..writeln('${l10n.checkoutAmountPaid}: ${fmt.money(sale.paid)}')
      ..writeln('${l10n.checkoutChange}: ${fmt.money(sale.change)}');
  } else {
    b.writeln('${l10n.receiptMethod}: ${l10n.paymentNonCash}');
  }
  b
    ..writeln()
    ..write(l10n.receiptThanks);
  return b.toString();
}
