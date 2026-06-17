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

module.exports = router;