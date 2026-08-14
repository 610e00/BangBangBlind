const { handleFace } = require("../controllers/faceController");
const { setFace, clearFace, detectFace } = require("../faceDetector");

require("../ttsListener");

function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}


async function runTest() {

    console.log("\n🧪 Day 28 - Face → EventBus → TTS Test");


    // =========================================
    // 1. 小明出現
    // =========================================

    console.log("\n==============================");
    console.log("1️⃣ 👤 小明出現");
    console.log("==============================");

    setFace("小明", 0.92);

    await handleFace(
        detectFace()
    );


    // =========================================
    // 2. 持續偵測
    // =========================================

    console.log("\n⏳ 持續偵測 3 秒...");

    await sleep(1500);

    await handleFace(
        detectFace()
    );

    await sleep(1700);

    await handleFace(
        detectFace()
    );


    // =========================================
    // 3. 換成小王
    // =========================================

    console.log("\n==============================");
    console.log("2️⃣ 👤 小王出現");
    console.log("==============================");

    setFace("小王", 0.91);

    await handleFace(
        detectFace()
    );

    await sleep(3200);

    await handleFace(
        detectFace()
    );


    // =========================================
    // 4. 人臉消失
    // =========================================

    console.log("\n==============================");
    console.log("3️⃣ 👻 人臉消失");
    console.log("==============================");

    clearFace();

    await handleFace(
        detectFace()
    );


    console.log("\n==============================");
    console.log("✅ Day 28 Face Test 完成");
    console.log("==============================");
}


runTest();
