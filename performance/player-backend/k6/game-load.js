import http from 'k6/http';
import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';
import { SharedArray } from 'k6/data';

// Usage and preconditions are in README.md. This test intentionally has no
// account/session provisioning or cleanup path. Fixture credentials are
// short-lived synthetic-player JWTs from a disposable environment.
const players = new SharedArray('synthetic players', () => {
  const path = __ENV.PLAYER_FIXTURES_FILE;
  if (!path) throw new Error('PLAYER_FIXTURES_FILE is required');
  return JSON.parse(open(path));
});

const mode = __ENV.MODE || 'read'; // read | purchase | purchase-retry | claim | claim-retry | settle-race | expired-jwt
const vuTarget = Number(__ENV.PLAYERS || 0);
const maxApprovedVus = Number(__ENV.APPROVED_MAX_VUS || 0);
const maxApprovedRps = Number(__ENV.APPROVED_MAX_RPS || 0);
const maxApprovedDbConnections = Number(__ENV.APPROVED_MAX_DB_CONNECTIONS || 0);
const runId = __ENV.RUN_ID || '';
const api = (__ENV.API_BASE_URL || '').replace(/\/$/, '');
const wsUrl = __ENV.WS_URL || '';
const sessionId = Number(__ENV.SESSION_ID || 0);
const expectedSessionState = __ENV.EXPECTED_SESSION_STATE || '';
const allowed = (__ENV.APPROVED_ORIGIN || '').replace(/\/$/, '');
const expectedSchema = __ENV.EXPECTED_DISPOSABLE_SCHEMA || '';
const expectedMysqlServerUuid = __ENV.EXPECTED_DISPOSABLE_MYSQL_SERVER_UUID || '';
const targetOrigin = api ? new URL(api).origin : '';

