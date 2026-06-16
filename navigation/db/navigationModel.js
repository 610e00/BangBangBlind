// 載入資料庫
const db = require("./database");

/*
=================================
新增導航紀錄
=================================
*/
function createRecord(
    user_id,
    longitude,
    latitude,
    destination_name
) {
    const stmt = db.prepare(`
        INSERT INTO navigation_records (
            user_id,
            longitude,
            latitude,
            destination_name
        )
        VALUES (?, ?, ?, ?)
    `);

    return stmt.run(
        user_id,
        longitude,
        latitude,
        destination_name
    );
}

/*
=================================
查詢使用者導航紀錄
=================================
*/
function getRecordsByUser(user_id) {

    const stmt = db.prepare(`
        SELECT *
        FROM navigation_records
        WHERE user_id = ?
        ORDER BY created_time DESC
    `);

    return stmt.all(user_id);
}

module.exports = {
    createRecord,
    getRecordsByUser
};