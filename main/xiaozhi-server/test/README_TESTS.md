# Unit Tests Guide

This directory contains unit tests for the xiaozhi test page modules.

## Summary

- **Total Tests:** 13 unit tests
- **Test Files:** 2 browser-compatible test files
- **Test Runner:** Browser-based (no npm required)
- **Coverage:** Microphone detection, HTTP detection, Live2D actions, error handling

## Test Files

- `js/core/audio/recorder.test.browser.js` - Browser-compatible tests for microphone availability detection and HTTP non-localhost detection
- `js/core/mcp/tools.test.browser.js` - Browser-compatible tests for MCP tools and Live2D action execution

**Note:** The `.browser.js` versions work without any npm dependencies. They use a simple test framework built into `test-runner.html`.

## Running Tests

### Browser-based Test Runner (No npm required!)

1. Start a local server:
```bash
cd main/xiaozhi-server/test
python -m http.server 8007
```

2. Open `http://localhost:8007/test-runner.html` in your browser

3. Click "▶ Run All Tests" button

That's it! No npm, no package.json, no dependencies needed.

## Test Coverage

### `recorder.test.browser.js`
- ✅ `checkMicrophoneAvailability()` - Returns true when microphone is available
- ✅ `checkMicrophoneAvailability()` - Returns false when microphone is not available
- ✅ `checkMicrophoneAvailability()` - Returns false when browser doesn't support getUserMedia
- ✅ `isHttpNonLocalhost()` - Returns true for HTTP non-localhost access
- ✅ `isHttpNonLocalhost()` - Returns false for localhost
- ✅ `isHttpNonLocalhost()` - Returns false for 127.0.0.1
- ✅ `isHttpNonLocalhost()` - Returns false for private IP addresses
- ✅ `isHttpNonLocalhost()` - Returns false for HTTPS protocol

### `tools.test.browser.js`
- ✅ `executeMcpTool('live2d.smile')` - Executes FlickUp action
- ✅ `executeMcpTool('live2d.wave')` - Executes Tap action
- ✅ `executeMcpTool('live2d.action')` - Executes custom action
- ✅ `executeMcpTool()` - Handles missing Live2D manager gracefully
- ✅ `executeMcpTool()` - Handles unknown tools gracefully

## Writing New Tests

When adding new functionality, create a `.browser.js` test file that follows these patterns:

```javascript
// your-module.test.browser.js
import { yourFunction } from './your-module.js';

describe('Your Feature', () => {
    beforeEach(() => {
        // Setup mocks and reset state
        vi.clearAllMocks();
    });

    test('should do something', () => {
        // Arrange
        const input = 'test';
        
        // Act
        const result = yourFunction(input);
        
        // Assert
        expect(result).toBe('expected');
    });
});
```

## Mocking Guidelines

- Use `vi.fn()` for function mocks
- Use `vi.fn().mockResolvedValue(value)` for async mocks that resolve
- Use `vi.fn().mockRejectedValue(error)` for async mocks that reject
- Use `vi.clearAllMocks()` in `beforeEach` to reset state
- Mock browser APIs (`navigator`, `window.location`, `localStorage`, `fetch`)
- Mock DOM elements when needed (`document.getElementById`, etc.)

## Available Test Functions

The browser test framework provides:
- `describe(name, fn)` - Define a test suite
- `test(name, fn)` - Define a test case
- `beforeEach(fn)` - Run before each test
- `afterEach(fn)` - Run after each test
- `expect(actual)` - Assertion object with:
  - `.toBe(expected)` - Strict equality
  - `.toHaveBeenCalled()` - Function was called
  - `.toHaveBeenCalledWith(...args)` - Function was called with specific args
  - `.toContain(substring)` - String contains substring
- `vi.fn(impl?)` - Create a mock function
- `vi.clearAllMocks()` - Clear all mocks

## Notes

- Tests use ES modules (`import`/`export`)
- Tests run directly in the browser (no Node.js needed)
- No npm dependencies required - everything is self-contained
- The test runner (`test-runner.html`) includes a simple test framework
- Tests are automatically loaded when you click "Run All Tests"
