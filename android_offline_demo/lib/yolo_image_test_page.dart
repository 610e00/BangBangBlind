import 'dart:io';
import 'dart:math' as math;
import 'dart:typed_data';

import 'countdown_recognition_service.dart';
import 'digit_opencv_d5_service.dart';
import 'countdown_fp16_service.dart';
import 'countdown_segmenter_service.dart';

import 'package:flutter/material.dart';
import 'package:image/image.dart' as img;
import 'package:image_picker/image_picker.dart';
import 'package:ultralytics_yolo/ultralytics_yolo.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(
    const MaterialApp(
      debugShowCheckedModeBanner: false,
      home: YoloImageTestPage(),
    ),
  );
}

class YoloImageTestPage extends StatefulWidget {
  const YoloImageTestPage({super.key});

  @override
  State<YoloImageTestPage> createState() => _YoloImageTestPageState();
}

class _YoloImageTestPageState extends State<YoloImageTestPage> {
  final _picker = ImagePicker();

  final _yolo = YOLO(
    modelPath: 'assets/models/traffic_yolov8n_v6_1280.tflite',
    task: YOLOTask.detect,
    useGpu: false,
  );

  Uint8List? _imageBytes;
  Uint8List? _roiBytes;
  CountdownSegmentation? _segmentation;
  CountdownRecognition? _cnnRecognition;
  CountdownSegmentation? _noOverlapSegmentation;
  CountdownRecognition? _noOverlapRecognition;
  CountdownRecognition? _openCvRecognition;
  CountdownRecognition? _fp16Recognition;

  int _imageWidth = 0;
  int _imageHeight = 0;

  List<YOLOResult> _results = [];
  bool _busy = false;
  String _status = '請選擇行人號誌圖片';

  Future<void> _chooseImage() async {
    if (_busy) return;

    final file = await _picker.pickImage(source: ImageSource.gallery);

    if (file == null) return;

    setState(() {
      _busy = true;
      _status = '正在執行 YOLO v6 LiteRT 推論…';
      _results = [];
      _roiBytes = null;
    });

    try {
      final bytes = await file.readAsBytes();
      final decoded = img.decodeImage(bytes);

      if (decoded == null) {
        throw StateError('圖片解碼失敗');
      }

      final response = await _yolo.predict(
        bytes,
        confidenceThreshold: 0.10,
        iouThreshold: 0.50,
      );

      final raw = response['detections'];

      if (raw is! List) {
        throw StateError('YOLO 回傳格式異常：${response.keys.toList()}');
      }

      final detections = raw
          .map((item) => YOLOResult.fromMap(item as Map<dynamic, dynamic>))
          .toList();

      if (!mounted) return;

      setState(() {
        _imageBytes = bytes;
        _imageWidth = decoded.width;
        _imageHeight = decoded.height;
        _results = detections;
        _status = 'YOLO 推論完成：${detections.length} 個偵測結果';
      });
    } catch (e, stack) {
      debugPrint('YOLO image test error: $e');
      debugPrintStack(stackTrace: stack);

      if (mounted) {
        setState(() {
          _status = '❌ 推論失敗：$e';
        });
      }
    } finally {
      if (mounted) {
        setState(() {
          _busy = false;
        });
      }
    }
  }

