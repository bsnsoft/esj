import { Decimal } from '../decimal.ts';
import { comparePathText } from '../paths.ts';
import type { NativeRule, RuleContext } from './engine.ts';
import { en16931NativeRules } from './native.ts';

/**
 * The rules of the EN 16931-1:2026 pack that the rule language cannot express.
 *
 * The pack names them by the classes of the reference implementation, and this module
 * supplies rules of its own under the same identifiers. Eight of them are the rules of the
 * pack of the 2017 edition, unchanged: the four that ask a scheme of the electronic addresses
 * and of the item identifiers, and the four that ask a stated scheme to be on its list. The
 * rest are this edition's: seven party identifiers that carry their scheme, one text per
 * exemption reason code, the VAT identifier prefixes, the VAT categories compared with the
 * parts of the invoice under them, which this edition divides by exemption reason and by
 * goods or services code, the total VAT amount, one breakdown per combination, and the
 * breakdowns in the VAT accounting currency compared with those in the invoice currency.
 *
 * No official validation artefact exists for this edition. What stands behind each rule is
 * the `oracle` of the pack's manifest; nothing here is corroborated by an artefact.
 *
 * This module carries facts of that edition and is left out of a distribution that does not
 * ship it (`model/en16931/2026.paths`), together with the pack it serves.
 */

/** The identifier of the pack these rules belong to. */
export const PACK_ID = 'en16931-2026';

/** The rules of the pack of the 2017 edition that this pack takes over unchanged. */
const TAKEN_OVER = new Set(['BR-62', 'BR-63', 'BR-64', 'BR-65', 'BR-CL-07', 'BR-CL-11',
  'BR-CL-13', 'BR-CL-25']);

/** Returns the first segments of a path, which is the group instance a value lies in. */
function prefixOf(path: string, segments: number): string {
  return '/' + path.slice(1).split('/').slice(0, segments).join('/');
}

/** Returns the path of the group instance a value lies directly in. */
function parentOf(path: string): string {
  return path.slice(0, path.lastIndexOf('/'));
}

/** The character that keeps the parts of a key apart; no code or text of a term carries it. */
const SEPARATOR = '\u001f';

/**
 * One instance of a group that carries a VAT category: the invoice line, the document level
 * allowance, the document level charge or tax, or the VAT breakdown.
 */
interface Part {
  readonly path: string;
  readonly category?: string;
  readonly rate?: Decimal;
  readonly amount?: Decimal;
  readonly exemptionCode?: string;
  readonly exemptionText?: string;
  readonly goodsCode?: string;
}

/** The six terms of one group, in the order category, rate, amount, code, text, goods code. */
const BREAKDOWNS = ['/BG-23/*/BT-118', '/BG-23/*/BT-119', '/BG-23/*/BT-116',
  '/BG-23/*/BT-121', '/BG-23/*/BT-120', '/BG-23/*/BT-210'];
const LINES = ['/BG-25/*/BG-30/BT-151', '/BG-25/*/BG-30/BT-152', '/BG-25/*/BT-131',
  '/BG-25/*/BG-30/BT-195', '/BG-25/*/BG-30/BT-194', '/BG-25/*/BG-31/BT-196'];
const ALLOWANCES = ['/BG-20/*/BT-95', '/BG-20/*/BT-96', '/BG-20/*/BT-92',
  '/BG-20/*/BT-174', '/BG-20/*/BT-173', '/BG-20/*/BT-213'];
const CHARGES = ['/BG-21/*/BT-102', '/BG-21/*/BT-103', '/BG-21/*/BT-99',
  '/BG-21/*/BT-176', '/BG-21/*/BT-175', '/BG-21/*/BT-214'];

/** The business terms every VAT rule of this module reads. */
const VAT_TERMS = ['BT-5', 'BT-92', 'BT-95', 'BT-96', 'BT-99', 'BT-102', 'BT-103', 'BT-116',
  'BT-117', 'BT-118', 'BT-119', 'BT-120', 'BT-121', 'BT-131', 'BT-151', 'BT-152', 'BT-173',
  'BT-174', 'BT-175', 'BT-176', 'BT-184', 'BT-194', 'BT-195', 'BT-196', 'BT-210', 'BT-213',
  'BT-214'];

