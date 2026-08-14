// =========================================
// Safety Perception Modules
// 模擬 AI 眼鏡的感知資料
// 未來可以直接替換成真正的 AI 模型輸出
// =========================================

let perceptionState = {
    obstacle: "low",
    trafficLight: "green",
    face: "unknown"
};

function getObstacleData() {
    return perceptionState.obstacle;
}

function getTrafficLight() {
    return perceptionState.trafficLight;
}

function getFaceStatus() {
    return perceptionState.face;
}

function setObstacleData(obstacleLevel) {
    if (obstacleLevel !== "low" && obstacleLevel !== "high") {
        console.log(
            "⚠️ Invalid obstacle level:",
            obstacleLevel
        );
        return;
    }

    perceptionState.obstacle = obstacleLevel;
    console.log("🚧 Obstacle:", obstacleLevel);
}

function setTrafficLight(trafficLightStatus) {
    if (
        trafficLightStatus !== "red" &&
        trafficLightStatus !== "green"
    ) {
        console.log(
            "⚠️ Invalid traffic light status:",
            trafficLightStatus
        );
        return;
    }

    perceptionState.trafficLight = trafficLightStatus;
    console.log("🚦 Traffic Light:", trafficLightStatus);
}

function setFaceStatus(faceStatus) {
    if (faceStatus !== "known" && faceStatus !== "unknown") {
        console.log(
            "⚠️ Invalid face status:",
            faceStatus
        );
        return;
    }

    perceptionState.face = faceStatus;
    console.log("📷 Face:", faceStatus);
}

function getPerceptionState() {
    return {
        ...perceptionState
    };
}

module.exports = {
    getObstacleData,
    getTrafficLight,
    getFaceStatus,
    setObstacleData,
    setTrafficLight,
    setFaceStatus,
    getPerceptionState
};
