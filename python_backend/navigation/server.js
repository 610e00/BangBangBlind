//# Express主伺服器
const navigationRoutes =require("./routes/navigationRoutes");//引入 navigation routes
const sosRoutes =require("./routes/sosRoutes");//引入 sos routes
const contactRoutes =require("./routes/contactRoutes");//引入 contact routes
const databaseRoutes =require("./routes/databaseRoutes");//引入 database routes
const emergencyRoutes = require("./routes/emergencyRoutes");// 引入 emergency routes
const decisionRoutes = require("./routes/decisionRoutes");//引入決策 routes
const safetyRoutes = require("./routes/safetyRoutes");//引入安全檢查 routes
const gpsRoutes = require("./routes/gpsRoutes");//引入GPS routes
const aiGatewayRoutes = require("./routes/aiGatewayRoutes");// 引入 AI Gateway routes
const express = require("express"); //express 建立 backend server
const cors = require("cors"); //cors 允許前後端連接
require("dotenv").config(); //讓 Node.js 可以讀 .env「秘密資料」  


const app = express();// 🚀 建立 Express app



// 基礎測試 API
app.get("/", (req, res) => { //app.get("/") 建立測試 API
    res.json({              //res.json 回傳符合你規則的 JSON
        status: "success",
        message: "backend is running",
        data: {}
    });
});

// ======================================================
// 📩 LINE WEBHOOK（⭐ 新增重點：用來拿 userId）
// ======================================================


// middleware
app.use(cors());
app.use(express.json()); //express.json 讓 API 能收 JSON
app.use(express.urlencoded({ extended: true }));



// ======================================================
// 📩 LINE Webhook（核心入口）
// 👉 LINE 所有訊息都會打到這裡
// ======================================================
app.post("/webhook", (req, res) => {

    const events = req.body?.events;

    // 🧠 防呆：避免沒有 events
    if (!events || events.length === 0) {
        return res.sendStatus(200);
    }

    // 👉 取第一個事件（LINE 通常一次一個）
    const event = events[0];

    // 🧠 安全檢查：只處理 message 類型
    if (event.type === "message") {

        const userId = event.source?.userId; // 使用者 ID
        const text = event.message?.text; // 使用者訊息

        // 📌 這裡就是你專題核心入口
        console.log("📩 LINE MESSAGE RECEIVED");
        console.log("🧠 USER ID:", userId);
        console.log("💬 TEXT:", text);
    }

    // ⚠️ LINE 規定一定要回 200
    res.sendStatus(200);
});


app.use("/api", aiGatewayRoutes);
app.use("/api", gpsRoutes);
app.use("/api", safetyRoutes);
app.use("/api", decisionRoutes);
app.use("/api", emergencyRoutes);
app.use("/api", databaseRoutes);
app.use("/api", contactRoutes);
app.use("/api", navigationRoutes);
app.use("/api", sosRoutes);

// ======================================================
// 🧪 基礎測試 API（確認 server 是否活著）
// ======================================================
app.get("/", (req, res) => {

    res.json({
        status: "success",
        message: "backend is running",
        data: {}
    });
});


// 啟動 server
const PORT = 3000;

app.listen(PORT, () => {     //app.listen 啟動 server
    console.log(`Server running on port ${PORT}`);
});




