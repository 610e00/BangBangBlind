import 'dart:ui';

import 'package:flutter_test/flutter_test.dart';

import 'package:tset1006/models/obstacle_detection_result.dart';
import 'package:tset1006/services/obstacle_decision.dart';

void main() {
  final ObstacleDecision decision = ObstacleDecision();

  // ==================================================
  // 1. 正前方很近
  // ==================================================

  test(
    '正前方很近的 pole 應該是 frontDanger',
    () {
      final detections = <ObstacleDetectionResult>[
        const ObstacleDetectionResult(
          objectType: 'pole',
          confidence: 0.90,
          box: Rect.fromLTRB(
            0.35,
            0.10,
            0.60,
            0.90,
          ),
        ),
      ];

      final result =
          decision.analyze(detections);

      expect(
        result.warningType,
        ObstacleWarningType.frontDanger,
      );

      expect(
        result.direction,
        ObstacleDirection.center,
      );
    },
  );


  // ==================================================
  // 2. 正前方中距離
  // ==================================================

  test(
    '正前方中距離 pole 應該是 frontWarning',
    () {
      final detections = <ObstacleDetectionResult>[
        const ObstacleDetectionResult(
          objectType: 'pole',
          confidence: 0.90,
          box: Rect.fromLTRB(
            0.40,
            0.10,
            0.55,
            0.80,
          ),
        ),
      ];

      final result =
          decision.analyze(detections);

      expect(
        result.warningType,
        ObstacleWarningType.frontWarning,
      );

      expect(
        result.direction,
        ObstacleDirection.center,
      );
    },
  );


  // ==================================================
  // 3. 左前方很近
  // ==================================================

  test(
    '左前方很近的 pole 應該是 sideDanger',
    () {
      final detections = <ObstacleDetectionResult>[
        const ObstacleDetectionResult(
          objectType: 'pole',
          confidence: 0.88,
          box: Rect.fromLTRB(
            0.05,
            0.10,
            0.29,
            0.90,
          ),
        ),
      ];

      final result =
          decision.analyze(detections);

      expect(
        result.warningType,
        ObstacleWarningType.sideDanger,
      );

      expect(
        result.direction,
        ObstacleDirection.leftCenter,
      );
    },
  );


  // ==================================================
  // 4. 右前方很近
  // ==================================================

  test(
    '右前方很近的 pole 應該是 sideDanger',
    () {
      final detections = <ObstacleDetectionResult>[
        const ObstacleDetectionResult(
          objectType: 'pole',
          confidence: 0.88,
          box: Rect.fromLTRB(
            0.71,
            0.10,
            0.95,
            0.90,
          ),
        ),
      ];

      final result =
          decision.analyze(detections);

      expect(
        result.warningType,
        ObstacleWarningType.sideDanger,
      );

      expect(
        result.direction,
        ObstacleDirection.rightCenter,
      );
    },
  );


  // ==================================================
  // 5. 最左側
  // ==================================================

  test(
    '最左側障礙物不應警示',
    () {
      final detections = <ObstacleDetectionResult>[
        const ObstacleDetectionResult(
          objectType: 'pole',
          confidence: 0.90,
          box: Rect.fromLTRB(
            0.02,
            0.10,
            0.17,
            0.90,
          ),
        ),
      ];

      final result =
          decision.analyze(detections);

      expect(
        result.warningType,
        ObstacleWarningType.none,
      );
    },
  );


  // ==================================================
  // 6. 最右側
  // ==================================================

  test(
    '最右側障礙物不應警示',
    () {
      final detections = <ObstacleDetectionResult>[
        const ObstacleDetectionResult(
          objectType: 'pole',
          confidence: 0.90,
          box: Rect.fromLTRB(
            0.83,
            0.10,
            0.98,
            0.90,
          ),
        ),
      ];

      final result =
          decision.analyze(detections);

      expect(
        result.warningType,
        ObstacleWarningType.none,
      );
    },
  );


  // ==================================================
  // 7. 沒有障礙物
  // ==================================================

  test(
    '沒有障礙物時應該是 none',
    () {
      final result =
          decision.analyze(
        <ObstacleDetectionResult>[],
      );

      expect(
        result.warningType,
        ObstacleWarningType.none,
      );
    },
  );


  // ==================================================
  // 8. 同時有多個障礙物
  // ==================================================

  test(
    '多個障礙物時 frontDanger 應優先於 frontWarning',
    () {
      final detections = <ObstacleDetectionResult>[
        const ObstacleDetectionResult(
          objectType: 'Transformer box',
          confidence: 0.90,
          box: Rect.fromLTRB(
            0.42,
            0.10,
            0.57,
            0.90,
          ),
        ),

        const ObstacleDetectionResult(
          objectType: 'pole',
          confidence: 0.92,
          box: Rect.fromLTRB(
            0.35,
            0.10,
            0.60,
            0.90,
          ),
        ),
      ];

      final result =
          decision.analyze(detections);

      expect(
        result.warningType,
        ObstacleWarningType.frontDanger,
      );

      expect(
        result.obstacle?.objectType,
        'pole',
      );
    },
  );


  // ==================================================
  // 9. 非模型四類別
  // ==================================================

  test(
    '未知類別應被忽略',
    () {
      final detections = <ObstacleDetectionResult>[
        const ObstacleDetectionResult(
          objectType: 'car',
          confidence: 0.99,
          box: Rect.fromLTRB(
            0.35,
            0.10,
            0.65,
            0.90,
          ),
        ),
      ];

      final result =
          decision.analyze(detections);

      expect(
        result.warningType,
        ObstacleWarningType.none,
      );
    },
  );
}