function preflight() {
  if (__ENV.DISPOSABLE_TARGET_APPROVED !== 'YES') {
    throw new Error('Refusing run: set DISPOSABLE_TARGET_APPROVED=YES only after verifying a disposable target.');
  }
  if (!api || !allowed || targetOrigin !== allowed) {
    throw new Error('Refusing run: API_BASE_URL origin must exactly match APPROVED_ORIGIN.');
  }
  const host = new URL(api).hostname.toLowerCase();
  const apiUrl = new URL(api);
  if (apiUrl.pathname !== '/' || apiUrl.search || apiUrl.hash) throw new Error('API_BASE_URL must be an origin without a path, query, or fragment.');
  const octets = host.split('.').map(Number);
  const ipv4Literal = octets.length === 4 && octets.every((part) => Number.isInteger(part) && part >= 0 && part <= 255);
  const loopback = host === 'localhost' || (ipv4Literal && octets[0] === 127);
  const privateIpv4 = ipv4Literal && (octets[0] === 10 ||
    (octets[0] === 192 && octets[1] === 168) ||
    (octets[0] === 172 && octets[1] >= 16 && octets[1] <= 31));
  if (!loopback && !privateIpv4) throw new Error('Refusing DNS names and nonprivate/public targets. Use loopback or an RFC1918 disposable backend IP.');
  const localHost = loopback;
  if (!localHost && apiUrl.protocol !== 'https:') throw new Error('Private non-loopback API targets require HTTPS.');
  if (!runId || !/^[a-zA-Z0-9_-]{6,24}$/.test(runId)) throw new Error('RUN_ID must be a unique 6-24 character identifier.');
  if (![100, 250, 500, 1000].includes(vuTarget)) throw new Error('PLAYERS must be one of 100, 250, 500, or 1000.');
  if (!Number.isInteger(maxApprovedVus) || maxApprovedVus < vuTarget || maxApprovedVus > 1000) {
    throw new Error('APPROVED_MAX_VUS must be an approved integer >= PLAYERS and <= 1000.');
  }
  if (!Number.isInteger(maxApprovedRps) || maxApprovedRps < 1) {
    throw new Error('APPROVED_MAX_RPS must be explicitly set to the approved request-rate ceiling.');
  }
  if (!Number.isInteger(maxApprovedDbConnections) || maxApprovedDbConnections < 1) {
    throw new Error('APPROVED_MAX_DB_CONNECTIONS must be an explicit positive approved ceiling.');
  }
  if (!/^bingo_perf_[a-z0-9_]{1,48}$/.test(expectedSchema)) {
    throw new Error('EXPECTED_DISPOSABLE_SCHEMA must name the dedicated bingo_perf_<run> database.');
  }
  if (!/^[0-9a-fA-F-]{36}$/.test(expectedMysqlServerUuid)) {
    throw new Error('EXPECTED_DISPOSABLE_MYSQL_SERVER_UUID must match the approved MySQL server UUID.');
  }
  if (!__ENV.TARGET_GUARD_TOKEN) throw new Error('TARGET_GUARD_TOKEN is required to verify the backend datasource before load.');
  if (!Number.isInteger(sessionId) || sessionId < 1) throw new Error('SESSION_ID must name a pre-provisioned disposable session.');
  if (!['DRAFT', 'OPEN', 'STARTED', 'DRAWING', 'CLOSED', 'SETTLED', 'CANCELLED'].includes(expectedSessionState)) {
    throw new Error('EXPECTED_SESSION_STATE must match the reviewed disposable fixture state.');
  }
  if ((mode === 'read' || mode.startsWith('claim') || mode === 'settle-race') && expectedSessionState !== 'DRAWING') {
    throw new Error(`${mode} requires EXPECTED_SESSION_STATE=DRAWING.`);
  }
  if (mode.startsWith('purchase') && expectedSessionState !== 'OPEN') {
    throw new Error('Purchase modes require EXPECTED_SESSION_STATE=OPEN and remaining ticket capacity.');
  }
  if (!['read', 'purchase', 'purchase-retry', 'claim', 'claim-retry', 'settle-race', 'expired-jwt'].includes(mode)) {
    throw new Error(`Unsupported MODE: ${mode}`);
  }
  const needsOneActor = mode === 'purchase-retry' || mode === 'claim-retry' || mode === 'settle-race' || mode === 'expired-jwt';
  if (players.length < (needsOneActor ? 1 : vuTarget)) {
    throw new Error(`Fixture file has ${players.length} rows; ${needsOneActor ? 1 : vuTarget} are required for ${mode}.`);
  }
  if (!['read', 'expired-jwt'].includes(mode) && __ENV.SYNTHETIC_FINANCIAL_MUTATIONS_APPROVED !== 'YES') {
    throw new Error('Refusing financial mutations without SYNTHETIC_FINANCIAL_MUTATIONS_APPROVED=YES.');
  }
  if (mode.startsWith('claim') && __ENV.VALID_CLAIM_FIXTURES_APPROVED !== 'YES') {
    throw new Error('Claim modes require pre-reviewed VALID_CLAIM_FIXTURES_APPROVED=YES.');
  }
  if (mode === 'settle-race' && __ENV.SYNTHETIC_PRIZE_SETTLEMENT_APPROVED !== 'YES') {
    throw new Error('Settlement race requires SYNTHETIC_PRIZE_SETTLEMENT_APPROVED=YES.');
  }
  if (players.some((p) => !p.accessToken || !Number.isInteger(p.playerId))) {
    throw new Error('Each fixture needs playerId and accessToken.');
  }
  if (mode === 'claim' && players.slice(0, vuTarget).some((p) => !p.ticketId || !p.claimedPattern)) {
    throw new Error('Claim fixtures need prevalidated ticketId and claimedPattern for every player.');
  }
  if (mode.startsWith('purchase') && players.slice(0, mode === 'purchase' ? vuTarget : 1).some((p) => p.initialBalance == null || p.ticketPrice == null)) {
    throw new Error('Purchase fixtures require independently recorded initialBalance and ticketPrice.');
  }
  if (mode.startsWith('purchase-retry') && __ENV.RETRY_ACTOR_INDEX == null) {
    throw new Error('purchase-retry requires a single pre-provisioned RETRY_ACTOR_INDEX.');
  }
  if (mode === 'claim-retry' && __ENV.RETRY_ACTOR_INDEX == null) {
    throw new Error('claim-retry requires a single pre-provisioned RETRY_ACTOR_INDEX.');
  }
  if (mode === 'settle-race' && (!__ENV.SETTLEMENT_ADMIN_TOKEN ||
      !Number.isInteger(Number(__ENV.ENTITLEMENT_ID)) || !__ENV.SETTLEMENT_REFERENCE)) {
    throw new Error('settle-race requires an approved Admin token and pending wallet-entitlement fixture.');
  }
  if (mode === 'expired-jwt' && !__ENV.EXPIRED_PLAYER_JWT) {
    throw new Error('expired-jwt requires an intentionally expired JWT issued for this disposable environment.');
  }
  if (mode.startsWith('purchase-retry') || mode === 'claim-retry') {
    const retryIndex = Number(__ENV.RETRY_ACTOR_INDEX);
    if (!Number.isInteger(retryIndex) || retryIndex < 0 || !players[retryIndex]) {
      throw new Error('RETRY_ACTOR_INDEX is outside fixture data.');
    }
    if (mode === 'claim-retry' && (!players[retryIndex].ticketId || !players[retryIndex].claimedPattern)) {
      throw new Error('Claim retry fixture needs a prevalidated ticketId and claimedPattern.');
    }
  }
  if (!wsUrl) throw new Error('WS_URL must point to the disposable backend /ws endpoint.');
  const socketUrl = new URL(wsUrl);
  if (socketUrl.pathname !== '/ws' || socketUrl.host !== apiUrl.host ||
      socketUrl.protocol !== (apiUrl.protocol === 'https:' ? 'wss:' : 'ws:')) {
    throw new Error('WS_URL must use the API host, matching secure scheme, and exact /ws path.');
  }
}

