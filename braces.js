const fs = require('fs');
const p = 'C:/Users/valen/Downloads/kpkn-fit-(beta-test)/android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/SessionEditorScreen.kt';
const lines = fs.readFileSync(p, 'utf8').split('\n');
let d = 0;
for (let i = 711; i