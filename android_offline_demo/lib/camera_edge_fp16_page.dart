import 'dart:async';
import 'dart:io';
import 'dart:math' as math;
import 'dart:typed_data';

import 'package:camera/camera.dart';
import 'package:flutter/material.dart';
import 'package:flutter_tts/flutter_tts.dart';
import 'package:image/image.dart' as img;
import 'package:ultralytics_yolo/ultralytics_yolo.dart';

import 'countdown_segmenter_service.dart';
import 'countdown_fp16_service.dart';
import 'edge_countdown_tracker.dart';
import 'edge_tts_guard.dart';

class CameraEdgeFp16Page extends StatefulWidget {
  const CameraEdgeFp16Page({super.key});

  @override
  State<CameraEdgeFp16Page> createState() => _CameraEdgeFp16PageState();
}

class _CameraEdgeFp16PageState extends State<CameraEdgeFp16Page> {
  final YOLO _yolo = YOLO(
    modelPath: 'assets/models/traffic_yolov8n_v6_1280.tflite',
    task: YOLOTask.detect,
    useGpu: false,
  );

  CameraController? _camera;
  Timer? _timer;

  bool _loaded = false;
  bool _busy = false;
  bool _stopped = false;

  // EDGE_ROI_PREVIEW_V1
  Uint8List? _roiPreview;
  List<Uint8List> _digitPreviews = [];

  String _status = '載入 YOLO v6 中…';
  String _lightStatus = '尚未確認';
  int? _seconds;
  String _digits = '--';
  String _strategy = '--';
  double? _cnnConfidence;
  double? _processingMs;

  // EDGE_TRACKER_INTEGRATION_V1
  final Stopwatch _trackingClock = Stopwatch()..start();
  final EdgeCountdownTracker _tracker = EdgeCountdownTracker();
  Timer? _trackingTimer;

  // EDGE_TTS_INTEGRATION_V2
  final FlutterTts _tts = FlutterTts();
  bool _ttsReady = false;
  bool _shortAnnounced = false;
  EdgeTrackingState _lastVoiceState = EdgeTrackingState.searching;

  @override
  void initState() {
    super.initState();
    _initialize();
    _setupTts();

    _trackingTimer = Timer.periodic(const Duration(milliseconds: 250), (_) {
      if (!mounted || _stopped) return;
      setState(() {
        _tracker.tick(_trackingClock.elapsedMilliseconds);
        _expireVerifiedCountdown();
        _handleTrackerSpeech();
      });
    });
  }

  Future<void> _initialize() async {
    try {
      final success = await _yolo.loadModel();

      if (!success) {
        throw StateError('YOLO v6 模型載入失敗');
      }

      _loaded = true;

      final cameras = await availableCameras();
      if (cameras.isEmpty) {
        throw StateError('找不到攝影機');
      }

      final selected = cameras.firstWhere(
        (camera) => camera.lensDirection == CameraLensDirection.back,
        orElse: () => cameras.first,
      );

      final controller = CameraController(
        selected,
        ResolutionPreset.high,
        enableAudio: false,
        imageFormatGroup: ImageFormatGroup.jpeg,
      );

      await controller.initialize();

      if (!mounted || _stopped) {
        await controller.dispose();
        return;
      }

      setState(() {
        _camera = controller;
        _status = 'Android 本地模型已載入，開始辨識';
      });

      _timer = Timer.periodic(
        const Duration(milliseconds: 1200),
        (_) => _processFrame(),
      );
    } catch (e, stack) {
      debugPrint('EDGE INIT ERROR: $e');
      debugPrintStack(stackTrace: stack);

      if (mounted) {
        setState(() => _status = '初始化失敗：$e');
      }
    }
  }

