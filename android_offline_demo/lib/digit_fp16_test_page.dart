import 'dart:math' as math;
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:image_picker/image_picker.dart';

class DigitFp16TestPage extends StatefulWidget {
  const DigitFp16TestPage({super.key});

  @override
  State<DigitFp16TestPage> createState() =>
      _DigitFp16TestPageState();
}

class _DigitFp16TestPageState extends State<DigitFp16TestPage> {
  static const channel = MethodChannel('bangbangblind/digit_cnn');

  Uint8List? imageBytes;
  int? predictedDigit;
  double? confidence;
  double? elapsedMs;
  String status = '請選擇一張單獨數字圖片';
  bool busy = false;

  Future<void> recognize() async {
    if (busy) return;

    final picked = await ImagePicker().pickImage(
      source: ImageSource.gallery,
    );

    if (picked == null || !mounted) return;

    setState(() {
      busy = true;
      status = 'Android CNN E3 FP16 辨識中…';
      predictedDigit = null;
      confidence = null;
      elapsedMs = null;
    });

    try {
      final bytes = await picked.readAsBytes();
      final stopwatch = Stopwatch()..start();

      final logits = await channel.invokeListMethod<double>(
        'predictDigitOpenCvFp16',
        bytes,
      );

      stopwatch.stop();

      if (logits == null ||
          logits.length != 10 ||
          logits.any((v) => !v.isFinite)) {
        throw StateError('CNN 輸出格式異常');
      }

      int best = 0;
      for (int i = 1; i < logits.length; i++) {
        if (logits[i] > logits[best]) best = i;
      }

      final maxLogit = logits.reduce(math.max);
      final expValues = logits
          .map((v) => math.exp(v - maxLogit))
          .toList();
      final sum = expValues.reduce((a, b) => a + b);

      if (!mounted) return;

      setState(() {
        imageBytes = bytes;
        predictedDigit = best;
        confidence = expValues[best] / sum;
        elapsedMs = stopwatch.elapsedMicroseconds / 1000;
        status = 'FP16 推論成功';
      });
    } catch (e) {
      if (!mounted) return;
      setState(() => status = '推論失敗：$e');
    } finally {
      if (mounted) {
        setState(() => busy = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('CNN E3 FP16｜單數字測試'),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            ElevatedButton(
              onPressed: busy ? null : recognize,
              child: Text(
                busy ? '辨識中…' : '選擇數字圖片',
              ),
            ),
            const SizedBox(height: 20),
            if (imageBytes != null)
              Image.memory(
                imageBytes!,
                height: 220,
                fit: BoxFit.contain,
              ),
            const SizedBox(height: 20),
            Text(status, textAlign: TextAlign.center),
            if (predictedDigit != null) ...[
              const SizedBox(height: 24),
              Text(
                '預測數字：$predictedDigit',
                textAlign: TextAlign.center,
                style: const TextStyle(
                  fontSize: 32,
                  fontWeight: FontWeight.bold,
                ),
              ),
              Text(
                '信心分數：${(confidence! * 100).toStringAsFixed(2)}%',
                textAlign: TextAlign.center,
              ),
              Text(
                '耗時：${elapsedMs!.toStringAsFixed(1)} ms',
                textAlign: TextAlign.center,
              ),
            ],
            const SizedBox(height: 20),
            const Text(
              '受控測試：此頁面僅辨識單一數字，不提供通行建議。',
              textAlign: TextAlign.center,
            ),
          ],
        ),
      ),
    );
  }
}
