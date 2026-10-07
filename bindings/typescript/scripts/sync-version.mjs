/**
 * Writes the version of the Maven project this checkout builds into package.json and
 * package-lock.json, so that the package carries the version of the project it is part of.
 *
 *     node scripts/sync-version.mjs      # from bindings/typescript, or: npm run sync-version
 *
 * The version is read from the root pom.xml as it stands: 0.9.5-SNAPSHOT is a semantic
 * version, and so is 0.9.5. test/version.test.ts fails while the two disagree.
 */

import { readFileSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const PACKAGE = path.resolve(HERE, '..');
const ROOT = path.resolve(PACKAGE, '../..');

/** The version of the Maven project, from the root pom.xml. */
export function projectVersion() {
  const pom = readFileSync(path.join(ROOT, 'pom.xml'), 'utf8');
  const found = /<artifactId>en16931-semantic-json<\/artifactId>\s*<version>([^<]+)<\/version>/
    .exec(pom);
  if (found === null) {
    throw new Error('the root pom.xml names no version of en16931-semantic-json');
  }
  return found[1];
}

function rewrite(file, change) {
  const json = JSON.parse(readFileSync(file, 'utf8'));
  change(json);
  writeFileSync(file, JSON.stringify(json, null, 2) + '\n');
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const version = projectVersion();
  rewrite(path.join(PACKAGE, 'package.json'), (json) => {
    json.version = version;
  });
  rewrite(path.join(PACKAGE, 'package-lock.json'), (json) => {
    json.version = version;
    json.packages[''].version = version;
  });
  process.stdout.write(version + '\n');
}
