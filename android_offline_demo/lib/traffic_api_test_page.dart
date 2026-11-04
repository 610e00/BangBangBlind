
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_tts/flutter_tts.dart';
import 'package:http/http.dart' as http;
import 'package:image_picker/image_picker.dart';

class TrafficApiTestPage extends StatefulWidget {
  const TrafficApiTestPage({super.key});

  @override
  State<TrafficApiTestPage> createState() =>
      _TrafficApiTestPageState();
}

class _TrafficApiTestPageState extends State<TrafficApiTestPage> {
  final _tts = FlutterTts();
  @override
void initState() {
  super.initState();

  _tts.setStartHandler(() {
    debugPrint('TTS EVENT: START');
  });

  _tts.setCompletionHandler(() {
    debugPrint('TTS EVENT: COMPLETE');
  });

  _tts.setErrorHandler((message) {
    debugPrint('TTS EVENT: ERROR $message');
  });

  _tts.setCancelHandler(() {
    debugPrint('TTS EVENT: CANCEL');
  });
}

  File? _image;
  Map<String, dynamic>? _result;
  String _status = '請選擇測試圖片';
  bool _busy = false;

  static const server =
      'http://10.0.2.2:8004/traffic/detect';

  Future<void> _test() async {
    if (_busy) return;

    final picked = await ImagePicker().pickImage(
      source: ImageSource.gallery,
    );

    if (picked == null || !mounted) return;

    setState(() {
      _busy = true;
      _image = File(picked.path);
      _result = null;
      _status = 'YOLO + CNN E3 辨識中...';
    });

    try {
      final request = http.MultipartRequest(
        'POST',
        Uri.parse(server),
      );

      request.files.add(
        await http.MultipartFile.fromPath(
          'file',
          picked.path,
        ),
      );

      final response = await request.send().timeout(
        const Duration(seconds: 40),
      );

      final text = await response.stream.bytesToString();

      if (response.statusCode != 200) {
        throw Exception(
          'HTTP ${response.statusCode}: $text',
        );
      }

      final json = jsonDecode(text)
          as Map<String, dynamic>;

      if (json['status'] != 'success') {
        throw Exception('API 回傳失敗');
      }

      final data = Map<String, dynamic>.from(
        json['data'] as Map,
      );

      if (!mounted) return;

      setState(() {
        _result = data;
        _status = '辨識完成';
      });
    } catch (e) {
      if (!mounted) return;

      setState(() {
        _status = '辨識失敗：$e';
      });
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  Future<void> _speak() async {
    if (_result == null) return;

    setState(() => _status = '正在檢查語音引擎...');

    try {
      final engines = await _tts.getEngines;
      final languages = await _tts.getLanguages;

      debugPrint('TTS ENGINES: $engines');
      debugPrint('TTS LANGUAGES: $languages');

      await _tts.setEngine('com.google.android.tts');

      final available = await _tts.isLanguageAvailable('zh-TW');
      debugPrint('TTS zh-TW available: $available');

      final languageResult = await _tts.setLanguage('zh-TW');
      debugPrint('TTS setLanguage: $languageResult');

      await _tts.setSpeechRate(0.45);
      await _tts.setVolume(1.0);
      await _tts.setPitch(1.0);

      const message = '語音測試，現在開始播報';

      debugPrint('TTS speaking: $message');

      if (mounted) {
        setState(() => _status = '正在播放測試語音...');
      }

      final result = await _tts.speak(message);
      debugPrint('TTS speak result: $result');

      if (mounted) {
        setState(() => _status = 'TTS 呼叫完成：$result');
      }
    } catch (e) {
      debugPrint('TTS ERROR: $e');

      if (mounted) {
        setState(() => _status = 'TTS 錯誤：$e');
      }
    }
  }

  @override
  void dispose() {
    _tts.stop();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final data = _result;

    return Scaffold(
      appBar: AppBar(
        title: const Text('幫幫盲｜即時 API 測試'),
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          FilledButton.icon(
            onPressed: _busy ? null : _test,
            icon: const Icon(Icons.image),
            label: const Text('選擇圖片並辨識'),
          ),
          const SizedBox(height: 12),
          Text(_status),
          if (_busy)
            const LinearProgressIndicator(),
          if (_image != null) ...[
            const SizedBox(height: 12),
            Image.file(_image!, height: 220),
          ],
          if (data != null) ...[
            const SizedBox(height: 20),
            Text(
              '燈號：${data['light_color'] ?? '未確認'}',
            ),
            Text(
              '倒數框：${data['countdown_detected']}',
            ),
            Text(
              'CNN 候選秒數：'
              '${data['raw_seconds'] ?? '無'}',
            ),
            Text(
              '確認秒數：'
              '${data['seconds_remaining'] ?? '未確認'}',
            ),
            Text(
              'CNN 信心：'
              '${data['cnn_confidence'] ?? '無'}',
            ),
            Text(
              '安全狀態：'
              '${data['is_safe_to_cross']}',
            ),
            const SizedBox(height: 12),
            const Text(
              '候選秒數未驗證，不可直接用於通行判斷。',
              style: TextStyle(color: Colors.orange),
            ),
            const SizedBox(height: 16),
            FilledButton.icon(
              onPressed: _speak,
              icon: const Icon(Icons.volume_up),
              label: const Text('測試語音播報'),
            ),
          ],
        ],
      ),
    );
  }
}
