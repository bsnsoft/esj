import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import { canonicalize, pretty } from '../src/canonical.ts';
import { FindingCode } from '../src/codes.ts';
import { digestOfBytes, documentDigest, semanticDigest } from '../src/digest.ts';
import {
  ACCEPTED_VERSIONS, VERSION, documentOf, isAcceptedVersion, type SemanticDocument,
} from '../src/document.ts';
import { EsjError } from '../src/errors.ts';
import { readDocument, readDocumentOrThrow } from '../src/reader.ts';
import { validate } from '../src/validate.ts';
import { registries } from '../src/node/data.ts';

/**
 * The format version (specification, section 4.3): a reader of 1.0 reads 1.0 and 0.1, a
 * document keeps the version it was read with or built with, and both forms write that version.
 *
 * The documents of 0.1 below are written out here, with the canonical bytes and the digests
 * this implementation gave them before the format became 1.0, so that the files of the
 * repository can move to 1.0 and these stay a pin: a document of 0.1 read today canonicalizes
 * to the bytes it always had, and a digest a caller stored of it stays valid.
 */

const ROOT = path.resolve(fileURLToPath(new URL('../../..', import.meta.url)));

interface Pinned {
  /** The document as it was written, with `"version": "0.1"`. */
  readonly text: string;
  /** Its canonical bytes before 1.0. */
  readonly canonical: string;
  readonly semanticDigest: string;
  readonly documentDigest: string;
  /** The document digest of the same content written as 1.0, taken with `shasum -a 256`. */
  readonly documentDigestAs10: string;
}

/** The four-value document of appendix B, in the pretty form it was printed in. */
const APPENDIX_B: Pinned = {
  text: '{\n'
    + '  "format": "EN16931-Semantic-JSON",\n'
    + '  "version": "0.1",\n'
    + '  "semanticModel": "EN16931-1:2017+A1:2019/AC:2020",\n'
    + '  "values": {\n'
    + '    "/BT-1": "RE-2026-0001",\n'
    + '    "/BT-2": "2026-01-15",\n'
    + '    "/BG-4/BT-29/0": {\n'
    + '      "value": "0088123456785",\n'
    + '      "scheme": "0088"\n'
    + '    },\n'
    + '    "/BG-25/0/BT-131": "100"\n'
    + '  }\n'
    + '}\n',
  canonical: '{"format":"EN16931-Semantic-JSON","version":"0.1",'
    + '"semanticModel":"EN16931-1:2017+A1:2019/AC:2020","values":{"/BT-1":"RE-2026-0001",'
    + '"/BT-2":"2026-01-15","/BG-4/BT-29/0":{"value":"0088123456785","scheme":"0088"},'
    + '"/BG-25/0/BT-131":"100"}}',
  semanticDigest: '27d43ab507a929218e8278be315a736467d9a5f881bf232fe1843b06bd6105b8',
  documentDigest: 'fdc75e42190b6aec5cf5454fe07e615af71f33fc275e297cb6aeefec0525e42a',
  documentDigestAs10: 'a0c7676f92be041f21c5ed606d34452422c76fcace28539cbf206c02e2c8b24d',
};

/** The same content under the 2026 edition, as appendix B printed it. */
const APPENDIX_B_2026: Pinned = {
  text: '{\n'
    + '  "format": "EN16931-Semantic-JSON",\n'
    + '  "version": "0.1",\n'
    + '  "semanticModel": "EN16931-1:2026",\n'
    + '  "values": {\n'
    + '    "/BT-1": "RE-2026-0001",\n'
    + '    "/BT-2": "2026-01-15",\n'
    + '    "/BT-166": "09:30:00+02:00",\n'
    + '    "/BG-4/BT-29/0": {\n'
    + '      "value": "0088123456785",\n'
    + '      "scheme": "0088"\n'
    + '    },\n'
    + '    "/BG-25/0/BT-131": "100"\n'
    + '  }\n'
    + '}\n',
  canonical: '{"format":"EN16931-Semantic-JSON","version":"0.1","semanticModel":"EN16931-1:2026",'
    + '"values":{"/BT-1":"RE-2026-0001","/BT-2":"2026-01-15","/BT-166":"09:30:00+02:00",'
    + '"/BG-4/BT-29/0":{"value":"0088123456785","scheme":"0088"},"/BG-25/0/BT-131":"100"}}',
  semanticDigest: '8f12f3ea4a038fa8877b9b8084793c0ff4d90190dfe6897b4a44b04d464f2d1c',
  documentDigest: '38b3abe5cfa1d8f705844b7169b56d8aecd35ffbd9ae65ec0aaa83d4c8b7b10f',
  documentDigestAs10: 'b67916682ed2b76bffea3a3c7acc61e5cdc90e746900fc74fc3b909fb3f8bc5e',
};

