import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import 'countdown_segmenter_service.dart';
import 'countdown_fp16_service.dart';
import 'countdown_recognition_service.dart';

class CountdownFp16TestPage extends StatefulWidget {
  const CountdownFp16TestPage({super.key});

  @override
  State<CountdownFp16TestPage> createState() =>
      _CountdownFp16TestPageState();
}

class _CountdownFp16TestPageState
    extends State<CountdownFp16TestPage> {
  Uint8List? _imageBytes;
  CountdownSegmentation? _segmentation;
  CountdownRecognition? _recognition;
  bool _busy = false;
  String _status = '請選擇包含倒數數字的 ROI 圖片';

  Future<void> _test() async {
    if (_busy) return;

    final picked = await ImagePicker().pickImage(
      source: ImageSource.gallery,
    );
    if (picked == null || !mounted) return;

    setState(() {
      _busy = true;
      _segmentation = null;
      _recognition = null;
      _status = '正在執行 Segmenter V4 + CNN E3 FP16…';
    });

    try {
      final bytes = await picked.readAsBytes();

      final segmentation =
          await CountdownSegmenterService().segment(bytes);

      final recognition =
          await CountdownFp16Service().recognize(segmentation);

      if (!mounted) return;

      setState(() {
        _imageBytes = bytes;
        _segmentation = segmentation;
        _recognition = recognition;
        _status = '辨識完成';
      });
    } catch (e) {
      if (!mounted) return;
      setState(() => _status = '辨識失敗：$e');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Segmenter V4 + CNN E3 FP16'),
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            ElevatedButton(
              onPressed: _busy ? null : _test,
              child: Text(_busy ? '辨識中…' : '選擇倒數 ROI 圖片'),
            ),
            const SizedBox(height: 20),
            if (_imageBytes != null)
              Image.memory(
                _imageBytes!,
                height: 200,
                fit: BoxFit.contain,
              ),
            const SizedBox(height: 20),
            Text(_status, textAlign: TextAlign.center),
            if (_segmentation != null &&
                _recognition != null) ...[
              const SizedBox(height: 24),
              Text(
                '分割數量：${_segmentation!.digitCount}',
                textAlign: TextAlign.center,
              ),
              Text(
                '分割方式：${_segmentation!.strategy}',
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 16),
              Text(
                '辨識數字：${_recognition!.displayText}',
                textAlign: TextAlign.center,
                style: const TextStyle(
                  fontSize: 36,
                  fontWeight: FontWeight.bold,
                ),
              ),
              Text(
                '剩餘秒數：${_recognition!.seconds}',
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 12),
              for (int i = 0;
                  i < _recognition!.digits.length;
                  i++)
                Text(
                  'Digit ${i + 1}：'
                  '${_recognition!.digits[i]}，'
                  '信心分數 '
                  '${(_recognition!.confidences[i] * 100).toStringAsFixed(2)}%',
                  textAlign: TextAlign.center,
                ),
            ],
            const SizedBox(height: 20),
            const Text(
              '受控測試：辨識結果不代表實際安全通行建議。',
              textAlign: TextAlign.center,
            ),
          ],
        ),
      ),
    );
  }
}
