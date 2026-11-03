/**
 * Email 備援通知（2026-10-29 新增，整合負責人）
 *
 * 為什麼需要：展示用手機沒有 SIM 卡，無法簡訊／電話；LINE 已於 2026-10-30 決議移除，通知只留 Email。
 * Email 由「後端電腦」透過網路寄出，手機不需要 SIM，也不需要別人的金鑰。
 *
 * 設定（後端 .env）：
 *   SMTP_USER=你的Gmail帳號@gmail.com
 *   SMTP_PASS=Gmail「應用程式密碼」（Google 帳戶 → 安全性 → 兩步驟驗證 → 應用程式密碼；不是登入密碼）
 *   ALERT_EMAIL_TO=收件人信箱（可用逗號分隔多個）
 * 並在後端資料夾執行一次：npm install nodemailer
 *
 * 沒設定或沒安裝 nodemailer 時只會跳過，不影響 SOS 流程。
 */
async function sendAlertEmail(subject, text, toOverride) {
    const user = process.env.SMTP_USER;
    const pass = process.env.SMTP_PASS;
    const to = toOverride || process.env.ALERT_EMAIL_TO;

    if (!user || !pass || !to) {
        console.warn("⚠️ Email 備援未設定或收件人為空（SMTP_USER / SMTP_PASS / 聯絡人 Email 或 ALERT_EMAIL_TO），跳過");
        return false;
    }

    let nodemailer;
    try {
        nodemailer = require("nodemailer");
    } catch (e) {
        console.warn("⚠️ 尚未安裝 nodemailer（請在後端資料夾執行 npm install nodemailer），跳過 Email");
        return false;
    }

    const transporter = nodemailer.createTransport({
        service: "gmail",
        auth: { user, pass }
    });

    await transporter.sendMail({ from: user, to, subject, text });
    return true;
}

module.exports = { sendAlertEmail };
