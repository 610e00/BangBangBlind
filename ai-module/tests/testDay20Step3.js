const eventBus = require("../events/eventBus");
const { handleEvent } = require("../aiController");
const { speakText } = require("../ttsManager");

console.log("\n🧪 Day 20 Step 3 - Emergency → TTS Test\n");

// ========================================
// Emergency Event → TTS
// ========================================

eventBus.on("emergency", (emergencyEvent) => {

    console.log("\n🚨 Emergency Event:");
    console.log(emergencyEvent);

    let message = "";

    // 一般聯絡人
    if (
        emergencyEvent.type === "contact" &&
        emergencyEvent.contact
    ) {
        message =
            `準備撥打${emergencyEvent.contact}的電話`;
    }

    // 緊急服務
    else if (
        emergencyEvent.type === "emergency_service"
    ) {
        message =
            "準備撥打緊急服務";
    }

    // 找不到聯絡人
    else if (
        emergencyEvent.action === "unknown_contact"
    ) {
        message =
            "無法辨識要聯絡的人";
    }

    if (message) {

        console.log(
            `🔊 Emergency → TTS: ${message}`
        );

        speakText(message);
    }
});


// ========================================
// 測試
// ========================================

const tests = [

    "幫我打給媽媽",

    "幫我撥給爸爸",

    "幫我打緊急電話",

    "幫我打給小王"

];


for (const text of tests) {

    console.log("\n==============================");

    console.log(
        `🎤 使用者輸入: ${text}`
    );

    const result =
        handleEvent(text);

    console.log(
        "\n📤 最終結果:"
    );

    console.log(result);
}


// ========================================
// 等待 TTS
// ========================================

setTimeout(() => {

    console.log("\n==============================");

    console.log(
        "✅ Day 20 Step 3 Test 完成"
    );

    console.log(
        "==============================\n"
    );

}, 12000);
