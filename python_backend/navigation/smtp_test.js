// 寄信測試：node smtp_test.js
// 用 .env 的 SMTP_USER / SMTP_PASS，寄一封測試信給自己，印出成功或失敗原因（不會印出密碼）
require("dotenv").config();
const nodemailer = require("nodemailer");
const user = process.env.SMTP_USER, pass = process.env.SMTP_PASS;
console.log("SMTP_USER:", user ? user : "（沒設定）");
console.log("SMTP_PASS:", pass ? `已設定（${pass.length} 個字元）` : "（沒設定）");
if (!user || !pass) process.exit(1);
nodemailer
  .createTransport({ service: "gmail", auth: { user, pass } })
  .sendMail({ from: user, to: user, subject: "幫幫盲寄信測試", text: "收到這封代表後端可以寄信。" })
  .then(() => console.log("OK：寄出成功，請去收件匣（也看垃圾郵件）"))
  .catch((e) => console.log("FAIL：", e.message));
