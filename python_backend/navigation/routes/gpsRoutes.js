//外部怎麼呼叫 GPS 功能(GPS API入口)
const express = require("express");

const router = express.Router();

const gpsService = require("../services/gpsService");
const axios = require("axios");
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

/**
 * 🌍 Google Maps 真實導航 API
 */
router.post("/navigation", async (req, res) => {

    try {

        const { latitude, longitude } = req.body;

        // 📍 起點（目前 GPS）
        const current = gpsService.getCurrentLocation();

        if (current.status !== "success") {
            return res.json({
                status: "error",
                message: "無法取得 GPS"
            });
        }

        const origin = `${current.data.latitude},${current.data.longitude}`;

        // 🎯 目的地
        const destination = `${latitude},${longitude}`;

        // 🌍 呼叫 Google Directions API
        const response = await axios.get(
            "https://maps.googleapis.com/maps/api/directions/json",
            {
                params: {
                    origin,
                    destination,
                    key: process.env.GOOGLE_MAPS_API_KEY
                }
            }
        );

        const data = response.data;
        console.log("GOOGLE RESPONSE:", JSON.stringify(data, null, 2));

        // ❌ 沒路線
        if (!data.routes || data.routes.length === 0) {
            return res.json({
                status: "error",
                message: "查無路線"
            });
        }

        // 📦 取第一條路線
        const route = data.routes[0];

        // 🧭 解析 steps
        const steps = route.legs[0].steps.map(step => step.html_instructions.replace(/<[^>]*>/g, ""));

        return res.json({
            status: "success",
            route: {
                distance: route.legs[0].distance.text,
                duration: route.legs[0].duration.text,
                steps
            }
        });

    } catch (error) {

        console.error(error);

        return res.status(500).json({
            status: "error",
            message: "導航失敗",
            error: error.message
        });
    }
});

module.exports = router;