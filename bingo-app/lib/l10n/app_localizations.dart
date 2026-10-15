import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:intl/intl.dart' as intl;

import 'app_localizations_am.dart';
import 'app_localizations_en.dart';

// ignore_for_file: type=lint

/// Callers can lookup localized strings with an instance of AppLocalizations
/// returned by `AppLocalizations.of(context)`.
///
/// Applications need to include `AppLocalizations.delegate()` in their app's
/// `localizationDelegates` list, and the locales they support in the app's
/// `supportedLocales` list. For example:
///
/// ```dart
/// import 'l10n/app_localizations.dart';
///
/// return MaterialApp(
///   localizationsDelegates: AppLocalizations.localizationsDelegates,
///   supportedLocales: AppLocalizations.supportedLocales,
///   home: MyApplicationHome(),
/// );
/// ```
///
/// ## Update pubspec.yaml
///
/// Please make sure to update your pubspec.yaml to include the following
/// packages:
///
/// ```yaml
/// dependencies:
///   # Internationalization support.
///   flutter_localizations:
///     sdk: flutter
///   intl: any # Use the pinned version from flutter_localizations
///
///   # Rest of dependencies
/// ```
///
/// ## iOS Applications
///
/// iOS applications define key application metadata, including supported
/// locales, in an Info.plist file that is built into the application bundle.
/// To configure the locales supported by your app, you’ll need to edit this
/// file.
///
/// First, open your project’s ios/Runner.xcworkspace Xcode workspace file.
/// Then, in the Project Navigator, open the Info.plist file under the Runner
/// project’s Runner folder.
///
/// Next, select the Information Property List item, select Add Item from the
/// Editor menu, then select Localizations from the pop-up menu.
///
/// Select and expand the newly-created Localizations item then, for each
/// locale your application supports, add a new item and select the locale
/// you wish to add from the pop-up menu in the Value field. This list should
/// be consistent with the languages listed in the AppLocalizations.supportedLocales
/// property.
abstract class AppLocalizations {
  AppLocalizations(String locale)
      : localeName = intl.Intl.canonicalizedLocale(locale.toString());

  final String localeName;

  static AppLocalizations? of(BuildContext context) {
    return Localizations.of<AppLocalizations>(context, AppLocalizations);
  }

  static const LocalizationsDelegate<AppLocalizations> delegate =
      _AppLocalizationsDelegate();

  /// A list of this localizations delegate along with the default localizations
  /// delegates.
  ///
  /// Returns a list of localizations delegates containing this delegate along with
  /// GlobalMaterialLocalizations.delegate, GlobalCupertinoLocalizations.delegate,
  /// and GlobalWidgetsLocalizations.delegate.
  ///
  /// Additional delegates can be added by appending to this list in
  /// MaterialApp. This list does not have to be used at all if a custom list
  /// of delegates is preferred or required.
  static const List<LocalizationsDelegate<dynamic>> localizationsDelegates =
      <LocalizationsDelegate<dynamic>>[
    delegate,
    GlobalMaterialLocalizations.delegate,
    GlobalCupertinoLocalizations.delegate,
    GlobalWidgetsLocalizations.delegate,
  ];

  /// A list of this localizations delegate's supported locales.
  static const List<Locale> supportedLocales = <Locale>[
    Locale('am'),
    Locale('en')
  ];

  /// No description provided for @appTitle.
  ///
  /// In en, this message translates to:
  /// **'Bingo'**
  String get appTitle;

  /// No description provided for @missingApiBaseUrlTitle.
  ///
  /// In en, this message translates to:
  /// **'API base URL is missing'**
  String get missingApiBaseUrlTitle;

  /// No description provided for @missingApiBaseUrlBody.
  ///
  /// In en, this message translates to:
  /// **'Start the app with --dart-define=API_BASE_URL=... so the client can reach the backend.'**
  String get missingApiBaseUrlBody;

  /// No description provided for @loading.
  ///
  /// In en, this message translates to:
  /// **'Loading...'**
  String get loading;

  /// No description provided for @retry.
  ///
  /// In en, this message translates to:
  /// **'Retry'**
  String get retry;

