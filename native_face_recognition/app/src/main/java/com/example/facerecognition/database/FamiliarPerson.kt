
package com.example.facerecognition.database

/**
 * FamiliarPerson
 *
 * 組員C：熟人資料模型
 *
 * 用來表示 SQLite 資料庫中的一位熟人。
 *
 * @property personId
 * 人物唯一識別碼。
 * 必須與組員B的 RecognitionResult.personId 對應。
 *
 * @property name
 * 熟人的姓名，例如「王小美」。
 *
 * @property relationship
 * 與使用者的關係或稱呼，例如「媽媽」、「朋友」。
 * 後續可以提供給 Android TTS 使用。
 *
 * @property embedding
 * MobileFaceNet 產生的 128 維人臉特徵向量。
 *
 * @property createdAt
 * 建立資料的時間，單位為毫秒。
 *
 * @property updatedAt
 * 最後修改資料的時間，單位為毫秒。
 */
data class FamiliarPerson(
    val personId: String,
    val name: String,
    val relationship: String,
    val embedding: FloatArray,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {

    init {

        // 人物 ID 不可為空白
        require(personId.isNotBlank()) {
            "personId 不可為空白"
        }

        // 熟人姓名不可為空白
        require(name.isNotBlank()) {
            "熟人姓名不可為空白"
        }

        // 特徵向量必須符合 MobileFaceNet 的輸出維度
        require(embedding.size == 128) {
            "人臉特徵向量必須為 128 維"
        }

        // 確保所有特徵值都是有效的浮點數
        require(embedding.all { it.isFinite() }) {
            "人臉特徵向量不可包含 NaN 或 Infinity"
        }
    }

    /**
     * 取得語音播報時使用的稱呼。
     *
     * 如果有設定關係稱呼，例如「媽媽」，
     * 優先使用 relationship。
     *
     * 如果 relationship 是空白，
     * 就使用 name。
     */
    fun getDisplayName(): String {
        return relationship.ifBlank { name }
    }
}
