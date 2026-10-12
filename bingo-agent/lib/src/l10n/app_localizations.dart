import 'package:flutter/widgets.dart';

class AppLocalizations {
  AppLocalizations(this.locale);

  final Locale locale;

  static const supportedLocales = <Locale>[Locale('en'), Locale('am')];

  static const LocalizationsDelegate<AppLocalizations> delegate = _AppLocalizationsDelegate();

  static AppLocalizations of(BuildContext context) => Localizations.of<AppLocalizations>(context, AppLocalizations)!;

  bool get _am => locale.languageCode == 'am';

  String get appTitle => _am ? 'ቢንጎ ኤጀንት' : 'Bingo Agent';
  String get loginTitle => _am ? 'ወደ መለያ ግባ' : 'Agent login';
  String get passwordLabel => _am ? 'የይለፍ ቃል' : 'Password';
  String get pinLabel => _am ? 'PIN' : 'PIN';
  String get phoneLabel => _am ? 'ስልክ ቁጥር' : 'Phone number';
  String get signIn => _am ? 'ግባ' : 'Sign in';
  String get logout => _am ? 'ውጣ' : 'Logout';
  String get loading => _am ? 'በመጫን ላይ...' : 'Loading...';
  String get offline => _am ? 'ከመስመር ውጭ' : 'Offline';
  String get online => _am ? 'በመስመር ላይ' : 'Online';
  String get dashboardTitle => _am ? 'ዳሽቦርድ' : 'Dashboard';
  String get playersTitle => _am ? 'ተጫዋቾች' : 'Players';
  String get ticketsTitle => _am ? 'ቲኬቶች' : 'Tickets';
  String get walletTitle => _am ? 'የተጫዋች ቦርሳ' : 'Wallet';
  String get payoutsTitle => _am ? 'ክፍያዎች' : 'Payouts';
  String get floatTitle => _am ? 'ፍሎት' : 'Float';
  String get supportTitle => _am ? 'ድጋፍ' : 'Support';
  String get commissionsTitle => _am ? 'ኮሚሽኖች' : 'Commissions';
  String get connectionLost => _am ? 'መስመር ጠፍቷል' : 'Connection lost';
  String get financialActionDisabled => _am ? 'የገንዘብ እንቅስቃሴ ተከልክሏል' : 'Financial actions are disabled offline';
  String missingApiBaseUrlBody(String label) =>
      _am ? 'API መሠረት URL ይፈልጋል: $label' : 'Configure the backend base URL with $label.';
  String get missingApiBaseUrlTitle => _am ? 'API URL ይጎድላል' : 'Missing API URL';
}

class _AppLocalizationsDelegate extends LocalizationsDelegate<AppLocalizations> {
  const _AppLocalizationsDelegate();

  @override
  bool isSupported(Locale locale) => ['en', 'am'].contains(locale.languageCode);

  @override
  Future<AppLocalizations> load(Locale locale) async => AppLocalizations(locale);

  @override
  bool shouldReload(covariant LocalizationsDelegate<AppLocalizations> old) => false;
}