  /// No description provided for @errorGeneric.
  ///
  /// In en, this message translates to:
  /// **'Something went wrong.'**
  String get errorGeneric;

  /// No description provided for @emptyState.
  ///
  /// In en, this message translates to:
  /// **'No items found.'**
  String get emptyState;

  /// No description provided for @loginTitle.
  ///
  /// In en, this message translates to:
  /// **'Sign in'**
  String get loginTitle;

  /// No description provided for @registerTitle.
  ///
  /// In en, this message translates to:
  /// **'Create account'**
  String get registerTitle;

  /// No description provided for @loginSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Use your phone number and password.'**
  String get loginSubtitle;

  /// No description provided for @registerSubtitle.
  ///
  /// In en, this message translates to:
  /// **'Create a player account with a display name.'**
  String get registerSubtitle;

  /// No description provided for @phoneLabel.
  ///
  /// In en, this message translates to:
  /// **'Phone number'**
  String get phoneLabel;

  /// No description provided for @passwordLabel.
  ///
  /// In en, this message translates to:
  /// **'PIN'**
  String get passwordLabel;

  /// No description provided for @displayNameLabel.
  ///
  /// In en, this message translates to:
  /// **'Display name'**
  String get displayNameLabel;

  /// No description provided for @phoneRequired.
  ///
  /// In en, this message translates to:
  /// **'Enter your phone number.'**
  String get phoneRequired;

  /// No description provided for @passwordRequired.
  ///
  /// In en, this message translates to:
  /// **'Enter your 6-digit PIN.'**
  String get passwordRequired;

  /// No description provided for @requiredField.
  ///
  /// In en, this message translates to:
  /// **'This field is required.'**
  String get requiredField;

  /// No description provided for @passwordTooShort.
  ///
  /// In en, this message translates to:
  /// **'PIN must be exactly 6 digits.'**
  String get passwordTooShort;

  /// No description provided for @displayNameRequired.
  ///
  /// In en, this message translates to:
  /// **'Enter a display name.'**
  String get displayNameRequired;

  /// No description provided for @invalidPhone.
  ///
  /// In en, this message translates to:
  /// **'Enter a valid phone number.'**
  String get invalidPhone;

  /// No description provided for @signIn.
  ///
  /// In en, this message translates to:
  /// **'Sign in'**
  String get signIn;

  /// No description provided for @createAccount.
  ///
  /// In en, this message translates to:
  /// **'Create account'**
  String get createAccount;

  /// No description provided for @switchToRegister.
  ///
  /// In en, this message translates to:
  /// **'Need an account? Register'**
  String get switchToRegister;

  /// No description provided for @switchToLogin.
  ///
  /// In en, this message translates to:
  /// **'Already have an account? Sign in'**
  String get switchToLogin;

  /// No description provided for @homeLobby.
  ///
  /// In en, this message translates to:
  /// **'Lobby'**
  String get homeLobby;

  /// No description provided for @homeWallet.
  ///
  /// In en, this message translates to:
  /// **'Wallet'**
  String get homeWallet;

  /// No description provided for @homeProfile.
  ///
  /// In en, this message translates to:
  /// **'Profile'**
  String get homeProfile;

  /// No description provided for @roomsTitle.
  ///
  /// In en, this message translates to:
  /// **'Rooms'**
  String get roomsTitle;

  /// No description provided for @liveGameTitle.
  ///
  /// In en, this message translates to:
  /// **'Live game'**
  String get liveGameTitle;

  /// No description provided for @sessionStatusLabel.
  ///
  /// In en, this message translates to:
  /// **'Session status'**
  String get sessionStatusLabel;

  /// No description provided for @ticketCountLabel.
  ///
  /// In en, this message translates to:
  /// **'Tickets'**
  String get ticketCountLabel;

  /// No description provided for @drawCountLabel.
  ///
  /// In en, this message translates to:
  /// **'Draws'**
  String get drawCountLabel;

  /// No description provided for @calledNumbersTitle.
  ///
  /// In en, this message translates to:
  /// **'Called numbers'**
  String get calledNumbersTitle;

