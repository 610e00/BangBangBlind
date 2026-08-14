# 開發規範檢查

## 命名

- 專案內自訂變數已避免使用 `data`、`temp`、`value`、`test` 這類用途不明名稱。
- 測試檔名仍保留 `test*.js`，因為這是測試檔案用途名稱，不是模糊的程式變數。
- `server.js` 回傳物件中的 `data` 保留，因為團隊的統一回傳格式明確要求此 JSON key。
- `frontend/index.html` 的 `event.data` 是瀏覽器 MediaRecorder API 既有屬性，不是自訂命名。

## 資料型別

- 電話、信心值、狀態等依目前介面維持既有合理型別。
- 目前此 AI interaction module 沒有距離欄位；未來若接收距離資料，JSON 欄位應使用 `distance_cm`，值必須為 number。

## JSON

對外 HTTP API 使用小寫 snake_case，例如：

- `recognized_text`
- `ai_result`
- `original_name`
- `mime_type`
- `size_bytes`

統一 HTTP 回傳格式：

```json
{
  "status": "success",
  "message": "說明",
  "data": {}
}
```

或：

```json
{
  "status": "error",
  "message": "錯誤原因",
  "data": null
}
```

EventBus 的內部事件物件屬於 Node.js 內部介面，不等同於對外 HTTP JSON；目前欄位仍保持既有介面，避免破壞已完成的模組串接。

## GitHub

- `.env` 已由 `.gitignore` 排除。
- `node_modules/` 已排除。
- runtime uploads 與產生的音訊檔已排除。
- 提供 `.env.example` 給其他組員建立自己的環境設定。

## 驗證

- 全部 JavaScript 檔案已通過 `node --check`。
- 基礎 AI Controller smoke test 通過。
- `npm test`：4/4 通過。
- `npm run test:safety`：通過。
- Full TTS integration 需在 macOS 且可使用 `afplay` 的環境執行。
