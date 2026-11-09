import 'package:flutter/material.dart';

/// Keeps transaction forms readable on tablets while filling phone screens.
class ResponsiveFormContent extends StatelessWidget {
  const ResponsiveFormContent({super.key, required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) => Center(
    child: ConstrainedBox(
      constraints: const BoxConstraints(maxWidth: 560),
      child: child,
    ),
  );
}
