
const express = require("express");
const router = express.Router();

const safetyService = require("../services/safetyService");
const decisionService = require("../services/decisionService");

/**
 * 📡 主流程測試 API
 * GPS → Safety → Decision → Emergency
 */
router.post("/decision/check", (req, res) => {
    
    const { latitude, longitude } = req.body;
    // 🧠 1. safety 判斷
    const safety = safetyService.getSafetyStatus(latitude, longitude);
    console.log("🔥 safety =", safety);
    const safetyData = safety.data || safety;
    console.log("🔥 safetyData =", safetyData);
    const alert_level = safetyData.alert_level;
    // 🧠 2. decision 判斷
    const result = decisionService.handleSafetyDecision(
        alert_level,
        { latitude, longitude }
    );
    console.log("🔥 result =", result);
    // 📤 回傳結果
    res.json({
        status: "success",
        data: {
            safety: safetyData,
            decision: result
        }
    });
});
module.exports = router;