/**
 * A document of 0.1 with every member of the envelope, in no canonical order, its version
 * spelled with an escape, a number of `extensions` in a spelling that is not canonical.
 */
const WHOLE_ENVELOPE: Pinned = {
  text: '{"source":{"syntax":"UBL","sha256":'
    + '"228f74263f1a2795e21bc4a659c60afbe31a4ba71392e335e3e5e68953731be5"},'
    + '"extensions":{"de.example.vendor":{"b":[3,1,2],"a":1.50}},'
    + '"values":{"/BT-2":"2026-01-15","/BT-1":"RE-2026-0001"},'
    + '"semanticModel":"EN16931-1:2017+A1:2019/AC:2020","version":"0.\\u0031",'
    + '"format":"EN16931-Semantic-JSON"}',
  canonical: '{"format":"EN16931-Semantic-JSON","version":"0.1",'
    + '"semanticModel":"EN16931-1:2017+A1:2019/AC:2020",'
    + '"values":{"/BT-1":"RE-2026-0001","/BT-2":"2026-01-15"},'
    + '"extensions":{"de.example.vendor":{"a":1.5,"b":[3,1,2]}},'
    + '"source":{"syntax":"UBL",'
    + '"sha256":"228f74263f1a2795e21bc4a659c60afbe31a4ba71392e335e3e5e68953731be5"}}',
  semanticDigest: '7d4f51967d94d16214961eeef118e9179fe1bba64d6c29a53677e7da44117973',
  documentDigest: '71bc7ef64e4194ee35f5707193c75cc01d68f3c10c6898a58273151196529816',
  documentDigestAs10: 'a7a5c8fbc82ce494b95e5faee5c5f8ead68acc4a58933460e6f3536d8d6c51c3',
};

const PINNED: Array<[string, Pinned]> = [
  ['appendix B', APPENDIX_B],
  ['appendix B under the 2026 edition', APPENDIX_B_2026],
  ['a document with every member of the envelope', WHOLE_ENVELOPE],
];

/** The text of a document with its `version` member replaced, wherever and however written. */
function withVersion(text: string, version: unknown): string {
  const json = JSON.parse(text) as Record<string, unknown>;
  json.version = version;
  return JSON.stringify(json);
}

/** The canonical bytes of a document of 0.1 with its content written as 1.0. */
function as10(canonical: string): string {
  return canonical.replace('"version":"0.1"', '"version":"1.0"');
}

function refusedVersion(failure: unknown): boolean {
  return failure instanceof EsjError && failure.code === FindingCode.L1_ENVELOPE_VALUE
    && failure.path === '' && failure.subject === 'version'
    && failure.message.includes('1.0') && failure.message.includes('0.1');
}

test('the format version is 1.0, and a reader of it takes 1.0 and 0.1', () => {
  assert.equal(VERSION, '1.0');
  assert.deepEqual([...ACCEPTED_VERSIONS], ['1.0', '0.1']);
  assert.ok(isAcceptedVersion('1.0') && isAcceptedVersion('0.1'));
  for (const other of ['1', '2.0', '1.1', '0.2', '1.00', ' 1.0', '']) {
    assert.ok(!isAcceptedVersion(other), JSON.stringify(other));
  }
});

for (const [name, pinned] of PINNED) {
  test('a document of 0.1 keeps its version, its canonical bytes and both digests: ' + name,
    async () => {
      const document = readDocumentOrThrow(pinned.text);
      assert.equal(document.version, '0.1');
      assert.equal(canonicalize(document), pinned.canonical);
      assert.equal(await semanticDigest(document), pinned.semanticDigest);
      assert.equal(await documentDigest(document), pinned.documentDigest);
      // What is derived of it keeps the version: its pretty form, read again, and its
      // canonical form, read again, are the same document of 0.1.
      for (const written of [pretty(document), canonicalize(document)]) {
        const again = readDocumentOrThrow(written);
        assert.equal(again.version, '0.1');
        assert.equal(canonicalize(again), pinned.canonical);
      }
    });

  test('a document of 1.0 is read, and differs from its 0.1 form in the document digest only: '
    + name, async () => {
    const document = readDocumentOrThrow(withVersion(pinned.text, '1.0'));
    assert.equal(document.version, '1.0');
    const canonical = canonicalize(document);
    assert.equal(canonical, as10(pinned.canonical));
    assert.equal(await semanticDigest(document), pinned.semanticDigest);
    assert.equal(await documentDigest(document), pinned.documentDigestAs10);
    assert.equal(await digestOfBytes(new TextEncoder().encode(canonical)),
      pinned.documentDigestAs10, 'the document digest is the SHA-256 of the canonical bytes');
    assert.notEqual(pinned.documentDigestAs10, pinned.documentDigest);
  });
}

