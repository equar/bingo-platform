import 'package:bingo_app/src/core/models/page_result.dart';

class SupportTicketSummary {
  const SupportTicketSummary({
    required this.id,
    required this.ticketNumber,
    required this.subject,
    required this.category,
    required this.priority,
    required this.status,
    required this.createdAt,
  });

  final int id;
  final String ticketNumber;
  final String subject;
  final String category;
  final String priority;
  final String status;
  final DateTime createdAt;

  factory SupportTicketSummary.fromJson(Map<String, dynamic> json) {
    return SupportTicketSummary(
      id: (json['id'] as num?)?.toInt() ?? 0,
      ticketNumber: json['ticketNumber']?.toString() ?? '',
      subject: json['subject']?.toString() ?? '',
      category: json['category']?.toString() ?? 'OTHER',
      priority: json['priority']?.toString() ?? 'NORMAL',
      status: json['status']?.toString() ?? 'OPEN',
      createdAt: DateTime.tryParse(json['createdAt']?.toString() ?? '') ??
          DateTime.now(),
    );
  }
}

class SupportTicketCreateRequest {
  const SupportTicketCreateRequest({
    required this.subject,
    required this.description,
    required this.category,
    required this.priority,
  });

  final String subject;
  final String description;
  final String category;
  final String priority;

  Map<String, dynamic> toJson() => {
        'subject': subject,
        'description': description,
        'category': category,
        'priority': priority,
      };
}

typedef SupportTicketPage = PageResult<SupportTicketSummary>;