  Uint8List _extractRoi(img.Image image, YOLOResult detection) {
    final box = detection.boundingBox;

    final x1 = box.left.toDouble();
    final y1 = box.top.toDouble();
    final x2 = box.right.toDouble();
    final y2 = box.bottom.toDouble();

    final padX = (x2 - x1) * 0.10;
    final padY = (y2 - y1) * 0.10;

    final left = (x1 - padX).floor().clamp(0, image.width);
    final top = (y1 - padY).floor().clamp(0, image.height);
    final right = (x2 + padX).ceil().clamp(0, image.width);
    final bottom = (y2 + padY).ceil().clamp(0, image.height);

    if (right <= left || bottom <= top) {
      throw StateError('倒數 ROI 座標無效');
    }

    final roi = img.copyCrop(
      image,
      x: left,
      y: top,
      width: right - left,
      height: bottom - top,
    );

    return Uint8List.fromList(img.encodePng(roi));
  }

  Future<void> _processFrame() async {
    final camera = _camera;

    if (_stopped ||
        !_loaded ||
        _busy ||
        camera == null ||
        !camera.value.isInitialized ||
        camera.value.isTakingPicture) {
      return;
    }

    _busy = true;
    String? temporaryPath;
    final watch = Stopwatch()..start();

    try {
      // 使用影格拍攝開始時的時間，補償推論延遲。
      final capturedAtMs = _trackingClock.elapsedMilliseconds;
      final picture = await camera.takePicture();
      temporaryPath = picture.path;

      final bytes = await picture.readAsBytes();
      final original = img.decodeImage(bytes);

      if (original == null) {
        throw StateError('攝影機 JPEG 解碼失敗');
      }

      final decoded = img.bakeOrientation(original);
      final normalizedBytes = Uint8List.fromList(
        img.encodeJpg(decoded, quality: 95),
      );

      debugPrint(
        'EDGE ORIENTATION: original='
        '${original.width}x${original.height} '
        'normalized=${decoded.width}x${decoded.height}',
      );

      // 由 Android 本機 YOLO v6 執行，不呼叫 FastAPI。
      final response = await _yolo.predict(
        normalizedBytes,
        confidenceThreshold: 0.10,
        iouThreshold: 0.50,
      );

      debugPrint('EDGE DEBUG YOLO KEYS: ${response.keys.toList()}');
      debugPrint('EDGE DEBUG IMAGE: ${decoded.width}x${decoded.height}');

      debugPrint('EDGE DEBUG YOLO IMAGE SIZE: ${response['imageSize']}');
      final raw = response['detections'];

      if (raw is! List) {
        throw StateError('YOLO 輸出格式不符：${response.keys.toList()}');
      }

      final detections = raw
          .map((value) => YOLOResult.fromMap(value as Map<dynamic, dynamic>))
          .toList();

      debugPrint('EDGE DEBUG DETECTIONS: ${detections.length}');
      for (final d in detections) {
        debugPrint(
          'EDGE BOX: ${d.className} '
          'conf=${d.confidence.toStringAsFixed(3)} '
          'box=${d.boundingBox}',
        );
      }

      final redDetected = detections.any(
        (d) => d.className == 'pedestrian_red' && d.confidence >= 0.25,
      );

      final greenDetected = detections.any(
        (d) => d.className == 'pedestrian_green' && d.confidence >= 0.15,
      );

      final countdowns =
          detections
              .where(
                (d) => d.className == 'countdown_timer' && d.confidence >= 0.25,
              )
              .toList()
            ..sort((a, b) => b.confidence.compareTo(a.confidence));

      // 只是觀察資訊，不能用來判定安全通行。
      final light = redDetected && greenDetected
          ? '燈號衝突，未確認'
          : redDetected
          ? '偵測到紅燈'
          : greenDetected
          ? '偵測到綠燈候選'
          : '未確認';

      if (redDetected) {
        _redConsecutive++;
        _redMissing = 0;
        _redHold = true;
      } else if (_redHold) {
        _redMissing++;
        if (_redMissing >= 3) {
          _redHold = false;
          _redAnnounced = false;
          _redConsecutive = 0;
          _redMissing = 0;
        }
      }

      // 只要仍有紅燈候選或尚未解除紅燈保留狀態，
      // 就不使用這段影格的倒數讀數建立追蹤。
      if (_redHold) {
        _greenConsecutive = 0;
        _greenAnnounced = false;
        _greenIntroFinished = false;
        _clearVerifiedCountdown();
        _tracker.reset(nextState: EdgeTrackingState.lost);
        _lastVoiceState = EdgeTrackingState.lost;

        // 連續兩個處理影格偵測紅燈，才發出明確提醒。
        if (_redConsecutive >= 2 && !_redAnnounced && _ttsReady) {
          _redAnnounced = true;
          _speak('紅燈，請等待', priority: 3, interrupt: true);
        }

        if (!mounted || _stopped) return;
        setState(() {
          _seconds = null;
          _digits = '--';
          _cnnConfidence = null;
          _strategy = '--';
          _roiPreview = null;
          _digitPreviews = [];
          _lightStatus = redDetected ? light : '號誌重新確認中';
          _status = '紅燈候選／確認中，暫停倒數追蹤';
          _processingMs = watch.elapsedMicroseconds / 1000;
        });
        return;
      }

      // 綠燈必須連續兩次被偵測，才播報一次。
      if (greenDetected && !redDetected) {
        _greenConsecutive++;

        if (_greenConsecutive >= 2 && !_greenAnnounced) {
          _greenAnnounced = true;
          _greenIntroFinished = false;
          _speak('綠燈', priority: 2);
          debugPrint('EDGE GREEN: confirmed, speech requested');
        }
      } else {
        _greenConsecutive = 0;
      }

      if (countdowns.isEmpty) {
        _clearVerifiedCountdown();
        if (!mounted || _stopped) return;

        setState(() {
          _seconds = null;
          _digits = '--';
          _cnnConfidence = null;
          _strategy = '--';
          _roiPreview = null;
          _digitPreviews = [];
          _lightStatus = light;
          _status = '未偵測到有效倒數框';
          _processingMs = watch.elapsedMicroseconds / 1000;
        });
        return;
      }

      // 使用與原本圖片測試頁相同的 boundingBox 裁切邏輯。

      final roi = _extractRoi(decoded, countdowns.first);
      debugPrint('EDGE DEBUG ROI BYTES: ${roi.length}');

      // 優先使用之前在 36 測試成功的不重疊分割。
      final segmentation = await CountdownSegmenterService().segment(
        roi,
        noOverlap: true,
      );

      // Kotlin OpenCV D5 + 已驗證的 CNN E3 FP16。
      debugPrint(
        'EDGE DEBUG SEGMENTER: '
        'count=${segmentation.digitCount} '
        'strategy=${segmentation.strategy} '
        'boxes=${segmentation.boxes}',
      );

      final recognition = await CountdownFp16Service().recognize(segmentation);

      final minConfidence = recognition.confidences.reduce(math.min);

      final observedSeconds = recognition.seconds;
      if (!mounted || _stopped) return;
      final accepted =
          !_redHold &&
          !redDetected &&
          _tracker.observe(
            seconds: observedSeconds,
            capturedAtMs: capturedAtMs,
            nowMs: _trackingClock.elapsedMilliseconds,
          );

      debugPrint(
        'EDGE TRACKER: raw=$observedSeconds '
        'state=${_tracker.state.name} '
        'predicted=${_tracker.predictedAt(_trackingClock.elapsedMilliseconds)} '
        'mismatches=${_tracker.mismatchCount}',
      );

      if (accepted) {
        _verifiedCnnSeconds = observedSeconds;
        _lastGoodCountdownAtMs = capturedAtMs;
      } else {
        _clearVerifiedCountdown();
      }
      _expireVerifiedCountdown();

      _handleTrackerSpeech();
      watch.stop();

      if (!mounted || _stopped) return;

      setState(() {
        _roiPreview = roi;
        _digitPreviews = List<Uint8List>.from(segmentation.crops);
        _seconds = _verifiedCnnSeconds;
        _digits = _verifiedCnnSeconds?.toString() ?? '--';
        _cnnConfidence = minConfidence;
        _strategy = segmentation.strategy;
        _lightStatus = light;
        _processingMs = watch.elapsedMicroseconds / 1000;
        _status = _verifiedCnnSeconds == null
            ? '本次讀數未通過追蹤驗證或已過期'
            : 'CNN 觀測已通過追蹤驗證';
      });

      debugPrint(
        'EDGE RESULT: digits=$_digits '
        'seconds=$_seconds '
        'confidence=$minConfidence '
        'ms=${_processingMs?.toStringAsFixed(1)}',
      );
    } catch (e, stack) {
      _clearVerifiedCountdown();
      _tracker.reset(nextState: EdgeTrackingState.lost);
      _handleTrackerSpeech();
      debugPrint('EDGE FRAME ERROR: $e');
      debugPrintStack(stackTrace: stack);

      if (mounted && !_stopped) {
        setState(() {
          _seconds = null;
          _digits = '--';
          _cnnConfidence = null;
          _status = '推論錯誤：$e';
          _roiPreview = null;
          _digitPreviews = [];
        });
      }
    } finally {
      if (temporaryPath != null) {
        try {
          await File(temporaryPath).delete();
        } catch (_) {}
      }
      _busy = false;
    }
  }