  /// No description provided for @myTicketsTitle.
  ///
  /// In en, this message translates to:
  /// **'My tickets'**
  String get myTicketsTitle;

  /// No description provided for @claimHistoryTitle.
  ///
  /// In en, this message translates to:
  /// **'Claim history'**
  String get claimHistoryTitle;

  /// No description provided for @claimPatternLabel.
  ///
  /// In en, this message translates to:
  /// **'Claim pattern'**
  String get claimPatternLabel;

  /// No description provided for @claimSettlementLabel.
  ///
  /// In en, this message translates to:
  /// **'Prize settlement'**
  String get claimSettlementLabel;

  /// No description provided for @claimSettlementUnavailableBody.
  ///
  /// In en, this message translates to:
  /// **'Prize settlement status is handled on the server. The player contract only exposes claim outcomes and entitlement IDs.'**
  String get claimSettlementUnavailableBody;

  /// No description provided for @claimSubmitted.
  ///
  /// In en, this message translates to:
  /// **'Claim submitted.'**
  String get claimSubmitted;

  /// No description provided for @submitClaim.
  ///
  /// In en, this message translates to:
  /// **'Submit claim'**
  String get submitClaim;

  /// No description provided for @previous.
  ///
  /// In en, this message translates to:
  /// **'Previous'**
  String get previous;

  /// No description provided for @next.
  ///
  /// In en, this message translates to:
  /// **'Next'**
  String get next;

  /// No description provided for @ticketPageLabel.
  ///
  /// In en, this message translates to:
  /// **'Ticket page'**
  String get ticketPageLabel;

  /// No description provided for @liveConnectionConnecting.
  ///
  /// In en, this message translates to:
  /// **'Connecting...'**
  String get liveConnectionConnecting;

  /// No description provided for @liveConnectionConnected.
  ///
  /// In en, this message translates to:
  /// **'Live'**
  String get liveConnectionConnected;

  /// No description provided for @liveConnectionReconnecting.
  ///
  /// In en, this message translates to:
  /// **'Reconnecting...'**
  String get liveConnectionReconnecting;

  /// No description provided for @liveConnectionPaused.
  ///
  /// In en, this message translates to:
  /// **'Paused'**
  String get liveConnectionPaused;

  /// No description provided for @liveConnectionDisconnected.
  ///
  /// In en, this message translates to:
  /// **'Disconnected'**
  String get liveConnectionDisconnected;

  /// No description provided for @liveConnectionExpired.
  ///
  /// In en, this message translates to:
  /// **'Session expired'**
  String get liveConnectionExpired;

  /// No description provided for @liveConnectionFinished.
  ///
  /// In en, this message translates to:
  /// **'Finished'**
  String get liveConnectionFinished;

  /// No description provided for @freeCenterLabel.
  ///
  /// In en, this message translates to:
  /// **'FREE'**
  String get freeCenterLabel;

  /// No description provided for @claimPatternPending.
  ///
  /// In en, this message translates to:
  /// **'Pending review'**
  String get claimPatternPending;

  /// No description provided for @claimPatternApproved.
  ///
  /// In en, this message translates to:
  /// **'Approved'**
  String get claimPatternApproved;

  /// No description provided for @claimPatternRejected.
  ///
  /// In en, this message translates to:
  /// **'Rejected'**
  String get claimPatternRejected;

  /// No description provided for @claimPatternFailed.
  ///
  /// In en, this message translates to:
  /// **'Server validation failed'**
  String get claimPatternFailed;

  /// No description provided for @sessionTimesTitle.
  ///
  /// In en, this message translates to:
  /// **'Session times'**
  String get sessionTimesTitle;

  /// No description provided for @sessionGapTitle.
  ///
  /// In en, this message translates to:
  /// **'Session list unavailable'**
  String get sessionGapTitle;

  /// No description provided for @sessionGapBody.
  ///
  /// In en, this message translates to:
  /// **'The backend contract does not expose a public list of upcoming sessions yet. Enter a session ID to open a session detail.'**
  String get sessionGapBody;

