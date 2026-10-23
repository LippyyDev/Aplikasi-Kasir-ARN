import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:path/path.dart' as p;
import 'package:share_plus/share_plus.dart';

import 'package:arn_kasir/app/providers.dart';
import 'package:arn_kasir/app/routes.dart';
import 'package:arn_kasir/domain/models/business_profile.dart';
import 'package:arn_kasir/domain/sales/sale_record.dart';
import 'package:arn_kasir/ui/cart/receipt.dart';
import 'package:arn_kasir/ui/shared/l10n_context.dart';
import 'package:arn_kasir/ui/shared/shared_providers.dart';
import 'package:arn_kasir/ui/shared/widgets.dart';

/// Satu transaksi menurut id (struk, rincian, dan pembatalan).
final saleProvider = FutureProvider.family<SaleRecord?, int>((ref, saleId) {
  return ref.watch(saleStoreProvider).findSale(saleId);
});

/// Membagikan struk sebagai teks atau gambar lewat lembar bagikan sistem.
class ReceiptSharer {
  ReceiptSharer({required this.boundaryKey});

  final GlobalKey boundaryKey;

  Future<void> shareText(String text, {required String subject}) async {
    await SharePlus.instance.share(ShareParams(text: text, subject: subject));
  }

  Future<void> shareImage(
    Directory tempDir, {
    required String fileName,
    required String text,
  }) async {
    final boundary =
        boundaryKey.currentContext?.findRenderObject() as RenderRepaintBoundary?;
    if (boundary == null) return;
    final image = await boundary.toImage(pixelRatio: 3);
    final data = await image.toByteData(format: ui.ImageByteFormat.png);
    if (data == null) return;
    await tempDir.create(recursive: true);
    final file = File(p.join(tempDir.path, '$fileName.png'));
    await file.writeAsBytes(data.buffer.asUint8List(), flush: true);
    await SharePlus.instance.share(
      ShareParams(files: <XFile>[XFile(file.path)], text: text),
    );
  }
}

/// Layar sukses setelah pembayaran, dengan struk dan opsi bagikan.
class ReceiptScreen extends ConsumerWidget {
  const ReceiptScreen({required this.saleId, this.afterPayment = true, super.key});

  final int saleId;

  /// `true` dari alur pembayaran (tombol "Transaksi baru"); `false` dari laporan.
  final bool afterPayment;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = context.l10n;
    final sale = ref.watch(saleProvider(saleId));
    final profile = ref.watch(businessProfileProvider).value ?? const BusinessProfile();

    return Scaffold(
      appBar: AppBar(
        title: Text(afterPayment ? l10n.receiptSuccessTitle : l10n.receiptTitle),
        automaticallyImplyLeading: !afterPayment,
      ),
      body: AsyncBody(
        value: sale,
        builder: (record) {
          if (record == null) {
            return EmptyState(icon: Icons.receipt_long_outlined, title: l10n.errSaleNotFound);
          }
          return ReceiptBody(
            sale: record,
            profile: profile,
            showSuccess: afterPayment,
            onNewSale: () => context.go(AppRoutes.home),
          );
        },
      ),
    );
  }
}

class ReceiptBody extends ConsumerStatefulWidget {
  const ReceiptBody({
    required this.sale,
    required this.profile,
    this.showSuccess = false,
    this.onNewSale,
    super.key,
  });

  final SaleRecord sale;
  final BusinessProfile profile;
  final bool showSuccess;
  final VoidCallback? onNewSale;

  @override
  ConsumerState<ReceiptBody> createState() => _ReceiptBodyState();
}

class _ReceiptBodyState extends ConsumerState<ReceiptBody> {
  final GlobalKey _boundaryKey = GlobalKey();

  @override
  Widget build(BuildContext context) {
    final l10n = context.l10n;
    final fmt = ref.watch(formattersProvider);
    final theme = Theme.of(context);
    final sharer = ReceiptSharer(boundaryKey: _boundaryKey);

    return ListView(
      padding: const EdgeInsets.all(16),
      children: <Widget>[
        if (widget.showSuccess) ...<Widget>[
          Icon(Icons.check_circle, size: 64, color: theme.colorScheme.primary),
          const SizedBox(height: 8),
          Text(
            l10n.receiptPaid,
            textAlign: TextAlign.center,
            style: theme.textTheme.headlineSmall,
          ),
          const SizedBox(height: 16),
        ],
        ClipRRect(
          borderRadius: BorderRadius.circular(12),
          child: RepaintBoundary(
            key: _boundaryKey,
            child: ReceiptView(
              sale: widget.sale,
              profile: widget.profile,
              l10n: l10n,
              fmt: fmt,
            ),
          ),
        ),
        const SizedBox(height: 16),
        Row(
          children: <Widget>[
            Expanded(
              child: OutlinedButton.icon(
                icon: const Icon(Icons.text_snippet_outlined),
                label: Text(l10n.receiptShareText),
                onPressed: () => sharer.shareText(
                  buildReceiptText(
                    sale: widget.sale,
                    profile: widget.profile,
                    l10n: l10n,
                    fmt: fmt,
                  ),
                  subject: widget.sale.number,
                ),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: OutlinedButton.icon(
                icon: const Icon(Icons.image_outlined),
                label: Text(l10n.receiptShareImage),
                onPressed: () async {
                  try {
                    await sharer.shareImage(
                      ref.read(appPathsProvider).workDir,
                      fileName: widget.sale.number,
                      text: widget.sale.number,
                    );
                  } on Object catch (e) {
                    if (context.mounted) showFailure(context, ref, e);
                  }
                },
              ),
            ),
          ],
        ),
        if (widget.onNewSale != null) ...<Widget>[
          const SizedBox(height: 12),
          FilledButton.icon(
            icon: const Icon(Icons.add_shopping_cart),
            label: Text(l10n.receiptNewSale),
            onPressed: widget.onNewSale,
          ),
        ],
      ],
    );
  }
}
