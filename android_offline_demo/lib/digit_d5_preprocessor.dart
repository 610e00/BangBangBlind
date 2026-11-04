import 'dart:typed_data';

import 'package:image/image.dart' as img;

class DigitD5Preprocessor {
  static const int size = 64;
  static const int tensorLength = 3 * size * size;

  static List<double> preprocess(Uint8List imageBytes) {
    final decoded = img.decodeImage(imageBytes);

    if (decoded == null) {
      throw FormatException('無法解碼數字圖片');
    }

    final width = decoded.width;
    final height = decoded.height;

    if (width <= 0 || height <= 0) {
      throw StateError('圖片尺寸無效');
    }

    // 與 Python D5 相同：保持比例
    final scale = [size / width, size / height].reduce((a, b) => a < b ? a : b);

    final newWidth = (width * scale).round().clamp(1, size).toInt();

    final newHeight = (height * scale).round().clamp(1, size).toInt();

    // 縮小採平均插值，放大採線性插值。
    // 注意：不保證與 OpenCV 逐像素完全一致。
    final interpolation = scale < 1.0
        ? img.Interpolation.average
        : img.Interpolation.linear;

    final resized = img.copyResize(
      decoded,
      width: newWidth,
      height: newHeight,
      interpolation: interpolation,
    );

    final x0 = (size - newWidth) ~/ 2;
    final y0 = (size - newHeight) ~/ 2;

    // NCHW：R 平面、G 平面、B 平面
    final tensor = Float32List(tensorLength);

    for (var y = 0; y < newHeight; y++) {
      for (var x = 0; x < newWidth; x++) {
        final pixel = resized.getPixel(x, y);

        final dstX = x + x0;
        final dstY = y + y0;
        final index = dstY * size + dstX;

        tensor[index] = pixel.r.toDouble() / 255.0;
        tensor[size * size + index] = pixel.g.toDouble() / 255.0;
        tensor[2 * size * size + index] = pixel.b.toDouble() / 255.0;
      }
    }

    if (tensor.length != tensorLength) {
      throw StateError('D5 Tensor 長度錯誤');
    }

    return tensor;
  }
}
