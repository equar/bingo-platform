import 'dart:convert';

class AuthResponse {
  AuthResponse({
    required this.accessToken,
    required this.tokenType,
    required this.expiresInSeconds,
    required this.user,
  });

  final String accessToken;
  final String tokenType;
  final int expiresInSeconds;
  final UserProfile user;

  DateTime? get expiresAt {
    final parts = accessToken.split('.');
    if (parts.length != 3) return null;
    try {
      final payload = jsonDecode(
        utf8.decode(base64Url.decode(base64Url.normalize(parts[1]))),
      );
      if (payload is! Map || payload['exp'] is! num) return null;
      return DateTime.fromMillisecondsSinceEpoch(
        (payload['exp'] as num).toInt() * 1000,
        isUtc: true,
      );
    } on FormatException {
      return null;
    }
  }

  bool get isExpired {
    final expiry = expiresAt;
    return expiry == null || !DateTime.now().toUtc().isBefore(expiry);
  }

  factory AuthResponse.fromJson(Map<String, dynamic> json) => AuthResponse(
    accessToken: json['accessToken']?.toString() ?? '',
    tokenType: json['tokenType']?.toString() ?? 'Bearer',
    expiresInSeconds: (json['expiresInSeconds'] as num?)?.toInt() ?? 0,
    user: UserProfile.fromJson(
      Map<String, dynamic>.from(json['user'] as Map? ?? const {}),
    ),
  );

  Map<String, dynamic> toJson() => {
    'accessToken': accessToken,
    'tokenType': tokenType,
    'expiresInSeconds': expiresInSeconds,
    'user': user.toJson(),
  };
}

class UserProfile {
  UserProfile({
    required this.id,
    required this.displayName,
    required this.phoneNumber,
    required this.roles,
  });

  final int id;
  final String displayName;
  final String phoneNumber;
  final List<String> roles;

  factory UserProfile.fromJson(Map<String, dynamic> json) => UserProfile(
    id: (json['id'] as num?)?.toInt() ?? 0,
    displayName: json['displayName']?.toString() ?? '',
    phoneNumber: json['phoneNumber']?.toString() ?? '',
    roles: (json['roles'] as List? ?? const [])
        .map((item) => item.toString())
        .toList(),
  );

  Map<String, dynamic> toJson() => {
    'id': id,
    'displayName': displayName,
    'phoneNumber': phoneNumber,
    'roles': roles,
  };
}

class AgentLoginRequest {
  AgentLoginRequest({
    required this.phoneNumber,
    required this.pin,
  });

  final String phoneNumber;
  final String pin;

  Map<String, dynamic> toJson() => {
    'phoneNumber': phoneNumber,
    'pin': pin,
  };
}

class AgentApplicationRequest {
  const AgentApplicationRequest({
    required this.displayName,
    required this.phoneNumber,
    required this.pin,
    required this.city,
    required this.territory,
    required this.idDocumentType,
    required this.idDocumentNumber,
  });

  final String displayName;
  final String phoneNumber;
  final String pin;
  final String city;
  final String territory;
  final String idDocumentType;
  final String idDocumentNumber;

  Map<String, dynamic> toJson() => {
    'displayName': displayName,
    'phoneNumber': phoneNumber,
    'pin': pin,
    'city': city,
    'territory': territory,
    'idDocumentType': idDocumentType,
    'idDocumentNumber': idDocumentNumber,
  };
}
