/**
 * Vue 3 迁移 - 替换 this.$set 为直接赋值
 *
 * this.$set(obj, key, value) → obj[key] = value
 * this.$set(arr, index, value) → arr[index] = value  (Vue 3 中数组的直接赋值也是响应式的)
 *
 * Vue 3 使用 Proxy 实现响应式，不再需要 $set/$delete
 */
const fs = require('fs');
const path = require('path');

const srcDir = path.resolve(__dirname, '../src');

function findVueJsFiles(dir) {
  let results = [];
  const items = fs.readdirSync(dir);
  for (const item of items) {
    const fullPath = path.join(dir, item);
    const stat = fs.statSync(fullPath);
    if (stat.isDirectory()) {
      results = results.concat(findVueJsFiles(fullPath));
    } else if (item.endsWith('.vue') || item.endsWith('.js')) {
      results.push(fullPath);
    }
  }
  return results;
}

function migrateFile(filePath) {
  let content = fs.readFileSync(filePath, 'utf8');
  const original = content;
  const fileName = path.basename(filePath);

  // 替换 this.$set(obj, key, value) → obj[key] = value
  // 匹配模式: this.$set(target, key, value)
  // key 可以是字符串字面量 'xxx' 或 "xxx"，也可以是变量
  // value 可以是任意表达式

  // 处理多行的 this.$set 调用
  content = content.replace(
    /this\.\$set\s*\(\s*([^,]+?)\s*,\s*(['"][\w.-]+['"]|\w[\w.-]*)\s*,\s*([\s\S]*?)\s*\)/g,
    (match, target, key, value) => {
      // 清理 value 中的换行和多余空格
      const cleanValue = value.replace(/\s+/g, ' ').trim();
      // 如果 key 是字符串字面量，转为点号访问（如果合法标识符）或方括号
      if (/^['"][\w$]+['"]$/.test(key)) {
        const prop = key.slice(1, -1);
        if (/^[a-zA-Z_$][a-zA-Z0-9_$]*$/.test(prop)) {
          return `${target.trim()}.${prop} = ${cleanValue}`;
        }
      }
      return `${target.trim()}[${key.trim()}] = ${cleanValue}`;
    }
  );

  if (content !== original) {
    fs.writeFileSync(filePath, content, 'utf8');
    console.log(`✓ Migrated: ${fileName}`);
    return true;
  }
  return false;
}

const files = findVueJsFiles(srcDir);
let migratedCount = 0;
for (const file of files) {
  if (migrateFile(file)) {
    migratedCount++;
  }
}
console.log(`\nDone! Migrated ${migratedCount}/${files.length} files.`);
