
package com.example.facerecognition.database

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

/**
 * FamiliarPersonDao
 *
 * 組員C：熟人資料庫存取工具
 *
 * 目前功能：
 * 1. 新增熟人
 * 2. 依 personId 查詢熟人
 * 3. 查詢所有熟人
 *
 * 後續會加入修改與刪除功能。
 */
class FamiliarPersonDao(
    private val databaseHelper: FamiliarDatabaseHelper
) {

    /**
     * 新增熟人資料。
     *
     * 成功回傳 true。
     * 若 personId 已存在，回傳 false。
     */
    fun insertPerson(person: FamiliarPerson): Boolean {

        // 建立資料庫欄位與對應數值
        val values = ContentValues().apply {
            put(
                FamiliarDatabaseHelper.COLUMN_PERSON_ID,
                person.personId
            )
            put(
                FamiliarDatabaseHelper.COLUMN_NAME,
                person.name
            )
            put(
                FamiliarDatabaseHelper.COLUMN_RELATIONSHIP,
                person.relationship
            )
            put(
                FamiliarDatabaseHelper.COLUMN_EMBEDDING,
                EmbeddingConverter.toByteArray(person.embedding)
            )
            put(
                FamiliarDatabaseHelper.COLUMN_CREATED_AT,
                person.createdAt
            )
            put(
                FamiliarDatabaseHelper.COLUMN_UPDATED_AT,
                person.updatedAt
            )
        }

        // 開啟可寫入的 SQLite 資料庫
        val db = databaseHelper.writableDatabase

        // personId 重複時不覆蓋既有資料
        val rowId = db.insertWithOnConflict(
            FamiliarDatabaseHelper.TABLE_FAMILIAR_PEOPLE,
            null,
            values,
            SQLiteDatabase.CONFLICT_IGNORE
        )

        return rowId != -1L
    }

    /**
     * 透過 personId 查詢單一熟人。
     *
     * 找不到時回傳 null。
     */
    fun getPersonById(personId: String): FamiliarPerson? {

        val db = databaseHelper.readableDatabase

        db.query(
            FamiliarDatabaseHelper.TABLE_FAMILIAR_PEOPLE,
            null,
            "${FamiliarDatabaseHelper.COLUMN_PERSON_ID} = ?",
            arrayOf(personId),
            null,
            null,
            null
        ).use { cursor ->

            return if (cursor.moveToFirst()) {
                cursorToPerson(cursor)
            } else {
                null
            }
        }
    }

    /**
     * 查詢所有已註冊熟人。
     *
     * 回傳 List<FamiliarPerson>。
     */
    fun getAllPersons(): List<FamiliarPerson> {

        val persons = mutableListOf<FamiliarPerson>()

        val db = databaseHelper.readableDatabase

        db.query(
            FamiliarDatabaseHelper.TABLE_FAMILIAR_PEOPLE,
            null,
            null,
            null,
            null,
            null,
            FamiliarDatabaseHelper.COLUMN_CREATED_AT + " ASC"
        ).use { cursor ->

            while (cursor.moveToNext()) {
                persons.add(cursorToPerson(cursor))
            }
        }

        return persons
    }

    /**
     * 將 SQLite Cursor 轉換成 FamiliarPerson。
     *
     * embedding 欄位會從 BLOB
     * 還原成 FloatArray(128)。
     */
    private fun cursorToPerson(cursor: Cursor): FamiliarPerson {

        fun columnIndex(columnName: String): Int {
            return cursor.getColumnIndexOrThrow(columnName)
        }

        val embeddingBytes = cursor.getBlob(
            columnIndex(FamiliarDatabaseHelper.COLUMN_EMBEDDING)
        )

        return FamiliarPerson(
            personId = cursor.getString(
                columnIndex(FamiliarDatabaseHelper.COLUMN_PERSON_ID)
            ),
            name = cursor.getString(
                columnIndex(FamiliarDatabaseHelper.COLUMN_NAME)
            ),
            relationship = cursor.getString(
                columnIndex(FamiliarDatabaseHelper.COLUMN_RELATIONSHIP)
            ),
            embedding = EmbeddingConverter.fromByteArray(
                embeddingBytes
            ),
            createdAt = cursor.getLong(
                columnIndex(FamiliarDatabaseHelper.COLUMN_CREATED_AT)
            ),
            updatedAt = cursor.getLong(
                columnIndex(FamiliarDatabaseHelper.COLUMN_UPDATED_AT)
            )
        )
    }


    /**
     * 修改指定熟人的姓名與關係稱呼。
     *
     * 不修改 personId、embedding、createdAt。
     *
     * @return true：成功修改一筆資料
     *         false：找不到該人物
     */
    fun updatePersonInfo(
        personId: String,
        newName: String,
        newRelationship: String
    ): Boolean {

        // 檢查輸入資料
        require(personId.isNotBlank()) {
            "personId 不可為空白"
        }

        require(newName.isNotBlank()) {
            "熟人姓名不可為空白"
        }

        // 建立需要修改的欄位
        val values = ContentValues().apply {
            put(
                FamiliarDatabaseHelper.COLUMN_NAME,
                newName
            )

            put(
                FamiliarDatabaseHelper.COLUMN_RELATIONSHIP,
                newRelationship
            )

            put(
                FamiliarDatabaseHelper.COLUMN_UPDATED_AT,
                System.currentTimeMillis()
            )
        }

        // 取得可寫入的 SQLite 資料庫
        val db = databaseHelper.writableDatabase

        // 只更新指定 personId 的熟人
        val affectedRows = db.update(
            FamiliarDatabaseHelper.TABLE_FAMILIAR_PEOPLE,
            values,
            "${FamiliarDatabaseHelper.COLUMN_PERSON_ID} = ?",
            arrayOf(personId)
        )

        // 至少修改一筆才算成功
        return affectedRows > 0
    }

    /**
     * 刪除指定熟人。
     *
     * 包含姓名、關係稱呼及人臉特徵向量。
     *
     * @return true：成功刪除
     *         false：找不到該人物
     */
    fun deletePerson(personId: String): Boolean {

        require(personId.isNotBlank()) {
            "personId 不可為空白"
        }

        // 取得可寫入的 SQLite 資料庫
        val db = databaseHelper.writableDatabase

        // 使用參數化條件，避免直接拼接使用者輸入
        val deletedRows = db.delete(
            FamiliarDatabaseHelper.TABLE_FAMILIAR_PEOPLE,
            "${FamiliarDatabaseHelper.COLUMN_PERSON_ID} = ?",
            arrayOf(personId)
        )

        return deletedRows > 0
    }

}
