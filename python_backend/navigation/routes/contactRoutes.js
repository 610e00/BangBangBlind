const express = require("express");
const router = express.Router();

const contactModel = require("../db/contactModel");

/*
=================================
新增聯絡人
POST /api/contact
=================================
*/
router.post("/contact", (req, res) => {

    try {

        const {
            user_id,
            contact_name,
            phone,
            relationship,
            priority
        } = req.body;

        const result = contactModel.createContact(
            user_id,
            contact_name,
            phone,
            relationship,
            priority
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
查詢指定使用者聯絡人
GET /api/contact/:user_id
=================================
*/
router.get("/contact/:user_id", (req, res) => {

    try {

        const userId = req.params.user_id;

        const contacts = contactModel.getContactsByUser(userId);

        res.json({
            success: true,
            data: contacts
        });

    } catch (error) {

        res.status(500).json({
            success: false,
            message: error.message
        });

    }

});

module.exports = router;