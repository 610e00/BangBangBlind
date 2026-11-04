import 'package:flutter_test/flutter_test.dart';
import 'package:bangbangblind_demo/voice_policy.dart';

void main() {
  test(
    '15 stays green, 14 and single digits warn, uncertainty cancels permission',
    () {
      final p = VoicePolicy();
      expect(p.update('green', 15, 0), isNull);
      expect(
        p.update('green', 15, .7),
        contains('15'),
      );
      expect(p.update('short', 14, .8), contains('時間不足'));
      expect(p.update('short', 9, 1), isNull);
      expect(p.update('unknown', null, 1.1), contains('請等待'));
      expect(p.update('green', 80, 1.2), isNull);
      p.reset();
      expect(p.update('red', null, 0), isNull);
      expect(p.update('red', null, .2), contains('紅燈'));
    },
  );
  test('repeat and reset do not retain stale countdown', () {
    final p = VoicePolicy();
    expect(p.update('short', 3, 0), contains('時間不足'));
    expect(p.update('short', 2, 1), isNull);
    expect(p.update('unknown', null, 2), contains('無法確認'));
    p.reset();
    expect(p.update('green', 30, 0), isNull);
    expect(p.update('green', 29, 1), contains('29'));
  });
}
