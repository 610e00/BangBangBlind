const eventBus = require("../events/eventBus");


// 啟動 Event Logger
require("../eventLogger");


console.log(
    "\n🧪 Day 19 EventBus Integration Test\n"
);


// =========================================
// Navigation
// =========================================

console.log(
    "\n========== 🚗 Navigation =========="
);

eventBus.emit("navigation", {

    mode: "navigation",

    destination: "台北車站",

    state: "attention"

});


// =========================================
// Emergency
// =========================================

console.log(
    "\n========== 🚨 Emergency =========="
);

eventBus.emit("emergency", {

    mode: "emergency",

    action: "call",

    contact: "媽媽",

    phone: "0900000000"

});


// =========================================
// Safety
// =========================================

console.log(
    "\n========== 🛡️ Safety =========="
);

eventBus.emit("safety", {

    level: "danger",

    message:
        "前方障礙物與紅燈，請立即停止"

});


console.log(
    "\n=============================="
);

console.log(
    "✅ Day 19 Step 2 Test 完成"
);