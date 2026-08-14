const { handleEvent } = require("../aiController");

console.log("\n🧪 Day 19 Navigation + Emergency Integration Test\n");

const tests = [
    "帶我去台北車站",
    "我想去中原大學",
    "請帶我到台北101",

    "幫我打給媽媽",
    "幫我撥給爸爸",
    "幫我打緊急電話",

    "幫我打給小王",

    "停止導航"
];


for (const text of tests) {

    console.log("\n==============================");

    console.log(
        `🎤 使用者輸入: ${text}`
    );

    const result = handleEvent(text);

    console.log(
        "📤 最終結果:",
        result
    );
}


console.log(
    "\n=============================="
);

console.log(
    "✅ Day 19 Step 1 Test 完成"
);