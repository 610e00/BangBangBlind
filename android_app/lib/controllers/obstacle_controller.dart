import 'dart:async';
import 'dart:typed_data';
import 'dart:ui';

import 'package:flutter/foundation.dart';
import 'package:ultralytics_yolo/ultralytics_yolo.dart';

import '../models/obstacle_detection_result.dart';

import '../services/obstacle_alert.dart';
import '../services/obstacle_decision.dart';
import '../services/obstacle_tracker.dart';

class ObstacleController {
  ObstacleController({
    this.modelPath = 'assets/models/best.tflite',
    Map<String, BoxDistanceThreshold>?
        calibratedThresholds,
    ObstacleSpeechRequest? onSpeechRequested,
    ObstacleToneRequest? onToneRequested,
  })  : _decision = ObstacleDecision(
          calibratedThresholds: calibratedThresholds,
        ),
        _alert = ObstacleAlert(
          onSpeechRequested: onSpeechRequested,
          onToneRequested: onToneRequested,
        );

  final String modelPath;

  // ====================================
  // 核心模組
  // ====================================

  final ObstacleDecision _decision;
  final ObstacleTracker _tracker = ObstacleTracker();
  final ObstacleAlert _alert;

  YOLO? _yolo;

  // ====================================
  // 系統狀態
  // ====================================

  bool _isStarted = false;
  bool _isStarting = false;
  bool _isStopping = false;

  bool _isProcessingFrame = false;

  Completer<void>? _activeInference;

  // ====================================
  // 攝影機監控
  // ====================================

  bool _hasReceivedValidFrame = false;
  bool _cameraFailureAnnounced = false;

  DateTime? _startedAt;
  DateTime? _lastValidFrameAt;

  Timer? _cameraWatchdog;

  // ====================================
  // Debug
  // ====================================

  bool _firstPredictionLogged = false;
  bool _firstDetectionLogged = false;

  DateTime? _lastLogTime;

  // ====================================
  // 防抖
  // ====================================

  static const Duration _stableDuration =
      Duration(milliseconds: 300);

  static const Duration _clearDuration =
      Duration(milliseconds: 700);

  String? _pendingDecisionKey;
  DateTime? _pendingDecisionSince;
  DateTime? _noHazardSince;

  ObstacleDecisionResult _currentDecision =
      ObstacleDecisionResult.none();

  // ====================================
  // 提供給整合端讀取的狀態
  // ====================================

  bool get isStarted => _isStarted;

  bool get hasReceivedValidFrame =>
      _hasReceivedValidFrame;

  ObstacleDecisionResult get currentDecision =>
      _currentDecision;

  // ====================================
  // 啟動避障模組
  // ====================================

  Future<void> start() async {
    if (_isStarted ||
        _isStarting ||
        _isStopping) {
      return;
    }

    _isStarting = true;

    try {
      // 1. 初始化警示服務
      await _alert.initialize();

      // 2. 載入 YOLO
      await _loadYoloModel();

      _isStarted = true;
      _startedAt = DateTime.now();

      debugPrint(
        '避障模型已就緒，'
        '等待共用 USB 鏡頭的 JPEG 畫面',
      );

      // 3. 定期檢查是否有有效畫面
      _cameraWatchdog?.cancel();

      _cameraWatchdog = Timer.periodic(
        const Duration(seconds: 1),
        (_) {
          _checkCameraTimeout();
        },
      );
    } catch (error) {
      debugPrint(
        '避障系統初始化失敗：$error',
      );

      await _cleanupResources();

      rethrow;
    } finally {
      _isStarting = false;
    }
  }

  // ====================================
  // 模型載入：GPU 失敗改 CPU
  // ====================================

