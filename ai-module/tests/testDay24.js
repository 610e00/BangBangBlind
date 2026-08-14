const {
    speakText,
    clearQueue
} = require("../ttsManager");

console.log("\n🧪 Day 24 - TTS Priority Queue Test\n");


// 清空舊佇列
clearQueue();


// =========================================
// 依序加入不同優先級
// =========================================

console.log("\n📥 加入 TTS 任務...\n");

speakText(
    "開始導航前往台北車站",
    "navigation"
);

speakText(
    "偵測到未知人物，請注意周圍安全",
    "attention"
);

speakText(
    "前方紅燈，請減速並注意環境",
    "caution"
);

speakText(
    "準備撥打媽媽的電話",
    "emergency"
);

speakText(
    "已停止目前操作",
    "stop"
);

speakText(
    "前方障礙物與紅燈，請立即停止",
    "danger"
);


// =========================================
// 等待所有 TTS
// =========================================

console.log("\n⏳ 等待所有語音播放完成...\n");

setTimeout(() => {

    console.log("\n==============================");
    console.log("✅ Day 24 Priority Queue Test 完成");
    console.log("==============================");

}, 20000);
