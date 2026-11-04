import 'dart:math' as math;
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import 'digit_cnn_service.dart';
import 'digit_d5_preprocessor.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const DigitCnnTestApp());
}

class DigitCnnTestApp extends StatelessWidget {
  const DigitCnnTestApp({super.key});

  @override
  Widget build(BuildContext context) {
    return const MaterialApp(
      debugShowCheckedModeBanner: false,
      home: DigitCnnTestPage(),
    );
  }
}

class DigitCnnTestPage extends StatefulWidget {
  const DigitCnnTestPage({super.key});

  @override
  State<DigitCnnTestPage> createState() => _DigitCnnTestPageState();
}

class _DigitCnnTestPageState extends State<DigitCnnTestPage> {
  final ImagePicker _picker = ImagePicker();
  final DigitCnnService _service = DigitCnnService();

  Uint8List? _imageBytes;
  int? _predictedDigit;
  double? _confidence;
  List<double>? _logits;

  bool _running = false;
  String _status = '請選擇一張單一數字的 Crop 圖片';

  Future<void> _selectAndPredict() async {
    if (_running) return;

    setState(() {
      _running = true;
      _status = '正在選擇圖片…';
      _predictedDigit = null;
      _confidence = null;
      _logits = null;
    });

    try {
      final file = await _picker.pickImage(source: ImageSource.gallery);

      if (file == null) {
        if (mounted) {
          setState(() {
            _status = '已取消選擇圖片';
          });
        }
        return;
      }

      final bytes = await file.readAsBytes();

      if (!mounted) return;

      setState(() {
        _imageBytes = bytes;
        _status = '正在進行 D5 前處理與 CNN 推論…';
      });

      final tensor = DigitD5Preprocessor.preprocess(bytes);

      if (tensor.length != 12288) {
        throw StateError('Tensor 長度錯誤：${tensor.length}');
      }

      final logits = await _service.predictDigit(tensor);

      if (logits.length != 10) {
        throw StateError('CNN 輸出不是 10 類');
      }

      if (logits.any((value) => !value.isFinite)) {
        throw StateError('CNN 輸出包含無效數值');
      }

      var bestIndex = 0;

      for (var i = 1; i < logits.length; i++) {
        if (logits[i] > logits[bestIndex]) {
          bestIndex = i;
        }
      }

      // 數值穩定的 Softmax
      final maxLogit = logits.reduce(math.max);

      final expValues = logits.map((v) => math.exp(v - maxLogit)).toList();

      final sumExp = expValues.reduce((a, b) => a + b);

      final confidence = expValues[bestIndex] / sumExp;

      if (!mounted) return;

      setState(() {
        _predictedDigit = bestIndex;
        _confidence = confidence;
        _logits = logits;
        _status = '✅ Android CNN 推論完成';
      });
    } catch (e, stack) {
      debugPrint('Digit CNN error: $e');
      debugPrintStack(stackTrace: stack);

      if (mounted) {
        setState(() {
          _status = '❌ 推論失敗：$e';
        });
      }
    } finally {
      if (mounted) {
        setState(() {
          _running = false;
        });
      }
    }
  }

  @override
  void dispose() {
    _service.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('幫幫盲｜CNN 數字辨識')),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(20),
          children: [
            const Text(
              'DigitRecognizerV3 E3',
              style: TextStyle(fontSize: 22, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 8),
            const Text(
              '選擇單一倒數數字 Crop，使用 D5 前處理及 '
              'Android LiteRT 2.1.5 進行推論。',
            ),
            const SizedBox(height: 20),

            Container(
              height: 210,
              alignment: Alignment.center,
              decoration: BoxDecoration(
                color: Colors.black,
                borderRadius: BorderRadius.circular(12),
              ),
              child: _imageBytes == null
                  ? const Text('尚未選擇圖片', style: TextStyle(color: Colors.white))
                  : Image.memory(_imageBytes!, fit: BoxFit.contain),
            ),

            const SizedBox(height: 20),

            ElevatedButton.icon(
              onPressed: _running ? null : _selectAndPredict,
              icon: const Icon(Icons.image_search),
              label: Text(_running ? '辨識中…' : '選擇數字圖片並辨識'),
            ),

            const SizedBox(height: 20),

            Text(_status, style: const TextStyle(fontSize: 16)),

            if (_predictedDigit != null) ...[
              const SizedBox(height: 24),

              const Text('CNN 預測數字', style: TextStyle(fontSize: 18)),

              Text(
                '$_predictedDigit',
                style: const TextStyle(
                  fontSize: 70,
                  fontWeight: FontWeight.bold,
                ),
              ),

              Text(
                'Softmax 信心度：'
                '${(_confidence! * 100).toStringAsFixed(2)}%',
                style: const TextStyle(fontSize: 18),
              ),
            ],

            if (_logits != null) ...[
              const SizedBox(height: 24),

              const Text(
                'CNN Logits',
                style: TextStyle(fontWeight: FontWeight.bold),
              ),

              const SizedBox(height: 8),

              SelectableText(
                List.generate(
                  _logits!.length,
                  (i) => '$i：${_logits![i].toStringAsFixed(6)}',
                ).join('\n'),
              ),
            ],

            const SizedBox(height: 24),

            const Text(
              '測試模式：僅供單一數字分類驗證，'
              '不提供道路通行建議。',
              style: TextStyle(color: Colors.grey),
            ),
          ],
        ),
      ),
    );
  }
}
