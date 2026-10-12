class PageResult<T> {
  const PageResult({
    required this.content,
    required this.page,
    required this.size,
    required this.totalElements,
    required this.totalPages,
    required this.first,
    required this.last,
  });

  final List<T> content;
  final int page;
  final int size;
  final int totalElements;
  final int totalPages;
  final bool first;
  final bool last;

  factory PageResult.fromJson(Map<String, dynamic> json, T Function(Map<String, dynamic>) mapper) {
    return PageResult(
      content: (json['content'] as List? ?? const [])
          .whereType<Map>()
          .map((item) => mapper(Map<String, dynamic>.from(item)))
          .toList(),
      page: (json['page'] as num?)?.toInt() ?? 0,
      size: (json['size'] as num?)?.toInt() ?? 0,
      totalElements: (json['totalElements'] as num?)?.toInt() ?? 0,
      totalPages: (json['totalPages'] as num?)?.toInt() ?? 0,
      first: json['first'] == true,
      last: json['last'] == true,
    );
  }
}