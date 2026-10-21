import 'package:bingo_app/src/core/network/api_client.dart';
import 'package:bingo_app/src/features/auth/data/auth_api.dart';
import 'package:bingo_app/src/features/lobby/data/lobby_api.dart';
import 'package:bingo_app/src/features/support/data/support_api.dart';
import 'package:bingo_app/src/features/wallet/data/wallet_api.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final authApiProvider =
    Provider<AuthApi>((ref) => AuthApi(ref.watch(dioProvider)));
final lobbyApiProvider =
    Provider<LobbyApi>((ref) => LobbyApi(ref.watch(dioProvider)));
final supportApiProvider =
    Provider<SupportApi>((ref) => SupportApi(ref.watch(dioProvider)));
final walletApiProvider =
    Provider<WalletApi>((ref) => WalletApi(ref.watch(dioProvider)));
