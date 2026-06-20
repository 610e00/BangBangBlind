const express = require("express");
const router = express.Router();

const navigationModel = require("../db/navigationModel");

/*
=================================
新增導航紀錄
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
            success: true,
            data: result
        });

    } catch (error) {

        res.status(500).json({
            success: false,
            message: error.message
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
            success: true,
            data: records
        });

    } catch (error) {

        res.status(500).json({
            success: false,
            message: error.message
        });

    }

});

/**
 * =================================
 * 導航開始
 * POST /navigation/start
 * =================================
 */
router.post("/navigation/start", (req, res) => {

    // 從前端取得是否已抵達
    const { arrived } = req.body;

    // 已抵達目的地
    if (arrived === true) {

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

    // 導航中
    res.json({
        status: "success",
        message: "導航中",
        data: {
            instruction: "直走50公尺後左轉",
            distance_to_next_cm: 5000,
            arrived: false
        }
    });

});

module.exports = router;