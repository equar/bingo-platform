import i18n from "i18next";
import { initReactI18next } from "react-i18next";
import { adminUiTranslations } from "./adminUiTranslations";

const sessionStorageKey = "bingo-admin-session";
const legacyLocaleStorageKey = "bingo-admin-locale";
const adminUiEnglish = Object.fromEntries(
  Object.keys(adminUiTranslations).map((text) => [text, text]),
);

export const SUPPORTED_LANGUAGES = ["en", "am"] as const;
export type SupportedLanguage = (typeof SUPPORTED_LANGUAGES)[number];
export const DEFAULT_LANGUAGE: SupportedLanguage = "en";

export function resolvePreferredLanguage(value: unknown): SupportedLanguage {
  return value === "am" ? "am" : DEFAULT_LANGUAGE;
}

// The unauthenticated login screen is always English. An authenticated user's
// saved preference is read from the backend profile cached in the user-scoped
// session so a page refresh renders in the right language without a flash.
// Browser language and shared global storage are intentionally ignored.
function getInitialLanguage(): SupportedLanguage {
  try {
    window.localStorage.removeItem(legacyLocaleStorageKey);
    const raw = window.localStorage.getItem(sessionStorageKey);
    if (!raw) {
      return DEFAULT_LANGUAGE;
    }
    const session = JSON.parse(raw) as { user?: { preferredLanguage?: unknown } };
    return resolvePreferredLanguage(session?.user?.preferredLanguage);
  } catch (error) {
    console.warn("Could not read the saved language preference.", error);
    return DEFAULT_LANGUAGE;
  }
}

export function applyLanguage(language: SupportedLanguage): void {
  if (i18n.language !== language) {
    void i18n.changeLanguage(language);
  }
}

