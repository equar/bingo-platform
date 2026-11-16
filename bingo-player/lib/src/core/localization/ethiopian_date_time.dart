/// Presentation of API instants using Ethiopia's calendar and clock.
/// Keep DateTime/ISO-8601 for transport, comparisons and durations.
abstract final class EthiopianDateTime {
  static String preferredCalendar = 'AUTO';

  static bool usesEthiopianCalendar(String languageCode) =>
      preferredCalendar == 'ETHIOPIAN' ||
      (preferredCalendar == 'AUTO' &&
          (languageCode == 'am' || languageCode == 'ti'));
  static const _englishMonths = [
    'Meskerem',
    'Tikimt',
    'Hidar',
    'Tahsas',
    'Tir',
    'Yekatit',
    'Megabit',
    'Miazia',
    'Ginbot',
    'Sene',
    'Hamle',
    'Nehase',
    'Pagume',
  ];
  static const _amharicMonths = [
    'መስከረም',
    'ጥቅምት',
    'ኅዳር',
    'ታኅሣሥ',
    'ጥር',
    'የካቲት',
    'መጋቢት',
    'ሚያዝያ',
    'ግንቦት',
    'ሰኔ',
    'ሐምሌ',
    'ነሐሴ',
    'ጳጉሜ',
  ];
  static const _gregorianEnglishMonths = [
    'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
    'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec',
  ];
  static const _gregorianAmharicMonths = [
    'ጃንዩወሪ', 'ፌብሩወሪ', 'ማርች', 'ኤፕሪል', 'ሜይ', 'ጁን',
    'ጁላይ', 'ኦገስት', 'ሴፕቴምበር', 'ኦክቶበር', 'ኖቬምበር', 'ዲሴምበር',
  ];

  // A UTC-backed wall-clock value prevents the device timezone from affecting
  // the calendar date or hour. This value is for formatting only.
  static DateTime _ethiopiaWallTime(DateTime instant) =>
      instant.toUtc().add(const Duration(hours: 3));

  /// Julian-day conversion by Beyene/Kudlek, including leap-day Pagume 6.
  /// Reference: https://www.geez.org/Calendars/
  static ({int year, int month, int day}) calendarDate(DateTime instant) {
    final wall = _ethiopiaWallTime(instant);
    final julianDay =
        DateTime.utc(
          wall.year,
          wall.month,
          wall.day,
        ).difference(DateTime.utc(1970)).inDays +
        2440588;
    final days = julianDay - 1723856;
    final remainder = days % 1461;
    final dayOfYear = remainder % 365 + 365 * (remainder ~/ 1460);
    return (
      year: 4 * (days ~/ 1461) + remainder ~/ 365 - remainder ~/ 1460,
      month: dayOfYear ~/ 30 + 1,
      day: dayOfYear % 30 + 1,
    );
  }

  static String date(DateTime instant, {String languageCode = 'en'}) {
    if (!usesEthiopianCalendar(languageCode)) {
      final wall = _ethiopiaWallTime(instant);
      final months = languageCode == 'am'
          ? _gregorianAmharicMonths
          : _gregorianEnglishMonths;
      return '${wall.day} ${months[wall.month - 1]} ${wall.year}';
    }
    final value = calendarDate(instant);
    final amharic = languageCode == 'am';
    final months = amharic ? _amharicMonths : _englishMonths;
    final era = amharic ? 'ዓ.ም.' : 'E.C.';
    return '${value.day} ${months[value.month - 1]} ${value.year} $era';
  }

  static String time(DateTime instant, {String languageCode = 'en'}) {
    final wall = _ethiopiaWallTime(instant);
    final hour = (wall.hour + 6) % 12;
    final daytime = wall.hour >= 6 && wall.hour < 18;
    final amharic = languageCode == 'am';
    final period = amharic
        ? (daytime ? 'ቀን' : 'ሌሊት')
        : (daytime ? 'daytime' : 'nighttime');
    return '${hour == 0 ? 12 : hour}:${wall.minute.toString().padLeft(2, '0')} $period';
  }

  static String dateTime(DateTime instant, {String languageCode = 'en'}) =>
      '${date(instant, languageCode: languageCode)} · '
      '${time(instant, languageCode: languageCode)}';
}
