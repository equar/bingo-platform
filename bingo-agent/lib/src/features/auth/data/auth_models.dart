class AuthResponse {
  AuthResponse({required this.accessToken, required this.tokenType, required this.expiresInSeconds, required this.user});

  final String accessToken;
  final String tokenType;
  final int expiresInSeconds;
  final UserProfile user;

  factory AuthResponse.fromJson(Map<String, dynamic> json) => AuthResponse(
        accessToken: json['accessToken']?.toString() ?? '',
        tokenType: json['tokenType']?.toString() ?? 'Bearer',
        expiresInSeconds: (json['expiresInSeconds'] as num?)?.toInt() ?? 0,
        user: UserProfile.fromJson(Map<String, dynamic>.from(json['user'] as Map? ?? const {})),
      );

  Map<String, dynamic> toJson() => {
        'accessToken': accessToken,
        'tokenType': tokenType,
        'expiresInSeconds': expiresInSeconds,
        'user': user.toJson(),
      };
}

class UserProfile {
  UserProfile({required this.id, required this.displayName, required this.phoneNumber, required this.roles});

  final int id;
  final String displayName;
  final String phoneNumber;
  final List<String> roles;

  factory UserProfile.fromJson(Map<String, dynamic> json) => UserProfile(
        id: (json['id'] as num?)?.toInt() ?? 0,
        displayName: json['displayName']?.toString() ?? '',
        phoneNumber: json['phoneNumber']?.toString() ?? '',
        roles: (json['roles'] as List? ?? const []).map((item) => item.toString()).toList(),
      );

  Map<String, dynamic> toJson() => {
        'id': id,
        'displayName': displayName,
        'phoneNumber': phoneNumber,
        'roles': roles,
      };
}

class AgentLoginRequest {
  AgentLoginRequest({required this.phoneNumber, required this.password, required this.pin});

  final String phoneNumber;
  final String password;
  final String pin;

  Map<String, dynamic> toJson() => {'phoneNumber': phoneNumber, 'password': password, 'pin': pin};
}