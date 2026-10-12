import 'package:bingo_app/src/core/models/page_result.dart';
import 'package:bingo_app/src/core/network/api_provider.dart';
import 'package:bingo_app/src/features/wallet/data/wallet_api.dart';
import 'package:bingo_app/src/features/wallet/domain/wallet_models.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final walletRepositoryProvider = Provider<WalletRepository>((ref) {
  return WalletRepository(ref.watch(walletApiProvider));
});

class WalletRepository {
  WalletRepository(this._api);

  final WalletApi _api;

  Future<WalletBalance> getBalance() => _api.getBalance();

  Future<PageResult<WalletTransaction>> getTransactions({required int page, required int size}) =>
      _api.getTransactions(page: page, size: size);

  Future<PageResult<DepositClaim>> getDeposits({required int page, required int size}) =>
      _api.getDeposits(page: page, size: size);

  Future<DepositClaim> submitDeposit(DepositRequest request, String idempotencyKey) =>
      _api.submitDeposit(request, idempotencyKey);
}