  Future<void> _loadYoloModel() async {
    try {
      final gpuYolo = YOLO(
        modelPath: modelPath,
        task: YOLOTask.detect,
        useGpu: true,
      );

      _yolo = gpuYolo;

      final loaded = await gpuYolo.loadModel();

      if (!loaded) {
        throw StateError(
          'GPU 模型載入失敗',
        );
      }

      debugPrint(
        'YOLO GPU 模式載入成功',
      );

      return;
    } catch (error) {
      debugPrint(
        'GPU 載入失敗，改用 CPU：$error',
      );

      final failedYolo = _yolo;
      _yolo = null;

      if (failedYolo != null) {
        try {
          await failedYolo.dispose();
        } catch (error) {
          debugPrint(
            'GPU 模型清理失敗：$error',
          );
        }
      }
    }

    final cpuYolo = YOLO(
      modelPath: modelPath,
      task: YOLOTask.detect,
      useGpu: false,
    );

    _yolo = cpuYolo;

    final loaded = await cpuYolo.loadModel();

    if (!loaded) {
      throw StateError(
        'CPU 模型載入失敗',
      );
    }

    debugPrint(
      'YOLO CPU 模式載入成功',
    );
  }

  // ====================================
  // A2：接收共用鏡頭的 JPEG 影格
  //
  // 由外部 Camera Service 呼叫。
  // 不在這裡控制 USB 鏡頭。
  // ====================================

  Future<List<ObstacleDetectionResult>> processFrame(
    Uint8List jpegBytes,
    int width,
    int height,
  ) async {
    if (!_isStarted ||
        _isStopping ||
        _isProcessingFrame) {
      return const [];
    }

    final yolo = _yolo;

    if (yolo == null) {
      return const [];
    }

    // 檢查 JPEG 基本格式
    if (width <= 0 ||
        height <= 0 ||
        jpegBytes.length < 4 ||
        jpegBytes[0] != 0xFF ||
        jpegBytes[1] != 0xD8) {
      debugPrint(
        '收到無效 JPEG 畫面，略過推論',
      );

      return const [];
    }

    // 上一張還在推論時，
    // 新影格直接略過，不排隊。
    _isProcessingFrame = true;

    final completer = Completer<void>();
    _activeInference = completer;

    final stopwatch = Stopwatch()..start();

    try {
      // =================================
      // 1. YOLO 推論
      // =================================

      final Map<String, dynamic> prediction =
          await yolo.predict(
        jpegBytes,

        // 保留較低的模型入口門檻。
        // Decision 再依不同類別過濾。
        confidenceThreshold: 0.25,

        iouThreshold: 0.45,
      );

      stopwatch.stop();

      // =================================
      // 2. 真正推論成功後，
      //    才記錄有效影格
      // =================================

      _lastValidFrameAt = DateTime.now();

      if (!_hasReceivedValidFrame) {
        debugPrint(
          'A5：成功收到並推論鏡頭影格 '
          '${width}x$height',
        );
      }

      _hasReceivedValidFrame = true;
      _cameraFailureAnnounced = false;

      // =================================
      // 3. 第一次結果 Log
      // =================================

      if (!_firstPredictionLogged) {
        _firstPredictionLogged = true;

        debugPrint(
          'YOLO 回傳欄位：'
          '${prediction.keys.toList()}',
        );

        final raw =
            prediction['detections'];

        if (raw is List && raw.isNotEmpty) {
          debugPrint(
            '第一筆原始 detection：'
            '${raw.first}',
          );
        }
      }

      // =================================
      // 4. A1：解析偵測框
      // =================================

      final detections =
          _convertPrediction(prediction);

      if (detections.isNotEmpty &&
          !_firstDetectionLogged) {
        _firstDetectionLogged = true;

        debugPrint(
          'A1：解析成功，'
          'parsed boxes > 0，'
          '數量=${detections.length}',
        );
      }

      // =================================
      // 5. Tracker：追蹤同一物件
      // =================================

      final tracked =
          _tracker.update(detections);

      final activeIds =
          _tracker.activeTrackIds;

      // 清理已消失追蹤 ID 的狀態
      _decision.retainTracks(activeIds);
      _alert.retainTracks(activeIds);

      // =================================
      // 6. Decision：
      // 五區、信心值、2m、1.5m
      // =================================

      final decision =
          _decision.analyze(tracked);

      // =================================
      // 7. Alert：
      // 2m 叮兩聲，1.5m 加快
      // =================================

      await _handleDecision(decision);

      // =================================
      // 8. 診斷 Log
      // =================================

      _logResults(
        stopwatch.elapsedMilliseconds,
        tracked,
        decision,
      );

      // 供整合端顯示偵測框
      return tracked;
    } catch (error) {
      debugPrint(
        'YOLO 推論失敗：$error',
      );

      // 推論失敗不能用舊框繼續警示
      await _alert.handle(
        ObstacleDecisionResult.none(),
      );

      return const [];
    } finally {
      if (stopwatch.isRunning) {
        stopwatch.stop();
      }

      _isProcessingFrame = false;

      if (!completer.isCompleted) {
        completer.complete();
      }

      if (identical(
        _activeInference,
        completer,
      )) {
        _activeInference = null;
      }
    }
  }

