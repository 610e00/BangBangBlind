// 載入資料庫連線
const db = require("./database");

/*
=========================
新增使用者
=========================
*/
function createUser() {

    const stmt = db.prepare(`
        INSERT INTO users DEFAULT VALUES
    `);

    return stmt.run();
}

/*
=========================
查詢所有使用者
=========================
*/
function getAllUsers() {

    const stmt = db.prepare(`
        SELECT * FROM users
    `);

    return stmt.all();
}

module.exports = {
    createUser,
    getAllUsers
};