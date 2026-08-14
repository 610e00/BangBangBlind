const eventBus = require("../events/eventBus");
const { speakText } = require("../ttsManager");

console.log("\n🧪 Day 20 Step 1 - Safety Event → TTS Test\n");


// =========================================
// Safety Event Listener
// =========================================

eventBus.on("safety", (safetyEvent) => {

    console.log("\n📢 Safety Event 收到:");
    console.log(safetyEvent);

    // 只有需要提醒的狀態才播報
    if (
        safetyEvent.level === "attention" ||
        safetyEvent.level === "caution" ||
        safetyEvent.level === "danger"
    ) {

        console.log(
            `🔊 Safety → TTS: ${safetyEvent.message}`
        );

        speakText(safetyEvent.message);
    }
});


// =========================================
// 測試 Safety Event
// =========================================

console.log("\n========== 👀 Attention ==========");

eventBus.emit("safety", {
    level: "attention",
    message: "偵測到未知人物，請注意周圍安全"
});


setTimeout(() => {

    console.log("\n========== 🟡 Caution ==========");

    eventBus.emit("safety", {
        level: "caution",
        message: "前方紅燈，請減速並注意環境"
    });

}, 5000);


setTimeout(() => {

    console.log("\n========== 🔴 Danger ==========");

    eventBus.emit("safety", {
        level: "danger",
        message: "前方障礙物與紅燈，請立即停止"
    });

}, 10000);


// =========================================
// 結束
// =========================================

setTimeout(() => {

    console.log("\n==============================");
    console.log("✅ Day 20 Step 1 Test 完成");
    console.log("==============================");

}, 16000);
