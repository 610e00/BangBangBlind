const emergencyService = require("./emergencyService");

/**
 * 🎤 AI語音SOS處理入口
 * 👉 AI回來的資料都從這裡進
 */
function handleVoiceSOS(aiResponse, gpsData) {

    // 🧠 AI沒同意 → 不做事
    if (!aiResponse.confirmed) {
        return {
            action: "cancel",
            message: "user cancelled"
        };
    }

    // 🟢 AI同意 → 發送SOS
    const result = emergencyService.sendSOS(
        "voice_request",
        gpsData,
        aiResponse.targetName || null,
        "voice"
    );

    return {
        action: "sos_sent",
        targetName: aiResponse.targetName || "first_priority",
        data: result
    };
}

module.exports = {
    handleVoiceSOS
};