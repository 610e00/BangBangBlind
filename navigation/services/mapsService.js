// 🎯 負責：處理導航路線（模擬版 + 未來可接 Google Maps API）

/**
 * 取得導航路線
 * @param {string} origin 起點
 * @param {string} destination 終點
 */

function getRoute(origin, destination) {

    // 🔥 模擬 Google Maps 回傳資料
    const mockRoute = {
        origin: origin,
        destination: destination,

        distance_km: 5.2,        // 距離（km）
        duration_min: 18,        // 預估時間（分鐘）

        steps: [
            "向前直走 200 公尺",
            "右轉進入主幹道",
            "直行 1.2 公里",
            "左轉進入目的地"
        ]
    };

    return {
        status: "success",
        message: "route generated",
        data: mockRoute
    };
}

module.exports = {
    getRoute
};