preflight();

export function setup() {
  const response = http.get(`${api}/api/v1/admin/performance-target`, {
    headers: {
      'X-Performance-Guard-Token': __ENV.TARGET_GUARD_TOKEN,
      'X-Correlation-ID': `${runId}-target-identity`,
    },
    tags: { operation: 'target-identity-guard' },
  });
  let identity;
  try { identity = response.json(); } catch (_) { identity = null; }
  if (response.status !== 200 || identity?.disposable !== true ||
      identity?.profile !== 'performance-test' || identity?.schemaName !== expectedSchema ||
      identity?.databaseAccount !== 'bingo_perf_runner' ||
      String(identity?.mysqlServerUuid).toLowerCase() !== expectedMysqlServerUuid.toLowerCase() ||
      Number(identity?.approvedMaximumConnections) !== maxApprovedDbConnections ||
      Number(identity?.hikariPoolMaximum) > maxApprovedDbConnections) {
    throw new Error('Backend identity guard refused target; no VUs will start. Verify performance-test profile, schema and restricted account.');
  }
  return { verifiedSchema: identity.schemaName, databaseAccount: identity.databaseAccount, profile: identity.profile };
}

const requestLatency = new Trend('player_request_latency', true);
const wsConnectLatency = new Trend('websocket_connect_latency', true);
const wsReconnectLatency = new Trend('websocket_reconnect_latency', true);
const drawDeliveryLatency = new Trend('draw_delivery_latency', true);
const duplicateDraws = new Counter('duplicate_draw_sequences');
const invariantFailures = new Counter('financial_invariant_failures');
const operationErrors = new Rate('player_operation_error_rate');
const receivedDraws = new Counter('websocket_draws_received');
const expiredJwtAcceptances = new Counter('expired_jwt_acceptances');
const expiredJwtUnconfirmed = new Counter('expired_jwt_unconfirmed');
const drawSequenceGaps = new Counter('draw_sequence_gaps');
const websocketSequenceGaps = new Counter('websocket_sequence_gaps_observed');
let lastReplaySequence = Number(__ENV.START_SEQUENCE || 0);
let lastWebsocketSequence = Number(__ENV.START_SEQUENCE || 0);
const websocketSequences = new Set();
let socketConnectionCount = 0;

