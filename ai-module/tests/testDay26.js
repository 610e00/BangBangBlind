const { handleEvent } = require("../aiController");
const { speakText, clearQueue } = require("../ttsManager");

console.log("\n🧪 Day 26 - Full AI Safety & TTS Stress Test\n");

clearQueue();

function wait(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}

async function runTest() {

    // =========================================
    // 1. Navigation
    // =========================================

    console.log("\n==============================");
    console.log("1️⃣ 🚗 Navigation");
    console.log("==============================");

    handleEvent("帶我去台北車站");

    speakText(
        "開始導航前往台北車站",
        "navigation"
    );

    await wait(1000);


    // =========================================
    // 2. Danger
    // =========================================

    console.log("\n==============================");
    console.log("2️⃣ 🔴 Danger");
    console.log("==============================");

    speakText(
        "前方障礙物與紅燈，請立即停止",
        "danger"
    );

    await wait(1000);


    // =========================================
    // 3. Emergency
    // =========================================

    console.log("\n==============================");
    console.log("3️⃣ 🚨 Emergency");
    console.log("==============================");

    handleEvent("幫我打給媽媽");

    speakText(
        "準備撥打媽媽的電話",
        "emergency"
    );

    await wait(1000);


    // =========================================
    // 4. Stop
    // =========================================

    console.log("\n==============================");
    console.log("4️⃣ 🛑 Stop");
    console.log("==============================");

    handleEvent("停止導航");

    speakText(
        "已停止目前操作",
        "stop"
    );

    await wait(1000);


    // =========================================
    // 5. Attention
    // =========================================

    console.log("\n==============================");
    console.log("5️⃣ 👀 Attention");
    console.log("==============================");

    speakText(
        "偵測到未知人物，請注意周圍安全",
        "attention"
    );


    // =========================================
    // 6. 等待
    // =========================================

    console.log("\n==============================");
    console.log("⏳ 等待所有 TTS 完成...");
    console.log("==============================");

    await wait(12000);

    console.log("\n==============================");
    console.log("✅ Day 26 Full Stress Test 完成");
    console.log("==============================");
}

runTest();
