import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { projectVersion } from '../scripts/sync-version.mjs';

/**
 * The package carries the version of the Maven project it is part of, and the name the
 * project publishes it under. `npm run sync-version` writes the version; this is what fails
 * until it has.
 */

const PACKAGE = path.resolve(fileURLToPath(new URL('..', import.meta.url)));

function json(name: string): Record<string, any> {
  return JSON.parse(readFileSync(path.join(PACKAGE, name), 'utf8')) as Record<string, any>;
}

test('the package and its lock file carry the version of the project', () => {
  const version = projectVersion();
  assert.equal(json('package.json').version, version, 'npm run sync-version');
  assert.equal(json('package-lock.json').version, version, 'npm run sync-version');
  assert.equal(json('package-lock.json').packages[''].version, version, 'npm run sync-version');
});

test('the package is published under the name of its publisher', () => {
  assert.equal(json('package.json').name, '@bsnsoft/esj');
  assert.equal(json('package-lock.json').name, '@bsnsoft/esj');
});
