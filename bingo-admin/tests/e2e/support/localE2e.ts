import { promises as fs } from 'fs';
import path from 'path';

export interface LocalE2eManifest {
  runId: string;
  backendBaseUrl: string;
  adminBaseUrl: string;
  createdAt: string;
  room?: { id: number; name: string };
  session?: { id: number; name: string };
  player?: {
    phoneNumber: string;
    displayName: string;
    primaryPhone?: string;
    secondaryPhone?: string;
    secondaryName?: string;
  };
  deposits?: Array<{
    id: number;
    telebirrReference: string;
    amount: string;
    status?: string;
  }>;
  winningDraws?: number[];
  purchase?: {
    ticketId: number;
    ticketCode: string;
    operationId?: string;
  };
  claim?: {
    id: number;
    ticketId: number;
    status?: string;
    pattern?: string;
  };
  settlement?: {
    reference: string;
  };
  notes?: string[];
}

function env(name: string) {
  return process.env[name];
}

export function getRunId() {
  return env('BINGO_E2E_RUN_ID') ?? `run-${new Date().toISOString().replace(/[:.]/g, '-')}`;
}

export function getResultsRoot() {
  return env('BINGO_E2E_RESULTS_DIR') ?? path.resolve(process.cwd(), '..', 'test-results', 'local-e2e');
}

export function getRunDir() {
  return path.join(getResultsRoot(), getRunId());
}

export function getManifestPath() {
  return env('BINGO_E2E_MANIFEST_PATH') ?? path.join(getRunDir(), 'manifest.json');
}

export async function ensureRunDir() {
  await fs.mkdir(getRunDir(), { recursive: true });
}

export async function readManifest(): Promise<LocalE2eManifest | null> {
  try {
    const content = await fs.readFile(getManifestPath(), 'utf8');
    const manifest = JSON.parse(content) as LocalE2eManifest;
    delete (manifest as LocalE2eManifest & { primaryPassword?: string }).primaryPassword;
    delete (manifest as LocalE2eManifest & { secondaryPassword?: string }).secondaryPassword;
    if (manifest.player) {
      delete (manifest.player as typeof manifest.player & { primaryPassword?: string }).primaryPassword;
      delete (manifest.player as typeof manifest.player & { secondaryPassword?: string }).secondaryPassword;
    }
    return manifest;
  } catch {
    return null;
  }
}

export async function writeManifest(patch: Partial<LocalE2eManifest>) {
  await ensureRunDir();
  const current: LocalE2eManifest = (await readManifest()) ?? {
    runId: getRunId(),
    backendBaseUrl: env('BINGO_E2E_BACKEND_URL') ?? 'http://localhost:8080',
    adminBaseUrl: env('BINGO_E2E_ADMIN_URL') ?? 'http://localhost:5173',
    createdAt: new Date().toISOString(),
    notes: [],
  };

  const next: LocalE2eManifest = {
    ...current,
    ...patch,
    room: patch.room ?? current.room,
    session: patch.session ?? current.session,
    player: patch.player ?? current.player,
    deposits: patch.deposits ?? current.deposits,
    winningDraws: patch.winningDraws ?? current.winningDraws,
    purchase: patch.purchase ?? current.purchase,
    claim: patch.claim ?? current.claim,
    settlement: patch.settlement ?? current.settlement,
    notes: patch.notes ?? current.notes,
  };

  await fs.writeFile(getManifestPath(), `${JSON.stringify(next, null, 2)}\n`, 'utf8');
  return next;
}
