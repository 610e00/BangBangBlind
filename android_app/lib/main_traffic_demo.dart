import 'package:flutter/material.dart';
import 'traffic/camera_live_test_page.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const TrafficDemoApp());
}

class TrafficDemoApp extends StatelessWidget {
  const TrafficDemoApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: '幫幫盲 - 行人號誌 Demo',
      debugShowCheckedModeBanner: false,
      home: const CameraLiveTestPage(),
    );
  }
}
