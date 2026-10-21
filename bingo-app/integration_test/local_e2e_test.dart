import 'dart:convert';
import 'dart:io';
import 'dart:math' as math;

import 'package:bingo_app/src/app.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

const apiBaseUrl = String.fromEnvironment('API_BASE_URL', defaultValue: '');

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  const phase = String.fromEnvironment('BINGO_E2E_PHASE', defaultValue: 'all');
  const runId = String.fromEnvironment(
    'BINGO_E2E_RUN_ID',
    defaultValue: 'local',
  );
  const primaryPhone = String.fromEnvironment(
    'BINGO_E2E_PRIMARY_PHONE',
    defaultValue: '',
  );
  const primaryPassword = String.fromEnvironment(
    'BINGO_E2E_PRIMARY_PASSWORD',
    defaultValue: '',
  );
  const depositAmount = String.fromEnvironment(
    'BINGO_E2E_DEPOSIT_AMOUNT',
    defaultValue: '100.00',
  );
  const secondDepositAmount = String.fromEnvironment(
    'BINGO_E2E_SECOND_DEPOSIT_AMOUNT',
    defaultValue: '50.00',
  );

  testWidgets('player setup and deposits', (tester) async {
    if (!phaseEnabled(phase, 'setup')) {
      return;
    }
    requirePrimaryCredentials(primaryPhone, primaryPassword);

    await pumpApp(tester);

    await enterText(
      tester,
      find.byKey(const ValueKey('auth-phone-field')),
      primaryPhone,
    );
    await enterText(
      tester,
      find.byKey(const ValueKey('auth-password-field')),
      generateSixDigitPin(except: primaryPassword),
    );
    await tap(tester, find.byKey(const ValueKey('auth-submit-button')));
    await pumpAndSettleBriefly(tester);
    expect(find.byKey(const ValueKey('auth-phone-field')), findsOneWidget);

    await enterText(
      tester,
      find.byKey(const ValueKey('auth-password-field')),
      primaryPassword,
    );
    await tap(tester, find.byKey(const ValueKey('auth-submit-button')));
    await pumpUntil(tester, find.byType(NavigationBar));

    await tap(tester, find.text('Wallet'));
    await pumpUntil(
      tester,
      find.byKey(const ValueKey('wallet-submit-deposit-button')),
    );

    final firstDepositRef = 'E2E-$runId-DEP-1';
    final normalizedFirstDepositRef = normalizeTelebirrReference(
      firstDepositRef,
    );
    await ensureDepositVisibleOrSubmit(
      tester,
      depositAmount,
      firstDepositRef,
      normalizedFirstDepositRef,
    );

    final secondDepositRef = 'E2E-$runId-DEP-2';
    final normalizedSecondDepositRef = normalizeTelebirrReference(
      secondDepositRef,
    );
    await ensureDepositVisibleOrSubmit(
      tester,
      secondDepositAmount,
      secondDepositRef,
      normalizedSecondDepositRef,
    );

    await submitDeposit(tester, depositAmount, firstDepositRef);
    await pumpAndSettleBriefly(tester);
    await tap(tester, find.text('Deposits'));
    await pumpUntil(tester, find.textContaining(normalizedFirstDepositRef));

    await tap(tester, find.text('Profile'));
    await pumpUntil(tester, find.byType(FilledButton));
    await tap(tester, find.byType(FilledButton));
    await pumpUntil(tester, find.byKey(const ValueKey('auth-submit-button')));

    final secondaryPhone = generateSecondaryPhone(runId);
    final secondaryPassword = generateSixDigitPin(except: primaryPassword);
    final secondaryName = 'E2E Secondary $runId';
    await enterText(
      tester,
      find.byKey(const ValueKey('auth-phone-field')),
      secondaryPhone,
    );
    await enterText(
      tester,
      find.byKey(const ValueKey('auth-password-field')),
      secondaryPassword,
    );
    await tap(tester, find.byKey(const ValueKey('auth-submit-button')));
    await pumpUntil(tester, find.byType(NavigationBar));

    await tap(tester, find.text('Wallet'));
    await pumpUntil(tester, find.textContaining('0'));
    await saveManifest({
      'runId': runId,
      'backendBaseUrl': apiBaseUrl,
      'player': {
        'primaryPhone': primaryPhone,
        'secondaryPhone': secondaryPhone,
        'secondaryName': secondaryName,
      },
      'deposits': [
        {'reference': normalizedFirstDepositRef, 'amount': depositAmount},
        {
          'reference': normalizedSecondDepositRef,
          'amount': secondDepositAmount,
        },
      ],
    });
  });

  testWidgets('player purchase and invalid claim', (tester) async {
    if (!phaseEnabled(phase, 'purchase')) {
      return;
    }

    final manifest = await readManifest();
    final sessionId = manifest['session']?['id'] as num?;
    if (sessionId == null) {
      fail('Session id missing from manifest');
    }
    await pumpApp(tester);
    await loginPrimaryPlayer(tester, primaryPhone, primaryPassword);

    await tap(tester, find.text('Lobby'));
    await pumpUntil(tester, find.byKey(const ValueKey('session-id-field')));
    await enterText(
      tester,
      find.byKey(const ValueKey('session-id-field')),
      sessionId.toString(),
    );
    await tap(tester, find.byTooltip('Load session'));
    await pumpUntil(tester, find.byKey(const ValueKey('buy-ticket-button')));

    await tap(tester, find.byKey(const ValueKey('buy-ticket-button')));
    await pumpUntil(tester, find.byType(ChoiceChip));
    await tap(tester, find.byType(ChoiceChip).first);
    final pageScrollable = find.byWidgetPredicate(
      (widget) =>
          widget is Scrollable &&
          widget.physics is AlwaysScrollableScrollPhysics,
    );
    final firstTicketCell = find.byKey(const ValueKey('ticket-cell-0-0'));
    await tester.scrollUntilVisible(
      firstTicketCell,
      300,
      scrollable: pageScrollable,
    );
    await tester.pumpAndSettle();

    final winningDraws = <int>[];
    for (var column = 0; column < 5; column += 1) {
      final finder = find.byKey(ValueKey('ticket-cell-0-$column'));
      final textFinder = find.descendant(
        of: finder,
        matching: find.byType(Text),
      );
      final text = tester.widget<Text>(textFinder.first).data?.trim();
      if (text == null) {
        fail('Unable to read ticket cell 0,$column');
      }
      final value = int.tryParse(text);
      if (value == null) {
        fail('Invalid ticket cell value: $text');
      }
      winningDraws.add(value);
    }

    await enterClaimPatternAndSubmit(tester, 'LINE');

    await saveManifest({
      'session': {'id': sessionId.toInt()},
      'winningDraws': winningDraws,
    });
  });

  testWidgets('player verification after settlement', (tester) async {
    if (!phaseEnabled(phase, 'verify')) {
      return;
    }

    await pumpApp(tester);
    await loginPrimaryPlayer(tester, primaryPhone, primaryPassword);

    final primaryLogin = await backendLogin(
      apiBaseUrl,
      primaryPhone,
      primaryPassword,
    );
    final primaryWallet =
        (await backendJson(
              apiBaseUrl,
              'GET',
              '/api/v1/wallet/transactions?page=0&size=50',
              token: primaryLogin['accessToken'] as String,
            ))
            as Map<String, dynamic>;
    final primaryTransactions = (primaryWallet['content'] as List<dynamic>)
        .cast<Map<String, dynamic>>();
    expect(
      primaryTransactions.any(
        (item) => item['transactionType'] == 'TICKET_PURCHASE_DEBIT',
      ),
      isTrue,
    );
    expect(
      primaryTransactions.any(
        (item) => item['transactionType'] == 'PRIZE_CREDIT',
      ),
      isTrue,
    );
  });

  testWidgets('player creates support ticket from the app', (tester) async {
    if (!phaseEnabled(phase, 'support')) {
      return;
    }

    requirePrimaryCredentials(primaryPhone, primaryPassword);
    final subject =
        'Player app support verification ${DateTime.now().toUtc().toIso8601String()}';

    await pumpApp(tester);
    await loginPrimaryPlayer(tester, primaryPhone, primaryPassword);
    await tap(tester, find.byKey(const ValueKey('support-tab')));
    await pumpUntil(
      tester,
      find.byKey(const ValueKey('support-create-button')),
    );
    await tap(tester, find.byKey(const ValueKey('support-create-button')));
    await pumpUntil(
      tester,
      find.byKey(const ValueKey('support-subject-field')),
    );
    await enterText(
      tester,
      find.byKey(const ValueKey('support-subject-field')),
      subject,
    );
    await enterText(
      tester,
      find.byKey(const ValueKey('support-description-field')),
      'Created through the player app to verify the admin support queue.',
    );
    await tap(tester, find.byKey(const ValueKey('support-submit-button')));
    await pumpUntil(tester, find.text(subject));
  });

  testWidgets('player submits winning claim', (tester) async {
    if (!phaseEnabled(phase, 'claim')) {
      return;
    }

    final manifest = await readManifest();
    final sessionId = manifest['session']?['id'] as num?;
    if (sessionId == null) {
      fail('Session id missing from manifest');
    }
    await pumpApp(tester);
    await loginPrimaryPlayer(tester, primaryPhone, primaryPassword);

    final loginResponse = await backendLogin(
      apiBaseUrl,
      primaryPhone,
      primaryPassword,
    );
    final ticketsResponse =
        await backendJson(
              apiBaseUrl,
              'GET',
              '/api/v1/sessions/$sessionId/my-tickets?page=0&size=20',
              token: loginResponse['accessToken'] as String,
            )
            as Map<String, dynamic>;
    final tickets = (ticketsResponse['content'] as List<dynamic>)
        .cast<Map<String, dynamic>>();
    if (tickets.isEmpty) {
      fail('No tickets found for session $sessionId');
    }
    final ticketId = tickets.first['id'] as num;
    final claimResponse =
        await backendJson(
              apiBaseUrl,
              'POST',
              '/api/v1/tickets/$ticketId/win-claims',
              token: loginResponse['accessToken'] as String,
              body: {'claimedPattern': 'LINE'},
              idempotencyKey: '$runId:claim:$ticketId',
            )
            as Map<String, dynamic>;

    expect(claimResponse['status'], 'PENDING_REVIEW');

    await saveManifest({
      'claim': {
        'sessionId': sessionId.toInt(),
        'ticketId': ticketId.toInt(),
        'pattern': 'LINE',
        'status': 'PENDING_REVIEW',
      },
    });
  });
}

