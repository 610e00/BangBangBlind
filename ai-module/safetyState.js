let state = "normal";

function updateState(level) {

    // 🔴 最高危險
    if (level === "danger") {
        state = "danger";
    }

    // 🟡 注意
    else if (level === "caution") {
        state = "caution";
    }

    // 👀 一般注意
    else if (level === "attention") {
        state = "attention";
    }

    // 🟢 安全
    else if (level === "safe") {
        state = "normal";
    }

    return state;
}

function getState() {
    return state;
}

module.exports = {
    updateState,
    getState
};