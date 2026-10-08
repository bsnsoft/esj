import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

/**
 * The pipe `conformance/fixtures/run.py` talks to this implementation through
 * (`tools/fixture-binding.ts`): a validate answer carries the status, the layers not evaluated
 * and every finding with its severity; a validate request may set the bounds of section 12.2;
 * and a registry request says whether a set of registry files is accepted.
 */

const BINDING = fileURLToPath(new URL('../tools/fixture-binding.ts', import.meta.url));

/** Sends requests to the binding, one per line, and returns its answers, one per request. */
function ask(...requests: object[]): Array<Record<string, unknown>> {
  const run = spawnSync(process.execPath, [BINDING], {
    input: requests.map((request) => JSON.stringify(request)).join('\n') + '\n',
    encoding: 'utf8',
    timeout: 60_000,
  });
  assert.equal(run.status, 0, run.stderr);
  const lines = run.stdout.trim().split('\n');
  assert.equal(lines.length, requests.length, run.stdout);
  return lines.map((line) => JSON.parse(line) as Record<string, unknown>);
}

const MINIMAL = JSON.parse(readFileSync(fileURLToPath(
  new URL('../../../examples/minimal.esj.json', import.meta.url)), 'utf8')) as {
  values: Record<string, unknown>;
};

test('a validate answer carries the status, what was not evaluated and every finding', () => {
  const [valid, unknown, invalid] = ask(
    { op: 'validate', file: 'examples/minimal.esj.json' },
    { op: 'validate', document: { ...MINIMAL,
      values: { ...MINIMAL.values, '/BG-ZZZ-1/0/BT-ZZZ-2': 'x' } } },
    { op: 'validate', file: 'examples/invalid/duplicate-member.esj.json' },
  );
  assert.deepEqual(valid, { status: 'VALID', notEvaluated: [], findings: [] });
  assert.equal(unknown.status, 'INDETERMINATE');
  assert.deepEqual(unknown.notEvaluated, []);
  assert.deepEqual((unknown.findings as unknown[])[0], {
    path: '/BG-ZZZ-1/0/BT-ZZZ-2', code: 'ESJ-L2-NOT-CHECKED', subject: '', severity: 'info',
  });
  assert.deepEqual(invalid, {
    status: 'INVALID',
    notEvaluated: [
      { layer: 'L2', reason: 'PRECEDING-LAYER-FAILED' },
      { layer: 'L3', reason: 'PRECEDING-LAYER-FAILED' },
    ],
    findings: [{
      path: '', code: 'ESJ-L1-DUPLICATE-MEMBER', subject: 'values["/BT-1"]', severity: 'error',
    }],
  });
});

test('a validate request may set the bounds of section 12.2 under their names', () => {
  const [stopped, refused] = ask(
    { op: 'validate', file: 'examples/minimal.esj.json', limits: { maxStringBytes: 16 } },
    { op: 'validate', file: 'examples/minimal.esj.json', limits: { maxStringByte: 16 } },
  );
  assert.deepEqual(stopped, {
    status: 'INDETERMINATE',
    notEvaluated: [{ layer: 'L2', reason: 'LIMIT' }, { layer: 'L3', reason: 'LIMIT' }],
    findings: [{ path: '', code: 'ESJ-L1-LIMIT', subject: 'format', severity: 'error' }],
  });
  assert.match(String(refused.error), /maxStringByte is not a limit/);
});

test('a registry request says whether the files are accepted, and why not', () => {
  const answers = ask(
    { op: 'registry', files: ['model/en16931/2017.json'] },
    { op: 'registry', files: ['model/en16931/2017.json', 'model/xrechnung/3.0.2.json',
      'model/b2c/0.1.json'] },
    { op: 'registry', files: ['model/en16931/2017.json', 'model/en16931/2017.json'] },
    { op: 'registry', files: [] },
  );
  assert.deepEqual(answers[0], { accepted: true });
  assert.deepEqual(answers[1], { accepted: true });
  assert.equal(answers[2].accepted, false);
  assert.match(String(answers[2].error), /own namespace only, and BT-1 carries none/);
  assert.ok(typeof answers[3].error === 'string');
});
