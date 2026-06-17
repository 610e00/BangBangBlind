// gpsService.js
// 🎯 負責：GPS位置管理（目前先用模擬資料）

// 模擬目前 GPS 位置
const current_location = {
    latitude: 25.0330,
    longitude: 121.5654
};


/**
 * 取得目前 GPS 位置
 */
function getCurrentLocation() {

    return {
        status: "success",
        message: "gps location fetched",
        data: {
            latitude: current_location.latitude,
            longitude: current_location.longitude
        }
    };
}


/**
 * 模擬 GPS 移動
 */
function updateLocation(new_latitude, new_longitude) {

    current_location.latitude = new_latitude;
    current_location.longitude = new_longitude;

    return {
        status: "success",
        message: "gps updated",
        data: {
            latitude: current_location.latitude,
            longitude: current_location.longitude
        }
    };
}


module.exports = {
    getCurrentLocation,
    updateLocation
};