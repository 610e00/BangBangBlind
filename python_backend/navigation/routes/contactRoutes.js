const express = require("express");
const router = express.Router();

const contactModel = require("../db/contactModel");
const { sendAlertEmail } = require("../services/emailService");

/**
 * 2026-10-29：新增／換信箱時寄一封「確認信」給聯絡人。
 * 目的：①讓使用者馬上知道信箱填對沒（收不到就是填錯）；
 *       ②萬一填到別人的信箱，對方看到的是說明而不是驚嚇。
 * 失敗不影響儲存，只回報 confirmation_sent=false。
 */
async function sendConfirmationEmail(email, contactName, userName) {
    if (!email) return false;
    const who = userName && String(userName).trim() ? String(userName).trim() : "一位使用者";
    const text =
`${contactName} 您好，

${who} 剛剛在「幫幫盲」App 把您的 Email 設為「緊急聯絡人」。

這只是一封確認信，目前沒有任何緊急狀況，您不需要回覆或做任何事。

之後如果 ${who} 按下緊急求助，您會在這個信箱收到通知，內容包含對方的位置連結。

如果您不認識這位使用者，或不希望收到這類通知，請直接忽略這封信，並請對方把您的信箱移除。
`;
    try {
        return await sendAlertEmail(
            "【幫幫盲】您被設為緊急聯絡人（確認信，並非緊急狀況）",
            text,
            email
        );
    } catch (e) {
        console.error("確認信寄送失敗：", e.message);
        return false;
    }
}

// 2026-10-29：簡單檢查 Email 格式（空字串代表沒填，允許）
function isValidEmail(email) {
    return !email || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

/*
=================================
只寄確認信（不存任何資料）
POST /api/contact/confirm
2026-10-31：聯絡人改存手機，手機存好後呼叫這支寄確認信
=================================
*/
router.post("/contact/confirm", async (req, res) => {
    try {
        const { email, contact_name, user_name } = req.body || {};
        if (!email || !isValidEmail(email)) {
            return res.status(400).json({ status: "error", message: "Email 格式不正確", data: null });
        }
        const sent = await sendConfirmationEmail(email, contact_name || "您", user_name);
        res.json({ status: "success", message: "已處理", data: { confirmation_sent: sent } });
    } catch (error) {
        res.status(500).json({ status: "error", message: error.message, data: null });
    }
});

/*
=================================
新增聯絡人
POST /api/contact
=================================
*/
router.post("/contact", async (req, res) => {

    try {

        const {
            user_id,
            contact_name,
            phone,
            relationship,
            priority,
            email,
            notify_on_sos,
            user_name
        } = req.body;

        if (!isValidEmail(email)) {
            return res.status(400).json({
                status: "error",
                message: "Email 格式不正確",
                data: null
            });
        }

        const result = contactModel.createContact(
            user_id,
            contact_name,
            phone || "",
            relationship,
            priority,
            email,
            notify_on_sos
        );

        // 測試（確認）信規則：只有「新增聯絡人且有填 Email」才寄
        const confirmationSent = email
            ? await sendConfirmationEmail(email, contact_name, user_name)
            : false;

        res.json({
            status: "success",
            message: "聯絡人新增成功",
            data: { ...result, confirmation_sent: confirmationSent }
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
查詢指定使用者聯絡人
GET /api/contact/:user_id
=================================
*/
router.get("/contact/:user_id", (req, res) => {

    try {

        const userId = req.params.user_id;

        const contacts = contactModel.getContactsByUser(userId);

        res.json({
            status: "success",
            message: "查詢聯絡人成功",
            data: contacts
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
修改聯絡人（2026-10-28 新增）
PUT /api/contact/:contact_id
=================================
*/
router.put("/contact/:contact_id", async (req, res) => {

    try {

        const { contact_name, phone, relationship, priority, email, notify_on_sos, user_name } = req.body;

        if (!isValidEmail(email)) {
            return res.status(400).json({ status: "error", message: "Email 格式不正確", data: null });
        }

        // 2026-10-29：電話改為選填（展示手機沒有 SIM，通知走 Email），只有姓名必填
        if (!contact_name) {
            return res.status(400).json({
                status: "error",
                message: "姓名不可為空",
                data: null
            });
        }

        const before = contactModel.getContactById(req.params.contact_id);

        const result = contactModel.updateContact(
            req.params.contact_id,
            contact_name,
            phone || "",
            relationship || "",
            priority || 1,
            email || "",
            notify_on_sos
        );

        if (result.changes === 0) {
            return res.status(404).json({
                status: "error",
                message: "找不到這位聯絡人",
                data: null
            });
        }

        // 測試（確認）信規則：只有「舊聯絡人的 Email 被改成不同的信箱」才寄；
        // 其他修改（改名字、關係、開關）都不寄。
        const emailChanged = !!email && (!before || (before.email || "") !== email);
        const confirmationSent = emailChanged
            ? await sendConfirmationEmail(email, contact_name, user_name)
            : false;

        res.json({
            status: "success",
            message: "聯絡人已修改",
            data: { confirmation_sent: confirmationSent }
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
刪除聯絡人（2026-10-28 新增）
DELETE /api/contact/:contact_id
=================================
*/
router.delete("/contact/:contact_id", (req, res) => {

    try {

        const result = contactModel.deleteContact(req.params.contact_id);

        if (result.changes === 0) {
            return res.status(404).json({
                status: "error",
                message: "找不到這位聯絡人",
                data: null
            });
        }

        res.json({ status: "success", message: "聯絡人已刪除", data: null });

    } catch (error) {

        res.status(500).json({
            status: "error",
            message: error.message,
            data: null
        });

    }

});

module.exports = router;