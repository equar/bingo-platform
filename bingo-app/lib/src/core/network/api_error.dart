import 'package:bingo_app/l10n/app_localizations.dart';
import 'package:dio/dio.dart';

class ApiError implements Exception {
  ApiError({
    required this.code,
    this.statusCode,
    this.details = const [],
    this.correlationId,
    this.requestPath,
    this.outcomeUnknown = false,
    this.cancelled = false,
  });

  final String code;
  final int? statusCode;
  final List<ApiValidationError> details;
  final String? correlationId;
  final String? requestPath;
  final bool outcomeUnknown;
  final bool cancelled;

  String userMessage(AppLocalizations localizations) {
    final message = _baseUserMessage(localizations);
    final reference = correlationId;
    if (!cancelled &&
        (outcomeUnknown || (statusCode != null && statusCode! >= 500)) &&
        reference != null &&
        RegExp(r'^[A-Za-z0-9._-]{1,100}$').hasMatch(reference)) {
      return '$message ${localizations.errorReference(reference)}';
    }
    return message;
  }

  String _baseUserMessage(AppLocalizations localizations) {
    if (cancelled) return '';
    if (outcomeUnknown) return localizations.errorTransactionUnknown;
    if (_isLoginPath &&
        ((statusCode == 401 || statusCode == 403) ||
            code == 'AUTH_INVALID_CREDENTIALS')) {
      return localizations.errorInvalidCredentials;
    }
    if (statusCode == 401 ||
        code == 'SESSION_EXPIRED' ||
        code == 'UNAUTHORIZED') {
      return localizations.sessionExpired;
    }
    if (code == 'AUTH_INVALID_CREDENTIALS' || code == 'AUTHENTICATION_DENIED') {
      return localizations.errorInvalidCredentials;
    }
    if (code == 'INSUFFICIENT_BALANCE' || code == 'INSUFFICIENT_FUNDS') {
      return localizations.errorInsufficientBalance;
    }
    if (code == 'GAME_CLOSED' ||
        code == 'ROOM_CLOSED' ||
        code == 'ROUND_CLOSED' ||
        code == 'SESSION_CLOSED') {
      return localizations.errorGameClosed;
    }
    if (code == 'SESSION_NOT_DRAWING') return localizations.errorGameNotOpen;
    if (code == 'SESSION_CAPACITY_REACHED') return localizations.errorGameFull;
    if (code == 'ACCESS_DENIED' || code == 'FORBIDDEN') {
      return localizations.errorPermissionDenied;
    }
    if (code == 'DEPOSIT_VERIFICATION_REQUIRED') {
      return localizations.errorDepositVerificationRequired;
    }
    if (code == 'FINANCE_SEPARATION_OF_DUTIES_REQUIRED') {
      return localizations.errorFinanceSeparation;
    }
    if (code == 'AGENT_NOT_APPROVED') {
      return localizations.errorAgentApprovalRequired;
    }
    if (code == 'AGENT_NOT_ACTIVE') return localizations.errorAgentInactive;
    if (code == 'AGENT_NOT_ELIGIBLE_FOR_FLOAT') {
      return localizations.errorAgentFloatIneligible;
    }
    if (code == 'TOP_UP_NOT_VERIFIED') {
      return localizations.errorTopUpNotVerified;
    }
    if (code == 'PAYOUT_ALREADY_SETTLED') {
      return localizations.errorPayoutSettled;
    }
    if (code == 'TOP_UP_REQUEST_ALREADY_REVIEWED' ||
        code == 'DEPOSIT_ALREADY_REVIEWED') {
      return localizations.errorAlreadyReviewed;
    }
    if (code == 'CLOSED_TICKET_TERMINAL') {
      return localizations.errorClosedTicket;
    }
    if (code == 'SELF_TRANSFER_NOT_ALLOWED') {
      return localizations.errorSelfTransfer;
    }
    if (code == 'TRANSFER_LIMIT_EXCEEDED') {
      return localizations.errorTransferLimit;
    }
    if (code == 'DAILY_TRANSFER_LIMIT_EXCEEDED') {
      return localizations.errorDailyTransferLimit;
    }
    if (code == 'TRANSFERS_DISABLED' || code == 'TRANSFER_POLICY_INACTIVE') {
      return localizations.errorTransferUnavailable;
    }
    if (code == 'PHONE_ALREADY_REGISTERED') {
      return localizations.errorAccountCreation;
    }
    if (code == 'INVALID_PHONE_NUMBER' || code == 'PHONE_REQUIRED') {
      return localizations.errorInvalidPhone;
    }
    if (code == 'INVALID_PIN') return localizations.errorInvalidPin;
    if (code == 'DUPLICATE_TELEBIRR_REFERENCE' ||
        code == 'DUPLICATE_BANK_REFERENCE') {
      return localizations.errorDuplicateReference;
    }
    if (code == 'RATE_LIMIT_EXCEEDED' ||
        code == 'AUTH_RATE_LIMITED' ||
        code.endsWith('_RATE_LIMITED')) {
      return localizations.errorTooManyAttempts;
    }
    if (code == 'VALIDATION_ERROR' || code == 'INVALID_REQUEST') {
      return localizations.errorValidation;
    }
    if (statusCode == 0 || code == 'NETWORK_ERROR') {
      return localizations.errorNoConnection;
    }
    if (statusCode == 403) return localizations.errorPermissionDenied;
    if (statusCode == 404) return localizations.errorItemMissing;
    if (statusCode == 400 || statusCode == 422) {
      return localizations.errorValidation;
    }
    if (statusCode == 429) return localizations.errorTooManyAttempts;
    if (statusCode == 503) return localizations.errorServiceUnavailable;
    return localizations.errorGeneric;
  }

