import 'package:flutter/material.dart';
import 'countdown_fp16_test_page.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();

  runApp(
    const MaterialApp(
      debugShowCheckedModeBanner: false,
      home: CountdownFp16TestPage(),
    ),
  );
}
