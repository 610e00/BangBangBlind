import 'package:flutter/material.dart';
import 'digit_fp16_test_page.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();

  runApp(
    const MaterialApp(
      debugShowCheckedModeBanner: false,
      home: DigitFp16TestPage(),
    ),
  );
}
