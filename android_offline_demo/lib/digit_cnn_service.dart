import 'package:flutter/services.dart';

class DigitCnnService {
  static const MethodChannel _channel = MethodChannel(
    'bangbangblind/digit_cnn',
  );

  Future<List<double>> testZeroInput() async {
    final result = await _channel.invokeListMethod<double>('testZeroInput');

    if (result == null || result.length != 10) {
      throw StateError('CNN 輸出異常，預期 10 個 logits，實際：$result');
    }

    return result;
  }

  Future<List<double>> predictDigit(List<double> input) async {
    if (input.length != 12288) {
      throw ArgumentError(
        'CNN 輸入必須有 12288 個 Float32 數值，'
        '目前收到 ${input.length} 個',
      );
    }

    if (input.any((value) => !value.isFinite)) {
      throw ArgumentError('CNN 輸入包含 NaN 或 Infinity');
    }

    final result = await _channel.invokeListMethod<double>(
      'predictDigit',
      input.toList(growable: false),
    );

    if (result == null || result.length != 10) {
      throw StateError('CNN 預期輸出 10 個 logits，實際：$result');
    }

    return result;
  }

  void dispose() {
    // Native test currently creates and closes the model per call.
  }
}
