// 🚨 緊急通知核心模組
const contactModel = require("../db/contactModel");// SQLite 聯絡人 Model

// LINE Push 模組
const { sendPushMessage } = require("./lineService");
const db = require("./dbService");//引入資料庫模組
/**
 * 🚨 發送 SOS
 */
async function sendSOS(reason, gpsData, targetName = null, source = "system") {

/*
=================================
取得最高優先聯絡人
目前 Demo 固定 user_id = 1

之後 LINE webhook 接進來後
改成真正 user_id
=================================
*/
const contact = contactModel.getTopPriorityContact(1);

/*
=================================
找不到聯絡人
=================================
*/
if (!contact) {

    throw new Error("找不到緊急聯絡人");

}
    // 🧠 建立 SOS 訊息
    const alertMessage = {
        status: "SOS_SENT",
        source: source, // 👈 這個就是AI對接用

        // 聯絡人資訊
        contact_name: contact.contact_name,
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

//引入 LINE 測試帳號
require("dotenv").config();
const demoUserId = process.env.LINE_DEMO_USER_ID;

// 組合 LINE 訊息
const lineMessage =
`🚨 SOS 緊急通知

聯絡人：${contact.contact_name}

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
try {

    await sendPushMessage(
        demoUserId,
        lineMessage
    );

} catch (error) {

    console.error("LINE 通知失敗：", error.message);

}
// =========================
// 📦 寫入 SOS 紀錄到 DB
// =========================

const data = db.readDB();

const sosRecord = {
    id: Date.now(),
    status: "SOS_SENT",
    source,
    contact_name: contact.contact_name,
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
        target_name:contact.contact_name,
        source: source,
        message: "SOS sent successfully",
        data: alertMessage
    };
}



// 匯出功能
module.exports = {
    sendSOS
};