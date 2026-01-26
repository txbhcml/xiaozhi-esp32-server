# 快速开始 - 浏览器测试（无需 npm！）

## 3 步运行测试

1. **启动本地服务器：**
   ```bash
   cd main/xiaozhi-server/test
   python -m http.server 8007
   ```

2. **在浏览器中打开：**
   ```
   http://localhost:8007/test-runner.html
   ```

3. **点击 "▶ Run All Tests"（运行所有测试）**

就这么简单！无需 npm，无需 package.json，无需任何依赖。

## 测试内容

- ✅ 麦克风可用性检测（3 个测试）
- ✅ HTTP 非本地访问检测（5 个测试）
- ✅ Live2D 动作执行（5 个测试）
- ✅ 错误处理和边界情况

**总计：13 个单元测试**（8 个录音器测试 + 5 个工具测试）

## 测试文件

- `js/core/audio/recorder.test.browser.js` - 音频/录音器测试
- `js/core/mcp/tools.test.browser.js` - MCP 工具和 Live2D 测试

## 故障排除

**测试无法运行？**
- 确保使用本地服务器（不要使用 `file://`）
- 检查浏览器控制台是否有错误
- 确保所有 `.js` 文件都可以访问

**部分测试失败？**
- 查看测试结果中的错误信息
- 验证模拟设置是否正确
- 检查浏览器控制台获取详细错误信息
