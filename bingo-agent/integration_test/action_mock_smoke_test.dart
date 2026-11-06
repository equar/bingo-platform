import 'package:bingo_agent/src/features/agents/data/agent_repository.dart';
import 'package:bingo_agent/src/features/auth/data/auth_models.dart';
import 'package:bingo_agent/src/features/players/presentation/players_screen.dart';
import 'package:bingo_agent/src/l10n/generated/app_localizations.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('mocked Agent registration validates and submits on device', (
    tester,
  ) async {
    final repository = _MockAgentRepository();
    await tester.pumpWidget(
      ProviderScope(
        overrides: [agentRepositoryProvider.overrideWithValue(repository)],
        child: MaterialApp(
          localizationsDelegates: AppLocalizations.localizationsDelegates,
          supportedLocales: AppLocalizations.supportedLocales,
          home: const Scaffold(body: PlayersScreen()),
        ),
      ),
    );

    final phone = find.byType(TextField).first;
    final name = find.byType(TextField).at(1);
    final register = find.widgetWithText(OutlinedButton, 'Register');
    await tester.enterText(phone, 'invalid');
    await tester.enterText(name, 'Device QA');
    await tester.pump();

    expect(tester.widget<OutlinedButton>(register).onPressed, isNull);
    expect(repository.registerCalls, 0);

    await tester.enterText(phone, '0911000001');
    await tester.pump();
    expect(tester.widget<OutlinedButton>(register).onPressed, isNotNull);
    await tester.tap(name);
    await tester.testTextInput.receiveAction(TextInputAction.done);
    await tester.pumpAndSettle();

    expect(repository.registerCalls, 1);
    expect(repository.registeredPhone, '0911000001');
  });
}

class _MockAgentRepository extends AgentRepository {
  _MockAgentRepository() : super(Dio());

  int registerCalls = 0;
  String? registeredPhone;

  @override
  Future<UserProfile> registerPlayer(Map<String, dynamic> body) async {
    registerCalls += 1;
    registeredPhone = body['phoneNumber'] as String;
    return UserProfile(
      id: 1,
      displayName: body['displayName'] as String,
      phoneNumber: registeredPhone!,
      roles: const ['PLAYER'],
    );
  }
}
