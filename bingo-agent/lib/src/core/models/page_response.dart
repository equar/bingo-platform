class PageResponse<T> {
  PageResponse({required this.content, required this.page, required this.totalPages, required this.totalElements});

  final List<T> content;
  final int page;
  final int totalPages;
  final int totalElements;

  factory PageResponse.fromJson(Map<String, dynamic> json, T Function(Map<String, dynamic>) fromJson) {
    return PageResponse(
      content: (json['content'] as List? ?? const [])
          .whereType<Map>()
          .map((item) => fromJson(Map<String, dynamic>.from(item)))
          .toList(),
      page: (json['page'] as num?)?.toInt() ??
          (json['pageable']?['pageNumber'] as num?)?.toInt() ??
          (json['number'] as num?)?.toInt() ??
          0,
      totalPages: (json['totalPages'] as num?)?.toInt() ?? 1,
      totalElements: (json['totalElements'] as num?)?.toInt() ?? 0,
    );
  }
}