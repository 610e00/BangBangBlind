import 'package:flutter/foundation.dart';
import 'package:flutter_tts/flutter_tts.dart';
import 'package:vibration/vibration.dart';

import 'obstacle_decision.dart';


class ObstacleAlert {
  ObstacleAlert({
    this.enableSideSpeech = true,
  });


  // ==============================
  // 是否開啟側邊語音
  //
  // 現在只有手機單顆震動馬達，
  // 無法真正表達 LEFT / RIGHT，
  // 所以 demo / 測試階段預設 true。
  //
  // 未來接左右外部震動器後，
  // 可改成 false。
  // ==============================

  final bool enableSideSpeech;


  final FlutterTts _flutterTts =
      FlutterTts();


  // ==============================
  // TTS 狀態
  // ==============================

  String? _lastSpokenKey;

  DateTime? _lastSpokenTime;


  // 同一個危險持續很久時，
  // 每隔一段時間才允許再提醒一次。
  //
  // 5 秒目前是暫定值，
  // 後續要使用者測試。
  static const Duration
      _ttsRepeatInterval =
      Duration(
    seconds: 5,
  );


  // ==============================
  // 震動狀態
  // ==============================

  DateTime? _lastVibrationTime;

  int _lastVibrationRiskLevel = 0;


  static const Duration
      _vibrationCooldown =
      Duration(
    milliseconds: 800,
  );


  // ==============================
  // 初始化 TTS
  // ==============================

  Future<void> initialize() async {
    await _flutterTts.setLanguage(
      'zh-TW',
    );

    await _flutterTts.setSpeechRate(
      0.5,
    );

    await _flutterTts.setVolume(
      1.0,
    );

    await _flutterTts.setPitch(
      1.0,
    );
  }


  // ==============================
  // 收到 Decision 結果
  // ==============================

  Future<void> handle(
    ObstacleDecisionResult result,
  ) async {
    // --------------------------------
    // 沒有危險
    // --------------------------------

    if (result.warningType ==
        ObstacleWarningType.none) {
      _resetAlertState();

      await Vibration.cancel();

      return;
    }


    final obstacle =
        result.obstacle;

    if (obstacle == null) {
      return;
    }


    // ==============================
    // 正前方預警
    // ==============================

    if (result.warningType ==
        ObstacleWarningType.frontWarning) {
      await _vibrateFrontWarning();

      return;
    }


    // ==============================
    // 正前方危險
    // ==============================

    if (result.warningType ==
        ObstacleWarningType.frontDanger) {
      await _vibrateFrontDanger();

      await _speakFrontDanger(
        obstacle.objectType,
        result.direction,
      );

      return;
    }


    // ==============================
    // 左 / 右側危險
    // ==============================

    if (result.warningType ==
        ObstacleWarningType.sideDanger) {
      await _vibrateSideDanger(
        result.direction,
      );


      // 現階段手機只有一顆震動馬達，
      // 所以可暫時用語音補足方向資訊。
      if (enableSideSpeech) {
        await _speakSideDanger(
          obstacle.objectType,
          result.direction,
        );
      }
    }
  }


  // ==============================
  // 正前方預警
  // ==============================

  Future<void>
      _vibrateFrontWarning() async {
    await _vibratePattern(
      pattern: const [
        0,
        200,
        700,
        200,
      ],
      riskLevel: 1,
    );
  }


  // ==============================
  // 正前方危險
  // ==============================

  Future<void>
      _vibrateFrontDanger() async {
    await _vibratePattern(
      pattern: const [
        0,
        120,
        100,
        120,
        100,
        120,
      ],
      riskLevel: 3,
    );
  }


  // ==============================
  // 側邊危險
  // ==============================

  Future<void> _vibrateSideDanger(
    ObstacleDirection? direction,
  ) async {
    if (direction ==
        ObstacleDirection.leftCenter) {
      debugPrint(
        '左側障礙物：LEFT vibration',
      );
    } else if (direction ==
        ObstacleDirection.rightCenter) {
      debugPrint(
        '右側障礙物：RIGHT vibration',
      );
    }


    await _vibratePattern(
      pattern: const [
        0,
        300,
      ],
      riskLevel: 2,
    );
  }


  // ==============================
  // 統一處理震動
  //
  // 規則：
  //
  // 相同 / 更低風險
  // → 受到 800ms cooldown 限制
  //
  // 風險升級
  // → 立即震動
  //
  // 例如：
  //
  // frontWarning (1)
  // → frontDanger (3)
  //
  // 不會被前一次 warning
  // 的 cooldown 擋掉。
  // ==============================

