import 'package:bingo_player/src/core/network/api_client.dart';
import 'package:bingo_player/src/features/game/data/game_api.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final gameApiProvider = Provider<GameApi>((ref) => GameApi(ref.watch(dioProvider)));
