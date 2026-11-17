import 'dart:async';

import 'package:flutter/material.dart';

Future<T> runWithProgressDialog<T>({
  required BuildContext context,
  required String message,
  required Future<T> Function() operation,
}) async {
  final navigator = Navigator.of(context, rootNavigator: true);
  unawaited(
    showDialog<void>(
      context: context,
      barrierDismissible: false,
      builder: (context) => PopScope(
        canPop: false,
        child: AlertDialog(
          content: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              const SizedBox(
                width: 24,
                height: 24,
                child: CircularProgressIndicator(strokeWidth: 2),
              ),
              const SizedBox(width: 16),
              Flexible(child: Text(message)),
            ],
          ),
        ),
      ),
    ).then<void>((_) {}),
  );

  try {
    return await operation();
  } finally {
    if (context.mounted && navigator.mounted && navigator.canPop()) {
      navigator.pop();
    }
  }
}
