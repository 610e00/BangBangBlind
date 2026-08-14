const { evaluateRisk } = require("../riskEngine");

function handleSafety(obstacle, light, face) {

    const result = evaluateRisk(
        obstacle,
        light,
        face
    );

    return result;
}

module.exports = { handleSafety };