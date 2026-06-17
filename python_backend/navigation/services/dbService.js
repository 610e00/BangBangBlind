const fs = require("fs");
const path = require("path");

/**
 * database.json 位置
 */
const DB_PATH = path.join(
    __dirname,
    "../storage/database.json"
);

/**
 * 讀取資料庫
 */
function readDB() {

    const rawData = fs.readFileSync(DB_PATH, "utf8");
    const data = JSON.parse(rawData || "{}");

    // 🧠 保底結構（避免 crash）
    return {
        contacts: data.contacts || [],
        sos_logs: data.sos_logs || [],
        navigation_records: data.navigation_records || []
    };
}

/**
 * 寫入資料庫
 */
function writeDB(data) {

    fs.writeFileSync(
        DB_PATH,
        JSON.stringify(data, null, 4)
    );
}

module.exports = {
    readDB,
    writeDB
};