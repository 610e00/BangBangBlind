// 載入資料庫
const db = require("./database");

// 2026-10-29：聯絡人新增 email、notify_on_sos 兩個欄位。
// 用「缺欄位才補」的方式升級，舊資料庫與舊資料都不會壞（既有聯絡人 notify_on_sos 預設 1＝開）。
function ensureColumns() {
    try {
        const cols = db.prepare("PRAGMA table_info(emergency_contact)").all().map((c) => c.name);
        if (!cols.includes("email")) {
            db.exec("ALTER TABLE emergency_contact ADD COLUMN email TEXT DEFAULT ''");
        }
        if (!cols.includes("notify_on_sos")) {
            db.exec("ALTER TABLE emergency_contact ADD COLUMN notify_on_sos INTEGER DEFAULT 1");
        }
    } catch (e) {
        console.error("聯絡人資料表升級失敗：", e.message);
    }
}
ensureColumns();

// 緊急聯絡人數量上限（避免一次寄太多封）
const MAX_CONTACTS = 5;

/*
=================================
新增緊急聯絡人
=================================
*/
function createContact(
    user_id,
    contact_name,
    phone,
    relationship,
    priority,
    email = "",
    notify_on_sos = 1
) {
    const count = db.prepare(
        "SELECT COUNT(*) AS n FROM emergency_contact WHERE user_id = ?"
    ).get(user_id).n;
    if (count >= MAX_CONTACTS) {
        throw new Error(`緊急聯絡人最多 ${MAX_CONTACTS} 位`);
    }

    const stmt = db.prepare(`
        INSERT INTO emergency_contact (
            user_id,
            contact_name,
            phone,
            relationship,
            priority,
            email,
            notify_on_sos
        )
        VALUES (?, ?, ?, ?, ?, ?, ?)
    `);

    return stmt.run(
        user_id,
        contact_name,
        phone,
        relationship,
        priority,
        email || "",
        notify_on_sos === 0 || notify_on_sos === false ? 0 : 1
    );
}

/*
=================================
用編號查單一聯絡人（2026-10-29 新增，修改時比對 Email 有沒有變動）
=================================
*/
function getContactById(contact_id) {
    return db.prepare("SELECT * FROM emergency_contact WHERE contact_id = ?").get(contact_id);
}

/*
=================================
查詢某使用者所有聯絡人
=================================
*/
function getContactsByUser(user_id) {

    const stmt = db.prepare(`
        SELECT *
        FROM emergency_contact
        WHERE user_id = ?
        ORDER BY priority ASC
    `);

    return stmt.all(user_id);
}

/*
=================================
查詢某使用者最高優先級的緊急聯絡人
priority 越小越優先
=================================
*/
function getTopPriorityContact(user_id) {

    const stmt = db.prepare(`
        SELECT *
        FROM emergency_contact
        WHERE user_id = ?
        ORDER BY priority ASC
        LIMIT 1
    `);

    return stmt.get(user_id);
}

/*
=================================
查詢「SOS 時要通知」的所有聯絡人（開關為開），依順位排序（2026-10-29 新增）
=================================
*/
function getSosContacts(user_id) {

    const stmt = db.prepare(`
        SELECT *
        FROM emergency_contact
        WHERE user_id = ? AND COALESCE(notify_on_sos, 1) = 1
        ORDER BY priority ASC
    `);

    return stmt.all(user_id);
}

/*
=================================
修改聯絡人（2026-10-28 新增）
=================================
*/
function updateContact(contact_id, contact_name, phone, relationship, priority, email = "", notify_on_sos = 1) {
    const stmt = db.prepare(`
        UPDATE emergency_contact
        SET contact_name = ?,
            phone = ?,
            relationship = ?,
            priority = ?,
            email = ?,
            notify_on_sos = ?
        WHERE contact_id = ?
    `);

    return stmt.run(
        contact_name,
        phone,
        relationship,
        priority,
        email || "",
        notify_on_sos === 0 || notify_on_sos === false ? 0 : 1,
        contact_id
    );
}

/*
=================================
刪除聯絡人（2026-10-28 新增）
=================================
*/
function deleteContact(contact_id) {
    const stmt = db.prepare(`
        DELETE FROM emergency_contact
        WHERE contact_id = ?
    `);

    return stmt.run(contact_id);
}

module.exports = {
    updateContact,
    deleteContact,
    createContact,
    getContactsByUser,
    getTopPriorityContact,
    getSosContacts,
    getContactById
};