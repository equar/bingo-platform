import 'dart:convert';

class AuthResponse {
  const AuthResponse({
    required this.accessToken,
    required this.tokenType,
    required this.expiresInSeconds,
    required this.user,
  });

  final String accessToken;
  final String tokenType;
  final int expiresInSeconds;
  final UserProfile user;

  factory AuthResponse.fromJson(Map<String, dynamic> json) {
    return AuthResponse(
      accessToken: json['accessToken']?.toString() ?? '',
      tokenType: json['tokenType']?.toString() ?? '',
      expiresInSeconds: (json['expiresInSeconds'] as num?)?.toInt() ?? 0,
      user: UserProfile.fromJson(Map<String, dynamic>.from(json['user'] as Map)),
    );
  }
}

class UserProfile {
  const UserProfile({
    required this.id,
    required this.displayName,
    required this.phoneNumber,
    required this.roles,
    required this.status,
    required this.createdAt,
    required this.updatedAt,
  });

  final int id;
  final String displayName;
  final String phoneNumber;
  final List<String> roles;
  final String status;
  final DateTime createdAt;
  final DateTime updatedAt;

  bool get isPlayer => roles.contains('PLAYER');

  factory UserProfile.fromJson(Map<String, dynamic> json) {
    return UserProfile(
      id: (json['id'] as num?)?.toInt() ?? 0,
      displayName: json['displayName']?.toString() ?? '',
      phoneNumber: json['phoneNumber']?.toString() ?? json['normalizedPhone']?.toString() ?? '',
      roles: (json['roles'] as List? ?? const []).map((item) => item.toString()).toList(),
      status: json['status']?.toString() ?? '',
      createdAt: DateTime.tryParse(json['createdAt']?.toString() ?? '') ?? DateTime.fromMillisecondsSinceEpoch(0),
      updatedAt: DateTime.tryParse(json['updatedAt']?.toString() ?? '') ?? DateTime.fromMillisecondsSinceEpoch(0),
    );
  }

  Map<String, dynamic> toJson() => {
        'id': id,
        'displayName': displayName,
        'phoneNumber': phoneNumber,
        'roles': roles,
        'status': status,
        'createdAt': createdAt.toIso8601String(),
        'updatedAt': updatedAt.toIso8601String(),
      };

  String toPersistedJson() => jsonEncode(toJson());
}

class AuthSession {
  const AuthSession({required this.accessToken, required this.expiresAt, required this.user});

  final String accessToken;
  final DateTime expiresAt;
  final UserProfile user;

  bool get isExpired => DateTime.now().toUtc().isAfter(expiresAt.toUtc());

  Map<String, dynamic> toJson() => {
        'accessToken': accessToken,
        'expiresAt': expiresAt.toIso8601String(),
        'user': user.toJson(),
      };

  String toPersistedJson() => jsonEncode(toJson());

  factory AuthSession.fromPersistedJson(String jsonString) {
    final json = jsonDecode(jsonString) as Map<String, dynamic>;
    return AuthSession(
      accessToken: json['accessToken']?.toString() ?? '',
      expiresAt: DateTime.tryParse(json['expiresAt']?.toString() ?? '') ?? DateTime.fromMillisecondsSinceEpoch(0),
      user: UserProfile.fromJson(Map<String, dynamic>.from(json['user'] as Map)),
    );
  }
}

class LoginRequest {
  const LoginRequest({required this.phoneNumber, required this.password});

  final String phoneNumber;
  final String password;

  Map<String, dynamic> toJson() => {'phoneNumber': phoneNumber, 'password': password};
}

class RegisterRequest {
  const RegisterRequest({required this.displayName, required this.phoneNumber, required this.password});

  final String displayName;
  final String phoneNumber;
  final String password;

  Map<String, dynamic> toJson() => {'displayName': displayName, 'phoneNumber': phoneNumber, 'password': password};
}