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
    expect(
      darkTheme.colorScheme.onSurface.computeLuminance(),
      greaterThan(darkTheme.colorScheme.surface.computeLuminance()),
    );
  });
}
