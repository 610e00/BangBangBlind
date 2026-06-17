const express = require("express");
const router = express.Router();

const safetyService = require("../services/safetyService");


// 安全檢查 API
router.post("/safety/check", (req, res) => {

    const { latitude, longitude } = req.body;

    const result = safetyService.getSafetyStatus(latitude, longitude);

    res.json(result);
});

module.exports = router;