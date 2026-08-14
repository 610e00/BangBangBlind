const { handleEvent } = require("../aiController");

const {
    speakText,
    clearQueue
} = require("../ttsManager");

const {
    setObstacleData,
    setTrafficLight,
    setFaceStatus
} = require("../modules");

const {
    setFace,
    clearFace,
    detectFace
} = require("../faceDetector");

const {
    handleFace
} = require("../controllers/faceController");


// =========================================
// TTS Listener
// =========================================

require("../ttsListener");


// =========================================
// Day 29
// =========================================

console.log("\n🧪 Day 29 - Full AI Integration Test\n");


// =========================================
// 清空 TTS
// =========================================

clearQueue();


// =========================================
// 1. Navigation
// =========================================

console.log("==============================");
console.log("1️⃣ 🚗 Navigation");
console.log("==============================");

setObstacleData("low");
setTrafficLight("green");
setFaceStatus("known");

handleEvent("帶我去台北車站");


// =========================================
// 等待導航語音開始
// =========================================

setTimeout(() => {

    // =====================================
    // 2. Danger
    // =====================================

    console.log("\n==============================");
    console.log("2️⃣ 🔴 Danger");
    console.log("==============================");

    setObstacleData("high");
    setTrafficLight("red");

    speakText(
        "前方障礙物與紅燈，請立即停止",
        "danger"
    );


}, 1500);


// =========================================
// 3. Face Recognition
// =========================================

setTimeout(() => {

    console.log("\n==============================");
    console.log("3️⃣ 👤 Face Recognition");
    console.log("==============================");

    setObstacleData("low");
    setTrafficLight("green");

    setFace("媽媽", 0.95);

    handleFace(detectFace());

    setTimeout(() => {

        handleFace(detectFace());

    }, 3200);


}, 4000);


// =========================================
// 4. Emergency
// =========================================

setTimeout(() => {

    console.log("\n==============================");
    console.log("4️⃣ 🚨 Emergency");
    console.log("==============================");

    handleEvent("幫我打給媽媽");


}, 8000);


// =========================================
// 5. Stop
// =========================================

setTimeout(() => {

    console.log("\n==============================");
    console.log("5️⃣ 🛑 Stop");
    console.log("==============================");

    handleEvent("停止導航");


}, 10000);


// =========================================
// 6. Face Disappear
// =========================================

setTimeout(() => {

    console.log("\n==============================");
    console.log("6️⃣ 👻 Face Disappear");
    console.log("==============================");

    clearFace();

    handleFace(detectFace());


}, 12000);


// =========================================
// Finish
// =========================================

setTimeout(() => {

    console.log("\n==============================");
    console.log("✅ Day 29 Full Integration Test 完成");
    console.log("==============================\n");

}, 14000);
