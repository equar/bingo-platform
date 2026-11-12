import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

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
  static const _selectedTicketsKey = 'player.selectedTicketIds';
  static const _manualMarkPrefix = 'player.manualMarks.';

  ThemeMode themeMode = ThemeMode.system;
  bool autoMark = true;
  bool hideUnregisteredCards = false;
  bool soundEnabled = true;
  bool notificationsEnabled = true;
  int cardsOnBoard = 2;
  int selectionColorIndex = 0;
  List<String> sortPriorities = [];
  bool loaded = false;
  bool multipleCardSelection = false;
  Set<int> selectedTicketIds = {};

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
    'Lines (anywhere)',
    'Lines touching free',
    'Lines not touching free',
    'Non-crossing lines',
    'Squares',
    'Rectangles',
    'Horizontal lines',
    'Vertical lines',
    'Big T',
    'Big Z',
    'Big H',
    'Big L',
    'Big U',
    'Diagonal lines',
    'Most called',
    'Small X',
    'Small T',
    'Small cross (+)',
    'Corner balls',
    'Half house',
    'Big O (border)',
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
    notificationsEnabled = prefs.getBool(_notificationsKey) ?? true;
    cardsOnBoard = [2, 3, 4].contains(prefs.getInt(_boardSizeKey))
        ? prefs.getInt(_boardSizeKey)!
        : 2;
    selectionColorIndex = (prefs.getInt(_selectionColorKey) ?? 0).clamp(
      0,
      selectionColors.length - 1,
    );
    sortPriorities = prefs.getStringList(_sortKey) ?? [];
    multipleCardSelection = prefs.getBool(_multipleCardsKey) ?? false;
    selectedTicketIds = (prefs.getStringList(_selectedTicketsKey) ?? [])
        .map(int.tryParse)
        .whereType<int>()
        .toSet();
    for (final key in prefs.getKeys().where(
      (key) => key.startsWith(_manualMarkPrefix),
    )) {
      final ticketId = int.tryParse(key.substring(_manualMarkPrefix.length));
      if (ticketId == null) continue;
      _manualMarks[ticketId] = (prefs.getStringList(key) ?? [])
          .map(int.tryParse)
          .whereType<int>()
          .toSet();
    }
    loaded = true;
    notifyListeners();
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
    await _save(_soundKey, value);
  }

  Future<void> setNotifications(bool value) async {
    notificationsEnabled = value;
    await _save(_notificationsKey, value);
  }

  Future<void> setBoardSize(int value) async {
    cardsOnBoard = value;
    await _save(_boardSizeKey, value);
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
      _selectedTicketsKey,
      selectedTicketIds.map((id) => id.toString()).toList(),
    );
    notifyListeners();
  }

  Future<void> toggleSelectedTicket(int ticketId) async {
    final next = {...selectedTicketIds};
    if (!next.add(ticketId)) next.remove(ticketId);
    selectedTicketIds = next;
    await _save(_selectedTicketsKey, next.map((id) => id.toString()).toList());
  }

  Set<int> manualMarksForTicket(int ticketId) {
    // Marks are loaded lazily from SharedPreferences by the async preload.
    return _manualMarks[ticketId] ?? const {};
  }

  final Map<int, Set<int>> _manualMarks = {};

  Future<void> toggleManualMark(int ticketId, int number) async {
    final marks = {...?_manualMarks[ticketId]};
    if (!marks.add(number)) marks.remove(number);
    _manualMarks[ticketId] = marks;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setStringList(
      '$_manualMarkPrefix$ticketId',
      marks.map((value) => value.toString()).toList(),
    );
    notifyListeners();
  }

  Future<void> clearManualMarks() async {
    final prefs = await SharedPreferences.getInstance();
    for (final key in prefs.getKeys().where(
      (key) => key.startsWith(_manualMarkPrefix),
    )) {
      await prefs.remove(key);
    }
    _manualMarks.clear();
    notifyListeners();
  }

  Future<void> setSelectionColor(int value) async {
    selectionColorIndex = value;
    await _save(_selectionColorKey, value);
  }

  Future<void> toggleSortPriority(String value) async {
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
