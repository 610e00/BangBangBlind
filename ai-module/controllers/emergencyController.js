const {
    resolveContact,
    resolveEmergencyService
} = require("../contactParser");

function handleEmergency(userText) {
    const emergencyService = resolveEmergencyService(userText);

    if (emergencyService.success) {
        return {
            success: true,
            action: "call",
            type: "emergency_service",
            phone: emergencyService.phone,
            message: emergencyService.message
        };
    }

    const contactResult = resolveContact(userText);

    if (contactResult.success) {
        return {
            success: true,
            action: "call",
            type: "contact",
            contact: contactResult.contact,
            phone: contactResult.phone
        };
    }

    return {
        success: false,
        action: "unknown_contact",
        message: contactResult.message || "無法辨識要聯絡的人"
    };
}

module.exports = {
    handleEmergency
};
