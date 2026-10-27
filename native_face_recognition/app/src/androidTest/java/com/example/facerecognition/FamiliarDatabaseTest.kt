
package com.example.facerecognition

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4

import com.example.facerecognition.database.FamiliarDatabaseHelper
import com.example.facerecognition.database.FamiliarPerson
import com.example.facerecognition.database.FamiliarPersonDao

import android.content.Context

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 組員C：SQLite 熟人資料庫整合測試
 *
 * 在 Android 模擬器上執行。
 *
 * 使用獨立的測試資料庫，
 * 不影響正式 familiar_faces.db。
 */
@RunWith(AndroidJUnit4::class)
class FamiliarDatabaseTest {

    private lateinit var context: Context
    private lateinit var databaseHelper: FamiliarDatabaseHelper
    private lateinit var dao: FamiliarPersonDao

    private val testDatabaseName = "familiar_faces_test.db"

    @Before
    fun setUp() {

        // 取得受測 App 的 Context
        context = ApplicationProvider.getApplicationContext()

        context.deleteDatabase(testDatabaseName)

        // 建立 SQLite Helper 和 DAO
        databaseHelper = FamiliarDatabaseHelper(
            context,
            testDatabaseName
        )
        dao = FamiliarPersonDao(databaseHelper)
    }

    @After
    fun tearDown() {

        // 測試完成後關閉資料庫
        databaseHelper.close()

        // 刪除測試資料
        context.deleteDatabase(testDatabaseName)
    }

    @Test
    fun testInsertAndReadPerson() {

        // 建立 128 維模擬特徵向量
        val embedding = FloatArray(128) { index ->
            index.toFloat() / 100f
        }

        // 建立一位測試熟人
        val person = FamiliarPerson(
            personId = "test_mom_001",
            name = "王小美",
            relationship = "媽媽",
            embedding = embedding
        )

        // 寫入 SQLite
        val inserted = dao.insertPerson(person)
        assertTrue(inserted)

        // 透過 ID 查詢 SQLite
        val savedPerson = dao.getPersonById(
            "test_mom_001"
        )

        // 確認成功讀取
        assertNotNull(savedPerson)

        // savedPerson 仍為可空型別，
        // 透過 requireNotNull 取得非空物件
        val result = requireNotNull(savedPerson)

        // 確認熟人資料一致
        assertEquals("王小美", result.name)
        assertEquals("媽媽", result.relationship)
        assertEquals("媽媽", result.getDisplayName())

        // 確認 128 維向量完全一致
        assertArrayEquals(
            embedding,
            result.embedding,
            0f
        )

        // 再查詢全部熟人
        val allPersons = dao.getAllPersons()
        assertEquals(1, allPersons.size)

        // 確認重複 ID 不會覆蓋資料
        val duplicateInserted = dao.insertPerson(person)
        assertFalse(duplicateInserted)

        println("SQLite 熟人新增與查詢測試成功")
        println("personId：${result.personId}")
        println("name：${result.name}")
        println("relationship：${result.relationship}")
        println("embedding 維度：${result.embedding.size}")
    }

    /**
     * 測試修改熟人的姓名與關係。
     *
     * 確認：
     * 1. 修改成功
     * 2. 姓名和關係更新
     * 3. personId 不變
     * 4. 人臉特徵向量不變
     */
    @Test
    fun testUpdatePersonInfo() {

        // 建立 128 維測試特徵向量
        val embedding = FloatArray(128) { index ->
            index.toFloat() / 100f
        }

        // 建立原始熟人資料
        val person = FamiliarPerson(
            personId = "test_mom_002",
            name = "王小美",
            relationship = "阿姨",
            embedding = embedding
        )

        // 先將熟人存入 SQLite
        assertTrue(dao.insertPerson(person))

        // 修改熟人的姓名與關係
        val updated = dao.updatePersonInfo(
            personId = "test_mom_002",
            newName = "王美玲",
            newRelationship = "媽媽"
        )

        // 確認修改成功
        assertTrue(updated)

        // 重新從 SQLite 查詢
        val result = requireNotNull(
            dao.getPersonById("test_mom_002")
        )

        // 確認資料修改正確
        assertEquals("test_mom_002", result.personId)
        assertEquals("王美玲", result.name)
        assertEquals("媽媽", result.relationship)
        assertEquals("媽媽", result.getDisplayName())

        // 確認特徵向量保持不變
        assertArrayEquals(
            embedding,
            result.embedding,
            0f
        )

        // 確認原始建立時間沒有改變
        assertEquals(person.createdAt, result.createdAt)

        // 測試修改不存在的人物
        assertFalse(
            dao.updatePersonInfo(
                personId = "not_existing_person",
                newName = "不存在",
                newRelationship = "朋友"
            )
        )

        println("SQLite 熟人修改測試成功")
        println("修改後姓名：${result.name}")
        println("修改後稱呼：${result.getDisplayName()}")
    }

    /**
     * 測試刪除熟人。
     *
     * 確認：
     * 1. 熟人可成功新增
     * 2. 刪除後無法再查詢到
     * 3. 重複刪除會回傳 false
     */
    @Test
    fun testDeletePerson() {

        // 建立測試熟人
        val person = FamiliarPerson(
            personId = "test_friend_001",
            name = "陳小華",
            relationship = "朋友",
            embedding = FloatArray(128) { 0.5f }
        )

        // 新增熟人
        assertTrue(dao.insertPerson(person))

        // 確認新增後可以查詢
        assertNotNull(
            dao.getPersonById("test_friend_001")
        )

        // 刪除熟人
        val deleted = dao.deletePerson(
            "test_friend_001"
        )

        // 確認刪除成功
        assertTrue(deleted)

        // 刪除後應該查不到此人物
        val result = dao.getPersonById(
            "test_friend_001"
        )

        org.junit.Assert.assertNull(result)

        // 全部熟人應該是空的
        assertTrue(dao.getAllPersons().isEmpty())

        // 再次刪除相同人物，應回傳 false
        assertFalse(
            dao.deletePerson("test_friend_001")
        )

        println("SQLite 熟人刪除測試成功")
        println("已刪除 personId：test_friend_001")
    }
}
