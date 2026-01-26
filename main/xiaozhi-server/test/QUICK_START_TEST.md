# Quick Start - Browser Tests (No npm required!)

## Run Tests in 3 Steps

1. **Start a local server:**
   ```bash
   cd main/xiaozhi-server/test
   python -m http.server 8007
   ```

2. **Open in browser:**
   ```
   http://localhost:8007/test-runner.html
   ```

3. **Click "▶ Run All Tests"**

That's it! No npm, no package.json, no dependencies needed.

## What Gets Tested?

- ✅ Microphone availability detection (3 tests)
- ✅ HTTP non-localhost detection (5 tests)  
- ✅ Live2D action execution (5 tests)
- ✅ Error handling and edge cases

**Total: 13 unit tests** (8 recorder tests + 5 tools tests)

## Test Files

- `js/core/audio/recorder.test.browser.js` - Audio/recorder tests
- `js/core/mcp/tools.test.browser.js` - MCP tools and Live2D tests

## Troubleshooting

**Tests don't run?**
- Make sure you're using a local server (not `file://`)
- Check browser console for errors
- Ensure all `.js` files are accessible

**Some tests fail?**
- Check the error message in the test results
- Verify mocks are set up correctly
- Check browser console for detailed errors
