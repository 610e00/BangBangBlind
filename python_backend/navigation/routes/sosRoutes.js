const express = require("express");
const router = express.Router();

const sosModel = require("../db/sosModel");

/*
=================================
新增安全事件
POST /api/sos
=================================
*/
router.post("/sos", (req, res) => {

    try {

        const {
            user_id,
            event_type,
            latitude,
            longitude,
            action_result
        } = req.body;

        const result = sosModel.createEvent(
            user_id,
            event_type,
            latitude,
            longitude,
            action_result
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
查詢使用者安全事件
GET /api/sos/:user_id
=================================
*/
router.get("/sos/:user_id", (req, res) => {

    try {

        const userId = req.params.user_id;

        const events =
            sosModel.getEventsByUser(userId);

        res.json({
            success: true,
            data: events
        });

    } catch (error) {

        res.status(500).json({
            success: false,
            message: error.message
        });

    }

});

module.exports = router;