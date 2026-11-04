import 'dart:math' as math;

import 'package:flutter/services.dart';

import 'countdown_recognition_service.dart';
import 'countdown_segmenter_service.dart';

class DigitOpenCvD5Service {
  static const _channel = MethodChannel('bangbangblind/digit_cnn');

  Future<CountdownRecognition> recognize(
    CountdownSegmentation segmentation,
  ) async {
    if (segmentation.crops.length != segmentation.digitCount ||
        segmentation.crops.isEmpty ||
        segmentation.crops.length > 2) {
      throw StateError('不重疊 Crop 數量異常');
    }

    final digits = <int>[];
    final confidences = <double>[];

    for (final crop in segmentation.crops) {
      // PNG bytes -> Kotlin OpenCV D5 -> LiteRT CNN E3
      final logits = await _channel.invokeListMethod<double>(
        'predictDigitOpenCv',
        crop,
      );

      if (logits == null ||
          logits.length != 10 ||
          logits.any((v) => !v.isFinite)) {
        throw StateError('OpenCV D5 CNN 輸出異常');
      }

      var best = 0;
      for (var i = 1; i < logits.length; i++) {
        if (logits[i] > logits[best]) best = i;
      }

      final maxLogit = logits.reduce(math.max);
      final exponentials = logits.map((v) => math.exp(v - maxLogit)).toList();
      final denominator = exponentials.reduce((a, b) => a + b);

      digits.add(best);
      confidences.add(exponentials[best] / denominator);
    }

    return CountdownRecognition(
      digits: digits,
      confidences: confidences,
      seconds: digits.fold<int>(0, (value, digit) => value * 10 + digit),
    );
  }
}
