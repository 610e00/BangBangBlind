const eventBus = require("./events/eventBus");
const {
    speakText,
    clearLevel
} = require("./ttsManager");

console.log("🔊 TTS Listener Ready");

eventBus.on("safety", safetyEvent => {
    if (!safetyEvent || !safetyEvent.message) {
        return;
    }

    console.log("\n🔊 Safety → TTS:", safetyEvent.message);
    speakText(
        safetyEvent.message,
        safetyEvent.level || "normal"
    );
});

eventBus.on("emergency", emergencyEvent => {
    if (!emergencyEvent) {
        return;
    }

    let announcement = "";

    if (
        emergencyEvent.action === "call" &&
        emergencyEvent.type === "contact"
    ) {
        announcement = `準備撥打${emergencyEvent.contact}的電話`;
    } else if (
        emergencyEvent.action === "call" &&
        emergencyEvent.type === "emergency_service"
    ) {
        announcement = "準備撥打緊急服務";
    } else if (emergencyEvent.action === "unknown_contact") {
        announcement = emergencyEvent.message || "無法辨識要聯絡的人";
    }

    if (!announcement) {
        return;
    }

    console.log("\n🚨 Emergency → TTS:", announcement);
    speakText(announcement, "emergency");
});

eventBus.on("navigation", navigationEvent => {
    if (!navigationEvent || !navigationEvent.destination) {
        return;
    }

    const announcement = `開始導航前往${navigationEvent.destination}`;
    console.log("\n🔊 Navigation → TTS:", announcement);
    speakText(announcement, "navigation");
});

eventBus.on("navigation_error", navigationErrorEvent => {
    const announcement =
        navigationErrorEvent?.error || "無法辨識導航目的地";

    console.log("\n⚠️ Navigation Error → TTS:", announcement);
    speakText(announcement, "attention");
});

eventBus.on("stop", () => {
    clearLevel("navigation");

    const announcement = "已停止目前操作";
    console.log("\n🔊 Stop → TTS:", announcement);
    speakText(announcement, "stop");
});

eventBus.on("faceAnnouncement", faceAnnouncement => {
    if (!faceAnnouncement || !faceAnnouncement.message) {
        return;
    }

    console.log("\n🔊 Face → TTS:", faceAnnouncement.message);
    speakText(faceAnnouncement.message, "face");
});

eventBus.on("unknown", unknownEvent => {
    if (!unknownEvent || !unknownEvent.message) {
        return;
    }

    console.log("\n❓ Unknown → TTS:", unknownEvent.message);
    speakText(unknownEvent.message, "attention");
});

module.exports = {};
