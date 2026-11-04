import 'package:flutter/material.dart';

import 'camera_edge_fp16_page.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();

  runApp(
    const MaterialApp(
      debugShowCheckedModeBanner: false,
      home: CameraEdgeFp16Page(),
    ),
  );
}
