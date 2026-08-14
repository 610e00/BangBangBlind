const os = require("os");
const path = require("path");
const gTTS = require("gtts");

function speak(text) {
    return new Promise((resolve, reject) => {
        if (!text || typeof text !== "string") {
            reject(new Error("TTS 沒有收到有效文字"));
            return;
        }

        const speech = new gTTS(text, "zh-tw");
        const speechFilePath = path.join(
            os.tmpdir(),
            `ai_module_tts_${process.pid}_${Date.now()}.mp3`
        );

        speech.save(speechFilePath, error => {
            if (error) {
                reject(error);
                return;
            }

            console.log("🔊 語音已生成:", speechFilePath);
            resolve(speechFilePath);
        });
    });
}

module.exports = {
    speak
};