  @override
  void dispose() {
    _stopped = true;
    _timer?.cancel();
    _trackingTimer?.cancel();
    _trackingClock.stop();
    _tts.stop();
    _camera?.dispose();
    super.dispose();
  }

  Future<void> _setupTts() async {
    try {
      await _tts.setLanguage('zh-TW');
      await _tts.setSpeechRate(0.8);
      await _tts.awaitSpeakCompletion(true);

      if (!mounted || _stopped) return;

      _ttsReady = true;
      debugPrint('EDGE TTS READY');
    } catch (e) {
      debugPrint('EDGE TTS INIT ERROR: $e');
    }
  }

  // EDGE_TTS_QUEUE_V1
  // EDGE_RED_PRIORITY_V1
  int _redConsecutive = 0;
  int _redMissing = 0;
  bool _redHold = false;
  bool _redAnnounced = false;

  int _activeTtsPriority = 0;
  int _pendingTtsPriority = 0;

  // EDGE_DYNAMIC_SECONDS_TTS_V1
  int _lastCountdownSpeechAtMs = -100000;
  static const int _countdownSpeechIntervalMs = 5000;

  // EDGE_GREEN_AND_GRACE_V1
  int _greenConsecutive = 0;
  bool _greenAnnounced = false;
  bool _greenIntroFinished = false;
  int? _lastGoodCountdownAtMs;
  int? _verifiedCnnSeconds;
  int? _lastSpokenCnnAtMs;
  static const int _staleSpeechLimitMs = 3000;

