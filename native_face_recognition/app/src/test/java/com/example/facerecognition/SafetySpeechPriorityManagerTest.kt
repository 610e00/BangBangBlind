
package com.example.facerecognition

import com.example.facerecognition.speech.SafetySpeechPriorityManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 組員C：安全語音優先權單元測試
 *
 * 不需要啟動 Pixel 7 模擬器。
 */
class SafetySpeechPriorityManagerTest {

    /**
     * 測試初始狀態沒有危險。
     */
    @Test
    fun testInitiallySafe() {

        val manager = SafetySpeechPriorityManager()

        assertFalse(manager.isDangerPresent())
        assertTrue(manager.canAnnounceFamiliarPerson())
    }

    /**
     * 測試危險出現時阻擋熟人播報。
     */
    @Test
    fun testDangerBlocksFamiliarSpeech() {

        val manager = SafetySpeechPriorityManager()

        manager.setDangerPresent(true)

        assertTrue(manager.isDangerPresent())
        assertFalse(manager.canAnnounceFamiliarPerson())
    }

    /**
     * 測試危險持續存在時仍然阻擋播報。
     */
    @Test
    fun testDangerRemainsActive() {

        val manager = SafetySpeechPriorityManager()

        manager.setDangerPresent(true)
        manager.setDangerPresent(true)

        assertFalse(manager.canAnnounceFamiliarPerson())
    }

    /**
     * 測試危險解除後恢復熟人播報。
     */
    @Test
    fun testDangerCleared() {

        val manager = SafetySpeechPriorityManager()

        manager.setDangerPresent(true)

        assertFalse(manager.canAnnounceFamiliarPerson())

        manager.setDangerPresent(false)

        assertFalse(manager.isDangerPresent())
        assertTrue(manager.canAnnounceFamiliarPerson())
    }
}