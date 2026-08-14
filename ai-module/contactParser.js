const fs = require("fs");
const path = require("path");

const contactsPath = path.join(__dirname, "contacts.json");

// =========================================
// 讀取聯絡人
// =========================================

function loadContacts() {
    try {
        const contactsJson = fs.readFileSync(
            contactsPath,
            "utf8"
        );

        return JSON.parse(contactsJson);
    } catch (error) {
        console.error(
            "❌ 無法讀取 contacts.json:",
            error.message
        );

        return {};
    }
}

// =========================================
// 從語句中找出聯絡人
// =========================================

function extractContactName(text) {
    if (!text || typeof text !== "string") {
        return null;
    }

    const normalizedText = text.trim();
    const contacts = loadContacts();

    for (const contactName of Object.keys(contacts)) {
        if (contactName === "緊急") {
            continue;
        }

        if (normalizedText.includes(contactName)) {
            return contactName;
        }
    }

    const aliases = {
        "我媽": "媽媽",
        "我母親": "媽媽",
        "母親": "媽媽",
        "我爸": "爸爸",
        "我父親": "爸爸",
        "父親": "爸爸"
    };

    for (const alias of Object.keys(aliases)) {
        if (normalizedText.includes(alias)) {
            const contactName = aliases[alias];

            if (contacts[contactName]) {
                return contactName;
            }
        }
    }

    return null;
}

// =========================================
// 找聯絡人電話
// =========================================

function findContact(contactName) {
    if (!contactName) {
        return null;
    }

    const contacts = loadContacts();

    if (!contacts[contactName]) {
        return null;
    }

    return {
        name: contactName,
        phone: contacts[contactName]
    };
}

// =========================================
// 緊急服務解析
// =========================================

function resolveEmergencyService(text) {
    if (!text || typeof text !== "string") {
        return {
            success: false
        };
    }

    const normalizedText = text.trim();
    const emergencyKeywords = [
        "緊急電話",
        "緊急服務",
        "打119",
        "撥119",
        "打 119",
        "撥 119"
    ];

    const isEmergency = emergencyKeywords.some(
        keyword => normalizedText.includes(keyword)
    );

    if (!isEmergency) {
        return {
            success: false
        };
    }

    const contacts = loadContacts();

    if (!contacts["緊急"]) {
        return {
            success: false,
            message: "找不到緊急服務電話"
        };
    }

    return {
        success: true,
        type: "emergency_service",
        phone: contacts["緊急"],
        message: "準備撥打緊急服務"
    };
}

// =========================================
// 一般聯絡人解析
// =========================================

function resolveContact(text) {
    const contactName = extractContactName(text);

    if (!contactName) {
        return {
            success: false,
            message: "無法辨識要聯絡的人"
        };
    }

    const contact = findContact(contactName);

    if (!contact) {
        return {
            success: false,
            contact: contactName,
            message: `找不到「${contactName}」的聯絡資料`
        };
    }

    return {
        success: true,
        contact: contact.name,
        phone: contact.phone
    };
}

module.exports = {
    loadContacts,
    extractContactName,
    findContact,
    resolveContact,
    resolveEmergencyService
};
