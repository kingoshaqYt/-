const fs = require('fs');
const path = 'app/src/main/java/com/example/MainActivity.kt';
let text = fs.readFileSync(path, 'utf8');

// Undo the shadow part
text = text.replace(/\.shadow\(if \(state\.isDarkTheme\) 4\.dp else 8\.dp, RoundedCornerShape\(16\.dp\), ambientColor = if\(state\.isDarkTheme\) Color\.Black else Color\.Gray, spotColor = if\(state\.isDarkTheme\) Color\.Black else Color\.Gray\)\.background\(if \(state\.isDarkTheme\) Color\(0xFF1E1E1E\) else Color\.White, RoundedCornerShape\(16\.dp\)\)/g, '');

fs.writeFileSync(path, text, 'utf8');
console.log("Done");
