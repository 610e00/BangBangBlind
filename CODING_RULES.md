# 開發規範

## 命名規則
- Python 用底線：has_obstacle, distance_cm
- Flutter 用駝峰：hasObstacle, distanceCm
- JSON key 用底線：全部小寫

## 禁止命名
data, temp, value, test（看不出用途的名字不可以用）

## 資料型別規則
- 數字就是數字：120 不可以寫成 "120"
- 布林值不加引號：true / false 不可以寫成 "true" / "false"
- 距離單位統一用 cm

## GitHub 規則
- 不可以修改別人的資料夾
- commit 訊息要寫清楚，禁止只寫「update」或「修改」
- 一個功能建一個資料夾

## 統一回傳結構
成功：
{
  "status": "success",
  "message": "說明",
  "data": {}
}

失敗：
{
  "status": "error",
  "message": "錯誤原因",
  "data": null
}
