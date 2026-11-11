import 'package:bingo_app/src/core/theme/app_theme.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('builds accessible light and dark application themes', () {
    final lightTheme = buildAppTheme();
    final darkTheme = buildAppTheme(brightness: Brightness.dark);

    expect(lightTheme.brightness, Brightness.light);
    expect(darkTheme.brightness, Brightness.dark);
    expect(darkTheme.scaffoldBackgroundColor, const Color(0xFF101725));
    _expectTextContrast(
      darkTheme.colorScheme.onSurface,
      darkTheme.colorScheme.surface,
    );
    _expectTextContrast(
      darkTheme.colorScheme.onSurfaceVariant,
      darkTheme.colorScheme.surfaceContainerHigh,
    );
    _expectTextContrast(
      darkTheme.colorScheme.onPrimaryContainer,
      darkTheme.colorScheme.primaryContainer,
    );
    _expectTextContrast(
      darkTheme.colorScheme.onSecondaryContainer,
      darkTheme.colorScheme.secondaryContainer,
    );
    _expectTextContrast(
      darkTheme.colorScheme.onTertiaryContainer,
      darkTheme.colorScheme.tertiaryContainer,
    );
  });
}

void _expectTextContrast(Color foreground, Color background) {
  final foregroundLuminance = foreground.computeLuminance();
  final backgroundLuminance = background.computeLuminance();
  final contrast =
      (foregroundLuminance > backgroundLuminance
          ? foregroundLuminance + .05
          : backgroundLuminance + .05) /
      (foregroundLuminance > backgroundLuminance
          ? backgroundLuminance + .05
          : foregroundLuminance + .05);

  expect(contrast, greaterThanOrEqualTo(4.5));
}
