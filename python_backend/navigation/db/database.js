// 載入 better-sqlite3
const Database = require("better-sqlite3");

// 連接資料庫
// 與 init-db.js 使用同一個資料庫檔案
const db = new Database("./db/navigation.db");

// 匯出資料庫物件
module.exports = db;