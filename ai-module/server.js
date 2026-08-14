require("dotenv").config();

const fs = require("fs");
const path = require("path");
const express = require("express");
const cors = require("cors");
const multer = require("multer");

const { handleEvent } = require("./aiController");
const { transcribeAudio } = require("./stt");

// 啟動 Event → TTS Listener
require("./ttsListener");

const app = express();
const port = Number(process.env.PORT) || 3000;
const uploadsPath = path.join(__dirname, "uploads");

fs.mkdirSync(uploadsPath, { recursive: true });

app.use(cors());
app.use(express.json());

const upload = multer({
    dest: uploadsPath,
    limits: {
        fileSize: 20 * 1024 * 1024
    }
});

app.get("/health", (req, res) => {
    res.json({
        status: "success",
        message: "AI module server is running",
        data: {
            service: "ai_module"
        }
    });
});

app.post("/upload", upload.single("audio"), async (req, res) => {
    const uploadedAudio = req.file;

    if (!uploadedAudio) {
        return res.status(400).json({
            status: "error",
            message: "沒有收到 audio 音檔",
            data: null
        });
    }

    console.log("📥 收到音檔:", {
        original_name: uploadedAudio.originalname,
        mime_type: uploadedAudio.mimetype,
        size_bytes: uploadedAudio.size
    });

    try {
        const recognizedText = await transcribeAudio(uploadedAudio.path);
        console.log("🧠 STT 結果:", recognizedText);

        const aiResult = handleEvent(recognizedText);

        return res.json({
            status: "success",
            message: "語音指令處理成功",
            data: {
                recognized_text: recognizedText,
                ai_result: aiResult
            }
        });
    } catch (error) {
        console.error("❌ STT / AI 處理失敗:", error.message);

        return res.status(500).json({
            status: "error",
            message: "STT 或 AI 處理失敗",
            data: null
        });
    } finally {
        fs.promises.unlink(uploadedAudio.path).catch(() => {});
    }
});

app.use((error, req, res, next) => {
    if (error instanceof multer.MulterError) {
        return res.status(400).json({
            status: "error",
            message: `音檔上傳失敗：${error.message}`,
            data: null
        });
    }

    return next(error);
});

app.listen(port, () => {
    console.log(`🚀 Server running: http://localhost:${port}`);
});
