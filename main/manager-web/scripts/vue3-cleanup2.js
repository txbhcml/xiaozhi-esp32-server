/**
 * 第二轮深度清理 - 处理遗漏的废弃写法
 *
 * 1. ::v-deep(.foo) → :deep(.foo)  (带括号形式)
 * 2. &::v-deep(.foo) → &:deep(.foo)
 * 3. ::v-deep { → :deep() {
 * 4. el-dialog custom-class="xxx" (无冒号) → class="xxx"
 * 5. 处理 v-on:xxx 残留形式
 */
const fs = require('fs');
const path = require('path');

const srcDir = path.resolve(__dirname, '../src');

function findVueFiles(dir) {
  let results = [];
  const items = fs.readdirSync(dir);
  for (const item of items) {
    const fullPath = path.join(dir, item);
    const stat = fs.statSync(fullPath);
    if (stat.isDirectory()) {
      results = results.concat(findVueFiles(fullPath));
    } else if (item.endsWith('.vue') || item.endsWith('.scss')) {
      results.push(fullPath);
    }
  }
  return results;
}

function migrateFile(filePath) {
  let content = fs.readFileSync(filePath, 'utf8');
  const original = content;
  const fileName = path.basename(filePath);

  // 1. ::v-deep(.foo) → :deep(.foo)  (含 & 前缀的情况也覆盖)
  content = content.replace(/::v-deep\(/g, ':deep(');

  // 2. ::v-deep { (无选择器) → :deep() {
  content = content.replace(/::v-deep(?=\s*\{)/g, ':deep()');

  // 3. ::v-deep 空格 + 选择器 (再保险一次)
  content = content.replace(/::v-deep\s+([^\s{,()]+)/g, ':deep($1)');

  // 4. el-dialog/el-drawer 上无冒号的 custom-class="xxx" → class="xxx"
  content = content.replace(
    /(<el(?:-dialog|-drawer)\b[^>]*?)\s+custom-class="([^"]*)"/g,
    '$1 class="$2"'
  );

  if (content !== original) {
    fs.writeFileSync(filePath, content, 'utf8');
    console.log(`✓ Cleaned: ${fileName}`);
    return true;
  }
  return false;
}

const files = findVueFiles(srcDir);
let migratedCount = 0;
for (const file of files) {
  if (migrateFile(file)) {
    migratedCount++;
  }
}
console.log(`\nDone! Cleaned ${migratedCount}/${files.length} files.`);
