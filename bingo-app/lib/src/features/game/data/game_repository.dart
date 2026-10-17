import 'package:bingo_app/src/features/game/data/game_api.dart';
import 'package:bingo_app/src/features/game/data/game_api_provider.dart';
import 'package:bingo_app/src/features/game/domain/live_game_models.dart';
import 'package:bingo_app/src/features/game/domain/ticket_models.dart';
import 'package:bingo_app/src/core/models/page_result.dart';
import 'package:bingo_app/src/features/lobby/domain/lobby_models.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final gameRepositoryProvider = Provider<GameRepository>((ref) {
  return GameRepository(ref.watch(gameApiProvider));
});

abstract class LiveGameRepository {
  Future<TicketPurchaseResponse> purchaseTicket(int sessionId, String idempotencyKey);

  Future<GameSession> getSession(int sessionId);

  Future<PageResult<TicketResponse>> getMyTickets(int sessionId, {required int page, required int size});

  Future<List<LiveDrawEvent>> getDraws(int sessionId, {int? afterSequence});

  Future<PageResult<WinClaimRecord>> getMyWinClaims({required int page, required int size});

  Future<WinClaimRecord> submitWinClaim(int ticketId, String claimedPattern, String idempotencyKey);
}

class GameRepository implements LiveGameRepository {
  GameRepository(this._api);

  final GameApi _api;

  @override
  Future<TicketPurchaseResponse> purchaseTicket(int sessionId, String idempotencyKey) {
    return _api.purchaseTicket(sessionId, idempotencyKey);
  }

  @override
  Future<GameSession> getSession(int sessionId) => _api.getSession(sessionId);

  @override
  Future<PageResult<TicketResponse>> getMyTickets(int sessionId, {required int page, required int size}) {
    return _api.getMyTickets(sessionId, page: page, size: size);
  }

  @override
  Future<List<LiveDrawEvent>> getDraws(int sessionId, {int? afterSequence}) {
    return _api.getDraws(sessionId, afterSequence: afterSequence);
  }

  @override
  Future<PageResult<WinClaimRecord>> getMyWinClaims({required int page, required int size}) {
    return _api.getMyWinClaims(page: page, size: size);
  }

  @override
  Future<WinClaimRecord> submitWinClaim(int ticketId, String claimedPattern, String idempotencyKey) {
    return _api.submitWinClaim(ticketId, claimedPattern, idempotencyKey);
  }
}
