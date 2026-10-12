class ApiError implements Exception {
  ApiError({required this.code, required this.message, this.statusCode, this.details});

  final String code;
  final String message;
  final int? statusCode;
  final List<ApiValidationError>? details;

  @override
  String toString() => '$code: $message';
}

class ApiValidationError {
  const ApiValidationError({required this.field, required this.message});

  final String field;
  final String message;
}