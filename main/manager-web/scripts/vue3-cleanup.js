/**
 * Element Plus 全面兼容性清理脚本
 * 彻底清理已废弃但仍可用的写法
 *
 * 1. ::v-deep .selector → :deep(.selector)
 * 2. >>> .selector → :deep(.selector)
 * 3. /deep/ .selector → :deep(.selector)
 * 4. <el-button type="text"> → <el-button link> (Element Plus 已废弃 type="text")
 * 5. <el-dialog :custom-class="xxx"> → <el-dialog :class="xxx"> 或 class="xxx"
 * 6. size="mini" → size="small" (Element Plus 已移除 mini)
 * 7. el-pagination 上的 layout 属性变更（保留以观察）
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
    } else if (item.endsWith('.vue')) {
      results.push(fullPath);
    }
  }
  return results;
}

function migrateFile(filePath) {
  let content = fs.readFileSync(filePath, 'utf8');
  const original = content;
  const fileName = path.basename(filePath);

  // 1. ::v-deep .selector → :deep(.selector)
  // 处理 ::v-deep 后跟空格再跟选择器的情况
  content = content.replace(/::v-deep\s+([^\s{,]+)/g, ':deep($1)');
  // 处理 ::v-deep 直接后跟 { 的情况（无选择器，理论上不存在）
  content = content.replace(/::v-deep(?=\s*\{)/g, ':deep()');

  // 2. /deep/ .selector → :deep(.selector)
  content = content.replace(/\/deep\/\s+([^\s{,]+)/g, ':deep($1)');
  content = content.replace(/\/deep\/(?=\s*\{)/g, ':deep()');

  // 3. >>> .selector → :deep(.selector)
  content = content.replace(/>>>\s+([^\s{,]+)/g, ':deep($1)');

  // 4. el-button type="text" → link
  // 匹配 <el-button ... type="text" ...>
  content = content.replace(
    /(<el-button\b[^>]*?)\s+type="text"/g,
    '$1 link'
  );

  // 5. el-dialog/el-drawer :custom-class="xxx" → class="xxx" 或 :class="xxx"
  // 如果是静态字符串 custom-class="foo" → class="foo"
  content = content.replace(
    /(<el(?:-dialog|-drawer)\b[^>]*?)\s+custom-class="([^"]*)"/g,
    '$1 class="$2"'
  );
  // 动态绑定 :custom-class="xxx" → :class="xxx"
  content = content.replace(
    /(<el(?:-dialog|-drawer)\b[^>]*?)\s+:custom-class="([^"]*)"/g,
    '$1 :class="$2"'
  );

  // 6. size="mini" → size="small" (Element Plus 已移除 mini)
  // 仅对 Element Plus 组件生效，但全局替换也可（mini 在 Vue 3 中普遍无意义）
  content = content.replace(/\ssize="mini"/g, ' size="small"');

  // 7. el-tag 的 type="info" 等仍然支持，无需改

  // 8. el-link 的 :underline 在 Element Plus 仍支持

  // 9. el-cascader 的 expandTrigger 等 props 大部分仍兼容

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
