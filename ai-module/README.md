# AI Module

AI 眼鏡的人機互動層原型，負責語音輸入、意圖辨識、事件協調與語音回饋。

## 已完成

- Navigation / Emergency / Stop / Unknown intent routing
- Navigation destination extraction
- Emergency contact resolution
- Safety state integration
- Face announcement event
- EventBus integration
- TTS priority queue and interruption
- Frontend audio upload endpoint
- STT adapter for OpenAI audio transcription

## 專案結構

```text
ai-module/
├── controllers/
├── events/
├── frontend/
├── tests/
├── aiController.js
├── contactParser.js
├── contacts.json
├── extractDestination.js
├── faceDetector.js
├── modules.js
├── recorder.js
├── riskEngine.js
├── safetyState.js
├── server.js
├── stt.js
├── tts.js
├── ttsListener.js
├── ttsManager.js
├── package.json
└── .env.example
```

## 安裝

```bash
npm install
cp .env.example .env
```

在 `.env` 填入自己的 `OPENAI_API_KEY`。請勿提交 `.env`。

## 執行

```bash
npm start
```

健康檢查：`GET /health`

語音上傳：`POST /upload`，multipart form field 名稱為 `audio`。

## 統一 API 回傳

成功：

```json
{
  "status": "success",
  "message": "說明",
  "data": {}
}
```

失敗：

```json
{
  "status": "error",
  "message": "錯誤原因",
  "data": null
}
```

## 測試

```bash
npm test
npm run test:safety
npm run test:integration
```

`test:integration` 會使用 TTS，macOS 環境需可使用 `afplay`。

## 串接介面

目前 `modules.js` 與 `faceDetector.js` 是模擬感知來源。之後可由其他組員的障礙物、紅綠燈、人臉辨識模組替換資料來源，不需要改動 AI Controller 的主要事件流程。
