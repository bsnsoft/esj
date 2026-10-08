import { test } from 'node:test';
import assert from 'node:assert/strict';
import type { RegistryComponent, RegistryFile, RegistryTerm } from '../src/registry.ts';
import { RegistryError, registryOf } from '../src/registry.ts';
import { Structure } from '../src/structure.ts';
import { registries } from '../src/node/data.ts';
import { instanceStem } from '../scripts/naming.ts';

/**
 * What a registry is checked for when it is read and when it is combined with another
 * (specification, sections 10 and 11.1): a registry no value can satisfy, or an extension that
 * cannot belong to the edition it is combined with, is refused where it is given.
 */

const CORE_EDITION = 'EN 16931-1:2017+A1:2019/AC:2020';

function term(id: string, overrides: Partial<RegistryTerm> = {}): RegistryTerm {
  const parent = overrides.parent ?? null;
  return {
    id,
    kind: id.startsWith('BG') ? 'BG' : 'BT',
    name: id,
    slug: 'term' + id.replace(/[^0-9]/g, ''),
    parent,
    path: parent === null ? [id] : [parent, id],
    min: 0,
    max: 1,
    datatype: id.startsWith('BG') ? null : 'Text',
    components: [],
    ...overrides,
  };
}

function component(role: RegistryComponent['role'], min: number): RegistryComponent {
  return { role, min, max: 1 };
}

function file(terms: RegistryTerm[], header: Partial<RegistryFile> = {}): RegistryFile {
  return {
    format: 'EN16931-Semantic-JSON-registry',
    version: '0.1',
    model: 'EN16931-1',
    edition: CORE_EDITION,
    terms,
    ...header,
  };
}

function refused(build: () => unknown, why: RegExp): void {
  assert.throws(build, (failure: unknown) => failure instanceof RegistryError
    && why.test(failure.message), why.source);
}

test('the registries of the repository are read and combined as they are', () => {
  const carried = registries();
  const core = carried.find((registry) => registry.edition === CORE_EDITION)!;
  const extensions = carried.filter((registry) => registry.isExtension());
  assert.equal(extensions.length, 2);
  assert.doesNotThrow(() => new Structure(core, extensions));
});

test('a term no value can satisfy is refused when the registry is read', () => {
  const binary = (components: RegistryComponent[]) => file([
    term('BT-1', { datatype: 'BinaryObject', components }),
  ]);
  refused(() => registryOf(binary([])), /carries the components mimeCode and filename/);
  refused(() => registryOf(binary([component('mimeCode', 1)])),
    /carries the components mimeCode and filename/);
  refused(() => registryOf(binary([component('mimeCode', 1), component('filename', 0)])),
    /filename of the Binary Object BT-1 is mandatory/);
  assert.doesNotThrow(() => registryOf(binary([component('mimeCode', 1),
    component('filename', 1)])));
  const identifier = (components: RegistryComponent[]) => file([
    term('BT-1', { datatype: 'Identifier', components }),
  ]);
  refused(() => registryOf(identifier([component('schemeVersion', 0)])),
    /scheme version without a scheme/);
  refused(() => registryOf(identifier([component('scheme', 0), component('schemeVersion', 1)])),
    /scheme version mandatory and its scheme optional/);
  refused(() => registryOf(identifier([component('scheme', 0), component('scheme', 0)])),
    /lists the component scheme twice/);
  refused(() => registryOf(identifier([component('mimeCode', 1)])),
    /Identifier of BT-1 has no component mimeCode/);
  refused(() => registryOf(file([term('BT-1', { components: [component('scheme', 0)] })])),
    /Text of BT-1 has no component scheme/);
  assert.doesNotThrow(() => registryOf(identifier([component('scheme', 1),
    component('schemeVersion', 0)])));
});

test('an identifier listed twice is refused, never overwritten', () => {
  refused(() => registryOf(file([term('BT-1'), term('BT-1')])), /lists BT-1 twice/);
});

test('a registry of another shape is refused as one', () => {
  refused(() => registryOf({ model: 'EN16931-1' } as unknown as RegistryFile),
    /carries model, edition and terms/);
  refused(() => registryOf(file([{ ...term('BT-1'), path: ['BT-2'] }])), /the term BT-1/);
});

/** An extension that hangs one term under BG-25 of the core and reuses BT-131 in a group. */
function extension(header: Partial<RegistryFile>, terms?: RegistryTerm[]): RegistryFile {
  return file(terms ?? [
    term('BT-ZZZ-1', { parent: 'BG-25', path: ['BG-25', 'BT-ZZZ-1'] }),
    term('BG-ZZZ-2', { reusesTerms: ['BT-131'] }),
  ], { model: 'ZZZ', edition: 'ZZZ 1.0', ...header });
}

test('an extension that names core terms and imports nothing is refused when it is read', () => {
  refused(() => registryOf(extension({})), /names BG-25, which it does not define, and imports/);
  const reuses = extension({}, [term('BG-ZZZ-2', { reusesTerms: ['BT-131'] })]);
  refused(() => registryOf(reuses), /names BT-131, which it does not define/);
});

test('an extension is combined only with the edition it imports', () => {
  const carried = registries();
  const core2017 = carried.find((registry) => registry.edition === CORE_EDITION)!;
  const core2026 = carried.find((registry) => registry.edition === 'EN 16931-1:2026');
  const imports2017 = registryOf(extension({
    imports: [{ model: 'EN16931-1', edition: CORE_EDITION }],
  }));
  assert.doesNotThrow(() => new Structure(core2017, [imports2017]));
  if (core2026 !== undefined) {
    refused(() => new Structure(core2026, [imports2017]),
      /written against EN16931-1 EN 16931-1:2017\+A1:2019\/AC:2020, and this registry describes/);
  }
  const foreign = registryOf(extension({ imports: [{ model: 'OTHER', edition: 'OTHER 1' }] }));
  refused(() => new Structure(core2017, [foreign]),
    /names BG-25 of a registry it does not import: it imports OTHER OTHER 1, not EN16931-1/);
});

test('an extension does not redefine a term the structure already carries', () => {
  const core = registries().find((registry) => registry.edition === CORE_EDITION)!;
  const imports = [{ model: 'EN16931-1', edition: CORE_EDITION }];
  const redefines = registryOf(extension({ imports }, [term('BT-1')]));
  refused(() => new Structure(core, [redefines]), /does not redefine the term BT-1/);
  const one = registryOf(extension({ imports, edition: 'ZZZ 1.0' }));
  const two = registryOf(extension({ imports, edition: 'ZZZ 1.1' }));
  refused(() => new Structure(core, [one, two]),
    /does not redefine the term BT-ZZZ-1, which .* or another extension defines/);
});

test('a repeatable group is named by the singular of its plural slug, and no other', () => {
  const group = (slug: string, max: number | 'n') => term('BG-1', { slug, max });
  assert.equal(instanceStem(group('invoiceLines', 'n')), 'invoiceLine');
  assert.equal(instanceStem(group('latePaymentPenalties', 'n')), 'latePaymentPenalty');
  assert.equal(instanceStem(group('seller', 1)), 'seller');
  assert.throws(() => instanceStem(group('invoiceLine', 'n')), /is no plural/);
  assert.throws(() => instanceStem(group('data', 2)), /is no plural/);
});
