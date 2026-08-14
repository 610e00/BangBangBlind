// =========================================
// Face Detection 模擬模組
// =========================================

let detectedFace = {
    detected: true,
    name: "小明",
    confidence: 0.92
};


// =========================================
// 取得目前人臉
// =========================================

function detectFace() {

    return detectedFace;
}


// =========================================
// 模擬人臉消失
// =========================================

function clearFace() {

    detectedFace = {
        detected: false,
        name: null,
        confidence: 0
    };

    console.log("📷 Face disappeared");
}


// =========================================
// 模擬不同的人出現
// =========================================

function setFace(name, confidence) {

    detectedFace = {
        detected: true,
        name,
        confidence
    };

    console.log(
        `📷 Face detected → ${name} (${confidence})`
    );
}


// =========================================
// Export
// =========================================

module.exports = {
    detectFace,
    clearFace,
    setFace
};