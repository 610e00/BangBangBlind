const {
    setObstacleData,
    setTrafficLight,
    setFaceStatus,
    getPerceptionState
} = require("../modules");

const {
    handleSafety
} = require("../controllers/safetyController");


console.log("\n🧪 Day 18 Safety Controller Test\n");


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


    // 設定感知資料

    setObstacleData(obstacle);

    setTrafficLight(trafficLight);

    setFaceStatus(face);


    // 取得目前感知狀態

    const perception =
        getPerceptionState();


    console.log(
        "📡 Perception:",
        perception
    );


    // Safety Controller

    const result =
        handleSafety(
            obstacle,
            trafficLight,
            face
        );


    console.log(
        "🛡️ Safety Result:",
        result
    );


    return result;
}


// =========================================
// Scenario 1
// =========================================

testScenario(
    "🟢 安全環境",
    "low",
    "green",
    "unknown"
);


// =========================================
// Scenario 2
// =========================================

testScenario(
    "🟡 紅燈注意",
    "low",
    "red",
    "unknown"
);


// =========================================
// Scenario 3
// =========================================

testScenario(
    "🔴 前方障礙物",
    "high",
    "green",
    "unknown"
);


// =========================================
// Scenario 4
// =========================================

testScenario(
    "🔴 障礙物 + 紅燈",
    "high",
    "red",
    "unknown"
);


console.log(
    "\n✅ Day 18 Safety Controller Test 完成"
);