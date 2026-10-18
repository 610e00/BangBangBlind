import 'dart:ui';

class ObstacleDetectionResult {
  // 障礙物種類
  // 例如 pole、Transformer box
  final String objectType;

  // 模型辨識信心值
  // 例如 0.85 = 85%
  final double confidence;

  // YOLO 的辨識框
  // 使用 normalizedBox，所以座標範圍大約是 0~1
  final Rect box;

  const ObstacleDetectionResult({
    required this.objectType,
    required this.confidence,
    required this.box,
  });
}