  bool _ttsSpeaking = false;
  String? _pendingTts;

  Future<void> _speak(
    String message, {
    int priority = 1,
    bool interrupt = false,
  }) async {
    if (!_ttsReady || !mounted || _stopped) return;

    if (_ttsSpeaking) {
      if (_pendingTts == null || priority >= _pendingTtsPriority) {
        _pendingTts = message;
        _pendingTtsPriority = priority;
      }

      // 最高優先級的紅燈訊息，可以中斷一般語音。
      if (interrupt && priority > _activeTtsPriority) {
        debugPrint('EDGE TTS INTERRUPT: $message');
        try {
          await _tts.stop();
        } catch (e) {
          debugPrint('EDGE TTS STOP ERROR: $e');
        }
      }

      return;
    }

    _ttsSpeaking = true;
    var current = message;
    var currentPriority = priority;

    try {
      while (mounted && !_stopped) {
        _activeTtsPriority = currentPriority;
        debugPrint('EDGE TTS SPEAK: $current');

        await _tts.speak(current);

        if (current == '綠燈' && !_redHold && mounted && !_stopped) {
          _greenIntroFinished = true;
          // 下一次倒數播報重新讀取最新秒數。
          _lastCountdownSpeechAtMs = -100000;
          debugPrint('EDGE GREEN: speech completed');
        }

        if (!mounted || _stopped) break;

        final next = _pendingTts;
        final nextPriority = _pendingTtsPriority;
        _pendingTts = null;
        _pendingTtsPriority = 0;

        if (next == null) break;

        // 紅燈尚未解除時，不播放舊的倒數資訊。
        if (_redHold && nextPriority < 3) break;

        current = next;
        currentPriority = nextPriority;
      }
    } catch (e) {
      debugPrint('EDGE TTS ERROR: $e');
    } finally {
      _ttsSpeaking = false;
      _activeTtsPriority = 0;
      _pendingTts = null;
      _pendingTtsPriority = 0;
    }
  }

