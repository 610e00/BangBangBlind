import 'package:flutter_test/flutter_test.dart';
import 'package:bangbangblind_demo/edge_tts_guard.dart';

void main() {
  // 統一使用正常情境作為預設值。
  // 每項測試只修改需要驗證的條件。
  bool check({
    bool ttsReady = true,
    bool redHold = false,
    int greenConsecutive = 2,
    bool greenAnnounced = true,
    bool greenIntroFinished = true,
    bool tracking = true,
    int mismatchCount = 0,
    int? lastGoodCountdownAtMs = 9000,
    int nowMs = 10000,
  }) {
    return EdgeTtsGuard.canAnnounceCountdown(
      ttsReady: ttsReady,
      redHold: redHold,
      greenConsecutive: greenConsecutive,
      greenAnnounced: greenAnnounced,
      greenIntroFinished: greenIntroFinished,
      tracking: tracking,
      mismatchCount: mismatchCount,
      lastGoodCountdownAtMs: lastGoodCountdownAtMs,
      nowMs: nowMs,
    );
  }

  test('A：沒有確認綠燈，不允許倒數語音', () {
    expect(
      check(
        greenConsecutive: 0,
        greenAnnounced: false,
        greenIntroFinished: false,
      ),
      isFalse,
    );
  });

  test('B：綠燈確認且倒數正常，允許倒數語音', () {
    expect(check(), isTrue);
  });

  test('C：CNN 誤判造成 mismatch，不允許倒數語音', () {
    expect(check(mismatchCount: 1), isFalse);
  });

  test('D：紅燈保留時，不允許倒數語音', () {
    expect(check(redHold: true), isFalse);
  });

  test('E：尚未進入 tracking，不允許倒數語音', () {
    expect(check(tracking: false), isFalse);
  });

  test('F：超過 3 秒沒有有效更新，不允許倒數語音', () {
    expect(check(lastGoodCountdownAtMs: 6000), isFalse);
  });

  test('G：綠燈語音尚未播完，不允許倒數語音', () {
    expect(check(greenIntroFinished: false), isFalse);
  });

  test('H：TTS 尚未準備完成，不允許倒數語音', () {
    expect(check(ttsReady: false), isFalse);
  });

  test('I：沒有有效倒數紀錄，不允許倒數語音', () {
    // 明確傳入 null，避免 Dart 預設參數無法區分省略與 null。
    final result = EdgeTtsGuard.canAnnounceCountdown(
      ttsReady: true,
      redHold: false,
      greenConsecutive: 2,
      greenAnnounced: true,
      greenIntroFinished: true,
      tracking: true,
      mismatchCount: 0,
      lastGoodCountdownAtMs: null,
      nowMs: 10000,
    );

    expect(result, isFalse);
  });

  test('J：時間順序異常，不允許倒數語音', () {
    expect(check(lastGoodCountdownAtMs: 11000), isFalse);
  });

  test('K：有效資料恰好 3 秒，仍允許倒數語音', () {
    expect(check(lastGoodCountdownAtMs: 7000), isTrue);
  });

  test('L：綠燈只偵測到一次，不允許倒數語音', () {
    expect(check(greenConsecutive: 1), isFalse);
  });
}
