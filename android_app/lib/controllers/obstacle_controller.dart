import 'dart:async';
import 'dart:ui';

import 'package:flutter/foundation.dart';
import 'package:ultralytics_yolo/ultralytics_yolo.dart';
import 'package:uvc_android_flutter/uvc_android_flutter.dart';

import '../models/obstacle_detection_result.dart';
import '../services/obstacle_alert.dart';
import '../services/obstacle_decision.dart';


class ObstacleController {
  // ==============================
  // UVC 鏡頭
  // ==============================

  final UvcAndroidFlutter _uvc =
      UvcAndroidFlutter.instance;


  // ==============================
  // 避障判斷
  // ==============================

  final ObstacleDecision _obstacleDecision =
      ObstacleDecision();


  // ==============================
  // 震動 + TTS
  //
  // 現在手機只有單顆震動馬達，
  // 所以暫時開啟側邊語音。
  //
  // 未來接左右外部震動器後，
  // 可以改成 false。
  // ==============================

  final ObstacleAlert _obstacleAlert =
      ObstacleAlert(
    enableSideSpeech: true,
  );


  // ==============================
  // YOLO 模型
  //
  // 不再使用 late final，
  // 因為 stop() 後有可能再次 start()
  // ==============================

  YOLO? _yolo;


  // ==============================
  // UVC 畫面串流
  // ==============================

  StreamSubscription<dynamic>?
      _frameSubscription;


  // ==============================
  // 推論狀態
  // ==============================

  bool _isProcessingFrame = false;

  Completer<void>?
      _activeInferenceCompleter;


  // ==============================
  // 系統生命週期
  // ==============================

  bool _isStarted = false;

  bool _isStarting = false;

  bool _isStopping = false;


  // ==============================
  // Debug / 效能
  // ==============================

  bool _hasLoggedFirstPrediction =
      false;

  DateTime? _lastPerformanceLogTime;


  // ==============================
  // 狀態穩定
  //
  // 不再用「3 幀」，
  // 改成實際時間。
  // ==============================

  static const Duration
      _stableDuration =
      Duration(
    milliseconds: 300,
  );

  static const Duration
      _clearDuration =
      Duration(
    milliseconds: 700,
  );

  String? _pendingDecisionKey;

  DateTime? _pendingDecisionSince;

  DateTime? _noHazardSince;

  ObstacleDecisionResult
      _currentDecision =
      ObstacleDecisionResult.none();


  // ==================================================
  // 啟動整套避障功能
  // ==================================================

  Future<void> start() async {
    // 已經啟動
    if (_isStarted) {
      return;
    }

    // 已經正在啟動
    if (_isStarting) {
      return;
    }

    // 正在停止時不要又啟動
    if (_isStopping) {
      return;
    }

    _isStarting = true;

    try {
      // --------------------------------
      // 1. 初始化 TTS / 警示
      // --------------------------------

      await _obstacleAlert.initialize();


      // --------------------------------
      // 2. 載入 YOLO
      //
      // 先 GPU
      // GPU 失敗再改 CPU
      // --------------------------------

      await _loadYoloModel();


      // --------------------------------
      // 3. 要求 UVC / Camera 權限
      // --------------------------------

      final bool permissionGranted =
          await _uvc.requestPermissions();

      if (!permissionGranted) {
        throw Exception(
          'UVC 鏡頭權限未取得',
        );
      }


      // --------------------------------
      // 4. 啟動 UVC Camera
      // --------------------------------

      await _uvc.start(
        width: 640,
        height: 480,
        fallbackToNearest: true,
        previewTransform:
            const UvcPreviewTransform(
          mirrorHorizontal: false,
          flipVertical: false,
          rotationDegrees: 0,
        ),
      );

      debugPrint(
        'UVC Camera 啟動成功',
      );


      // --------------------------------
      // 5. 持續取得 JPEG frame
      // --------------------------------

      _frameSubscription =
          _uvc.frameStream(
        width: 640,
        fps: 10,
        format:
            UvcPreviewFrameFormat.jpeg,
        quality: 80,
      ).listen(
        (frame) {
          // 系統正在停止
          // 就不要再收新畫面
          if (_isStopping) {
            return;
          }

          // 上一張還在推論，
          // 新的一張直接略過。
          //
          // 不讓舊畫面排隊。
          if (_isProcessingFrame) {
            return;
          }

          final Uint8List imageBytes =
              frame.bytes;

          unawaited(
            _processFrame(
              imageBytes,
            ),
          );
        },

        onError: (Object error) {
          debugPrint(
            'UVC frame 錯誤：$error',
          );
        },
      );


      _isStarted = true;

      debugPrint(
        '避障系統開始運作',
      );
    } catch (error) {
      debugPrint(
        '避障系統啟動失敗：$error',
      );

      // start() 中途失敗時，
      // 把已經建立的資源清乾淨。
      await _cleanupResources();

      rethrow;
    } finally {
      _isStarting = false;
    }
  }


