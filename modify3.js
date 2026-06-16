const fs = require('fs');

const path = 'app/src/main/java/com/example/ui/ReclaimViewModel.kt';
let text = fs.readFileSync(path, 'utf8');

text = text.replace(/val language: LanguageDictionary\.Language = LanguageDictionary\.Language\.ENGLISH,/g, '');
text = text.replace(/fun changeLanguage\(lang: LanguageDictionary\.Language\s*(?:,\s*context: android\.content\.Context\?\s*=\s*null)?\)\s*\{[\s\S]*?showToast\("LANGUAGE SWITCHED SUCCESSFULLY", "SUCCESS"\)\s*\}/, '');
text = text.replace(/fun initLanguage\(context: android\.content\.Context\)\s*\{[\s\S]*?\}\s*catch \(e: Exception\) \{\}\s*\}/, '');
text = text.replace(/initLanguage\(context\)\n/g, '');

fs.writeFileSync(path, text, 'utf8');
console.log("Done");