  /// No description provided for @roomPriceLabel.
  ///
  /// In en, this message translates to:
  /// **'Price'**
  String get roomPriceLabel;

  /// No description provided for @roomStatusActive.
  ///
  /// In en, this message translates to:
  /// **'Active'**
  String get roomStatusActive;

  /// No description provided for @roomStatusInactive.
  ///
  /// In en, this message translates to:
  /// **'Inactive'**
  String get roomStatusInactive;

  /// No description provided for @roomCapacityLabel.
  ///
  /// In en, this message translates to:
  /// **'Capacity'**
  String get roomCapacityLabel;

  /// No description provided for @availableSeatsLabel.
  ///
  /// In en, this message translates to:
  /// **'Available seats'**
  String get availableSeatsLabel;

  /// No description provided for @roomScheduleLabel.
  ///
  /// In en, this message translates to:
  /// **'Schedule'**
  String get roomScheduleLabel;

  /// No description provided for @roomPlayersLabel.
  ///
  /// In en, this message translates to:
  /// **'Server player count'**
  String get roomPlayersLabel;

  /// No description provided for @playerCountUnavailable.
  ///
  /// In en, this message translates to:
  /// **'Unavailable from the current contract'**
  String get playerCountUnavailable;

  /// No description provided for @enterSessionIdLabel.
  ///
  /// In en, this message translates to:
  /// **'Session ID'**
  String get enterSessionIdLabel;

  /// No description provided for @loadSession.
  ///
  /// In en, this message translates to:
  /// **'Load session'**
  String get loadSession;

  /// No description provided for @sessionIdRequired.
  ///
  /// In en, this message translates to:
  /// **'Enter a session ID.'**
  String get sessionIdRequired;

  /// No description provided for @purchaseTicket.
  ///
  /// In en, this message translates to:
  /// **'Buy ticket'**
  String get purchaseTicket;

  /// No description provided for @purchaseTicketTitle.
  ///
  /// In en, this message translates to:
  /// **'Confirm ticket purchase'**
  String get purchaseTicketTitle;

  /// No description provided for @purchaseTicketBody.
  ///
  /// In en, this message translates to:
  /// **'This purchase uses an idempotency key so retries do not create duplicate tickets.'**
  String get purchaseTicketBody;

  /// No description provided for @confirm.
  ///
  /// In en, this message translates to:
  /// **'Confirm'**
  String get confirm;

  /// No description provided for @cancel.
  ///
  /// In en, this message translates to:
  /// **'Cancel'**
  String get cancel;

  /// No description provided for @purchaseSuccess.
  ///
  /// In en, this message translates to:
  /// **'Ticket purchase completed.'**
  String get purchaseSuccess;

  /// No description provided for @balanceTitle.
  ///
  /// In en, this message translates to:
  /// **'Balance'**
  String get balanceTitle;

  /// No description provided for @balanceDisclaimer.
  ///
  /// In en, this message translates to:
  /// **'Pending deposits are not spendable until approved.'**
  String get balanceDisclaimer;

  /// No description provided for @currentBalance.
  ///
  /// In en, this message translates to:
  /// **'Current balance'**
  String get currentBalance;

  /// No description provided for @transactionsTitle.
  ///
  /// In en, this message translates to:
  /// **'Transactions'**
  String get transactionsTitle;

  /// No description provided for @depositsTitle.
  ///
  /// In en, this message translates to:
  /// **'Deposits'**
  String get depositsTitle;

  /// No description provided for @transactionHistoryEmpty.
  ///
  /// In en, this message translates to:
  /// **'No wallet transactions yet.'**
  String get transactionHistoryEmpty;

  /// No description provided for @depositHistoryEmpty.
  ///
  /// In en, this message translates to:
  /// **'No deposits yet.'**
  String get depositHistoryEmpty;

  /// No description provided for @amountLabel.
  ///
  /// In en, this message translates to:
  /// **'Amount'**
  String get amountLabel;

  /// No description provided for @telebirrReferenceLabel.
  ///
  /// In en, this message translates to:
  /// **'Telebirr reference'**
  String get telebirrReferenceLabel;

