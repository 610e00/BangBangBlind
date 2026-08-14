const {
    setObstacleData,
    setTrafficLight,
    setFaceStatus,
    getPerceptionState
} = require("../modules");

const {
    handleSafety
} = require("../controllers/safetyController");

const {
    updateState,
    getState
} = require("../safetyState");


console.log("\n🧪 Day 18 Safety State Machine Test\n");


// =========================================
// 測試工具
// =========================================

function testScenario(
    name,
    obstacle,
    trafficLight,
    face
) {

    console.log(
        `\n========== ${name} ==========`
    );


    // 1. 更新感知資料

    setObstacleData(obstacle);

    setTrafficLight(trafficLight);

    setFaceStatus(face);


    // 2. 取得感知狀態

    const perception =
        getPerceptionState();


    console.log(
        "📡 Perception:",
        perception
    );


    // 3. Safety Controller

    const safetyResult =
        handleSafety(
            obstacle,
            trafficLight,
            face
        );


    console.log(
        "🛡️ Safety Result:",
        safetyResult
    );


    // 4. 更新 State Machine

    updateState(
        safetyResult.level
    );


    console.log(
        "🧠 Safety State:",
        getState()
    );
}


// =========================================
// 1. 安全
// =========================================

testScenario(
    "🟢 安全環境",
    "low",
    "green",
    "known"
);


// =========================================
// 2. 紅燈
// =========================================

testScenario(
    "🟡 紅燈",
    "low",
    "red",
    "known"
);


// =========================================
// 3. 危險
// =========================================

testScenario(
    "🔴 障礙物 + 紅燈",
    "high",
    "red",
    "known"
);


// =========================================
// 4. 恢復安全
// =========================================

testScenario(
    "🟢 恢復安全",
    "low",
    "green",
    "known"
);


console.log(
    "\n================================"
);

console.log(
    "🧠 最終 Safety State:",
    getState()
);

console.log(
    "================================"
);

console.log(
    "\n✅ Day 18 Safety State Test 完成"
);