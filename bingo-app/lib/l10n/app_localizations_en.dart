// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for English (`en`).
class AppLocalizationsEn extends AppLocalizations {
  AppLocalizationsEn([String locale = 'en']) : super(locale);

  @override
  String get appTitle => 'Sunset Bingo';

  @override
  String get missingApiBaseUrlTitle => 'API base URL is missing';

  @override
  String get missingApiBaseUrlBody =>
      'Start the app with --dart-define=API_BASE_URL=... so the client can reach the backend.';

  @override
  String get loading => 'Loading...';

  @override
  String get retry => 'Retry';

  @override
  String get errorGeneric => 'Something went wrong.';

  @override
  String get emptyState => 'No items found.';

  @override
  String get loginTitle => 'Sign in';

  @override
  String get registerTitle => 'Create account';

  @override
  String get loginSubtitle => 'Use your phone number and password.';

  @override
  String get registerSubtitle => 'Create a player account with a display name.';

  @override
  String get phoneLabel => 'Phone number';

  @override
  String get passwordLabel => 'PIN';

  @override
  String get displayNameLabel => 'Display name';

  @override
  String get phoneRequired => 'Enter your phone number.';

  @override
  String get passwordRequired => 'Enter your 6-digit PIN.';

  @override
  String get requiredField => 'This field is required.';

  @override
  String get passwordTooShort => 'PIN must be exactly 6 digits.';

  @override
  String get displayNameRequired => 'Enter a display name.';

  @override
  String get invalidPhone => 'Enter a valid phone number.';

  @override
  String get signIn => 'Sign in';

  @override
  String get createAccount => 'Create account';

  @override
  String get switchToRegister => 'Need an account? Register';

  @override
  String get switchToLogin => 'Already have an account? Sign in';

  @override
  String get homeLobby => 'Lobby';

  @override
  String get homeWallet => 'Wallet';

  @override
  String get homeProfile => 'Profile';

  @override
  String get roomsTitle => 'Rooms';

  @override
  String get liveGameTitle => 'Live game';

  @override
  String get sessionStatusLabel => 'Session status';

  @override
  String get ticketCountLabel => 'Tickets';

  @override
  String get drawCountLabel => 'Draws';

  @override
  String get calledNumbersTitle => 'Called numbers';

  @override
  String get myTicketsTitle => 'My tickets';

  @override
  String get claimHistoryTitle => 'Claim history';

  @override
  String get claimPatternLabel => 'Claim pattern';

  @override
  String get claimSettlementLabel => 'Prize settlement';

  @override
  String get claimSettlementUnavailableBody =>
      'Prize settlement status is handled on the server. The player contract only exposes claim outcomes and entitlement IDs.';

  @override
  String get claimSubmitted => 'Claim submitted.';

  @override
  String get submitClaim => 'Submit claim';

  @override
  String get previous => 'Previous';

  @override
  String get next => 'Next';

  @override
  String get ticketPageLabel => 'Ticket page';

  @override
  String get liveConnectionConnecting => 'Connecting...';

  @override
  String get liveConnectionConnected => 'Live';

  @override
  String get liveConnectionReconnecting => 'Reconnecting...';

  @override
  String get liveConnectionPaused => 'Paused';

  @override
  String get liveConnectionDisconnected => 'Disconnected';

  @override
  String get liveConnectionExpired => 'Session expired';

  @override
  String get liveConnectionFinished => 'Finished';

  @override
  String get freeCenterLabel => 'FREE';

  @override
  String get claimPatternPending => 'Pending review';

  @override
  String get claimPatternApproved => 'Approved';

  @override
  String get claimPatternRejected => 'Rejected';

  @override
  String get claimPatternFailed => 'Server validation failed';

  @override
  String get sessionTimesTitle => 'Session times';

  @override
  String get sessionGapTitle => 'Session list unavailable';

  @override
  String get sessionGapBody =>
      'The backend contract does not expose a public list of upcoming sessions yet. Enter a session ID to open a session detail.';

  @override
  String get roomPriceLabel => 'Price';

  @override
  String get roomStatusActive => 'Active';

  @override
  String get roomStatusInactive => 'Inactive';

  @override
  String get roomCapacityLabel => 'Capacity';

  @override
  String get availableSeatsLabel => 'Available seats';

  @override
  String get roomScheduleLabel => 'Schedule';

  @override
  String get roomPlayersLabel => 'Server player count';

  @override
  String get playerCountUnavailable => 'Unavailable from the current contract';

  @override
  String get enterSessionIdLabel => 'Session ID';

  @override
  String get loadSession => 'Load session';

  @override
  String get sessionIdRequired => 'Enter a session ID.';

  @override
  String get purchaseTicket => 'Buy ticket';

  @override
  String get purchaseTicketTitle => 'Confirm ticket purchase';

  @override
  String get purchaseTicketBody =>
      'This purchase uses an idempotency key so retries do not create duplicate tickets.';

  @override
  String get confirm => 'Confirm';

  @override
  String get cancel => 'Cancel';

  @override
  String get purchaseSuccess => 'Ticket purchase completed.';

  @override
  String get balanceTitle => 'Balance';

  @override
  String get balanceDisclaimer =>
      'Pending deposits are not spendable until approved.';

  @override
  String get currentBalance => 'Current balance';

  @override
  String get transactionsTitle => 'Transactions';

  @override
  String get depositsTitle => 'Deposits';

  @override
  String get transactionHistoryEmpty => 'No wallet transactions yet.';

  @override
  String get depositHistoryEmpty => 'No deposits yet.';

  @override
  String get amountLabel => 'Amount';

  @override
  String get telebirrReferenceLabel => 'Telebirr reference';

  @override
  String get currencyLabel => 'Currency';

  @override
  String get submitDeposit => 'Submit deposit';

  @override
  String get telebirrInstructionsTitle => 'Telebirr instructions';

  @override
  String get telebirrInstructionsBody =>
      'Use the provider details from the backend response and submit the reference exactly as shown on the payment receipt.';

  @override
  String get profileTitle => 'Profile';

  @override
  String get logout => 'Logout';

  @override
  String get profileMemberSince => 'Member since';

  @override
  String get profileRoles => 'Roles';

  @override
  String get statusPending => 'Pending';

  @override
  String get statusApproved => 'Approved';

  @override
  String get statusRejected => 'Rejected';

  @override
  String get statusOpen => 'Open';

  @override
  String get statusInProgress => 'In progress';

  @override
  String get statusWaiting => 'Waiting for customer';

  @override
  String get statusResolved => 'Resolved';

  @override
  String get statusClosed => 'Closed';

  @override
  String get sessionExpired => 'Your session expired. Please sign in again.';

  @override
  String get contractGapTitle => 'Backend contract gap';

  @override
  String get contractGapBody =>
      'The current API does not expose everything the UI can show yet. The app will surface only real backend data and clearly label missing coverage.';

  @override
  String get refresh => 'Refresh';
}
