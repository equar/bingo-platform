import 'package:bingo_player/l10n/app_localizations.dart';

/// Mirrors the server's stored reference contract: ASCII letters and digits
/// remain, all other characters are separators and are discarded.
String normalizeTelebirrReference(String value) =>
    value.trim().replaceAll(RegExp(r'[^A-Za-z0-9]'), '').toUpperCase();

String? depositReferenceError(String value, AppLocalizations l10n) {
  if (value.trim().isEmpty) return l10n.requiredField;
  final normalized = normalizeTelebirrReference(value);
  if (normalized.isEmpty || normalized.length > 120) {
    return l10n.telebirrReferenceInvalid;
  }
  return null;
}

String? depositAmountError(
  String value, {
  required String minimum,
  required String maximum,
  required AppLocalizations l10n,
}) {
  final text = value.trim();
  if (text.isEmpty) return l10n.requiredField;
  if (!RegExp(r'^-?(?:\d+(?:\.\d{1,2})?|\.\d{1,2})$').hasMatch(text)) {
    if (RegExp(r'^-?(?:\d+(?:\.\d*)?|\.\d*)$').hasMatch(text)) {
      return l10n.depositAmountPrecision;
    }
    return l10n.errorInvalidAmount;
  }

  final cents = _amountCents(text);
  if (cents == null) return l10n.errorInvalidAmount;
  final minimumCents = _amountCents(minimum);
  final maximumCents = _amountCents(maximum);
  if (minimumCents == null || maximumCents == null) {
    return l10n.errorInvalidAmount;
  }
  if (cents < minimumCents) return l10n.depositAmountPositive;
  if (cents > maximumCents) return l10n.depositAmountMaximum(maximum);
  return null;
}

BigInt? _amountCents(String value) {
  final negative = value.startsWith('-');
  final unsigned = negative ? value.substring(1) : value;
  final parts = unsigned.split('.');
  if (parts.length > 2) return null;
  final whole = parts.first.isEmpty ? '0' : parts.first;
  final fraction = parts.length == 1 ? '' : parts[1];
  try {
    final cents =
        BigInt.parse(whole) * BigInt.from(100) +
        BigInt.parse(
          fraction.padRight(2, '0').isEmpty ? '0' : fraction.padRight(2, '0'),
        );
    return negative ? -cents : cents;
  } on FormatException {
    return null;
  }
}
