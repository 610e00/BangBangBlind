const { handleEvent } = require("../aiController");

// 啟動 TTS Listener
require("../ttsListener");

console.log("\n🧪 Day 21 EventBus → TTS Integration Test\n");


async function wait(ms) {
    return new Promise(resolve => {
        setTimeout(resolve, ms);
    });
}


async function runTest() {

    // =========================================
    // 🚗 Navigation
    // =========================================

    console.log("\n==============================");
    console.log("🎤 導航測試");
    console.log("==============================");

    handleEvent("帶我去台北車站");

    await wait(5000);


    // =========================================
    // 🚨 Emergency
    // =========================================

    console.log("\n==============================");
    console.log("🎤 緊急聯絡測試");
    console.log("==============================");

    handleEvent("幫我打給媽媽");

    await wait(5000);


    // =========================================
    // 🛑 Stop
    // =========================================

    console.log("\n==============================");
    console.log("🎤 停止導航測試");
    console.log("==============================");

    handleEvent("停止導航");

    await wait(5000);


    console.log("\n==============================");
    console.log("✅ Day 21 Test 完成");
    console.log("==============================");
}


runTest();
