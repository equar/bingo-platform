import 'dart:convert';
import 'dart:typed_data';

import 'package:bingo_player/src/features/auth/data/auth_api.dart';
import 'package:bingo_player/src/features/auth/data/auth_models.dart';
import 'package:bingo_player/src/features/game/data/game_api.dart';
import 'package:bingo_player/src/features/wallet/data/wallet_api.dart';
import 'package:bingo_player/src/features/wallet/domain/wallet_models.dart';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';

class ApiTestAdapter implements HttpClientAdapter {
  final requests = <RequestOptions>[];
  int statusCode = 200;
  Object response = <String, dynamic>{};

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);
    final body = jsonEncode(response);
    return ResponseBody.fromString(
      body,
      statusCode,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

void main() {
  late ApiTestAdapter adapter;
  late Dio dio;

  setUp(() {
    adapter = ApiTestAdapter();
    dio = Dio(BaseOptions(baseUrl: 'https://api.example.test'))
      ..httpClientAdapter = adapter;
  });

  tearDown(() => dio.close(force: true));

  group('WalletApi direct HTTP client contract', () {
    test('posts a deposit with its idempotency key and parses response', () async {
      adapter.response = {
        'id': 9,
        'operationId': 'deposit-op',
        'playerId': 3,
        'submittedCashAmount': 25.5,
        'currency': 'ETB',
        'conversionRateSnapshot': 1,
        'calculatedCreditAmount': 25.5,
        'normalizedTelebirrReference': 'AB123',
        'status': 'PENDING',
        'submittedAt': '2026-05-01T10:00:00Z',
      };
      final result = await WalletApi(dio).submitDeposit(
        const DepositRequest(
          submittedCashAmount: 25.5,
          currency: 'ETB',
          telebirrReference: 'AB123',
        ),
        'deposit-key',
      );

      expect(result.id, 9);
      expect(adapter.requests.single.path, '/api/v1/wallet/deposits');
      expect(adapter.requests.single.headers['Idempotency-Key'], 'deposit-key');
      expect(adapter.requests.single.data, {
        'submittedCashAmount': 25.5,
        'currency': 'ETB',
        'telebirrReference': 'AB123',
      });
    });

    test('sends wallet transfer key and converts server failures to Dio errors', () async {
      adapter.response = {
        'id': 1,
        'operationId': 'transfer-op',
        'senderId': 2,
        'senderPhoneNumber': '0911000000',
        'recipientId': 3,
        'recipientPhoneNumber': '0922000000',
        'amount': 10,
        'currency': 'ETB',
        'status': 'COMPLETED',
        'senderBalanceBefore': 20,
        'senderBalanceAfter': 10,
        'recipientBalanceBefore': 0,
        'recipientBalanceAfter': 10,
        'createdAt': '2026-05-01T10:00:00Z',
      };
      final result = await WalletApi(dio).submitTransfer(
        const WalletTransferRequest(
          recipientPhoneNumber: '0922000000',
          amount: 10,
          currency: 'ETB',
        ),
        'transfer-key',
      );
      expect(result.id, 1);
      expect(adapter.requests.single.headers['Idempotency-Key'], 'transfer-key');

      adapter.statusCode = 503;
      adapter.response = {'message': 'Unavailable'};
      await expectLater(
        WalletApi(dio).getBalance(),
        throwsA(isA<DioException>()),
      );
    });
  });

  group('GameApi direct HTTP client contract', () {
    test('purchases a ticket with a stable retry key and parses the ticket', () async {
      adapter.response = {
        'ticket': {
          'id': 42,
          'sessionId': 7,
          'ownerId': 3,
          'publicTicketCode': 'T-42',
          'operationId': 'purchase-op',
          'grid': [],
          'purchasedAt': '2026-05-01T10:00:00Z',
        },
      };
      final result = await GameApi(dio).purchaseTicket(7, 'purchase-key');
      expect(result.ticket.id, 42);
      expect(adapter.requests.single.path, '/api/v1/sessions/7/tickets');
      expect(adapter.requests.single.headers['Idempotency-Key'], 'purchase-key');
    });

    test('loads live draws after a sequence cursor', () async {
      adapter.response = [
        {
          'sessionId': 7,
          'sequenceNo': 5,
          'numberDrawn': 24,
          'drawnAt': '2026-05-01T10:00:00Z',
        },
      ];
      final draws = await GameApi(dio).getDraws(7, afterSequence: 4);
      expect(draws.single.numberDrawn, 24);
      expect(adapter.requests.single.queryParameters['afterSequence'], 4);
    });
  });

  group('AuthApi direct HTTP client contract', () {
    test('sends login credentials and parses the returned session identity', () async {
      adapter.response = {
        'accessToken': 'token-value',
        'tokenType': 'Bearer',
        'expiresInSeconds': 300,
        'user': {
          'id': 3,
          'displayName': 'Player',
          'phoneNumber': '0911000000',
          'roles': ['PLAYER'],
          'status': 'ACTIVE',
          'createdAt': '2026-05-01T10:00:00Z',
          'updatedAt': '2026-05-01T10:00:00Z',
        },
      };
      final result = await AuthApi(dio).login(
        const LoginRequest(phoneNumber: '0911000000', password: 'pin'),
      );
      expect(result.accessToken, 'token-value');
      expect(result.user.isPlayer, isTrue);
      expect(adapter.requests.single.path, '/api/v1/auth/login');
      expect(adapter.requests.single.data, {
        'phoneNumber': '0911000000',
        'password': 'pin',
      });
    });

    test('logout calls the revocation endpoint', () async {
      adapter.response = <String, dynamic>{};
      await AuthApi(dio).logout();
      expect(adapter.requests.single.method, 'POST');
      expect(adapter.requests.single.path, '/api/v1/auth/logout');
    });
  });
}
