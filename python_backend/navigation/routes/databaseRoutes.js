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
            status: "success",
            message: "使用者建立成功",
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
查詢全部使用者
GET /api/user
=================================
*/
router.get("/user", (req, res) => {

    try {

        const users =
            userModel.getAllUsers();

        res.json({
            status: "success",
            message: "查詢使用者成功",
            data: users
        });

    } catch (error) {

        res.status(500).json({
            status: "error",
            message: error.message,
            data: null
        });

    }

});

module.exports = router;