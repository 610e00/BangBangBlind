// safetyService.js
// 🎯 負責：安全判斷（偏離 / 停滯 / 狀態）

// 模擬上一個位置（實務上會來自 GPS）
let last_location = {
    latitude: 25.0330,
    longitude: 121.5654
};

// 模擬時間
let last_move_time = Date.now();


/**
 * 判斷是否偏離（簡化版）
 */
function checkDeviation(current_lat, current_lng) {

    const lat_diff = Math.abs(current_lat - last_location.latitude);
    const lng_diff = Math.abs(current_lng - last_location.longitude);

    const is_deviated = (lat_diff + lng_diff) > 0.01;

    return {
        status: "success",
        message: "deviation checked",
        data: {
            has_obstacle: false,
            is_deviated: is_deviated
        }
    };
}


/**
 * 判斷是否停滯
 */
function checkStuck(current_lat, current_lng) {

    const moved = (
        current_lat !== last_location.latitude ||
        current_lng !== last_location.longitude
    );

    if (moved) {
        last_move_time = Date.now();
        last_location = { latitude: current_lat, longitude: current_lng };
    }

    const now = Date.now();
    const stuck_time_sec = (now - last_move_time) / 1000;

    const is_stuck = stuck_time_sec > 30;

    return {
        status: "success",
        message: "stuck status checked",
        data: {
            is_stuck: is_stuck,
            stuck_time_sec: stuck_time_sec
        }
    };
}


/**
 * 綜合安全判斷
 */
function getSafetyStatus(current_lat, current_lng) {

    const deviation = checkDeviation(current_lat, current_lng);
    const stuck = checkStuck(current_lat, current_lng);

    const is_deviated = deviation.data.is_deviated;
    const is_stuck = stuck.data.is_stuck;

    // 🧠 1. 判斷風險等級
    let alert_level = "normal";

    if (is_deviated && is_stuck) {
        alert_level = "danger";
    } 
    else if (is_deviated || is_stuck) {
        alert_level = "warning";
    }

    // 🧠 2. 決定下一步行為
    let suggestion = "continue_navigation";

    if (alert_level === "warning") {
        suggestion = "ask_user";
    }

    if (alert_level === "danger") {
        suggestion = "trigger_emergency";
    }

    return {
        status: "success",
        message: "safety status checked",
        data: {
            is_deviated,
            is_stuck,
            stuck_time_sec: stuck.data.stuck_time_sec,
            alert_level,
            suggestion
        }
    };
}
/**
 * 🚨 統一風險判斷
 */
function checkRisk(gpsData) {

    const lat = gpsData.latitude;
    const lng = gpsData.longitude;

    const deviation = checkDeviation(lat, lng);
    const stuck = checkStuck(lat, lng);

    let status = "normal";

    if (deviation.data.is_deviated && stuck.data.is_stuck) {
        status = "danger";
    } else if (deviation.data.is_deviated || stuck.data.is_stuck) {
        status = "warning";
    }

    return {
        status,
        deviation: deviation.data,
        stuck: stuck.data
    };
}

module.exports = {
    checkRisk,
    checkDeviation,
    checkStuck,
    getSafetyStatus
};