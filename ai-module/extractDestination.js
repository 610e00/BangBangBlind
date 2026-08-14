function extractDestination(text) {

    if (!text || typeof text !== "string") {
        return null;
    }

    let destination = text.trim();

    const prefixes = [
        "請帶我去",
        "請帶我到",
        "帶我前往",
        "帶我去",
        "帶我到",
        "我想去",
        "我要去",
        "導航到",
        "導航去",
        "導航前往",
        "前往"
    ];

    let matched = false;

    for (const prefix of prefixes) {

        if (destination.startsWith(prefix)) {

            destination = destination
                .slice(prefix.length)
                .trim();

            matched = true;
            break;
        }
    }

    // 沒有符合導航語句，不應該當成目的地
    if (!matched || !destination) {
        return null;
    }

    return destination;
}

module.exports = {
    extractDestination
};