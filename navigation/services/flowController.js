const gpsService = require("./gpsService");
const safetyService = require("./safetyService");
const decisionService = require("./decisionService");
const emergencyService = require("./emergencyService");

/**

* 🚨 核心 Flow Controller
* 每次 GPS 更新後都會呼叫
  */
  async function processLocationUpdate(latitude, longitude) {

  // 1️⃣ 更新 GPS
  const gpsData = gpsService.updateLocation(latitude, longitude);

  // 2️⃣ Safety 檢查
  const safetyResult = safetyService.checkRisk(gpsData.data);

  // 3️⃣ Decision 判斷
  const decision = decisionService.evaluate(safetyResult);

  console.log("📊 FLOW RESULT:", decision);

  // 4️⃣ 根據結果處理
  if (decision.status === "danger") {

  ```
   console.log("🚨 DANGER DETECTED → AUTO SOS");

   await emergencyService.sendSOS(
       "auto_detect_danger",
       gpsData.data,
       null,
       "system"
   );
  ```

  }

  return {
  gps: gpsData,
  safety: safetyResult,
  decision: decision
  };
  }

module.exports = {
processLocationUpdate
};