const duration = __ENV.DURATION || '5m';
const durationMatch = /^(\d+)(s|m)$/.exec(duration);
if (!durationMatch || Number(durationMatch[1]) * (durationMatch[2] === 'm' ? 60 : 1) > 1800 ||
    Number(durationMatch[1]) * (durationMatch[2] === 'm' ? 60 : 1) < 30) {
  throw new Error('DURATION must be between 30s and 30m.');
}
export const options = {
  scenarios: {
    player_workload: {
      executor: mode === 'read' ? 'constant-vus' : 'per-vu-iterations',
      vus: vuTarget,
      ...(mode === 'read' ? { duration } : { iterations: 1, maxDuration: '15m' }),
      gracefulStop: '15s',
    },
  },
  rps: maxApprovedRps,
  thresholds: {
    duplicate_draw_sequences: ['count==0'],
    draw_sequence_gaps: ['count==0'],
    financial_invariant_failures: ['count==0'],
    expired_jwt_acceptances: ['count==0'],
    expired_jwt_unconfirmed: ['count==0'],
  },
  summaryTrendStats: ['min', 'med', 'avg', 'p(90)', 'p(95)', 'p(99)', 'max'],
  noConnectionReuse: false,
  userAgent: `bingo-disposable-load/${runId}`,
};

function correlationId(suffix) { return `${runId}-vu${__VU}-it${__ITER}-${suffix}`; }
function authHeaders(player, suffix) {
  return {
    Authorization: `Bearer ${player.accessToken}`,
    'X-Correlation-ID': correlationId(suffix),
    'Content-Type': 'application/json',
  };
}
function measured(response) { requestLatency.add(response.timings.duration); }
function safeJson(response) {
  try { return response.json(); } catch (_) { return null; }
}
function get(path, player, suffix) {
  const response = http.get(`${api}${path}`, { headers: authHeaders(player, suffix), tags: { operation: suffix } });
  measured(response);
  return response;
}
function assertOk(response, name) {
  const ok = check(response, { [`${name} HTTP 2xx`]: (r) => r.status >= 200 && r.status < 300 });
  operationErrors.add(!ok);
  return ok;
}

function verifyReplay(player) {
  const replay = get(`/api/v1/sessions/${sessionId}/draws?afterSequence=${lastReplaySequence}`, player, 'draw-replay');
  if (!assertOk(replay, 'draw replay')) return;
  const draws = safeJson(replay);
  if (!Array.isArray(draws)) {
    operationErrors.add(true);
    return;
  }
  let cursor = lastReplaySequence;
  for (const draw of draws) {
    const sequence = Number(draw.sequenceNo);
    if (!Number.isInteger(sequence) || sequence <= cursor) {
      duplicateDraws.add(1);
      operationErrors.add(true);
      return;
    }
    if (sequence !== cursor + 1) {
      drawSequenceGaps.add(1);
      operationErrors.add(true);
      return;
    }
    cursor = sequence;
  }
  lastReplaySequence = cursor;
}

function openDrawSocket(player) {
  const started = Date.now();
  let connected = false;
  const response = ws.connect(wsUrl, {
    headers: { Origin: __ENV.WS_ORIGIN || allowed },
    tags: { operation: 'draw-websocket' },
  }, (socket) => {
    socket.on('open', () => {
      socket.send(`CONNECT\naccept-version:1.2\nheart-beat:10000,10000\nAuthorization:Bearer ${player.accessToken}\n\n\u0000`);
    });
    socket.on('message', (raw) => {
      const message = String(raw);
      if (message.startsWith('CONNECTED')) {
        connected = true;
        const connectDuration = Date.now() - started;
        wsConnectLatency.add(connectDuration);
        if (socketConnectionCount > 0) wsReconnectLatency.add(connectDuration);
        socketConnectionCount++;
        socket.setInterval(() => socket.send('\n'), 10000);
        socket.send(`SUBSCRIBE\nid:perf-${runId}-${__VU}\ndestination:/topic/session/${sessionId}/draws\nack:auto\n\n\u0000`);
        return;
      }
      if (!message.startsWith('MESSAGE')) return;
      const separator = message.indexOf('\n\n');
      if (separator < 0) return;
      const frameBody = message.slice(separator + 2).replace(/\u0000+$/, '');
      try {
        const draw = JSON.parse(frameBody);
        const sequence = Number(draw.sequenceNo);
        receivedDraws.add(1);
        if (websocketSequences.has(sequence) || sequence <= lastWebsocketSequence) {
          duplicateDraws.add(1);
          operationErrors.add(true);
        } else if (sequence !== lastWebsocketSequence + 1) {
          websocketSequenceGaps.add(1);
        }
        if (Number.isInteger(sequence) && sequence > lastWebsocketSequence) lastWebsocketSequence = sequence;
        websocketSequences.add(sequence);
        const publishedAt = Date.parse(draw.drawnAt);
        if (Number.isFinite(publishedAt)) drawDeliveryLatency.add(Date.now() - publishedAt);
      } catch (_) { operationErrors.add(true); }
    });
    socket.on('error', () => operationErrors.add(true));
    socket.setTimeout(() => socket.close(), Number(__ENV.WS_HOLD_MS || 30000));
  });
  check(response, { 'websocket upgrade 101': (r) => r && r.status === 101 }) || operationErrors.add(true);
  if (!connected) operationErrors.add(true);
}

