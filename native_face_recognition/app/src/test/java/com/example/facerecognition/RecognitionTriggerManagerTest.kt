
package com.example.facerecognition

import com.example.facerecognition.trigger.RecognitionTriggerManager
import com.example.facerecognition.trigger.RecognitionTriggerResult
import com.example.facerecognition.trigger.RecognitionTriggerSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 組員C：辨識觸發管理器單元測試。
 *
 * 不需模擬器，不會真正執行人臉辨識。
 */
class RecognitionTriggerManagerTest {

    /**
     * 測試語音、避障及手動三種觸發來源。
     */
    @Test
    fun testThreeTriggerSources() {

        val receivedSources =
            mutableListOf<RecognitionTriggerSource>()

        val manager = RecognitionTriggerManager { source ->
            receivedSources.add(source)
        }

        manager.setReady(true)

        val sources = listOf(
            RecognitionTriggerSource.VOICE,
            RecognitionTriggerSource.OBSTACLE,
            RecognitionTriggerSource.MANUAL
        )

        for (source in sources) {

            val result = manager.requestRecognition(source)

            assertEquals(
                RecognitionTriggerResult.ACCEPTED,
                result
            )

            // 模擬辨識流程結束
            manager.onRecognitionFinished()
        }

        assertEquals(sources, receivedSources)

        println("三種觸發來源測試成功")
    }

    /**
     * 測試辨識期間不接受重複請求。
     */
    @Test
    fun testBusyState() {

        var requestCount = 0

        val manager = RecognitionTriggerManager {
            requestCount++
        }

        manager.setReady(true)

        // 第一次請求成功
        assertEquals(
            RecognitionTriggerResult.ACCEPTED,
            manager.requestRecognition(
                RecognitionTriggerSource.VOICE
            )
        )

        // 尚未呼叫 onRecognitionFinished()
        assertTrue(manager.isRecognitionRunning())

        // 第二次請求應該被拒絕
        assertEquals(
            RecognitionTriggerResult.BUSY,
            manager.requestRecognition(
                RecognitionTriggerSource.MANUAL
            )
        )

        assertEquals(1, requestCount)

        // 完成第一次辨識
        manager.onRecognitionFinished()

        assertFalse(manager.isRecognitionRunning())

        // 現在可以接受下一次請求
        assertEquals(
            RecognitionTriggerResult.ACCEPTED,
            manager.requestRecognition(
                RecognitionTriggerSource.MANUAL
            )
        )

        manager.onRecognitionFinished()

        assertEquals(2, requestCount)

        println("防重複觸發測試成功")
    }

    /**
     * 測試危險障礙物存在時阻擋熟人辨識。
     */
    @Test
    fun testDangerBlocksRecognition() {

        var requestCount = 0

        val manager = RecognitionTriggerManager {
            requestCount++
        }

        manager.setReady(true)

        // 模擬避障模組偵測到危險
        manager.setDangerPresent(true)

        for (source in RecognitionTriggerSource.values()) {

            assertEquals(
                RecognitionTriggerResult.BLOCKED_BY_DANGER,
                manager.requestRecognition(source)
            )
        }

        // 危險期間不可啟動辨識
        assertEquals(0, requestCount)

        // 危險解除
        manager.setDangerPresent(false)

        assertEquals(
            RecognitionTriggerResult.ACCEPTED,
            manager.requestRecognition(
                RecognitionTriggerSource.OBSTACLE
            )
        )

        manager.onRecognitionFinished()

        assertEquals(1, requestCount)

        println("危險狀態阻擋測試成功")
    }

    /**
     * 測試尚未初始化的狀態。
     */
    @Test
    fun testNotReady() {

        var requestCount = 0

        val manager = RecognitionTriggerManager {
            requestCount++
        }

        // 尚未呼叫 setReady(true)
        assertEquals(
            RecognitionTriggerResult.NOT_READY,
            manager.requestRecognition(
                RecognitionTriggerSource.VOICE
            )
        )

        assertEquals(0, requestCount)

        println("尚未準備完成測試成功")
    }

    /**
     * 測試回呼發生錯誤後可以重新接受請求。
     */
    @Test
    fun testCallbackFailure() {

        val manager = RecognitionTriggerManager {
            throw IllegalStateException("模擬啟動失敗")
        }

        manager.setReady(true)

        assertEquals(
            RecognitionTriggerResult.FAILED,
            manager.requestRecognition(
                RecognitionTriggerSource.MANUAL
            )
        )

        // 失敗後必須解除忙碌狀態
        assertFalse(manager.isRecognitionRunning())

        println("觸發失敗恢復測試成功")
    }
}
