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
/// import 'generated/app_localizations.dart';
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
  /// **'Bingo Agent'**
  String get appTitle;

  /// No description provided for @loginTitle.
  ///
  /// In en, this message translates to:
  /// **'Agent login'**
  String get loginTitle;

  /// No description provided for @passwordLabel.
  ///
  /// In en, this message translates to:
  /// **'Password'**
  String get passwordLabel;

  /// No description provided for @pinLabel.
  ///
  /// In en, this message translates to:
  /// **'PIN'**
  String get pinLabel;

  /// No description provided for @phoneLabel.
  ///
  /// In en, this message translates to:
  /// **'Phone number'**
  String get phoneLabel;

  /// No description provided for @signIn.
  ///
  /// In en, this message translates to:
  /// **'Sign in'**
  String get signIn;

  /// No description provided for @logout.
  ///
  /// In en, this message translates to:
  /// **'Logout'**
  String get logout;

  /// No description provided for @loading.
  ///
  /// In en, this message translates to:
  /// **'Loading…'**
  String get loading;

  /// No description provided for @offline.
  ///
  /// In en, this message translates to:
  /// **'Offline'**
  String get offline;

  /// No description provided for @online.
  ///
  /// In en, this message translates to:
  /// **'Online'**
  String get online;

  /// No description provided for @dashboardTitle.
  ///
  /// In en, this message translates to:
  /// **'Dashboard'**
  String get dashboardTitle;

  /// No description provided for @playersTitle.
  ///
  /// In en, this message translates to:
  /// **'Players'**
  String get playersTitle;

  /// No description provided for @ticketsTitle.
  ///
  /// In en, this message translates to:
  /// **'Tickets'**
  String get ticketsTitle;

  /// No description provided for @walletTitle.
  ///
  /// In en, this message translates to:
  /// **'Wallet'**
  String get walletTitle;

  /// No description provided for @payoutsTitle.
  ///
  /// In en, this message translates to:
  /// **'Payouts'**
  String get payoutsTitle;

  /// No description provided for @floatTitle.
  ///
  /// In en, this message translates to:
  /// **'Float'**
  String get floatTitle;

  /// No description provided for @supportTitle.
  ///
  /// In en, this message translates to:
  /// **'Support'**
  String get supportTitle;

  /// No description provided for @historyTitle.
  ///
  /// In en, this message translates to:
  /// **'History'**
  String get historyTitle;

  /// No description provided for @connectionLost.
  ///
  /// In en, this message translates to:
  /// **'Connection lost'**
  String get connectionLost;

  /// No description provided for @financialActionDisabled.
  ///
  /// In en, this message translates to:
  /// **'Financial actions are disabled offline'**
  String get financialActionDisabled;

  /// No description provided for @missingApiBaseUrlTitle.
  ///
  /// In en, this message translates to:
  /// **'Missing API URL'**
  String get missingApiBaseUrlTitle;

  /// No description provided for @missingApiBaseUrlBody.
  ///
  /// In en, this message translates to:
  /// **'Configure the backend base URL with {label}.'**
  String missingApiBaseUrlBody(String label);

  /// No description provided for @requiredField.
  ///
  /// In en, this message translates to:
  /// **'This field is required.'**
  String get requiredField;

  /// No description provided for @signInFailed.
  ///
  /// In en, this message translates to:
  /// **'Sign-in failed. Check your details and try again.'**
  String get signInFailed;

  /// No description provided for @requestFailed.
  ///
  /// In en, this message translates to:
  /// **'Request failed. Check your connection and try again.'**
  String get requestFailed;

  /// No description provided for @availableFloat.
  ///
  /// In en, this message translates to:
  /// **'Available float'**
  String get availableFloat;

  /// No description provided for @clearingFloat.
  ///
  /// In en, this message translates to:
  /// **'Clearing float'**
  String get clearingFloat;

  /// No description provided for @ticketsSold.
  ///
  /// In en, this message translates to:
  /// **'Tickets sold'**
  String get ticketsSold;

  /// No description provided for @commissions.
  ///
  /// In en, this message translates to:
  /// **'Commissions'**
  String get commissions;

  /// No description provided for @pendingTopUps.
  ///
  /// In en, this message translates to:
  /// **'Pending top-ups'**
  String get pendingTopUps;

  /// No description provided for @floatHistoryTitle.
  ///
  /// In en, this message translates to:
  /// **'Float history'**
  String get floatHistoryTitle;

  /// No description provided for @availableBalance.
  ///
  /// In en, this message translates to:
  /// **'Available: {amount} {currency}'**
  String availableBalance(String amount, String currency);

  /// No description provided for @requestFloatTopUp.
  ///
  /// In en, this message translates to:
  /// **'Request float top-up'**
  String get requestFloatTopUp;

  /// No description provided for @topUpAmount.
  ///
  /// In en, this message translates to:
  /// **'Amount'**
  String get topUpAmount;

  /// No description provided for @topUpNote.
  ///
  /// In en, this message translates to:
  /// **'Note'**
  String get topUpNote;

  /// No description provided for @optional.
  ///
  /// In en, this message translates to:
  /// **'Optional'**
  String get optional;

  /// No description provided for @confirmTopUpTitle.
  ///
  /// In en, this message translates to:
  /// **'Confirm top-up request'**
  String get confirmTopUpTitle;

  /// No description provided for @confirmTopUpBody.
  ///
  /// In en, this message translates to:
  /// **'Request {amount} {currency} for your agent float?'**
  String confirmTopUpBody(String amount, String currency);

  /// No description provided for @cancel.
  ///
  /// In en, this message translates to:
  /// **'Cancel'**
  String get cancel;

  /// No description provided for @confirm.
  ///
  /// In en, this message translates to:
  /// **'Confirm'**
  String get confirm;

  /// No description provided for @submitRequest.
  ///
  /// In en, this message translates to:
  /// **'Submit request'**
  String get submitRequest;

  /// No description provided for @positiveAmountRequired.
  ///
  /// In en, this message translates to:
  /// **'Enter an amount greater than zero.'**
  String get positiveAmountRequired;

  /// No description provided for @topUpRequestSubmitted.
  ///
  /// In en, this message translates to:
  /// **'Top-up request submitted for admin review.'**
  String get topUpRequestSubmitted;

  /// No description provided for @topUpRequestFailed.
  ///
  /// In en, this message translates to:
  /// **'Could not submit the request. Retry to safely check the same request.'**
  String get topUpRequestFailed;

  /// No description provided for @topUpRequests.
  ///
  /// In en, this message translates to:
  /// **'Top-up requests'**
  String get topUpRequests;

  /// No description provided for @pending.
  ///
  /// In en, this message translates to:
  /// **'Pending'**
  String get pending;

  /// No description provided for @approved.
  ///
  /// In en, this message translates to:
  /// **'Approved'**
  String get approved;

  /// No description provided for @rejected.
  ///
  /// In en, this message translates to:
  /// **'Rejected'**
  String get rejected;

  /// No description provided for @submittedAt.
  ///
  /// In en, this message translates to:
  /// **'Submitted {date}'**
  String submittedAt(String date);

  /// No description provided for @financialHistoryTitle.
  ///
  /// In en, this message translates to:
  /// **'Financial history'**
  String get financialHistoryTitle;

  /// No description provided for @filterByType.
  ///
  /// In en, this message translates to:
  /// **'Transaction type'**
  String get filterByType;

  /// No description provided for @filterByStatus.
  ///
  /// In en, this message translates to:
  /// **'Status'**
  String get filterByStatus;

  /// No description provided for @filterByDate.
  ///
  /// In en, this message translates to:
  /// **'Date range'**
  String get filterByDate;

  /// No description provided for @allTypes.
  ///
  /// In en, this message translates to:
  /// **'All types'**
  String get allTypes;

  /// No description provided for @allStatuses.
  ///
  /// In en, this message translates to:
  /// **'All statuses'**
  String get allStatuses;

  /// No description provided for @anyDate.
  ///
  /// In en, this message translates to:
  /// **'Any date'**
  String get anyDate;

  /// No description provided for @clearFilters.
  ///
  /// In en, this message translates to:
  /// **'Clear filters'**
  String get clearFilters;

  /// No description provided for @noTransactions.
  ///
  /// In en, this message translates to:
  /// **'No transactions match these filters.'**
  String get noTransactions;

  /// No description provided for @transactionDetails.
  ///
  /// In en, this message translates to:
  /// **'Transaction details'**
  String get transactionDetails;

  /// No description provided for @transactionTypeLabel.
  ///
  /// In en, this message translates to:
  /// **'Transaction type'**
  String get transactionTypeLabel;

  /// No description provided for @transactionAmount.
  ///
  /// In en, this message translates to:
  /// **'Amount'**
  String get transactionAmount;

  /// No description provided for @transactionDate.
  ///
  /// In en, this message translates to:
  /// **'Date and time'**
  String get transactionDate;

  /// No description provided for @transactionStatus.
  ///
  /// In en, this message translates to:
  /// **'Status'**
  String get transactionStatus;

  /// No description provided for @transactionReference.
  ///
  /// In en, this message translates to:
  /// **'Reference'**
  String get transactionReference;

  /// No description provided for @counterparty.
  ///
  /// In en, this message translates to:
  /// **'Counterparty'**
  String get counterparty;

  /// No description provided for @relatedRecord.
  ///
  /// In en, this message translates to:
  /// **'Related record'**
  String get relatedRecord;

  /// No description provided for @noCounterparty.
  ///
  /// In en, this message translates to:
  /// **'Not available'**
  String get noCounterparty;

  /// No description provided for @close.
  ///
  /// In en, this message translates to:
  /// **'Close'**
  String get close;

  /// No description provided for @completed.
  ///
  /// In en, this message translates to:
  /// **'Completed'**
  String get completed;

  /// No description provided for @accrued.
  ///
  /// In en, this message translates to:
  /// **'Accrued'**
  String get accrued;

  /// No description provided for @settled.
  ///
  /// In en, this message translates to:
  /// **'Settled'**
  String get settled;

  /// No description provided for @openingBalance.
  ///
  /// In en, this message translates to:
  /// **'Opening balance'**
  String get openingBalance;

  /// No description provided for @floatTopUp.
  ///
  /// In en, this message translates to:
  /// **'Float top-up'**
  String get floatTopUp;

  /// No description provided for @ticketSale.
  ///
  /// In en, this message translates to:
  /// **'Ticket sale'**
  String get ticketSale;

  /// No description provided for @playerWalletTopUp.
  ///
  /// In en, this message translates to:
  /// **'Player wallet top-up'**
  String get playerWalletTopUp;

  /// No description provided for @payoutDisbursement.
  ///
  /// In en, this message translates to:
  /// **'Payout disbursement'**
  String get payoutDisbursement;

  /// No description provided for @payoutClearing.
  ///
  /// In en, this message translates to:
  /// **'Payout clearing'**
  String get payoutClearing;

  /// No description provided for @commissionCredit.
  ///
  /// In en, this message translates to:
  /// **'Commission payout'**
  String get commissionCredit;

  /// No description provided for @manualAdjustment.
  ///
  /// In en, this message translates to:
  /// **'Manual adjustment'**
  String get manualAdjustment;

  /// No description provided for @otherTransaction.
  ///
  /// In en, this message translates to:
  /// **'Other transaction'**
  String get otherTransaction;

  /// No description provided for @commissionAccrual.
  ///
  /// In en, this message translates to:
  /// **'Commission accrued'**
  String get commissionAccrual;

  /// No description provided for @floatTopUpRequest.
  ///
  /// In en, this message translates to:
  /// **'Float top-up request'**
  String get floatTopUpRequest;

  /// No description provided for @playerLookupRegistration.
  ///
  /// In en, this message translates to:
  /// **'Fast player lookup and registration'**
  String get playerLookupRegistration;

  /// No description provided for @displayName.
  ///
  /// In en, this message translates to:
  /// **'Display name'**
  String get displayName;

  /// No description provided for @lookup.
  ///
  /// In en, this message translates to:
  /// **'Lookup'**
  String get lookup;

  /// No description provided for @register.
  ///
  /// In en, this message translates to:
  /// **'Register'**
  String get register;

  /// No description provided for @playerWalletTopUpTitle.
  ///
  /// In en, this message translates to:
  /// **'Player wallet top-up'**
  String get playerWalletTopUpTitle;

  /// No description provided for @playerPhone.
  ///
  /// In en, this message translates to:
  /// **'Player phone'**
  String get playerPhone;

  /// No description provided for @topUp.
  ///
  /// In en, this message translates to:
  /// **'Top up'**
  String get topUp;

  /// No description provided for @serverBalance.
  ///
  /// In en, this message translates to:
  /// **'Server balance: {amount}'**
  String serverBalance(String amount);

  /// No description provided for @approvedAgentCashPayout.
  ///
  /// In en, this message translates to:
  /// **'Approved agent cash payout'**
  String get approvedAgentCashPayout;

  /// No description provided for @payoutCode.
  ///
  /// In en, this message translates to:
  /// **'Payout code'**
  String get payoutCode;

  /// No description provided for @verifyAndDisburse.
  ///
  /// In en, this message translates to:
  /// **'Verify and disburse'**
  String get verifyAndDisburse;

  /// No description provided for @ticketSaleTitle.
  ///
  /// In en, this message translates to:
  /// **'Ticket sale'**
  String get ticketSaleTitle;

  /// No description provided for @sessionId.
  ///
  /// In en, this message translates to:
  /// **'Session ID'**
  String get sessionId;

  /// No description provided for @sellTicket.
  ///
  /// In en, this message translates to:
  /// **'Sell ticket'**
  String get sellTicket;

  /// No description provided for @publicTicketCode.
  ///
  /// In en, this message translates to:
  /// **'Public ticket code'**
  String get publicTicketCode;

  /// No description provided for @supportTickets.
  ///
  /// In en, this message translates to:
  /// **'Support tickets'**
  String get supportTickets;

  /// No description provided for @availableCommission.
  ///
  /// In en, this message translates to:
  /// **'Commission amount'**
  String get availableCommission;

  /// No description provided for @commissionAmountValue.
  ///
  /// In en, this message translates to:
  /// **'Commission: {amount}'**
  String commissionAmountValue(String amount);

  /// No description provided for @enterAmount.
  ///
  /// In en, this message translates to:
  /// **'Enter an amount'**
  String get enterAmount;

  /// No description provided for @retry.
  ///
  /// In en, this message translates to:
  /// **'Retry'**
  String get retry;

  /// No description provided for @noData.
  ///
  /// In en, this message translates to:
  /// **'No records found.'**
  String get noData;
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