function purchase(player, index) {
  let before;
  let beforeBalance;
  if (mode === 'purchase') {
    before = get('/api/v1/wallet/balance', player, 'wallet-before');
    if (!assertOk(before, 'wallet before')) return;
    beforeBalance = Number(safeJson(before)?.availableBalance);
    const expectedInitial = Number(players[index].initialBalance);
    if (!Number.isFinite(beforeBalance) || !Number.isFinite(expectedInitial) ||
        Math.round(beforeBalance * 100) !== Math.round(expectedInitial * 100)) {
      invariantFailures.add(1);
      return;
    }
  }
  const key = `${runId}-purchase-${player.playerId}`;
  const url = `${api}/api/v1/sessions/${sessionId}/tickets`;
  const send = () => {
    const response = http.post(url, null, {
      headers: { ...authHeaders(player, 'ticket-purchase'), 'Idempotency-Key': key },
      tags: { operation: 'ticket-purchase' },
    });
    measured(response);
    return response;
  };
  const first = send();
  if (!assertOk(first, 'ticket purchase')) return;
  const firstJson = safeJson(first);
  if (!Number.isInteger(Number(firstJson?.ticket?.id))) invariantFailures.add(1);
  let finalJson = firstJson;
  if (mode === 'purchase-retry') {
    const retry = send();
    if (!assertOk(retry, 'ticket idempotent retry')) return;
    finalJson = safeJson(retry);
    if (firstJson?.ticket?.id !== finalJson?.ticket?.id) invariantFailures.add(1);
  }
  if (mode === 'purchase') {
    const after = get('/api/v1/wallet/balance', player, 'wallet-after');
    if (!assertOk(after, 'wallet after')) return;
    const afterBalance = Number(safeJson(after)?.availableBalance);
    const price = Number(players[index].ticketPrice);
    if (Number.isFinite(beforeBalance) && Number.isFinite(afterBalance) && Number.isFinite(price) &&
        Math.round((beforeBalance - afterBalance) * 100) !== Math.round(price * 100)) {
      invariantFailures.add(1);
    }
  }
}

function submitClaim(player) {
  const request = () => {
    const response = http.post(`${api}/api/v1/tickets/${player.ticketId}/win-claims`,
      JSON.stringify({ claimedPattern: player.claimedPattern }), {
        headers: { ...authHeaders(player, 'win-claim'), 'Idempotency-Key': `${runId}-claim-${player.playerId}` },
        tags: { operation: 'win-claim' },
      });
    measured(response);
    return response;
  };
  const first = request();
  if (!assertOk(first, 'win claim')) return;
  if (mode === 'claim-retry') {
    const replay = request();
    if (!assertOk(replay, 'win claim retry')) return;
    if (safeJson(first)?.id !== safeJson(replay)?.id) invariantFailures.add(1);
  }
}

