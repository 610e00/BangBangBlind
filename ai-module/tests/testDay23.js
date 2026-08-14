const { handleEvent } = require("../aiController");

// 啟動 TTS Listener
require("../ttsListener");

console.log("\n🧪 Day 23 Full AI → EventBus → Priority TTS Test\n");


function wait(ms) {
    return new Promise(resolve => {
        setTimeout(resolve, ms);
    });
}


async function runTest() {

    // =========================================
    // 🚗 Navigation
    // =========================================

    console.log("\n==============================");
    console.log("1️⃣ 🚗 Navigation");
    console.log("==============================");

    handleEvent("帶我去台北車站");

    await wait(1000);


    // =========================================
    // 🚨 Emergency
    // =========================================

    console.log("\n==============================");
    console.log("2️⃣ 🚨 Emergency");
    console.log("==============================");

    handleEvent("幫我打給媽媽");

    await wait(1000);


    // =========================================
    // 🆘 Emergency Service
    // =========================================

    console.log("\n==============================");
    console.log("3️⃣ 🆘 Emergency Service");
    console.log("==============================");

    handleEvent("幫我打緊急電話");

    await wait(1000);


    // =========================================
    // 🛑 Stop
    // =========================================

    console.log("\n==============================");
    console.log("4️⃣ 🛑 Stop");
    console.log("==============================");

    handleEvent("停止導航");


    // =========================================
    // 等待所有 TTS
    // =========================================

    console.log("\n⏳ 等待所有 TTS 播放完成...\n");

    await wait(15000);


    console.log("\n==============================");
    console.log("✅ Day 23 Full Integration Test 完成");
    console.log("==============================");
}


runTest();
