import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:image_picker/image_picker.dart';
import 'package:video_player/video_player.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:bangbangblind_demo/main.dart';

class UnsupportedHdrPlayer extends VideoPlayerController {
  UnsupportedHdrPlayer()
    : super.networkUrl(Uri.parse('http://localhost/unused'));
  @override
  Future<void> initialize() async => throw PlatformException(
    code: 'VideoError',
    message: 'Dolby Vision unsupported',
  );
}

void main() {
  testWidgets(
    'Video workflow starts with upload disabled and image route available',
    (tester) async {
      await tester.pumpWidget(const BangBangBlindApp());
      expect(find.text('選擇影片'), findsOneWidget);
      expect(find.text('尚未選擇影片'), findsOneWidget);
      await tester.scrollUntilVisible(
        find.text('上傳並辨識'),
        200,
        scrollable: find.byType(Scrollable).first,
      );
      final button = tester.widget<FilledButton>(
        find.widgetWithText(FilledButton, '上傳並辨識'),
      );
      expect(button.onPressed, isNull);
      await tester.tap(find.byTooltip('圖片辨識'));
      await tester.pumpAndSettle();
      expect(find.text('選擇測試圖片'), findsOneWidget);
    },
  );

  testWidgets(
    'HDR preview failure preserves the original file and enables upload',
    (tester) async {
      final dir = Directory.systemTemp.createTempSync('hdr-upload-test-');
      final file = File('${dir.path}/sample.MOV')..writeAsBytesSync([1, 2, 3]);
      var previewRequested = false;
      final client = MockClient((request) async {
        previewRequested = request.url.path == '/video-preview';
        return http.Response('Preview unavailable', 503);
      });
      await tester.pumpWidget(
        MaterialApp(
          home: VideoDetectionPage(
            pickVideo: () async => XFile(file.path),
            playerFactory: (_) => UnsupportedHdrPlayer(),
            client: client,
          ),
        ),
      );
      await tester.scrollUntilVisible(
        find.text('選擇影片'),
        200,
        scrollable: find.byType(Scrollable).first,
      );
      await tester.runAsync(() async {
        await tester.tap(find.text('選擇影片'));
        await Future<void>.delayed(const Duration(milliseconds: 200));
      });
      await tester.pumpAndSettle();
      await tester.scrollUntilVisible(
        find.text('上傳並辨識'),
        200,
        scrollable: find.byType(Scrollable).first,
      );
      expect(previewRequested, isTrue);
      expect(find.text('影片已選取，預覽不可用；仍可按「上傳並辨識」'), findsOneWidget);
      expect(
        tester
            .widget<FilledButton>(find.widgetWithText(FilledButton, '上傳並辨識'))
            .onPressed,
        isNotNull,
      );
      await tester.pumpWidget(const SizedBox());
      dir.deleteSync(recursive: true);
    },
  );
}
