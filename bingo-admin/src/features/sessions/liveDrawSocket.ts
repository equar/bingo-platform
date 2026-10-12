import { apiBaseUrl } from '../../api/http';
import type { DrawEventResponse } from '../../api/admin';

export interface LiveDrawStream {
  close: () => void;
}

function toWebSocketUrl(baseUrl: string): string {
  const url = new URL(baseUrl);
  url.pathname = '/ws';
  url.search = '';
  url.hash = '';
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
  return url.toString();
}

function parseFrames(buffer: string) {
  const frames: string[] = [];
  let remaining = buffer;
  for (;;) {
    const index = remaining.indexOf('\u0000');
    if (index < 0) {
      break;
    }
    const frame = remaining.slice(0, index);
    remaining = remaining.slice(index + 1);
    if (frame.trim()) {
      frames.push(frame);
    }
  }
  return { frames, remaining };
}

function parseFrame(raw: string) {
  const [headerSection, ...bodyParts] = raw.split('\n\n');
  const [command, ...headerLines] = headerSection.split('\n');
  const headers: Record<string, string> = {};
  headerLines.forEach((line) => {
    const splitIndex = line.indexOf(':');
    if (splitIndex > 0) {
      headers[line.slice(0, splitIndex)] = line.slice(splitIndex + 1);
    }
  });
  return { command, headers, body: bodyParts.join('\n\n') };
}

export function connectLiveDrawStream(params: {
  accessToken: string;
  sessionId: number;
  onDraw: (event: DrawEventResponse) => void;
  onError: (message: string) => void;
  onGap: (fromSequence: number) => Promise<void> | void;
}) {
  let socket: WebSocket | null = null;
  let buffer = '';
  let reconnectTimer: number | undefined;
  let closedManually = false;
  let reconnectDelay = 1000;
  let lastSeenSequence = 0;

  const connect = () => {
    if (!apiBaseUrl) {
      params.onError('Missing VITE_API_BASE_URL');
      return;
    }
    socket = new WebSocket(toWebSocketUrl(apiBaseUrl));
    socket.onopen = () => {
      socket?.send(`CONNECT\naccept-version:1.2\nheart-beat:0,0\nAuthorization:Bearer ${params.accessToken}\n\n\u0000`);
      reconnectDelay = 1000;
    };
    socket.onmessage = async (event) => {
      buffer += String(event.data);
      const parsed = parseFrames(buffer);
      buffer = parsed.remaining;
      for (const rawFrame of parsed.frames) {
        const frame = parseFrame(rawFrame);
        if (frame.command === 'CONNECTED') {
          socket?.send(`SUBSCRIBE\nid:session-${params.sessionId}\ndestination:/topic/session/${params.sessionId}/draws\nack:auto\n\n\u0000`);
          continue;
        }
        if (frame.command === 'MESSAGE') {
          const payload = JSON.parse(frame.body) as DrawEventResponse;
          if (payload.sequenceNo > lastSeenSequence + 1) {
            await params.onGap(lastSeenSequence);
          }
          if (payload.sequenceNo > lastSeenSequence) {
            lastSeenSequence = payload.sequenceNo;
            params.onDraw(payload);
          }
        }
      }
    };
    socket.onerror = () => {
      params.onError('Live draw connection error');
    };
    socket.onclose = () => {
      if (closedManually) {
        return;
      }
      reconnectTimer = window.setTimeout(connect, reconnectDelay);
      reconnectDelay = Math.min(reconnectDelay * 2, 15000);
    };
  };

  connect();

  return {
    close: () => {
      closedManually = true;
      if (reconnectTimer) {
        window.clearTimeout(reconnectTimer);
      }
      socket?.close();
    },
    getLastSequence: () => lastSeenSequence,
    setLastSequence: (sequenceNo: number) => {
      lastSeenSequence = sequenceNo;
    },
  } satisfies LiveDrawStream & { getLastSequence: () => number; setLastSequence: (sequenceNo: number) => void };
}
