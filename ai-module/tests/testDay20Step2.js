const eventBus = require("../events/eventBus");
const { handleEvent } = require("../aiController");
const { speakText } = require("../ttsManager");

console.log("\n🧪 Day 20 Step 2 - AI Controller → Safety → TTS\n");


// =========================================
// Safety → TTS
// =========================================

eventBus.on("safety", (safetyEvent) => {

    console.log("\n📢 Safety Event:");

    console.log(safetyEvent);

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
// Navigation Event
// =========================================

eventBus.on("navigation", (navigationEvent) => {

    console.log("\n🚗 Navigation Event:");

    console.log(navigationEvent);
});


// =========================================
// Emergency Event
// =========================================

eventBus.on("emergency", (emergencyEvent) => {

    console.log("\n🚨 Emergency Event:");

    console.log(emergencyEvent);
});


// =========================================
// 測試函式
// =========================================

function runScenario(userInput) {

    console.log("\n==============================");

    console.log(
        `🎤 使用者輸入: ${userInput}`
    );

    console.log("==============================");

    const result = handleEvent(userInput);

    console.log("\n📤 最終結果:");

    console.log(result);
}


// =========================================
// Test 1：Navigation
// =========================================

runScenario("帶我去台北車站");


// =========================================
// 等待 TTS
// 再測 Emergency
// =========================================

setTimeout(() => {

    runScenario("幫我打給媽媽");

}, 6000);


// =========================================
// Test 3：Stop
// =========================================

setTimeout(() => {

    runScenario("停止導航");

}, 8000);


// =========================================
// 結束
// =========================================

setTimeout(() => {

    console.log("\n==============================");

    console.log(
        "✅ Day 20 Step 2 Test 完成"
    );

    console.log("==============================");

}, 10000);
