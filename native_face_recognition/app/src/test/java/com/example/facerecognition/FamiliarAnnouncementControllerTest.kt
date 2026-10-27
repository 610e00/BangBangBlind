
package com.example.facerecognition

import com.example.facerecognition.speech.FamiliarAnnouncementController
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 組員C：防重複播報單元測試
 *
 * 使用模擬時間，確認冷卻機制正確。
 */
class FamiliarAnnouncementControllerTest {

    /**
     * 測試同一個人的 10 秒冷卻時間。
     */
    @Test
    fun testCooldownForSamePerson() {

        // 使用可自行控制的模擬時間
        var fakeTime = 100_000L

        val controller = FamiliarAnnouncementController(
            cooldownMillis = 10_000L,
            currentTimeMillis = { fakeTime }
        )

        // 第一次辨識到媽媽：允許播報
        assertTrue(
            controller.shouldAnnounce("person_mom")
        )

        // 5 秒後：仍在冷卻時間內
        fakeTime += 5_000L

        assertFalse(
            controller.shouldAnnounce("person_mom")
        )

        // 再過 5 秒：已經滿 10 秒
        fakeTime += 5_000L

        assertTrue(
            controller.shouldAnnounce("person_mom")
        )

        println("同一人物 10 秒冷卻測試成功")
    }

    /**
     * 測試不同人物有獨立的冷卻時間。
     */
    @Test
    fun testDifferentPersons() {

        var fakeTime = 100_000L

        val controller = FamiliarAnnouncementController(
            currentTimeMillis = { fakeTime }
        )

        // 媽媽第一次出現
        assertTrue(
            controller.shouldAnnounce("person_mom")
        )

        // 1 秒後朋友出現，仍可播報
        fakeTime += 1_000L

        assertTrue(
            controller.shouldAnnounce("person_friend")
        )

        // 但媽媽還在冷卻期間
        assertFalse(
            controller.shouldAnnounce("person_mom")
        )

        println("不同人物獨立冷卻測試成功")
    }

    /**
     * 測試清除單一人物的播報紀錄。
     */
    @Test
    fun testResetPerson() {

        val controller = FamiliarAnnouncementController(
            currentTimeMillis = { 100_000L }
        )

        // 第一次可以播報
        assertTrue(
            controller.shouldAnnounce("person_mom")
        )

        // 同一時間第二次被阻擋
        assertFalse(
            controller.shouldAnnounce("person_mom")
        )

        // 清除這位人物的播報紀錄
        controller.resetPerson("person_mom")

        // 清除後可以再次播報
        assertTrue(
            controller.shouldAnnounce("person_mom")
        )

        println("單一人物冷卻重設測試成功")
    }

    /**
     * 測試清除全部人物的播報紀錄。
     */
    @Test
    fun testResetAll() {

        val controller = FamiliarAnnouncementController(
            currentTimeMillis = { 100_000L }
        )

        // 兩位人物第一次都可以播報
        assertTrue(controller.shouldAnnounce("person_mom"))
        assertTrue(controller.shouldAnnounce("person_friend"))

        // 第二次都受到冷卻限制
        assertFalse(controller.shouldAnnounce("person_mom"))
        assertFalse(controller.shouldAnnounce("person_friend"))

        // 清除全部紀錄
        controller.resetAll()

        // 兩位人物都可以再次播報
        assertTrue(controller.shouldAnnounce("person_mom"))
        assertTrue(controller.shouldAnnounce("person_friend"))

        println("全部人物冷卻重設測試成功")
    }
}
