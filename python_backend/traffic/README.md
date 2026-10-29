# 行人號誌模組：YOLOv8s v5＋CNN

此提交只修改 python_backend/traffic/，不修改共用main.py、其他組員資料夾或Flutter專案。既有 TrafficDetector(yolo_model_path).detect(image_path, yolo_conf=..., signal_conf_threshold=...) 入口保留，讀秒由PaddleOCR改成已訓練的CNN。模型權重已包含，無須重新訓練。

## 安裝與單獨啟動

建議 Python 3.11。在儲存庫的python_backend目錄執行：

```bash
python3.11 -m venv .venv
source .venv/bin/activate
python -m pip install -r traffic/requirements.txt
python -m traffic.check_models
python -m unittest discover -s traffic/tests -p 'check_*.py' -v
uvicorn traffic.demo_app:app --host 0.0.0.0 --port 8000 --reload
```

這是獨立模組主機，不會覆蓋團隊main.py。主App整合者在既有FastAPI app增加：

```python
from traffic.routes import router as traffic_router
app.include_router(traffic_router)
```

若團隊已有全域錯誤處理器，請合併traffic/error_handlers.py的status/message/data格式，不要直接覆蓋既有處理器。單獨demo_app會安裝該處理器並在startup預載模型；團隊可在既有lifespan呼叫get_traffic_service()作相同預載。

## API

POST /traffic/detect，multipart/form-data，圖片欄位名稱file。接受JPEG/PNG，最高8MB與2000萬畫素。讀取時間較長時請丟棄過期秒數，不排隊播報舊讀值。

```bash
curl -X POST http://127.0.0.1:8000/traffic/detect -F 'file=@/path/to/signal.jpg'
```

回傳遵守根目錄API_SPEC.md。綠燈且秒數>=15，is_safe_to_cross=true；紅燈、小於15秒、秒數未確認均為false。JSON數字與布林保持原型別。成功沒偵測到仍是HTTP200；錯誤使用status:error、message與data:null。

未確認秒數但綠燈可辨時保留light_color:green與light_detected:true，seconds_remaining:null。多方向歧義保守回傳未確認，詳見docs/API_SUPPLEMENT.md。`is_safe_to_cross`只表示專題門檻判斷，不是安全通行保證。

## 模型與命名

models/best_v5.pt：YOLOv8s三類號誌；models/final_v3_recognizer_9I2D_E3.pt：既有CNN。CNN分割和前處理未改，僅對兩個模糊value變數等價改名；原先的模型與holdout檢查保留。完整SHA256見docs/model_manifest.json。可用TRAFFIC_MODEL_DIR指定另一個模型目錄。

Python用snake_case，API的data欄位依正式規格保留。tests/check_contract.py的test_*是unittest固定命名慣例，不是一般變數。

## 語音整合（由Flutter主App處理）

有確認秒數時優先播報，漏偵不播「無法辨識」，也不切斷已開始的秒數。紅燈映射全域最高優先，少於15秒提醒「時間不足，請勿開始穿越」，使用者說話時暫停輸出。這裡只提交後端，沒有聲稱後端本身實作TTS、收音或其他模組的音效仲裁。

## 驗證與限制

已包含API與決策測試，涵蓋15秒邊界、歧義、缺少欄位、型別、無效圖片、辨識忙碌等。真實模型可載入並通過實際圖片管線測試，不代表該次秒數一定正確。CNN仍有高信心誤讀，遠距／斜角仍可能漏偵；僅限受控展示與整合測試，不作獨立道路通行依據。

這個提交尚需整合者掛載router、合併依賴與錯誤格式、在裝置上驗收。沒有自動修改其他組員的共用檔案。
