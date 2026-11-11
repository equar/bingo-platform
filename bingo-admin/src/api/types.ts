export interface ApiErrorDetail {
  field: string;
  message: string;
}

interface ApiErrorOptions {
  status?: number;
  code?: string;
  details?: ApiErrorDetail[];
  correlationId?: string;
  requestPath?: string;
  outcomeUnknown?: boolean;
  cancelled?: boolean;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly details: ApiErrorDetail[];
  readonly correlationId?: string;
  readonly requestPath?: string;
  readonly outcomeUnknown: boolean;
  readonly cancelled: boolean;

  constructor(message: string, options: ApiErrorOptions = {}) {
    super(message);
    this.name = "ApiError";
    this.status = options.status ?? 0;
    this.code = options.code ?? "UNEXPECTED_ERROR";
    this.details = options.details ?? [];
    this.correlationId = options.correlationId;
    this.requestPath = options.requestPath;
    this.outcomeUnknown = options.outcomeUnknown ?? false;
    this.cancelled = options.cancelled ?? false;
  }
}

export interface ApiErrorResponse {
  timestamp: string;
  status: number;
  error: string;
  code?: string;
  message: string;
  path?: string;
  correlationId?: string;
  details?: ApiErrorDetail[];
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export function pageDefaults<T>(): PageResponse<T> {
  return {
    content: [],
    page: 0,
    size: 0,
    totalElements: 0,
    totalPages: 0,
    first: true,
    last: true,
  };
}

export interface UserFacingErrorOptions {
  financialAction?: boolean;
}

const SAFE_MESSAGES = {
  invalidLogin: "The phone number or password is incorrect.",
  expiredSession: "Your session has expired. Please sign in again.",
  permissionDenied: "You don’t have permission to perform this action.",
  noConnection: "We couldn’t reach the server right now. Check your connection or try again in a moment.",
  serviceUnavailable: "The service is temporarily unavailable. Please try again later.",
  missingItem: "This item is no longer available. Refresh and try again.",
  validation: "Check the highlighted fields and try again.",
  insufficientBalance: "You don’t have enough balance to complete this transaction.",
  closedGame: "This game is closed. Please choose another game.",
  transactionUnknown: "We couldn’t confirm the transaction status. Check your transaction history before trying again.",
  unexpected: "Something went wrong. Please try again.",
} as const;

const CODE_MESSAGES: Record<string, string> = {
  ACCESS_DENIED: SAFE_MESSAGES.permissionDenied,
  FORBIDDEN: SAFE_MESSAGES.permissionDenied,
  ADMIN_ROLE_REQUIRED: SAFE_MESSAGES.permissionDenied,
  DEPOSIT_VERIFICATION_REQUIRED: "This deposit must be independently verified before approval.",
  FINANCE_SEPARATION_OF_DUTIES_REQUIRED: "A different authorized finance user must review this request.",
  FINANCE_VERIFIER_NOT_AUTHORIZED: "You don’t have permission to verify this payment.",
  FINANCE_APPROVER_REQUIRED: "An authorized finance approver must review this request.",
  FINANCE_AUTHORIZATION_REQUIRED: "An authorized finance user is required for this action.",
  AGENT_NOT_APPROVED: "This agent must be approved before this action.",
  AGENT_NOT_ACTIVE: "This agent account is not active.",
  AGENT_NOT_ELIGIBLE_FOR_FLOAT: "This agent is not eligible to receive float.",
  TOP_UP_NOT_VERIFIED: "This request must be independently verified before it can be posted.",
  CLOSED_TICKET_TERMINAL: "This support ticket is closed and can no longer be changed.",
  SESSION_NOT_DRAWING: "This game is not currently open for draws.",
  SESSION_HAS_TICKETS_CANNOT_CANCEL: "Cancellation is unavailable after tickets have been sold.",
  SESSION_START_MUST_BE_FUTURE: "Scheduled start must be in the future.",
  SESSION_CAPACITY_REACHED: "This game is full. Choose another game.",
  TRANSFER_LIMIT_EXCEEDED: "This transfer is above the allowed limit.",
  DAILY_TRANSFER_LIMIT_EXCEEDED: "You have reached the daily transfer limit.",
  SELF_TRANSFER_NOT_ALLOWED: "You can’t transfer money to your own account.",
  DEPOSIT_ALREADY_REVIEWED: "This deposit has already been reviewed. Refresh to see its latest status.",
  INVALID_VERIFICATION_TRANSITION: "This review step is no longer available. Refresh the queue and try again.",
  INSUFFICIENT_BALANCE: SAFE_MESSAGES.insufficientBalance,
  INSUFFICIENT_FUNDS: SAFE_MESSAGES.insufficientBalance,
  GAME_CLOSED: SAFE_MESSAGES.closedGame,
  ROOM_CLOSED: SAFE_MESSAGES.closedGame,
  ROUND_CLOSED: SAFE_MESSAGES.closedGame,
  TICKET_CLOSED: SAFE_MESSAGES.closedGame,
  NOT_FOUND: SAFE_MESSAGES.missingItem,
  RESOURCE_NOT_FOUND: SAFE_MESSAGES.missingItem,
  VALIDATION_ERROR: SAFE_MESSAGES.validation,
  INVALID_REQUEST: SAFE_MESSAGES.validation,
  PHONE_ALREADY_REGISTERED: "We couldn’t create the account with those details. Check the information or sign in.",
  EMAIL_ALREADY_REGISTERED: "That email address is already in use. Enter a different email address.",
  EMAIL_DELIVERY_UNAVAILABLE: "The invitation email couldn’t be sent. Check the email delivery settings and try again.",
  SELF_ACCOUNT_STATUS_CHANGE_FORBIDDEN: "You can’t change your own account status.",
  SELF_ACTIVATION_RESEND_FORBIDDEN: "You can’t resend an activation email to your own account.",
  INVALID_PIN: "Enter a valid PIN.",
  INVALID_PHONE_NUMBER: "Enter a valid phone number.",
  PHONE_REQUIRED: "Enter a valid phone number.",
  INVALID_AMOUNT: "Enter a valid amount.",
  DUPLICATE_TELEBIRR_REFERENCE: "This reference has already been submitted. Check your transaction history.",
  DUPLICATE_BANK_REFERENCE: "This reference has already been submitted. Check your transaction history.",
  IDEMPOTENCY_KEY_REUSED: "This action has changed. Refresh and try again.",
  TOP_UP_REQUEST_ALREADY_PROCESSED: "This request has already been processed. Refresh to see its latest status.",
  TOP_UP_REQUEST_ALREADY_REVIEWED: "This request has already been reviewed. Refresh to see its latest status.",
  PAYOUT_ALREADY_SETTLED: "This payout has already been settled. Refresh to see its latest status.",
  PAYOUT_ALREADY_PAID: "This payout has already been completed. Refresh to see its latest status.",
  RATE_LIMIT_EXCEEDED: "Too many attempts. Please wait a moment and try again.",
  AUTH_RATE_LIMITED: "Too many attempts. Please wait a moment and try again.",
  INTERNAL_ERROR: SAFE_MESSAGES.unexpected,
};

const FIELD_MESSAGES: Record<string, string> = {
  amount: "Enter a valid amount.",
  confirmPassword: "The passwords do not match.",
  email: "Enter a valid email address.",
  currentPassword: "The current password is incorrect.",
  newPassword: "Choose a password that meets the requirements.",
  password: "Enter a valid password.",
  phone: "Enter a valid phone number.",
  phoneNumber: "Enter a valid phone number.",
  reference: "Enter a valid transaction reference.",
  referenceNumber: "Enter a valid transaction reference.",
  token: "This link is invalid or has expired. Request a new one.",
};

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function getRecord(value: unknown): Record<string, unknown> | undefined {
  return isRecord(value) ? value : undefined;
}

function getString(value: unknown): string | undefined {
  return typeof value === "string" && value.length > 0 ? value : undefined;
}

function getStatus(value: unknown): number {
  return typeof value === "number" && Number.isFinite(value) ? value : 0;
}

function normalizePath(value: unknown): string | undefined {
  const raw = getString(value);
  if (!raw) return undefined;
  try {
    return new URL(raw, "http://localhost").pathname;
  } catch {
    return raw.split(/[?#]/, 1)[0];
  }
}

function isLoginPath(path?: string): boolean {
  return Boolean(path && /(?:^|\/)(?:admin\/)?login\/?$/.test(path));
}

function isFinancialMutation(method?: string, path?: string): boolean {
  if (!path || !method || !["POST", "PUT", "PATCH", "DELETE"].includes(method.toUpperCase())) {
    return false;
  }
  return /\/(?:wallet\/(?:deposits?|transfers?|withdrawals?|adjustments?|top-ups?|purchases?|tickets?|claims?)|wallet-adjustment(?:s|-requests)?|deposits?|transfers?|withdrawals?|payments?|payouts?|float(?:-top-up)?|top-ups?|purchases?|tickets?|claims?|prize-entitlements)(?:\/|-|$)/i.test(path);
}

function getSafeMessage(status: number, code: string, path: string | undefined, outcomeUnknown: boolean): string {
  if (outcomeUnknown) return SAFE_MESSAGES.transactionUnknown;
  if (isLoginPath(path) && (status === 401 || status === 403 || code === "AUTH_INVALID_CREDENTIALS")) {
    return SAFE_MESSAGES.invalidLogin;
  }
  if (status === 401 && path?.endsWith("/admin/me/password")) {
    return "The current password is incorrect.";
  }
  if (code === "SESSION_EXPIRED" || (status === 401 && !isLoginPath(path))) {
    return SAFE_MESSAGES.expiredSession;
  }
  if (code === "AUTH_INVALID_CREDENTIALS" || code === "AUTHENTICATION_DENIED") {
    return SAFE_MESSAGES.invalidLogin;
  }
  if (code === "ACTIVATION_TOKEN_INVALID" || code === "ACTIVATION_TOKEN_EXPIRED") {
    return "This activation link is invalid or has expired. Request a new one.";
  }
  if (CODE_MESSAGES[code]) return CODE_MESSAGES[code];
  if (status === 0) return SAFE_MESSAGES.noConnection;
  if (status === 401) return SAFE_MESSAGES.expiredSession;
  if (status === 403) return SAFE_MESSAGES.permissionDenied;
  if (status === 404) return SAFE_MESSAGES.missingItem;
  if (status === 422 || status === 400) return SAFE_MESSAGES.validation;
  if (status === 409) return "This item has changed. Refresh and try again.";
  if (status === 429) return "Too many attempts. Please wait a moment and try again.";
  if (status === 503) return SAFE_MESSAGES.serviceUnavailable;
  if (status >= 500) return SAFE_MESSAGES.unexpected;
  return SAFE_MESSAGES.unexpected;
}

function parseDetails(value: unknown): ApiErrorDetail[] {
  if (!Array.isArray(value)) return [];
  return value.flatMap((entry): ApiErrorDetail[] => {
    const record = getRecord(entry);
    const field = getString(record?.field);
    if (!field) return [];
    return [{ field, message: FIELD_MESSAGES[field] ?? "Check this field and try again." }];
  });
}

export function parseApiError(error: unknown, options: UserFacingErrorOptions = {}): ApiError {
  if (error instanceof ApiError) return error;
  const errorRecord = getRecord(error);
  const response = getRecord(errorRecord?.response);
  const data = getRecord(response?.data);
  const status = getStatus(response?.status) || getStatus(data?.status);
  const path = normalizePath(getRecord(errorRecord?.config)?.url ?? data?.path);
  const method = getString(getRecord(errorRecord?.config)?.method)?.toUpperCase();
  const code = getString(data?.code) ?? (status === 401 ? "UNAUTHORIZED" : status === 403 ? "FORBIDDEN" : "UNEXPECTED_ERROR");
  const cancelled =
    errorRecord?.code === "ERR_CANCELED" ||
    errorRecord?.name === "CanceledError" ||
    errorRecord?.__CANCEL__ === true;
  const hasResponse = Boolean(response);
  const isTransportFailure =
    !hasResponse &&
    (errorRecord?.isAxiosError === true ||
      errorRecord?.code === "ERR_NETWORK" ||
      errorRecord?.code === "ECONNABORTED" ||
      errorRecord?.code === "ETIMEDOUT");
  const financialAction = options.financialAction ?? isFinancialMutation(method, path);
  const outcomeUnknown =
    financialAction &&
    !cancelled &&
    (isTransportFailure || status === 408 || (status >= 500 && status <= 599));
  const message = cancelled
    ? ""
    : isTransportFailure
      ? financialAction
        ? SAFE_MESSAGES.transactionUnknown
        : SAFE_MESSAGES.noConnection
      : getSafeMessage(status || 520, code, path, outcomeUnknown);

  return new ApiError(message, {
    status,
    code,
    details: parseDetails(data?.details),
    correlationId: getString(data?.correlationId),
    requestPath: path,
    outcomeUnknown,
    cancelled,
  });
}

export function getUserFacingErrorMessage(error: unknown, options?: UserFacingErrorOptions): string | undefined {
  const parsed = error instanceof ApiError ? error : parseApiError(error, options);
  if (parsed.cancelled) return undefined;
  const reference = parsed.correlationId;
  if (
    (parsed.outcomeUnknown || parsed.status >= 500) &&
    reference &&
    /^[A-Za-z0-9._-]{1,100}$/.test(reference)
  ) {
    return `${parsed.message} Reference: ${reference}`;
  }
  return parsed.message;
}

export function getFieldError(error: unknown, field: string): string | undefined {
  const parsed = error instanceof ApiError ? error : parseApiError(error);
  const normalizedField = field.toLowerCase();
  const detail = parsed.details.find((item) => item.field.toLowerCase() === normalizedField);
  if (detail) return detail.message;
  if (
    parsed.code === "PHONE_REQUIRED" &&
    ["phone", "phonenumber", "playerphonenumber", "recipientphonenumber"].includes(normalizedField)
  ) {
    return FIELD_MESSAGES[field] ?? FIELD_MESSAGES.phone;
  }
  return undefined;
}

export function getApiErrorReference(error: unknown): string | undefined {
  const parsed = error instanceof ApiError ? error : parseApiError(error);
  const reference = parsed.correlationId;
  return reference && /^[A-Za-z0-9._-]{1,100}$/.test(reference)
    ? `Reference: ${reference}`
    : undefined;
}
