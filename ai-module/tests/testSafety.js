const {
    getObstacleData,
    getTrafficLight,
    getFaceStatus,

    setObstacleData,
    setTrafficLight,
    setFaceStatus,

    getPerceptionState
} = require("../modules");


console.log("\n🧪 Day 18 Safety Perception Test\n");


// =========================================
// 初始狀態
// =========================================

console.log("1️⃣ 初始感知");

console.log(
    getPerceptionState()
);


// =========================================
// 障礙物
// =========================================

console.log("\n2️⃣ 障礙物變化");

setObstacleData("high");

console.log(
    "Obstacle:",
    getObstacleData()
);


// =========================================
// 紅綠燈
// =========================================

console.log("\n3️⃣ 紅綠燈變化");

setTrafficLight("red");

console.log(
    "Traffic Light:",
    getTrafficLight()
);


// =========================================
// 人臉
// =========================================

console.log("\n4️⃣ 人臉變化");

setFaceStatus("known");

console.log(
    "Face:",
    getFaceStatus()
);


// =========================================
// 最終狀態
// =========================================

console.log("\n5️⃣ 最終感知狀態");

console.log(
    getPerceptionState()
);


console.log(
    "\n✅ Day 18 Safety Perception Test 完成"
);