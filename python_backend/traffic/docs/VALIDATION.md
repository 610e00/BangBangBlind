# 此次後端提交驗證（2026-10-29）

範圍僅限python_backend/traffic/。沒有修改Flutter、其他功能目錄、共用main.py或根目錄依賴。

- 15項單元及ASGI請求測試通過：標準回傳格式、15秒邊界、紅燈、未知秒數、錯誤型別、方向歧義、CNN低信心與分割失敗、multipart成功與缺失欄位、圖片錯誤、429/503、原TrafficDetector建構與detect入口相容。
- YOLOv8s v5與CNN權重均可在既有Python推論環境載入，CNN雜湊符合原模型。權重及來源見model_manifest.json。
- 原分割器和CNN前處理只作自訂value變數等價改名，未改模型架構、前處理或訓練。已作AST等價檢查。
- 統一接口在獨立主機traffic.demo_app:app提供POST /traffic/detect。測試可不使用外部網路或測試圖片檔，真實模型不參與單元測試的固定秒數斷言。
- 已另作實際照片的模型管線測試，但沒有把模型輸出當作人工真值或安全性證明。

尚需團隊驗證：乾淨環境安裝requirements、將router掛載到團隊main.py、共用錯誤處理與所有模組音效優先級、手機網路及語音、相機延遲與讀秒正確率。原CNN仍有高信心誤讀，詳見KNOWN_LIMITATIONS.md。

重跑：在python_backend執行 `python -m unittest discover -s traffic/tests -p 'check_*.py' -v`。
