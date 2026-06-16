// 🚨 緊急通知核心模組

// 引入聯絡人模組
const contactService = require("./contactService");
// LINE Push 模組
const { sendPushMessage } = require("./lineService");
const db = require("./dbService");//引入資料庫模組
/**
 * 🚨 發送 SOS
 */
async function sendSOS(reason, gpsData, targetName = null, source = "system") {

    // 🧠 取得聯絡人資料
    const contact = contactService.resolveContact(targetName);

    // 🧠 建立 SOS 訊息
    const alertMessage = {
        status: "SOS_SENT",
        source: source, // 👈 這個就是AI對接用

        // 聯絡人資訊
        contact_name: contact.name,
        contact_phone: contact.phone,

        // SOS 原因
        reason: reason,

        // GPS 位置
        location: gpsData,

        // 時間
        timestamp: new Date()
    };

    // 🧠 模擬發送通知
    // 目前只是 console.log
    // 之後才會接 Telegram / LINE
    console.log("🚨 SOS ALERT:", alertMessage);
    // =========================
// LINE Push 通知
// =========================

// 先用固定測試帳號
// 之後可以改成 contact.lineUserId

const demoUserId = "U67d09829a90c4cb10ba5c251d61abfa2"

// 組合 LINE 訊息
const lineMessage =
`🚨 SOS 緊急通知

聯絡人：${contact.name}

原因：${reason}

位置：
緯度：${gpsData?.latitude || "未知"}
經度：${gpsData?.longitude || "未知"}

時間：
${new Date().toLocaleString()}
`;
if (!demoUserId) {
    throw new Error("LINE userId 是空的");
}
// 發送 LINE
await sendPushMessage(
    demoUserId,
    lineMessage
);
// =========================
// 📦 寫入 SOS 紀錄到 DB
// =========================

const data = db.readDB();

const sosRecord = {
    id: Date.now(),
    status: "SOS_SENT",
    source,
    contact_name: contact.name,
    contact_phone: contact.phone,
    reason,
    location: gpsData,
    timestamp: new Date().toISOString()
};

data.sos_logs.push(sosRecord);

db.writeDB(data);

    // 回傳 API 結果
    return {
        action: "sos_sent",
        status: "success",
        target_name: contact.name,
        source: source,
        message: "SOS sent successfully",
        data: alertMessage
    };
}



// 匯出功能
module.exports = {
    sendSOS
};