import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:camera/camera.dart';
import 'package:flutter/material.dart';
import 'package:flutter_tts/flutter_tts.dart';
import 'package:http/http.dart' as http;

class CameraLiveTestPage extends StatefulWidget {
  const CameraLiveTestPage({super.key});

  @override
  State<CameraLiveTestPage> createState() =>
      _CameraLiveTestPageState();
}

class _CameraLiveTestPageState extends State<CameraLiveTestPage> {
  static const api = 'http://10.0.2.2:8004/traffic/detect';

  final FlutterTts _tts = FlutterTts();
  final Stopwatch _clock = Stopwatch()..start();

  CameraController? _camera;
  Timer? _captureTimer;
  Timer? _countdownTimer;

  bool _running = false;
  bool _busy = false;
  bool _ttsReady = false;
  bool _crossing = false;
  bool _redAnnounced = false;
  bool _readyAnnounced = false;
  bool _shortAnnounced = false;

  String _status = '正在啟動攝影機...';
  String? _light;
  int? _rawSeconds;
  double? _cnnConfidence;
  bool _countdownDetected = false;

  // 以下狀態僅供 Demo 使用，不影響後端安全判斷。
  bool demoGreenAssumed = false;

  int? _candidateSeconds;
  int? _candidateAtMs;

  int? _anchorSeconds;
  int? _anchorAtMs;
  int? _lastValidAtMs;

  int? _lastSpokenSeconds;
  int _lastSpokenAtMs = -100000;
  int _mismatchCount = 0;

  @override
  void initState() {
    super.initState();
    _setupTts();
    _initializeCamera();

    _countdownTimer = Timer.periodic(
      const Duration(milliseconds: 250),
      (_) => _tick(),
    );
  }

  int get _nowMs => _clock.elapsedMilliseconds;

  int? _predictedAt(int timestampMs) {
    if (_anchorSeconds == null || _anchorAtMs == null) {
      return null;
    }

    final elapsed = timestampMs - _anchorAtMs!;
    final deduction = elapsed <= 0
        ? 0
        : (elapsed + 999) ~/ 1000;

    final remaining = _anchorSeconds! - deduction;
    return remaining > 0 ? remaining : 0;
  }

  void _resetTracking(String reason) {
    _anchorSeconds = null;
    _anchorAtMs = null;
    _lastValidAtMs = null;
    _candidateSeconds = null;
    _candidateAtMs = null;
    _mismatchCount = 0;
    demoGreenAssumed = false;
    _readyAnnounced = false;
    _shortAnnounced = false;
    _lastSpokenSeconds = null;

    debugPrint('TRACK STOP: $reason');

    if (mounted) {
      setState(() {
        _status = reason;
      });
    }
  }

  void _acceptObservation(
    int seconds,
    int capturedAtMs,
  ) {
    if (seconds < 1 || seconds > 90) return;

    // 尚未啟動：要求連續兩次合理的倒數讀數。
    if (_anchorSeconds == null) {
      if (_candidateSeconds == null ||
          _candidateAtMs == null ||
          capturedAtMs - _candidateAtMs! > 4000) {
        _candidateSeconds = seconds;
        _candidateAtMs = capturedAtMs;
        return;
      }

      final elapsed =
          capturedAtMs - _candidateAtMs!;

      final expected = _candidateSeconds! -
          ((elapsed + 999) ~/ 1000);

      final reasonable =
          (seconds - expected).abs() <= 2 &&
          seconds <= _candidateSeconds!;

      if (!reasonable) {
        _candidateSeconds = seconds;
        _candidateAtMs = capturedAtMs;
        return;
      }

      _anchorSeconds = seconds;
      _anchorAtMs = capturedAtMs;
      _lastValidAtMs = _nowMs;
      _mismatchCount = 0;
      demoGreenAssumed = true;

      final remaining = _predictedAt(_nowMs);

      if (!_readyAnnounced &&
          remaining != null &&
          remaining >= 15) {
        _readyAnnounced = true;
        final actualGreen = _light == 'green';

        _speak(
          actualGreen
              ? '偵測到綠燈，預估剩餘 $remaining 秒，'
                '請確認路況後再開始過馬路'
              : '測試模式，偵測到倒數，'
                '預估剩餘 $remaining 秒，請確認號誌',
        );
      }

      debugPrint(
        'TRACK START: $seconds seconds '
        '(demo_green_assumed=true)',
      );
      return;
    }

    // 已啟動：新讀數只用來確認和校正。
    final predicted = _predictedAt(capturedAtMs);
    if (predicted == null) return;

    final difference = (seconds - predicted).abs();

    if (difference > 2) {
      _mismatchCount++;

      debugPrint(
        'TRACK OUTLIER IGNORED: CNN=$seconds '
        'predicted=$predicted count=$_mismatchCount',
      );

      // 例如 15 -> 14 -> 13 -> 85：
      // 忽略 85，本地時鐘繼續推算。
      // SEARCHING 模式仍受 4.5 秒資料過期保護。
      return;
    }

    _mismatchCount = 0;
    _lastValidAtMs = _nowMs;

    // 只有差距達 1 秒以上才校正，減少抖動。
    if (difference >= 2) {
      _anchorSeconds = seconds;
      _anchorAtMs = capturedAtMs;
      debugPrint('TRACK CORRECT: $seconds seconds');
    }
  }

