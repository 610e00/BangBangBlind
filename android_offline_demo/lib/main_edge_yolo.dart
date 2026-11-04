import 'package:flutter/material.dart';
import 'live_detection_page.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const EdgeYoloApp());
}

class EdgeYoloApp extends StatelessWidget {
  const EdgeYoloApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: '幫幫盲｜YOLO v6 本地測試',
      home: const LiveDetectionPage(),
    );
  }
}
