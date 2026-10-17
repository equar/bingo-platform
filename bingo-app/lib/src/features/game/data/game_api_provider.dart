import 'package:bingo_app/src/core/network/api_client.dart';
import 'package:bingo_app/src/features/game/data/game_api.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final gameApiProvider = Provider<GameApi>((ref) => GameApi(ref.watch(dioProvider)));
