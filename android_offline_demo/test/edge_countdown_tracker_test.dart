import 'package:flutter_test/flutter_test.dart';

import 'package:bangbangblind_demo/edge_countdown_tracker.dart';

void main() {
  test('接受結果不以 mismatchCount 為準，拒絕無效與過期影格', () {
    final tracker = EdgeCountdownTracker();
    expect(tracker.observe(seconds: 33, capturedAtMs: 0, nowMs: 100), isFalse);
    expect(
      tracker.observe(seconds: 32, capturedAtMs: 1200, nowMs: 1300),
      isTrue,
    );
    expect(
      tracker.observe(seconds: 0, capturedAtMs: 1400, nowMs: 1400),
      isFalse,
    );
    expect(tracker.mismatchCount, 0);
    expect(
      tracker.observe(seconds: 91, capturedAtMs: 1500, nowMs: 1500),
      isFalse,
    );
    expect(
      tracker.observe(seconds: 31, capturedAtMs: 2200, nowMs: 2000),
      isFalse,
    );
    expect(
      tracker.observe(seconds: 32, capturedAtMs: 1200, nowMs: 2100),
      isFalse,
    );
    expect(
      tracker.observe(seconds: 32, capturedAtMs: 1100, nowMs: 2100),
      isFalse,
    );
    expect(
      tracker.observe(seconds: 38, capturedAtMs: 2400, nowMs: 2500),
      isFalse,
    );
    expect(
      tracker.observe(seconds: 30, capturedAtMs: 2500, nowMs: 2600),
      isTrue,
    );
    expect(
      tracker.observe(seconds: 29, capturedAtMs: 2600, nowMs: 11000),
      isFalse,
    );
    expect(tracker.state, EdgeTrackingState.lost);
    tracker.reset();
    expect(
      tracker.observe(seconds: 20, capturedAtMs: 12000, nowMs: 12000),
      isFalse,
    );
  });

  // ============================================================
  // Test 1：正常倒數，忽略 38、8 等異常值
  // ============================================================
  test('倒數可隨時間遞減，並忽略 38、8 等異常值', () {
    final tracker = EdgeCountdownTracker();

    tracker.observe(seconds: 33, capturedAtMs: 0, nowMs: 100);

    expect(tracker.state, EdgeTrackingState.verifying);

    tracker.observe(seconds: 32, capturedAtMs: 1200, nowMs: 1300);

    expect(tracker.state, EdgeTrackingState.tracking);
    expect(tracker.predictedAt(1300), 31);

    // 異常值 38，不應更新追蹤基準。
    tracker.observe(seconds: 38, capturedAtMs: 2400, nowMs: 2500);

    expect(tracker.mismatchCount, 1);
    expect(tracker.predictedAt(2500), 30);

    // 異常值 8，也不應更新追蹤基準。
    tracker.observe(seconds: 8, capturedAtMs: 3600, nowMs: 3700);

    expect(tracker.mismatchCount, 2);
    expect(tracker.predictedAt(3700), 29);

    // 短暫失去號誌，Tracker 仍可利用本地時鐘推算。
    tracker.tick(6000);

    expect(tracker.state, EdgeTrackingState.tracking);
    expect(tracker.predictedAt(6000), 27);

    // 超過 8 秒沒有有效觀測，Tracker 失效。
    tracker.tick(10000);

    expect(tracker.state, EdgeTrackingState.lost);
    expect(tracker.predictedAt(10000), isNull);
  });

  // ============================================================
  // Test 2：單一讀數不能直接啟動追蹤
  // ============================================================
  test('單一讀數不能直接啟動追蹤', () {
    final tracker = EdgeCountdownTracker();

    tracker.observe(seconds: 88, capturedAtMs: 0, nowMs: 100);

    expect(tracker.state, EdgeTrackingState.verifying);
    expect(tracker.predictedAt(100), isNull);
  });

  // ============================================================
  // Test 3：數字 5 被 CNN 誤判為 8
  // ============================================================
  test('數字 5 誤判為 8 時，Tracker 拒絕異常讀數', () {
    final tracker = EdgeCountdownTracker();

    // 第 1 秒：CNN 辨識到 7，開始驗證。
    tracker.observe(seconds: 7, capturedAtMs: 1000, nowMs: 1000);

    expect(tracker.state, EdgeTrackingState.verifying);

    // 第 2 秒：CNN 辨識到 6，建立追蹤。
    tracker.observe(seconds: 6, capturedAtMs: 2000, nowMs: 2000);

    expect(tracker.state, EdgeTrackingState.tracking);
    expect(tracker.mismatchCount, 0);

    // 第 3 秒：實際是 5，但 CNN 誤判為 8。
    tracker.observe(seconds: 8, capturedAtMs: 3000, nowMs: 3000);

    // Tracker 應拒絕 8，記錄一次異常。
    expect(tracker.state, EdgeTrackingState.tracking);
    expect(tracker.mismatchCount, 1);

    // 本地時鐘的推估值仍然是 5。
    expect(tracker.predictedAt(3000), 5);

    // 第 4 秒：CNN 恢復正確，辨識到 4。
    tracker.observe(seconds: 4, capturedAtMs: 4000, nowMs: 4000);

    // 異常次數清零，恢復正常追蹤。
    expect(tracker.mismatchCount, 0);
    expect(tracker.state, EdgeTrackingState.tracking);
    expect(tracker.predictedAt(4000), 4);
  });
}
