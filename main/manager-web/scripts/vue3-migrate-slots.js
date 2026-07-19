/**
 * 处理非 template 标签上的 slot="xxx" 属性
 * 将 <XxxElement slot="yyy" attrs>...</XxxElement> 转换为
 * <template #yyy><XxxElement attrs>...</XxxElement></template>
 *
 * 仅处理匹配到的简单 case（开始标签 + 对应结束标签）。
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

/**
 * 在文件内容中找到匹配开始标签的结束标签位置
 * @param {string} content 文件内容
 * @param {number} startTagStart 开始标签起始位置（< 的位置）
 * @param {string} tagName 标签名
 * @returns {number} 结束标签结束位置（含 >），-1 表示未找到
 */
function findMatchingEndTag(content, startTagStart, tagName) {
  // 先找到开始标签的结束位置 >
  let i = startTagStart + 1 + tagName.length;
  let selfClosing = false;
  while (i < content.length) {
    if (content[i] === '>') {
      if (content[i - 1] === '/') {
        selfClosing = true;
      }
      break;
    }
    i++;
  }
  if (i >= content.length) return -1;
  if (selfClosing) {
    return i + 1;
  }

  // 从 i+1 开始查找匹配的结束标签
  let depth = 1;
  const openTagRe = new RegExp(`<${tagName}\\b`, 'g');
  const closeTagRe = new RegExp(`</${tagName}>`, 'g');
  openTagRe.lastIndex = i + 1;
  closeTagRe.lastIndex = i + 1;

  while (depth > 0) {
    const openMatch = openTagRe.exec(content);
    const closeMatch = closeTagRe.exec(content);
    if (!closeMatch) return -1;
    if (openMatch && openMatch.index < closeMatch.index) {
      depth++;
    } else {
      depth--;
      if (depth === 0) {
        return closeMatch.index + closeMatch[0].length;
      }
    }
  }
  return -1;
}

function migrateFile(filePath) {
  let content = fs.readFileSync(filePath, 'utf8');
  const original = content;
  const fileName = path.basename(filePath);

  // 匹配 <tagName slot="slotName" ...> 模式
  // 排除 <template
  const slotAttrRe = /<([a-zA-Z][\w-]*)\s+([^>]*?)\bslot="([\w-]+)"([^>]*?)>/g;
  let match;
  const replacements = [];

  while ((match = slotAttrRe.exec(content)) !== null) {
    const fullMatch = match[0];
    const tagName = match[1];
    const beforeSlot = match[2];
    const slotName = match[3];
    const afterSlot = match[4];

    if (tagName === 'template') continue; // 跳过 template（已处理）

    const startTagStart = match.index;
    const endPos = findMatchingEndTag(content, startTagStart, tagName);
    if (endPos === -1) {
      console.warn(`  ! Cannot find end tag for <${tagName} slot="${slotName}"> in ${fileName}, skipping`);
      continue;
    }

    // 构造新的开始标签（移除 slot="xxx"）
    const newStartTag = `<${tagName}${beforeSlot ? ' ' + beforeSlot.trim() : ''}${afterSlot ? ' ' + afterSlot.trim() : ''}>`.replace(/\s+/g, ' ').replace(/\s+>/g, '>');

    replacements.push({
      startTagStart,
      startTagEnd: match.index + fullMatch.length,
      endPos,
      newStartTag,
      slotName,
    });
  }

  // 从后向前替换，避免位置变化
  replacements.reverse();
  for (const r of replacements) {
    const before = content.substring(0, r.startTagStart);
    const startTagEnd = r.startTagEnd;
    const innerAndEndTag = content.substring(startTagEnd, r.endPos);
    const after = content.substring(r.endPos);
    content = before + `<template #${r.slotName}>` + r.newStartTag + innerAndEndTag + `</template>` + after;
  }

  if (content !== original) {
    fs.writeFileSync(filePath, content, 'utf8');
    console.log(`✓ Migrated slots: ${fileName}`);
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