  void _clearVerifiedCountdown() {
    _verifiedCnnSeconds = null;
    _lastGoodCountdownAtMs = null;
    _seconds = null;
    _digits = '--';
  }

  void _expireVerifiedCountdown() {
    final capturedAt = _lastGoodCountdownAtMs;
    final now = _trackingClock.elapsedMilliseconds;
    if (_redHold ||
        _tracker.state != EdgeTrackingState.tracking ||
        capturedAt == null ||
        now < capturedAt ||
        now - capturedAt > _staleSpeechLimitMs) {
      _clearVerifiedCountdown();
    }
    if (_tracker.state == EdgeTrackingState.lost) {
      _shortAnnounced = false;
    }
  }

  void _handleTrackerSpeech() {
    if (!_ttsReady || !mounted || _stopped || _redHold) {
      return;
    }

    final state = _tracker.state;
    final now = _trackingClock.elapsedMilliseconds;

    if (state == EdgeTrackingState.tracking) {
      final canAnnounce = EdgeTtsGuard.canAnnounceCountdown(
        ttsReady: _ttsReady,
        redHold: _redHold,
        greenConsecutive: _greenConsecutive,
        greenAnnounced: _greenAnnounced,
        greenIntroFinished: _greenIntroFinished,
        tracking: _tracker.state == EdgeTrackingState.tracking,
        mismatchCount: _tracker.mismatchCount,
        lastGoodCountdownAtMs: _lastGoodCountdownAtMs,
        nowMs: now,
      );

      if (!canAnnounce) {
        _lastVoiceState = state;
        return;
      }

      // 超過三秒沒有有效更新，不再播報倒數。
      // 本地時鐘仍可暫時維持追蹤，等待鏡頭重新取得號誌。
      final lastGood = _lastGoodCountdownAtMs;
      if (lastGood == null || now - lastGood > _staleSpeechLimitMs) {
        _lastVoiceState = state;
        return;
      }

      final remaining = _verifiedCnnSeconds;
      final observationAtMs = _lastGoodCountdownAtMs;
      if (remaining != null &&
          remaining > 0 &&
          observationAtMs != null &&
          _lastSpokenCnnAtMs != observationAtMs) {
        final intervalReached =
            now - _lastCountdownSpeechAtMs >= _countdownSpeechIntervalMs;
        final firstWarning = remaining < 15 && !_shortAnnounced;
        if (!_ttsSpeaking &&
            _pendingTts == null &&
            (intervalReached || firstWarning)) {
          _lastCountdownSpeechAtMs = now;
          _lastSpokenCnnAtMs = observationAtMs;
          if (remaining < 15) _shortAnnounced = true;
          _speak(
            remaining < 15 ? '剩餘 $remaining 秒，建議等待' : '剩餘 $remaining 秒',
            priority: remaining < 15 ? 2 : 1,
          );
        }
      }
    }

    if (state == EdgeTrackingState.lost &&
        _lastVoiceState == EdgeTrackingState.tracking) {
      _greenConsecutive = 0;
      _greenAnnounced = false;
      _greenIntroFinished = false;
      _clearVerifiedCountdown();

      _speak('辨識中斷，請等待', priority: 2);
    }

    _lastVoiceState = state;
  }