  // ====================================
  // A1：解析 YOLO 結果
  //
  // 優先使用 detections。
  // 兼容部分版本的 boxes。
  // ====================================

  List<ObstacleDetectionResult>
      _convertPrediction(
    Map<String, dynamic> prediction,
  ) {
    final dynamic raw =
        prediction['detections'] is List
            ? prediction['detections']
            : prediction['boxes'];

    final results =
        <ObstacleDetectionResult>[];

    if (raw is! List) {
      debugPrint(
        'YOLO 結果沒有可用的 '
        'detections / boxes 清單',
      );

      return results;
    }

    for (final item in raw) {
      if (item is! Map) {
        continue;
      }

      final detection = <String, dynamic>{};

      for (final entry in item.entries) {
        detection[entry.key.toString()] =
            entry.value;
      }

      // 類別名稱
      final objectType = (
        detection['className'] ??
        detection['class_name'] ??
        detection['label'] ??
        detection['name'] ??
        detection['class'] ??
        ''
      ).toString().trim();

      // 信心值
      final confidence = _toDouble(
        detection['confidence'] ??
        detection['score'],
      );

      // normalizedBox 或 x1_norm 格式
      final box =
          _readNormalizedBox(
            detection['normalizedBox'],
          ) ??
          _readNormalizedBox(detection);

      if (objectType.isEmpty ||
          confidence == null ||
          !confidence.isFinite ||
          confidence < 0 ||
          confidence > 1 ||
          box == null) {
        continue;
      }

      results.add(
        ObstacleDetectionResult(
          objectType: objectType,
          confidence: confidence,
          box: box,
        ),
      );
    }

    return results;
  }

  // ====================================
  // 解析標準化座標
  // ====================================

  Rect? _readNormalizedBox(dynamic raw) {
    if (raw is Rect) {
      return _clampBox(raw);
    }

    if (raw is! Map) {
      return null;
    }

    final box = <String, dynamic>{};

    for (final entry in raw.entries) {
      box[entry.key.toString()] =
          entry.value;
    }

    final left = _toDouble(
      box['left'] ?? box['x1_norm'],
    );

    final top = _toDouble(
      box['top'] ?? box['y1_norm'],
    );

    final right = _toDouble(
      box['right'] ?? box['x2_norm'],
    );

    final bottom = _toDouble(
      box['bottom'] ?? box['y2_norm'],
    );

    if (left == null ||
        top == null ||
        right == null ||
        bottom == null) {
      return null;
    }

    return _clampBox(
      Rect.fromLTRB(
        left,
        top,
        right,
        bottom,
      ),
    );
  }

  // ====================================
  // 座標限制在 0～1
  // ====================================

