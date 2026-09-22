/**
 * Copies the registries and the rule pack of the repository into this package.
 *
 * The files under `model/` and `rules/` are the data both implementations read, and a copy
 * of them here is a copy and never a second source: nothing in `data/` is edited, the
 * directory is not checked in, and this script runs before every build and every test run.
 * A binding that hand-maintained a registry would be measuring documents against its own
 * idea of the model.
 */

import { cp, mkdir } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..', '..', '..');
const data = path.resolve(here, '..', 'data');

/** The directories of the repository this package carries a copy of. */
const DIRECTORIES = [
  'model/en16931',
  'model/xrechnung',
  'model/b2c',
  'rules/en16931',
];

await mkdir(data, { recursive: true });
for (const directory of DIRECTORIES) {
  await cp(path.join(root, directory), path.join(data, directory), {
    recursive: true,
    force: true,
  });
}
console.log('synchronised ' + DIRECTORIES.length + ' directories from ' + root);
