import 'package:flutter/material.dart';

import 'digit_cnn_service.dart';

void main() {
  runApp(const CnnSmokeTestApp());
}

class CnnSmokeTestApp extends StatelessWidget {
  const CnnSmokeTestApp({super.key});

  @override
  Widget build(BuildContext context) {
    return const MaterialApp(
      debugShowCheckedModeBanner: false,
      home: CnnSmokeTestPage(),
    );
  }
}

class CnnSmokeTestPage extends StatefulWidget {
  const CnnSmokeTestPage({super.key});

  @override
  State<CnnSmokeTestPage> createState() => _CnnSmokeTestPageState();
}

class _CnnSmokeTestPageState extends State<CnnSmokeTestPage> {
  final DigitCnnService _service = DigitCnnService();

  bool _running = false;
  String _status = '尚未測試';

  Future<void> _runTest() async {
    setState(() {
      _running = true;
      _status = '正在載入 CNN 並執行推論…';
    });

    try {
      final logits = await _service.testZeroInput();

      var bestIndex = 0;
      for (var i = 1; i < logits.length; i++) {
        if (logits[i] > logits[bestIndex]) {
          bestIndex = i;
        }
      }

      if (!mounted) return;

      setState(() {
        _status =
            '✅ Android CNN 推論成功\n\n'
            '輸出數量：${logits.length}\n'
            '預測數字：$bestIndex\n\n'
            'Logits：\n'
            '${logits.map((v) => v.toStringAsFixed(6)).join('\n')}';
      });
    } catch (e, stack) {
      debugPrint('CNN ERROR: $e');
      debugPrintStack(stackTrace: stack);

      if (!mounted) return;

      setState(() {
        _status = '❌ CNN 推論失敗\n\n$e';
      });
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
      appBar: AppBar(title: const Text('幫幫盲｜CNN 模型測試')),
      body: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Text(
              'DigitRecognizerV3 E3',
              style: TextStyle(fontSize: 22, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 12),
            const Text(
              '測試輸入：全零 Tensor [1, 3, 64, 64]\n'
              '這是模型執行測試，不是真實數字辨識。',
            ),
            const SizedBox(height: 20),
            ElevatedButton(
              onPressed: _running ? null : _runTest,
              child: Text(_running ? '執行中…' : '開始 CNN 推論測試'),
            ),
            const SizedBox(height: 20),
            Expanded(
              child: SingleChildScrollView(child: SelectableText(_status)),
            ),
          ],
        ),
      ),
    );
  }
}
