// ignore: unused_import
import 'package:intl/intl.dart' as intl;
import 'app_localizations.dart';

// ignore_for_file: type=lint

/// The translations for Amharic (`am`).
class AppLocalizationsAm extends AppLocalizations {
  AppLocalizationsAm([String locale = 'am']) : super(locale);

  @override
  String get appTitle => 'ቢንጎ';

  @override
  String get missingApiBaseUrlTitle => 'የAPI መሠረት URL አልተገለጸም';

  @override
  String get missingApiBaseUrlBody =>
      'አፕሊኬሽኑን --dart-define=API_BASE_URL=... በማስነሳት ወደ backend ይደርሱ።';

  @override
  String get loading => 'በመጫን ላይ...';

  @override
  String get retry => 'ድገም';

  @override
  String get errorGeneric => 'ችግኝ ተፈጥሯል።';

  @override
  String get emptyState => 'ምንም ንጥል አልተገኘም።';

  @override
  String get loginTitle => 'ግባ';

  @override
  String get registerTitle => 'መለያ ፍጠር';

  @override
  String get loginSubtitle => 'ስልክ ቁጥርዎን እና የይለፍ ቃልዎን ይጠቀሙ።';

  @override
  String get registerSubtitle => 'በማሳያ ስም የተጫዋች መለያ ይፍጠሩ።';

  @override
  String get phoneLabel => 'ስልክ ቁጥር';

  @override
  String get passwordLabel => 'ፒን';

  @override
  String get displayNameLabel => 'የሚታይ ስም';

  @override
  String get phoneRequired => 'ስልክ ቁጥር ያስገቡ።';

  @override
  String get passwordRequired => 'ባለ 6 አሃዝ ፒን ያስገቡ።';

  @override
  String get requiredField => 'ይህ መስክ አስፈላጊ ነው።';

  @override
  String get passwordTooShort => 'ፒኑ ትክክል 6 አሃዝ መሆን አለበት።';

  @override
  String get displayNameRequired => 'የሚታይ ስም ያስገቡ።';

  @override
  String get invalidPhone => 'ትክክለኛ ስልክ ቁጥር ያስገቡ።';

  @override
  String get signIn => 'ግባ';

  @override
  String get createAccount => 'መለያ ፍጠር';

  @override
  String get switchToRegister => 'መለያ የለዎትም? ይመዝገቡ';

  @override
  String get switchToLogin => 'መለያ አለዎት? ይግቡ';

  @override
  String get homeLobby => 'ሎቢ';

  @override
  String get homeWallet => 'ቦርሳ';

  @override
  String get homeProfile => 'መገለጫ';

  @override
  String get roomsTitle => 'ክፍሎች';

  @override
  String get liveGameTitle => 'የቀጥታ ጨዋታ';

  @override
  String get sessionStatusLabel => 'የሴሽን ሁኔታ';

  @override
  String get ticketCountLabel => 'ቲኬቶች';

  @override
  String get drawCountLabel => 'መዝገቦች';

  @override
  String get calledNumbersTitle => 'የተጠሩ ቁጥሮች';

  @override
  String get myTicketsTitle => 'የእኔ ቲኬቶች';

  @override
  String get claimHistoryTitle => 'የጥያቄ ታሪክ';

  @override
  String get claimPatternLabel => 'የጥያቄ አይነት';

  @override
  String get claimSettlementLabel => 'የሽልማት ክፍያ';

  @override
  String get claimSettlementUnavailableBody =>
      'የሽልማት ክፍያ ሁኔታ በbackend ይተካል። የplayer ኮንትራቱ የጥያቄ ውጤትና entitlement ID ብቻ ያሳያል።';

  @override
  String get claimSubmitted => 'ጥያቄ ተልኳል።';

  @override
  String get submitClaim => 'ጥያቄ ላክ';

  @override
  String get previous => 'ቀዳሚ';

  @override
  String get next => 'ቀጣይ';

  @override
  String get ticketPageLabel => 'የቲኬት ገጽ';

  @override
  String get liveConnectionConnecting => 'በመገናኘት ላይ...';

  @override
  String get liveConnectionConnected => 'ቀጥታ';

  @override
  String get liveConnectionReconnecting => 'እንደገና በመገናኘት ላይ...';

  @override
  String get liveConnectionPaused => 'ቆሟል';

  @override
  String get liveConnectionDisconnected => 'ተቋርጧል';

  @override
  String get liveConnectionExpired => 'ስርዓቱ ጊዜው አልፏል';

  @override
  String get liveConnectionFinished => 'ተጠናቋል';

  @override
  String get freeCenterLabel => 'FREE';

  @override
  String get claimPatternPending => 'በመጠባበቅ ላይ';

  @override
  String get claimPatternApproved => 'ጸድቋል';