/**
 * Reads one of the four groups, each term as a pattern, joined by the instance the values lie
 * in; one pass per term, made once for a whole run.
 */
function join(context: RuleContext, key: string, patterns: readonly string[]): Part[] {
  return context.shared(key, () => {
    const texts = (pattern: string): Map<string, string> => new Map(context.texts(pattern)
      .map(([path, text]): [string, string] => [prefixOf(path, 2), text]));
    const numbers = (pattern: string): Map<string, Decimal> => new Map(context.decimals(pattern)
      .map(([path, number]): [string, Decimal] => [prefixOf(path, 2), number]));
    const categories = texts(patterns[0]);
    const rates = numbers(patterns[1]);
    const amounts = numbers(patterns[2]);
    const codes = texts(patterns[3]);
    const reasons = texts(patterns[4]);
    const goods = texts(patterns[5]);
    const instances = [...new Set([...categories.keys(), ...amounts.keys()])];
    instances.sort(comparePathText);
    return instances.map((path): Part => ({
      path,
      category: categories.get(path),
      rate: rates.get(path),
      amount: amounts.get(path),
      exemptionCode: codes.get(path),
      exemptionText: reasons.get(path),
      goodsCode: goods.get(path),
    }));
  });
}

const allBreakdowns = (context: RuleContext): Part[] =>
  join(context, 'vat26.allBreakdowns', BREAKDOWNS);

/** The currency each VAT breakdown names for its amounts (BT-184), by breakdown. */
function currencies(context: RuleContext): Map<string, string> {
  return context.shared('vat26.currencies', () => new Map(context.texts('/BG-23/*/BT-184')
    .map(([path, text]): [string, string] => [prefixOf(path, 2), text])));
}

/** The tax amount each VAT breakdown states (BT-117), by breakdown. */
function taxAmounts(context: RuleContext): Map<string, Decimal> {
  return context.shared('vat26.taxAmounts', () => new Map(context.decimals('/BG-23/*/BT-117')
    .map(([path, number]): [string, Decimal] => [prefixOf(path, 2), number])));
}

/** Tells whether a breakdown states its amounts in the invoice currency (BT-5). */
function inInvoiceCurrency(context: RuleContext, breakdown: Part): boolean {
  const currency = currencies(context).get(breakdown.path);
  return currency === undefined || currency === context.texts('/BT-5')[0]?.[1];
}

/**
 * The VAT breakdowns in the invoice currency: those that name no currency (BT-184) or name
 * the invoice currency. Every comparison of a breakdown with the parts under it is one of
 * these; a breakdown in the VAT accounting currency is compared by BR-CO-49 and BR-CO-50.
 */
const breakdowns = (context: RuleContext): Part[] => context.shared('vat26.breakdowns',
  () => allBreakdowns(context).filter((breakdown) => inInvoiceCurrency(context, breakdown)));

/** The VAT breakdowns that name a currency other than the invoice currency. */
const accounting = (context: RuleContext): Part[] => context.shared('vat26.accounting',
  () => allBreakdowns(context).filter((breakdown) => !inInvoiceCurrency(context, breakdown)));
const lines = (context: RuleContext): Part[] => join(context, 'vat26.lines', LINES);
const allowances = (context: RuleContext): Part[] => join(context, 'vat26.allowances', ALLOWANCES);
const charges = (context: RuleContext): Part[] => join(context, 'vat26.charges', CHARGES);

/** Tells whether a part states any of the three terms this edition divides a category by. */
function statesAReason(part: Part): boolean {
  return part.exemptionCode !== undefined || part.exemptionText !== undefined
    || part.goodsCode !== undefined;
}

/** What the exemption reason and the goods code of a part are, for a message. */
function reasonOf(part: Part): string {
  const said: string[] = [];
  if (part.exemptionCode !== undefined) {
    said.push('the exemption reason and specification code ' + part.exemptionCode);
  }
  if (part.exemptionText !== undefined) {
    said.push('an exemption reason text');
  }
  if (part.goodsCode !== undefined) {
    said.push('the goods or services code ' + part.goodsCode);
  }
  return said.length === 0 ? '' : ' with ' + said.join(', ');
}