  Rect? _clampBox(Rect raw) {
    if (!raw.left.isFinite ||
        !raw.top.isFinite ||
        !raw.right.isFinite ||
        !raw.bottom.isFinite) {
      return null;
    }

    final left =
        raw.left.clamp(0.0, 1.0).toDouble();

    final top =
        raw.top.clamp(0.0, 1.0).toDouble();

    final right =
        raw.right.clamp(0.0, 1.0).toDouble();

    final bottom =
        raw.bottom.clamp(0.0, 1.0).toDouble();

    if (right <= left || bottom <= top) {
      return null;
    }

    return Rect.fromLTRB(
      left,
      top,
      right,
      bottom,
    );
  }

  double? _toDouble(dynamic value) {
    if (value is num) {
      return value.toDouble();
    }

    if (value is String) {
      return double.tryParse(value);
    }

    return null;
  }

  // ====================================
  // 警示狀態穩定處理
  // ====================================

  Future<void> _handleDecision(
    ObstacleDecisionResult next,
  ) async {
    final now = DateTime.now();

    // =================================
    // 沒有障礙物：
    // 不繼續播放短音
    // =================================

    if (next.warningType ==
        ObstacleWarningType.none) {
      await _alert.handle(
        ObstacleDecisionResult.none(),
      );

      _pendingDecisionKey = null;
      _pendingDecisionSince = null;

      _noHazardSince ??= now;

      // 保留原本 700ms 的狀態清除緩衝
      if (_currentDecision.warningType !=
              ObstacleWarningType.none &&
          now.difference(_noHazardSince!) >=
              _clearDuration) {
        _currentDecision =
            ObstacleDecisionResult.none();

        _noHazardSince = null;
      }

      return;
    }

    _noHazardSince = null;

    // =================================
    // 1.5m：追蹤已確認後立即警示
    // =================================

    final isNear =
        next.warningType ==
            ObstacleWarningType.frontDanger ||
        next.warningType ==
            ObstacleWarningType.sideCritical;

    if (isNear) {
      _currentDecision = next;

      _pendingDecisionKey = null;
      _pendingDecisionSince = null;

      await _alert.handle(next);
      return;
    }

    // =================================
    // 2m：一般警示先穩定 300ms
    // =================================

    final key = _decisionKey(next);

    if (_currentDecision.warningType !=
            ObstacleWarningType.none &&
        _decisionKey(_currentDecision) == key) {
      _currentDecision = next;

      _pendingDecisionKey = null;
      _pendingDecisionSince = null;

      await _alert.handle(next);
      return;
    }

    // 新狀態第一次出現
    if (_pendingDecisionKey != key) {
      _pendingDecisionKey = key;
      _pendingDecisionSince = now;
      return;
    }

    _pendingDecisionSince ??= now;

    // 持續 300ms 才成立
    if (now.difference(_pendingDecisionSince!) >=
        _stableDuration) {
      _currentDecision = next;

      _pendingDecisionKey = null;
      _pendingDecisionSince = null;

      await _alert.handle(next);
    }
  }

  // ====================================
  // 判斷是否同一種警示
  // ====================================

  String _decisionKey(
    ObstacleDecisionResult result,
  ) {
    return '${result.trackId ?? 'none'}-'
        '${result.direction?.name ?? 'none'}-'
        '${result.warningType.name}';
  }

  // ====================================
  // A5：攝影機影格逾時監控
  // ====================================

  void _checkCameraTimeout() {
    if (!_isStarted ||
        _isStopping ||
        _cameraFailureAnnounced) {
      return;
    }

    final now = DateTime.now();

    // 模型已啟動，但一直沒有有效畫面
    if (!_hasReceivedValidFrame) {
      if (_startedAt != null &&
          now.difference(_startedAt!) >=
              const Duration(seconds: 5)) {
        unawaited(
          notifyCameraFailure(
            '尚未收到有效的鏡頭畫面',
          ),
        );
      }

      return;
    }

    // 原本有畫面，後來中斷
    if (_lastValidFrameAt != null &&
        now.difference(_lastValidFrameAt!) >=
            const Duration(seconds: 5)) {
      unawaited(
        notifyCameraFailure(
          '鏡頭影格或模型推論已逾時',
        ),
      );
    }
  }

