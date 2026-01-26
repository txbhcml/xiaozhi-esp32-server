import { executeMcpTool, initMcpTools } from './tools.js';

describe('Live2D Action Tools', () => {
    let mockLive2DManager, originalChatApp;

    beforeEach(() => {
        vi.clearAllMocks();
        originalChatApp = window.chatApp;
        mockLive2DManager = { motion: vi.fn() };
        window.chatApp = { live2dManager: mockLive2DManager };
        localStorage.getItem = vi.fn(() => null);
        globalThis.fetch = vi.fn(() => Promise.resolve({ json: () => Promise.resolve([{ name: 'live2d.smile', description: 'Make the virtual human smile', inputSchema: { type: 'object', properties: {} } }, { name: 'live2d.wave', description: 'Make the virtual human wave', inputSchema: { type: 'object', properties: {} } }, { name: 'live2d.action', description: 'Trigger a specified action', inputSchema: { type: 'object', properties: { action: { type: 'string' } }, required: ['action'] } }]) }));
        const mockContainer = { innerHTML: '', appendChild: vi.fn(), textContent: '' };
        document.getElementById = vi.fn((id) => {
            if (id === 'mcpToolsContainer' || id === 'mcpPropertiesContainer') return mockContainer;
            if (id === 'mcpToolsCount') return { textContent: '' };
            return null;
        });
    });

    afterEach(() => { window.chatApp = originalChatApp; });

    test('should execute Live2D smile action', async () => {
        await initMcpTools();
        const result = executeMcpTool('live2d.smile', {});
        expect(result.success).toBe(true);
        expect(result.action).toBe('FlickUp');
        expect(result.tool).toBe('live2d.smile');
        expect(mockLive2DManager.motion).toHaveBeenCalledWith('FlickUp');
    });

    test('should execute Live2D wave action', async () => {
        await initMcpTools();
        const result = executeMcpTool('live2d.wave', {});
        expect(result.success).toBe(true);
        expect(result.action).toBe('Tap');
        expect(result.tool).toBe('live2d.wave');
        expect(mockLive2DManager.motion).toHaveBeenCalledWith('Tap');
    });

    test('should handle generic action tool', async () => {
        await initMcpTools();
        const result = executeMcpTool('live2d.action', { action: 'FlickDown' });
        expect(result.success).toBe(true);
        expect(result.action).toBe('FlickDown');
        expect(result.tool).toBe('live2d.action');
        expect(mockLive2DManager.motion).toHaveBeenCalledWith('FlickDown');
    });

    test('should handle missing Live2D manager gracefully', async () => {
        await initMcpTools();
        window.chatApp = null;
        const result = executeMcpTool('live2d.smile', {});
        expect(result.success).toBe(false);
        expect(result.error).toContain('Live2D管理器未初始化');
    });

    test('should handle unknown tool gracefully', async () => {
        await initMcpTools();
        const result = executeMcpTool('unknown.tool', {});
        expect(result.success).toBe(false);
        expect(result.error).toContain('未知工具');
    });
});