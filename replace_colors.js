const fs = require('fs');

let content = fs.readFileSync('app/src/main/java/com/example/MainActivity.kt', 'utf-8');

// Replace Color.White, with if (state.isDarkTheme) Color.White else Color.Black
content = content.replace(/tint = Color\.White,/g, 'tint = if (state.isDarkTheme) Color.White else Color.Black,');

// Replace Color.Black with if (state.isDarkTheme) Color.White else Color.Black, except if it's already inside an if block
// Actually, let's just make it simpler for a few specific ones.
content = content.replace(/tint = Color\.Black/g, 'tint = if (state.isDarkTheme) Color.White else Color.Black');

fs.writeFileSync('app/src/main/java/com/example/MainActivity.kt', content);
console.log('done');
