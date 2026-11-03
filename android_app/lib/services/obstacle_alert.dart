import 'dart:async';
import 'dart:math' as math;
import 'dart:typed_data';

import 'package:audioplayers/audioplayers.dart';
import 'package:flutter/foundation.dart';

import 'obstacle_decision.dart';

// ====================================
// 共用 TTS 服務接口
// ====================================

typedef ObstacleSpeechRequest =
    Future<void> Function(
  String text,
  int priority,
);

// ====================================
// 共用音效服務接口
// ====================================

typedef ObstacleToneRequest =
    Future<void> Function(
  ObstacleToneEvent event,
);

// ====================================
// 提示音種類
// ====================================

enum ObstacleToneKind {
  frontDouble,
  leftDouble,
  rightDouble,

  frontFast,
  leftFast,
  rightFast,

  stop,
}

class ObstacleToneEvent {
  final ObstacleToneKind kind;
  final int priority;
  final Uint8List? wavBytes;

  const ObstacleToneEvent({
    required this.kind,
    required this.priority,
    this.wavBytes,
  });
}

// ====================================
// 避障警示服務
// ====================================

class ObstacleAlert {
  ObstacleAlert({
    this.onSpeechRequested,
    this.onToneRequested,
  });

  final ObstacleSpeechRequest? onSpeechRequested;
  final ObstacleToneRequest? onToneRequested;

  // 同一個物件是否已經叮兩聲
  final Set<int> _dingedTrackIds = {};

  // 同一個物件是否已經播報 TTS
  final Set<int> _spokenTrackIds = {};

  // 快速短音的節奏控制
  int? _lastFastTrackId;
  ObstacleToneKind? _lastFastToneKind;
  DateTime? _lastFastToneAt;

  // 快速短音每 0.6 秒一次
  static const Duration _fastInterval =
      Duration(milliseconds: 600);

  // 避免提示音同時重疊播放
  bool _toneBusy = false;
  bool _hasTone = false;

  // 音效失效控制
  int _toneEpoch = 0;

  AudioPlayer? _tonePlayer;

  // 已產生的音效快取
  final Map<ObstacleToneKind, Uint8List>
      _soundCache = {};

  // ====================================
  // 初始化
  // ====================================

  Future<void> initialize() async {
    if (onToneRequested == null &&
        _tonePlayer == null) {
      final player = AudioPlayer();

      await player.setPlayerMode(
        PlayerMode.lowLatency,
      );

      await player.setReleaseMode(
        ReleaseMode.stop,
      );

      // 先用較溫和的音量測試
      await player.setVolume(0.55);

      _tonePlayer = player;
    }

    if (onSpeechRequested == null) {
      debugPrint(
        'ObstacleAlert：尚未接上共用 TTS，'
      );
    }
  }

  // ====================================
  // 清除已失效的追蹤 ID
  // ====================================

  void retainTracks(Set<int> activeIds) {
    _dingedTrackIds.removeWhere(
      (id) => !activeIds.contains(id),
    );

    _spokenTrackIds.removeWhere(
      (id) => !activeIds.contains(id),
    );

    if (_lastFastTrackId != null &&
        !activeIds.contains(_lastFastTrackId)) {
      _lastFastTrackId = null;
      _lastFastToneKind = null;
      _lastFastToneAt = null;
    }
  }

  // ====================================
  // 接收 Decision 結果
  // ====================================

