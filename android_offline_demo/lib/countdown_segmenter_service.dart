import 'package:flutter/services.dart';

class CountdownSegmentation {
  final int digitCount;
  final String strategy;
  final List<List<int>> boxes;
  final double aspectRatio;
  final List<Uint8List> crops;

  const CountdownSegmentation({
    required this.digitCount,
    required this.strategy,
    required this.boxes,
    required this.aspectRatio,
    required this.crops,
  });
}

class CountdownSegmenterService {
  static const _channel = MethodChannel('bangbangblind/countdown_segmenter');

  Future<CountdownSegmentation> segment(
    Uint8List roiBytes, {
    bool noOverlap = false,
  }) async {
    if (roiBytes.isEmpty) {
      throw ArgumentError('ROI 圖片不可為空');
    }

    final result = await _channel.invokeMethod<dynamic>(
      noOverlap ? 'segmentCountdownNoOverlap' : 'segmentCountdown',
      roiBytes,
    );

    if (result is! Map) {
      throw StateError('Kotlin Segmenter 回傳格式錯誤');
    }

    final boxesRaw = result['boxes_xyxy'] as List;
    final cropsRaw = result['crops_png'] as List;

    final boxes = boxesRaw.map((box) {
      return (box as List).map((value) => (value as num).toInt()).toList();
    }).toList();

    final crops = cropsRaw.map((crop) {
      if (crop is Uint8List) return crop;
      if (crop is List) {
        return Uint8List.fromList(crop.map((v) => (v as num).toInt()).toList());
      }
      throw StateError('數字 Crop 格式錯誤');
    }).toList();

    final digitCount = (result['digit_count'] as num).toInt();

    if (digitCount != boxes.length ||
        digitCount != crops.length ||
        digitCount < 1 ||
        digitCount > 2) {
      throw StateError('Segmenter 輸出數量不一致');
    }

    return CountdownSegmentation(
      digitCount: digitCount,
      strategy: result['strategy'] as String,
      boxes: boxes,
      aspectRatio: (result['foreground_aspect_ratio'] as num).toDouble(),
      crops: crops,
    );
  }
}
