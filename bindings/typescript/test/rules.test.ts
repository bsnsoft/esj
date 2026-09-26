import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { readDocumentOrThrow } from '../src/reader.ts';
import { Structure } from '../src/structure.ts';
import type { SemanticDocument } from '../src/document.ts';
import { compile } from '../src/rules/engine.ts';
import type { RulePackFile } from '../src/rules/pack.ts';
import { registries } from '../src/node/data.ts';

/**
 * A rule that names the case it has no answer for, with `undecided`: where the case holds the
 * rule is not decided and says why, and nothing else of it is weighed; where the case does not
 * hold, or cannot be decided, the assertion decides as usual.
 */

const ROOT = path.resolve(fileURLToPath(new URL('../../..', import.meta.url)));

const EDITION = 'EN16931-1:2017+A1:2019/AC:2020';

const CORE = registries().find(
  (registry) => !registry.isExtension() && registry.semanticModel === EDITION)!;

const PACK: RulePackFile = {
  id: 'test',
  version: '1',
  edition: CORE.edition,
  description: 'A pack the tests write.',
  rules: [{
    id: 'BR-TEST',
    severity: 'fatal',
    oracle: 'cases',
    context: '/BG-25/*',
    terms: ['BT-129'],
    assert: { eq: [{ value: '/BG-29/BT-146' },
      { div: [{ value: '/BT-131' }, { value: '/BT-129' }] }] },
    undecided: {
      when: { eq: [{ value: '/BT-129' }, { const: '0' }] },
      message: 'the quantity at {@/BT-129} is {/BT-129}, and a price per unit of no units is'
        + ' no number',
    },
    message: 'BR-TEST does not hold.',
    source: 'EN 16931-1, 6.4',
  }],
};

const ENGINE = compile(PACK, new Structure(CORE, []));

/** The minimal example with the quantity of its one line replaced, or removed. */
function minimal(quantity: string | undefined): SemanticDocument {
  const written = JSON.parse(readFileSync(path.join(ROOT, 'examples', 'minimal.esj.json'),
    'utf8')) as { values: Record<string, string> };
  if (quantity === undefined) {
    delete written.values['/BG-25/0/BT-129'];
  } else {
    written.values['/BG-25/0/BT-129'] = quantity;
  }
  return readDocumentOrThrow(JSON.stringify(written));
}

test('where the case a rule names holds, the rule is not decided and says why', () => {
  const findings = ENGINE.evaluate(minimal('0'));

  assert.equal(findings.length, 1);
  assert.equal(findings[0].rule, 'BR-TEST');
  assert.equal(findings[0].severity, 'info');
  assert.equal(findings[0].message, 'not decided: the quantity at /BG-25/0/BT-129 is 0, and a'
    + ' price per unit of no units is no number');
});

test('where the case does not hold, or cannot be decided, the assertion decides', () => {
  assert.deepEqual(ENGINE.evaluate(minimal('1')), []);
  assert.deepEqual(ENGINE.evaluate(minimal('2')).map((finding) => finding.severity), ['fatal']);
  assert.deepEqual(ENGINE.evaluate(minimal(undefined)), []);
});
