enum EdgeTrackingState { searching, verifying, tracking, lost }

class EdgeCountdownTracker {
  static const int maxSeconds = 90;
  static const int candidateTimeoutMs = 4000;
  static const int trackingTimeoutMs = 8000;
  static const int allowedDifference = 2;

  EdgeTrackingState state = EdgeTrackingState.searching;

  int? _candidateSeconds;
  int? _candidateAtMs;

  int? _anchorSeconds;
  int? _anchorAtMs;
  int? _lastValidAtMs;
  int? _lastObservedAtMs;

  int mismatchCount = 0;

  int? get anchorSeconds => _anchorSeconds;
  int? get lastValidAtMs => _lastValidAtMs;

  int? predictedAt(int nowMs) {
    if (_anchorSeconds == null || _anchorAtMs == null) {
      return null;
    }

    final elapsed = nowMs - _anchorAtMs!;

    final deduction = elapsed <= 0 ? 0 : (elapsed + 999) ~/ 1000;

    final remaining = _anchorSeconds! - deduction;

    return remaining > 0 ? remaining : 0;
  }

  void reset({EdgeTrackingState nextState = EdgeTrackingState.searching}) {
    _candidateSeconds = null;
    _candidateAtMs = null;
    _anchorSeconds = null;
    _anchorAtMs = null;
    _lastValidAtMs = null;
    _lastObservedAtMs = null;
    mismatchCount = 0;
    state = nextState;
  }

  /// Returns true only when this observation establishes or updates tracking.
  bool observe({
    required int seconds,
    required int capturedAtMs,
    required int nowMs,
  }) {
    tick(nowMs);

    // 避免使用過期或時間順序異常的影格。
    if (capturedAtMs > nowMs || nowMs - capturedAtMs > trackingTimeoutMs) {
      return false;
    }

    if (_lastObservedAtMs != null && capturedAtMs <= _lastObservedAtMs!) {
      return false;
    }

    if (seconds < 1 || seconds > maxSeconds) {
      return false;
    }

    _lastObservedAtMs = capturedAtMs;

    if (_anchorSeconds == null) {
      if (_candidateSeconds == null ||
          _candidateAtMs == null ||
          capturedAtMs < _candidateAtMs! ||
          capturedAtMs - _candidateAtMs! > candidateTimeoutMs) {
        _candidateSeconds = seconds;
        _candidateAtMs = capturedAtMs;
        state = EdgeTrackingState.verifying;
        return false;
      }

      final elapsed = capturedAtMs - _candidateAtMs!;

      final expected = _candidateSeconds! - ((elapsed + 999) ~/ 1000);

      final reasonable =
          (seconds - expected).abs() <= allowedDifference &&
          seconds <= _candidateSeconds!;

      if (!reasonable) {
        _candidateSeconds = seconds;
        _candidateAtMs = capturedAtMs;
        state = EdgeTrackingState.verifying;
        return false;
      }

      _anchorSeconds = seconds;
      _anchorAtMs = capturedAtMs;
      _lastValidAtMs = nowMs;
      mismatchCount = 0;
      state = EdgeTrackingState.tracking;
      return true;
    }

    final predicted = predictedAt(capturedAtMs);
    if (predicted == null) return false;

    final difference = (seconds - predicted).abs();

    // 已建立倒數後，不接受大幅增加或減少的秒數。
    if (difference > allowedDifference) {
      mismatchCount++;
      return false;
    }

    mismatchCount = 0;
    _lastValidAtMs = nowMs;

    // 差異小時使用本地時鐘繼續倒數，避免畫面抖動。
    if (difference >= 2) {
      _anchorSeconds = seconds;
      _anchorAtMs = capturedAtMs;
    }

    state = EdgeTrackingState.tracking;
    return true;
  }

  void tick(int nowMs) {
    if (state == EdgeTrackingState.verifying) {
      if (_candidateAtMs != null &&
          nowMs - _candidateAtMs! > candidateTimeoutMs) {
        reset(nextState: EdgeTrackingState.lost);
      }
      return;
    }

    if (state != EdgeTrackingState.tracking) return;

    if (_lastValidAtMs == null ||
        nowMs - _lastValidAtMs! > trackingTimeoutMs ||
        predictedAt(nowMs) == 0) {
      reset(nextState: EdgeTrackingState.lost);
    }
  }
}
