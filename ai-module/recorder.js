const mic = require("mic");
const fs = require("fs");

function recordAudio(outputFile = "audio.wav") {

    return new Promise((resolve) => {

        console.log("🎤 開始錄音...（5秒）");

        const micInstance = mic({
            rate: "16000",
            channels: "1",
            fileType: "wav"
        });

        const output = fs.createWriteStream(outputFile);
        const micInputStream = micInstance.getAudioStream();

        micInputStream.pipe(output);

        micInstance.start();

        setTimeout(() => {
            micInstance.stop();
            console.log("⏹ 錄音結束");
            resolve(outputFile);
        }, 5000);
    });
}

module.exports = { recordAudio };