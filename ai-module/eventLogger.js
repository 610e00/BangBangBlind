const eventBus = require("./events/eventBus");

eventBus.on("navigation", navigationEvent => {
    console.log("\n📡 EVENT → Navigation");
    console.log(navigationEvent);
});

eventBus.on("emergency", emergencyEvent => {
    console.log("\n📡 EVENT → Emergency");
    console.log(emergencyEvent);
});

eventBus.on("safety", safetyEvent => {
    console.log("\n📡 EVENT → Safety");
    console.log(safetyEvent);
});

eventBus.on("stop", stopEvent => {
    console.log("\n📡 EVENT → Stop");
    console.log(stopEvent);
});

console.log("📡 Event Logger Ready");
