
package com.example.facerecognition.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * FamiliarDatabaseHelper
 *
 * 組員C：SQLite 熟人資料庫管理
 *
 * 目前負責：
 * 1. 建立 familiar_faces.db 資料庫
 * 2. 建立 familiar_people 熟人資料表
 * 3. 管理資料庫版本
 *
 * 之後會加入：
 * 熟人新增、修改、刪除、查詢，
 * 以及 128 維特徵向量的儲存功能。
 */

class FamiliarDatabaseHelper(
    context: Context,

    // 預設使用正式資料庫
    // 單元測試可以指定不同的資料庫名稱
    databaseName: String = DATABASE_NAME

) : SQLiteOpenHelper(
    context.applicationContext,
    databaseName,
    null,
    DATABASE_VERSION
) {


    companion object {

        // 資料庫名稱
        const val DATABASE_NAME = "familiar_faces.db"

        // 第一版資料庫
        const val DATABASE_VERSION = 1

        // 熟人資料表名稱
        const val TABLE_FAMILIAR_PEOPLE = "familiar_people"

        // 資料欄位
        const val COLUMN_PERSON_ID = "person_id"
        const val COLUMN_NAME = "name"
        const val COLUMN_RELATIONSHIP = "relationship"
        const val COLUMN_EMBEDDING = "embedding"
        const val COLUMN_CREATED_AT = "created_at"
        const val COLUMN_UPDATED_AT = "updated_at"
    }

    /**
     * 第一次建立資料庫時呼叫。
     *
     * 使用 SQL 建立熟人資料表。
     */
    override fun onCreate(db: SQLiteDatabase) {

        val createTableSql = """
            CREATE TABLE $TABLE_FAMILIAR_PEOPLE (
                $COLUMN_PERSON_ID TEXT PRIMARY KEY NOT NULL,
                $COLUMN_NAME TEXT NOT NULL,
                $COLUMN_RELATIONSHIP TEXT NOT NULL,
                $COLUMN_EMBEDDING BLOB NOT NULL,
                $COLUMN_CREATED_AT INTEGER NOT NULL,
                $COLUMN_UPDATED_AT INTEGER NOT NULL
            )
        """.trimIndent()

        db.execSQL(createTableSql)
    }

    /**
     * 資料庫版本更新時呼叫。
     *
     * 目前只有第一版，尚未設計升級流程。
     *
     * 特別注意：
     * 不使用 DROP TABLE 刪除熟人資料。
     * 未來修改資料表時，應建立正式的
     * Migration，避免使用者資料遺失。
     */
    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {
        throw IllegalStateException(
            "尚未支援資料庫升級：$oldVersion → $newVersion"
        )
    }
}
