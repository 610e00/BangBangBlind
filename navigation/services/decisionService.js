// 🚨 引入 emergency 模組
const emergencyService = require("./emergencyService");

// 🧠 記錄最近一次警告時間（避免一直問）
let lastWarningTime = 0;

// 🧠 監控降級時間（ms）
const COOLDOWN_TIME = 30000; // 30秒內不重複詢問

/**
 * 🧠 解除警戒
 */
function resetSafety() {
    return {
        action: "continue_navigation",
        state: "NORMAL",
        message: "back to normal navigation"
    };
}

/**
 * 🟡 降級監控模式
 * 👉 不再頻繁詢問，但仍持續偵測
 */
function enterMonitoringMode() {
    return {
        action: "monitoring_mode",
        state: "MONITORING",
        message: "low frequency safety monitoring"
    };
}

/**
 * 🚨 核心決策邏輯
 */
function handleSafetyDecision(alert_level, gpsData, userResponse) {

    // 🟢 正常 → 不做事
    if (alert_level === "normal") {
        return {
            action: "continue_navigation",
            state: "NORMAL"
        };
    }

    // 🟠 warning → 要先問使用者
    if (alert_level === "warning") {
        // 🧠 冷卻機制：避免一直問
        const now = Date.now();
   
        const canAskUser = (now - lastWarningTime) > COOLDOWN_TIME;
       // 🧠 尚未詢問（AI層還沒回來）
       if (!userResponse) {
        if (!canAskUser) {
            return enterMonitoringMode();
        }

        lastWarningTime = now;
        return {
            action: "ask_user",
            state: "WARNING",
            message: "Do you need assistance?"
        };
    }
        // 🟢 使用者已回應 & 不需要幫助 → 解除警戒
        if (userResponse.responded && userResponse.needHelp === false) {
            return enterMonitoringMode();
        }

        // 🔴 使用者已回應 & 需要幫助 → SOS
        if (userResponse.responded && userResponse.needHelp === true) {
            return emergencyService.sendSOS(
                "user_request_help",
                gpsData
            );
        }

        // 🔴 未回應 → SOS
        return emergencyService.sendSOS(
            "no_response",
            gpsData
        );
    }

    // 🔴 高風險狀態（danger）
    if (alert_level === "danger") {

        return emergencyService.sendSOS(
            "auto_danger",
            gpsData
        );
    }

    // ❓ 非預期輸入
    return {
        action: "unknown",
        state: "ERROR",
        reason: "invalid_alert_level"
    };
}

/**
 * 🎯 決策引擎
 * 根據 safety 結果決定下一步
 */

function evaluate(safetyResult) {

    const status = safetyResult.status;

    if (status === "danger") {
        return {
            status: "danger",
            action: "sendSOS"
        };
    }

    if (status === "warning") {
        return {
            status: "warning",
            action: "askUser"
        };
    }

    return {
        status: "normal",
        action: "continue"
    };
}

/**
 * 📦 匯出
 */
module.exports = {
    handleSafetyDecision,
    evaluate
    
};