  @override
  String get claimPatternRejected => 'ተቃውሟል';

  @override
  String get claimPatternFailed => 'የbackend ማረጋገጫ አልተሳካም';

  @override
  String get sessionTimesTitle => 'የሰአት መረጃ';

  @override
  String get sessionGapTitle => 'የሴሽን ዝርዝር የለም';

  @override
  String get sessionGapBody =>
      'Backend የሚመጡ ሴሽኖችን በቀጥታ አያሳይም። የሴሽን ID በመግባት ዝርዝሩን ይክፈቱ።';

  @override
  String get roomPriceLabel => 'ዋጋ';

  @override
  String get roomStatusActive => 'ንቁ';

  @override
  String get roomStatusInactive => 'ንቁ አይደለም';

  @override
  String get roomCapacityLabel => 'አቅም';

  @override
  String get availableSeatsLabel => 'የቀሩ መቀመጫዎች';

  @override
  String get roomScheduleLabel => 'መርሀ ግብር';

  @override
  String get roomPlayersLabel => 'የተጫዋቾች ብዛት';

  @override
  String get playerCountUnavailable => 'በአሁኑ ኮንትራት አልተገለጸም';

  @override
  String get enterSessionIdLabel => 'የሴሽን ID';

  @override
  String get loadSession => 'ሴሽን አሳይ';

  @override
  String get sessionIdRequired => 'የሴሽን ID ያስገቡ።';

  @override
  String get purchaseTicket => 'ቲኬት ይግዙ';

  @override
  String get purchaseTicketTitle => 'የቲኬት ግዢ ማረጋገጫ';

  @override
  String get purchaseTicketBody =>
      'ድጋሚ ሙከራዎች ድርብ ቲኬት እንዳይፈጥሩ የidempotency ቁልፍ ይጠቀማል።';

  @override
  String get confirm => 'አረጋግጥ';

  @override
  String get cancel => 'ሰርዝ';

  @override
  String get purchaseSuccess => 'የቲኬት ግዢ ተጠናቀቀ።';

  @override
  String get balanceTitle => 'ቀሪ መጠን';

  @override
  String get balanceDisclaimer => 'በመጠባበቅ ላይ ያሉ ተቀማጮች ሊያወጡ አይችሉም።';

  @override
  String get currentBalance => 'አሁን ያለው ቀሪ መጠን';

  @override
  String get transactionsTitle => 'ተግባሮች';

  @override
  String get depositsTitle => 'ተቀማጮች';

  @override
  String get transactionHistoryEmpty => 'እስካሁን የቦርሳ ተግባር የለም።';

  @override
  String get depositHistoryEmpty => 'እስካሁን ተቀማጭ የለም።';

  @override
  String get amountLabel => 'መጠን';

  @override
  String get telebirrReferenceLabel => 'Telebirr ማስታወቂያ ቁጥር';

  @override
  String get currencyLabel => 'ገንዘብ አይነት';

  @override
  String get submitDeposit => 'ተቀማጭ ላክ';

  @override
  String get telebirrInstructionsTitle => 'Telebirr መመሪያ';

  @override
  String get telebirrInstructionsBody =>
      'ከbackend የሚመጡትን የአቅራቢ መረጃዎች ተጠቅመው በተቀባይነት ደረሰኝ ላይ እንደሚታይ ትክክለኛውን ማስታወቂያ ቁጥር ያስገቡ።';

  @override
  String get profileTitle => 'መገለጫ';

  @override
  String get logout => 'ውጣ';

  @override
  String get profileMemberSince => 'ከዚህ ጀምሮ';

  @override
  String get profileRoles => 'ሚናዎች';

  @override
  String get statusPending => 'በመጠባበቅ ላይ';

  @override
  String get statusApproved => 'ጸድቋል';

  @override
  String get statusRejected => 'ተቃውሟል';

  @override
  String get statusOpen => 'ክፍት';

  @override
  String get statusInProgress => 'በሂደት ላይ';

  @override
  String get statusWaiting => 'የደንበኛ ምላሽ በመጠባበቅ ላይ';

  @override
  String get statusResolved => 'ተፈታ';

  @override
  String get statusClosed => 'ተዘግቷል';

  @override
  String get sessionExpired => 'ስርዓቱ ጊዜው አልፏል። እባክዎ እንደገና ይግቡ።';

  @override
  String get contractGapTitle => 'የbackend ኮንትራት ክፍት ቦታ';

  @override
  String get contractGapBody =>
      'የአሁኑ API ለUI ማሳየት የሚችለውን ሁሉ አያቀርብም። መተግበሪያው የእውነተኛ backend መረጃ ብቻ ያሳያል እና የማይሞላውን ክፍት ቦታ ይጠቁማል።';

  @override
  String get refresh => 'አድስ';
}
