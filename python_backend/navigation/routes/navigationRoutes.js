const express = require("express");
const router = express.Router();

const navigationModel = require("../db/navigationModel");
const navigationSessionService = require("../services/navigationSessionService");

/*
=================================
新增導航紀錄（存進資料庫，給後台記錄/分析用，App 不會直接呼叫這支）
POST /api/navigation
=================================
*/
router.post("/navigation", (req, res) => {

    try {

        const {
            user_id,
            longitude,
            latitude,
            destination_name
        } = req.body;

        const result = navigationModel.createRecord(
            user_id,
            longitude,
            latitude,
            destination_name
        );

        res.json({
            status: "success",
            message: "導航紀錄新增成功",
            data: result
        });

    } catch (error) {

        res.status(500).json({
            status: "error",
            message: error.message,
            data: null
        });

    }

});

/*
=================================
查詢使用者導航紀錄
GET /api/navigation/:user_id
=================================
*/
router.get("/navigation/:user_id", (req, res) => {

    try {

        const userId = req.params.user_id;

        const records =
            navigationModel.getRecordsByUser(userId);

        res.json({
            status: "success",
            message: "查詢導航紀錄成功",
            data: records
        });

    } catch (error) {

        res.status(500).json({
            status: "error",
            message: error.message,
            data: null
        });

    }

});

/**
 * =================================
 * 導航開始／更新進度
 * POST /navigation/start
 * 對應 API_SPEC.md：路徑 /navigation/start
 *
 * 真正呼叫 Google Maps Directions API 算路線（整合負責人實作）。
 *
 * App 端用法：
 * 1. 第一次呼叫：帶 destination + latitude + longitude（目前位置）
 *    → 後端會呼叫 Google Maps 算出完整路線，回傳第一句指示
 * 2. 之後每次使用者移動，帶最新的 latitude + longitude 再呼叫一次
 *    （destination 可以不用再帶）→ 後端判斷要不要換下一句指示、有沒有抵達
 * 3. 使用者自己按「我已抵達」時，帶 arrived: true → 強制結束導航
 * =================================
 */
/*
 * =================================
 * POST /navigation/candidates   （2026-10-28 新增，整合負責人）
 * 開始導航前先呼叫：判斷使用者輸入的地點有沒有疑慮。
 * body: { query, latitude, longitude, specific? }
 * 回傳 data: { mode: "resolved"|"choose"|"none", candidates:[{place_id,name,address,distance_text,distance_m}] }
 * =================================
 */
router.post("/navigation/candidates", async (req, res) => {
    try {
        const { query, latitude, longitude, specific } = req.body;
        if (!query || typeof latitude !== "number" || typeof longitude !== "number") {
            return res.status(400).json({
                status: "error",
                message: "缺少 query / latitude / longitude",
                data: null
            });
        }
        const r = await navigationSessionService.findCandidates(
            String(query).trim(), latitude, longitude, specific === true
        );
        res.json({
            status: "success",
            message: "查詢完成",
            data: {
                mode: r.mode,
                candidates: r.candidates.map((c) => ({
                    place_id: c.placeId,
                    name: c.name,
                    address: c.address,
                    distance_m: c.distanceMeters,
                    distance_text: c.distanceText
                }))
            }
        });
    } catch (error) {
        res.status(500).json({ status: "error", message: error.message, data: null });
    }
});

router.post("/navigation/start", async (req, res) => {

    const { destination, latitude, longitude, arrived, place_id, destination_name, destination_address } = req.body;

    try {

        // 使用者手動回報「我已抵達」，強制結束導航（當 GPS 判斷不準時的備用保障）
        if (arrived === true) {

            navigationSessionService.clearSession();

            return res.json({
                status: "success",
                message: "已抵達目的地",
                data: {
                    instruction: "您已抵達目的地",
                    distance_to_next_cm: 0,
                    arrived: true
                }
            });
        }

        let stepResult;

        // 2026-10-03 修正：原本這裡是用「後端目前有沒有進行中的 session」來判斷
        // 要開新路線還是更新進度。但 App 端按「停止導航」時，只是停掉前端的
        // 計時器，並沒有通知後端清掉 session——所以如果使用者停止導航後，
        // 馬上搜尋下一個目的地，後端仍然以為「現在在導航中」，就把這次帶來的
        // 新 destination 整個忽略、繼續用舊路線的進度回應，導致畫面上看到的
        // 是錯誤的路線、也不會有目的地名稱/預計到達時間（因為那兩個欄位只有
        // 「真正開始新 session」那一次才會回傳）。
        //
        // 改成：只要這次請求「有帶 destination」，就一律視為使用者要開始一段
        // 新的導航，直接重新呼叫 startSession（會覆蓋掉舊的 session），
        // 不再依賴「後端是否記得自己在導航中」這個容易對不齊的狀態。
        if (destination) {

            if (typeof latitude !== "number" || typeof longitude !== "number") {

                return res.status(400).json({
                    status: "error",
                    message: "缺少 latitude/longitude（目前位置），無法開始導航",
                    data: null
                });
            }

            stepResult = await navigationSessionService.startSession(
                destination,
                latitude,
                longitude,
                place_id
                    ? { placeId: place_id, name: destination_name || destination, address: destination_address || "" }
                    : null
            );

        } else {

            // 沒有帶 destination：代表這次單純是「回報最新位置、更新進度」，
            // 必須是已經有進行中的 session 才有意義
            if (!navigationSessionService.hasActiveSession()) {

                return res.status(400).json({
                    status: "error",
                    message: "尚未開始導航，且本次請求沒有帶 destination（目的地）",
                    data: null
                });
            }

            if (typeof latitude !== "number" || typeof longitude !== "number") {

                return res.status(400).json({
                    status: "error",
                    message: "缺少 latitude/longitude，無法更新導航進度",
                    data: null
                });
            }

            stepResult = navigationSessionService.updatePosition(
                latitude,
                longitude
            );
        }

        // instruction / distance_to_next_cm / arrived 是 API_SPEC.md 原本就定義好的欄位，
        // 下面 eta_seconds / eta_text / destination_name / destination_address 是新增欄位
        // （只有第一次建立導航 session 時才有意義，之後每次回報位置不會重複帶）
        const responseData = {
            instruction: stepResult.instruction,
            distance_to_next_cm: stepResult.distanceToNextCm,
            arrived: stepResult.arrived
        };

        // 2026-10-28：下一個轉折點座標（App 做起步校正用）；已抵達時沒有
        if (typeof stepResult.nextLatitude === "number") {
            responseData.next_lat = stepResult.nextLatitude;
            responseData.next_lng = stepResult.nextLongitude;
        }

        if (stepResult.isNewSession) {
            responseData.eta_seconds = stepResult.etaSeconds;
            responseData.eta_text = stepResult.etaText;
            responseData.destination_name = stepResult.destinationName;
            responseData.destination_address = stepResult.destinationAddress;
            responseData.distance_text = stepResult.distanceText;
        }

        return res.json({
            status: "success",
            message: stepResult.arrived ? "已抵達目的地" : "導航中",
            data: responseData
        });

    } catch (error) {

        // 任何失敗（例如 Google Maps 查無路線、金鑰錯誤）都清掉 session，
        // 避免卡在一個壞掉的導航狀態裡
        navigationSessionService.clearSession();

        return res.status(500).json({
            status: "error",
            message: `導航失敗：${error.message}`,
            data: null
        });
    }

});

module.exports = router;
