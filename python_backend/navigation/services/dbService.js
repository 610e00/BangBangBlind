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
function ensureDBFile() {
    // 2026-10-28：storage/database.json 不在 GitHub 上（資料檔不進版控），
    // 新環境第一次執行會找不到檔案（ENOENT），導致 SOS 失敗。
    // 這裡自動建立資料夾與空的資料檔。
    const dir = path.dirname(DB_PATH);
    if (!fs.existsSync(dir)) {
        fs.mkdirSync(dir, { recursive: true });
    }
    if (!fs.existsSync(DB_PATH)) {
        fs.writeFileSync(
            DB_PATH,
            JSON.stringify(
                { contacts: [], sos_logs: [], navigation_records: [] },
                null,
                4
            )
        );
    }
}

function readDB() {

    ensureDBFile();

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

    ensureDBFile();

    fs.writeFileSync(
        DB_PATH,
        JSON.stringify(data, null, 4)
    );
}

module.exports = {
    readDB,
    writeDB
};