/** The key a part is matched on, without the rate. */
function keyOf(part: Part, divided: boolean): string {
  const values = divided
    ? [part.category, part.exemptionCode, part.exemptionText, part.goodsCode]
    : [part.category];
  return values.map((value) => (value ?? '') + SEPARATOR).join('');
}

/** The key with the rate, which the categories that levy at a rate match on. */
function keyWithRate(part: Part, divided: boolean): string {
  return keyOf(part, divided) + SEPARATOR + '@'
    + (part.rate === undefined ? '' : part.rate.toString());
}

/**
 * What a rule compares two parts on, as a key of a set: the category, the rate where it is
 * compared, and — where the invoice divides the category — the exemption reason code, the
 * exemption reason text and, where it is compared, the goods or services code. Two keys are
 * the same exactly where the parts agree on each of those as `===` does; the rate is written
 * in its canonical form, so that 19 and 19.00 are one key, as they are one number.
 */
function combinationOf(
  part: Part, withRate: boolean, divides: boolean, withGoodsCode: boolean,
): string {
  const rate = withRate && part.rate !== undefined ? part.rate.toString() : null;
  return JSON.stringify(divides
    ? [part.category ?? null, rate, part.exemptionCode ?? null, part.exemptionText ?? null,
      withGoodsCode ? part.goodsCode ?? null : null]
    : [part.category ?? null, rate, null, null, null]);
}

/**
 * The VAT categories this invoice divides by exemption reason: those of which a line, an
 * allowance or a charge states an exemption reason code, an exemption reason text or a goods
 * or services code. Where none is stated, a part belongs to a breakdown by category alone.
 */
function divided(context: RuleContext): Set<string> {
  return context.shared('vat26.divided', () => {
    const codes = new Set<string>();
    for (const group of [lines(context), allowances(context), charges(context)]) {
      for (const part of group) {
        if (part.category !== undefined && statesAReason(part)) {
          codes.add(part.category);
        }
      }
    }
    return codes;
  });
}

/**
 * The combinations the VAT breakdowns in the invoice currency state, each once: a breakdown
 * with its rate, for a part that states one, and without it, for a part that states none.
 * Whether a part has a breakdown of its own is then one look-up, which is what makes the
 * question cost the number of parts plus the number of breakdowns and not their product.
 */
function combinations(context: RuleContext): Set<string> {
  return context.shared('vat26.combinations', () => {
    const split = divided(context);
    const stated = new Set<string>();
    for (const breakdown of breakdowns(context)) {
      if (breakdown.category === undefined) {
        continue;
      }
      const divides = split.has(breakdown.category);
      stated.add(combinationOf(breakdown, false, divides, true));
      stated.add(combinationOf(breakdown, true, divides, true));
    }
    return stated;
  });
}

/**
 * The VAT breakdowns in the invoice currency by the key a breakdown in the VAT accounting
 * currency is paired on; where two state the same key, the first in document order.
 */
function partners(context: RuleContext): Map<string, Part> {
  return context.shared('vat26.partners', () => {
    const first = new Map<string, Part>();
    for (const breakdown of breakdowns(context)) {
      const key = fullKey(breakdown);
      if (!first.has(key)) {
        first.set(key, breakdown);
      }
    }
    return first;
  });
}

/** The parts of the invoice of one VAT category, in document order. */
function partsOf(context: RuleContext, code: string): Part[] {
  return [lines(context), allowances(context), charges(context)]
    .flatMap((group) => group.filter((part) => part.category === code));
}

/** What the parts of the invoice come to, by key without the rate and by key with it. */
interface Totals {
  under(breakdown: Part, byRate: boolean): Decimal;
}

function totals(context: RuleContext): Totals {
  return context.shared('vat26.totals', () => {
    const split = divided(context);
    const byKey = new Map<string, Decimal>();
    const byKeyAndRate = new Map<string, Decimal>();
    const add = (parts: readonly Part[], subtract: boolean): void => {
      for (const part of parts) {
        if (part.category === undefined || part.amount === undefined) {
          continue;
        }
        const divides = split.has(part.category);
        const amount = subtract ? part.amount.negate() : part.amount;
        const key = keyOf(part, divides);
        byKey.set(key, (byKey.get(key) ?? Decimal.ZERO).add(amount));
        if (part.rate !== undefined) {
          const rated = keyWithRate(part, divides);
          byKeyAndRate.set(rated, (byKeyAndRate.get(rated) ?? Decimal.ZERO).add(amount));
        }
      }
    };
    add(lines(context), false);
    add(charges(context), false);
    add(allowances(context), true);
    return {
      under: (breakdown: Part, byRate: boolean): Decimal => {
        const divides = split.has(breakdown.category!);
        return byRate
          ? byKeyAndRate.get(keyWithRate(breakdown, divides)) ?? Decimal.ZERO
          : byKey.get(keyOf(breakdown, divides)) ?? Decimal.ZERO;
      },
    };
  });
}

