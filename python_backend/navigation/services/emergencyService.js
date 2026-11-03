// 🚨 緊急通知核心模組
// 2026-10-29 改版（整合負責人）：SOS 改為同時通知「所有開啟 SOS 通知的聯絡人」，
// 並逐人回報成功／失敗（原本只通知順位 1 一位）。
const contactModel = require("../db/contactModel");// SQLite 聯絡人 Model

// Email 通知（手機沒有 SIM 時的主要管道，見 services/emailService.js）
const { sendAlertEmail } = require("./emailService");
const db = require("./dbService");//引入資料庫模組
require("dotenv").config();

/**
 * 🚨 發送 SOS
 * @param {string} reason 求助原因
 * @param {{latitude:number, longitude:number}} gpsData 位置
 * @param {string|null} targetName 使用者說的對象（姓名或關係出現在這句話裡的聯絡人才通知）；null＝通知全部開關為開的人
 * @param {string} source 來源（system / AI）
 * @param {string} userName 求助者姓名（顯示在通知裡，讓收信人知道是誰在求助）
 * @param {Array|null} providedContacts 手機帶來的聯絡人（陣列，含空陣列）；null＝改讀後端資料庫
 */
async function sendSOS(reason, gpsData, targetName = null, source = "system", userName = "", providedContacts = null) {

    // Demo 固定 user_id = 1（帳號系統尚未實作）
    // 2026-10-31：有帶 providedContacts（陣列）就完全用它，後端不需要存聯絡人（雲端重啟不會掉資料）
    const usePhoneContacts = Array.isArray(providedContacts);
    const allContacts = usePhoneContacts
        ? providedContacts
              .filter((c) => c && c.contact_name)
              .map((c) => ({
                  contact_name: String(c.contact_name),
                  relationship: String(c.relationship || ""),
                  email: String(c.email || ""),
                  notify_on_sos: c.notify_on_sos === 0 || c.notify_on_sos === false ? 0 : 1
              }))
        : contactModel.getContactsByUser(1);

    // 2026-10-29：使用者用說的指定對象（例如「我要發送給姑姑」）時，
    // 只通知「姓名或關係」出現在這句話裡的聯絡人，並且不受 SOS 開關限制
    // （使用者明確點名，優先於開關）。
    // 說了名字但找不到人（例如沒有「姑姑」）：為了安全，改通知全部開關為開的人，
    // 並回報 target_matched=false，讓 App 老實告訴使用者。
    let contacts = [];
    let targetMatched = null; // null＝沒指定對象
    const wanted = targetName ? String(targetName).replace(/\s/g, "") : "";
    if (wanted) {
        const hit = (text) => text && text.length >= 2 && wanted.includes(text);
        contacts = allContacts.filter(
            (c) => hit((c.contact_name || "").replace(/\s/g, "")) ||
                   hit((c.relationship || "").replace(/\s/g, ""))
        );
        targetMatched = contacts.length > 0;
    }
    if (contacts.length === 0) {
        contacts = usePhoneContacts
            ? allContacts.filter((c) => c.notify_on_sos !== 0)
            : contactModel.getSosContacts(1);
    }

    if (!contacts || contacts.length === 0) {
        // 區分「完全沒有聯絡人」與「有但全部關閉 SOS 通知」，讓使用者知道怎麼修
        throw new Error(
            allContacts.length === 0
                ? "找不到緊急聯絡人，請先到設定新增"
                : "所有緊急聯絡人都關閉了 SOS 通知，請到設定開啟至少一位"
        );
    }

    // ===== 組合通知訊息（Email 通知用）=====
    const hasGps = gpsData && typeof gpsData.latitude === "number" && typeof gpsData.longitude === "number";
    const mapLink = hasGps
        ? `https://www.google.com/maps?q=${gpsData.latitude},${gpsData.longitude}`
        : null;
    const nowTaiwan = new Date().toLocaleString("zh-TW", { timeZone: "Asia/Taipei" });
    const who = userName && String(userName).trim() ? String(userName).trim() : "（未設定姓名）";
    const message =
`🚨 SOS 緊急通知

求助者：${who}

原因：${reason}

位置：${mapLink ? mapLink : "無法取得定位"}
${hasGps ? `（緯度 ${gpsData.latitude}，經度 ${gpsData.longitude}）` : ""}

時間：${nowTaiwan}
`;

    // 供記錄／AI 對接用
    const alertMessage = {
        status: "SOS_SENT",
        source: source,
        contacts: contacts.map((c) => c.contact_name),
        reason: reason,
        location: gpsData,
        timestamp: new Date()
    };
    console.log("🚨 SOS ALERT:", alertMessage);

    // ===== 逐一通知每位聯絡人（Email），互不影響 =====
    const results = await Promise.all(
        contacts.map(async (c) => {
            const email = (c.email || "").trim();
            // 該聯絡人沒填 Email：退回 .env 的 ALERT_EMAIL_TO（展示用備用信箱）
            const target = email || process.env.ALERT_EMAIL_TO || "";
            if (!target) {
                console.warn(`⚠️ ${c.contact_name} 沒有 Email，也沒有備用信箱，無法通知`);
                return { name: c.contact_name, ok: false, reason: "沒有 Email" };
            }
            try {
                const sent = await sendAlertEmail("🚨 SOS 緊急通知（幫幫盲）", message, target);
                return sent
                    ? { name: c.contact_name, ok: true }
                    : { name: c.contact_name, ok: false, reason: "Email 設定不完整" };
            } catch (error) {
                console.error(`Email 通知 ${c.contact_name} 失敗：`, error.message);
                return { name: c.contact_name, ok: false, reason: "寄信失敗" };
            }
        })
    );

    const notifiedNames = results.filter((r) => r.ok).map((r) => r.name);
    const failedNames = results.filter((r) => !r.ok).map((r) => r.name);
    // 求助是安全功能，不能給假的安心感：任一管道真的送出才算已通知
    const notified = notifiedNames.length > 0;

    // ===== 寫入 SOS 紀錄 =====
    const dbSnapshot = db.readDB();
    dbSnapshot.sos_logs.push({
        id: Date.now(),
        status: "SOS_SENT",
        source,
        user_name: who,
        contacts: contacts.map((c) => c.contact_name),
        notified_names: notifiedNames,
        failed_names: failedNames,
        reason,
        location: gpsData,
        timestamp: new Date().toISOString()
    });
    db.writeDB(dbSnapshot);

    return {
        action: "sos_sent",
        status: "success",
        notified: notified,
        notified_names: notifiedNames,
        failed_names: failedNames,
        target_matched: targetMatched,
        target_name: notifiedNames.join("、"),
        source: source,
        message: "SOS processed",
        data: alertMessage
    };
}

// 匯出功能
module.exports = {
    sendSOS
};
