import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:arn_kasir/app/providers.dart';

/// Logo usaha dari penyimpanan internal, atau ikon pengganti bila belum diatur.
class LogoImage extends ConsumerWidget {
  const LogoImage({required this.logoPath, this.size = 96, super.key});

  final String? logoPath;
  final double size;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final scheme = Theme.of(context).colorScheme;
    final file = ref.watch(imageStoreProvider).resolve(logoPath);
    final placeholder = ColoredBox(
      color: scheme.primaryContainer,
      child: Icon(Icons.storefront_outlined, size: size / 2, color: scheme.onPrimaryContainer),
    );
    return ClipRRect(
      borderRadius: BorderRadius.circular(size / 5),
      child: SizedBox(
        width: size,
        height: size,
        child: file == null
            ? placeholder
            : Image.file(
                file,
                fit: BoxFit.cover,
                cacheWidth: (size * 3).round(),
                errorBuilder: (_, __, ___) => placeholder,
              ),
      ),
    );
  }
}
