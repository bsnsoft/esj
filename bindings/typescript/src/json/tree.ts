/**
 * The JSON tree an ESJ reader parses into.
 *
 * It is not what `JSON.parse` produces, and the two differences are the reason this exists.
 * A member name that occurs twice is kept rather than collapsed, because the specification
 * forbids a reader to silently keep the first or the last of them (section 4.2, rule 3); and
 * a number keeps the spelling the document gave it, because the canonical form of a number
 * inside `extensions` is computed from its lexical form and never from a binary floating
 * point value (section 7.6).
 */
export type JsonNode = JsonObject | JsonArray | JsonString | JsonNumber | JsonBoolean | JsonNull;

/**
 * An object, with its members in the order the document wrote them.
 *
 * A name that occurs twice is two members here and is never collapsed into one: the reader
 * walks them in the order the text wrote them, which is the order section 9.6 ranks a member
 * name it cannot take by.
 */
export interface JsonObject {
  readonly t: 'object';
  readonly members: readonly JsonMember[];
}

/** One member of an object. */
export interface JsonMember {
  readonly name: string;
  readonly value: JsonNode;
}

/** An array, whose element order is significant and preserved. */
export interface JsonArray {
  readonly t: 'array';
  readonly items: readonly JsonNode[];
}

/** A string, with the line endings of section 6.8 not yet normalized. */
export interface JsonString {
  readonly t: 'string';
  readonly value: string;
}

/** A number, kept as the document spells it. */
export interface JsonNumber {
  readonly t: 'number';
  readonly raw: string;
}

/** `true` or `false`. */
export interface JsonBoolean {
  readonly t: 'boolean';
  readonly value: boolean;
}

/** `null`. */
export interface JsonNull {
  readonly t: 'null';
}

/** Returns the member of an object with that name, or `undefined`. */
export function member(node: JsonObject, name: string): JsonNode | undefined {
  for (const entry of node.members) {
    if (entry.name === name) {
      return entry.value;
    }
  }
  return undefined;
}

/** Turns a tree back into the plain JavaScript value a caller of the library expects. */
export function toPlain(node: JsonNode): unknown {
  switch (node.t) {
    case 'object': {
      const out: Record<string, unknown> = {};
      for (const entry of node.members) {
        out[entry.name] = toPlain(entry.value);
      }
      return out;
    }
    case 'array':
      return node.items.map(toPlain);
    case 'string':
      return node.value;
    case 'number':
      return node.raw;
    case 'boolean':
      return node.value;
    case 'null':
      return null;
  }
}
