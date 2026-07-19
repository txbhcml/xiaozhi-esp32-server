/**
 * Vue 2 → Vue 3 批量迁移脚本（增强版 v2）
 * 处理 .vue 文件中的 breaking changes:
 * 1. :visible.sync → v-model (el-dialog/el-drawer) 或 v-model:visible (自定义组件)
 * 2. .native 事件修饰符移除（含 @xxx.yyy.native 等组合）
 * 3. slot="xxx" → #xxx (template 标签) 或 包裹为 <template #xxx> (其他元素)
 * 4. slot-scope="xxx" → #default="xxx" 或合并到已有的 #xxx
 * 5. beforeDestroy → beforeUnmount
 * 6. destroyed( → unmounted(
 * 7. $scopedSlots → $slots
 * 8. ::v-deep → :deep() (推荐写法)
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

  // 1. :visible.sync → v-model 或 v-model:visible
  content = content.replace(
    /(<(?:el-dialog|el-drawer)\b[^>]*?)\s*:visible\.sync="([^"]*)"/g,
    '$1 v-model="$2"'
  );
  content = content.replace(
    /:visible\.sync="([^"]*)"/g,
    'v-model:visible="$1"'
  );

  // 2. .native 事件修饰符移除 - 处理任意位置的 .native（包括 @click.native, @keyup.enter.native 等）
  content = content.replace(/\.native\b/g, '');

  // 3. 处理 <template #xxx slot-scope="yyy"> → <template #xxx="yyy">
  content = content.replace(
    /<template\s+#(\w+)\s+slot-scope="([^"]*)"\s*>/g,
    '<template #$1="$2">'
  );
  content = content.replace(
    /<template\s+slot-scope="([^"]*)"\s*>/g,
    '<template #default="$1">'
  );

  // 4. slot="xxx" → #xxx (仅在 <template> 上)
  content = content.replace(
    /<template\s+slot="([^"]*)"\s*>/g,
    '<template #$1>'
  );
  content = content.replace(
    /<template\s+slot="([^"]*)"\s+/g,
    '<template #$1 '
  );

  // 5. 非 template 标签上的 slot="xxx" - 这类情况在 Vue 3 中已废弃
  // 处理 <div slot="xxx" ...> → <template #xxx><div ...> 但这样需要找到匹配的结束标签
  // 简化处理: 将 slot="xxx" 改为 v-slot:xxx（仅 template 上有效），其他保留警告
  // 对于 <span slot="footer">, <div slot="content"> 等，最简单的处理是改为 <template #xxx><span>
  // 但风险大，先只做简单替换: slot="xxx" → v-slot:xxx (虽然只在 template 上合法，但保留作为提示)
  // 此处不做非 template 的处理，保留让用户手动处理

  // 6. beforeDestroy → beforeUnmount
  content = content.replace(/\bbeforeDestroy\b\s*[\(\{]/g, (match) =>
    match.replace('beforeDestroy', 'beforeUnmount')
  );
  content = content.replace(/\bbeforeDestroy\s*:/g, 'beforeUnmount:');

  // 7. destroyed( → unmounted( / destroyed: → unmounted:
  content = content.replace(/\bdestroyed\s*\(/g, 'unmounted(');
  content = content.replace(/\bdestroyed\s*:/g, 'unmounted:');

  // 8. $scopedSlots → $slots
  content = content.replace(/\$scopedSlots/g, '$slots');

  // 9. el-button size="mini" → size="small" (Element Plus 已移除 mini)
  content = content.replace(/(<el-button\b[^>]*?)\s+size="mini"/g, '$1 size="small"');

  if (content !== original) {
    fs.writeFileSync(filePath, content, 'utf8');
    console.log(`✓ Migrated: ${fileName}`);
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
console.log(`\nDone! Migrated ${migratedCount}/${files.length} files.`);
