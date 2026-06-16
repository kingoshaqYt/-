const fs = require('fs');

const path = 'app/src/main/java/com/example/MainActivity.kt';
let text = fs.readFileSync(path, 'utf8');

text = text.replace(/SettingsPanel\(title = "INTERFACE LANGUAGE SELECTION"\) \{[\s\S]*?\}\s*\}/, '');

fs.writeFileSync(path, text, 'utf8');
console.log("Done");
