import 'dart:math' as math;
import 'dart:ui';

import '../models/obstacle_detection_result.dart';

class ObstacleTracker {
  ObstacleTracker({
    this.maxMissingAge = const Duration(milliseconds: 850),
  });

  final Duration maxMissingAge;

  final List<_Track> _tracks = [];
  int _nextId = 1;

  Set<int> get activeTrackIds =>
      _tracks.map((track) => track.id).toSet();

  List<ObstacleDetectionResult> update(
    List<ObstacleDetectionResult> detections, {
    DateTime? timestamp,
  }) {
    final now = timestamp ?? DateTime.now();

    // 超過 850ms 沒有再次看到，就移除追蹤
    _tracks.removeWhere(
      (track) =>
          now.difference(track.lastSeen) > maxMissingAge,
    );

    final usedIds = <int>{};
    final results = <ObstacleDetectionResult>[];

    for (final detection in detections) {
      _Track? matched;
      double bestScore = -1;

      // 尋找是不是之前追蹤的同一個物件
      for (final track in _tracks) {
        if (usedIds.contains(track.id)) continue;

        final overlap = _iou(track.box, detection.box);

        final dx =
            track.box.center.dx - detection.box.center.dx;
        final dy =
            track.box.center.dy - detection.box.center.dy;

        final centerDistance =
            math.sqrt(dx * dx + dy * dy);

        final oldArea =
            track.box.width * track.box.height;
        final newArea =
            detection.box.width * detection.box.height;

        final areaRatio = oldArea > 0 && newArea > 0
            ? math.min(oldArea, newArea) /
                math.max(oldArea, newArea)
            : 0.0;

        // 不以單幀辨識類別決定是否為同一物件
        final plausible =
            overlap >= 0.15 ||
            (centerDistance <= 0.10 &&
                areaRatio >= 0.30);

        if (!plausible) continue;

        final score =
            overlap * 2 +
            (1 - centerDistance)
                    .clamp(0.0, 1.0)
                    .toDouble() *
                0.25 +
            areaRatio * 0.10;

        if (score > bestScore) {
          bestScore = score;
          matched = track;
        }
      }

      // 找不到既有追蹤，建立新 ID
      if (matched == null) {
        matched = _Track(
          _nextId++,
          detection.box,
          now,
        );
        _tracks.add(matched);
      }

      usedIds.add(matched.id);

      // 長時間沒有連續確認，重新累積確認次數
      if (now.difference(matched.lastSeen) >
          const Duration(milliseconds: 800)) {
        matched.confirmedHits = 0;
      }

      matched.box = detection.box;
      matched.lastSeen = now;

      // 信心較低的影格不累積確認次數
      if (detection.confidence >= 0.40) {
        matched.confirmedHits++;
        matched.recordClass(detection.objectType);
      } else {
        matched.confirmedHits = 0;
      }

      // 框還不大時，保留較有意義的方向參考
      if (detection.box.width <= 0.75) {
        matched.reliableCenterX =
            detection.box.center.dx;
        matched.reliableCenterTime = now;
      }

      double? referenceX = matched.reliableCenterX;

      // 太久以前的位置不能一直沿用
      if (matched.reliableCenterTime == null ||
          now.difference(matched.reliableCenterTime!) >
              const Duration(milliseconds: 1200)) {
        referenceX = null;
      }

      results.add(
        ObstacleDetectionResult(
          objectType: detection.objectType,
          confidence: detection.confidence,

          // 一定使用本幀真正得到的框
          box: detection.box,

          trackId: matched.id,
          trackHits: matched.confirmedHits,
          stableObjectType: matched.stableClass,
          reliableCenterX: referenceX,
        ),
      );
    }

    return results;
  }

  // 清空追蹤，例如鏡頭斷線或模型切換
  void clear() {
    _tracks.clear();
  }

  static double _iou(Rect a, Rect b) {
    final left = math.max(a.left, b.left);
    final top = math.max(a.top, b.top);
    final right = math.min(a.right, b.right);
    final bottom = math.min(a.bottom, b.bottom);

    final width = math.max(0.0, right - left);
    final height = math.max(0.0, bottom - top);

    final intersection = width * height;

    final union =
        a.width * a.height +
        b.width * b.height -
        intersection;

    if (union <= 0) return 0;

    return intersection / union;
  }
}

class _Track {
  _Track(this.id, this.box, this.lastSeen);

  final int id;

  Rect box;
  DateTime lastSeen;

  int confirmedHits = 0;

  String? stableClass;

  double? reliableCenterX;
  DateTime? reliableCenterTime;

  final List<String> _recentClasses = [];

  void recordClass(String rawClass) {
    final value = rawClass.trim().toLowerCase();

    if (value.isEmpty) return;

    _recentClasses.add(value);

    // 只保留最近五次辨識結果
    if (_recentClasses.length > 5) {
      _recentClasses.removeAt(0);
    }

    final votes = <String, int>{};

    for (final cls in _recentClasses) {
      votes.update(
        cls,
        (count) => count + 1,
        ifAbsent: () => 1,
      );
    }

    final sorted = votes.entries.toList()
      ..sort(
        (a, b) => b.value.compareTo(a.value),
      );

    if (sorted.isEmpty) return;

    final winner = sorted.first;

    // 第一次確認類別：至少三票
    if (stableClass == null && winner.value >= 3) {
      stableClass = winner.key;
    }

    // 原本已確認類別，切換成另一類需至少四票
    else if (stableClass != null &&
        winner.key != stableClass &&
        winner.value >= 4) {
      stableClass = winner.key;
    }
  }
}