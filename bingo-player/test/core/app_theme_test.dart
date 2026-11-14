import 'package:bingo_player/src/core/theme/app_theme.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('builds accessible light and dark application themes', () {
    final lightTheme = buildAppTheme();
    final darkTheme = buildAppTheme(brightness: Brightness.dark);

    expect(lightTheme.brightness, Brightness.light);
    expect(darkTheme.brightness, Brightness.dark);
    expect(darkTheme.scaffoldBackgroundColor, const Color(0xFF101725));
    const darkInputHighlightColor = Colors.white;
    expect(
      darkTheme.inputDecorationTheme.focusedBorder!.borderSide.color,
      darkInputHighlightColor,
    );
    expect(
      darkTheme.inputDecorationTheme.errorBorder!.borderSide.color,
      darkInputHighlightColor,
    );
    expect(
      darkTheme.inputDecorationTheme.focusedErrorBorder!.borderSide.color,
      darkInputHighlightColor,
    );
    expect(
      darkTheme.inputDecorationTheme.errorStyle!.color,
      darkInputHighlightColor,
    );
    const lightInputHighlightColor = Color(0xFF1264C8);
    expect(
      lightTheme.inputDecorationTheme.focusedBorder!.borderSide.color,
      lightInputHighlightColor,
    );
    expect(
      lightTheme.inputDecorationTheme.errorBorder!.borderSide.color,
      lightInputHighlightColor,
    );
    expect(
      lightTheme.inputDecorationTheme.focusedErrorBorder!.borderSide.color,
      lightInputHighlightColor,
    );
    expect(
      lightTheme.inputDecorationTheme.errorStyle!.color,
      lightInputHighlightColor,
    );
    for (final theme in [lightTheme, darkTheme]) {
      final colors = theme.colorScheme;
      _expectTextContrast(
        'onSurface/surface',
        colors.onSurface,
        colors.surface,
      );
      _expectTextContrast(
        'onSurfaceVariant/surfaceContainerHigh',
        colors.onSurfaceVariant,
        colors.surfaceContainerHigh,
      );
      _expectTextContrast(
        'chip-label/chip-background',
        theme.chipTheme.labelStyle!.color!,
        theme.chipTheme.backgroundColor!,
      );
      _expectTextContrast(
        'onPrimary/primary',
        colors.onPrimary,
        colors.primary,
      );
      _expectTextContrast(
        'onSecondary/secondary',
        colors.onSecondary,
        colors.secondary,
      );
      _expectTextContrast('onError/error', colors.onError, colors.error);
      _expectTextContrast(
        'onPrimaryContainer/primaryContainer',
        colors.onPrimaryContainer,
        colors.primaryContainer,
      );
      _expectTextContrast(
        'onSecondaryContainer/secondaryContainer',
        colors.onSecondaryContainer,
        colors.secondaryContainer,
      );
      _expectTextContrast(
        'onTertiaryContainer/tertiaryContainer',
        colors.onTertiaryContainer,
        colors.tertiaryContainer,
      );
      _expectBorderContrast(colors.outline, colors.surface);
    }
  });
}

void _expectBorderContrast(Color border, Color surface) {
  expect(_contrastRatio(border, surface), greaterThanOrEqualTo(3));
}

void _expectTextContrast(String pair, Color foreground, Color background) {
  expect(
    _contrastRatio(foreground, background),
    greaterThanOrEqualTo(4.5),
    reason: pair,
  );
}

double _contrastRatio(Color foreground, Color background) {
  final foregroundLuminance = foreground.computeLuminance();
  final backgroundLuminance = background.computeLuminance();
  return (foregroundLuminance > backgroundLuminance
          ? foregroundLuminance + .05
          : backgroundLuminance + .05) /
      (foregroundLuminance > backgroundLuminance
          ? backgroundLuminance + .05
          : foregroundLuminance + .05);
}
