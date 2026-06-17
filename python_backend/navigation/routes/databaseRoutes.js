const express = require("express");
const router = express.Router();

// 載入 Model
const userModel = require("../db/userModel");

/*
=================================
新增使用者
POST /api/user
=================================
*/
router.post("/user", (req, res) => {

    try {

        const result =
            userModel.createUser();

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
查詢全部使用者
GET /api/user
=================================
*/
router.get("/user", (req, res) => {

    try {

        const users =
            userModel.getAllUsers();

        res.json({
            success: true,
            data: users
        });

    } catch (error) {

        res.status(500).json({
            success: false,
            message: error.message
        });

    }

});

module.exports = router;