  // ==================================================
  // 載入 YOLO
  //
  // 先 GPU
  // GPU 失敗 → dispose
  // 再建立新的 CPU YOLO
  // ==================================================

  Future<void> _loadYoloModel() async {
    // --------------------------------
    // 先嘗試 GPU
    // --------------------------------

    try {
      final YOLO gpuYolo =
          YOLO(
        modelPath:
            'assets/models/best.tflite',
        task: YOLOTask.detect,
        useGpu: true,
      );

      _yolo = gpuYolo;

      final bool loaded =
          await gpuYolo.loadModel();

      if (!loaded) {
        throw Exception(
          'GPU 模式模型載入失敗',
        );
      }

      debugPrint(
        'best.tflite GPU 模式載入成功',
      );

      return;
    } catch (error) {
      debugPrint(
        'GPU 模式失敗，改用 CPU：$error',
      );

      final YOLO? failedYolo =
          _yolo;

      _yolo = null;

      if (failedYolo != null) {
        try {
          await failedYolo.dispose();
        } catch (disposeError) {
          debugPrint(
            'GPU YOLO dispose 失敗：'
            '$disposeError',
          );
        }
      }
    }


    // --------------------------------
    // GPU 失敗後改用 CPU
    // --------------------------------

    final YOLO cpuYolo =
        YOLO(
      modelPath:
          'assets/models/best.tflite',
      task: YOLOTask.detect,
      useGpu: false,
    );

    _yolo = cpuYolo;

    final bool loaded =
        await cpuYolo.loadModel();

    if (!loaded) {
      throw Exception(
        'best.tflite CPU 模式載入失敗',
      );
    }

    debugPrint(
      'best.tflite CPU 模式載入成功',
    );
  }


  // ==================================================
  // 處理一張 UVC 圖片
  // ==================================================

  Future<void> _processFrame(
    Uint8List imageBytes,
  ) async {
    if (_isProcessingFrame ||
        _isStopping) {
      return;
    }


    // 使用 local reference。
    //
    // 這樣即使之後 stop()
    // 準備把 _yolo 清掉，
    // 目前正在執行的 predict
    // 還有自己的參考。
    final YOLO? yolo =
        _yolo;

    if (yolo == null) {
      return;
    }


    _isProcessingFrame = true;

    final Completer<void> completer =
        Completer<void>();

    _activeInferenceCompleter =
        completer;


    final Stopwatch stopwatch =
        Stopwatch()
          ..start();


    try {
      // --------------------------------
      // 1. 手機本機 YOLO
      // --------------------------------

      final Map<String, dynamic>
          prediction =
          await yolo.predict(
        imageBytes,

        // 漏報比誤報危險，
        // 目前先維持 0.25。
        confidenceThreshold: 0.25,

        // 原本 0.70 改成 0.45，
        // 之後真機再觀察重疊框。
        iouThreshold: 0.45,
      );


      stopwatch.stop();


      // --------------------------------
      // 第一次 predict 結果
      //
      // 真機第一次一定要看，
      // 用來確認 plugin 實際 key：
      //
      // boxes
      // className
      // confidence
      // normalizedBox
      // --------------------------------

      if (!_hasLoggedFirstPrediction) {
        debugPrint(
          '第一次 YOLO prediction：'
          '$prediction',
        );

        _hasLoggedFirstPrediction =
            true;
      }


      // --------------------------------
      // 推論效能
      // 每約 2 秒印一次
      // --------------------------------

      _logInferencePerformance(
        stopwatch.elapsedMilliseconds,
      );


      // --------------------------------
      // 2. YOLO 結果
      // → App 自己的格式
      // --------------------------------

      final List<ObstacleDetectionResult>
          detections =
          _convertPrediction(
        prediction,
      );


      // --------------------------------
      // 3. Decision
      // --------------------------------

      final ObstacleDecisionResult
          decision =
          _obstacleDecision.analyze(
        detections,
      );


      // --------------------------------
      // 4. 狀態穩定
      // --------------------------------

      await _handleDecision(
        decision,
      );
    } catch (error) {
      if (stopwatch.isRunning) {
        stopwatch.stop();
      }

      debugPrint(
        'YOLO 推論失敗：$error',
      );
    } finally {
      _isProcessingFrame = false;

      if (!completer.isCompleted) {
        completer.complete();
      }

      if (identical(
        _activeInferenceCompleter,
        completer,
      )) {
        _activeInferenceCompleter =
            null;
      }
    }
  }


  // ==================================================
  // 推論效能 log
  // ==================================================