  void _startCrossing() {
    final remaining = _predictedAt(_nowMs);

    // 固定 15 秒門檻只適用於受控 Demo。
    if (_crossing ||
        !demoGreenAssumed ||
        remaining == null ||
        remaining < 15 ||
        _light == 'red') {
      return;
    }

    _captureTimer?.cancel();
    _tts.stop();

    setState(() {
      _crossing = true;
      _status = '過馬路模式：本地自主倒數';
      _lastSpokenSeconds = null;
      _lastSpokenAtMs = -100000;
    });

    debugPrint('CROSSING START: $remaining seconds');
  }

  void _finishCrossing(String reason) {
    if (!_crossing) return;

    _crossing = false;
    _tts.stop();
    _resetTracking(reason);

    // 回到搜尋模式，重新取得新的號誌秒數。
    _captureTimer?.cancel();
    if (_running && mounted) {
      _captureTimer = Timer.periodic(
        const Duration(milliseconds: 800),
        (_) => _processFrame(),
      );
    }

    debugPrint('CROSSING END: $reason');
  }

  void _tick() {
    if (!mounted) return;

    final now = _nowMs;

    if (_anchorSeconds != null) {
      if (!_crossing &&
          (_lastValidAtMs == null ||
           now - _lastValidAtMs! > 4500)) {
        _resetTracking('辨識資訊過期，停止倒數');
        return;
      }

      final remaining = _predictedAt(now);

      if (remaining == null || remaining <= 0) {
        if (_crossing) {
          _finishCrossing('倒數結束，重新搜尋號誌');
        } else {
          _resetTracking('倒數結束');
        }
        return;
      }

      // 已確認倒數追蹤，但 Demo 剩餘秒數不足門檻。
      if (!_crossing &&
          remaining < 15 &&
          !_shortAnnounced &&
          _ttsReady) {
        _shortAnnounced = true;
        debugPrint(
          'WAIT ANNOUNCEMENT: remaining=$remaining',
        );
        _speak('剩餘時間不足十五秒，請等待');
      }

      // 播報由本地時鐘觸發，不依賴 CNN 回傳。
      if (_crossing &&
          _ttsReady &&
          now - _lastSpokenAtMs >= 2000 &&
          _lastSpokenSeconds != remaining) {
        _lastSpokenAtMs = now;
        _lastSpokenSeconds = remaining;

        // 暫以 1000ms 預估實際開始發聲時的時間。
        // 這是 Demo 校正參數，後續須以實測確認。
        final spokenSeconds =
            _predictedAt(_nowMs) ?? remaining;

        if (spokenSeconds > 0) {
          debugPrint(
            'LOCAL TTS: predicted=$remaining '
            'spoken=$spokenSeconds',
          );
          _speak('$spokenSeconds 秒');
        }
      }
    }

    setState(() {});
  }

  Future<void> _setupTts() async {
    try {
      await _tts.setLanguage('zh-TW');
      await _tts.setSpeechRate(0.8);
      await _tts.awaitSpeakCompletion(false);

      _tts.setStartHandler(() {
        debugPrint('TTS START');
      });

      _tts.setErrorHandler((message) {
        debugPrint('TTS ERROR: $message');
      });

      if (!mounted) return;
      _ttsReady = true;
      debugPrint('TTS READY');
    } catch (e) {
      debugPrint('TTS INIT ERROR: $e');
    }
  }

  Future<void> _speak(String message) async {
    try {
      await _tts.stop();
      if (!mounted) return;
      await _tts.speak(message);
    } catch (e) {
      debugPrint('TTS SPEAK ERROR: $e');
    }
  }

  Future<void> _initializeCamera() async {
    try {
      final cameras = await availableCameras();

      if (cameras.isEmpty) {
        throw Exception('找不到攝影機');
      }

      // 使用後鏡頭，對應模擬器 Back Camera = Webcam0。
      final selectedCamera = cameras.firstWhere(
        (camera) =>
            camera.lensDirection == CameraLensDirection.back,
        orElse: () => cameras.first,
      );

      debugPrint(
        'CAMERA SELECTED: ${selectedCamera.name} '
        '${selectedCamera.lensDirection}',
      );

      final controller = CameraController(
        selectedCamera,
        ResolutionPreset.medium,
        enableAudio: false,
        imageFormatGroup: ImageFormatGroup.jpeg,
      );

      await controller.initialize();

      if (!mounted) {
        await controller.dispose();
        return;
      }

      setState(() {
        _camera = controller;
        _running = true;
        _status = '攝影機已啟動，搜尋倒數';
      });

      _captureTimer = Timer.periodic(
        const Duration(milliseconds: 800),
        (_) => _processFrame(),
      );
    } catch (e) {
      if (mounted) {
        setState(() => _status = '攝影機錯誤：$e');
      }
    }
  }

