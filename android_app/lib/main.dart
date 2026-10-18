import 'dart:async';

import 'package:flutter/material.dart';

import 'controllers/obstacle_controller.dart';


void main() {
  WidgetsFlutterBinding
      .ensureInitialized();

  runApp(
    const MyApp(),
  );
}


class MyApp extends StatefulWidget {
  const MyApp({
    super.key,
  });

  @override
  State<MyApp> createState() =>
      _MyAppState();
}


class _MyAppState extends State<MyApp> {
  final ObstacleController
      _obstacleController =
      ObstacleController();


  String _status =
      '避障系統初始化中...';


  @override
  void initState() {
    super.initState();


    // 等 Flutter 畫面建立後
    // 再啟動 UVC / YOLO
    WidgetsBinding.instance
        .addPostFrameCallback(
      (_) {
        unawaited(
          _startObstacleSystem(),
        );
      },
    );
  }


  Future<void>
      _startObstacleSystem() async {
    try {
      await _obstacleController
          .start();


      if (!mounted) {
        return;
      }


      setState(() {
        _status =
            '避障系統運作中';
      });
    } catch (error) {
      debugPrint(
        '避障系統啟動失敗：$error',
      );


      if (!mounted) {
        return;
      }


      setState(() {
        _status =
            '避障系統啟動失敗\n$error';
      });
    }
  }


  @override
  void dispose() {
    unawaited(
      _obstacleController.stop(),
    );

    super.dispose();
  }


  @override
  Widget build(
    BuildContext context,
  ) {
    return MaterialApp(
      debugShowCheckedModeBanner:
          false,

      title:
          'AI之眼幫幫盲',

      home: Scaffold(
        appBar: AppBar(
          title: const Text(
            'AI之眼幫幫盲',
          ),
        ),

        body: Center(
          child: Text(
            _status,

            textAlign:
                TextAlign.center,

            style:
                const TextStyle(
              fontSize: 22,
            ),
          ),
        ),
      ),
    );
  }
}