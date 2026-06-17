\# Navigation Module Backend



\## 資料庫模組完成內容



本模組使用 SQLite + better-sqlite3 建立資料庫，並提供 RESTful API 供 Android App 與其他模組整合。



\---



\## 技術架構



\- Node.js

\- Express

\- SQLite

\- better-sqlite3



\---



\### 資料表



\#### users



儲存使用者基本資訊



| 欄位           | 說明    |

| ------------ | ----- |

| user\_id      | 使用者ID |

| created\_time | 建立時間  |



\---



\#### emergency\_contact



儲存緊急聯絡人資料



| 欄位           | 說明     |

| ------------ | ------ |

| contact\_id   | 聯絡人ID  |

| user\_id      | 使用者ID  |

| contact\_name | 聯絡人姓名  |

| phone        | 電話     |

| relationship | 關係     |

| priority     | 通知優先順序 |



\---



\#### navigation\_records



儲存導航歷史紀錄



| 欄位               | 說明    |

| ---------------- | ----- |

| record\_id        | 紀錄ID  |

| user\_id          | 使用者ID |

| longitude        | 經度    |

| latitude         | 緯度    |

| destination\_name | 目的地   |

| created\_time     | 建立時間  |



\---



\#### SOS



儲存安全事件紀錄



| 欄位            | 說明    |

| ------------- | ----- |

| event\_id      | 事件ID  |

| user\_id       | 使用者ID |

| event\_type    | 事件類型  |

| latitude      | 緯度    |

| longitude     | 經度    |

| timestamp     | 發生時間  |

| action\_result | 處理結果  |



\---



\## API



\### User



建立使用者



```http

POST /api/user

```



取得所有使用者



```http

GET /api/user

```



\---



\### Contact



新增緊急聯絡人



```http

POST /api/contact

```



查詢指定使用者聯絡人



```http

GET /api/contact/:user\_id

```



\---



\### Navigation



新增導航紀錄



```http

POST /api/navigation

```



查詢指定使用者導航紀錄



```http

GET /api/navigation/:user\_id

```



\---



\### SOS



新增安全事件紀錄



```http

POST /api/sos

```



查詢指定使用者安全事件紀錄



```http

GET /api/sos/:user\_id

```



\---



\## 安裝方式



安裝套件：



```bash

npm install

```



建立 SQLite 資料庫：



```bash

node db/init-db.js

```



啟動伺服器：



```bash

node server.js

```



預設埠號：



```text

http://localhost:3000

```



\---



\## 測試結果



已完成：



SQLite 整合

users CRUD 測試

emergency\_contact CRUD 測試

navigation\_records CRUD 測試

SOS CRUD 測試

REST API 測試

PowerShell API 測試

LINE Push Notification 已完成



\---



\## LINE Demo 備註



Demo 階段所有 LINE 通知將統一傳送至開發者帳號。



為模擬實際情境，訊息內容仍會標示原始通知對象（如：媽媽、爸爸），以驗證系統是否正確取得緊急聯絡人資料。



\---

