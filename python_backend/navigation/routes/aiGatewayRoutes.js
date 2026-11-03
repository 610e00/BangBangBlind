//# ⭐ AI助理入口（核心）
// 建立 express router
const express = require("express");

// 建立 router
const router = express.Router();

// 2026-10-03 整合負責人修改：
// navigation intent 原本呼叫 mapsService.js 的假資料（固定路線），
// 跟 App 直接呼叫的 /navigation/start 是兩套不同邏輯、兩種結果。
// 改成呼叫同一套 navigationSessionService（真正接 Google Maps），
// 讓「App 文字輸入目的地」和「語音說『帶我去＿＿』」這兩個入口
// 最後都走同一套真實導航邏輯，不會出現兩套結果不一致的情況。
const navigationSessionService = require("../services/navigationSessionService");


// =========================
// AI Gateway API
// =========================

// AI 所有指令都會進這裡
router.post("/ai", async (req, res) => {

    // 從 AI JSON 取得資料
    const { intent, destination, latitude, longitude } = req.body;


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
    // 🧠 分流：navigation intent
    // 真正呼叫 Google Maps（跟 /navigation/start 共用同一套邏輯/同一個 session）
    // =========================

    if (intent === "navigation") {

        if (typeof latitude !== "number" || typeof longitude !== "number") {

            return res.json({
                status: "error",
                message: "缺少 latitude/longitude，無法開始導航",
                data: null
            });
        }

        try {

            const stepResult = navigationSessionService.hasActiveSession()
                ? navigationSessionService.updatePosition(latitude, longitude)
                : await navigationSessionService.startSession(destination, latitude, longitude);

            return res.json({
                status: "success",
                message: stepResult.arrived ? "已抵達目的地" : "導航中",
                data: {
                    instruction: stepResult.instruction,
                    distance_to_next_cm: stepResult.distanceToNextCm,
                    arrived: stepResult.arrived
                }
            });

        } catch (error) {

            navigationSessionService.clearSession();

            return res.status(500).json({
                status: "error",
                message: `導航失敗：${error.message}`,
                data: null
            });
        }
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