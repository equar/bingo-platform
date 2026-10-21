// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Amharic (`am`).
class AppLocalizationsAm extends AppLocalizations {
  AppLocalizationsAm([String locale = 'am']) : super(locale);

  @override
  String get appTitle => 'ቢንጎ ኤጀንት';

  @override
  String get loginTitle => 'ወደ ኤጀንት መለያ ይግቡ';

  @override
  String get passwordLabel => 'የይለፍ ቃል';

  @override
  String get pinLabel => 'ፒን';

  @override
  String get phoneLabel => 'ስልክ ቁጥር';

  @override
  String get signIn => 'ግባ';

  @override
  String get logout => 'ውጣ';

  @override
  String get loading => 'በመጫን ላይ…';

  @override
  String get offline => 'ከመስመር ውጭ';

  @override
  String get online => 'በመስመር ላይ';

  @override
  String get dashboardTitle => 'ዳሽቦርድ';

  @override
  String get playersTitle => 'ተጫዋቾች';

  @override
  String get ticketsTitle => 'ቲኬቶች';

  @override
  String get walletTitle => 'የተጫዋች ቦርሳ';

  @override
  String get payoutsTitle => 'ክፍያዎች';

  @override
  String get floatTitle => 'ፍሎት';

  @override
  String get supportTitle => 'ድጋፍ';

  @override
  String get historyTitle => 'ታሪክ';

  @override
  String get connectionLost => 'ግንኙነት ተቋርጧል';

  @override
  String get financialActionDisabled => 'ከመስመር ውጭ የገንዘብ እንቅስቃሴ አይፈቀድም';

  @override
  String get missingApiBaseUrlTitle => 'የAPI አድራሻ የለም';

  @override
  String missingApiBaseUrlBody(String label) {
    return 'የጀርባ አገልጋዩን መሠረታዊ አድራሻ በ$label ያዋቅሩ።';
  }

  @override
  String get requiredField => 'ይህ መስክ ያስፈልጋል።';

  @override
  String get signInFailed => 'መግባት አልተሳካም። መረጃዎን ያረጋግጡና እንደገና ይሞክሩ።';

  @override
  String get requestFailed => 'ጥያቄው አልተሳካም። ግንኙነትዎን ያረጋግጡና እንደገና ይሞክሩ።';

  @override
  String get availableFloat => 'ያለ ፍሎት';

  @override
  String get clearingFloat => 'የማጣሪያ ፍሎት';

  @override
  String get ticketsSold => 'የተሸጡ ቲኬቶች';

  @override
  String get commissions => 'ኮሚሽኖች';

  @override
  String get pendingTopUps => 'በመጠባበቅ ላይ ያሉ ጭማሪዎች';

  @override
  String get floatHistoryTitle => 'የፍሎት ታሪክ';

  @override
  String availableBalance(String amount, String currency) {
    return 'ያለ: $amount $currency';
  }

  @override
  String get requestFloatTopUp => 'የፍሎት ጭማሪ ይጠይቁ';

  @override
  String get topUpAmount => 'መጠን';

  @override
  String get topUpNote => 'ማስታወሻ';

  @override
  String get optional => 'አማራጭ';

  @override
  String get confirmTopUpTitle => 'የጭማሪ ጥያቄውን ያረጋግጡ';

  @override
  String confirmTopUpBody(String amount, String currency) {
    return 'ለኤጀንት ፍሎትዎ $amount $currency እንዲጨመር ይጠይቁ?';
  }

  @override
  String get cancel => 'ሰርዝ';

  @override
  String get confirm => 'አረጋግጥ';

  @override
  String get submitRequest => 'ጥያቄውን ላክ';

  @override
  String get positiveAmountRequired => 'ከዜሮ በላይ የሆነ መጠን ያስገቡ።';

  @override
  String get topUpRequestSubmitted => 'የጭማሪ ጥያቄው ለአስተዳዳሪ ግምገማ ተልኳል።';

  @override
  String get topUpRequestFailed =>
      'ጥያቄውን መላክ አልተቻለም። ተመሳሳይ ጥያቄን በደህና ለመፈተሽ እንደገና ይሞክሩ።';

  @override
  String get topUpRequests => 'የፍሎት ጭማሪ ጥያቄዎች';

  @override
  String get pending => 'በመጠባበቅ ላይ';

  @override
  String get approved => 'ጸድቋል';

  @override
  String get rejected => 'ውድቅ ተደርጓል';

