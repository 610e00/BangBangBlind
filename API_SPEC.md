# API 規格文件

輸出格式

---

## 避障模組
類型：WebSocket
路徑：ws://localhost:8000/ws/obstacle
說明：app啟動自動連線，持續偵測推送

有障礙物：
{
  "status": "success",
  "message": "偵測到障礙物",
  "data": {
    "has_obstacle": true,
    "object_type": "機車",
    "direction": "left",
    "distance_cm": 120
  }
}

無障礙物：
{
  "status": "success",
  "message": "無障礙物",
  "data": {
    "has_obstacle": false,
    "object_type": null,
    "direction": null,
    "distance_cm": null
  }
}

direction 只能填這五個：
left / left-center / center / right-center / right

---

## 人臉辨識模組
類型：REST API
路徑：POST /face/recognize
說明：使用者說「這是誰」時觸發

認出來：
{
  "status": "success",
  "message": "辨識完成",
  "data": {
    "face_detected": true,
    "person_name": "王大明",
    "confidence": 0.92
  }
}

沒認出來：
{
  "status": "success",
  "message": "無法辨識",
  "data": {
    "face_detected": true,
    "person_name": null,
    "confidence": 0.31
  }
}

沒有偵測到臉：
{
  "status": "success",
  "message": "未偵測到人臉",
  "data": {
    "face_detected": false,
    "person_name": null,
    "confidence": null
  }
}

---

## 紅綠燈模組
類型：REST API
路徑：POST /traffic/detect
說明：例如使用者說「可以過馬路嗎」時觸發

偵測到紅綠燈：
{
  "status": "success",
  "message": "紅綠燈辨識完成",
  "data": {
    "light_detected": true,
    "light_color": "green",
    "seconds_remaining": 18,
    "is_safe_to_cross": true
  }
}

light_color 只能填：red / green
is_safe_to_cross 規則：
- green 且 seconds_remaining >= 15 → true
- green 且 seconds_remaining < 15  → false
- red                               → false

沒偵測到：
{
  "status": "success",
  "message": "未偵測到紅綠燈",
  "data": {
    "light_detected": false,
    "light_color": null,
    "seconds_remaining": null,
    "is_safe_to_cross": false
  }
}

---

## 導航模組
類型：REST API
路徑：POST /navigation/start
說明：使用者說「帶我去＿＿」時觸發

導航中：
{
  "status": "success",
  "message": "導航中",
  "data": {
    "instruction": "直走50公尺後左轉",
    "distance_to_next_cm": 5000,
    "arrived": false
  }
}

抵達：
{
  "status": "success",
  "message": "已抵達目的地",
  "data": {
    "instruction": "您已抵達目的地",
    "distance_to_next_cm": 0,
    "arrived": true
  }
}

---

## 緊急聯絡模組
類型：REST API
路徑：POST /emergency/trigger
說明：使用者求救或停留超過10分鐘未回應時觸發

{
  "status": "success",
  "message": "簡訊已發送",
  "data": {
    "sms_sent": true,
    "location_lat": 25.0478,
    "location_lng": 121.5318,
    "contact_name": "媽媽"
  }
}

---

## 音效優先級（整合用）

等級1 最高：紅燈警告 → 立即中斷所有聲音
等級2：distance_cm 小於 100 → 急促警示音＋震動
等級3：distance_cm 100到200 → 語音說出方向和障礙物
等級4：distance_cm 大於 200 → 短音效就好
等級5 最低：人臉辨識、導航語音

規則：
- 高等級出現時直接中斷低等級
- 使用者說話時所有輸出暫停
