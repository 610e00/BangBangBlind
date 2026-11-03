# 幫幫盲｜行人號誌辨識 Demo 後端

## 功能介紹

本模組整合 FastAPI、YOLOv8n 與 CNN E3，實現行人號誌及倒數秒數辨識，並搭配 Flutter 提供自主倒數與中文語音播報。

## 技術架構

- YOLOv8n：偵測行人號誌與倒數區域
- CNN E3：辨識倒數秒數
- FastAPI：接收攝影機影格並回傳辨識結果
- Flutter：負責本地倒數追蹤及 TTS 語音播報

## 啟動 Python 後端

```bash
cd python_backend/traffic_demo_backend

python3 -m venv .venv
source .venv/bin/activate

python -m pip install -r requirements.txt

python -m uvicorn main:app --host 127.0.0.1 --port 8004
```

## 啟動 Flutter Demo

另外開啟終端機：

```bash
cd android_app

flutter pub get

flutter run -d emulator-5554 -t lib/main_traffic_demo.dart
```

## API 說明

**POST `/traffic/detect`**

使用 `multipart/form-data` 上傳圖片，欄位名稱為 `file`。

主要回傳欄位：

- `light_color`：辨識到的燈號
- `raw_seconds`：CNN 候選秒數
- `countdown_detected`：是否偵測到倒數區域
- `cnn_confidence`：數字辨識信心分數
- `is_safe_to_cross`：後端通行判斷
- `demo_only`：是否為測試模式

## 模型檔案

- `best.pt`：YOLOv8n 模型
- `models/final_v3_recognizer_9I2D_E3.pt`：CNN E3 模型

## 注意事項

1. Android 模擬器使用 `http://10.0.2.2:8004` 連接電腦上的 FastAPI。
2. 實體 Android 手機不能直接使用 `10.0.2.2`。
3. Demo 以有效倒數推定綠燈的邏輯僅適用於受控測試場景，並非實際燈號確認。
4. 本系統尚未完成真實道路通行安全驗證。
5. Python 依賴版本仍需在全新環境測試。
