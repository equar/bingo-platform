import 'package:bingo_player/src/core/localization/ethiopian_date_time.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  group('Ethiopian calendar', () {
    test('New Year and leap-year Pagume boundaries', () {
      expect(EthiopianDateTime.calendarDate(DateTime.utc(2023, 9, 11)), (
        year: 2015,
        month: 13,
        day: 6,
      ));
      expect(EthiopianDateTime.calendarDate(DateTime.utc(2023, 9, 12)), (
        year: 2016,
        month: 1,
        day: 1,
      ));
      expect(EthiopianDateTime.calendarDate(DateTime.utc(2024, 9, 10)), (
        year: 2016,
        month: 13,
        day: 5,
      ));
      expect(EthiopianDateTime.calendarDate(DateTime.utc(2024, 9, 11)), (
        year: 2017,
        month: 1,
        day: 1,
      ));
    });

    test('every month boundary including Gregorian February 29', () {
      final start = DateTime.utc(2023, 9, 12);
      for (var month = 1; month <= 13; month++) {
        expect(
          EthiopianDateTime.calendarDate(
            start.add(Duration(days: (month - 1) * 30)),
          ),
          (year: 2016, month: month, day: 1),
        );
      }
      expect(EthiopianDateTime.calendarDate(DateTime.utc(2024, 2, 29)), (
        year: 2016,
        month: 6,
        day: 21,
      ));
    });

    test(
      'date rolls over at Ethiopian midnight, independent of input offset',
      () {
        expect(
          EthiopianDateTime.date(DateTime.parse('2024-09-10T20:59:00Z')),
          '5 Pagume 2016 E.C.',
        );
        expect(
          EthiopianDateTime.date(DateTime.parse('2024-09-10T21:00:00Z')),
          '1 Meskerem 2017 E.C.',
        );
        expect(
          EthiopianDateTime.date(DateTime.parse('2024-09-11T00:00:00+03:00')),
          '1 Meskerem 2017 E.C.',
        );
      },
    );

    test('Amharic month and era', () {
      expect(
        EthiopianDateTime.date(DateTime.utc(2024, 9, 11), languageCode: 'am'),
        '1 መስከረም 2017 ዓ.ም.',
      );
    });
  });

  group('Ethiopian clock', () {
    test('sunrise, daytime, sunset and midnight', () {
      final cases = <int, String>{
        3: '12:05 daytime',
        4: '1:05 daytime',
        9: '6:05 daytime',
        14: '11:05 daytime',
        15: '12:05 nighttime',
        16: '1:05 nighttime',
        21: '6:05 nighttime',
        2: '11:05 nighttime',
      };
      for (final entry in cases.entries) {
        expect(
          EthiopianDateTime.time(DateTime.utc(2024, 9, 11, entry.key, 5)),
          entry.value,
        );
      }
    });

    test('Amharic periods and combined formatting', () {
      expect(
        EthiopianDateTime.dateTime(
          DateTime.utc(2024, 9, 11, 4, 30),
          languageCode: 'am',
        ),
        '1 መስከረም 2017 ዓ.ም. · 1:30 ቀን',
      );
      expect(
        EthiopianDateTime.time(
          DateTime.utc(2024, 9, 11, 16, 30),
          languageCode: 'am',
        ),
        '1:30 ሌሊት',
      );
    });
  });
}