  Future<void> handle(
    ObstacleDecisionResult result,
  ) async {
    // 沒有有效障礙物
    if (result.warningType ==
        ObstacleWarningType.none) {
      await _stopTone();
      return;
    }

    final obstacle = result.obstacle;
    final trackId = result.trackId;

    if (obstacle == null || trackId == null) {
      return;
    }

    // =================================
    // 判斷是否已進入 1.5m
    // =================================

    final isNear =
        result.warningType ==
            ObstacleWarningType.frontDanger ||
        result.warningType ==
            ObstacleWarningType.sideCritical;

    // =================================
    // 是否位於正中
    // =================================

    final isFront =
        result.direction ==
            ObstacleDirection.center;

    // =================================
    // 左、中、右聲道
    // =================================

    final bool isLeft =
        result.direction ==
            ObstacleDirection.left ||
        result.direction ==
            ObstacleDirection.leftCenter;

    final bool isRight =
        result.direction ==
            ObstacleDirection.right ||
        result.direction ==
            ObstacleDirection.rightCenter;

    late final ObstacleToneKind doubleTone;
    late final ObstacleToneKind fastTone;

    if (isFront) {
      doubleTone = ObstacleToneKind.frontDouble;
      fastTone = ObstacleToneKind.frontFast;
    } else if (isLeft) {
      doubleTone = ObstacleToneKind.leftDouble;
      fastTone = ObstacleToneKind.leftFast;
    } else if (isRight) {
      doubleTone = ObstacleToneKind.rightDouble;
      fastTone = ObstacleToneKind.rightFast;
    } else {
      return;
    }

    final priority = isNear ? 2 : 3;
    final now = DateTime.now();

    // =================================
    // 第一階段：第一次進入警示範圍
    // =================================

    final firstWarning =
        _dingedTrackIds.add(trackId);

    if (firstWarning) {
      _requestTone(
        ObstacleToneEvent(
          kind: doubleTone,
          priority: priority,
          wavBytes: _soundFor(doubleTone),
        ),
      );


      if (isNear) {
        _lastFastTrackId = trackId;
        _lastFastToneKind = fastTone;
        _lastFastToneAt = now;
      }
    }

    // =================================
    // 第二階段：1.5m 持續快速短音
    // =================================

    else if (isNear) {
      final isDifferentFastTone =
          _lastFastTrackId != trackId ||
          _lastFastToneKind != fastTone;

      if (isDifferentFastTone) {
        _lastFastTrackId = trackId;
        _lastFastToneKind = fastTone;
        _lastFastToneAt = null;
      }

      final intervalPassed =
          _lastFastToneAt == null ||
          now.difference(_lastFastToneAt!) >=
              _fastInterval;

      if (intervalPassed) {
        _lastFastToneAt = now;

        _requestTone(
          ObstacleToneEvent(
            kind: fastTone,
            priority: 2,
            wavBytes: _soundFor(fastTone),
          ),
        );
      }
    }

    // =================================
    // 從 1.5m 回到 2m
    // =================================

    else {
      _lastFastTrackId = null;
      _lastFastToneKind = null;
      _lastFastToneAt = null;
    }

    // =================================
    // 左右兩側不播放 TTS
    // =================================

    if (!isFront) {
      return;
    }

    // =================================
    // 正中首次 TTS
    //
    // 同一個 trackId 只說一次。
    // =================================

    if (!_spokenTrackIds.add(trackId)) {
      return;
    }

    final message = _getSpeechMessage(
      obstacle.stableObjectType,
    );

    unawaited(
      _sendSpeech(
        message,
        priority,
      ),
    );
  }

  // ====================================
  // 執行短音
  // ====================================

  void _requestTone(
    ObstacleToneEvent event,
  ) {
    if (_toneBusy) {
      return;
    }

    _hasTone = true;

    final epoch = _toneEpoch;

    _toneBusy = true;

    unawaited(
      _playTone(event, epoch).whenComplete(() {
        _toneBusy = false;
      }),
    );
  }

  Future<void> _playTone(
    ObstacleToneEvent event,
    int epoch,
  ) async {
    try {
      if (epoch != _toneEpoch) {
        return;
      }

      // 優先交給整合端的共用音訊服務
      if (onToneRequested != null) {
        await onToneRequested!(event);
        return;
      }

      // 尚未整合共用提示音時，
      // 使用 audioplayers 備援播放
      final player = _tonePlayer;

      if (player == null ||
          event.wavBytes == null) {
        return;
      }

      await player.stop();

      if (epoch != _toneEpoch) {
        return;
      }

      await player.play(
        BytesSource(
          event.wavBytes!,
          mimeType: 'audio/wav',
        ),
      );
    } catch (error) {
      debugPrint(
        '避障提示音播放失敗：$error',
      );
    }
  }

  // ====================================
  // 停止提示音
  // ====================================

  Future<void> _stopTone() async {
    if (!_hasTone) {
      return;
    }

    _hasTone = false;

    _toneEpoch++;

    _lastFastTrackId = null;
    _lastFastToneKind = null;
    _lastFastToneAt = null;

    try {
      if (onToneRequested != null) {
        await onToneRequested!(
          const ObstacleToneEvent(
            kind: ObstacleToneKind.stop,
            priority: 2,
          ),
        );
      } else {
        await _tonePlayer?.stop();
      }
    } catch (error) {
      debugPrint(
        '停止避障提示音失敗：$error',
      );
    }
  }

  // ====================================
  // TTS 交由 AudioPriorityService
  // ====================================

  Future<void> _sendSpeech(
    String text,
    int priority,
  ) async {
    try {
      if (onSpeechRequested == null) {
        debugPrint(
          '預計播報 TTS：'
          'priority=$priority，$text',
        );
        return;
      }

      await onSpeechRequested!(
        text,
        priority,
      );
    } catch (error) {
      debugPrint(
        '共用 TTS 播報失敗：$error',
      );
    }
  }

  // ====================================
  // 攝影機異常播報
  // ====================================

  Future<void> announceCameraFailure() async {
    await _sendSpeech(
      '攝影機連線異常，'
      '避障功能暫時無法使用。',
      2,
    );
  }

  // ====================================
  // 取得中文語音內容
  // ====================================

  String _getSpeechMessage(
    String? objectType,
  ) {
    switch (objectType?.trim().toLowerCase()) {
      case 'pole':
        return '前電線桿';

      case 'transformer box':
        return '前變電箱';

      case 'ground billboard':
        return '前廣告看板';

      case 'rear overhang':
        return '前車尾突出物';

      default:
        return '前障礙物';
    }
  }