Map<String, dynamic>? _manifestCache;

Future<void> pumpApp(WidgetTester tester) async {
  if (apiBaseUrl.isEmpty) {
    throw StateError(
      'Set API_BASE_URL explicitly with --dart-define=API_BASE_URL=... before running local E2E tests.',
    );
  }
  await tester.pumpWidget(const BingoApp());
  await tester.pumpAndSettle();
  if (find.byKey(const ValueKey('auth-phone-field')).evaluate().isEmpty) {
    if (find.text('Profile').evaluate().isNotEmpty) {
      await tap(tester, find.text('Profile'));
      await pumpUntil(tester, find.byType(FilledButton));
      await tap(tester, find.byType(FilledButton));
      await pumpUntil(tester, find.byKey(const ValueKey('auth-submit-button')));
    }
  }

  await pumpUntil(tester, find.byKey(const ValueKey('auth-phone-field')));
}

bool phaseEnabled(String selectedPhase, String expectedPhase) {
  return selectedPhase == 'all' || selectedPhase == expectedPhase;
}

Future<void> loginPlayer(
  WidgetTester tester,
  String phone,
  String password,
) async {
  await enterText(
    tester,
    find.byKey(const ValueKey('auth-phone-field')),
    phone,
  );
  await enterText(
    tester,
    find.byKey(const ValueKey('auth-password-field')),
    password,
  );
  await tap(tester, find.byKey(const ValueKey('auth-submit-button')));
  await pumpUntil(tester, find.byType(NavigationBar));
}

