// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for English (`en`).
class AppLocalizationsEn extends AppLocalizations {
  AppLocalizationsEn([String locale = 'en']) : super(locale);

  @override
  String get appTitle => 'Bingo Agent';

  @override
  String get loginTitle => 'Agent login';

  @override
  String get passwordLabel => 'Password';

  @override
  String get pinLabel => 'PIN';

  @override
  String get phoneLabel => 'Phone number';

  @override
  String get signIn => 'Sign in';

  @override
  String get logout => 'Logout';

  @override
  String get loading => 'Loading…';

  @override
  String get offline => 'Offline';

  @override
  String get online => 'Online';

  @override
  String get dashboardTitle => 'Dashboard';

  @override
  String get playersTitle => 'Players';

  @override
  String get ticketsTitle => 'Tickets';

  @override
  String get walletTitle => 'Wallet';

  @override
  String get payoutsTitle => 'Payouts';

  @override
  String get floatTitle => 'Float';

  @override
  String get supportTitle => 'Support';

  @override
  String get historyTitle => 'History';

  @override
  String get connectionLost => 'Connection lost';

  @override
  String get financialActionDisabled =>
      'Financial actions are disabled offline';

  @override
  String get missingApiBaseUrlTitle => 'Missing API URL';

  @override
  String missingApiBaseUrlBody(String label) {
    return 'Configure the backend base URL with $label.';
  }

  @override
  String get requiredField => 'This field is required.';

  @override
  String get signInFailed =>
      'Sign-in failed. Check your details and try again.';

  @override
  String get requestFailed =>
      'Request failed. Check your connection and try again.';

  @override
  String get availableFloat => 'Available float';

  @override
  String get clearingFloat => 'Clearing float';

  @override
  String get ticketsSold => 'Tickets sold';

  @override
  String get commissions => 'Commissions';

  @override
  String get pendingTopUps => 'Pending top-ups';

  @override
  String get floatHistoryTitle => 'Float history';

  @override
  String availableBalance(String amount, String currency) {
    return 'Available: $amount $currency';
  }

  @override
  String get requestFloatTopUp => 'Request float top-up';

  @override
  String get topUpAmount => 'Amount';

  @override
  String get topUpNote => 'Note';

  @override
  String get optional => 'Optional';

  @override
  String get confirmTopUpTitle => 'Confirm top-up request';

  @override
  String confirmTopUpBody(String amount, String currency) {
    return 'Request $amount $currency for your agent float?';
  }

  @override
  String get cancel => 'Cancel';

  @override
  String get confirm => 'Confirm';

  @override
  String get submitRequest => 'Submit request';

  @override
  String get positiveAmountRequired => 'Enter an amount greater than zero.';

  @override
  String get topUpRequestSubmitted =>
      'Top-up request submitted for admin review.';

  @override
  String get topUpRequestFailed =>
      'Could not submit the request. Retry to safely check the same request.';

  @override
  String get topUpRequests => 'Top-up requests';

  @override
  String get pending => 'Pending';

  @override
  String get approved => 'Approved';

  @override
  String get rejected => 'Rejected';

  @override
  String submittedAt(String date) {
    return 'Submitted $date';
  }

  @override
  String get financialHistoryTitle => 'Financial history';

  @override
  String get filterByType => 'Transaction type';

  @override
  String get filterByStatus => 'Status';

  @override
  String get filterByDate => 'Date range';

  @override
  String get allTypes => 'All types';

  @override
  String get allStatuses => 'All statuses';

  @override
  String get anyDate => 'Any date';

  @override
  String get clearFilters => 'Clear filters';

  @override
  String get noTransactions => 'No transactions match these filters.';

  @override
  String get transactionDetails => 'Transaction details';

  @override
  String get transactionTypeLabel => 'Transaction type';

  @override
  String get transactionAmount => 'Amount';

  @override
  String get transactionDate => 'Date and time';

  @override
  String get transactionStatus => 'Status';

  @override
  String get transactionReference => 'Reference';

  @override
  String get counterparty => 'Counterparty';

  @override
  String get relatedRecord => 'Related record';

  @override
  String get noCounterparty => 'Not available';

  @override
  String get close => 'Close';

  @override
  String get completed => 'Completed';

  @override
  String get accrued => 'Accrued';

  @override
  String get settled => 'Settled';

  @override
  String get openingBalance => 'Opening balance';

  @override
  String get floatTopUp => 'Float top-up';

  @override
  String get ticketSale => 'Ticket sale';

  @override
  String get playerWalletTopUp => 'Player wallet top-up';

  @override
  String get payoutDisbursement => 'Payout disbursement';

  @override
  String get payoutClearing => 'Payout clearing';

  @override
  String get commissionCredit => 'Commission payout';

  @override
  String get manualAdjustment => 'Manual adjustment';

  @override
  String get otherTransaction => 'Other transaction';

  @override
  String get commissionAccrual => 'Commission accrued';

  @override
  String get floatTopUpRequest => 'Float top-up request';

  @override
  String get playerLookupRegistration => 'Fast player lookup and registration';

  @override
  String get displayName => 'Display name';

  @override
  String get lookup => 'Lookup';

  @override
  String get register => 'Register';

  @override
  String get playerWalletTopUpTitle => 'Player wallet top-up';

  @override
  String get playerPhone => 'Player phone';

  @override
  String get topUp => 'Top up';

  @override
  String serverBalance(String amount) {
    return 'Server balance: $amount';
  }

  @override
  String get approvedAgentCashPayout => 'Approved agent cash payout';

  @override
  String get payoutCode => 'Payout code';

  @override
  String get verifyAndDisburse => 'Verify and disburse';

  @override
  String get ticketSaleTitle => 'Ticket sale';

  @override
  String get sessionId => 'Session ID';

  @override
  String get sellTicket => 'Sell ticket';

  @override
  String get publicTicketCode => 'Public ticket code';

  @override
  String get supportTickets => 'Support tickets';

  @override
  String get availableCommission => 'Commission amount';

  @override
  String commissionAmountValue(String amount) {
    return 'Commission: $amount';
  }

  @override
  String get enterAmount => 'Enter an amount';

  @override
  String get retry => 'Retry';

  @override
  String get noData => 'No records found.';
}
