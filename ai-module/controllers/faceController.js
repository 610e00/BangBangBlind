const eventBus = require("../events/eventBus");

// =========================================
// Face Recognition 設定
// =========================================

const CONFIDENCE_THRESHOLD = 0.75;
const REQUIRED_DURATION = 3000;
const COOLDOWN = 10000;


// =========================================
// Face State
// =========================================

let currentFace = null;
let firstSeenTime = null;

let lastAnnouncedName = null;
let lastAnnouncedTime = 0;


// =========================================
// 處理 Face Recognition
// =========================================

async function handleFace(faceData) {

    if (!faceData || !faceData.detected) {

        if (currentFace !== null) {

            console.log(
                `👤 ${currentFace} 離開視野`
            );
        }

        resetFaceTracking();

        return null;
    }


    const {
        name,
        confidence
    } = faceData;


    // =========================================
    // 1. 資料檢查
    // =========================================

    if (!name || typeof confidence !== "number") {
        return null;
    }


    // =========================================
    // 2. Confidence
    // =========================================

    if (confidence < CONFIDENCE_THRESHOLD) {

        console.log(
            `⚠️ Face confidence 太低: ${confidence}`
        );

        resetFaceTracking();

        return null;
    }


    const now = Date.now();


    // =========================================
    // 3. 新的人
    // =========================================

    if (currentFace !== name) {

        currentFace = name;
        firstSeenTime = now;

        console.log(
            `👤 偵測到熟人: ${name}`
        );

        return null;
    }


    // =========================================
    // 4. 持續時間
    // =========================================

    const duration =
        now - firstSeenTime;

    console.log(
        `👤 ${name} 持續出現: ${duration} ms`
    );


    if (duration < REQUIRED_DURATION) {
        return null;
    }


    // =========================================
    // 5. Cooldown
    // =========================================

    if (
        lastAnnouncedName === name &&
        now - lastAnnouncedTime < COOLDOWN
    ) {

        return null;
    }


    // =========================================
    // 6. 建立播報資料
    // =========================================

    const result = {

        type: "face_announcement",

        name: name,

        confidence: confidence,

        message: `前方是${name}`,

        duration: duration
    };


    lastAnnouncedName = name;
    lastAnnouncedTime = now;


    // =========================================
    // 7. EventBus
    // =========================================

    eventBus.emit(
        "faceAnnouncement",
        result
    );


    console.log(
        "🔊 Face Announcement:",
        result
    );


    // =========================================
    // 8. Return
    // =========================================

    return result;
}


// =========================================
// Reset
// =========================================

function resetFaceTracking() {

    currentFace = null;
    firstSeenTime = null;
}


// =========================================
// Export
// =========================================

module.exports = {

    handleFace,

    resetFaceTracking
};