/**
 * The number of fraction digits the currency of the invoice admits, or `undefined` where the
 * invoice names no currency or the snapshot gives it none.
 */
function minorUnit(context: RuleContext): number | undefined {
  const currency = context.texts('/BT-5')[0];
  return currency === undefined
    ? undefined
    : context.codeList('iso-4217').minorUnit(currency[1]);
}

/** The key of a breakdown with every term it states, which pairs two breakdowns. */
function fullKey(part: Part): string {
  return keyWithRate(part, true);
}

/**
 * The taxable amount of a VAT breakdown is what the parts of the invoice under it come to:
 * the line net amounts, plus the document level charges and taxes, minus the document level
 * allowances. A part is under a breakdown where the two agree on the category, on the rate
 * where the category levies at one, and — where the invoice divides the category — on the
 * exemption reason code, the exemption reason text and the goods or services code. The sum
 * is rounded once, half up, to the minor unit of the invoice currency, and compared exactly.
 */
function categoryTaxableAmount(
  id: string, code: string, name: string, byRate: boolean, source: string,
): NativeRule {
  return {
    id,
    context: '/',
    terms: VAT_TERMS,
    source,
    check: (context) => {
      const digits = minorUnit(context);
      if (digits === undefined) {
        return undefined;
      }
      const sums = totals(context);
      for (const breakdown of breakdowns(context)) {
        if (breakdown.category !== code || breakdown.amount === undefined) {
          continue;
        }
        if (byRate && breakdown.rate === undefined) {
          continue;
        }
        const expected = sums.under(breakdown, byRate).round(digits);
        if (breakdown.amount.compare(expected) === 0) {
          continue;
        }
        return 'The VAT breakdown at ' + breakdown.path + ' is categorised "' + code + '" ('
          + name + ')'
          + (byRate ? ' at the rate ' + breakdown.rate!.toString() + ' per cent' : '')
          + reasonOf(breakdown)
          + ' and states the taxable amount (BT-116) ' + breakdown.amount.toString()
          + '; the invoice lines, document level charges and taxes and document level'
          + ' allowances under that breakdown come to ' + expected.toString() + '.';
      }
      return undefined;
    },
  };
}

/**
 * An invoice that categorises a line, an allowance or a charge under a category in which no
 * tax is levied carries a VAT breakdown of that category for every exemption reason it used,
 * and for the intra-community category for every goods or services code as well.
 */
function breakdownPerReason(
  id: string, code: string, name: string, withGoodsCode: boolean, source: string,
): NativeRule {
  return {
    id,
    context: '/',
    terms: VAT_TERMS,
    source,
    check: (context) => {
      const divides = divided(context).has(code);
      const stated = new Set<string>();
      for (const breakdown of breakdowns(context)) {
        if (breakdown.category === code) {
          stated.add(combinationOf(breakdown, false, divides, withGoodsCode));
        }
      }
      for (const part of partsOf(context, code)) {
        if (!stated.has(combinationOf(part, false, divides, withGoodsCode))) {
          return 'The invoice states ' + part.path + ' under the VAT category "' + code
            + '" (' + name + ')' + reasonOf(part)
            + ', and no VAT breakdown (BG-23) of that category states the same exemption'
            + ' reason; this edition asks for one breakdown per reason.';
        }
      }
      return undefined;
    },
  };
}

/**
 * `BR-CO-14`: the tax amounts of the VAT breakdowns in the invoice currency add up to the
 * invoice total VAT amount (BT-110), rounded once to the minor unit of that currency and
 * compared exactly.
 */