  void _logInferencePerformance(
    int elapsedMilliseconds,
  ) {
    final DateTime now =
        DateTime.now();

    if (_lastPerformanceLogTime !=
            null &&
        now
                .difference(
                  _lastPerformanceLogTime!,
                )
                .inSeconds <
            2) {
      return;
    }

    _lastPerformanceLogTime = now;


    final double fps =
        elapsedMilliseconds > 0
            ? 1000 /
                elapsedMilliseconds
            : 0;


    debugPrint(
      'YOLO 推論耗時：'
      '${elapsedMilliseconds}ms，'
      '單次推論約 '
      '${fps.toStringAsFixed(1)} FPS',
    );
  }


  // ==================================================
  // YOLO Map
  // ↓
  // List<ObstacleDetectionResult>
  // ==================================================

  List<ObstacleDetectionResult>
      _convertPrediction(
    Map<String, dynamic> prediction,
  ) {
    final List<ObstacleDetectionResult>
        detections = [];


    final dynamic rawBoxes =
        prediction['boxes'];


    if (rawBoxes is! List) {
      debugPrint(
        'YOLO prediction["boxes"] '
        '格式不是 List：'
        '${rawBoxes.runtimeType}',
      );

      return detections;
    }


    for (final dynamic rawDetection
        in rawBoxes) {
      if (rawDetection is! Map) {
        continue;
      }


      final Map<String, dynamic>
          detection =
          Map<String, dynamic>.from(
        rawDetection,
      );


      // --------------------------------
      // 類別名稱
      // --------------------------------

      final String objectType =
          detection['className']
                  ?.toString() ??
              '';


      // --------------------------------
      // 信心值
      // --------------------------------

      final double? confidence =
          _toDouble(
        detection['confidence'],
      );


      // --------------------------------
      // normalized bounding box
      // --------------------------------

      final Rect? box =
          _readNormalizedBox(
        detection['normalizedBox'],
      );


      if (objectType.isEmpty ||
          confidence == null ||
          box == null) {
        debugPrint(
          '略過無法解析的 YOLO detection：'
          '$detection',
        );

        continue;
      }


      detections.add(
        ObstacleDetectionResult(
          objectType: objectType,
          confidence: confidence,
          box: box,
        ),
      );
    }


    return detections;
  }


  // ==================================================
  // normalizedBox Map
  // → Rect
  // ==================================================

  Rect? _readNormalizedBox(
    dynamic rawBox,
  ) {
    if (rawBox is! Map) {
      return null;
    }


    final Map<String, dynamic> box =
        Map<String, dynamic>.from(
      rawBox,
    );


    final double? left =
        _toDouble(
      box['left'],
    );

    final double? top =
        _toDouble(
      box['top'],
    );

    final double? right =
        _toDouble(
      box['right'],
    );

    final double? bottom =
        _toDouble(
      box['bottom'],
    );


    if (left == null ||
        top == null ||
        right == null ||
        bottom == null) {
      return null;
    }


    // 確認 normalized box
    // 基本幾何關係合理。
    if (right <= left ||
        bottom <= top) {
      debugPrint(
        'normalizedBox 幾何異常：'
        '$box',
      );

      return null;
    }


    return Rect.fromLTRB(
      left,
      top,
      right,
      bottom,
    );
  }


  // ==================================================
  // num → double
  // ==================================================

  double? _toDouble(
    dynamic value,
  ) {
    if (value is num) {
      return value.toDouble();
    }

    return null;
  }


  // ==================================================
  // 處理 Decision
  //
  // frontDanger：
  // 立即成立
  //
  // 其他狀態：
  // 持續約 300ms 才成立
  //
  // none：
  // 持續約 700ms 才解除
  // ==================================================

