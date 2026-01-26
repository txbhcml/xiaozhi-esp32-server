/**
 * Audio recording module tests
 * Test microphone availability detection functionality
 */

import { describe, test, expect, vi, beforeEach, afterEach } from 'vitest';
import { checkMicrophoneAvailability, isHttpNonLocalhost } from './recorder.js';

describe('Microphone Availability Detection', () => {
    beforeEach(() => {
        // Reset mocks before each test
        vi.clearAllMocks();
    });

    /**
     * Test checkMicrophoneAvailability function - success case
     */
    test('should return true when microphone is available', async () => {
        // Mock navigator.mediaDevices.getUserMedia to return a successful stream
        const mockTrack = {
            stop: vi.fn()
        };
        const mockStream = {
            getTracks: () => [mockTrack]
        };
        
        global.navigator.mediaDevices.getUserMedia = vi.fn().mockResolvedValue(mockStream);

        const result = await checkMicrophoneAvailability();

        expect(result).toBe(true);
        expect(global.navigator.mediaDevices.getUserMedia).toHaveBeenCalledWith({
            audio: {
                echoCancellation: true,
                noiseSuppression: true,
                sampleRate: 16000,
                channelCount: 1
            }
        });
        expect(mockTrack.stop).toHaveBeenCalled();
    });

    /**
     * Test checkMicrophoneAvailability function - failure case
     */
    test('should return false when microphone is not available', async () => {
        // Mock getUserMedia to throw an error
        const mockError = new Error('Permission denied');
        global.navigator.mediaDevices.getUserMedia = vi.fn().mockRejectedValue(mockError);

        const result = await checkMicrophoneAvailability();

        expect(result).toBe(false);
        expect(global.navigator.mediaDevices.getUserMedia).toHaveBeenCalled();
    });

    /**
     * Test checkMicrophoneAvailability function - browser not supported
     */
    test('should return false when browser does not support getUserMedia', async () => {
        // Mock navigator without mediaDevices
        const originalMediaDevices = global.navigator.mediaDevices;
        delete global.navigator.mediaDevices;

        const result = await checkMicrophoneAvailability();

        expect(result).toBe(false);

        // Restore
        global.navigator.mediaDevices = originalMediaDevices;
    });

    /**
     * Test isHttpNonLocalhost function - HTTP non-localhost
     */
    test('should return true for HTTP non-localhost access', () => {
        // Mock window.location for HTTP non-localhost
        Object.defineProperty(window, 'location', {
            value: {
                protocol: 'http:',
                hostname: 'example.com'
            },
            writable: true,
            configurable: true
        });

        const result = isHttpNonLocalhost();
        expect(result).toBe(true);
    });

    /**
     * Test isHttpNonLocalhost function - localhost should return false
     */
    test('should return false for localhost', () => {
        Object.defineProperty(window, 'location', {
            value: {
                protocol: 'http:',
                hostname: 'localhost'
            },
            writable: true,
            configurable: true
        });

        const result = isHttpNonLocalhost();
        expect(result).toBe(false);
    });

    /**
     * Test isHttpNonLocalhost function - 127.0.0.1 should return false
     */
    test('should return false for 127.0.0.1', () => {
        Object.defineProperty(window, 'location', {
            value: {
                protocol: 'http:',
                hostname: '127.0.0.1'
            },
            writable: true,
            configurable: true
        });

        const result = isHttpNonLocalhost();
        expect(result).toBe(false);
    });

    /**
     * Test isHttpNonLocalhost function - private IP should return false
     */
    test('should return false for private IP addresses', () => {
        const privateIPs = ['192.168.1.100', '10.0.0.1', '172.16.0.1'];

        privateIPs.forEach(ip => {
            Object.defineProperty(window, 'location', {
                value: {
                    protocol: 'http:',
                    hostname: ip
                },
                writable: true,
                configurable: true
            });

            const result = isHttpNonLocalhost();
            expect(result).toBe(false);
        });
    });

    /**
     * Test isHttpNonLocalhost function - HTTPS should return false
     */
    test('should return false for HTTPS protocol', () => {
        Object.defineProperty(window, 'location', {
            value: {
                protocol: 'https:',
                hostname: 'example.com'
            },
            writable: true,
            configurable: true
        });

        const result = isHttpNonLocalhost();
        expect(result).toBe(false);
    });
});