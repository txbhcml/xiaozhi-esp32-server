/**
 * MCP工具模块测试
 * 测试Live2D动作工具执行功能
 */

import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import { executeMcpTool, initMcpTools, setWebSocket } from './tools.js';

describe('Live2D Action Tools', () => {
    let mockLive2DManager;

    beforeEach(() => {
        // Reset mocks before each test
        vi.clearAllMocks();
        
        // Mock Live2D manager
        mockLive2DManager = {
            motion: vi.fn()
        };
        
        // Setup window.chatApp
        global.window.chatApp = {
            live2dManager: mockLive2DManager
        };

        // Mock localStorage
        global.localStorage.getItem = vi.fn(() => null);
        
        // Mock fetch for default-mcp-tools.json
        global.fetch = vi.fn(() =>
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

        // Mock DOM elements
        global.document.getElementById = vi.fn((id) => {
            if (id === 'mcpToolsContainer') {
                return {
                    innerHTML: '',
                    appendChild: vi.fn()
                };
            }
            if (id === 'mcpToolsCount') {
                return {
                    textContent: ''
                };
            }
            return null;
        });
    });

    afterEach(() => {
        // Clean up
        global.window.chatApp = null;
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
        
        global.window.chatApp = null;
        
        const result = executeMcpTool('live2d.smile', {});
        
        expect(result.success).toBe(false);
        expect(result.error).toContain('Live2D管理器未初始化');
    });

    /**
     * 测试 executeMcpTool - 未知的动作
     */
    test('should handle unknown action gracefully', async () => {
        await initMcpTools();
        
        // Add an unknown live2d tool to the list
        const tools = await global.fetch().then(res => res.json());
        tools.push({
            name: 'live2d.unknown',
            description: 'Unknown action',
            inputSchema: { type: 'object', properties: {} }
        });
        
        global.fetch = vi.fn(() =>
            Promise.resolve({
                json: () => Promise.resolve(tools)
            })
        );
        
        await initMcpTools();
        
        const result = executeMcpTool('live2d.unknown', {});
        
        expect(result.success).toBe(false);
        expect(result.error).toContain('未知的动作');
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

    /**
     * 测试 executeMcpTool - 其他动作映射
     */
    test('should handle other action mappings', async () => {
        await initMcpTools();
        
        const actionMappings = [
            { tool: 'live2d.happy', expectedAction: 'FlickUp' },
            { tool: 'live2d.sad', expectedAction: 'FlickDown' },
            { tool: 'live2d.tap', expectedAction: 'Tap' },
            { tool: 'live2d.tapBody', expectedAction: 'Tap@Body' },
            { tool: 'live2d.flick', expectedAction: 'Flick' },
            { tool: 'live2d.flickBody', expectedAction: 'Flick@Body' },
            { tool: 'live2d.flickUp', expectedAction: 'FlickUp' },
            { tool: 'live2d.flickDown', expectedAction: 'FlickDown' }
        ];

        // Add these tools to the mock
        const tools = await global.fetch().then(res => res.json());
        actionMappings.forEach(mapping => {
            tools.push({
                name: mapping.tool,
                description: `Test ${mapping.tool}`,
                inputSchema: { type: 'object', properties: {} }
            });
        });

        global.fetch = vi.fn(() =>
            Promise.resolve({
                json: () => Promise.resolve(tools)
            })
        );

        await initMcpTools();

        for (const mapping of actionMappings) {
            mockLive2DManager.motion.mockClear();
            const result = executeMcpTool(mapping.tool, {});
            
            expect(result.success).toBe(true);
            expect(result.action).toBe(mapping.expectedAction);
            expect(mockLive2DManager.motion).toHaveBeenCalledWith(mapping.expectedAction);
        }
    });
});