  Future<void> _handleDecision(
    ObstacleDecisionResult newDecision,
  ) async {
    final DateTime now =
        DateTime.now();


    // --------------------------------
    // 沒有危險
    // --------------------------------

    if (newDecision.warningType ==
        ObstacleWarningType.none) {
      _pendingDecisionKey = null;

      _pendingDecisionSince = null;

      _noHazardSince ??= now;


      final Duration absentTime =
          now.difference(
        _noHazardSince!,
      );


      if (_currentDecision.warningType !=
              ObstacleWarningType.none &&
          absentTime >=
              _clearDuration) {
        _currentDecision =
            ObstacleDecisionResult.none();

        _noHazardSince = null;

        await _obstacleAlert.handle(
          _currentDecision,
        );
      }

      return;
    }


    // 又看到危險
    _noHazardSince = null;


    // --------------------------------
    // 正前方近距危險
    //
    // 不等待穩定時間
    // 立即處理
    // --------------------------------

    if (newDecision.warningType ==
        ObstacleWarningType.frontDanger) {
      _currentDecision =
          newDecision;

      _pendingDecisionKey = null;

      _pendingDecisionSince = null;

      await _obstacleAlert.handle(
        newDecision,
      );

      return;
    }


    // --------------------------------
    // frontWarning / sideDanger
    // --------------------------------

    final String newKey =
        _makeDecisionKey(
      newDecision,
    );


    // --------------------------------
    // 如果這個狀態已經正式成立，
    // 不必重新等 300ms。
    //
    // 直接繼續交給 Alert，
    // 由 Alert 自己控制震動 cooldown。
    // --------------------------------

    if (_currentDecision.warningType !=
            ObstacleWarningType.none &&
        _makeDecisionKey(
              _currentDecision,
            ) ==
            newKey) {
      _currentDecision =
          newDecision;

      _pendingDecisionKey = null;

      _pendingDecisionSince = null;

      await _obstacleAlert.handle(
        newDecision,
      );

      return;
    }


    // --------------------------------
    // 新狀態第一次出現
    // --------------------------------

    if (_pendingDecisionKey !=
        newKey) {
      _pendingDecisionKey =
          newKey;

      _pendingDecisionSince =
          now;

      return;
    }


    // 理論上不會 null，
    // 但防呆保護。
    _pendingDecisionSince ??=
        now;


    final Duration stableTime =
        now.difference(
      _pendingDecisionSince!,
    );


    // --------------------------------
    // 持續約 300ms
    // 才正式成立
    // --------------------------------

    if (stableTime >=
        _stableDuration) {
      _currentDecision =
          newDecision;

      _pendingDecisionKey = null;

      _pendingDecisionSince = null;

      await _obstacleAlert.handle(
        newDecision,
      );
    }
  }


  // ==================================================
  // 比較是否為同一種警示狀態
  // ==================================================

  String _makeDecisionKey(
    ObstacleDecisionResult result,
  ) {
    final String objectType =
        result.obstacle
                ?.objectType ??
            'none';

    final String direction =
        result.direction
                ?.name ??
            'none';

    final String warningType =
        result.warningType.name;


    return '$objectType-'
        '$direction-'
        '$warningType';
  }


  // ==================================================
  // 清理資源
  //
  // 順序：
  // 1. 停止新 frame
  // 2. 等目前 predict 完成
  // 3. release UVC
  // 4. dispose YOLO
  // 5. 停止警示
  // ==================================================

  Future<void> _cleanupResources() async {
    // --------------------------------
    // 1. 取消 UVC stream
    // --------------------------------

    try {
      await _frameSubscription
          ?.cancel();
    } catch (error) {
      debugPrint(
        '取消 frame stream 失敗：'
        '$error',
      );
    }

    _frameSubscription = null;


    // --------------------------------
    // 2. 等目前正在執行的推論
    // --------------------------------

    final Completer<void>?
        activeInference =
        _activeInferenceCompleter;

    if (activeInference != null &&
        !activeInference.isCompleted) {
      try {
        await activeInference.future;
      } catch (error) {
        debugPrint(
          '等待 YOLO 推論結束失敗：'
          '$error',
        );
      }
    }


    // --------------------------------
    // 3. release UVC
    // --------------------------------

    try {
      await _uvc.release();
    } catch (error) {
      debugPrint(
        'UVC release 失敗：$error',
      );
    }


    // --------------------------------
    // 4. dispose YOLO
    // --------------------------------

    final YOLO? yolo =
        _yolo;

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


    // --------------------------------
    // 5. 停止震動 / TTS
    // --------------------------------

    try {
      await _obstacleAlert.stop();
    } catch (error) {
      debugPrint(
        'ObstacleAlert stop 失敗：'
        '$error',
      );
    }


    // --------------------------------
    // 6. 重設狀態
    // --------------------------------

    _isStarted = false;

    _isProcessingFrame = false;

    _activeInferenceCompleter =
        null;

    _pendingDecisionKey = null;

    _pendingDecisionSince = null;

    _noHazardSince = null;

    _currentDecision =
        ObstacleDecisionResult.none();

    _hasLoggedFirstPrediction =
        false;

    _lastPerformanceLogTime =
        null;
  }


  // ==================================================
  // 停止整套避障系統
  // ==================================================

  Future<void> stop() async {
    if (_isStopping) {
      return;
    }


    // 如果剛好正在 start，
    // 先等 start 流程結束。
    //
    // 避免 start / stop 同時操作
    // 同一個 YOLO / UVC。
    while (_isStarting) {
      await Future<void>.delayed(
        const Duration(
          milliseconds: 20,
        ),
      );
    }


    // 真的完全沒有任何資源
    if (!_isStarted &&
        _yolo == null &&
        _frameSubscription == null) {
      return;
    }


    _isStopping = true;


    try {
      await _cleanupResources();

      debugPrint(
        '避障系統已停止',
      );
    } finally {
      _isStopping = false;
    }
  }
}