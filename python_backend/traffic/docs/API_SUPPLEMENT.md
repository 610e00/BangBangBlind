# 原規格以外的處理決定

以下保留原API_SPEC，不宣稱已由全團隊正式核准，整合時請確認。

1. 綠燈可辨但秒數未知：success、light_detected=true、light_color=green、seconds_remaining=null、is_safe_to_cross=false。不把無法讀秒等同沒有燈，也不猜測秒數。
2. 紅燈：秒數固定null、is_safe_to_cross=false，不必先讀CNN才警告。
3. 多個可信行人號誌／方向不明：保守回傳未偵測到（light_detected=false、其餘null／false）。這表示「沒有確認單一號誌」，不保證現場沒有號誌；原規格沒有歧義狀態欄位，因此不額外添加欄位。
4. 有綠燈，但找不到唯一相鄰倒數：保留綠燈，秒數null。CNN最低信心0.8、秒數必須是0到99的整數；即便高信心仍可能誤讀。
5. 本模組沒有倒數OCR文字接口、沒有安全通行指令、沒有自動代替其他模組作決定。
6. 健康檢查沒有另加私有/frame路徑；單獨範例的/openapi.json和/docs是FastAPI的文件功能，不是號誌辨識回傳格式。