  Future<void> _vibratePattern({
    required List<int> pattern,
    required int riskLevel,
  }) async {
    final DateTime now =
        DateTime.now();


    final bool riskIncreased =
        riskLevel >
            _lastVibrationRiskLevel;


    bool cooldownPassed = true;


    if (_lastVibrationTime != null) {
      final Duration difference =
          now.difference(
        _lastVibrationTime!,
      );

      cooldownPassed =
          difference >=
              _vibrationCooldown;
    }


    // 沒有升級，
    // 而且 cooldown 還沒過，
    // 就不重新震。
    if (!riskIncreased &&
        !cooldownPassed) {
      return;
    }


    // 如果是風險升級，
    // 先取消前一個低風險震動，
    // 再立即播放更急迫的震動。
    if (riskIncreased &&
        _lastVibrationTime != null) {
      await Vibration.cancel();
    }


    _lastVibrationTime = now;

    _lastVibrationRiskLevel =
        riskLevel;


    await Vibration.vibrate(
      pattern: pattern,
    );
  }


  // ==============================
  // 正前方危險語音
  // ==============================

  Future<void> _speakFrontDanger(
    String objectType,
    ObstacleDirection? direction,
  ) async {
    final String key =
        'front-'
        '$objectType-'
        '${direction?.name}';


    if (!_canSpeakAgain(key)) {
      return;
    }


    final String obstacleName =
        _getChineseObstacleName(
      objectType,
    );


    await _speak(
      key: key,
      text:
          '前方有$obstacleName，請注意。',
    );
  }


  // ==============================
  // 側邊危險語音
  //
  // 只是目前單馬達階段
  // 暫時補足左右方向。
  // ==============================

  Future<void> _speakSideDanger(
    String objectType,
    ObstacleDirection? direction,
  ) async {
    final String directionText;


    if (direction ==
        ObstacleDirection.leftCenter) {
      directionText = '左前方';
    } else if (direction ==
        ObstacleDirection.rightCenter) {
      directionText = '右前方';
    } else {
      directionText = '側前方';
    }


    final String key =
        'side-'
        '$objectType-'
        '${direction?.name}';


    if (!_canSpeakAgain(key)) {
      return;
    }


    final String obstacleName =
        _getChineseObstacleName(
      objectType,
    );


    await _speak(
      key: key,
      text:
          '$directionText有'
          '$obstacleName，請注意。',
    );
  }


  // ==============================
  // 判斷 TTS 能不能再講
  // ==============================

  bool _canSpeakAgain(
    String key,
  ) {
    // 不同事件
    // → 立即講
    if (_lastSpokenKey != key) {
      return true;
    }


    // 理論上很少發生，
    // 防呆。
    if (_lastSpokenTime == null) {
      return true;
    }


    final Duration difference =
        DateTime.now().difference(
      _lastSpokenTime!,
    );


    // 同一危險持續太久，
    // 超過間隔才再提醒。
    return difference >=
        _ttsRepeatInterval;
  }


  // ==============================
  // 真正執行 TTS
  // ==============================

  Future<void> _speak({
    required String key,
    required String text,
  }) async {
    _lastSpokenKey = key;

    _lastSpokenTime =
        DateTime.now();


    // 避障屬於高優先級
    // 先停止上一段 TTS。
    await _flutterTts.stop();


    await _flutterTts.speak(
      text,
    );
  }


  // ==============================
  // 英文類別 → 中文
  // ==============================

  String _getChineseObstacleName(
    String objectType,
  ) {
    switch (
        objectType
            .trim()
            .toLowerCase()) {
      case 'pole':
        return '電線桿';

      case 'transformer box':
        return '變電箱';

      case 'ground billboard':
        return '廣告看板';

      case 'rear overhang':
        return '車尾懸掛障礙物';

      default:
        return '障礙物';
    }
  }


  // ==============================
  // 危險解除
  // ==============================

  void _resetAlertState() {
    _lastSpokenKey = null;

    _lastSpokenTime = null;

    _lastVibrationTime = null;

    _lastVibrationRiskLevel = 0;
  }


  // ==============================
  // 停止所有警示
  // ==============================

  Future<void> stop() async {
    await Vibration.cancel();

    await _flutterTts.stop();

    _resetAlertState();
  }
}