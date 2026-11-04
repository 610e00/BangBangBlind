import 'package:flutter_test/flutter_test.dart';

import '../lib/edge_countdown_tracker.dart';

void main() {
  test('倒數可隨時間遞減，並忽略 38、8 等異常值', () {
    final tracker = EdgeCountdownTracker();

    tracker.observe(seconds: 33, capturedAtMs: 0, nowMs: 100);

    expect(tracker.state, EdgeTrackingState.verifying);

    tracker.observe(seconds: 32, capturedAtMs: 1200, nowMs: 1300);

    expect(tracker.state, EdgeTrackingState.tracking);
    expect(tracker.predictedAt(1300), 31);

    tracker.observe(seconds: 38, capturedAtMs: 2400, nowMs: 2500);

    expect(tracker.mismatchCount, 1);
    expect(tracker.predictedAt(2500), 30);

    tracker.observe(seconds: 8, capturedAtMs: 3600, nowMs: 3700);

    expect(tracker.mismatchCount, 2);
    expect(tracker.predictedAt(3700), 29);

    // 短暫失去號誌，Tracker 仍可使用本地時鐘推算。
    tracker.tick(6000);
    expect(tracker.state, EdgeTrackingState.tracking);
    expect(tracker.predictedAt(6000), 27);

    // 超過 8 秒沒有有效觀測，Tracker 才失效。
    tracker.tick(10000);
    expect(tracker.state, EdgeTrackingState.lost);
    expect(tracker.predictedAt(10000), isNull);
  });

  test('單一讀數不能直接啟動追蹤', () {
    final tracker = EdgeCountdownTracker();

    tracker.observe(seconds: 88, capturedAtMs: 0, nowMs: 100);

    expect(tracker.state, EdgeTrackingState.verifying);
    expect(tracker.predictedAt(100), isNull);
  });
}
