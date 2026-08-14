function evaluateRisk(obstacle, light, face) {

    let level = "safe";
    let message = "目前路況正常，可以前進";

    // 🚧 高危險（直接停）
    if (obstacle === "high" && light === "red") {
        level = "danger";
        message = "前方障礙物與紅燈，請立即停止";
    }

    // 🚦 紅燈提醒
    else if (light === "red") {
        level = "caution";
        message = "前方紅燈，請減速並注意環境";
    }

    // 🚧 高障礙
    else if (obstacle === "high") {
        level = "caution";
        message = "前方障礙物密集，請小心通行";
    }

    // 👤 不明人物
    else if (face === "unknown") {
        level = "attention";
        message = "偵測到未知人物，請注意周圍安全";
    }

    return { level, message };
}

module.exports = { evaluateRisk };