function handleVoice(text) {

    if (
        !text ||
        typeof text !== "string"
    ) {

        return {
            type: "unknown",
            confidence: 0,
            message: "無法理解語音指令"
        };
    }


    const t =
        text.trim().toLowerCase();


    // =========================================
    // 🛑 1. Stop
    // =========================================

    const stopKeywords = [

        "停止導航",
        "取消導航",

        "停止",
        "取消",

        "不要導航",

        "不用了",
        "不要了",

        "停下來",
        "停下"
    ];


    if (
        stopKeywords.some(
            keyword =>
                t.includes(keyword)
        )
    ) {

        return {

            type: "stop",

            confidence: 0.95,

            message: "停止操作"
        };
    }


    // =========================================
    // 🚨 2. Emergency
    // =========================================

    const emergencyKeywords = [

        "打給",
        "撥給",
        "撥打",
        "聯絡",
        "打電話",
        "撥電話",

        "幫我打電話",
        "幫我撥電話",

        "救命",

        "緊急電話",
        "緊急服務",

        "打119",
        "撥119",
        "打 119",
        "撥 119"
    ];


    if (
        emergencyKeywords.some(
            keyword =>
                t.includes(keyword)
        )
    ) {

        return {

            type: "emergency",

            confidence: 0.95,

            message: "緊急聯絡請求"
        };
    }


    // =========================================
    // 🚗 3. Navigation
    // =========================================

    const navigationKeywords = [

        "帶我去",
        "帶我到",
        "帶我前往",

        "我要去",
        "我想去",

        "前往",

        "導航到",
        "導航去",
        "導航前往"
    ];


    if (
        navigationKeywords.some(
            keyword =>
                t.includes(keyword)
        )
    ) {

        return {

            type: "navigation",

            confidence: 0.9,

            message: "導航請求"
        };
    }


    // =========================================
    // ❓ 4. Unknown
    // =========================================

    return {

        type: "unknown",

        confidence: 0.2,

        message: "無法理解語音指令"
    };
}


module.exports = {
    handleVoice
};