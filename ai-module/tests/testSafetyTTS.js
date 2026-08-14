const eventBus = require("../events/eventBus");

// 啟動 Safety → TTS Listener
require("../ttsListener");


console.log("\n🧪 Day 18 Safety → TTS Test\n");


// =========================================
// 測試 1：Caution
// =========================================

console.log(
    "\n========== 🟡 Caution =========="
);

eventBus.emit("safety", {

    level: "caution",

    message:
        "前方紅燈，請減速並注意環境"

});


// =========================================
// 等待一段時間
// =========================================

setTimeout(() => {


    // =========================================
    // 測試 2：Danger
    // =========================================

    console.log(
        "\n========== 🔴 Danger =========="
    );


    eventBus.emit("safety", {

        level: "danger",

        message:
            "前方障礙物與紅燈，請立即停止"

    });


}, 5000);