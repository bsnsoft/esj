import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  comparePaths, groupPaths, lastTermId, parsePath, pathText, splitSegments, termIds,
} from '../src/paths.ts';
import { validate } from '../src/validate.ts';
import { registries } from '../src/node/data.ts';

/**
 * Semantic paths, and what a validator says about a value whose content does not spell what
 * its term requires.
 */

const REGISTRIES = registries();

test('a path is held as its segments, and reads back as it was written', () => {
  const path = '/BG-25/0/BG-31/BT-158/0';
  const segments = parsePath(path);
  assert.equal(pathText(segments), path);
  assert.deepEqual(termIds(segments), ['BG-25', 'BG-31', 'BT-158']);
  assert.equal(lastTermId(segments), 'BT-158');
  assert.deepEqual(groupPaths(segments), ['/BG-25/0', '/BG-25/0/BG-31']);
});

test('a member name that is not a path is not read as one', () => {
  assert.throws(() => parsePath('/BT-1x'), /not a semantic path/);
  assert.throws(() => parsePath('/BG-25/00/BT-131'), /not a semantic path/);
});

test('the canonical order is total over paths a registry would refuse', () => {
  const index = splitSegments('/BT-1/0');
  const term = splitSegments('/BT-1/BT-2');
  assert.ok(comparePaths(term, index) < 0, 'a term segment comes before an index segment');
  assert.equal(comparePaths(splitSegments('/BT-1'), splitSegments('/BT-1')), 0);
  assert.ok(comparePaths(splitSegments('/BT-1'), splitSegments('/BT-1/0')) < 0,
    'the shorter path comes first where one is a prefix of the other');
});

function envelope(values: Record<string, unknown>): string {
  return JSON.stringify({
    format: 'EN16931-Semantic-JSON',
    version: '0.1',
    semanticModel: 'EN16931-1:2017+A1:2019/AC:2020',
    values,
  });
}

test('a finding says which rule of the grammar the content broke', () => {
  const cases: Array<[string, string, RegExp]> = [
    ['/BG-22/BT-106', '100.00', /trailing zeros/],
    ['/BG-22/BT-106', '1E2', /exponent/],
    ['/BG-22/BT-106', '-0', /signs a zero/],
    ['/BT-2', '2026-02-30', /does not exist in the calendar/],
    ['/BT-2', '15.01.2026', /written YYYY-MM-DD/],
  ];
  for (const [path, content, why] of cases) {
    const result = validate(envelope({ [path]: content }), { registries: REGISTRIES });
    const message = result.findings.find((entry) => entry.path === path)?.message ?? '';
    assert.match(message, why, path + ' = ' + content);
  }
});