Future<void> loginPrimaryPlayer(
  WidgetTester tester,
  String phone,
  String password,
) async {
  requirePrimaryCredentials(phone, password);
  await loginPlayer(tester, phone, password);
}

void requirePrimaryCredentials(String phone, String password) {
  if (phone.trim().isEmpty) {
    fail('BINGO_E2E_PRIMARY_PHONE must be provided');
  }
  if (password.trim().isEmpty) {
    fail('BINGO_E2E_PRIMARY_PASSWORD must be provided');
  }
}

String generateSixDigitPin({String except = ''}) {
  final random = math.Random.secure();
  var pin = random.nextInt(1000000).toString().padLeft(6, '0');
  while (pin == except) {
    pin = random.nextInt(1000000).toString().padLeft(6, '0');
  }
  return pin;
}

Future<void> submitDeposit(
  WidgetTester tester,
  String amount,
  String reference,
) async {
  await tap(tester, find.byKey(const ValueKey('wallet-submit-deposit-button')));
  await pumpUntil(tester, find.byKey(const ValueKey('deposit-amount-field')));
  await enterText(
    tester,
    find.byKey(const ValueKey('deposit-amount-field')),
    amount,
  );
  await enterText(
    tester,
    find.byKey(const ValueKey('deposit-reference-field')),
    reference,
  );
  await tap(tester, find.text('Confirm'));
  await pumpAndSettleBriefly(tester);
}

