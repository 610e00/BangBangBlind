import 'dart:ui';

class ObstacleDetectionResult {
  // 障礙物種類
  final String objectType;

  // 模型辨識信心值
  // 例如 0.85 = 85%
  final double confidence;

  // YOLO 的辨識框，座標範圍大約是 0~1
  final Rect box;

  // 同一個障礙物的追蹤編號
  final int? trackId;

  // 已確認偵測到同一物件的影格數
  final int trackHits;

  // 經過多幀多數決後的類別
  final String? stableObjectType;

  // 記錄框的水平中心位置
  final double? reliableCenterX;

  const ObstacleDetectionResult({
    required this.objectType,
    required this.confidence,
    required this.box,
    this.trackId,
    this.trackHits = 0,
    this.stableObjectType,
    this.reliableCenterX,
  });
}