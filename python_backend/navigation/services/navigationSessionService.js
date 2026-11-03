// =========================================
// 真實導航 Session 管理（整合負責人新增）
// =========================================
// 這支負責：
// 1. （新增）先用 Google Places API 找出「離使用者目前位置最近」的實際地點，
//    解決「輸入 7-11 這種連鎖店名稱，到底要導去哪一家分店」的問題
// 2. 呼叫 Google Directions API 算出完整步行路線，並記錄預計到達時間（ETA）
// 3. 記住使用者目前走到第幾個路口指示
// 4. 每次收到新的 GPS 位置，判斷要不要換下一句指示、有沒有抵達
//
// ⚠️ 已知限制：目前整個專案還沒有登入/多使用者系統
// （ContactService 也是用同一種方式，固定 demo_user_id=1），
// 所以這裡先用「單一記憶體 session」，同一時間只能有一個人在導航。
// 之後如果要支援多人同時導航，要改成依 user_id 分開存多組 session。

const axios = require("axios");

// 多靠近才判定「已經走到這個路口」，避免 GPS 飄動造成誤判換句過快
// 2026-11-03：從 15 縮到 10 公尺，轉彎提示更接近路口（手機 GPS 誤差約 5～10 公尺，再小會抓不到）。
const STEP_ARRIVAL_RADIUS_METERS = 10;
// 萬一 GPS 沒剛好落在 10 公尺內：曾經靠近路口 25 公尺內，之後又離遠 6 公尺，視為已經走過路口，照樣換下一句
const NEAR_STEP_END_METERS = 25;
const PASSED_MARGIN_METERS = 6;
// 多靠近終點才算真正抵達
const DESTINATION_ARRIVAL_RADIUS_METERS = 15;

// 目前唯一一組導航進度（demo 版單一使用者，見上方說明）
let activeSession = null;

/**
 * 地球上兩點距離（公尺），Haversine 公式
 */
function distanceMeters(latitude1, longitude1, latitude2, longitude2) {

    const earthRadiusMeters = 6371000;
    const toRad = (deg) => (deg * Math.PI) / 180;

    const deltaLat = toRad(latitude2 - latitude1);
    const deltaLng = toRad(longitude2 - longitude1);

    const a =
        Math.sin(deltaLat / 2) ** 2 +
        Math.cos(toRad(latitude1)) *
            Math.cos(toRad(latitude2)) *
            Math.sin(deltaLng / 2) ** 2;

    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

    return earthRadiusMeters * c;
}

/**
 * 用 Google Places API（Nearby Search，rankby=distance）找出
 * 「離使用者目前位置最近」符合這個名稱/關鍵字的實際地點。
 *
 * 用途：使用者輸入「7-11」「全家」這種連鎖店名稱時，全台有上萬家分店，
 * 如果直接把文字丟給 Directions API，Google 會自己選一家（不保證最近），
 * 而且視障使用者完全不知道被導去了哪一家分店。
 *
 * 這裡先查出最近分店的名稱、地址、座標，導航時改用這個精確地點，
 * 並把分店名稱回傳給 App，讓 App 開始導航時先用語音講一次
 * 「正在為您導航至 ○○店」，使用者不用自己從清單裡選，但仍然知道去哪。
 *
 * 查無結果（例如使用者輸入的是完整地址、不是店名/地標）是正常情況，
 * 回傳 null，外層會自動退回「直接把文字交給 Directions API」的原始做法。
 */
