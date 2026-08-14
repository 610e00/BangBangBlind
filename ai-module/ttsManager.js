const fs = require("fs");
const { spawn } = require("child_process");
const { speak } = require("./tts");

let isPlaying = false;
let speechQueue = [];
let currentSpeechItem = null;
let currentAudioProcess = null;
let interruptionToken = 0;

const PRIORITY = {
    danger: 100,
    stop: 90,
    emergency: 80,
    caution: 60,
    attention: 40,
    face: 35,
    navigation: 20,
    safe: 15,
    normal: 10
};

function getPriority(level) {
    return PRIORITY[level] ?? PRIORITY.normal;
}

function playAudio(audioFilePath, playbackToken) {
    return new Promise((resolve, reject) => {
        const audioProcess = spawn("afplay", [audioFilePath]);
        currentAudioProcess = audioProcess;

        audioProcess.on("error", error => {
            if (currentAudioProcess === audioProcess) {
                currentAudioProcess = null;
            }
            reject(error);
        });

        audioProcess.on("close", exitCode => {
            if (currentAudioProcess === audioProcess) {
                currentAudioProcess = null;
            }

            if (playbackToken !== interruptionToken) {
                resolve({ interrupted: true });
                return;
            }

            if (exitCode === 0) {
                resolve({ interrupted: false });
                return;
            }

            reject(new Error(`afplay 結束，code: ${exitCode}`));
        });
    });
}

function interruptCurrent() {
    if (!isPlaying || !currentAudioProcess || !currentSpeechItem) {
        return;
    }

    console.log(
        `⛔ 立即中斷 TTS [${currentSpeechItem.level}]: ${currentSpeechItem.text}`
    );

    interruptionToken++;
    currentAudioProcess.kill("SIGTERM");
}

async function processQueue() {
    if (isPlaying || speechQueue.length === 0) {
        return;
    }

    isPlaying = true;
    speechQueue.sort((firstItem, secondItem) =>
        secondItem.priority - firstItem.priority
    );

    currentSpeechItem = speechQueue.shift();
    const speechItem = currentSpeechItem;
    const playbackToken = interruptionToken;
    let speechFilePath = null;

    try {
        console.log(
            `🔊 TTS 開始播報 [${speechItem.level}]: ${speechItem.text}`
        );

        speechFilePath = await speak(speechItem.text);

        if (playbackToken !== interruptionToken) {
            console.log(
                `⛔ TTS 略過已中斷語音 [${speechItem.level}]: ${speechItem.text}`
            );
        } else {
            const playbackResult = await playAudio(
                speechFilePath,
                playbackToken
            );

            if (!playbackResult.interrupted) {
                console.log(
                    `✅ TTS 播放完成 [${speechItem.level}]: ${speechItem.text}`
                );
            }
        }
    } catch (error) {
        console.error("❌ TTS 播放失敗:", error.message);
    } finally {
        if (speechFilePath) {
            fs.promises.unlink(speechFilePath).catch(() => {});
        }

        if (currentSpeechItem === speechItem) {
            currentSpeechItem = null;
        }

        isPlaying = false;
        processQueue();
    }
}

function speakText(text, level = "normal") {
    if (!text || typeof text !== "string") {
        return;
    }

    const speechItem = {
        text: text.trim(),
        level,
        priority: getPriority(level)
    };

    console.log(`📥 TTS Queue [${level}]: ${speechItem.text}`);
    speechQueue.push(speechItem);

    if (
        isPlaying &&
        currentSpeechItem &&
        speechItem.priority > currentSpeechItem.priority
    ) {
        interruptCurrent();
    }

    processQueue();
}

function clearQueue() {
    const removedCount = speechQueue.length;
    speechQueue = [];

    if (isPlaying && currentAudioProcess) {
        interruptionToken++;
        currentAudioProcess.kill("SIGTERM");
    }

    console.log(`🧹 TTS Queue 已清空 → ${removedCount} 個等待任務`);
}

function clearLevel(level) {
    const previousCount = speechQueue.length;
    speechQueue = speechQueue.filter(
        speechItem => speechItem.level !== level
    );
    const removedCount = previousCount - speechQueue.length;

    console.log(
        `🧹 TTS Queue 清除 [${level}] → ${removedCount} 個任務`
    );
}

function getStatus() {
    return {
        is_playing: isPlaying,
        current_item: currentSpeechItem,
        queue: [...speechQueue]
    };
}

module.exports = {
    speakText,
    clearQueue,
    clearLevel,
    getStatus
};