  Widget _diagnosticImage(String title, Uint8List bytes) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 6),
      child: Column(
        children: [
          Text(title, style: const TextStyle(fontSize: 12)),
          const SizedBox(height: 4),
          Container(
            width: 94,
            height: 90,
            decoration: BoxDecoration(
              color: Colors.black,
              border: Border.all(color: Colors.grey),
            ),
            child: Image.memory(
              bytes,
              fit: BoxFit.contain,
              filterQuality: FilterQuality.none,
              gaplessPlayback: false,
            ),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final camera = _camera;

    return Scaffold(
      appBar: AppBar(title: const Text('幫幫盲｜Android 離線辨識')),
      body: SafeArea(
        child: Column(
          children: [
            Expanded(
              child: camera != null && camera.value.isInitialized
                  ? CameraPreview(camera)
                  : const Center(child: CircularProgressIndicator()),
            ),
            Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                children: [
                  Text(_status, textAlign: TextAlign.center),
                  const SizedBox(height: 12),
                  Text('燈號：$_lightStatus'),
                  const SizedBox(height: 8),
                  Text(
                    '已驗證辨識：$_digits',
                    style: const TextStyle(
                      fontSize: 28,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  Text(
                    _seconds == null ? '剩餘秒數：--' : '剩餘秒數：$_seconds',
                    style: const TextStyle(fontSize: 22),
                  ),
                  const SizedBox(height: 8),
                  Text('分割策略：$_strategy'),
                  const SizedBox(height: 12),
                  const Divider(),
                  const Text(
                    '時間一致性追蹤（實驗功能）',
                    style: TextStyle(fontWeight: FontWeight.bold, fontSize: 17),
                  ),
                  const SizedBox(height: 8),
                  Text('追蹤狀態：${_tracker.state.name.toUpperCase()}'),
                  Text(
                    '已驗證 CNN 秒數：${_verifiedCnnSeconds?.toString() ?? "--"}',
                    style: const TextStyle(fontSize: 20),
                  ),
                  Text('連續異常讀數：${_tracker.mismatchCount}'),
                  const Text(
                    '追蹤僅作診斷，不代表燈號或通行安全已確認',
                    style: TextStyle(fontSize: 12),
                  ),

                  if (_roiPreview != null) ...[
                    const SizedBox(height: 12),
                    const Text(
                      '即時裁切診斷',
                      style: TextStyle(fontWeight: FontWeight.bold),
                    ),
                    const SizedBox(height: 8),
                    SizedBox(
                      height: 118,
                      child: SingleChildScrollView(
                        scrollDirection: Axis.horizontal,
                        child: Row(
                          children: [
                            _diagnosticImage('YOLO ROI', _roiPreview!),
                            for (var i = 0; i < _digitPreviews.length; i++)
                              _diagnosticImage('Digit $i', _digitPreviews[i]),
                          ],
                        ),
                      ),
                    ),
                  ],
                  Text(
                    '最低單數字信心：'
                    '${_cnnConfidence == null ? "--" : "${(_cnnConfidence! * 100).toStringAsFixed(1)}%"}',
                  ),
                  Text(
                    '單次處理耗時：'
                    '${_processingMs?.toStringAsFixed(0) ?? "--"} ms',
                  ),
                  const SizedBox(height: 8),
                  const Text(
                    '離線實驗模式：未驗證安全通行，不提供過馬路指示',
                    style: TextStyle(fontSize: 12),
                    textAlign: TextAlign.center,
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
