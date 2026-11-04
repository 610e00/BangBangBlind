
class EdgeTtsGuard {
  static const int staleLimitMs = 3000;

  /// 判斷目前是否允許產生新的倒數秒數語音。
  ///
  /// 此函式只負責語音准入，不影響 YOLO、CNN 或 Tracker。
  static bool canAnnounceCountdown({
    required bool ttsReady,
    required bool redHold,
    required int greenConsecutive,
    required bool greenAnnounced,
    required bool greenIntroFinished,
    required bool tracking,
    required int mismatchCount,
    required int? lastGoodCountdownAtMs,
    required int nowMs,
  }) {
    // TTS 未準備完成或紅燈保留時，禁止播報。
    if (!ttsReady || redHold) {
      return false;
    }

    // Tracker 必須已建立追蹤。
    if (!tracking) {
      return false;
    }

    // 綠燈必須確認，且開場語音已播放完成。
    final greenConfirmed =
        greenConsecutive >= 2 &&
        greenAnnounced &&
        greenIntroFinished;

    if (!greenConfirmed) {
      return false;
    }

    // 最新倒數觀測有異常，不播報。
    if (mismatchCount != 0) {
      return false;
    }

    // 尚未取得有效的倒數更新。
    if (lastGoodCountdownAtMs == null) {
      return false;
    }

    // 防止時間順序異常。
    if (nowMs < lastGoodCountdownAtMs) {
      return false;
    }

    // 超過 3 秒未取得有效更新，禁止播報。
    if (nowMs - lastGoodCountdownAtMs > staleLimitMs) {
      return false;
    }

    return true;
  }
}
