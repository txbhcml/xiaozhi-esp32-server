/**
 * MCP工具模块测试 - Browser compatible version
 * 测试Live2D动作工具执行功能
 * 
 * This version works without Vitest - uses the simple test framework from test-runner.html
 */

import { executeMcpTool, initMcpTools, setWebSocket } from './tools.js';

describe('Live2D Action Tools', () => {
    let mockLive2DManager;
    let originalChatApp;

    beforeEach(() => {
        // Reset mocks before each test
        vi.clearAllMocks();
        
        // Save original chatApp
        originalChatApp = window.chatApp;
        
        // Mock Live2D manager
        mockLive2DManager = {
            motion: vi.fn()
        };
        
        // Setup window.chatApp
        window.chatApp = {
            live2dManager: mockLive2DManager
        };

        // Mock localStorage
        localStorage.getItem = vi.fn(() => null);
        
        // Mock fetch for default-mcp-tools.json
        globalThis.fetch = vi.fn(() =>
            Promise.resolve({
                json: () => Promise.resolve([
                    {
                        name: 'live2d.smile',
                        description: 'Make the virtual human smile',
                        inputSchema: { type: 'object', properties: {} }
                    },
                    {
                        name: 'live2d.wave',
                        description: 'Make the virtual human wave',
                        inputSchema: { type: 'object', properties: {} }
                    },
                    {
                        name: 'live2d.action',
                        description: 'Trigger a specified action',
                        inputSchema: {
                            type: 'object',
                            properties: {
                                action: { type: 'string' }
                            },
                            required: ['action']
                        }
                    }
                ])
            })
        );

        // Mock DOM elements - ensure all required elements exist
        const mockContainer = {
            innerHTML: '',
            appendChild: vi.fn(),
            textContent: ''
        };
        
        document.getElementById = vi.fn((id) => {
            // Return mock elements for all IDs that tools.js might access
            if (id === 'mcpToolsContainer' || id === 'mcpPropertiesContainer') {
                return mockContainer;
            }
            if (id === 'mcpToolsCount') {
                return { textContent: '' };
            }
            // Return null for other elements (they're checked with if statements)
            return null;
        });
    });

    afterEach(() => {
        // Clean up
        window.chatApp = originalChatApp;
    });

    /**
     * 测试 executeMcpTool - smile 动作
     */
    test('should execute Live2D smile action', async () => {
        await initMcpTools();
        
        const result = executeMcpTool('live2d.smile', {});
        
        expect(result.success).toBe(true);
        expect(result.action).toBe('FlickUp');
        expect(result.tool).toBe('live2d.smile');
        expect(mockLive2DManager.motion).toHaveBeenCalledWith('FlickUp');
    });

    /**
     * 测试 executeMcpTool - wave 动作
     */
    test('should execute Live2D wave action', async () => {
        await initMcpTools();
        
        const result = executeMcpTool('live2d.wave', {});
        
        expect(result.success).toBe(true);
        expect(result.action).toBe('Tap');
        expect(result.tool).toBe('live2d.wave');
        expect(mockLive2DManager.motion).toHaveBeenCalledWith('Tap');
    });

    /**
     * 测试 executeMcpTool - 通用动作工具
     */
    test('should handle generic action tool', async () => {
        await initMcpTools();
        
        const result = executeMcpTool('live2d.action', { action: 'FlickDown' });
        
        expect(result.success).toBe(true);
        expect(result.action).toBe('FlickDown');
        expect(result.tool).toBe('live2d.action');
        expect(mockLive2DManager.motion).toHaveBeenCalledWith('FlickDown');
    });

    /**
     * 测试 executeMcpTool - Live2D管理器未初始化
     */
    test('should handle missing Live2D manager gracefully', async () => {
        await initMcpTools();
        
        window.chatApp = null;
        
        const result = executeMcpTool('live2d.smile', {});
        
        expect(result.success).toBe(false);
        expect(result.error).toContain('Live2D管理器未初始化');
    });

    /**
     * 测试 executeMcpTool - 未知的工具
     */
    test('should handle unknown tool gracefully', async () => {
        await initMcpTools();
        
        const result = executeMcpTool('unknown.tool', {});
        
        expect(result.success).toBe(false);
        expect(result.error).toContain('未知工具');
    });
});
