// LINE 推播（整合負責人新增，2026-10-29）
// 為什麼不直接用組員的 lineService.js：
//  1) 它會把錯誤吞掉、永遠回傳成功，SOS 無法知道有沒有真的送出
//  2) 它在載入時把 NODE_TLS_REJECT_UNAUTHORIZED 設成 0（關閉憑證驗證），不安全
// 這支回傳 { ok, reason }，並且不動任何全域設定。組員的檔案維持原樣。
const axios = require("axios");
require("dotenv").config();

function lineConfigStatus() {
    return {
        token_set: !!process.env.LINE_CHANNEL_ACCESS_TOKEN,
        demo_user_set: !!process.env.LINE_DEMO_USER_ID
    };
}

/**
 * @param {string} userId LINE User ID（U 開頭、33 碼）
 * @param {string} text 訊息
 * @returns {Promise<{ok:boolean, reason?:string}>}
 */
async function pushText(userId, text) {
    const token = process.env.LINE_CHANNEL_ACCESS_TOKEN;
    if (!token) return { ok: false, reason: "未設定 LINE_CHANNEL_ACCESS_TOKEN" };
    if (!userId) return { ok: false, reason: "沒有收件人 LINE User ID" };
    try {
        await axios.post(
            "https://api.line.me/v2/bot/message/push",
            { to: userId, messages: [{ type: "text", text: String(text).slice(0, 4900) }] },
            {
                headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
                timeout: 8000
            }
        );
        return { ok: true };
    } catch (e) {
        const status = e.response && e.response.status;
        const detail = e.response && e.response.data && (e.response.data.message || JSON.stringify(e.response.data));
        let reason = detail || e.message;
        if (status === 401) reason = "Token 無效或過期（請重新核發 Channel access token）";
        else if (status === 400) reason = "User ID 格式不對，或對方尚未加官方帳號為好友（" + (detail || "") + "）";
        else if (status === 429) reason = "本月免費訊息額度已用完";
        console.error("LINE 推播失敗：", status, reason);
        return { ok: false, reason };
    }
}

module.exports = { pushText, lineConfigStatus };
