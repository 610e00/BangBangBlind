# BangBangBlind
# 幫幫盲

## 開發規則

###1.命名規則
- Android：camelCase（例：cameraStatus）
- Python：snake_case（例：distance_cm）
- 禁止使用：data, temp, value, test(看不出具體運用)
------ 
###2.回傳格式統一）
Python回傳資料長這樣：
{
  "status": "success",
  "message": "說明",
  "data": {}
}

------
###3.常用欄位
- has_obstacle（是否有障礙物）
- distance_cm（距離）
- direction（left/center/right）
- object_type（物件類型）
------
###4.規則
- JSON用小寫、兩個單字用底線分開(例如:apple_juice)
- 數字不能變字串（120 ≠ "120"）
- true/false 不可加引號
- 距離單位用 cm
---
###5.GitHub
- 一種功能建一個資料夾，我先建了一個主功能(避障)的資料夾，其他請自行建立
- android_app→android頁面
- python_backend → python避障
- 不要改別人的資料夾