function totalVatAmount(): NativeRule {
  return {
    id: 'BR-CO-14',
    context: '/',
    terms: [...VAT_TERMS, 'BT-110'],
    source: 'EN 16931-1:2026, 6.4.2, Table 4, BR-CO-14',
    check: (context) => {
      const stated = context.decimals('/BG-22/BT-110')[0];
      const digits = minorUnit(context);
      if (stated === undefined || digits === undefined) {
        return undefined;
      }
      const amounts = taxAmounts(context);
      let sum = Decimal.ZERO;
      for (const breakdown of breakdowns(context)) {
        sum = sum.add(amounts.get(breakdown.path) ?? Decimal.ZERO);
      }
      const expected = sum.round(digits);
      if (stated[1].compare(expected) === 0) {
        return undefined;
      }
      return 'The VAT category tax amounts (BT-117) of the VAT breakdowns in the invoice'
        + ' currency add up to ' + expected.toString() + ', and the invoice total VAT amount'
        + ' (BT-110) carries ' + stated[1].toString() + '.';
    },
  };
}

/**
 * `BR-CO-18`: every line, document level allowance and document level charge or tax finds a
 * VAT breakdown in the invoice currency of its category, of its rate where it states one,
 * and — where the invoice divides the category — of its exemption reason and goods code.
 */
function breakdownPerCombination(): NativeRule {
  return {
    id: 'BR-CO-18',
    context: '/',
    terms: VAT_TERMS,
    source: 'EN 16931-1:2026, 6.4.2, Table 4, BR-CO-18',
    check: (context) => {
      const stated = combinations(context);
      const split = divided(context);
      for (const group of [lines(context), allowances(context), charges(context)]) {
        for (const part of group) {
          if (part.category === undefined) {
            continue;
          }
          if (!stated.has(combinationOf(part, true, split.has(part.category), true))) {
            return 'The invoice states ' + part.path + ' under the VAT category "'
              + part.category + '"'
              + (part.rate === undefined ? '' : ' at the rate ' + part.rate.toString() + ' per cent')
              + reasonOf(part) + ', and no VAT breakdown (BG-23) in the invoice currency states'
              + ' that combination; this edition asks for one breakdown per combination of'
              + ' category, rate, exemption reason and goods or services code.';
          }
        }
      }
      return undefined;
    },
  };
}

/**
 * `BR-CO-49` and `BR-CO-50`: an amount of a VAT breakdown in the VAT accounting currency is
 * the amount of the breakdown in the invoice currency that states the same combination,
 * multiplied by the exchange rate (BT-167), rounded once to the minor unit of the accounting
 * currency, within the tolerance of clause 6.5.14 on the amount provided.
 */
function accountingConversion(id: string, term: string, name: string,
  amountOf: (context: RuleContext, breakdown: Part) => Decimal | undefined): NativeRule {
  return {
    id,
    context: '/',
    terms: [...VAT_TERMS, 'BT-6', 'BT-167'],
    source: 'EN 16931-1:2026, 6.4.2, Table 4, ' + id,
    check: (context) => {
      const rate = context.decimals('/BT-167')[0]?.[1];
      const currency = context.texts('/BT-6')[0]?.[1];
      if (rate === undefined || currency === undefined) {
        return undefined;
      }
      const digits = context.codeList('iso-4217').minorUnit(currency);
      if (digits === undefined) {
        return undefined;
      }
      const paired = partners(context);
      for (const converted of accounting(context)) {
        if (currencies(context).get(converted.path) !== currency) {
          continue;
        }
        const partner = paired.get(fullKey(converted));
        if (partner === undefined) {
          continue;
        }
        const stated = amountOf(context, converted);
        const original = amountOf(context, partner);
        if (stated === undefined || original === undefined) {
          continue;
        }
        const expected = original.multiply(rate).round(digits);
        const floor = Decimal.of('0.01');
        const share = Decimal.of('0.001').multiply(stated.abs());
        const widest = share.compare(floor) > 0 ? share : floor;
        const tolerance = widest.compare(Decimal.ONE) < 0 ? widest : Decimal.ONE;
        if (stated.subtract(expected).abs().compare(tolerance) <= 0) {
          continue;
        }
        return 'The VAT breakdown at ' + converted.path + ' states the ' + name + ' (' + term
          + ') ' + stated.toString() + ' in the VAT accounting currency '
          + context.escape(currency) + ', and the breakdown at ' + partner.path + ' states '
          + original.toString() + ' in the invoice currency, which at the exchange rate'
          + ' (BT-167) ' + rate.toString() + ' comes to ' + expected.toString() + '.';
      }
      return undefined;
    },
  };
}

