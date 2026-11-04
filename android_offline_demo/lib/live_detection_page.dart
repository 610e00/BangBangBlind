import 'package:flutter/material.dart';
import 'package:ultralytics_yolo/ultralytics_yolo.dart';

class LiveDetectionPage extends StatefulWidget {
  const LiveDetectionPage({super.key});

  @override
  State<LiveDetectionPage> createState() => _LiveDetectionPageState();
}

class _LiveDetectionPageState extends State<LiveDetectionPage> {
  static const thresholds = <String, double>{
    'pedestrian_green': 0.15,
    'pedestrian_red': 0.25,
    'countdown_timer': 0.25,
  };

  List<YOLOResult> _detections = [];
  double? _fps;
  String _status = '正在啟動攝影機…';

  String _label(String name) {
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

  void _onResult(List<YOLOResult> results) {
    final filtered = results.where((result) {
      final threshold = thresholds[result.className];
      return threshold != null &&
          result.confidence >= threshold;
    }).toList();

    if (!mounted) return;

    setState(() {
      _detections = filtered;
      _status = filtered.isEmpty
          ? '未確認行人號誌'
          : '偵測到 ${filtered.length} 個有效目標';
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('幫幫盲｜即時行人號誌辨識'),
      ),
      body: SafeArea(
        child: Column(
          children: [
            Expanded(
              child: YOLOView(
                modelPath:
                    'assets/models/traffic_yolov8n_v6_1280.tflite',
                task: YOLOTask.detect,
                useGpu: false,
                confidenceThreshold: 0.10,
                iouThreshold: 0.50,
                cameraResolution: '720p',
                onResult: _onResult,
                onPerformanceMetrics: (metrics) {
                  if (!mounted) return;
                  setState(() {
                    _fps = metrics.fps;
                  });
                },
              ),
            ),
            Padding(
              padding: const EdgeInsets.all(12),
              child: Column(
                children: [
                  Text(
                    _status,
                    style: const TextStyle(
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  const SizedBox(height: 6),
                  Text(
                    'FPS：${_fps?.toStringAsFixed(1) ?? "--"}',
                  ),
                  const SizedBox(height: 8),
                  SizedBox(
                    height: 110,
                    child: ListView.builder(
                      itemCount: _detections.length,
                      itemBuilder: (context, index) {
                        final result = _detections[index];
                        return ListTile(
                          dense: true,
                          title: Text(_label(result.className)),
                          trailing: Text(
                            '${(result.confidence * 100).toStringAsFixed(1)}%',
                          ),
                        );
                      },
                    ),
                  ),
                  const Text(
                    '測試模式：目前不提供安全通行指示',
                    style: TextStyle(fontSize: 12),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
