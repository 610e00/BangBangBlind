import 'dart:math' as math;

import '../models/obstacle_detection_result.dart';

// ====================================
// 五個方向區域
// ====================================

enum ObstacleDirection {
  left,
  leftCenter,
  center,
  rightCenter,
  right,
}

// ====================================
// 警示階段
// ====================================

enum ObstacleWarningType {
  none,

  frontWarning, // 正中約 2m
  frontDanger,  // 正中約 1.5m

  sideDanger,   // 左右約 2m
  sideCritical, // 左右約 1.5m
}

// ====================================
// 每個類別自己的框比例門檻
// ====================================

class BoxDistanceThreshold {
  final double at2m;
  final double at1_5m;

  const BoxDistanceThreshold({
    required this.at2m,
    required this.at1_5m,
  });
}

// ====================================
// 最後的警示判斷結果
// ====================================

class ObstacleDecisionResult {
  final ObstacleDetectionResult? obstacle;
  final ObstacleDirection? direction;
  final ObstacleWarningType warningType;
  final double closeness;
  final int? trackId;

  const ObstacleDecisionResult({
    required this.obstacle,
    required this.direction,
    required this.warningType,
    required this.closeness,
    this.trackId,
  });

  factory ObstacleDecisionResult.none() {
    return const ObstacleDecisionResult(
      obstacle: null,
      direction: null,
      warningType: ObstacleWarningType.none,
      closeness: 0,
    );
  }
}

class ObstacleDecision {
  ObstacleDecision({
    Map<String, BoxDistanceThreshold>?
        calibratedThresholds,
  }) : _thresholds = {
          ..._temporaryThresholds,
          ...?calibratedThresholds,
        } {
    for (final entry in _thresholds.entries) {
      final t = entry.value;

      if (!t.at2m.isFinite ||
          !t.at1_5m.isFinite ||
          t.at2m <= 0 ||
          t.at1_5m <= t.at2m ||
          t.at1_5m > 1) {
        throw ArgumentError(
          '框比例門檻設定錯誤：${entry.key}',
        );
      }
    }
  }

  // ====================================
  // 暫定比例：不是實測距離
  //
  // 之後實測完成，主要修改這裡。
  // ====================================

  static const Map<String, BoxDistanceThreshold>
      _temporaryThresholds = {
    'ground billboard': BoxDistanceThreshold(
      at2m: 0.12,
      at1_5m: 0.22,
    ),

    'transformer box': BoxDistanceThreshold(
      at2m: 0.12,
      at1_5m: 0.20,
    ),

    'rear overhang': BoxDistanceThreshold(
      at2m: 0.12,
      at1_5m: 0.22,
    ),

    'pole': BoxDistanceThreshold(
      at2m: 0.32,
      at1_5m: 0.50,
    ),
  };

  // ====================================
  // 各類別信心值
  //
  // V4 模型換上後需要重新評估。
  // ====================================

  static const Map<String, double> _minConfidence = {
    'ground billboard': 0.55,
    'transformer box': 0.55,
    'rear overhang': 0.50,
    'pole': 0.45,
  };

  final Map<String, BoxDistanceThreshold> _thresholds;

  // 記住哪些追蹤物件已經進入 1.5m 階段
  final Map<int, bool> _wasNear = {};

  // 1.5m 門檻的防跳動比例
  static const double _nearHysteresis = 0.02;

  // ====================================
  // 清除已經消失的追蹤 ID
  // ====================================

  void retainTracks(Set<int> activeIds) {
    _wasNear.removeWhere(
      (id, _) => !activeIds.contains(id),
    );
  }

  void clear() {
    _wasNear.clear();
  }

  // ====================================
  // 分析所有障礙物
  // ====================================

