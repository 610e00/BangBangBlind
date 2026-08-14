const eventBus = require("../events/eventBus");
const { handleEvent } = require("../aiController");

let passedCount = 0;
let caseCount = 0;

function runCase(caseName, caseFunction) {
    caseCount++;
    console.log(`\n===== ${caseName} =====`);

    try {
        const caseResult = caseFunction();
        console.log("結果:", caseResult);
        passedCount++;
    } catch (error) {
        console.log("❌ FAIL:", error.message);
    }
}

eventBus.on("navigation", navigationEvent => {
    console.log("🚗 Navigation Event:", navigationEvent);
});

eventBus.on("emergency", emergencyEvent => {
    console.log("🚨 Emergency Event:", emergencyEvent);
});

eventBus.on("safety", safetyEvent => {
    console.log("⚠️ Safety Event:", safetyEvent);
});

runCase("Navigation Test", () => handleEvent("帶我去台北車站"));
runCase("Emergency Test", () => handleEvent("幫我打給媽媽"));
runCase("Stop Test", () => handleEvent("停止"));
runCase("Navigation Phrase Test", () => handleEvent("我想去學校"));

setTimeout(() => {
    console.log("\n====================");
    console.log(`✅ PASS: ${passedCount}/${caseCount}`);
    console.log("====================\n");
}, 500);
