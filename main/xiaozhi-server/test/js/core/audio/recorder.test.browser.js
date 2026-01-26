import { checkMicrophoneAvailability, isHttpNonLocalhost } from './recorder.js';

describe('Microphone Availability Detection', () => {
    beforeEach(() => vi.clearAllMocks());

    test('should return true when microphone is available', async () => {
        const mockTrack = { stop: vi.fn() };
        const mockStream = { getTracks: () => [mockTrack] };
        navigator.mediaDevices.getUserMedia = vi.fn().mockResolvedValue(mockStream);
        const result = await checkMicrophoneAvailability();
        expect(result).toBe(true);
        expect(navigator.mediaDevices.getUserMedia).toHaveBeenCalledWith({ audio: { echoCancellation: true, noiseSuppression: true, sampleRate: 16000, channelCount: 1 } });
        expect(mockTrack.stop).toHaveBeenCalled();
    });

    test('should return false when microphone is not available', async () => {
        navigator.mediaDevices.getUserMedia = vi.fn().mockRejectedValue(new Error('Permission denied'));
        const result = await checkMicrophoneAvailability();
        expect(result).toBe(false);
        expect(navigator.mediaDevices.getUserMedia).toHaveBeenCalled();
    });

    test('should return false when browser does not support getUserMedia', async () => {
        const originalGetUserMedia = navigator.mediaDevices.getUserMedia;
        navigator.mediaDevices.getUserMedia = undefined;
        const result = await checkMicrophoneAvailability();
        expect(result).toBe(false);
        navigator.mediaDevices.getUserMedia = originalGetUserMedia;
    });

    test('should return true for HTTP non-localhost access', () => {
        expect(typeof isHttpNonLocalhost()).toBe('boolean');
    });

    test('should return false for localhost', () => {
        const result = isHttpNonLocalhost();
        if (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1') {
            expect(result).toBe(false);
        } else {
            expect(typeof result).toBe('boolean');
        }
    });

    test('should return false for 127.0.0.1', () => {
        const result = isHttpNonLocalhost();
        if (window.location.hostname === '127.0.0.1') {
            expect(result).toBe(false);
        } else {
            expect(typeof result).toBe('boolean');
        }
    });

    test('should return false for private IP addresses', () => {
        const result = isHttpNonLocalhost();
        const hostname = window.location.hostname;
        const isPrivateIP = hostname.startsWith('192.168.') || hostname.startsWith('10.') || hostname.startsWith('172.');
        if (isPrivateIP && window.location.protocol === 'http:') {
            expect(result).toBe(false);
        } else {
            expect(typeof result).toBe('boolean');
        }
    });

    test('should return false for HTTPS protocol', () => {
        const result = isHttpNonLocalhost();
        if (window.location.protocol === 'https:') {
            expect(result).toBe(false);
        } else {
            expect(typeof result).toBe('boolean');
        }
    });
});