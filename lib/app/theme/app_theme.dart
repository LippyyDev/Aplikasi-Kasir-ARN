import 'package:flutter/material.dart';

/// Warna merek sementara (cokelat logo ARN) sampai aset resmi tersedia (PRD 15).
abstract final class AppTheme {
  static const Color brandSeed = Color(0xFF6B4226);

  static ThemeData light([ColorScheme? dynamicScheme]) =>
      _build(dynamicScheme ?? ColorScheme.fromSeed(seedColor: brandSeed));

  static ThemeData dark([ColorScheme? dynamicScheme]) => _build(
        dynamicScheme ??
            ColorScheme.fromSeed(seedColor: brandSeed, brightness: Brightness.dark),
      );

  static ThemeData _build(ColorScheme scheme) {
    return ThemeData(
      useMaterial3: true,
      colorScheme: scheme,
      visualDensity: VisualDensity.standard,
      appBarTheme: const AppBarTheme(centerTitle: false),
      cardTheme: CardThemeData(
        elevation: 0,
        clipBehavior: Clip.antiAlias,
        color: scheme.surfaceContainerLow,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
          side: BorderSide(color: scheme.outlineVariant),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
      ),
      // Target sentuh minimal 48 dp (BACKLOG #29).
      materialTapTargetSize: MaterialTapTargetSize.padded,
      snackBarTheme: const SnackBarThemeData(behavior: SnackBarBehavior.floating),
    );
  }
}
