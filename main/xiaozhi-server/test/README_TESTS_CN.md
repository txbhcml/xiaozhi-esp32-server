# 单元测试指南

本目录包含 xiaozhi 测试页面模块的单元测试。

## 摘要

- **测试总数：** 13 个单元测试
- **测试文件：** 2 个浏览器兼容的测试文件
- **测试运行器：** 基于浏览器（无需 npm）
- **测试覆盖：** 麦克风检测、HTTP 检测、Live2D 动作、错误处理

## 测试文件

- `js/core/audio/recorder.test.browser.js` - 浏览器兼容的麦克风可用性检测和 HTTP 非本地访问检测测试
- `js/core/mcp/tools.test.browser.js` - 浏览器兼容的 MCP 工具和 Live2D 动作执行测试

**注意：** `.browser.js` 版本无需任何 npm 依赖。它们使用内置在 `test-runner.html` 中的简单测试框架。

## 运行测试

### 基于浏览器的测试运行器（无需 npm！）

1. 启动本地服务器：
```bash
cd main/xiaozhi-server/test
python -m http.server 8007
```

2. 在浏览器中打开 `http://localhost:8007/test-runner.html`

3. 点击 "▶ Run All Tests"（运行所有测试）按钮

就这么简单！无需 npm，无需 package.json，无需任何依赖。

## 测试覆盖

### `recorder.test.browser.js`
- ✅ `checkMicrophoneAvailability()` - 当麦克风可用时返回 true
- ✅ `checkMicrophoneAvailability()` - 当麦克风不可用时返回 false
- ✅ `checkMicrophoneAvailability()` - 当浏览器不支持 getUserMedia 时返回 false
- ✅ `isHttpNonLocalhost()` - 对于 HTTP 非本地访问返回 true
- ✅ `isHttpNonLocalhost()` - 对于 localhost 返回 false
- ✅ `isHttpNonLocalhost()` - 对于 127.0.0.1 返回 false
- ✅ `isHttpNonLocalhost()` - 对于私有 IP 地址返回 false
- ✅ `isHttpNonLocalhost()` - 对于 HTTPS 协议返回 false

### `tools.test.browser.js`
- ✅ `executeMcpTool('live2d.smile')` - 执行 FlickUp 动作
- ✅ `executeMcpTool('live2d.wave')` - 执行 Tap 动作
- ✅ `executeMcpTool('live2d.action')` - 执行自定义动作
- ✅ `executeMcpTool()` - 优雅处理缺失的 Live2D 管理器
- ✅ `executeMcpTool()` - 优雅处理未知工具

## 编写新测试

添加新功能时，创建一个遵循以下模式的 `.browser.js` 测试文件：

```javascript
// your-module.test.browser.js
import { yourFunction } from './your-module.js';

describe('您的功能', () => {
    beforeEach(() => {
        // 设置模拟并重置状态
        vi.clearAllMocks();
    });

    test('应该执行某些操作', () => {
        // 准备
        const input = 'test';
        
        // 执行
        const result = yourFunction(input);
        
        // 断言
        expect(result).toBe('expected');
    });
});
```

## 模拟指南

- 使用 `vi.fn()` 创建函数模拟
- 使用 `vi.fn().mockResolvedValue(value)` 创建解析的异步模拟
- 使用 `vi.fn().mockRejectedValue(error)` 创建拒绝的异步模拟
- 在 `beforeEach` 中使用 `vi.clearAllMocks()` 重置状态
- 模拟浏览器 API（`navigator`、`window.location`、`localStorage`、`fetch`）
- 需要时模拟 DOM 元素（`document.getElementById` 等）

## 可用的测试函数

浏览器测试框架提供：
- `describe(name, fn)` - 定义测试套件
- `test(name, fn)` - 定义测试用例
- `beforeEach(fn)` - 在每个测试之前运行
- `afterEach(fn)` - 在每个测试之后运行
- `expect(actual)` - 断言对象，包含：
  - `.toBe(expected)` - 严格相等
  - `.toHaveBeenCalled()` - 函数已被调用
  - `.toHaveBeenCalledWith(...args)` - 函数已使用特定参数调用
  - `.toContain(substring)` - 字符串包含子字符串
- `vi.fn(impl?)` - 创建模拟函数
- `vi.clearAllMocks()` - 清除所有模拟

## 注意事项

- 测试使用 ES 模块（`import`/`export`）
- 测试直接在浏览器中运行（无需 Node.js）
- 无需 npm 依赖 - 所有内容都是自包含的
- 测试运行器（`test-runner.html`）包含一个简单的测试框架
- 点击 "Run All Tests"（运行所有测试）时自动加载测试