  // ====================================
  // 取得或產生短音
  // ====================================

  Uint8List _soundFor(
    ObstacleToneKind kind,
  ) {
    return _soundCache.putIfAbsent(
      kind,
      () => _generateStereoWav(kind),
    );
  }

  // ====================================
  // 產生柔和的立體聲「叮」聲
  //
  // 2m：兩聲，一組 WAV
  // 1.5m：單聲，每 0.6 秒觸發
  // ====================================

  Uint8List _generateStereoWav(
    ObstacleToneKind kind,
  ) {
    const int sampleRate = 22050;
    const int channels = 2;
    const int bitsPerSample = 16;

    // 每個叮聲約 0.13 秒
    const double dingDuration = 0.13;

    // 兩聲開始時間相隔 0.25 秒
    const double secondDingStart = 0.25;

    final bool isDouble =
        kind == ObstacleToneKind.frontDouble ||
        kind == ObstacleToneKind.leftDouble ||
        kind == ObstacleToneKind.rightDouble;

    final bool isFront =
        kind == ObstacleToneKind.frontDouble ||
        kind == ObstacleToneKind.frontFast;

    final bool isLeft =
        kind == ObstacleToneKind.leftDouble ||
        kind == ObstacleToneKind.leftFast;

    final bool isRight =
        kind == ObstacleToneKind.rightDouble ||
        kind == ObstacleToneKind.rightFast;

    final bool leftEnabled = isFront || isLeft;
    final bool rightEnabled = isFront || isRight;

    final totalDuration = isDouble
        ? secondDingStart + dingDuration
        : dingDuration;

    final sampleCount =
        (sampleRate * totalDuration).round();

    final dataSize =
        sampleCount * channels * 2;

    final wav = ByteData(44 + dataSize);

    void writeText(int offset, String value) {
      for (int i = 0; i < value.length; i++) {
        wav.setUint8(
          offset + i,
          value.codeUnitAt(i),
        );
      }
    }

    // WAV header
    writeText(0, 'RIFF');

    wav.setUint32(
      4,
      36 + dataSize,
      Endian.little,
    );

    writeText(8, 'WAVE');
    writeText(12, 'fmt ');

    wav.setUint32(16, 16, Endian.little);
    wav.setUint16(20, 1, Endian.little);
    wav.setUint16(22, channels, Endian.little);

    wav.setUint32(
      24,
      sampleRate,
      Endian.little,
    );

    wav.setUint32(
      28,
      sampleRate * channels * 2,
      Endian.little,
    );

    wav.setUint16(
      32,
      channels * 2,
      Endian.little,
    );

    wav.setUint16(
      34,
      bitsPerSample,
      Endian.little,
    );

    writeText(36, 'data');

    wav.setUint32(
      40,
      dataSize,
      Endian.little,
    );

    // 同一個柔和音色，
    const double frequency = 620.0;

    for (int i = 0; i < sampleCount; i++) {
      final double time = i / sampleRate;

      // 兩聲各自有自己的起音和衰減
      double localTime = time;

      if (isDouble && time >= secondDingStart) {
        localTime = time - secondDingStart;
      }

      double signal = 0;

      if (localTime >= 0 &&
          localTime < dingDuration) {
        // 柔和的淡入
        final fadeIn =
            (localTime / 0.008)
                .clamp(0.0, 1.0)
                .toDouble();

        // 模擬鈴聲逐漸衰減
        final decay =
            math.exp(-localTime * 19.0);

        // 結尾淡出，避免爆音
        final fadeOut =
            ((dingDuration - localTime) / 0.015)
                .clamp(0.0, 1.0)
                .toDouble();

        final envelope =
            fadeIn * decay * fadeOut;

        final fundamental = math.sin(
          2 * math.pi * frequency * localTime,
        );

        final harmonic = math.sin(
          2 * math.pi * frequency * 1.5 * localTime,
        );

        signal =
            (fundamental * 0.85 +
             harmonic * 0.15) *
            envelope *
            0.25;
      }

      final sample =
          (signal * 32767).round();

      final leftSample =
          leftEnabled ? sample : 0;

      final rightSample =
          rightEnabled ? sample : 0;

      final offset = 44 + i * 4;

      wav.setInt16(
        offset,
        leftSample,
        Endian.little,
      );

      wav.setInt16(
        offset + 2,
        rightSample,
        Endian.little,
      );
    }

    return wav.buffer.asUint8List();
  }

  // ====================================
  // 重設警示
  // ====================================

  Future<void> reset() async {
    await _stopTone();

    _dingedTrackIds.clear();
    _spokenTrackIds.clear();

    _lastFastTrackId = null;
    _lastFastToneKind = null;
    _lastFastToneAt = null;
  }

  // ====================================
  // 停止避障服務
  // ====================================

  Future<void> stop() async {
    await reset();

    final player = _tonePlayer;
    _tonePlayer = null;

    if (player != null) {
      await player.dispose();
    }

    // 不關閉全 App 共用 AudioPriorityService。
  }
}