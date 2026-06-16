const axios = require("axios");

// 🔑 放你的 Channel Access Token
const TOKEN = "你的token貼這裡";

/**
 * 📩 發送 LINE 訊息
 */
async function sendNotification(contact, message) {

    const text =
`🚨 SOS ALERT
👤 ${contact.name}
📞 ${contact.phone}
📍 ${message.reason || "SOS"}
⏰ ${new Date().toLocaleString()}`;

    try {
        const res = await axios.post(
            "https://api.line.me/v2/bot/message/push",
            {
                to: "YOUR_USER_ID",
                messages: [
                    {
                        type: "text",
                        text: text
                    }
                ]
            },
            {
                headers: {
                    "Content-Type": "application/json",
                    "Authorization": `Bearer ${TOKEN}`
                }
            }
        );

        console.log("📩 LINE SENT SUCCESS");
        return res.data;

    } catch (err) {
        console.log("❌ LINE ERROR:", err.response?.data || err.message);
    }
}

module.exports = {
    sendNotification
};