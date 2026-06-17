const express = require("express");
const router = express.Router();

// 🚨 引入 emergency 模組
const emergencyService = require("../services/emergencyService");


/**
 * 🚨 手動 SOS API
 * 之後前端 SOS 按鈕會打這支 API
 */
router.post("/emergency/sos", async(req, res) => {

    // 取得前端資料
    const { reason, latitude, longitude } = req.body;

    // 呼叫 SOS
    const result = await emergencyService.sendSOS(
        reason,
        {
            latitude,
            longitude
        }
    );

    // 回傳結果
    res.json(result);
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