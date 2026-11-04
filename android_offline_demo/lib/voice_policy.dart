/// Speech uses confirmed frames, never an inferred countdown.
class VoicePolicy {
  String? _candidate;
  double _since = 0;
  String? _spoken;
  double _spokenAt = -100;
  int? _lastSeconds;

  void reset() {
    _candidate = null;
    _spoken = null;
    _since = 0;
    _spokenAt = -100;
    _lastSeconds = null;
  }

  String? update(String state, int? seconds, double time) {
    if (state == 'green' || state == 'short') {
      if (seconds == null ||
          (_lastSeconds != null && seconds > _lastSeconds! + 2)) {
        state = 'unknown';
      } else {
        _lastSeconds = seconds;
        state = seconds < 15 ? 'short' : 'green';
      }
    }
    if (_candidate != state) {
      _candidate = state;
      _since = time;
    }
    // Announce caution promptly; require sustained confirmation for green.
    final delay = state == 'green'
        ? .6
        : state == 'red'
        ? .13
        : 0.0;
    if (time - _since < delay) return null;
    if (_spoken == state && time - _spokenAt < 8) return null;
    _spoken = state;
    _spokenAt = time;
    switch (state) {
      case 'red':
        return '紅燈，請等待。';
      case 'short':
        return '綠燈剩餘不到十五秒，時間不足，請等待下一輪。';
      case 'green':
        return '偵測到綠燈，剩餘 $seconds 秒，請確認路況。';
      default:
        return '尚無法確認燈號或秒數，請等待。';
    }
  }
}