  bool get _isLoginPath =>
      (requestPath ?? '').endsWith('/auth/login') ||
      (requestPath ?? '').endsWith('/player/login');

  String get message {
    if (cancelled) return '';
    if (outcomeUnknown) {
      return 'We couldn’t confirm the transaction status. Check your transaction history before trying again.';
    }
    if (statusCode == 401 ||
        code == 'SESSION_EXPIRED' ||
        code == 'UNAUTHORIZED') {
      return 'Your session has expired. Please sign in again.';
    }
    if (statusCode == 403 || code == 'ACCESS_DENIED' || code == 'FORBIDDEN') {
      return 'You don’t have permission to perform this action.';
    }
    if (statusCode == 0) {
      return 'Unable to connect. Check your internet connection and try again.';
    }
    if (statusCode == 503) {
      return 'The service is temporarily unavailable. Please try again later.';
    }
    return 'Something went wrong. Please try again.';
  }

  @override
  String toString() => message;
}

class ApiValidationError {
  const ApiValidationError({required this.field, required this.message});

  final String field;
  final String message;
}

ApiError mapDioApiError(DioException error) {
  final response = error.response;
  final data = response?.data;
  final body = data is Map ? data : const <Object?, Object?>{};
  final statusCode =
      response?.statusCode ??
      (body['status'] is int ? body['status'] as int : null);
  final method = error.requestOptions.method.toUpperCase();
  final path = _pathWithoutQuery(error.requestOptions.uri.path);
  final code = body['code'] is String
      ? body['code'] as String
      : statusCode == 401
      ? 'UNAUTHORIZED'
      : statusCode == 403
      ? 'FORBIDDEN'
      : statusCode == 0 || statusCode == null
      ? 'NETWORK_ERROR'
      : 'HTTP_$statusCode';
  final cancelled = error.type == DioExceptionType.cancel;
  final financialAction = _isFinancialMutation(method, path);
  final transportFailure =
      response == null &&
      (error.type == DioExceptionType.connectionError ||
          error.type == DioExceptionType.connectionTimeout ||
          error.type == DioExceptionType.sendTimeout ||
          error.type == DioExceptionType.receiveTimeout ||
          error.type == DioExceptionType.unknown);
  final outcomeUnknown =
      financialAction &&
      !cancelled &&
      (transportFailure ||
          statusCode == 408 ||
          (statusCode != null && statusCode >= 500));

  return ApiError(
    code: code,
    statusCode: statusCode,
    details: _safeDetails(body['details']),
    correlationId: body['correlationId'] is String
        ? body['correlationId'] as String
        : null,
    requestPath: path,
    outcomeUnknown: outcomeUnknown,
    cancelled: cancelled,
  );
}