void i18n.use(initReactI18next).init({
  resources: {
    en: {
      translation: {
        ...adminUiEnglish,
        "language.label": "Language",
        "language.helper": "Controls the display language of the admin application.",
        "language.saving": "Saving...",
        "language.saved": "Your language preference has been saved.",
        "language.saveFailed": "Your language preference could not be saved.",
        "profile.preferences": "Preferences",
        "profile.preferencesIntro": "Settings saved to your account.",
        "brand": "Sunset Bingo Admin",
        "auth.signInTitle": "Sign in",
        "auth.activateAccountTitle": "Activate account",
        "auth.resetPasswordTitle": "Reset password",
        "nav.overview": "Overview",
        "nav.operations": "Operations",
        "nav.reviews": "Reviews & Support",
        "nav.accounts": "Players & Agents",
        "nav.finance": "Finance",
        "nav.administration": "Administration",
        "nav.dashboard": "Dashboard",
        "nav.support": "Support tickets",
        "nav.claims": "Win claims",
        "nav.deposits": "Deposits",
        "nav.sessions": "Sessions",
        "nav.sessionTickets": "Session tickets",
        "nav.rooms": "Rooms",
        "nav.community": "Player community",
        "nav.audit": "Audit logs",
        "nav.reports": "Reports",
        "nav.adminUsers": "Admin users",
        "nav.agents": "Agents",
        "nav.players": "Players",
        "nav.verification": "Player verification",
        "nav.signupReward": "Promotional rewards",
        "nav.signupRewardGrants": "Promotional reward grants",
        "Refresh": "Refresh",
        "Refreshing": "Refreshing",
        "Refresh results": "Refresh results",
        "nav.transferPolicy": "Transfer policy",
        "nav.transfers": "Transfer history",
        "nav.entitlements": "Prize entitlements",
        "account": "Account",
        "noRole": "No role",
        "menu.profile": "Profile",
        "menu.changePassword": "Change password",
        "menu.logout": "Logout",
        "menu.openNavigation": "Open navigation",
        "menu.open": "Menu",
        "navigation.note": "Backend authorization remains authoritative.",
        "navigation.primary": "Primary navigation",
        "menu.closeNavigation": "Close navigation",
        "menu.collapseNavigation": "Collapse navigation",
        "menu.expandNavigation": "Expand navigation",
        "login.sessionExpired": "Your session has expired. Please sign in again.",
        "login.adminConsole": "ADMIN CONSOLE",
        "login.welcome": "Welcome to Sunset Bingo's operations hub. Sign in to keep every game running smoothly and every player smiling.",
        "login.email": "Email address",
        "login.password": "Password",
        "login.hidePassword": "Hide password",
        "login.showPassword": "Show password",
        "login.hide": "Hide",
        "login.show": "Show",
        "login.signingIn": "Signing in...",
        "login.signIn": "Sign in",
        "login.secureAccess": "Secure access for authorized admin and support accounts",
        "login.invalidCredentials": "The email or password is incorrect.",
        "login.unexpected": "Something went wrong. Please try again.",
        "login.noConnection": "We couldn’t reach the server right now. Check your connection or try again in a moment.",
        "login.serviceUnavailable": "The service is temporarily unavailable. Please try again later.",
        "profile.adminProfile": "Admin profile",
        "profile.title": "Profile",
        "profile.intro": "Review your signed-in admin account details in one focused place.",
        "profile.accountDetails": "Account details",
        "profile.identity": "Your current admin identity and status.",
        "profile.personalInfo": "Personal information",
        "profile.personalInfoIntro": "Keep your name and contact number up to date.",
        "profile.displayName": "Full name",
        "profile.email": "Email address",
        "profile.phone": "Phone number",
        "profile.emailReadOnly": "Email is used to sign in and cannot be changed here.",
        "profile.phoneHelper": "Enter a valid Ethiopian mobile number.",
        "profile.saveChanges": "Save changes",
        "profile.saving": "Saving…",
        "profile.saved": "Your profile has been updated.",
        "profile.saveFailed": "Your profile could not be updated. Check the details and try again.",
        "profile.nameRequired": "Enter your name.",
        "profile.status": "Status",
        "profile.roles": "Roles",
        "profile.userId": "User ID",
        "profile.created": "Created",
        "profile.updated": "Updated",
        "profile.accountMenuHint": "Use the account menu in the top-right corner for password changes and logout.",
        "status.active": "Active",
        "status.inactive": "Inactive",
        "status.pending": "Pending",
        "status.suspended": "Suspended",
        "status.unknown": "Unknown",
        "activation.title": "Set up your Sunset Bingo Admin account",
        "activation.description": "Choose a password to activate your account. The activation link can only be used once.",
        "activation.invalidLink": "This activation link is missing or has already been used.",
        "activation.newPassword": "New password",
        "activation.confirmPassword": "Confirm new password",
        "activation.passwordHint": "Use 8 to 72 characters.",
        "activation.confirmPasswordHint": "Re-enter the new password.",
        "Complete all required fields and make sure the passwords match.": "Complete all required fields and make sure the passwords match.",
        "This field is required.": "This field is required.",
        "Re-enter the new password.": "Re-enter the new password.",
        "Password must contain 8 to 72 characters.": "Password must contain 8 to 72 characters.",
        "activation.passwordRequirements": "Choose a password that meets the requirements.",
        "activation.passwordMismatch": "The passwords do not match.",
        "activation.success": "Your account is ready. Sign in with your new password.",
        "activation.signIn": "Sign in",
        "activation.activating": "Activating...",
        "activation.activate": "Activate account",
        "activation.invalidCredentials": "This activation link is invalid or has expired. Request a new one.",
        "activation.unexpected": "Something went wrong. Please try again.",
        "missingConfig.title": "Sunset Bingo Admin",
        "missingConfig.alert": "Missing Vite environment variable",
        "missingConfig.body": "Configure the backend base URL in .env.local before starting the admin console.",
      },
    },
    am: {
      translation: {
        ...adminUiTranslations,
        "language.label": "ቋንቋ",
        "language.helper": "የአስተዳደር መተግበሪያውን የማሳያ ቋንቋ ይቆጣጠራል።",
        "language.saving": "በማስቀመጥ ላይ...",
        "language.saved": "የቋንቋ ምርጫዎ ተቀምጧል።",
        "language.saveFailed": "የቋንቋ ምርጫዎን ማስቀመጥ አልተቻለም።",
        "profile.preferences": "ምርጫዎች",
        "profile.preferencesIntro": "በመለያዎ ላይ የተቀመጡ ቅንብሮች።",
        "brand": "የSunset Bingo አስተዳደር",
        "auth.signInTitle": "ግባ",
        "auth.activateAccountTitle": "መለያ አግብር",
        "auth.resetPasswordTitle": "የይለፍ ቃል ዳግም አስጀምር",
        "nav.overview": "አጠቃላይ እይታ",
        "nav.operations": "ክዋኔዎች",
        "nav.reviews": "ግምገማና ድጋፍ",
        "nav.accounts": "ተጫዋቾችና ወኪሎች",
        "nav.finance": "ፋይናንስ",
        "nav.administration": "አስተዳደር",
        "nav.dashboard": "ዳሽቦርድ",
        "nav.support": "የድጋፍ ጥያቄዎች",
        "nav.claims": "የሽልማት ጥያቄዎች",
        "nav.deposits": "ተቀማጭ ገንዘቦች",
        "nav.sessions": "ክፍለ ጊዜዎች",
        "nav.sessionTickets": "የክፍለ ጊዜ ቲኬቶች",
        "nav.rooms": "ክፍሎች",
        "nav.community": "የተጫዋቾች ማህበረሰብ",
        "nav.audit": "የኦዲት መዝገቦች",
        "nav.reports": "ሪፖርቶች",
        "nav.adminUsers": "የአስተዳዳሪ ተጠቃሚዎች",
        "nav.agents": "ወኪሎች",
        "nav.players": "ተጫዋቾች",
        "nav.verification": "የተጫዋች ማረጋገጫ",
        "nav.signupReward": "የማስታወቂያ ሽልማቶች",
        "nav.signupRewardGrants": "የማስታወቂያ ሽልማት ስጦታዎች",
        "Refresh": "አድስ",
        "Refreshing": "በማደስ ላይ",
        "Refresh results": "ውጤቶችን አድስ",
        "nav.transferPolicy": "የዝውውር መመሪያ",
        "nav.transfers": "የዝውውር ታሪክ",
        "nav.entitlements": "የሽልማት መብቶች",
        "account": "መለያ",
        "noRole": "ሚና የለም",
        "menu.profile": "መገለጫ",
        "menu.changePassword": "የይለፍ ቃል ቀይር",
        "menu.logout": "ውጣ",
        "menu.openNavigation": "የአሰሳ ምናሌን ክፈት",
        "menu.open": "ምናሌ",
        "navigation.note": "የbackend ፈቃድ ቁጥጥር ዋናው ነው።",
        "navigation.primary": "ዋና የአሰሳ ምናሌ",
        "menu.closeNavigation": "የአሰሳ ምናሌን ዝጋ",
        "menu.collapseNavigation": "የአሰሳ ምናሌን አጠብብ",
        "menu.expandNavigation": "የአሰሳ ምናሌን ዘርጋ",
        "login.sessionExpired": "የመግቢያ ጊዜዎ አልፏል። እባክዎ እንደገና ይግቡ።",
        "login.adminConsole": "የአስተዳደር ማዕከል",
        "login.welcome": "ወደ Sunset Bingo የክዋኔ ማዕከል እንኳን በደህና መጡ። ጨዋታዎች በተቀላጠፈ እንዲሄዱ ለመግባት ይምረጡ።",
        "login.email": "የኢሜይል አድራሻ",
        "login.password": "የይለፍ ቃል",
        "login.hidePassword": "የይለፍ ቃሉን ደብቅ",
        "login.showPassword": "የይለፍ ቃሉን አሳይ",
        "login.hide": "ደብቅ",
        "login.show": "አሳይ",
        "login.signingIn": "በመግባት ላይ...",
        "login.signIn": "ግባ",
        "login.secureAccess": "ለተፈቀዱ አስተዳዳሪዎችና የድጋፍ ሰራተኞች የተጠበቀ መግቢያ",
        "login.invalidCredentials": "የኢሜይል አድራሻው ወይም የይለፍ ቃሉ ትክክል አይደለም።",
        "login.unexpected": "ችግኝ ተፈጥሯል። እባክዎ እንደገና ይሞክሩ።",
        "login.noConnection": "አሁን ከአገልግሎቱ ጋር መገናኘት አልቻልንም። እባክዎ ግንኙነትዎን ያረጋግጡ ወይም ትንሽ ቆይተው እንደገና ይሞክሩ።",
        "login.serviceUnavailable": "አገልግሎቱ ለጊዜው አይገኝም። እባክዎ በኋላ ይሞክሩ።",
        "profile.adminProfile": "የአስተዳዳሪ መገለጫ",
        "profile.title": "መገለጫ",
        "profile.intro": "የገቡበትን የአስተዳዳሪ መለያ ዝርዝር እዚህ ይመልከቱ።",
        "profile.accountDetails": "የመለያ ዝርዝሮች",
        "profile.identity": "የአሁኑ የአስተዳዳሪ መለያዎና ሁኔታዎ።",
        "profile.personalInfo": "የግል መረጃ",
        "profile.personalInfoIntro": "ስምዎንና የስልክ ቁጥርዎን ወቅታዊ ያድርጉ።",
        "profile.displayName": "ሙሉ ስም",
        "profile.email": "የኢሜይል አድራሻ",
        "profile.phone": "ስልክ ቁጥር",
        "profile.emailReadOnly": "ኢሜይሉ ለመግቢያ ይጠቅማል፤ እዚህ ሊቀየር አይችልም።",
        "profile.phoneHelper": "ትክክለኛ የኢትዮጵያ ሞባይል ቁጥር ያስገቡ።",
        "profile.saveChanges": "ለውጦችን አስቀምጥ",
        "profile.saving": "በማስቀመጥ ላይ…",
        "profile.saved": "የመገለጫ መረጃዎ ተዘምኗል።",
        "profile.saveFailed": "የመገለጫ መረጃዎን ማዘመን አልተቻለም። ዝርዝሮቹን ያረጋግጡና እንደገና ይሞክሩ።",
        "profile.nameRequired": "ስምዎን ያስገቡ።",
        "profile.status": "ሁኔታ",
        "profile.roles": "ሚናዎች",
        "profile.userId": "የተጠቃሚ መለያ ቁጥር",
        "profile.created": "የተፈጠረበት",
        "profile.updated": "የተዘመነበት",
        "profile.accountMenuHint": "የይለፍ ቃል ለመቀየር ወይም ለመውጣት ከላይ በቀኝ ያለውን የመለያ ምናሌ ይጠቀሙ።",
        "status.active": "ንቁ",
        "status.inactive": "ንቁ ያልሆነ",
        "status.pending": "በመጠባበቅ ላይ",
        "status.suspended": "ታግዷል",
        "status.unknown": "ያልታወቀ",
        "activation.title": "የSunset Bingo አስተዳዳሪ መለያዎን ያዘጋጁ",
        "activation.description": "መለያዎን ለማንቃት የይለፍ ቃል ይምረጡ። የማንቂያ ማገናኛው አንድ ጊዜ ብቻ ይሰራል።",
        "activation.invalidLink": "ይህ የማንቂያ ማገናኛ የለም ወይም አስቀድሞ ተጠቅመውበታል።",
        "activation.newPassword": "አዲስ የይለፍ ቃል",
        "activation.confirmPassword": "አዲሱን የይለፍ ቃል ያረጋግጡ",
        "activation.passwordHint": "ከ8 እስከ 72 ቁምፊዎችን ይጠቀሙ።",
        "activation.confirmPasswordHint": "አዲሱን የይለፍ ቃል እንደገና ያስገቡ።",
        "Complete all required fields and make sure the passwords match.": "ሁሉንም አስፈላጊ መስኮች ይሙሉና የይለፍ ቃሎቹ መዛመዳቸውን ያረጋግጡ።",
        "This field is required.": "ይህ መስክ ያስፈልጋል።",
        "Re-enter the new password.": "አዲሱን የይለፍ ቃል እንደገና ያስገቡ።",
        "Password must contain 8 to 72 characters.": "የይለፍ ቃሉ ከ8 እስከ 72 ቁምፊዎች መሆን አለበት።",
        "activation.passwordRequirements": "መስፈርቶቹን የሚያሟላ የይለፍ ቃል ይምረጡ።",
        "activation.passwordMismatch": "የይለፍ ቃሎቹ አይዛመዱም።",
        "activation.success": "መለያዎ ዝግጁ ነው። በአዲሱ የይለፍ ቃል ይግቡ።",
        "activation.signIn": "ግባ",
        "activation.activating": "በማንቃት ላይ...",
        "activation.activate": "መለያን አንቃ",
        "activation.invalidCredentials": "ይህ የማንቂያ ማገናኛ ልክ አይደለም ወይም ጊዜው አልፏል። አዲስ ማገናኛ ይጠይቁ።",
        "activation.unexpected": "ችግኝ ተፈጥሯል። እባክዎ እንደገና ይሞክሩ።",
        "missingConfig.title": "የSunset Bingo አስተዳደር",
        "missingConfig.alert": "Vite የአካባቢ ተለዋዋጭ የለም",
        "missingConfig.body": "የአስተዳደር መተግበሪያውን ከማስነሳትዎ በፊት የbackend መሠረት አድራሻን በ.env.local ያዋቅሩ።",
      },
    },
  },
  fallbackLng: "en",
  lng: getInitialLanguage(),
  supportedLngs: ["en", "am"],
  keySeparator: false,
  nsSeparator: false,
  interpolation: { escapeValue: false },
});

