import 'dart:io';
import 'dart:math' as math;
import 'dart:ui' as ui;

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:ultralytics_yolo/ultralytics_yolo.dart';

class EdgeYoloTestPage extends StatefulWidget {
  const EdgeYoloTestPage({super.key});

  @override
  State<EdgeYoloTestPage> createState() => _EdgeYoloTestPageState();
}

class _EdgeYoloTestPageState extends State<EdgeYoloTestPage> {
  static const thresholds = <String, double>{
    'pedestrian_green': 0.15,
    'pedestrian_red': 0.25,
    'countdown_timer': 0.25,
  };

  final YOLO _yolo = YOLO(
    modelPath: 'assets/models/traffic_yolov8n_v6_1280.tflite',
    task: YOLOTask.detect,
    useGpu: false,
  );

  bool _loaded = false;
  bool _busy = false;
  String _status = '模型載入中…';

  File? _image;
  int _imageWidth = 0;
  int _imageHeight = 0;

  List<Map<String, dynamic>> _boxes = [];

  double? _totalMs;
  double? _predictMs;
  double? _preMs;
  double? _postMs;

  @override
  void initState() {
    super.initState();
    _loadModel();
  }

  Future<void> _loadModel() async {
    try {
      final success = await _yolo.loadModel();
      if (!mounted) return;

      setState(() {
        _loaded = success;
        _status = success ? '模型載入成功' : '模型載入失敗';
      });
    } catch (e) {
      if (!mounted) return;
      setState(() => _status = '模型載入錯誤：$e');
    }
  }

