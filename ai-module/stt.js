require("dotenv").config();

const fs = require("fs");
const OpenAI = require("openai");

function createOpenAIClient() {
    if (!process.env.OPENAI_API_KEY) {
        throw new Error("尚未設定 OPENAI_API_KEY");
    }

    return new OpenAI({
        apiKey: process.env.OPENAI_API_KEY
    });
}

async function transcribeAudio(audioFilePath) {
    if (!audioFilePath || !fs.existsSync(audioFilePath)) {
        throw new Error("找不到要辨識的音檔");
    }

    console.log("🎤 開始語音辨識...");

    const openaiClient = createOpenAIClient();
    const transcription = await openaiClient.audio.transcriptions.create({
        file: fs.createReadStream(audioFilePath),
        model: "whisper-1"
    });

    const recognizedText = transcription.text?.trim();

    if (!recognizedText) {
        throw new Error("STT 沒有辨識出有效文字");
    }

    console.log("📝 辨識結果:", recognizedText);
    return recognizedText;
}

module.exports = {
    transcribeAudio
};
