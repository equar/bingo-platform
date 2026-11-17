import 'dart:async';

import 'package:bingo_player/src/core/widgets/purchase_progress_dialog.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('shows progress until the operation completes', (tester) async {
    final operation = Completer<void>();
    late Future<void> pendingOperation;

    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder: (context) => Scaffold(
            body: TextButton(
              onPressed: () {
                pendingOperation = runWithProgressDialog<void>(
                  context: context,
                  message: 'Processing purchase...',
                  operation: () => operation.future,
                );
              },
              child: const Text('Purchase'),
            ),
          ),
        ),
      ),
    );

    await tester.tap(find.text('Purchase'));
    await tester.pump();

    expect(find.byType(CircularProgressIndicator), findsOneWidget);
    expect(find.text('Processing purchase...'), findsOneWidget);

    operation.complete();
    await pendingOperation;
    await tester.pumpAndSettle();

    expect(find.byType(CircularProgressIndicator), findsNothing);
    expect(find.text('Processing purchase...'), findsNothing);
  });
}