ApiError normalizeApiError(Object error) {
  if (error is ApiError) return error;
  if (error is DioException) return mapDioApiError(error);
  return ApiError(code: 'UNEXPECTED_ERROR');
}

String? userFacingApiError(
  Object? error,
  AppLocalizations localizations, {
  bool financialAction = false,
}) {
  if (error is ApiError) {
    return error.cancelled ? null : error.userMessage(localizations);
  }
  if (error is DioException) {
    final mapped = mapDioApiError(error);
    if (financialAction && !mapped.cancelled && !mapped.outcomeUnknown) {
      return ApiError(
        code: mapped.code,
        statusCode: mapped.statusCode,
        requestPath: mapped.requestPath,
        outcomeUnknown: true,
      ).userMessage(localizations);
    }
    return mapped.cancelled ? null : mapped.userMessage(localizations);
  }
  return localizations.errorGeneric;
}

String? fieldErrorMessage(
  Object? error,
  String field,
  AppLocalizations localizations,
) {
  if (error is! ApiError) return null;
  ApiValidationError? detail;
  for (final item in error.details) {
    if (item.field.toLowerCase() == field.toLowerCase()) {
      detail = item;
      break;
    }
  }
  if (detail == null) {
    if (error.code == 'PHONE_REQUIRED' &&
        const {
          'phone',
          'phonenumber',
          'playerphonenumber',
          'recipientphonenumber',
        }.contains(field.toLowerCase())) {
      return localizations.errorInvalidPhone;
    }
    return null;
  }
  switch (field.toLowerCase()) {
    case 'phone':
    case 'phonenumber':
    case 'playerphonenumber':
    case 'recipientphonenumber':
      return localizations.errorInvalidPhone;
    case 'pin':
    case 'password':
      return localizations.errorInvalidPin;
    case 'amount':
    case 'submittedcashamount':
    case 'payoutamount':
      return localizations.errorInvalidAmount;
    case 'reference':
    case 'referencenumber':
    case 'telebirrreference':
    case 'transferreference':
    case 'transactionreference':
    case 'supportingreference':
      return localizations.errorInvalidReference;
    default:
      return localizations.errorValidation;
  }
}

List<ApiValidationError> _safeDetails(Object? rawDetails) {
  if (rawDetails is! List) return const [];
  return rawDetails
      .whereType<Map>()
      .where((item) => item['field'] is String)
      .map((item) {
        return ApiValidationError(
          field: item['field'] as String,
          message: 'Check this field and try again.',
        );
      })
      .toList();
}

String _pathWithoutQuery(String path) => path.split('?').first;

bool _isFinancialMutation(String method, String path) =>
    const {'POST', 'PUT', 'PATCH', 'DELETE'}.contains(method) &&
    RegExp(
      r'/(?:wallet/(?:deposits?|transfers?|withdrawals?|adjustments?|top-ups?|purchases?|tickets?|claims?)|wallet-adjustment(?:s|-requests)?|deposits?|transfers?|withdrawals?|payments?|payouts?|float(?:-top-up)?|top-ups?|purchases?|tickets?|claims?|prize-entitlements)(?:/|-|$)',
      caseSensitive: false,
    ).hasMatch(path);
