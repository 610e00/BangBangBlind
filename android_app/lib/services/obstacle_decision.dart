import 'dart:ui';

import '../models/obstacle_detection_result.dart';


// ==============================
// 障礙物方向
// ==============================

enum ObstacleDirection {
  left,
  leftCenter,
  center,
  rightCenter,
  right,
}


// ==============================
// 警示等級
// ==============================

enum ObstacleWarningType {
  none,

  // 正前方約 2～3 公尺
  frontWarning,

  // 正前方約 2 公尺內
  frontDanger,

  // 左右緩衝區約 2 公尺內
  sideDanger,
}


// ==============================
// 每個類別自己的距離門檻
//
// 目前使用 bounding box 寬度比例
// 來暫時代表「約 3m / 約 2m」
//
// 正式使用 UVC 後必須重新校正
// ==============================

class BoxDistanceThreshold {
  final double warning;
  final double danger;

  const BoxDistanceThreshold({
    required this.warning,
    required this.danger,
  });
}


// ==============================
// Decision 最後回傳的答案
// ==============================

class ObstacleDecisionResult {
  final ObstacleDetectionResult? obstacle;

  final ObstacleDirection? direction;

  final ObstacleWarningType warningType;

  // bounding box 的接近程度
  final double closeness;

  const ObstacleDecisionResult({
    required this.obstacle,
    required this.direction,
    required this.warningType,
    required this.closeness,
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


// ==============================
// 正式避障判斷
// ==============================

class ObstacleDecision {
  // ----------------------------------
  // 暫時距離門檻
  //
  // warning ≈ 3 公尺預警
  // danger  ≈ 2 公尺危險
  //
  // 這些值目前還不是正式校正結果
  // ----------------------------------

  static const Map<String, BoxDistanceThreshold>
      _thresholds = {
    'ground billboard':
        BoxDistanceThreshold(
      warning: 0.12,
      danger: 0.22,
    ),

    'rear overhang':
        BoxDistanceThreshold(
      warning: 0.12,
      danger: 0.22,
    ),

    'transformer box':
        BoxDistanceThreshold(
      warning: 0.12,
      danger: 0.20,
    ),

    'pole':
        BoxDistanceThreshold(
      warning: 0.11,
      danger: 0.20,
    ),
  };


  // ==============================
  // 分析一整幀所有障礙物
  // ==============================

  ObstacleDecisionResult analyze(
    List<ObstacleDetectionResult> detections,
  ) {
    final List<ObstacleDecisionResult>
        candidates = [];

    for (final detection in detections) {
      // --------------------------
      // 找這個類別自己的距離門檻
      // --------------------------

      final threshold =
          _getThreshold(
        detection.objectType,
      );

      // 不屬於我們四個類別就跳過
      if (threshold == null) {
        continue;
      }


      // --------------------------
      // 判斷障礙物方向
      // --------------------------

      final ObstacleDirection direction =
          _getDirection(
        detection.box,
      );


      // --------------------------
      // bounding box 寬度
      //
      // 暫時拿來當作接近程度
      // --------------------------

      final double closeness =
          detection.box.width
              .clamp(0.0, 1.0)
              .toDouble();


      ObstacleWarningType warningType =
          ObstacleWarningType.none;


      // ==========================
      // 正前方
      // ==========================

      if (direction ==
          ObstacleDirection.center) {
        // 約 2 公尺內
        if (closeness >=
            threshold.danger) {
          warningType =
              ObstacleWarningType
                  .frontDanger;
        }

        // 約 2～3 公尺
        else if (closeness >=
            threshold.warning) {
          warningType =
              ObstacleWarningType
                  .frontWarning;
        }
      }


      // ==========================
      // 左前方緩衝區
      // ==========================

      else if (direction ==
          ObstacleDirection.leftCenter) {
        if (closeness >=
            threshold.danger) {
          warningType =
              ObstacleWarningType
                  .sideDanger;
        }
      }


      // ==========================
      // 右前方緩衝區
      // ==========================

      else if (direction ==
          ObstacleDirection.rightCenter) {
        if (closeness >=
            threshold.danger) {
          warningType =
              ObstacleWarningType
                  .sideDanger;
        }
      }


      // left / right 最外側
      // 原則上不警示


      // --------------------------
      // 如果需要警示
      // 加進候選清單
      // --------------------------

      if (warningType !=
          ObstacleWarningType.none) {
        candidates.add(
          ObstacleDecisionResult(
            obstacle: detection,
            direction: direction,
            warningType: warningType,
            closeness: closeness,
          ),
        );
      }
    }


    // ==============================
    // 沒有任何危險
    // ==============================

    if (candidates.isEmpty) {
      return ObstacleDecisionResult.none();
    }


    // ==============================
    // 多障礙物排序
    //
    // 1. 危險等級高的優先
    // 2. 同等級時更接近的優先
    // ==============================

    candidates.sort((a, b) {
      final int riskComparison =
          _riskLevel(
        b.warningType,
      ).compareTo(
        _riskLevel(
          a.warningType,
        ),
      );

      if (riskComparison != 0) {
        return riskComparison;
      }

      return b.closeness
          .compareTo(
        a.closeness,
      );
    });


    // 回傳最需要警示的那一個
    return candidates.first;
  }


  // ==============================
  // 找每個類別的距離門檻
  // ==============================

  BoxDistanceThreshold? _getThreshold(
    String objectType,
  ) {
    final String key =
        objectType
            .trim()
            .toLowerCase();

    return _thresholds[key];
  }


  // ==============================
  // 判斷方向
  // ==============================

  ObstacleDirection _getDirection(
    Rect box,
  ) {
    // --------------------------------
    // 30%～70%
    //
    // 只要辨識框有碰到正前方區域，
    // 優先算正前方
    // --------------------------------

    if (_overlaps(
      box,
      0.30,
      0.70,
    )) {
      return ObstacleDirection.center;
    }


    // --------------------------------
    // 左側緩衝區
    // 20%～30%
    // --------------------------------

    if (_overlaps(
      box,
      0.20,
      0.30,
    )) {
      return ObstacleDirection.leftCenter;
    }


    // --------------------------------
    // 右側緩衝區
    // 70%～80%
    // --------------------------------

    if (_overlaps(
      box,
      0.70,
      0.80,
    )) {
      return ObstacleDirection.rightCenter;
    }


    // --------------------------------
    // 剩下就是最外側
    // --------------------------------

    if (box.center.dx < 0.50) {
      return ObstacleDirection.left;
    }

    return ObstacleDirection.right;
  }


  // ==============================
  // 判斷 bounding box
  // 有沒有碰到某一區
  // ==============================

  bool _overlaps(
    Rect box,
    double start,
    double end,
  ) {
    return box.right > start &&
        box.left < end;
  }


  // ==============================
  // 危險程度排序
  // ==============================

  int _riskLevel(
    ObstacleWarningType warningType,
  ) {
    switch (warningType) {
      case ObstacleWarningType.frontDanger:
        return 3;

      case ObstacleWarningType.sideDanger:
        return 2;

      case ObstacleWarningType.frontWarning:
        return 1;

      case ObstacleWarningType.none:
        return 0;
    }
  }
}