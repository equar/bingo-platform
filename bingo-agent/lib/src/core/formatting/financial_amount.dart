import 'package:intl/intl.dart';

/// Formats ledger amounts without silently presenting malformed values as zero.
String formatFinancialAmount(String value, NumberFormat formatter) {
  final amount = double.tryParse(value);
  if (amount == null || !amount.isFinite) return '—';
  return formatter.format(amount);
}
