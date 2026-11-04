import 'dart:math' as math;

import 'countdown_segmenter_service.dart';
import 'digit_cnn_service.dart';
import 'digit_d5_preprocessor.dart';

class CountdownRecognition {
  final List<int> digits;
  final List<double> confidences;
  final int seconds;

  const CountdownRecognition({
    required this.digits,
    required this.confidences,
    required this.seconds,
  });

  String get displayText => digits.join();
}

class CountdownRecognitionService {
  final DigitCnnService _cnn = DigitCnnService();

  Future<CountdownRecognition> recognize(
    CountdownSegmentation segmentation,
  ) async {
    if (segmentation.crops.isEmpty ||
        segmentation.crops.length > 2 ||
        segmentation.crops.length != segmentation.digitCount) {
      throw StateError('Segmenter 數字數量異常');
    }

    final digits = <int>[];
    final confidences = <double>[];

    for (final crop in segmentation.crops) {
      // 原本已建立的 D5 前處理
      final tensor = DigitD5Preprocessor.preprocess(crop);

      // Android Kotlin + LiteRT CNN E3
      final logits = await _cnn.predictDigit(tensor);

      if (logits.length != 10 || logits.any((value) => !value.isFinite)) {
        throw StateError('CNN 輸出格式異常');
      }

      // 找出最高 Logit 對應的數字
      var bestIndex = 0;

      for (var i = 1; i < logits.length; i++) {
        if (logits[i] > logits[bestIndex]) {
          bestIndex = i;
        }
      }

      // 數值穩定的 Softmax
      final maxLogit = logits.reduce(math.max);

      final expValues = logits
          .map((value) => math.exp(value - maxLogit))
          .toList();

      final sumExp = expValues.reduce((a, b) => a + b);

      final confidence = expValues[bestIndex] / sumExp;

      digits.add(bestIndex);
      confidences.add(confidence);
    }

    // 例如 [3, 6] -> 36
    // 例如 [0, 1] -> 1
    final seconds = digits.fold<int>(0, (value, digit) => value * 10 + digit);

    return CountdownRecognition(
      digits: digits,
      confidences: confidences,
      seconds: seconds,
    );
  }
}
