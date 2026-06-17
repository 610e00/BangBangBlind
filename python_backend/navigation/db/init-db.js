// 載入 better-sqlite3
const Database = require("better-sqlite3");

// 建立資料庫檔案
const db = new Database("./db/navigation.db");

console.log("開始建立資料表...");

/*
=================================
users
使用者基本資料
=================================
*/
db.exec(`
CREATE TABLE IF NOT EXISTS users (

    -- 使用者ID
    user_id INTEGER PRIMARY KEY AUTOINCREMENT,

    -- 建立時間
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP
);
`);

console.log("users 建立完成");

/*
=================================
emergency_contact
緊急聯絡人
=================================
*/
db.exec(`
CREATE TABLE IF NOT EXISTS emergency_contact (

    -- 聯絡人ID
    contact_id INTEGER PRIMARY KEY AUTOINCREMENT,

    -- 對應使用者
    user_id INTEGER NOT NULL,

    -- 聯絡人姓名
    contact_name TEXT NOT NULL,

    -- 電話
    phone TEXT NOT NULL,

    -- 關係
    relationship TEXT,

    -- 通知優先順序
    priority INTEGER DEFAULT 1,

    -- 外鍵
    FOREIGN KEY(user_id)
    REFERENCES users(user_id)
);
`);

console.log("emergency_contact 建立完成");

/*
=================================
navigation_records
導航紀錄
=================================
*/
db.exec(`
    CREATE TABLE IF NOT EXISTS navigation_records (
    
        -- 導航紀錄ID
        record_id INTEGER PRIMARY KEY AUTOINCREMENT,
    
        -- 使用者ID
        user_id INTEGER NOT NULL,
    
        -- 經度
        longitude REAL NOT NULL,
    
        -- 緯度
        latitude REAL NOT NULL,
    
        -- 目的地名稱
        destination_name TEXT NOT NULL,
    
        -- 建立時間
        created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    
        -- 外鍵
        FOREIGN KEY(user_id)
        REFERENCES users(user_id)
    );
    `);
    
    console.log("navigation_records 建立完成");

    /*
=================================
SOS
安全事件紀錄
=================================
*/
db.exec(`
    CREATE TABLE IF NOT EXISTS SOS (
    
        -- 事件ID
        event_id INTEGER PRIMARY KEY AUTOINCREMENT,
    
        -- 使用者ID
        user_id INTEGER NOT NULL,
    
        -- 事件類型
        event_type TEXT NOT NULL,
    
        -- 緯度
        latitude REAL NOT NULL,
    
        -- 經度
        longitude REAL NOT NULL,
    
        -- 發生時間
        timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
    
        -- 系統處理結果
        action_result TEXT,
    
        -- 外鍵
        FOREIGN KEY(user_id)
        REFERENCES users(user_id)
    );
    `);
    
    console.log("SOS 建立完成");
    
db.close();

console.log("所有資料表建立完成");