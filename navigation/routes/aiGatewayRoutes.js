//# ⭐ AI助理入口（核心）
//加入 mapsService
const mapsService = require("../services/mapsService");
// 建立 express router
const express = require("express");

// 建立 router
const router = express.Router();


// =========================
// AI Gateway API
// =========================

// AI 所有指令都會進這裡
router.post("/ai", (req, res) => {

    // 從 AI JSON 取得資料
    const { intent, destination } = req.body;


    // =========================
    // 基本驗證
    // =========================

    // intent 不存在
    if (!intent) {

        return res.json({
            status: "error",
            message: "intent is required",
            data: {}
        });
    }


    // =========================
    // 🧠 分流（先做最簡版）navigation intent
    // =========================

    if (intent === "navigation") {

        const origin = "目前位置"; // 之後會接 GPS
        const destination = req.body.destination;
    
        const result = mapsService.getRoute(origin, destination);
    
        return res.json(result);
    }


    // =========================
    // emergency intent
    // =========================

    if (intent === "emergency") {

        return res.json({
            status: "success",
            message: "emergency triggered",
            data: {}
        });
    }


    // =========================
    // safety_check intent
    // =========================

    if (intent === "safety_check") {

        return res.json({
            status: "success",
            message: "safety check running",
            data: {}
        });
    }


    // =========================
    // unknown intent
    // =========================

    return res.json({
        status: "error",
        message: "unknown intent",
        data: {}
    });

});


// 匯出 router
module.exports = router;