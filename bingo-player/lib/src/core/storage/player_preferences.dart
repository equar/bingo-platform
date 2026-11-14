import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:bingo_player/src/core/platform/player_notifications.dart';

final playerPreferencesProvider = ChangeNotifierProvider<PlayerPreferences>(
  (ref) => PlayerPreferences()..load(),
);

class PlayerPreferences extends ChangeNotifier {
  static const _themeKey = 'player.themeMode';
  static const _autoMarkKey = 'player.autoMark';
  static const _hideUnregisteredKey = 'player.hideUnregistered';
  static const _boardSizeKey = 'player.boardSize';
  static const _selectionColorKey = 'player.selectionColor';
  static const _soundKey = 'player.sound';
  static const _notificationsKey = 'player.notifications';
  static const _sortKey = 'player.ticketSortPriorities';
  static const _multipleCardsKey = 'player.multipleCards';
  static const _selectedTicketsKey = 'player.selectedTicketIds.';
  static const _manualMarkPrefix = 'player.manualMarks.';
  String _scope = 'default';

  ThemeMode themeMode = ThemeMode.system;
  bool autoMark = true;
  bool hideUnregisteredCards = false;
  bool soundEnabled = true;
  bool notificationsEnabled = false;
  int cardsOnBoard = 2;
  int selectionColorIndex = 0;
  List<String> sortPriorities = [];
  bool loaded = false;
  bool multipleCardSelection = false;
  Set<int> selectedTicketIds = {};
  Future<void>? _scopeLoad;

  static const selectionColors = <Color>[
    Color(0xFF245AD8),
    Color(0xFFE91E63),
    Color(0xFF1097B6),
    Color(0xFF14945C),
    Color(0xFFF07800),
    Color(0xFFE53935),
    Color(0xFF8052D8),
    Color(0xFF18294B),
  ];

  static const sortOptions = <String>[
    'LINE',
    'TWO_LINES',
    'FULL_HOUSE',
    'FOUR_CORNERS',
    'POSTAGE_STAMP',
    'X_SHAPE',
    'T_SHAPE',
    'L_SHAPE',
    'OUTER_FRAME',
  ];

  Future<void> load() async {
    final prefs = await SharedPreferences.getInstance();
    final theme = prefs.getString(_themeKey) ?? 'system';
    themeMode = ThemeMode.values.firstWhere(
      (value) => value.name == theme,
      orElse: () => ThemeMode.system,
    );
    autoMark = prefs.getBool(_autoMarkKey) ?? true;
    hideUnregisteredCards = prefs.getBool(_hideUnregisteredKey) ?? false;
    soundEnabled = prefs.getBool(_soundKey) ?? true;
    notificationsEnabled = prefs.getBool(_notificationsKey) ?? false;
    cardsOnBoard = [1, 2, 3, 4].contains(prefs.getInt(_boardSizeKey))
        ? prefs.getInt(_boardSizeKey)!
        : 2;
    selectionColorIndex = (prefs.getInt(_selectionColorKey) ?? 0).clamp(
      0,
      selectionColors.length - 1,
    );
    sortPriorities = (prefs.getStringList(_sortKey) ?? [])
        .where(sortOptions.contains)
        .toSet()
        .take(3)
        .toList();
    multipleCardSelection = prefs.getBool(_multipleCardsKey) ?? false;
    final scopeLoad = _loadScopedData(prefs);
    _scopeLoad = scopeLoad;
    await scopeLoad;
    if (identical(_scopeLoad, scopeLoad)) _scopeLoad = null;
    loaded = true;
    notifyListeners();
  }

  Future<void> setScope(String scope) async {
    final normalized = scope.replaceAll(RegExp(r'[^a-zA-Z0-9_-]'), '_');
    if (normalized == _scope) return;
    _scope = normalized;
    _manualMarks.clear();
    selectedTicketIds = {};
    notifyListeners();
    final scopeLoad = SharedPreferences.getInstance().then((prefs) {
      if (_scope != normalized) return null;
      return _loadScopedData(prefs);
    });
    _scopeLoad = scopeLoad;
    await scopeLoad;
    if (identical(_scopeLoad, scopeLoad)) _scopeLoad = null;
    notifyListeners();
  }

  /// Retains the player's explicit selection across live ranking changes.
  /// On first use, seeds the board deterministically from owned tickets.
  Future<void> ensureBoardSelection(
    Iterable<int> availableTicketIds, {
    String? expectedScope,
  }) async {
    final targetScope = expectedScope ?? _scope;
    if (targetScope != _scope) return;
    await _scopeLoad;
    if (targetScope != _scope) return;
    final scope = targetScope;
    final available = availableTicketIds.toSet();
    if (available.isEmpty) return;
    final previous = selectedTicketIds;
    final retained = previous
        .where(available.contains)
        .take(cardsOnBoard)
        .toSet();
    final next = <int>[...retained];
    final sortedAvailable = available.toList()..sort();
    for (final id in sortedAvailable) {
      if (next.length >= cardsOnBoard) break;
      if (!next.contains(id)) next.add(id);
    }
    selectedTicketIds = next.toSet();
    if (selectedTicketIds.length != previous.length ||
        !previous.containsAll(selectedTicketIds)) {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setStringList(
        '$_selectedTicketsKey$scope',
        selectedTicketIds.map((id) => id.toString()).toList(),
      );
      notifyListeners();
    }
  }