  Future<void> _processFrame() async {
    final camera = _camera;

    if (!_running ||
        _crossing ||
        _busy ||
        camera == null ||
        !camera.value.isInitialized ||
        camera.value.isTakingPicture) {
      return;
    }

    _busy = true;
    String? picturePath;

    try {
      // 記錄影格擷取時間，用來補償辨識延遲。
      final capturedAtMs = _nowMs;
      final picture = await camera.takePicture();
      picturePath = picture.path;

      final request = http.MultipartRequest(
        'POST',
        Uri.parse(api),
      );

      request.files.add(
        await http.MultipartFile.fromPath(
          'file',
          picture.path,
        ),
      );

      final response = await request.send().timeout(
        const Duration(seconds: 15),
      );

      final body = await response.stream.bytesToString();

      if (response.statusCode != 200) {
        throw Exception('HTTP ${response.statusCode}');
      }

      final result = jsonDecode(body)
          as Map<String, dynamic>;

      final data = Map<String, dynamic>.from(
        result['data'] as Map,
      );

      if (!mounted || _crossing) return;

      final light = data['light_color'] as String?;
      final seconds =
          (data['raw_seconds'] as num?)?.toInt();

      _light = light;
      _rawSeconds = seconds;
      _cnnConfidence =
          (data['cnn_confidence'] as num?)?.toDouble();
      _countdownDetected =
          data['countdown_detected'] == true;

      if (light == 'red') {
        if (!_redAnnounced) {
          _redAnnounced = true;
          _speak('目前紅燈，請等待');
        }

        if (_anchorSeconds != null ||
            demoGreenAssumed) {
          _resetTracking('目前紅燈，持續等待綠燈');
        }
      } else {
        _redAnnounced = false;

        if (seconds != null && _countdownDetected) {
          // 僅限受控 Demo：已排除紅燈倒數場景。
          _acceptObservation(seconds, capturedAtMs);
        }
      }

      setState(() {
        if (_anchorSeconds == null) {
          _status = '搜尋／確認倒數中';
        } else {
          _status = 'Demo 本地倒數追蹤中';
        }
      });
    } catch (e) {
      debugPrint('FRAME ERROR: $e');
      if (mounted) {
        setState(() => _status = '辨識錯誤：$e');
      }
    } finally {
      if (picturePath != null) {
        try {
          await File(picturePath).delete();
        } catch (_) {}
      }
      _busy = false;
    }
  }

  @override
  void dispose() {
    _running = false;
    _captureTimer?.cancel();
    _countdownTimer?.cancel();
    _tts.stop();
    _camera?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final camera = _camera;
    final localSeconds = _predictedAt(_nowMs);

    return Scaffold(
      appBar: AppBar(
        title: const Text('幫幫盲｜Demo 倒數追蹤'),
      ),
      body: Column(
        children: [
          Expanded(
            child: camera != null &&
                    camera.value.isInitialized
                ? CameraPreview(camera)
                : const Center(
                    child: CircularProgressIndicator(),
                  ),
          ),
          Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              children: [
                Text(_status),
                const SizedBox(height: 8),
                Text('YOLO 燈號：${_light ?? '未確認'}'),
                Text('倒數框：$_countdownDetected'),
                Text('CNN 候選：${_rawSeconds ?? '無'}'),
                Text(
                  'CNN 信心：'
                  '${_cnnConfidence?.toStringAsFixed(3) ?? '--'}',
                ),
                const SizedBox(height: 8),
                Text(
                  '本地預測：'
                  '${localSeconds == null ? '--' : '$localSeconds 秒'}',
                  style: const TextStyle(
                    fontSize: 26,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                Text(
                  'demo_green_assumed：$demoGreenAssumed',
                ),
                const SizedBox(height: 12),
                if (!_crossing)
                  ElevatedButton.icon(
                    onPressed: demoGreenAssumed &&
                            localSeconds != null &&
                            localSeconds >= 15 &&
                            _light != 'red'
                        ? _startCrossing
                        : null,
                    icon: const Icon(Icons.directions_walk),
                    label: const Text('開始過馬路（Demo）'),
                  )
                else
                  ElevatedButton.icon(
                    onPressed: () => _finishCrossing(
                      '手動結束，重新搜尋號誌',
                    ),
                    icon: const Icon(Icons.stop),
                    label: const Text('結束過馬路'),
                  ),
                const SizedBox(height: 8),
                const Text(
                  '僅限受控 Demo，不能作為實際通行依據',
                  style: TextStyle(fontSize: 12),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
