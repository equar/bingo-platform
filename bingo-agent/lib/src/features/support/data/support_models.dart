import 'package:bingo_agent/src/core/models/page_response.dart';

class SupportMessage {
  SupportMessage({required this.id, required this.authorUserId, required this.body, required this.createdAt});

  final int id;
  final int authorUserId;
  final String body;
  final DateTime createdAt;

  factory SupportMessage.fromJson(Map<String, dynamic> json) => SupportMessage(
        id: (json['id'] as num?)?.toInt() ?? 0,
        authorUserId: (json['authorUserId'] as num?)?.toInt() ?? 0,
        body: json['body']?.toString() ?? '',
        createdAt: DateTime.tryParse(json['createdAt']?.toString() ?? '') ?? DateTime.now(),
      );
}

class SupportNote {
  SupportNote({required this.id, required this.authorUserId, required this.body, required this.createdAt});

  final int id;
  final int authorUserId;
  final String body;
  final DateTime createdAt;

  factory SupportNote.fromJson(Map<String, dynamic> json) => SupportNote(
        id: (json['id'] as num?)?.toInt() ?? 0,
        authorUserId: (json['authorUserId'] as num?)?.toInt() ?? 0,
        body: json['body']?.toString() ?? '',
        createdAt: DateTime.tryParse(json['createdAt']?.toString() ?? '') ?? DateTime.now(),
      );
}

class SupportTicketSummary {
  SupportTicketSummary({required this.id, required this.ticketNumber, required this.subject, required this.category, required this.priority, required this.status, required this.assignedToUserId, required this.linkedEntityType, required this.linkedEntityId, required this.createdAt, required this.updatedAt});

  final int id;
  final String ticketNumber;
  final String subject;
  final String category;
  final String priority;
  final String status;
  final int? assignedToUserId;
  final String? linkedEntityType;
  final int? linkedEntityId;
  final DateTime createdAt;
  final DateTime updatedAt;

  factory SupportTicketSummary.fromJson(Map<String, dynamic> json) => SupportTicketSummary(
        id: (json['id'] as num?)?.toInt() ?? 0,
        ticketNumber: json['ticketNumber']?.toString() ?? '',
        subject: json['subject']?.toString() ?? '',
        category: json['category']?.toString() ?? '',
        priority: json['priority']?.toString() ?? '',
        status: json['status']?.toString() ?? '',
        assignedToUserId: (json['assignedToUserId'] as num?)?.toInt(),
        linkedEntityType: json['linkedEntityType']?.toString(),
        linkedEntityId: (json['linkedEntityId'] as num?)?.toInt(),
        createdAt: DateTime.tryParse(json['createdAt']?.toString() ?? '') ?? DateTime.now(),
        updatedAt: DateTime.tryParse(json['updatedAt']?.toString() ?? '') ?? DateTime.now(),
      );
}

class SupportTicketDetail extends SupportTicketSummary {
  SupportTicketDetail({required super.id, required super.ticketNumber, required super.subject, required super.category, required super.priority, required super.status, required super.assignedToUserId, required super.linkedEntityType, required super.linkedEntityId, required super.createdAt, required super.updatedAt, required this.requesterDisplayName, required this.description, required this.linkedEntityReference, required this.resolutionSummary, required this.messages});

  final String requesterDisplayName;
  final String description;
  final String? linkedEntityReference;
  final String? resolutionSummary;
  final List<SupportMessage> messages;

  factory SupportTicketDetail.fromJson(Map<String, dynamic> json) => SupportTicketDetail(
        id: (json['id'] as num?)?.toInt() ?? 0,
        ticketNumber: json['ticketNumber']?.toString() ?? '',
        subject: json['subject']?.toString() ?? '',
        category: json['category']?.toString() ?? '',
        priority: json['priority']?.toString() ?? '',
        status: json['status']?.toString() ?? '',
        assignedToUserId: (json['assignedToUserId'] as num?)?.toInt(),
        linkedEntityType: json['linkedEntityType']?.toString(),
        linkedEntityId: (json['linkedEntityId'] as num?)?.toInt(),
        createdAt: DateTime.tryParse(json['createdAt']?.toString() ?? '') ?? DateTime.now(),
        updatedAt: DateTime.tryParse(json['updatedAt']?.toString() ?? '') ?? DateTime.now(),
        requesterDisplayName: json['requesterDisplayName']?.toString() ?? '',
        description: json['description']?.toString() ?? '',
        linkedEntityReference: json['linkedEntityReference']?.toString(),
        resolutionSummary: json['resolutionSummary']?.toString(),
        messages: (json['messages'] as List? ?? const [])
            .whereType<Map>()
            .map((item) => SupportMessage.fromJson(Map<String, dynamic>.from(item)))
            .toList(),
      );
}

class SupportTicketCreateRequest {
  SupportTicketCreateRequest({required this.subject, required this.description, required this.category, required this.priority});

  final String subject;
  final String description;
  final String category;
  final String priority;

  Map<String, dynamic> toJson() => {'subject': subject, 'description': description, 'category': category, 'priority': priority};
}