/** An identifier that is only meaningful together with its scheme carries that scheme. */
function schemeStated(
  id: string, pattern: string, term: string, name: string,
): NativeRule {
  return {
    id,
    context: '/',
    terms: [term],
    source: 'EN 16931-1:2026, 6.4.1, Table 3, ' + id,
    check: (context) => {
      for (const [path, value] of context.values(pattern)) {
        if (value.scheme === undefined) {
          return 'The ' + name + ' (' + term + ') at ' + path + ' is '
            + context.escape(value.value) + ' and names no identification scheme; an'
            + ' identifier of this term is read together with its scheme.';
        }
      }
      return undefined;
    },
  };
}

/** The four places an exemption reason code and its text are stated, code and text. */
const REASON_CODES = ['/BG-23/*/BT-121', '/BG-25/*/BG-30/BT-195', '/BG-20/*/BT-174',
  '/BG-21/*/BT-176'];
const REASON_TEXTS = ['/BG-23/*/BT-120', '/BG-25/*/BG-30/BT-194', '/BG-20/*/BT-173',
  '/BG-21/*/BT-175'];

/** `BR-68`: one exemption reason code is explained by one exemption reason text throughout. */
function oneTextPerReasonCode(): NativeRule {
  return {
    id: 'BR-68',
    context: '/',
    terms: ['BT-120', 'BT-121', 'BT-173', 'BT-174', 'BT-175', 'BT-176', 'BT-194', 'BT-195'],
    source: 'EN 16931-1:2026, 6.4.1, Table 3, BR-68',
    check: (context) => {
      const seen = new Map<string, [string, string]>();
      for (let i = 0; i < REASON_CODES.length; i++) {
        const texts = new Map(context.texts(REASON_TEXTS[i])
          .map(([path, text]): [string, string] => [parentOf(path), text]));
        for (const [path, code] of context.texts(REASON_CODES[i])) {
          const text = texts.get(parentOf(path));
          if (text === undefined) {
            continue;
          }
          const first = seen.get(code);
          if (first === undefined) {
            seen.set(code, [path, text]);
            continue;
          }
          if (first[1] !== text) {
            return 'The exemption reason and specification code ' + context.escape(code)
              + ' is explained at ' + first[0] + ' by one exemption reason text and at ' + path
              + ' by another; one code carries one text throughout an invoice.';
          }
        }
      }
      return undefined;
    },
  };
}

/** The three VAT identifiers BR-CO-09 is about. */
const VAT_IDENTIFIERS: ReadonlyArray<[string, string]> = [
  ['/BG-4/BT-31', 'seller VAT identifier (BT-31)'],
  ['/BG-7/BT-48', 'buyer VAT identifier (BT-48)'],
  ['/BG-11/BT-63', 'seller tax representative VAT identifier (BT-63)'],
];

/**
 * The prefixes this edition admits beside the country codes of ISO 3166-1: Greece, Northern
 * Ireland, Kosovo and the three one stop shop schemes.
 */
const BESIDE_THE_LIST = new Set(['EL', 'XI', '1A', 'EU', 'IM', 'IN']);

/** `BR-CO-09`: a VAT identifier begins with the prefix of the state that issued it. */
function vatIdentifierCountry(): NativeRule {
  return {
    id: 'BR-CO-09',
    context: '/',
    terms: ['BT-31', 'BT-48', 'BT-63'],
    source: 'EN 16931-1:2026, 6.4.2, Table 4, BR-CO-9',
    check: (context) => {
      const countries = context.codeList('iso-3166-1');
      for (const [pattern, name] of VAT_IDENTIFIERS) {
        for (const [path, text] of context.texts(pattern)) {
          const prefix = text.length < 2 ? text : text.slice(0, 2);
          if (BESIDE_THE_LIST.has(prefix) || countries.contains(prefix)) {
            continue;
          }
          return 'The ' + name + ' at ' + path + ' is ' + context.escape(text)
            + ', and it begins with ' + context.escape(prefix)
            + ', which is not a country of the iso-3166-1 snapshot this pack decides against'
            + ' and is none of the prefixes this edition names beside it.';
        }
      }
      return undefined;
    },
  };
}