  Future<void> _exportCountdownRoi() async {
    final bytes = _roiBytes;
    if (bytes == null) return;

    try {
      final file = File(
        '${Directory.systemTemp.path}/bangbangblind_countdown_roi.png',
      );

      await file.writeAsBytes(bytes, flush: true);

      if (!mounted) return;

      setState(() {
        _status = '✅ ROI 已儲存：${file.path}';
      });

      debugPrint('ROI_EXPORTED: ${file.path}');
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _status = '❌ ROI 匯出失敗：$e';
      });
    }
  }

  Future<void> _runFp16ComparisonTest() async {
    final segmentation = _noOverlapSegmentation;
    if (segmentation == null || _busy) return;

    setState(() {
      _busy = true;
      _fp16Recognition = null;
      _status = '正在測試 Kotlin OpenCV D5 + CNN E3 FP16…';
    });

    try {
      final recognition = await CountdownFp16Service().recognize(segmentation);

      if (!mounted) return;

      setState(() {
        _fp16Recognition = recognition;
        _status = '✅ FP16 測試完成：${recognition.displayText}';
      });
    } catch (e, stack) {
      debugPrint('FP16 comparison error: $e');
      debugPrintStack(stackTrace: stack);
      if (mounted) {
        setState(() {
          _status = '❌ FP16 測試失敗：$e';
        });
      }
    } finally {
      if (mounted) {
        setState(() => _busy = false);
      }
    }
  }

  Future<void> _runOpenCvD5Test() async {
    final segmentation = _noOverlapSegmentation;

    if (segmentation == null || _busy) return;

    setState(() {
      _busy = true;
      _openCvRecognition = null;
      _status = '正在測試 Kotlin OpenCV D5 + CNN E3…';
    });

    try {
      final recognition = await DigitOpenCvD5Service().recognize(segmentation);

      if (!mounted) return;

      setState(() {
        _openCvRecognition = recognition;
        _status = '✅ OpenCV D5 測試完成：${recognition.displayText}';
      });
    } catch (e, stack) {
      debugPrint('OpenCV D5 error: $e');
      debugPrintStack(stackTrace: stack);

      if (mounted) {
        setState(() {
          _status = '❌ OpenCV D5 測試失敗：$e';
        });
      }
    } finally {
      if (mounted) {
        setState(() {
          _busy = false;
        });
      }
    }
  }

  Future<void> _runNoOverlapTest() async {
    final roi = _roiBytes;
    if (roi == null || _busy) return;

    setState(() {
      _busy = true;
      _noOverlapSegmentation = null;
      _noOverlapRecognition = null;
      _openCvRecognition = null;
      _fp16Recognition = null;
      _status = '正在執行 Kotlin 不重疊 Crop + CNN…';
    });

    try {
      final segmentation = await CountdownSegmenterService().segment(
        roi,
        noOverlap: true,
      );

      final recognition = await CountdownRecognitionService().recognize(
        segmentation,
      );

      if (!mounted) return;

      setState(() {
        _noOverlapSegmentation = segmentation;
        _noOverlapRecognition = recognition;
        _status = '✅ 不重疊測試完成：${recognition.displayText}';
      });
    } catch (e, stack) {
      debugPrint('No-overlap test error: $e');
      debugPrintStack(stackTrace: stack);

      if (mounted) {
        setState(() {
          _status = '❌ 不重疊測試失敗：$e';
        });
      }
    } finally {
      if (mounted) {
        setState(() {
          _busy = false;
        });
      }
    }
  }

  Future<void> _runSegmenterV4() async {
    final roi = _roiBytes;
    if (roi == null || _busy) return;

    setState(() {
      _busy = true;
      _segmentation = null;
      _cnnRecognition = null;
      _status = '正在執行 Kotlin Segmenter v4…';
    });

    try {
      final result = await CountdownSegmenterService().segment(roi);

      final recognition = await CountdownRecognitionService().recognize(result);

      if (!mounted) return;

      setState(() {
        _segmentation = result;
        _cnnRecognition = recognition;
        _status = '✅ Segmenter + CNN 辨識完成：${recognition.displayText}';
      });
    } catch (e, stack) {
      debugPrint('Segmenter error: $e');
      debugPrintStack(stackTrace: stack);

      if (!mounted) return;

      setState(() {
        _status = '❌ Segmenter 失敗：$e';
      });
    } finally {
      if (mounted) {
        setState(() {
          _busy = false;
        });
      }
    }
  }

  void _extractCountdownRoi(YOLOResult result) {
    final bytes = _imageBytes;
    if (bytes == null) return;

    final image = img.decodeImage(bytes);
    if (image == null) return;

    final box = result.boundingBox;

    final bx1 = box.left.toInt();
    final by1 = box.top.toInt();
    final bx2 = box.right.toInt();
    final by2 = box.bottom.toInt();

    final padX = ((bx2 - bx1) * 0.10).toInt();
    final padY = ((by2 - by1) * 0.10).toInt();

    final x1 = math.max(0, bx1 - padX);
    final y1 = math.max(0, by1 - padY);
    final x2 = math.min(image.width, bx2 + padX);
    final y2 = math.min(image.height, by2 + padY);

    if (x2 <= x1 || y2 <= y1) {
      setState(() {
        _status = '❌ 倒數框座標無效';
      });
      return;
    }

    final crop = img.copyCrop(
      image,
      x: x1,
      y: y1,
      width: x2 - x1,
      height: y2 - y1,
    );

    setState(() {
      _roiBytes = Uint8List.fromList(img.encodePng(crop));
      _status =
          '已裁切 countdown_timer ROI：'
          '${crop.width} × ${crop.height}';
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('幫幫盲｜YOLO v6 圖片測試')),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            const Text(
              'YOLOv8n v6 1280｜LiteRT',
              style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 12),
            ElevatedButton.icon(
              onPressed: _busy ? null : _chooseImage,
              icon: const Icon(Icons.image_search),
              label: Text(_busy ? '推論中…' : '選擇圖片並執行 YOLO'),
            ),
            const SizedBox(height: 12),
            Text(_status),
            const SizedBox(height: 16),

            if (_imageBytes != null && _imageWidth > 0 && _imageHeight > 0)
              AspectRatio(
                aspectRatio: _imageWidth / _imageHeight,
                child: LayoutBuilder(
                  builder: (context, constraints) {
                    final sx = constraints.maxWidth / _imageWidth;
                    final sy = constraints.maxHeight / _imageHeight;

                    return Stack(
                      clipBehavior: Clip.hardEdge,
                      children: [
                        Positioned.fill(
                          child: Image.memory(_imageBytes!, fit: BoxFit.fill),
                        ),
                        for (final result in _results)
                          Positioned(
                            left: result.boundingBox.left * sx,
                            top: result.boundingBox.top * sy,
                            width: result.boundingBox.width * sx,
                            height: result.boundingBox.height * sy,
                            child: Container(
                              decoration: BoxDecoration(
                                border: Border.all(
                                  color: result.className == 'countdown_timer'
                                      ? Colors.yellow
                                      : Colors.green,
                                  width: 2,
                                ),
                              ),
                            ),
                          ),
                      ],
                    );
                  },
                ),
              ),

            const SizedBox(height: 18),
            const Text(
              'YOLO 偵測結果',
              style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
            ),

            if (_results.isEmpty) const Text('目前沒有偵測結果'),

            for (final result in _results)
              Card(
                child: ListTile(
                  title: Text(result.className),
                  subtitle: Text(
                    '信心度：'
                    '${(result.confidence * 100).toStringAsFixed(1)}%\n'
                    '框：${result.boundingBox}',
                  ),
                  trailing: result.className == 'countdown_timer'
                      ? TextButton(
                          onPressed: () => _extractCountdownRoi(result),
                          child: const Text('裁切 ROI'),
                        )
                      : null,
                ),
              ),

            if (_roiBytes != null) ...[
              const SizedBox(height: 20),
              const Text(
                '選取的倒數 ROI',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 10),
              Image.memory(_roiBytes!, height: 200, fit: BoxFit.contain),

              const SizedBox(height: 12),
              ElevatedButton.icon(
                onPressed: _exportCountdownRoi,
                icon: const Icon(Icons.save_alt),
                label: const Text('匯出 ROI 到 App 暫存目錄'),
              ),
            ],

            const SizedBox(height: 24),

            if (_roiBytes != null) ...[
              const SizedBox(height: 16),
              ElevatedButton.icon(
                onPressed: _busy ? null : _runSegmenterV4,
                icon: const Icon(Icons.content_cut),
                label: const Text('執行 Kotlin Segmenter v4'),
              ),
            ],
            if (_segmentation != null) ...[
              const SizedBox(height: 20),
              const Text(
                'Kotlin Segmenter v4 結果',
                style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
              ),
              Text('數字位數：${_segmentation!.digitCount}'),
              Text('分割策略：${_segmentation!.strategy}'),
              Text('數字框：${_segmentation!.boxes}'),
              Text(
                '前景寬高比：'
                '${_segmentation!.aspectRatio.toStringAsFixed(6)}',
              ),
              const SizedBox(height: 12),
              for (var i = 0; i < _segmentation!.crops.length; i++) ...[
                Text('Digit $i'),
                Image.memory(
                  _segmentation!.crops[i],
                  height: 120,
                  fit: BoxFit.contain,
                  filterQuality: FilterQuality.none,
                ),
                const SizedBox(height: 12),
              ],
            ],

            if (_cnnRecognition != null) ...[
              const SizedBox(height: 24),
              const Text(
                'CNN E3 倒數秒數辨識',
                style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 12),
              for (var i = 0; i < _cnnRecognition!.digits.length; i++)
                ListTile(
                  title: Text(
                    'Digit $i：'
                    '${_cnnRecognition!.digits[i]}',
                  ),
                  subtitle: Text(
                    'Softmax 信心度：'
                    '${(_cnnRecognition!.confidences[i] * 100).toStringAsFixed(2)}%',
                  ),
                ),
              const SizedBox(height: 12),
              Text(
                '完整辨識：'
                '${_cnnRecognition!.displayText}',
                style: const TextStyle(fontSize: 18),
              ),
              Text(
                '${_cnnRecognition!.seconds} 秒',
                style: const TextStyle(
                  fontSize: 42,
                  fontWeight: FontWeight.bold,
                ),
              ),
              const Text(
                '僅供模型測試，不作為通行許可。',
                style: TextStyle(color: Colors.grey),
              ),
            ],

            if (_roiBytes != null) ...[
              const SizedBox(height: 20),
              ElevatedButton.icon(
                onPressed: _busy ? null : _runNoOverlapTest,
                icon: const Icon(Icons.compare_arrows),
                label: const Text('測試不重疊 Crop + CNN'),
              ),
            ],
            if (_noOverlapSegmentation != null &&
                _noOverlapRecognition != null) ...[
              const SizedBox(height: 20),
              const Text(
                'B｜不重疊 Crop 測試',
                style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
              ),
              Text('分割策略：${_noOverlapSegmentation!.strategy}'),
              Text('數字框：${_noOverlapSegmentation!.boxes}'),
              const SizedBox(height: 12),
              for (
                var i = 0;
                i < _noOverlapRecognition!.digits.length;
                i++
              ) ...[
                Text('Digit $i：${_noOverlapRecognition!.digits[i]}'),
                Text(
                  '信心度：${(_noOverlapRecognition!.confidences[i] * 100).toStringAsFixed(2)}%',
                ),
                Image.memory(
                  _noOverlapSegmentation!.crops[i],
                  height: 100,
                  fit: BoxFit.contain,
                  filterQuality: FilterQuality.none,
                ),
                const SizedBox(height: 12),
              ],
              Text(
                '完整辨識：${_noOverlapRecognition!.displayText}',
                style: const TextStyle(
                  fontSize: 28,
                  fontWeight: FontWeight.bold,
                ),
              ),
              const Text(
                '診斷測試結果，不作為通行許可。',
                style: TextStyle(color: Colors.grey),
              ),
            ],

            if (_noOverlapSegmentation != null) ...[
              const SizedBox(height: 20),
              ElevatedButton.icon(
                onPressed: _busy ? null : _runOpenCvD5Test,
                icon: const Icon(Icons.science_outlined),
                label: const Text('使用 Kotlin OpenCV D5 辨識'),
              ),
            ],

            if (_noOverlapSegmentation != null) ...[
              const SizedBox(height: 20),
              ElevatedButton.icon(
                onPressed: _busy ? null : _runFp16ComparisonTest,
                icon: const Icon(Icons.memory),
                label: const Text('D｜使用 CNN E3 FP16 辨識'),
              ),
            ],

            if (_fp16Recognition != null) ...[
              const SizedBox(height: 20),
              const Text(
                'D｜Kotlin OpenCV D5 + CNN E3 FP16',
                style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 12),
              for (var i = 0; i < _fp16Recognition!.digits.length; i++) ...[
                Text('Digit $i：${_fp16Recognition!.digits[i]}'),
                Text(
                  'Softmax 信心度：'
                  '${(_fp16Recognition!.confidences[i] * 100).toStringAsFixed(2)}%',
                ),
                const SizedBox(height: 8),
              ],
              const SizedBox(height: 12),
              Text(
                '完整辨識：${_fp16Recognition!.displayText}',
                style: const TextStyle(
                  fontSize: 28,
                  fontWeight: FontWeight.bold,
                ),
              ),
              const SizedBox(height: 12),
              const Text('測試結果不作為通行許可。'),
            ],

            if (_openCvRecognition != null) ...[
              const SizedBox(height: 20),
              const Text(
                'C｜Kotlin OpenCV D5 + CNN E3',
                style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 12),
              for (var i = 0; i < _openCvRecognition!.digits.length; i++) ...[
                Text('Digit $i：${_openCvRecognition!.digits[i]}'),
                Text(
                  'Softmax 信心度：'
                  '${(_openCvRecognition!.confidences[i] * 100).toStringAsFixed(2)}%',
                ),
                const SizedBox(height: 8),
              ],
              const SizedBox(height: 12),
              Text(
                '完整辨識：${_openCvRecognition!.displayText}',
                style: const TextStyle(
                  fontSize: 28,
                  fontWeight: FontWeight.bold,
                ),
              ),
              Text(
                '${_openCvRecognition!.seconds} 秒',
                style: const TextStyle(
                  fontSize: 42,
                  fontWeight: FontWeight.bold,
                ),
              ),
              const Text(
                '診斷測試結果，不作為通行許可。',
                style: TextStyle(color: Colors.grey),
              ),
            ],
            const Text(
              '測試模式：目前不提供安全通行指示',
              style: TextStyle(color: Colors.grey),
            ),
          ],
        ),
      ),
    );
  }
}