  Future<void> _loadScopedData(SharedPreferences prefs) async {
    selectedTicketIds =
        (prefs.getStringList('$_selectedTicketsKey$_scope') ?? [])
            .map(int.tryParse)
            .whereType<int>()
            .take(4)
            .toSet();
    final prefix = '$_manualMarkPrefix$_scope.';
    for (final key in prefs.getKeys().where((key) => key.startsWith(prefix))) {
      final ticketId = int.tryParse(key.substring(prefix.length));
      if (ticketId == null) continue;
      _manualMarks[ticketId] = (prefs.getStringList(key) ?? [])
          .map(int.tryParse)
          .whereType<int>()
          .where((n) => n >= 1 && n <= 75)
          .toSet();
    }
  }

  Future<void> _save(String key, Object value) async {
    final prefs = await SharedPreferences.getInstance();
    if (value is bool) await prefs.setBool(key, value);
    if (value is int) await prefs.setInt(key, value);
    if (value is String) await prefs.setString(key, value);
    if (value is List<String>) await prefs.setStringList(key, value);
    notifyListeners();
  }

  Future<void> setTheme(ThemeMode value) async {
    themeMode = value;
    await _save(_themeKey, value.name);
  }

  Future<void> setAutoMark(bool value) async {
    autoMark = value;
    await _save(_autoMarkKey, value);
  }

  Future<void> setHideUnregistered(bool value) async {
    hideUnregisteredCards = value;
    await _save(_hideUnregisteredKey, value);
  }

  Future<void> setSound(bool value) async {
    soundEnabled = value;
    if (!value) await PlayerNotifications.stopSound();
    await _save(_soundKey, value);
  }

  Future<void> setNotifications(bool value) async {
    notificationsEnabled = value;
    await _save(_notificationsKey, value);
  }

  Future<void> setBoardSize(int value) async {
    final scope = _scope;
    cardsOnBoard = value.clamp(1, 4).toInt();
    if (selectedTicketIds.length > cardsOnBoard) {
      selectedTicketIds = selectedTicketIds.take(cardsOnBoard).toSet();
      final selectedIds = selectedTicketIds;
      final prefs = await SharedPreferences.getInstance();
      await prefs.setStringList(
        '$_selectedTicketsKey$scope',
        selectedIds.map((id) => id.toString()).toList(),
      );
    }
    await _save(_boardSizeKey, cardsOnBoard);
  }

  Future<void> setMultipleCardSelection(
    bool value, {
    int? initialTicketId,
  }) async {
    multipleCardSelection = value;
    if (value && initialTicketId != null && selectedTicketIds.isEmpty) {
      selectedTicketIds = {initialTicketId};
    } else if (!value) {
      selectedTicketIds = {};
    }
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(_multipleCardsKey, value);
    await prefs.setStringList(
      '$_selectedTicketsKey$_scope',
      selectedTicketIds.map((id) => id.toString()).toList(),
    );
    notifyListeners();
  }

  Future<void> toggleSelectedTicket(int ticketId) async {
    final key = '$_selectedTicketsKey$_scope';
    final next = {...selectedTicketIds};
    if (next.contains(ticketId)) {
      next.remove(ticketId);
    } else if (next.length < cardsOnBoard) {
      next.add(ticketId);
    } else {
      next.remove(next.first);
      next.add(ticketId);
    }
    selectedTicketIds = next;
    await _save(key, next.map((id) => id.toString()).toList());
  }

  Future<void> selectTicketForBoard(int ticketId) async {
    if (selectedTicketIds.contains(ticketId)) return;
    final key = '$_selectedTicketsKey$_scope';
    final next = {...selectedTicketIds};
    if (next.length >= cardsOnBoard) next.remove(next.first);
    next.add(ticketId);
    selectedTicketIds = next;
    await _save(key, next.map((id) => id.toString()).toList());
  }

  Set<int> manualMarksForTicket(int ticketId) {
    // Marks are loaded lazily from SharedPreferences by the async preload.
    return _manualMarks[ticketId] ?? const {};
  }

  final Map<int, Set<int>> _manualMarks = {};

  Future<void> toggleManualMark(int ticketId, int number) async {
    if (number == 0) return;
    final key = '$_manualMarkPrefix$_scope.$ticketId';
    final marks = {...?_manualMarks[ticketId]};
    if (!marks.add(number)) marks.remove(number);
    _manualMarks[ticketId] = marks;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setStringList(
      key,
      marks.map((value) => value.toString()).toList(),
    );
    notifyListeners();
  }

  Future<void> markNumbers(int ticketId, Iterable<int> numbers) async {
    final key = '$_manualMarkPrefix$_scope.$ticketId';
    final marks = {
      ...?_manualMarks[ticketId],
      ...numbers.where((number) => number != 0),
    };
    _manualMarks[ticketId] = marks;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setStringList(
      key,
      marks.map((value) => value.toString()).toList(),
    );
    notifyListeners();
  }

  Future<void> clearManualMarks() async {
    final scope = _scope;
    final prefix = '$_manualMarkPrefix$scope.';
    final prefs = await SharedPreferences.getInstance();
    for (final key in prefs.getKeys().where((key) => key.startsWith(prefix))) {
      await prefs.remove(key);
    }
    if (_scope == scope) _manualMarks.clear();
    notifyListeners();
  }

  Future<void> setSelectionColor(int value) async {
    selectionColorIndex = value.clamp(0, selectionColors.length - 1).toInt();
    await _save(_selectionColorKey, selectionColorIndex);
  }

  Future<void> toggleSortPriority(String value) async {
    if (!sortOptions.contains(value)) return;
    final next = [...sortPriorities];
    if (next.contains(value)) {
      next.remove(value);
    } else if (next.length < 3) {
      next.add(value);
    }
    sortPriorities = next;
    await _save(_sortKey, next);
  }

  Future<void> clearSortPriorities() async {
    sortPriorities = [];
    await _save(_sortKey, <String>[]);
  }
}
