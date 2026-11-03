const express = require("express");
const router = express.Router();

// 🚨 引入 emergency 模組
const emergencyService = require("../services/emergencyService");


/**
 * 🚨 手動 SOS API
 * 對應 API_SPEC.md：POST /emergency/trigger
 * （原路徑 /emergency/sos，整合時依規格文件改名）
 */
router.post("/emergency/trigger", async (req, res) => {

    // 取得前端資料
    const { reason, latitude, longitude, user_name, target, contacts } = req.body;

    try {

        // 呼叫 SOS
        const result = await emergencyService.sendSOS(
            reason,
            {
                latitude,
                longitude
            },
            target || null,
            "system",
            user_name || "",
            // 2026-10-31：聯絡人由手機帶來（雲端後端不存聯絡人）；沒帶就用後端資料庫（舊做法）
            Array.isArray(contacts) ? contacts : null
        );

        // 回傳結果：對應 API_SPEC.md 規格欄位
        res.json({
            status: "success",
            message: result.notified === true ? "緊急求助已送出" : "已記錄求助，但通知尚未送出",
            data: {
                // 2026-10-28：改成「通知真的有送出」才是 true（原本只要流程跑完就 true）
                sms_sent: result.notified === true,
                location_lat: latitude,
                location_lng: longitude,
                contact_name: result.target_name,
                // 2026-10-29：逐人回報，App 可念出「已通知A、B；沒能通知C」
                notified_names: result.notified_names,
                failed_names: result.failed_names,
                // null＝沒指定對象；false＝說了名字但找不到人（已改通知全部）
                target_matched: result.target_matched
            }
        });

    } catch (error) {

        res.status(500).json({
            status: "error",
            message: error.message,
            data: null
        });
    }
});


// 匯出 routes
module.exports = router;

/**
 * 🚨 SOS 測試 API
 *
 * 瀏覽器直接開：
 * http://localhost:3000/api/sos-test
 */
router.get("/sos-test", async (req, res) => {

    try {

        const result = await emergencyService.sendSOS(
            "測試SOS",
            {
                latitude: 24.998,
                longitude: 121.308
            }
        );

        res.json(result);

    } catch (error) {

        console.error(error);

        res.status(500).json({
            error: error.message
        });
    }
});
