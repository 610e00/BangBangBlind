// 🎯 負責：處理導航路線（模擬版 + 未來可接 Google Maps API）
//
// ⚠️ 2026-10-03 整合負責人備註：已棄用，沒有任何地方呼叫這支了。
// 真正接 Google Maps 的邏輯已經實作在 services/navigationSessionService.js，
// routes/navigationRoutes.js 的 /navigation/start 和
// routes/aiGatewayRoutes.js 的 intent=navigation 現在都改呼叫那支。
// 保留這個檔案只是為了留個對照，之後確認沒問題可以直接刪除。

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