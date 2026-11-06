import 'package:flutter/material.dart';

/// Shared, inexpensive Material styling; system fonts retain native text scaling.
ThemeData buildAppTheme() {
  final scheme = ColorScheme.fromSeed(
    seedColor: const Color(0xFF1688FF),
    primary: const Color(0xFF1264C8),
    secondary: const Color(0xFFFF7900),
    surface: Colors.white,
    onSurface: const Color(0xFF081D46),
    outlineVariant: const Color(0xFFE2E7F0),
  );
  final outline = OutlineInputBorder(borderRadius: BorderRadius.circular(14));
  return ThemeData(
    useMaterial3: true,
    colorScheme: scheme,
    scaffoldBackgroundColor: const Color(0xFFFAFBFD),
    materialTapTargetSize: MaterialTapTargetSize.padded,
    textTheme: ThemeData.light().textTheme.copyWith(
      headlineMedium: const TextStyle(
        fontFamily: 'Roboto',
        fontSize: 30,
        fontWeight: FontWeight.w800,
        color: Color(0xFF081D46),
        letterSpacing: -.8,
      ),
      headlineSmall: const TextStyle(
        fontFamily: 'Roboto',
        fontSize: 24,
        fontWeight: FontWeight.w800,
        color: Color(0xFF081D46),
        letterSpacing: -.5,
      ),
      titleLarge: const TextStyle(
        fontFamily: 'Roboto',
        fontSize: 21,
        fontWeight: FontWeight.w800,
        color: Color(0xFF081D46),
        letterSpacing: -.3,
      ),
      titleMedium: const TextStyle(
        fontFamily: 'Roboto',
        fontSize: 16,
        fontWeight: FontWeight.w700,
        color: Color(0xFF081D46),
      ),
      bodyLarge: const TextStyle(
        fontFamily: 'Roboto',
        fontSize: 16,
        height: 1.5,
        color: Color(0xFF4D5B73),
      ),
      bodyMedium: const TextStyle(
        fontFamily: 'Roboto',
        fontSize: 14,
        height: 1.45,
        color: Color(0xFF4D5B73),
      ),
    ),
    cardTheme: CardThemeData(
      elevation: 0,
      margin: EdgeInsets.zero,
      color: Colors.white,
      surfaceTintColor: Colors.transparent,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(22),
        side: BorderSide(color: scheme.outlineVariant),
      ),
    ),
    bottomSheetTheme: const BottomSheetThemeData(
      backgroundColor: Colors.white,
      surfaceTintColor: Colors.transparent,
      showDragHandle: true,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
      ),
    ),
    chipTheme: ChipThemeData(
      backgroundColor: const Color(0xFFF1F5FB),
      side: BorderSide.none,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      labelStyle: const TextStyle(
        fontFamily: 'Roboto',
        color: Color(0xFF4D5B73),
        fontSize: 12,
        fontWeight: FontWeight.w600,
      ),
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 4),
    ),
    tabBarTheme: TabBarThemeData(
      labelColor: scheme.primary,
      unselectedLabelColor: const Color(0xFF667084),
      labelStyle: const TextStyle(
        fontFamily: 'Roboto',
        fontSize: 14,
        fontWeight: FontWeight.w800,
      ),
      unselectedLabelStyle: const TextStyle(
        fontFamily: 'Roboto',
        fontSize: 14,
        fontWeight: FontWeight.w600,
      ),
      indicatorSize: TabBarIndicatorSize.label,
      dividerColor: scheme.outlineVariant,
    ),
    snackBarTheme: SnackBarThemeData(
      behavior: SnackBarBehavior.floating,
      backgroundColor: const Color(0xFF081D46),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
    ),
    appBarTheme: const AppBarTheme(
      backgroundColor: Color(0xFFFAFBFD),
      foregroundColor: Color(0xFF081D46),
      surfaceTintColor: Colors.transparent,
      centerTitle: false,
      titleSpacing: 20,
      titleTextStyle: TextStyle(
        fontFamily: 'Roboto',
        fontSize: 23,
        fontWeight: FontWeight.w800,
        color: Color(0xFF081D46),
        letterSpacing: -.5,
      ),
      scrolledUnderElevation: 0,
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: Colors.white,
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
      border: outline,
      enabledBorder: outline.copyWith(
        borderSide: BorderSide(color: scheme.outlineVariant),
      ),
      focusedBorder: outline.copyWith(
        borderSide: BorderSide(color: scheme.primary, width: 2),
      ),
      errorMaxLines: 4,
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        // A deeper orange keeps the original palette and improves white-label contrast.
        backgroundColor: const Color(0xFFBA4900),
        foregroundColor: Colors.white,
        minimumSize: const Size(48, 48),
        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        textStyle: const TextStyle(
          fontFamily: 'Roboto',
          fontSize: 16,
          fontWeight: FontWeight.w700,
        ),
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(minimumSize: const Size(48, 48)),
    ),
    textButtonTheme: TextButtonThemeData(
      style: TextButton.styleFrom(minimumSize: const Size(48, 48)),
    ),
    dividerTheme: DividerThemeData(color: scheme.outlineVariant, thickness: 1),
    progressIndicatorTheme: ProgressIndicatorThemeData(color: scheme.primary),
  );
}