export default function () {
  let index = __VU - 1;
  if (mode === 'purchase-retry' || mode === 'claim-retry') index = Number(__ENV.RETRY_ACTOR_INDEX);
  if (mode === 'settle-race') index = 0;
  const player = players[index];
  if (!player) { operationErrors.add(true); return; }

  if (mode === 'settle-race') {
    settlePrize();
    return;
  }
  if (mode === 'expired-jwt') {
    testExpiredJwt(player);
    return;
  }

  // A run is bounded by duration and explicit WS lifetime. Read runs are safe
  // to repeat. Mutation modes intentionally perform no more than one operation
  // per iteration and require synthetic-only data and explicit opt-in.
  const session = get(`/api/v1/sessions/${sessionId}`, player, 'session-read');
  if (!assertOk(session, 'session read')) return;
  if (safeJson(session)?.state !== expectedSessionState) {
    invariantFailures.add(1);
    return;
  }
  verifyReplay(player);
  if (mode === 'purchase' || mode === 'purchase-retry') purchase(player, index);
  if (mode === 'claim' || mode === 'claim-retry') submitClaim(player);
  if (mode.endsWith('-retry')) return;
  openDrawSocket(player);
  sleep(Number(__ENV.THINK_TIME_SECONDS || 1));
}

function settlePrize() {
  const response = http.post(`${api}/api/v1/admin/prize-entitlements/${Number(__ENV.ENTITLEMENT_ID)}/settle`,
    JSON.stringify({ settlementReference: __ENV.SETTLEMENT_REFERENCE, settlementNote: `Disposable QA ${runId}` }), {
      headers: {
        Authorization: `Bearer ${__ENV.SETTLEMENT_ADMIN_TOKEN}`,
        'X-Correlation-ID': correlationId('prize-settlement'),
        'Content-Type': 'application/json',
      },
      tags: { operation: 'wallet-prize-settlement' },
    });
  measured(response);
  if (!assertOk(response, 'settlement race')) return;
  const result = safeJson(response);
  if (result?.status !== 'SETTLED' || result?.settlementPath !== 'PLAYER_WALLET' ||
      result?.settlementReference !== __ENV.SETTLEMENT_REFERENCE) {
    invariantFailures.add(1);
  }
}

function testExpiredJwt(player) {
  const response = http.get(`${api}/api/v1/sessions/${sessionId}`, {
    headers: {
      Authorization: `Bearer ${__ENV.EXPIRED_PLAYER_JWT}`,
      'X-Correlation-ID': correlationId('expired-jwt'),
    },
    tags: { operation: 'expired-jwt-rest' },
  });
  measured(response);
  if (response.status === 200) expiredJwtAcceptances.add(1);
  check(response, { 'expired JWT rejected by REST': (r) => r.status === 401 || r.status === 403 });

  let connected = false;
  let rejected = false;
  const socketResponse = ws.connect(wsUrl, { headers: { Origin: __ENV.WS_ORIGIN || allowed } }, (socket) => {
    socket.on('open', () => socket.send(
      `CONNECT\naccept-version:1.2\nheart-beat:10000,10000\nAuthorization:Bearer ${__ENV.EXPIRED_PLAYER_JWT}\n\n\u0000`,
    ));
    socket.on('message', (raw) => {
      const frame = String(raw);
      if (frame.startsWith('CONNECTED')) { connected = true; expiredJwtAcceptances.add(1); socket.close(); }
      if (frame.startsWith('ERROR')) { rejected = true; socket.close(); }
    });
    socket.on('close', () => { if (!connected) rejected = true; });
    socket.setTimeout(() => socket.close(), 5000);
  });
  if (socketResponse && socketResponse.status !== 101) {
    const handshakeRejected = socketResponse.status === 401 || socketResponse.status === 403;
    check(socketResponse, { 'expired JWT rejected during WebSocket handshake': () => handshakeRejected });
    rejected = handshakeRejected;
  }
  // A WebSocket upgrade may be 101 before STOMP JWT validation. The required
  // evidence is no CONNECTED frame; server logs should show no authenticated subscription.
  if (connected) expiredJwtAcceptances.add(1);
  if (!rejected) expiredJwtUnconfirmed.add(1);
}
