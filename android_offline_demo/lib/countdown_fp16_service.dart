import 'dart:math' as math;

import 'package:flutter/services.dart';

import 'countdown_recognition_service.dart';
import 'countdown_segmenter_service.dart';

class CountdownFp16Service {
  static const _channel = MethodChannel('bangbangblind/digit_cnn');

  Future<CountdownRecognition> recognize(
    CountdownSegmentation segmentation,
  ) async {
    if (segmentation.crops.isEmpty ||
        segmentation.crops.length > 2 ||
        segmentation.crops.length != segmentation.digitCount) {
      throw StateError('FP16 Segmenter 數字數量異常');
    }

    final digits = <int>[];
    final confidences = <double>[];

    for (final crop in segmentation.crops) {
      final logits = await _channel.invokeListMethod<double>(
        'predictDigitOpenCvFp16',
        crop,
      );

      if (logits == null ||
          logits.length != 10 ||
          logits.any((value) => !value.isFinite)) {
        throw StateError('FP16 CNN 輸出格式異常');
      }

      var best = 0;
      for (var i = 1; i < logits.length; i++) {
        if (logits[i] > logits[best]) best = i;
      }

      final maxLogit = logits.reduce(math.max);
      final exps = logits.map((v) => math.exp(v - maxLogit)).toList();
      final sum = exps.reduce((a, b) => a + b);

      digits.add(best);
      confidences.add(exps[best] / sum);
    }

    return CountdownRecognition(
      digits: digits,
      confidences: confidences,
      seconds: digits.fold<int>(
        0,
        (value, digit) => value * 10 + digit,
      ),
    );
  }
}
