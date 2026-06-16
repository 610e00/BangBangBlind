// 載入資料庫
const db = require("./database");

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
    priority
) {
    const stmt = db.prepare(`
        INSERT INTO emergency_contact (
            user_id,
            contact_name,
            phone,
            relationship,
            priority
        )
        VALUES (?, ?, ?, ?, ?)
    `);

    return stmt.run(
        user_id,
        contact_name,
        phone,
        relationship,
        priority
    );
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

module.exports = {
    createContact,
    getContactsByUser
};