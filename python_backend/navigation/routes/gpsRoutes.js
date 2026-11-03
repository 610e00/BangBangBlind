//外部怎麼呼叫 GPS 功能(GPS API入口)
const express = require("express");

const router = express.Router();

const gpsService = require("../services/gpsService");
const flowController = require("../services/flowController");
// 取得目前 GPS
router.get("/gps", (req, res) => {

    const result = gpsService.getCurrentLocation();

    return res.json(result);
});

//更新GPS位置
router.post("/gps/update", async (req, res) => {

    const { latitude, longitude } = req.body;

    // 🚨 交給 Flow Controller 處理整個系統流程
    const result = await flowController.processLocationUpdate(
        latitude,
        longitude
    );

    return res.json(result);
});

// ⚠️ 整合負責人備註（2026-10-03）：
// 這裡原本有一支 POST /navigation，跟 navigationRoutes.js 的路徑完全相同，
// 而且 server.js 掛載順序比 navigationRoutes.js 早，造成：
// 1. navigationRoutes.js 裡同名的那支變成永遠不會被執行的死碼
// 2. 這支本身雖然會執行，但沒有 GOOGLE_MAPS_API_KEY 金鑰時一定失敗，
//    而且回傳格式（distance/duration/steps）跟 API_SPEC.md 規定的
//    /navigation/start 格式（instruction/distance_to_next_cm/arrived）不一樣，
//    App 端從來沒有真正呼叫過這支
// 已把這裡真正會動的 Google Maps 呼叫邏輯，整合進
// navigationRoutes.js 的 POST /navigation/start（也是 API_SPEC.md 指定的正式入口），
// 現在只保留這支檔案原本就有、真正在用的 /gps、/gps/update。

module.exports = router;