test('the pretty form of a document of 0.1 is the layout it was printed in, version included',
  () => {
    for (const pinned of [APPENDIX_B, APPENDIX_B_2026]) {
      assert.equal(pretty(readDocumentOrThrow(pinned.text)), pinned.text);
      const as10Text = pinned.text.replace('"version": "0.1"', '"version": "1.0"');
      assert.equal(pretty(readDocumentOrThrow(as10Text)), as10Text);
    }
  });

test('a version spelled with escapes is the version it spells', () => {
  const document = readDocumentOrThrow(
    APPENDIX_B.text.replace('"version": "0.1"', '"version": "1.\\u0030"'));
  assert.equal(document.version, '1.0');
  assert.ok(canonicalize(document).includes('"version":"1.0"'));
});

test('any other version is refused, and the message names both versions a reader takes', () => {
  for (const version of ['2.0', '1', '1.1', '0.2', '1.00', '1.0 ', '', 'v1.0']) {
    const read = readDocument(withVersion(APPENDIX_B.text, version));
    assert.equal(read.document, undefined, JSON.stringify(version));
    assert.deepEqual(read.findings.map((entry) => [entry.code, entry.path, entry.subject]),
      [[FindingCode.L1_ENVELOPE_VALUE, '', 'version']], JSON.stringify(version));
    const message = read.findings[0].message;
    assert.ok(message.includes('1.0') && message.includes('0.1'), message);
    assert.throws(() => readDocumentOrThrow(withVersion(APPENDIX_B.text, version)),
      refusedVersion, JSON.stringify(version));
  }
  // A version that is no string is refused as a value of the wrong type, as it always was.
  for (const version of [1, 1.0, null, ['1.0'], { value: '1.0' }]) {
    const read = readDocument(withVersion(APPENDIX_B.text, version));
    assert.deepEqual(read.findings.map((entry) => [entry.code, entry.subject]),
      [[FindingCode.L1_ENVELOPE_VALUE, 'version']], JSON.stringify(version));
  }
});

test('a validator answers a document of 1.0 as it answers the same content of 0.1', () => {
  const carried = registries();
  const minimal = readFileSync(path.join(ROOT, 'examples', 'minimal.esj.json'), 'utf8');
  const answers = ['0.1', '1.0'].map((version) => {
    const result = validate(withVersion(minimal, version), { registries: carried });
    return {
      status: result.status,
      notEvaluated: result.notEvaluated,
      findings: result.findings.map((entry) => [entry.code, entry.path, entry.subject]),
    };
  });
  assert.deepEqual(answers[1], answers[0]);
  assert.equal(answers[0].status, 'VALID');
  const refused = validate(withVersion(minimal, '2.0'), { registries: carried });
  assert.equal(refused.status, 'INVALID');
  assert.deepEqual(refused.findings.map((entry) => [entry.code, entry.subject]),
    [[FindingCode.L1_ENVELOPE_VALUE, 'version']]);
  assert.deepEqual(refused.notEvaluated.map((entry) => entry.layer + ':' + entry.reason),
    ['L2:PRECEDING-LAYER-FAILED', 'L3:PRECEDING-LAYER-FAILED']);
});

test('a document built from nothing is 1.0; one built from another keeps its version',
  async () => {
    const built = documentOf({
      semanticModel: 'EN16931-1:2017+A1:2019/AC:2020',
      values: [...readDocumentOrThrow(APPENDIX_B.text).values],
    });
    assert.equal(built.version, VERSION);
    assert.equal(canonicalize(built), as10(APPENDIX_B.canonical));
    assert.equal(await documentDigest(built), APPENDIX_B.documentDigestAs10);

    const source = readDocumentOrThrow(APPENDIX_B.text);
    const derived = documentOf({
      ...source,
      values: [...source.values, ['/BT-3', { value: '380' }]],
    });
    assert.equal(derived.version, '0.1');
    assert.ok(canonicalize(derived).startsWith(
      '{"format":"EN16931-Semantic-JSON","version":"0.1",'));
    assert.equal(await semanticDigest(documentOf({ ...source, values: source.values })),
      APPENDIX_B.semanticDigest);
    assert.equal(await documentDigest(documentOf({ ...source, values: source.values })),
      APPENDIX_B.documentDigest);
  });

test('a document of a version a reader refuses cannot be built and has no canonical form',
  async () => {
    for (const version of ['2.0', '1', '9.9']) {
      assert.throws(() => documentOf({
        version, semanticModel: 'EN16931-1:2017+A1:2019/AC:2020', values: [],
      }), refusedVersion, version);
      // A document a caller assembled without documentOf is held to the same rule where it
      // is written.
      const assembled: SemanticDocument = {
        ...readDocumentOrThrow(APPENDIX_B.text), version,
      };
      assert.throws(() => canonicalize(assembled), refusedVersion, version);
      assert.throws(() => pretty(assembled), refusedVersion, version);
      await assert.rejects(documentDigest(assembled), refusedVersion, version);
    }
  });