i18n.on("languageChanged", (language) => {
  if (typeof document !== "undefined") {
    document.documentElement.lang = language;
  }
});

if (typeof document !== "undefined") {
  document.documentElement.lang = i18n.language;
}

export default i18n;

const displayStatusLabels: Record<string, string> = {
  ACTIVE: "Active",
  AWAITING_VERIFICATION: "Awaiting verification",
  APPROVED: "Approved",
  CANCELLED: "Cancelled",
  CLOSED: "Closed",
  COMPLETED: "Completed",
  DISABLED: "Disabled",
  DRAWING: "Drawing",
  DRAFT: "Draft",
  EXPIRED: "Expired",
  FAILED: "Failed",
  FAILED_SERVER_VALIDATION: "Failed server validation",
  INACTIVE: "Inactive",
  IN_PROGRESS: "In progress",
  OPEN: "Open",
  PENDING: "Pending",
  PENDING_REVIEW: "Pending review",
  REJECTED: "Rejected",
  REVERSED: "Reversed",
  RESOLVED: "Resolved",
  SCHEDULED: "Scheduled",
  SETTLED: "Settled",
  STARTED: "Started",
  SUSPENDED: "Suspended",
  UNSETTLED: "Unsettled",
  UPDATE_SESSION_STATE: "Update session state",
  VERIFIED: "Verified",
  VERIFICATION_EXCEPTION: "Verification exception",
  WAITING_FOR_CUSTOMER: "Waiting for customer",
  WAITING_FOR_PLAYER: "Waiting for player",
  VOID: "Voided",
};