  /// No description provided for @currencyLabel.
  ///
  /// In en, this message translates to:
  /// **'Currency'**
  String get currencyLabel;

  /// No description provided for @submitDeposit.
  ///
  /// In en, this message translates to:
  /// **'Submit deposit'**
  String get submitDeposit;

  /// No description provided for @telebirrInstructionsTitle.
  ///
  /// In en, this message translates to:
  /// **'Telebirr instructions'**
  String get telebirrInstructionsTitle;

  /// No description provided for @telebirrInstructionsBody.
  ///
  /// In en, this message translates to:
  /// **'Use the provider details from the backend response and submit the reference exactly as shown on the payment receipt.'**
  String get telebirrInstructionsBody;

  /// No description provided for @profileTitle.
  ///
  /// In en, this message translates to:
  /// **'Profile'**
  String get profileTitle;

  /// No description provided for @logout.
  ///
  /// In en, this message translates to:
  /// **'Logout'**
  String get logout;

  /// No description provided for @profileMemberSince.
  ///
  /// In en, this message translates to:
  /// **'Member since'**
  String get profileMemberSince;

  /// No description provided for @profileRoles.
  ///
  /// In en, this message translates to:
  /// **'Roles'**
  String get profileRoles;

  /// No description provided for @statusPending.
  ///
  /// In en, this message translates to:
  /// **'Pending'**
  String get statusPending;

  /// No description provided for @statusApproved.
  ///
  /// In en, this message translates to:
  /// **'Approved'**
  String get statusApproved;

  /// No description provided for @statusRejected.
  ///
  /// In en, this message translates to:
  /// **'Rejected'**
  String get statusRejected;

  /// No description provided for @statusOpen.
  ///
  /// In en, this message translates to:
  /// **'Open'**
  String get statusOpen;

  /// No description provided for @statusInProgress.
  ///
  /// In en, this message translates to:
  /// **'In progress'**
  String get statusInProgress;

  /// No description provided for @statusWaiting.
  ///
  /// In en, this message translates to:
  /// **'Waiting for customer'**
  String get statusWaiting;

  /// No description provided for @statusResolved.
  ///
  /// In en, this message translates to:
  /// **'Resolved'**
  String get statusResolved;

  /// No description provided for @statusClosed.
  ///
  /// In en, this message translates to:
  /// **'Closed'**
  String get statusClosed;

  /// No description provided for @sessionExpired.
  ///
  /// In en, this message translates to:
  /// **'Your session expired. Please sign in again.'**
  String get sessionExpired;

  /// No description provided for @contractGapTitle.
  ///
  /// In en, this message translates to:
  /// **'Backend contract gap'**
  String get contractGapTitle;

  /// No description provided for @contractGapBody.
  ///
  /// In en, this message translates to:
  /// **'The current API does not expose everything the UI can show yet. The app will surface only real backend data and clearly label missing coverage.'**
  String get contractGapBody;

  /// No description provided for @refresh.
  ///
  /// In en, this message translates to:
  /// **'Refresh'**
  String get refresh;
}

class _AppLocalizationsDelegate
    extends LocalizationsDelegate<AppLocalizations> {
  const _AppLocalizationsDelegate();

  @override
  Future<AppLocalizations> load(Locale locale) {
    return SynchronousFuture<AppLocalizations>(lookupAppLocalizations(locale));
  }

  @override
  bool isSupported(Locale locale) =>
      <String>['am', 'en'].contains(locale.languageCode);

  @override
  bool shouldReload(_AppLocalizationsDelegate old) => false;
}

AppLocalizations lookupAppLocalizations(Locale locale) {
  // Lookup logic when only language code is specified.
  switch (locale.languageCode) {
    case 'am':
      return AppLocalizationsAm();
    case 'en':
      return AppLocalizationsEn();
  }

  throw FlutterError(
      'AppLocalizations.delegate failed to load unsupported locale "$locale". This is likely '
      'an issue with the localizations generation tool. Please file an issue '
      'on GitHub with a reproducible sample app and the gen-l10n configuration '
      'that was used.');
}