  Future<void> _detectImage() async {
    if (!_loaded || _busy) return;

    final picked = await ImagePicker().pickImage(source: ImageSource.gallery);

    if (picked == null || !mounted) return;

    setState(() {
      _busy = true;
      _status = 'Android YOLO 辨識中…';
      _boxes = [];
      _totalMs = null;
      _predictMs = null;
      _preMs = null;
      _postMs = null;
    });

    try {
      final totalWatch = Stopwatch()..start();

      final file = File(picked.path);
      final bytes = await file.readAsBytes();

      final codec = await ui.instantiateImageCodec(bytes);
      final frame = await codec.getNextFrame();

      final width = frame.image.width;
      final height = frame.image.height;

      frame.image.dispose();
      codec.dispose();

      final predictWatch = Stopwatch()..start();

      final result = await _yolo.predict(
        bytes,
        confidenceThreshold: 0.10,
        iouThreshold: 0.50,
      );

      predictWatch.stop();
      totalWatch.stop();

      final rawBoxes = result['boxes'] as List? ?? [];

      final filtered = <Map<String, dynamic>>[];

      for (final raw in rawBoxes) {
        if (raw is! Map) continue;

        final box = Map<String, dynamic>.from(raw);
        final name = (box['className'] ?? '').toString();

        final confidence = (box['confidence'] as num?)?.toDouble() ?? 0.0;

        final threshold = thresholds[name];

        if (threshold == null || confidence < threshold) {
          continue;
        }

        final requiredKeys = ['x1_norm', 'y1_norm', 'x2_norm', 'y2_norm'];

        if (!requiredKeys.every((key) => box[key] is num)) {
          continue;
        }

        filtered.add(box);
      }

      if (!mounted) return;

      setState(() {
        _image = file;
        _imageWidth = width;
        _imageHeight = height;
        _boxes = filtered;

        _totalMs = totalWatch.elapsedMicroseconds / 1000.0;
        _predictMs = predictWatch.elapsedMicroseconds / 1000.0;
        _preMs = (result['preMs'] as num?)?.toDouble();
        _postMs = (result['postMs'] as num?)?.toDouble();

        _status = '推論完成，${filtered.length} 個有效偵測框';
      });

      debugPrint(
        'EDGE YOLO: boxes=${filtered.length}, '
        'total=${_totalMs?.toStringAsFixed(1)} ms',
      );
    } catch (e) {
      if (!mounted) return;
      setState(() => _status = '推論失敗：$e');
      debugPrint('EDGE YOLO ERROR: $e');
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  String _classLabel(String name) {
    switch (name) {
      case 'pedestrian_green':
        return '行人綠燈';
      case 'pedestrian_red':
        return '行人紅燈';
      case 'countdown_timer':
        return '倒數號誌';
      default:
        return name;
    }
  }

  Widget _buildImage() {
    final image = _image;

    if (image == null || _imageWidth <= 0 || _imageHeight <= 0) {
      return const Center(child: Text('尚未選擇圖片'));
    }

    return LayoutBuilder(
      builder: (context, constraints) {
        final scale = math.min(
          constraints.maxWidth / _imageWidth,
          constraints.maxHeight / _imageHeight,
        );

        final displayWidth = _imageWidth * scale;
        final displayHeight = _imageHeight * scale;

        return Center(
          child: SizedBox(
            width: displayWidth,
            height: displayHeight,
            child: Stack(
              fit: StackFit.expand,
              children: [
                Image.file(image, fit: BoxFit.fill),
                CustomPaint(painter: DetectionBoxPainter(_boxes)),
              ],
            ),
          ),
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('幫幫盲｜Android Edge YOLO')),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Column(
            children: [
              Expanded(flex: 5, child: _buildImage()),
              const SizedBox(height: 10),

              Text(
                _status,
                style: const TextStyle(fontWeight: FontWeight.bold),
              ),

              if (_busy) const CircularProgressIndicator(),

              if (_totalMs != null) ...[
                const SizedBox(height: 8),
                Text('總耗時：${_totalMs!.toStringAsFixed(1)} ms'),
                Text(
                  'YOLO 呼叫耗時：'
                  '${_predictMs!.toStringAsFixed(1)} ms',
                ),
                if (_preMs != null)
                  Text('前處理：${_preMs!.toStringAsFixed(1)} ms'),
                if (_postMs != null)
                  Text('後處理：${_postMs!.toStringAsFixed(1)} ms'),
              ],

              if (_boxes.isNotEmpty)
                Expanded(
                  flex: 2,
                  child: ListView.builder(
                    itemCount: _boxes.length,
                    itemBuilder: (context, index) {
                      final box = _boxes[index];
                      final name = box['className'].toString();
                      final confidence = (box['confidence'] as num).toDouble();

                      return ListTile(
                        dense: true,
                        title: Text(_classLabel(name)),
                        trailing: Text(
                          '${(confidence * 100).toStringAsFixed(1)}%',
                        ),
                      );
                    },
                  ),
                ),

              const SizedBox(height: 8),

              SizedBox(
                width: double.infinity,
                child: FilledButton(
                  onPressed: _loaded && !_busy ? _detectImage : null,
                  child: const Text('選擇圖片並在 Android 辨識'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class DetectionBoxPainter extends CustomPainter {
  DetectionBoxPainter(this.boxes);

  final List<Map<String, dynamic>> boxes;

  @override
  void paint(Canvas canvas, Size size) {
    for (final box in boxes) {
      final name = box['className'].toString();
      final confidence = (box['confidence'] as num).toDouble();

      double normalized(String key) {
        return (box[key] as num).toDouble().clamp(0.0, 1.0).toDouble();
      }

      final x1 = normalized('x1_norm') * size.width;
      final y1 = normalized('y1_norm') * size.height;
      final x2 = normalized('x2_norm') * size.width;
      final y2 = normalized('y2_norm') * size.height;

      final color = switch (name) {
        'pedestrian_green' => Colors.greenAccent,
        'pedestrian_red' => Colors.redAccent,
        'countdown_timer' => Colors.amber,
        _ => Colors.white,
      };

      final label = switch (name) {
        'pedestrian_green' => '綠燈',
        'pedestrian_red' => '紅燈',
        'countdown_timer' => '倒數',
        _ => name,
      };

      final paint = Paint()
        ..color = color
        ..style = PaintingStyle.stroke
        ..strokeWidth = 2;

      canvas.drawRect(Rect.fromLTRB(x1, y1, x2, y2), paint);

      final textPainter = TextPainter(
        text: TextSpan(
          text: '$label ${(confidence * 100).toStringAsFixed(1)}%',
          style: const TextStyle(
            color: Colors.white,
            fontSize: 12,
            fontWeight: FontWeight.bold,
          ),
        ),
        textDirection: TextDirection.ltr,
        maxLines: 1,
      )..layout();

      final labelWidth = textPainter.width + 8;
      final labelHeight = textPainter.height + 4;

      final labelX = math.max(0.0, math.min(x1, size.width - labelWidth));

      final labelY = math.max(0.0, y1 - labelHeight);

      canvas.drawRect(
        Rect.fromLTWH(labelX, labelY, labelWidth, labelHeight),
        Paint()..color = Colors.black87,
      );

      textPainter.paint(canvas, Offset(labelX + 4, labelY + 2));
    }
  }

  @override
  bool shouldRepaint(covariant DetectionBoxPainter oldDelegate) {
    return true;
  }
}