  @override
  String submittedAt(String date) {
    return 'የተላከበት $date';
  }

  @override
  String get financialHistoryTitle => 'የገንዘብ እንቅስቃሴ ታሪክ';

  @override
  String get filterByType => 'የእንቅስቃሴ ዓይነት';

  @override
  String get filterByStatus => 'ሁኔታ';

  @override
  String get filterByDate => 'የቀን ክልል';

  @override
  String get allTypes => 'ሁሉም ዓይነቶች';

  @override
  String get allStatuses => 'ሁሉም ሁኔታዎች';

  @override
  String get anyDate => 'ማንኛውም ቀን';

  @override
  String get clearFilters => 'ማጣሪያዎችን አጽዳ';

  @override
  String get noTransactions => 'ከእነዚህ ማጣሪያዎች ጋር የሚዛመድ እንቅስቃሴ የለም።';

  @override
  String get transactionDetails => 'የእንቅስቃሴ ዝርዝር';

  @override
  String get transactionTypeLabel => 'የእንቅስቃሴ ዓይነት';

  @override
  String get transactionAmount => 'መጠን';

  @override
  String get transactionDate => 'ቀን እና ሰዓት';

  @override
  String get transactionStatus => 'ሁኔታ';

  @override
  String get transactionReference => 'ማጣቀሻ';

  @override
  String get counterparty => 'ተዛማጅ ወገን';

  @override
  String get relatedRecord => 'ተዛማጅ መዝገብ';

  @override
  String get noCounterparty => 'መረጃ የለም';

  @override
  String get close => 'ዝጋ';

  @override
  String get completed => 'ተጠናቋል';

  @override
  String get accrued => 'ተቀምጧል';

  @override
  String get settled => 'ተከፍሏል';

  @override
  String get openingBalance => 'የመነሻ ቀሪ ሂሳብ';

  @override
  String get floatTopUp => 'የፍሎት ጭማሪ';

  @override
  String get ticketSale => 'የቲኬት ሽያጭ';

  @override
  String get playerWalletTopUp => 'ወደ ተጫዋች ቦርሳ ጭማሪ';

  @override
  String get payoutDisbursement => 'የሽልማት ክፍያ';

  @override
  String get payoutClearing => 'የሽልማት ማጣሪያ';

  @override
  String get commissionCredit => 'የኮሚሽን ክፍያ';

  @override
  String get manualAdjustment => 'በእጅ ማስተካከያ';

  @override
  String get otherTransaction => 'ሌላ እንቅስቃሴ';

  @override
  String get commissionAccrual => 'የተቀመጠ ኮሚሽን';

  @override
  String get floatTopUpRequest => 'የፍሎት ጭማሪ ጥያቄ';

  @override
  String get playerLookupRegistration => 'ተጫዋችን በፍጥነት ፈልግ ወይም መዝግብ';

  @override
  String get displayName => 'የሚታይ ስም';

  @override
  String get lookup => 'ፈልግ';

  @override
  String get register => 'መዝግብ';

  @override
  String get playerWalletTopUpTitle => 'ወደ ተጫዋች ቦርሳ ጭማሪ';

  @override
  String get playerPhone => 'የተጫዋች ስልክ';

  @override
  String get topUp => 'ጨምር';

  @override
  String serverBalance(String amount) {
    return 'የአገልጋይ ቀሪ ሂሳብ: $amount';
  }

  @override
  String get approvedAgentCashPayout => 'የጸደቀ የኤጀንት ገንዘብ ክፍያ';

  @override
  String get payoutCode => 'የክፍያ ኮድ';

  @override
  String get verifyAndDisburse => 'አረጋግጥና ክፈል';

  @override
  String get ticketSaleTitle => 'የቲኬት ሽያጭ';

  @override
  String get sessionId => 'የዙር መለያ';

  @override
  String get sellTicket => 'ቲኬት ሽጥ';

  @override
  String get publicTicketCode => 'የሕዝብ ቲኬት ኮድ';

  @override
  String get supportTickets => 'የድጋፍ ጥያቄዎች';

  @override
  String get availableCommission => 'የኮሚሽን መጠን';

  @override
  String commissionAmountValue(String amount) {
    return 'ኮሚሽን: $amount';
  }

  @override
  String get enterAmount => 'መጠን ያስገቡ';

  @override
  String get retry => 'እንደገና ሞክር';

  @override
  String get noData => 'መዝገቦች አልተገኙም።';
}