/**
 * The rules of the EN 16931-1:2026 pack this binding writes itself, one per identifier the
 * pack declares the rule language cannot express.
 *
 * @return the rules
 */
export function nativeRules(): NativeRule[] {
  return [
    ...en16931NativeRules().filter((rule) => TAKEN_OVER.has(rule.id)),
    oneTextPerReasonCode(),
    schemeStated('BR-69', '/BG-4/BT-29/*', 'BT-29', 'seller identifier'),
    schemeStated('BR-70', '/BG-4/BT-30', 'BT-30', 'seller legal registration identifier'),
    schemeStated('BR-71', '/BG-7/BT-46/*', 'BT-46', 'buyer identifier'),
    schemeStated('BR-72', '/BG-7/BT-47', 'BT-47', 'buyer legal registration identifier'),
    schemeStated('BR-73', '/BG-10/BT-60', 'BT-60', 'payee identifier'),
    schemeStated('BR-74', '/BG-10/BT-61', 'BT-61', 'payee legal registration identifier'),
    schemeStated('BR-75', '/BG-13/BT-71', 'BT-71', 'deliver to location identifier'),
    vatIdentifierCountry(),
    totalVatAmount(),
    breakdownPerCombination(),
    accountingConversion('BR-CO-49', 'BT-116', 'taxable amount',
      (_context, breakdown) => breakdown.amount),
    accountingConversion('BR-CO-50', 'BT-117', 'tax amount',
      (context, breakdown) => taxAmounts(context).get(breakdown.path)),
    categoryTaxableAmount('BR-S-08', 'S', 'standard or reduced rate', true,
      'EN 16931-1:2026, 6.4.3.3.2, Table 6, BR-S-8'),
    categoryTaxableAmount('BR-Z-08', 'Z', 'zero rated', false,
      'EN 16931-1:2026, 6.4.3.4.2, Table 7, BR-Z-8'),
    categoryTaxableAmount('BR-E-08', 'E', 'exempt from VAT', false,
      'EN 16931-1:2026, 6.4.3.4.4, Table 8, BR-E-8'),
    categoryTaxableAmount('BR-AE-08', 'AE', 'reverse charge', false,
      'EN 16931-1:2026, 6.4.3.4.6, Table 9, BR-AE-8'),
    categoryTaxableAmount('BR-IC-08', 'K', 'intra-community supply', false,
      'EN 16931-1:2026, 6.4.3.4.8, Table 10, BR-IC-8'),
    categoryTaxableAmount('BR-G-08', 'G', 'export outside the EU', false,
      'EN 16931-1:2026, 6.4.3.4.10, Table 11, BR-G-8'),
    categoryTaxableAmount('BR-O-08', 'O', 'not subject to VAT', false,
      'EN 16931-1:2026, 6.4.3.4.12, Table 12, BR-O-8'),
    categoryTaxableAmount('BR-IG-08', 'L', 'Canary Islands tax', false,
      'EN 16931-1:2026, 6.4.3.4.15, Table 14, BR-IG-8'),
    categoryTaxableAmount('BR-IP-08', 'M', 'Ceuta and Melilla tax', false,
      'EN 16931-1:2026, 6.4.3.4.15, Table 15, BR-IP-8'),
    breakdownPerReason('BR-E-01', 'E', 'exempt from VAT', false,
      'EN 16931-1:2026, 6.4.3.4.4, Table 8, BR-E-1'),
    breakdownPerReason('BR-AE-01', 'AE', 'reverse charge', false,
      'EN 16931-1:2026, 6.4.3.4.6, Table 9, BR-AE-1'),
    breakdownPerReason('BR-IC-01', 'K', 'intra-community supply', true,
      'EN 16931-1:2026, 6.4.3.4.8, Table 10, BR-IC-1'),
    breakdownPerReason('BR-G-01', 'G', 'export outside the EU', false,
      'EN 16931-1:2026, 6.4.3.4.10, Table 11, BR-G-1'),
    breakdownPerReason('BR-O-01', 'O', 'not subject to VAT', false,
      'EN 16931-1:2026, 6.4.3.4.12, Table 12, BR-O-1'),
  ];
}