Future<void> ensureDepositVisibleOrSubmit(
  WidgetTester tester,
  String amount,
  String reference,
  String normalizedReference,
) async {
  await tap(tester, find.text('Deposits'));
  await pumpAndSettleBriefly(tester);
  if (find.textContaining(normalizedReference).evaluate().isEmpty) {
    await submitDeposit(tester, amount, reference);
    await tap(tester, find.text('Deposits'));
    await pumpUntil(tester, find.textContaining(normalizedReference));
  } else {
    await pumpUntil(tester, find.textContaining(normalizedReference));
  }
}

Future<void> enterClaimPatternAndSubmit(
  WidgetTester tester,
  String pattern,
) async {
  final claimPatternField = find.byKey(const ValueKey('claim-pattern-field'));
  final pageScrollable = find.byWidgetPredicate(
    (widget) =>
        widget is Scrollable && widget.physics is AlwaysScrollableScrollPhysics,
  );
  await tester.scrollUntilVisible(
    claimPatternField,
    300,
    scrollable: pageScrollable,
  );
  await tester.pumpAndSettle();
  await tap(tester, claimPatternField);
  await pumpAndSettleBriefly(tester);
  await tap(tester, find.text(pattern).last);
  await pumpAndSettleBriefly(tester);
  final claimSubmitButton = find.byKey(const ValueKey('claim-submit-button'));
  await tester.scrollUntilVisible(
    claimSubmitButton,
    300,
    scrollable: pageScrollable,
  );
  await tester.pumpAndSettle();
  await tap(tester, claimSubmitButton);
  await pumpAndSettleBriefly(tester);
}

Future<void> enterText(WidgetTester tester, Finder finder, String value) async {
  final textField = find.descendant(
    of: finder,
    matching: find.byType(EditableText),
  );
  await tester.enterText(textField, value);
  await tester.pump();
}

Future<void> tap(WidgetTester tester, Finder finder) async {
  await tester.tap(finder);
  await tester.pump();
}

Future<void> pumpUntil(
  WidgetTester tester,
  Finder finder, {
  Duration timeout = const Duration(seconds: 15),
}) async {
  final deadline = DateTime.now().add(timeout);
  while (DateTime.now().isBefore(deadline)) {
    if (finder.evaluate().isNotEmpty) {
      return;
    }
    await tester.pump(const Duration(milliseconds: 200));
  }
  fail('Timed out waiting for $finder');
}

Future<void> pumpAndSettleBriefly(WidgetTester tester) async {
  await tester.pump(const Duration(milliseconds: 300));
  await tester.pumpAndSettle(const Duration(seconds: 2));
}