const displayValueLabels: Record<string, string> = {
  ...displayStatusLabels,
  ACCOUNT: "Account",
  ADMIN: "Administrator",
  AGENT: "Agent",
  AGENT_CASH: "Agent cash",
  AGENT_FLOAT_TOP_UP_CREDIT: "Agent float top-up credit",
  AGENT_FLOAT_TRANSFER_CREDIT: "Agent float transfer credit",
  BILLING: "Billing",
  COMMISSION_CREDIT: "Commission credit",
  DEPOSIT_CREDIT: "Deposit credit",
  FOUR_CORNERS: "Four corners",
  FULL_HOUSE: "Full house",
  GAME: "Game",
  HIGH: "High",
  L_SHAPE: "L shape",
  LINE: "Line",
  LOW: "Low",
  MANUAL_ADJUSTMENT: "Manual adjustment",
  MEDIUM: "Medium",
  OUTER_FRAME: "Outer frame",
  OPENING_BALANCE: "Opening balance",
  OTHER: "Other",
  POSTAGE_STAMP: "Postage stamp",
  PAYOUT_CLEARING_CREDIT: "Payout clearing credit",
  PAYOUT_DISBURSEMENT_DEBIT: "Payout disbursement debit",
  PLAYER: "Player",
  PLAYER_WALLET: "Player wallet",
  PLAYER_WALLET_TRANSFER_DEBIT: "Player wallet transfer debit",
  PRIZE_CREDIT: "Prize credit",
  SIGNUP_REWARD_CREDIT: "Signup reward credit",
  SUPPORT: "Support staff",
  TECHNICAL: "Technical",
  T_SHAPE: "T shape",
  TICKET_PURCHASE_DEBIT: "Ticket purchase debit",
  TICKET_SALE_DEBIT: "Ticket sale debit",
  TOP_UP: "Top up",
  TWO_LINES: "Two lines",
  URGENT: "Urgent",
  X_SHAPE: "X shape",
  WALLET: "Wallet",
  WALLET_ADJUSTMENT: "Wallet adjustment",
  WALLET_TRANSFER_CREDIT: "Wallet transfer credit",
  WALLET_TRANSFER_DEBIT: "Wallet transfer debit",
};

const displayRoleLabels: Record<string, string> = {
  ADMIN: "Administrator",
  AGENT: "Agent",
  SUPPORT: "Support staff",
  PLAYER: "Player",
};

export function translateAdminStatus(value: string, t: (key: string) => string) {
  const label = displayStatusLabels[value.toUpperCase()];
  return label ? t(label) : value.replace(/_/g, " ");
}

export function translateAdminValue(value: string, t: (key: string) => string) {
  const label = displayValueLabels[value.toUpperCase()];
  return label ? t(label) : value.replace(/_/g, " ");
}

export function translateAdminRole(value: string, t: (key: string) => string) {
  const label = displayRoleLabels[value.toUpperCase()];
  return label ? t(label) : value.replace(/_/g, " ");
}
