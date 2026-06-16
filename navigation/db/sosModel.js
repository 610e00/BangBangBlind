// 載入資料庫
const db = require("./database");

/*
=================================
新增安全事件
=================================
*/
function createEvent(
    user_id,
    event_type,
    latitude,
    longitude,
    action_result
) {

    const stmt = db.prepare(`
        INSERT INTO SOS (
            user_id,
            event_type,
            latitude,
            longitude,
            action_result
        )
        VALUES (?, ?, ?, ?, ?)
    `);

    return stmt.run(
        user_id,
        event_type,
        latitude,
        longitude,
        action_result
    );
}

/*
=================================
查詢使用者安全事件
=================================
*/
function getEventsByUser(user_id) {

    const stmt = db.prepare(`
        SELECT *
        FROM SOS
        WHERE user_id = ?
        ORDER BY timestamp DESC
    `);

    return stmt.all(user_id);
}

module.exports = {
    createEvent,
    getEventsByUser
};