  ObstacleDecisionResult analyze(
    List<ObstacleDetectionResult> detections,
  ) {
    final candidates = <ObstacleDecisionResult>[];

    for (final detection in detections) {
      // 至少三次有效追蹤
      if (detection.trackId == null ||
          detection.trackHits < 3) {
        continue;
      }

      // 以 Tracker 穩定後的類別為主
      final kind = (
        detection.stableObjectType ??
        detection.objectType
      ).trim().toLowerCase();

      final threshold = _thresholds[kind];
      final minConfidence = _minConfidence[kind];

      if (threshold == null || minConfidence == null) {
        continue;
      }

      // 依類別過濾低信心偵測
      if (detection.confidence < minConfidence) {
        continue;
      }

      final box = detection.box;

      if (box.width <= 0 || box.height <= 0) {
        continue;
      }

      // 極端滿版、低信心且尚未充分確認的框
      // 暫時視為可疑誤報。
      //
      // 不能把所有 90% 以上的大框都刪掉，
      // 因為真正靠近的障礙物也可能很大。
      if (box.width >= 0.98 &&
          box.height >= 0.98 &&
          detection.confidence < 0.60 &&
          detection.trackHits < 5) {
        continue;
      }

      // =================================
      // 五區方向
      // =================================

      double centerX = box.center.dx;

      // 大框可能因裁切而使框中心偏移。
      // 如果 Tracker 有近期較小框的位置，
      // 可暫時作為方向參考。
      if (box.width >= 0.85 &&
          detection.reliableCenterX != null) {
        centerX = detection.reliableCenterX!;
      }

      centerX = centerX.clamp(0.0, 1.0).toDouble();

      final direction = _getDirection(centerX);

      // =================================
      // 距離比例
      // =================================

      // pole 使用框高；
      // 其餘三類使用框寬。
      final closeness = (
        kind == 'pole'
            ? box.height
            : box.width
      ).clamp(0.0, 1.0).toDouble();

      final id = detection.trackId!;

      // 未達約 2m 門檻就不發警示
      if (closeness < threshold.at2m) {
        _wasNear[id] = false;
        continue;
      }

      // =================================
      // 1.5m 防跳動
      // =================================

      final wasNear = _wasNear[id] ?? false;

      // 已經進入近距階段後，
      // 允許框比例稍微下降，
      // 避免警示快速、慢速反覆切換。
      final releaseThreshold = math.max(
        threshold.at2m,
        threshold.at1_5m - _nearHysteresis,
      );

      final isNear =
          closeness >= threshold.at1_5m ||
          (wasNear && closeness >= releaseThreshold);

      _wasNear[id] = isNear;

      // =================================
      // 正中、左側、右側
      // =================================

      final ObstacleWarningType warningType;

      if (direction == ObstacleDirection.center) {
        warningType = isNear
            ? ObstacleWarningType.frontDanger
            : ObstacleWarningType.frontWarning;
      } else {
        // 最左、偏左、偏右、最右
        // 現在全部都會警示。
        warningType = isNear
            ? ObstacleWarningType.sideCritical
            : ObstacleWarningType.sideDanger;
      }

      candidates.add(
        ObstacleDecisionResult(
          obstacle: detection,
          direction: direction,
          warningType: warningType,
          closeness: closeness,
          trackId: id,
        ),
      );
    }

    if (candidates.isEmpty) {
      return ObstacleDecisionResult.none();
    }

    // =================================
    // 多障礙物排序
    //
    // 優先：近距正中 > 近距側邊 >
    //       2m 正中 > 2m 側邊
    // =================================

    candidates.sort((a, b) {
      final riskComparison =
          _riskLevel(b.warningType).compareTo(
        _riskLevel(a.warningType),
      );

      if (riskComparison != 0) {
        return riskComparison;
      }

      return b.closeness.compareTo(a.closeness);
    });

    return candidates.first;
  }

  // ====================================
  // 五區位置
  // ====================================

  ObstacleDirection _getDirection(double x) {
    if (x < 0.20) {
      return ObstacleDirection.left;
    }

    if (x < 0.30) {
      return ObstacleDirection.leftCenter;
    }

    if (x <= 0.70) {
      return ObstacleDirection.center;
    }

    if (x <= 0.80) {
      return ObstacleDirection.rightCenter;
    }

    return ObstacleDirection.right;
  }

  // ====================================
  // 危險等級排序
  // ====================================

  int _riskLevel(ObstacleWarningType type) {
    switch (type) {
      case ObstacleWarningType.frontDanger:
        return 4;

      case ObstacleWarningType.sideCritical:
        return 3;

      case ObstacleWarningType.frontWarning:
        return 2;

      case ObstacleWarningType.sideDanger:
        return 1;

      case ObstacleWarningType.none:
        return 0;
    }
  }
}