  // ====================================
  // 提供共用 Camera Service 呼叫
  // ====================================

  Future<void> notifyCameraFailure([
    String reason = '鏡頭連線異常',
  ]) async {
    if (_cameraFailureAnnounced ||
        _isStopping ||
        !_isStarted) {
      return;
    }

    _cameraFailureAnnounced = true;
    _hasReceivedValidFrame = false;

    debugPrint(
      'A5：攝影機異常：$reason',
    );

    _tracker.clear();
    _decision.clear();
    _resetDecisionState();

    await _alert.reset();
    await _alert.announceCameraFailure();
  }

  // ====================================
  // Debug Log
  // ====================================

  void _logResults(
    int elapsedMs,
    List<ObstacleDetectionResult> tracked,
    ObstacleDecisionResult result,
  ) {
    final now = DateTime.now();

    if (_lastLogTime != null &&
        now.difference(_lastLogTime!) <
            const Duration(seconds: 2)) {
      return;
    }

    _lastLogTime = now;

    debugPrint(
      'YOLO 推論 ${elapsedMs}ms，'
      '本幀框數=${tracked.length}',
    );

    for (final d in tracked) {
      debugPrint(
        'Track #${d.trackId} '
        '類別=${d.objectType} '
        '穩定類別=${d.stableObjectType} '
        'conf=${d.confidence.toStringAsFixed(2)} '
        'hits=${d.trackHits} '
        'width=${d.box.width.toStringAsFixed(3)} '
        'height=${d.box.height.toStringAsFixed(3)}',
      );
    }

    debugPrint(
      '警示=${result.warningType.name}，'
      '方向=${result.direction?.name}，'
      '追蹤 ID=${result.trackId}',
    );
  }

  // ====================================
  // 重設判斷狀態
  // ====================================

  void _resetDecisionState() {
    _pendingDecisionKey = null;
    _pendingDecisionSince = null;
    _noHazardSince = null;

    _currentDecision =
        ObstacleDecisionResult.none();
  }

  // ====================================
  // 清理避障資源
  // ====================================

  Future<void> _cleanupResources() async {
    _cameraWatchdog?.cancel();
    _cameraWatchdog = null;

    // 等目前推論完成
    final active = _activeInference;

    if (active != null &&
        !active.isCompleted) {
      await active.future;
    }

    final yolo = _yolo;
    _yolo = null;

    if (yolo != null) {
      try {
        await yolo.dispose();
      } catch (error) {
        debugPrint(
          'YOLO dispose 失敗：$error',
        );
      }
    }

    await _alert.stop();

    _tracker.clear();
    _decision.clear();
    _resetDecisionState();

    _isStarted = false;
    _isProcessingFrame = false;
    _activeInference = null;

    _hasReceivedValidFrame = false;
    _cameraFailureAnnounced = false;

    _startedAt = null;
    _lastValidFrameAt = null;

    _firstPredictionLogged = false;
    _firstDetectionLogged = false;
    _lastLogTime = null;
  }

  // ====================================
  // 停止避障模組
  // ====================================

  Future<void> stop() async {
    if (_isStopping) {
      return;
    }

    while (_isStarting) {
      await Future<void>.delayed(
        const Duration(milliseconds: 20),
      );
    }

    if (!_isStarted && _yolo == null) {
      return;
    }

    _isStopping = true;

    try {
      await _cleanupResources();

      debugPrint(
        '避障模組已停止，'
        '不會釋放團隊共用 USB 鏡頭',
      );
    } finally {
      _isStopping = false;
    }
  }
}