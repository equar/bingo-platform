import { afterEach, describe, expect, it, vi } from 'vitest';
import { connectLiveDrawStream } from './liveDrawSocket';
vi.mock('../../api/http', () => ({ apiBaseUrl: 'http://localhost:8080' }));

class TestSocket {
  static instances: TestSocket[] = [];
  onopen: (() => void) | null = null;
  onmessage: ((event: { data: string }) => Promise<void>) | null = null;
  onclose: (() => void) | null = null;
  onerror: (() => void) | null = null;
  send = vi.fn();
  close = vi.fn();
  constructor() { TestSocket.instances.push(this); }
  connected() { return this.onmessage?.({ data: 'CONNECTED\nversion:1.2\n\n\u0000' }); }
}

afterEach(() => { vi.useRealTimers(); vi.unstubAllGlobals(); TestSocket.instances = []; });
describe('live draw replay', () => {
  it('replays on initial connection and reconnect even when no further draw arrives', async () => {
    vi.useFakeTimers(); vi.stubGlobal('WebSocket', TestSocket);
    const onGap = vi.fn().mockResolvedValue(undefined), onConnected = vi.fn(), onDraw = vi.fn();
    const stream = connectLiveDrawStream({ accessToken: 'test', sessionId: 9, onGap, onConnected, onDraw, onError: vi.fn() });
    const first = TestSocket.instances[0]; await first.connected();
    expect(first.send).toHaveBeenCalledWith(expect.stringContaining('/topic/session/9/draws'));
    expect(onGap).toHaveBeenCalledWith(0);
    await first.onmessage?.({ data: 'MESSAGE\n\n' + JSON.stringify({ sessionId: 9, sequenceNo: 75, numberDrawn: 42 }) + '\u0000' });
    first.onclose?.(); await vi.advanceTimersByTimeAsync(1000);
    await TestSocket.instances[1].connected();
    expect(onGap).toHaveBeenLastCalledWith(75);
    expect(onConnected).toHaveBeenCalledTimes(2);
    expect(onDraw).toHaveBeenCalledTimes(1);
    stream.close();
  });

  it('reports replay failure instead of reporting a current connection', async () => {
    vi.stubGlobal('WebSocket', TestSocket);
    const onConnected = vi.fn(), onError = vi.fn();
    const stream = connectLiveDrawStream({ accessToken: 'test', sessionId: 9, onGap: vi.fn().mockRejectedValue(new Error('offline')),
      onConnected, onError, onDraw: vi.fn() });
    await TestSocket.instances[0].connected();
    expect(onConnected).not.toHaveBeenCalled(); expect(onError).toHaveBeenCalledWith(expect.stringContaining('Unable to replay'));
    stream.close();
  });
});