Future<Map<String, dynamic>> readManifest() async {
  final path = const String.fromEnvironment(
    'BINGO_E2E_MANIFEST_PATH',
    defaultValue: '',
  );
  if (path.isNotEmpty) {
    final file = File(path);
    if (await file.exists()) {
      try {
        final raw = await file.readAsString();
        final parsed = jsonDecode(raw) as Map<String, dynamic>;
        parsed.remove('primaryPassword');
        parsed.remove('secondaryPassword');
        final player = parsed['player'];
        if (player is Map<String, dynamic>) {
          player.remove('primaryPassword');
          player.remove('secondaryPassword');
        }
        _manifestCache = parsed;
        return parsed;
      } catch (_) {
        // Fall through to the in-memory/bootstrap state below.
      }
    }
  }

  if (_manifestCache != null) {
    return _manifestCache!;
  }

  final bootstrap = <String, dynamic>{
    'runId': const String.fromEnvironment(
      'BINGO_E2E_RUN_ID',
      defaultValue: 'local',
    ),
    'backendBaseUrl': const String.fromEnvironment(
      'BINGO_E2E_BACKEND_URL',
      defaultValue: apiBaseUrl,
    ),
    'adminBaseUrl': const String.fromEnvironment(
      'BINGO_E2E_ADMIN_URL',
      defaultValue: 'http://localhost:5173',
    ),
    'session': {
      'id': int.tryParse(
        const String.fromEnvironment('BINGO_E2E_SESSION_ID', defaultValue: ''),
      ),
    },
    'player': {
      'primaryPhone': const String.fromEnvironment(
        'BINGO_E2E_PRIMARY_PHONE',
        defaultValue: '',
      ),
      'secondaryPhone': const String.fromEnvironment(
        'BINGO_E2E_SECONDARY_PHONE',
        defaultValue: '',
      ),
      'secondaryName': const String.fromEnvironment(
        'BINGO_E2E_SECONDARY_NAME',
        defaultValue: '',
      ),
    },
  };

  _manifestCache = bootstrap;
  return bootstrap;
}

Future<void> saveManifest(Map<String, dynamic> patch) async {
  final path = const String.fromEnvironment(
    'BINGO_E2E_MANIFEST_PATH',
    defaultValue: '',
  );
  final file = File(path);
  final current = await readManifest();
  final next = <String, dynamic>{...current, ...patch};
  _manifestCache = next;
  if (path.isEmpty) {
    return;
  }
  await file.parent.create(recursive: true);
  try {
    await file.writeAsString(const JsonEncoder.withIndent('  ').convert(next));
  } catch (_) {
    // Persisting to the requested path is best-effort on Android; keep the
    // updated manifest in memory for the rest of this run.
  }
}

Future<Map<String, dynamic>> backendLogin(
  String baseUrl,
  String phoneNumber,
  String password,
) async {
  return (await backendJson(
        baseUrl,
        'POST',
        '/api/v1/auth/login',
        body: {'phoneNumber': phoneNumber, 'password': password},
      ))
      as Map<String, dynamic>;
}

Future<dynamic> backendJson(
  String baseUrl,
  String method,
  String path, {
  String? token,
  Object? body,
  String? idempotencyKey,
}) async {
  final client = HttpClient();
  try {
    final request = await client.openUrl(method, Uri.parse('$baseUrl$path'));
    if (token != null) {
      request.headers.set(HttpHeaders.authorizationHeader, 'Bearer $token');
    }
    if (body != null) {
      request.headers.contentType = ContentType.json;
      if (idempotencyKey != null) {
        request.headers.set('Idempotency-Key', idempotencyKey);
      }
      request.write(jsonEncode(body));
    }

    final response = await request.close();
    final responseBody = await utf8.decodeStream(response);
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw StateError(
        '$method $path failed: ${response.statusCode} $responseBody',
      );
    }
    if (responseBody.isEmpty) {
      return null;
    }
    return jsonDecode(responseBody);
  } finally {
    client.close(force: true);
  }
}

String generateSecondaryPhone(String seed) {
  final digits = seed.hashCode.abs().toString().padLeft(8, '0');
  return '09${digits.substring(0, 8)}';
}

String normalizeTelebirrReference(String reference) {
  return reference.replaceAll(RegExp(r'[^A-Za-z0-9]'), '').toUpperCase();
}
