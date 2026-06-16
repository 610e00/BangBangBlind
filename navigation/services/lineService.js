const axios = require("axios");
process.env.NODE_TLS_REJECT_UNAUTHORIZED = "0";
/**
 * =====================================================
 * LINE PUSH SERVICE
 * =====================================================
 * 功能：主動推送訊息到指定 LINE 使用者
 * 使用場景：
 * - SOS 緊急通知
 * - 指定聯絡人通知
 * - 系統警報
 * =====================================================
 */

/**
 * 發送 LINE Push Message
 *
 * @param {string} userId - LINE 使用者 ID
 * @param {string} message - 要傳送的訊息
 */
async function sendPushMessage(userId, message) {

    try {

        // ================================
        // 1️⃣ 取得 LINE Channel Token
        // ================================
        const token = process.env.LINE_CHANNEL_ACCESS_TOKEN;

        if (!token) {
            throw new Error("LINE_CHANNEL_ACCESS_TOKEN 未設定");
        }

        // ================================
        // 2️⃣ 呼叫 LINE Push API
        // ================================
        await axios.post(
            "https://api.line.me/v2/bot/message/push",
            {
                // 收件人 LINE User ID
                to: userId,

                // 訊息內容（目前只用文字）
                messages: [
                    {
                        type: "text",
                        text: message
                    }
                ]
            },
            {
                headers: {
                    // LINE 驗證
                    Authorization: `Bearer ${token}`,

                    // JSON 格式
                    "Content-Type": "application/json"
                }
            }
        );

        // ================================
        // 3️⃣ 成功 log
        // ================================
        console.log("✅ LINE PUSH 成功");

    } catch (error) {

        // ================================
        // 4️⃣ 錯誤處理
        // ================================
        console.error("❌ LINE PUSH 失敗");

        console.error(error.response?.data || error.message);
    }
}

// ================================
// 匯出 function
// ================================
module.exports = {
    sendPushMessage
};