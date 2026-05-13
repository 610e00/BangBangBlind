# BangBangBlind
# AI之眼幫幫盲

## 開發前必讀
- CODING_RULES.md：命名規則和GitHub規範
- API_SPEC.md：所有模組的回傳格式

## 啟動方式

Python 後端：
cd python_backend
pip install -r requirements.txt
uvicorn main:app --host 0.0.0.0 --port 8000 --reload

Flutter 前端：
cd android_app
flutter pub get
flutter run

## 資料夾結構
android_app/        → Flutter前端
python_backend/
  ├── main.py       → FastAPI 主程式
  ├── obstacle/     → 避障
  ├── face/         → 人臉辨識
  ├── navigation/   → 導航＋緊急聯絡
  └── shared/       → 共用工具