async function resolveNearestPlace(destinationText, currentLatitude, currentLongitude) {

    const response = await axios.get(
        "https://maps.googleapis.com/maps/api/place/nearbysearch/json",
        {
            params: {
                location: `${currentLatitude},${currentLongitude}`,
                rankby: "distance",
                keyword: destinationText,
                language: "zh-TW",
                key: process.env.GOOGLE_MAPS_API_KEY
            }
        }
    );

    const placesData = response.data;

    if (placesData.status !== "OK" || !placesData.results || placesData.results.length === 0) {
        return null;
    }

    const results = placesData.results;

    // 2026-10-28：判斷使用者輸入的是「連鎖店」還是「單一地標」。
    // 連鎖店（7-11、全家）：附近會有好幾家同品牌 → 導去最近那家。
    // 單一地標（例如「101」「台北車站」）：附近查到的只是名稱剛好含關鍵字的
    // 不相干店家（例如 3 分鐘外的某間「101」），直接取最近的會導到錯的地方。
    // → 改用 Places「文字搜尋」，依知名度／相關度找最像使用者本意的地點。
    // 品牌 = 店名第一段（空白、括號、橫線前），例如「7-ELEVEN 興隆門市」→「7」，
    // 所以改用「去掉分店字樣後的前幾個字」比對：取前 4 個字（不足就全取）。
    const brandOf = (name) =>
        String(name || "")
            .replace(/[\s（(].*$/, "")
            .slice(0, 4);

    const topBrand = brandOf(results[0].name);
    const sameBrandCount = results
        .slice(0, 10)
        .filter((r) => brandOf(r.name) === topBrand).length;

    // 純數字（例如「101」）不可能是連鎖品牌名，且附近常有一堆店名剛好含該數字，
    // 一律當地標處理（2026-10-28 實測：輸入 101 被導到 3 分鐘外的小店）
    const isDigitsOnly = /^[0-9\s]+$/.test(String(destinationText || ""));
    const looksLikeChain = !isDigitsOnly && topBrand.length > 0 && sameBrandCount >= 3;

    if (looksLikeChain) {
        // rankby=distance 已經依距離排序，第一筆就是最近的
        const nearest = results[0];
        return {
            placeId: nearest.place_id,
            name: nearest.name,
            address: nearest.vicinity || "",
            kind: "chain"
        };
    }

    // 地標：文字搜尋
    const textResponse = await axios.get(
        "https://maps.googleapis.com/maps/api/place/textsearch/json",
        {
            params: {
                query: destinationText,
                location: `${currentLatitude},${currentLongitude}`,
                language: "zh-TW",
                region: "tw",
                key: process.env.GOOGLE_MAPS_API_KEY
            }
        }
    );

    const textData = textResponse.data;

    if (textData.status === "OK" && textData.results && textData.results.length > 0) {
        const best = textData.results[0];
        return {
            placeId: best.place_id,
            name: best.name,
            address: best.formatted_address || "",
            kind: "landmark"
        };
    }

    // 文字搜尋也沒結果：退回附近最近的
    const fallback = results[0];
    return {
        placeId: fallback.place_id,
        name: fallback.name,
        address: fallback.vicinity || "",
        kind: "nearby"
    };
}


/**
 * 2026-10-28 新增：找出「可能的目的地候選清單」，讓 App 在有疑慮時請使用者選。
 *
 * 回傳 { mode, candidates }
 *  - mode "resolved"：很明確，candidates[0] 就是要去的地方，直接導航
 *  - mode "choose"  ：有疑慮（例如輸入「101」，附近有「101麵食館」，
 *                     但大家熟知的「台北101」在很遠的地方），請使用者選
 *  - mode "none"    ：查不到任何地點（可能是完整地址），App 退回原本直接用文字導航
 *
 * specific=true：使用者從「常用地點」按鈕出發（例如暱稱「小七」、地址「7-11 XX店」），
 * 代表他指定的就是那一間，不可以被換成「最近的」，也不需要再問，
 * 直接用文字搜尋的第一筆。
 */
async function findCandidates(query, lat, lng, specific) {

    const toCandidate = (r, address) => {
        const loc = r.geometry && r.geometry.location;
        const meters = loc
            ? Math.round(distanceMeters(lat, lng, loc.lat, loc.lng))
            : null;
        return {
            placeId: r.place_id,
            name: r.name,
            address: address || "",
            distanceMeters: meters,
            distanceText: meters === null
                ? null
                : meters < 1000
                    ? `${meters} 公尺`
                    : `${(meters / 1000).toFixed(1)} 公里`
        };
    };

    // 文字搜尋（知名度／相關度排序，偏向使用者所在地區）
    let textResults = [];
    try {
        const t = await axios.get(
            "https://maps.googleapis.com/maps/api/place/textsearch/json",
            {
                params: {
                    query,
                    location: `${lat},${lng}`,
                    language: "zh-TW",
                    region: "tw",
                    key: process.env.GOOGLE_MAPS_API_KEY
                }
            }
        );
        if (t.data.status === "OK") {
            textResults = t.data.results.slice(0, 5)
                .map((r) => toCandidate(r, r.formatted_address));
        }
    } catch (e) { textResults = []; }

    if (specific) {
        return textResults.length > 0
            ? { mode: "resolved", candidates: [textResults[0]] }
            : { mode: "none", candidates: [] };
    }

    // 附近搜尋（依距離）
    let nearbyResults = [];
    try {
        const n = await axios.get(
            "https://maps.googleapis.com/maps/api/place/nearbysearch/json",
            {
                params: {
                    location: `${lat},${lng}`,
                    rankby: "distance",
                    keyword: query,
                    language: "zh-TW",
                    key: process.env.GOOGLE_MAPS_API_KEY
                }
            }
        );
        if (n.data.status === "OK") {
            nearbyResults = n.data.results.slice(0, 10)
                .map((r) => toCandidate(r, r.vicinity));
        }
    } catch (e) { nearbyResults = []; }

    if (nearbyResults.length === 0 && textResults.length === 0) {
        return { mode: "none", candidates: [] };
    }

    // 連鎖店（小七、全家、seven）：前10筆同品牌 ≥3 且非純數字 → 直接最近那家
    const brandOf = (name) =>
        String(name || "").replace(/[\s（(].*$/, "").slice(0, 4);
    const isDigitsOnly = /^[0-9\s]+$/.test(query);
    if (!isDigitsOnly && nearbyResults.length > 0) {
        const top = brandOf(nearbyResults[0].name);
        const same = nearbyResults.filter((c) => brandOf(c.name) === top).length;
        if (top.length > 0 && same >= 3) {
            return { mode: "resolved", candidates: [nearbyResults[0]] };
        }
    }

    // 其他：合併「最近的 2 個」與「文字搜尋最相關的 2 個」，去重後請使用者選
    const merged = [];
    const seen = new Set();
    const push = (c) => {
        if (c && !seen.has(c.placeId)) { seen.add(c.placeId); merged.push(c); }
    };
    nearbyResults.slice(0, 2).forEach(push);
    textResults.slice(0, 2).forEach(push);

    if (merged.length === 1) {
        return { mode: "resolved", candidates: merged };
    }

    // 2026-10-29：使用者輸入夠精準（≥3 字，例如「台北101」），
    // 文字搜尋第一名的名稱包含這串字，而且附近沒有「別的」地點名稱也包含它
    // （對比輸入「101」時，附近有「101麵食館」會有疑慮）→ 很明確，不必再問
    const normQ = String(query).replace(/\s/g, "").toLowerCase();
    const normN = (x) => String(x || "").replace(/\s/g, "").toLowerCase();
    const t0 = textResults[0];
    if (t0 && normQ.length >= 3 && normN(t0.name).includes(normQ) &&
        !nearbyResults.some((n) => n.placeId !== t0.placeId && normN(n.name).includes(normQ))) {
        return { mode: "resolved", candidates: [t0] };
    }

    // 有候選的名稱與使用者輸入「完全相同」（例如輸入「中原大學」）→ 很明確，不必再問
    const norm = (x) => String(x || "").replace(/\s/g, "").toLowerCase();
    const exact = merged.find((c) => norm(c.name) === norm(query));
    if (exact) {
        return { mode: "resolved", candidates: [exact] };
    }

    return { mode: "choose", candidates: merged.slice(0, 4) };
}

/**
 * 呼叫 Google Directions API，建立新的導航 session
 * @param {string} destinationText 目的地（地名、地址，或連鎖店名稱）
 * @param {number} currentLatitude 使用者目前緯度
 * @param {number} currentLongitude 使用者目前經度
 */
async function startSession(destinationText, currentLatitude, currentLongitude, chosenPlace) {

    const origin = `${currentLatitude},${currentLongitude}`;

    // 先試著解析成「最近的實際地點」，查詢失敗（例如金鑰沒開 Places API）
    // 不應該讓整個導航失敗，退回成原本直接用文字查路線的做法即可
    // 2026-10-28：App 已讓使用者選定地點（chosenPlace）時，直接用，不再自己猜
    let resolvedPlace = chosenPlace && chosenPlace.placeId ? chosenPlace : null;
    try {
        if (!resolvedPlace) resolvedPlace = await resolveNearestPlace(
            destinationText,
            currentLatitude,
            currentLongitude
        );
    } catch (placesError) {
        resolvedPlace = null;
    }

    const directionsDestination = resolvedPlace
        ? `place_id:${resolvedPlace.placeId}`
        : destinationText;

    const response = await axios.get(
        "https://maps.googleapis.com/maps/api/directions/json",
        {
            params: {
                origin,
                destination: directionsDestination,
                mode: "walking", // 視障者是步行導航，不是開車，一定要指定
                language: "zh-TW",
                key: process.env.GOOGLE_MAPS_API_KEY
            }
        }
    );

    const directionsData = response.data;

    if (directionsData.status !== "OK") {

        throw new Error(
            `Google Maps 回應異常：${directionsData.status}` +
            (directionsData.error_message ? `（${directionsData.error_message}）` : "")
        );
    }

    const route = directionsData.routes[0];
    const leg = route.legs[0];

    const steps = leg.steps.map((step) => ({
        instruction: cleanInstruction(step.html_instructions),
        endLocation: {
            latitude: step.end_location.lat,
            longitude: step.end_location.lng
        }
    }));

    activeSession = {
        destinationText,
        steps,
        currentStepIndex: 0,
        finalDestination: {
            latitude: leg.end_location.lat,
            longitude: leg.end_location.lng
        }
    };

    // leg.duration 是 Google 算好的「預計步行時間」（整段路線，只在開始導航時算一次）
    const etaSeconds = leg.duration ? leg.duration.value : null;
    const etaText = leg.duration ? leg.duration.text : null;
    // 2026-10-28：查不到「地點」時（例如輸入完整地址），改用 Google 回報的
    // 實際終點地址當名稱，讓使用者一定聽得到「要去哪裡」。
    const fallbackAddress = leg.end_address
        ? String(leg.end_address).replace(/^\d+/, "").replace(/^台灣/, "").trim()
        : null;
    const destinationName = resolvedPlace ? resolvedPlace.name : fallbackAddress;
    const destinationAddress = resolvedPlace ? resolvedPlace.address : (leg.end_address || null);
    const distanceText = leg.distance ? leg.distance.text : null;

    // buildStepResult 可能會在極少數情況下（起點就在終點旁）直接判定抵達、
    // 把 activeSession 清掉，所以上面要先把這次路線的資訊存成區域變數
    const stepResult = buildStepResult(currentLatitude, currentLongitude);

    return {
        ...stepResult,
        etaSeconds,
        etaText,
        destinationName,
        destinationAddress,
        distanceText,
        isNewSession: true
    };
}

/**
 * 收到新的 GPS 位置，判斷目前該播報哪一句指示
 */
function updatePosition(currentLatitude, currentLongitude) {

    if (!activeSession) {
        throw new Error("尚未開始導航，請先呼叫 startSession");
    }

    return {
        ...buildStepResult(currentLatitude, currentLongitude),
        isNewSession: false
    };
}

function buildStepResult(currentLatitude, currentLongitude) {

    const session = activeSession;

    const distanceToFinal = distanceMeters(
        currentLatitude,
        currentLongitude,
        session.finalDestination.latitude,
        session.finalDestination.longitude
    );

    if (distanceToFinal <= DESTINATION_ARRIVAL_RADIUS_METERS) {

        activeSession = null;

        return {
            instruction: "您已抵達目的地",
            distanceToNextCm: 0,
            arrived: true
        };
    }

    // 檢查目前位置是否已經靠近這一步的終點，該換下一句指示了
    // （用 while 而不是 if，是為了應付「GPS 訊號不穩，一次跳過好幾步」的狀況）
    while (session.currentStepIndex < session.steps.length - 1) {

        const currentStep = session.steps[session.currentStepIndex];

        const distanceToStepEnd = distanceMeters(
            currentLatitude,
            currentLongitude,
            currentStep.endLocation.latitude,
            currentStep.endLocation.longitude
        );

        if (session.minDistToStepEnd === undefined ||
            distanceToStepEnd < session.minDistToStepEnd) {
            session.minDistToStepEnd = distanceToStepEnd;
        }
        const passed =
            session.minDistToStepEnd <= NEAR_STEP_END_METERS &&
            distanceToStepEnd > session.minDistToStepEnd + PASSED_MARGIN_METERS;

        if (distanceToStepEnd <= STEP_ARRIVAL_RADIUS_METERS || passed) {
            session.currentStepIndex += 1;
            session.minDistToStepEnd = undefined;
        } else {
            break;
        }
    }

    const currentStep = session.steps[session.currentStepIndex];

    const distanceToStepEnd = distanceMeters(
        currentLatitude,
        currentLongitude,
        currentStep.endLocation.latitude,
        currentStep.endLocation.longitude
    );

    return {
        instruction: currentStep.instruction,
        distanceToNextCm: Math.round(distanceToStepEnd * 100),
        arrived: false,
        // 2026-10-28：這一步的終點座標，App 用來做「起步校正」
        // （比對使用者實際行走方向與路線方向，指出要向左或向右轉）
        nextLatitude: currentStep.endLocation.latitude,
        nextLongitude: currentStep.endLocation.longitude
    };
}

function clearSession() {
    activeSession = null;
}

function hasActiveSession() {
    return activeSession !== null;
}


/**
 * 2026-10-28：把 Google 的 html_instructions 轉成適合「唸出來」的純文字。
 * Google 會把附註放在 <div> 裡（例如「限制通行的道路」），原本直接刪標籤會
 * 變成「往西南朝協同街前進限制通行的道路」黏成一句，聽起來像路名。
 * 這裡在 <div> 前補上句號，並把「限制通行的道路」改成比較白話的提醒。
 */
function cleanInstruction(html) {
    let text = String(html || "")
        .replace(/<div[^>]*>/gi, "。")
        .replace(/<[^>]*>/g, "")
        .replace(/&nbsp;/g, " ")
        .replace(/\s+/g, " ")
        .replace(/。+/g, "。")
        .replace(/^。/, "")
        .trim();
    const original = text;
    // 2026-10-28：視障者不容易知道「東北／西南」是哪個方向，拿掉這類絕對方位詞，
    // 方向改由 App 的「起步校正」用實際行走方向判斷後說「向左／向右轉」。
    // 例：「往東北走大勇一街朝力行北街前進」→「走大勇一街朝力行北街前進」
    //     「往西南朝協同街前進」→「朝協同街前進」
    text = text.replace(
        /[往向朝](?:正)?(?:東北|東南|西北|西南|東|西|南|北)方?(?=走|朝|行|前進|，|。|$)/g,
        ""
    );
    if (!text.replace(/。/g, "").trim()) {
        text = original;
    }
    text = text.replace(/限制通行的道路/g, "此路段可能限制通行，請留意現場標示");
    return text;
}

module.exports = {
    startSession,
    findCandidates,
    updatePosition,
    clearSession,
    hasActiveSession,
    distanceMeters
};
