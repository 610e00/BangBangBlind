const eventBus = require("./events/eventBus");

const { handleVoice } = require("./controllers/voiceController");
const { handleSafety } = require("./controllers/safetyController");
const { handleEmergency } = require("./controllers/emergencyController");
const { extractDestination } = require("./extractDestination");

const {
    getObstacleData,
    getTrafficLight,
    getFaceStatus
} = require("./modules");

const {
    updateState,
    getState
} = require("./safetyState");

// =========================================
// 🧠 AI Controller
// =========================================

function handleEvent(userText) {
    console.log("\n🧠 AI Controller 啟動");
    console.log("🎤 使用者輸入:", userText);

    if (
        !userText ||
        typeof userText !== "string" ||
        !userText.trim()
    ) {
        const result = {
            mode: "unknown",
            message: "沒有收到有效的語音指令",
            state: getState()
        };

        eventBus.emit("unknown", result);
        return result;
    }

    const normalizedText = userText.trim();
    const voiceResult = handleVoice(normalizedText);

    console.log("🧠 Intent:", voiceResult);

    if (
        !voiceResult ||
        typeof voiceResult.confidence !== "number" ||
        voiceResult.confidence < 0.5
    ) {
        const result = {
            mode: "unknown",
            message: "無法理解語音指令",
            state: getState()
        };

        eventBus.emit("unknown", result);
        return result;
    }

    // =========================================
    // 🚨 Emergency
    // =========================================

    if (voiceResult.type === "emergency") {
        console.log("\n🚨 Emergency Request");

        const emergencyResult = handleEmergency(normalizedText);

        const result = {
            mode: "emergency",
            action: emergencyResult.action,
            ...(emergencyResult.type && { type: emergencyResult.type }),
            ...(emergencyResult.contact && { contact: emergencyResult.contact }),
            ...(emergencyResult.phone && { phone: emergencyResult.phone }),
            ...(emergencyResult.message && { message: emergencyResult.message }),
            state: getState()
        };

        eventBus.emit("emergency", result);
        return result;
    }

    // =========================================
    // 🛑 Stop
    // =========================================

    if (voiceResult.type === "stop") {
        console.log("\n🛑 Stop Request");

        const result = {
            mode: "stop",
            message: "已停止目前操作",
            state: getState()
        };

        eventBus.emit("stop", result);
        return result;
    }

    // =========================================
    // 📡 Perception / Safety
    // =========================================

    const obstacleLevel = getObstacleData();
    const trafficLightStatus = getTrafficLight();
    const faceStatus = getFaceStatus();

    const perception = {
        obstacle: obstacleLevel,
        traffic_light: trafficLightStatus,
        face: faceStatus
    };

    console.log("📡 Perception:", perception);

    const safetyResult = handleSafety(
        obstacleLevel,
        trafficLightStatus,
        faceStatus
    );

    updateState(safetyResult.level || "safe");
    const safetyState = getState();

    console.log("🧠 Safety State:", safetyState);

    if (safetyResult) {
        eventBus.emit("safety", safetyResult);
    }

    // =========================================
    // 🚗 Navigation
    // =========================================

    if (voiceResult.type === "navigation") {
        console.log("\n🚗 Navigation Request");

        const destination = extractDestination(normalizedText);

        if (!destination) {
            const result = {
                mode: "navigation",
                error: "無法辨識導航目的地",
                safety: safetyResult,
                state: safetyState
            };

            eventBus.emit("navigation_error", result);
            return result;
        }

        const result = {
            mode: "navigation",
            destination,
            safety: safetyResult,
            state: safetyState
        };

        eventBus.emit("navigation", result);
        console.log("🚗 Navigation Event:", result);
        return result;
    }

    const result = {
        mode: "unknown",
        message: "未辨識指令",
        state: safetyState
    };

    eventBus.emit("unknown", result);
    return result;
}

module.exports = {
    handleEvent
};
