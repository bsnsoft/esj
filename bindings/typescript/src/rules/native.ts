import { Decimal } from '../decimal.ts';
import { comparePathText } from '../paths.ts';
import type { NativeRule, RuleContext } from './engine.ts';

/**
 * The rules of the EN 16931 pack that the rule language cannot express.
 *
 * The language addresses business terms, and three questions of the standard are not about a
 * business term: whether a value states the identification scheme it was issued under, and
 * whether that scheme is on a code list — a scheme is a supplementary component of a value
 * and no path reaches it; how many business group instances of a *filtered* set there are —
 * the language has no filter under an aggregate, on purpose, because one would make it a
 * query language; and what the first two characters of a value are.
 *
 * The pack declares these rules and names the classes of the reference implementation for
 * them. Naming is not loading: this module supplies rules of its own under the same
 * identifiers, and the compiler checks that exactly the declared set arrived. A finding
 * produced here is a finding of the same shape as one produced by a rule file — which side
 * of the line a rule fell on is an implementation detail and never a fact about the invoice.
 */

/** Returns the first segments of a path, which is the group instance a value lies in. */
function prefixOf(path: string, segments: number): string {
  return '/' + path.slice(1).split('/').slice(0, segments).join('/');
}

/** One instance of a group that carries a VAT category, with its rate and its amount. */
interface VatItem {
  readonly path: string;
  readonly category?: string;
  readonly rate?: Decimal;
  readonly amount?: Decimal;
}

/** The four groups of the model that carry a VAT category, as patterns from the root. */
const LINES = ['/BG-25/*/BG-30/BT-151', '/BG-25/*/BG-30/BT-152', '/BG-25/*/BT-131'];
const ALLOWANCES = ['/BG-20/*/BT-95', '/BG-20/*/BT-96', '/BG-20/*/BT-92'];
const CHARGES = ['/BG-21/*/BT-102', '/BG-21/*/BT-103', '/BG-21/*/BT-99'];
const BREAKDOWNS = ['/BG-23/*/BT-118', '/BG-23/*/BT-119', '/BG-23/*/BT-116'];

/** The business terms every VAT rule of this module reads. */
const VAT_TERMS = ['BT-92', 'BT-95', 'BT-96', 'BT-99', 'BT-102', 'BT-103',
  'BT-116', 'BT-118', 'BT-119', 'BT-131', 'BT-151', 'BT-152'];

/**
 * Reads one of the four groups: the VAT category, the rate and the amount of each instance,
 * joined by the instance they lie in.
 *
 * A rule may not write an occurrence index, so each business term is read as a pattern and
 * the answers are joined afterwards. That costs one pass per term and not one pass per
 * instance, and the join is made once for a whole run however many rules ask for it.
 */
function join(context: RuleContext, key: string, patterns: readonly string[]): VatItem[] {
  return context.shared(key, () => {
    const depth = 2;
    const categories = new Map<string, string>();
    for (const [path, text] of context.texts(patterns[0])) {
      categories.set(prefixOf(path, depth), text);
    }
    const rates = new Map<string, Decimal>();
    for (const [path, number] of context.decimals(patterns[1])) {
      rates.set(prefixOf(path, depth), number);
    }
    const amounts = new Map<string, Decimal>();
    for (const [path, number] of context.decimals(patterns[2])) {
      amounts.set(prefixOf(path, depth), number);
    }
    const instances = [...categories.keys()];
    for (const instance of amounts.keys()) {
      if (!categories.has(instance)) {
        instances.push(instance);
      }
    }
    instances.sort(comparePathText);
    return instances.map((path) => ({
      path,
      ...(categories.has(path) ? { category: categories.get(path)! } : {}),
      ...(rates.has(path) ? { rate: rates.get(path)! } : {}),
      ...(amounts.has(path) ? { amount: amounts.get(path)! } : {}),
    }));
  });
}

const lines = (context: RuleContext): VatItem[] => join(context, 'vat.lines', LINES);
const allowances = (context: RuleContext): VatItem[] => join(context, 'vat.allowances', ALLOWANCES);
const charges = (context: RuleContext): VatItem[] => join(context, 'vat.charges', CHARGES);
const breakdowns = (context: RuleContext): VatItem[] => join(context, 'vat.breakdowns', BREAKDOWNS);

/** What the lines, charges and allowances of each VAT category, and of each rate, come to. */
interface Totals {
  of(code: string): Decimal;
  at(code: string, rate: Decimal): Decimal;
}

function rateKey(code: string, rate: Decimal): string {
  return code + ' @ ' + rate.toString();
}

/**
 * Buckets the invoice lines, the document level charges and the document level allowances by
 * VAT category, and by category and rate, in one pass for the whole run.
 *
 * Without it a rule that compares a VAT breakdown with the parts of the invoice in its
 * category would walk the lines once per breakdown, and an invoice may carry many of both.
 */
function totals(context: RuleContext): Totals {
  return context.shared('vat.totals', () => {
    const byCategory = new Map<string, Decimal>();
    const byRate = new Map<string, Decimal>();
    const add = (items: readonly VatItem[], subtract: boolean): void => {
      for (const item of items) {
        if (item.category === undefined || item.amount === undefined) {
          continue;
        }
        const amount = subtract ? item.amount.negate() : item.amount;
        byCategory.set(item.category,
          (byCategory.get(item.category) ?? Decimal.ZERO).add(amount));
        if (item.rate !== undefined) {
          const key = rateKey(item.category, item.rate);
          byRate.set(key, (byRate.get(key) ?? Decimal.ZERO).add(amount));
        }
      }
    };
    add(lines(context), false);
    add(charges(context), false);
    add(allowances(context), true);
    return {
      of: (code: string) => byCategory.get(code) ?? Decimal.ZERO,
      at: (code: string, rate: Decimal) => byRate.get(rateKey(code, rate)) ?? Decimal.ZERO,
    };
  });
}

function countOf(items: readonly VatItem[], code: string): number {
  return items.filter((item) => item.category === code).length;
}

/** Tells whether an invoice line, a document level allowance or charge states a category. */
function used(context: RuleContext, code: string): boolean {
  return countOf(lines(context), code) > 0
    || countOf(allowances(context), code) > 0
    || countOf(charges(context), code) > 0;
}

/**
 * Which official artefact of release 1.3.16 admits a difference below one unit of the invoice
 * currency between the stated taxable amount and the sum, where the two do not ask the same.
 */
type Tolerance = 'neither' | 'ubl' | 'cii' | 'ubl-cii-silent';

const TOLERANCE_NOTE: Record<Tolerance, string> = {
  neither: '',
  ubl: ', and the official CII artefact of release 1.3.16 faults a difference this small.',
  cii: ', and the official UBL artefact of release 1.3.16 faults a difference this small.',
  'ubl-cii-silent': ', and the standard asks the two to be equal: the official UBL artefact of'
    + ' release 1.3.16 admits a difference this small, and the CII assertion of that release'
    + ' cannot report it.',
};

/**
 * The taxable amount of a VAT breakdown is what the parts of the invoice in that category
 * come to: the invoice line net amounts, plus the document level charges, minus the document
 * level allowances.
 *
 * Every VAT category of the standard states this, and no closed operator set reaches it: the
 * sum is over a filtered set, and for the categories that carry a rate over the lines of one
 * category at one rate.
 *
 * How closely the two figures have to agree is decided rule by rule, because the two official
 * artefacts of release 1.3.16 decide it rule by rule and do not agree with each other. A
 * difference of one unit of the invoice currency or more is faulted by both and is fatal
 * here; a difference inside a zone only one of them grants is a warning that says what is
 * true of that difference in the release.
 */
function categoryTaxableAmount(
  id: string, code: string, name: string, byRate: boolean, tolerance: Tolerance, source: string,
): NativeRule {
  const decide = (context: RuleContext, fatal: boolean): string | undefined => {
    const sums = totals(context);
    for (const breakdown of breakdowns(context)) {
      if (breakdown.category !== code || breakdown.amount === undefined) {
        continue;
      }
      if (byRate && breakdown.rate === undefined) {
        continue;
      }
      const expected = byRate ? sums.at(code, breakdown.rate!) : sums.of(code);
      const apart = breakdown.amount.subtract(expected).abs();
      const beyond = tolerance === 'neither'
        ? apart.signum() !== 0
        : apart.compare(Decimal.ONE) >= 0;
      const inside = !beyond && apart.signum() !== 0;
      if (fatal ? !beyond : !inside) {
        continue;
      }
      return 'The VAT breakdown at ' + breakdown.path + ' is categorised "' + code + '" ('
        + name + ')' + (byRate ? ' at the rate ' + breakdown.rate!.toString() + ' per cent' : '')
        + ' and states the taxable amount (BT-116) ' + breakdown.amount.toString()
        + '; the invoice lines, document level charges and document level allowances of that'
        + ' category come to ' + expected.toString()
        + (fatal
          ? (tolerance === 'neither' ? '.' : ', which is a unit of the invoice currency or more away.')
          : TOLERANCE_NOTE[tolerance]);
    }
    return undefined;
  };
  return {
    id,
    context: '/',
    terms: VAT_TERMS,
    source,
    check: (context) => decide(context, true),
    warn: (context) => tolerance === 'neither' ? undefined : decide(context, false),
  };
}

/**
 * An invoice that categorises anything under one of the VAT categories in which no VAT is
 * levied carries exactly one VAT breakdown group for that category.
 *
 * Six of the nine categories say *exactly* one; the three that say *at least* one are those
 * in which VAT is levied at a rate, where one breakdown per rate is the point. The rule
 * language can ask whether an instance of a given shape exists and cannot ask how many
 * instances of a filtered set there are.
 */
function exactlyOneBreakdown(
  id: string, code: string, name: string, source: string,
): NativeRule {
  return {
    id,
    context: '/',
    terms: ['BT-95', 'BT-102', 'BT-118', 'BT-151'],
    source,
    check: (context) => {
      if (!used(context, code)) {
        return undefined;
      }
      const found = countOf(breakdowns(context), code);
      if (found === 1) {
        return undefined;
      }
      return 'An invoice line, a document level allowance or a document level charge is'
        + ' categorised "' + code + '" (' + name + '), and the invoice carries ' + found
        + ' VAT breakdown groups (BG-23) with that VAT category code (BT-118) where exactly'
        + ' one belongs.';
    },
  };
}

/**
 * An identifier that is only meaningful together with the scheme it was issued under carries
 * that scheme. A scheme is a supplementary component of a value and not a business term, so
 * no path of the rule language reaches it.
 */
function schemeStated(
  id: string, pattern: string, term: string, name: string, source: string,
): NativeRule {
  return {
    id,
    context: '/',
    terms: [term],
    source,
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

/** One business term a membership rule is about. */
interface Scheme {
  readonly pattern: string;
  readonly term: string;
  readonly name: string;
}

/**
 * An identification scheme, where one is stated, is one the code list its business term names
 * carries. The scheme is optional on most of these terms, and a value that states none is not
 * a value these rules are about.
 */
function schemeInList(
  id: string, listId: string, source: string, schemes: readonly Scheme[],
): NativeRule {
  return {
    id,
    context: '/',
    terms: schemes.map((scheme) => scheme.term),
    source,
    check: (context) => {
      const list = context.codeList(listId);
      for (const scheme of schemes) {
        for (const [path, value] of context.values(scheme.pattern)) {
          if (value.scheme === undefined || list.contains(value.scheme)) {
            continue;
          }
          return 'The ' + scheme.name + ' (' + scheme.term + ') at ' + path
            + ' names the identification scheme ' + context.escape(value.scheme)
            + ', which is not on the ' + listId + ' snapshot this pack decides against.';
        }
      }
      return undefined;
    },
  };
}

/** The attachment whose media type BR-CL-24 is about. */
const ATTACHMENT = '/BG-24/*/BT-125';

/** The two electronic addresses BR-CL-25 is about. */
const ADDRESSES: ReadonlyArray<[string, string]> = [
  ['/BG-4/BT-34', 'seller electronic address (BT-34)'],
  ['/BG-7/BT-49', 'buyer electronic address (BT-49)'],
];

/** The three VAT identifiers BR-CO-09 is about. */
const VAT_IDENTIFIERS: ReadonlyArray<[string, string]> = [
  ['/BG-4/BT-31', 'seller VAT identifier (BT-31)'],
  ['/BG-7/BT-48', 'buyer VAT identifier (BT-48)'],
  ['/BG-11/BT-63', 'seller tax representative VAT identifier (BT-63)'],
];

/** The prefix Greece uses on a VAT identifier, which ISO 3166-1 does not have. */
const GREECE = 'EL';

/**
 * The rules of the EN 16931 pack this binding writes itself, one per identifier the pack
 * declares the rule language cannot express.
 *
 * @return the rules, in the order the pack names them
 */
export function en16931NativeRules(): NativeRule[] {
  return [
    schemeStated('BR-62', '/BG-4/BT-34', 'BT-34', 'seller electronic address',
      'EN 16931-1, 6.4.1, Table 3, BR-62'),
    schemeStated('BR-63', '/BG-7/BT-49', 'BT-49', 'buyer electronic address',
      'EN 16931-1, 6.4.1, Table 3, BR-63'),
    schemeStated('BR-64', '/BG-25/*/BG-31/BT-157', 'BT-157', 'item standard identifier',
      'EN 16931-1, 6.4.1, Table 3, BR-64'),
    schemeStated('BR-65', '/BG-25/*/BG-31/BT-158/*', 'BT-158', 'item classification identifier',
      'EN 16931-1, 6.4.1, Table 3, BR-65'),
    schemeInList('BR-CL-07', 'untdid-1153', 'EN 16931-1, 6.3, Table 2, BT-18 and BT-128', [
      { pattern: '/BT-18', term: 'BT-18', name: 'invoiced object identifier' },
      { pattern: '/BG-25/*/BT-128', term: 'BT-128', name: 'invoice line object identifier' },
    ]),
    schemeInList('BR-CL-10', 'iso-6523-icd',
      'EN 16931-1, 6.3, Table 2, BT-29, BT-46 and BT-60', [
        { pattern: '/BG-4/BT-29/*', term: 'BT-29', name: 'seller identifier' },
        { pattern: '/BG-7/BT-46', term: 'BT-46', name: 'buyer identifier' },
        { pattern: '/BG-10/BT-60', term: 'BT-60', name: 'payee identifier' },
      ]),
    schemeInList('BR-CL-11', 'iso-6523-icd',
      'EN 16931-1, 6.3, Table 2, BT-30, BT-47 and BT-61', [
        { pattern: '/BG-4/BT-30', term: 'BT-30', name: 'seller legal registration identifier' },
        { pattern: '/BG-7/BT-47', term: 'BT-47', name: 'buyer legal registration identifier' },
        { pattern: '/BG-10/BT-61', term: 'BT-61', name: 'payee legal registration identifier' },
      ]),
    schemeInList('BR-CL-13', 'untdid-7143', 'EN 16931-1, 6.3, Table 2, BT-158', [
      {
        pattern: '/BG-25/*/BG-31/BT-158/*', term: 'BT-158',
        name: 'item classification identifier',
      },
    ]),
    schemeInList('BR-CL-21', 'iso-6523-icd', 'EN 16931-1, 6.3, Table 2, BT-157', [
      { pattern: '/BG-25/*/BG-31/BT-157', term: 'BT-157', name: 'item standard identifier' },
    ]),
    mediaTypeAdmitted(),
    electronicAddressScheme(),
    schemeInList('BR-CL-26', 'iso-6523-icd', 'EN 16931-1, 6.3, Table 2, BT-71', [
      { pattern: '/BG-13/BT-71', term: 'BT-71', name: 'deliver to location identifier' },
    ]),
    vatIdentifierCountry(),
    categoryTaxableAmount('BR-S-08', 'S', 'standard rated', true, 'ubl',
      'EN 16931-1, 6.4.3.3.2, Table 6, BR-S-8'),
    exactlyOneBreakdown('BR-Z-01', 'Z', 'zero rated',
      'EN 16931-1, 6.4.3.4.2, Table 7, BR-Z-1'),
    categoryTaxableAmount('BR-Z-08', 'Z', 'zero rated', false, 'cii',
      'EN 16931-1, 6.4.3.4.2, Table 7, BR-Z-8'),
    exactlyOneBreakdown('BR-E-01', 'E', 'exempt from VAT',
      'EN 16931-1, 6.4.3.4.3, Table 8, BR-E-1'),
    categoryTaxableAmount('BR-E-08', 'E', 'exempt from VAT', false, 'cii',
      'EN 16931-1, 6.4.3.4.3, Table 8, BR-E-8'),
    exactlyOneBreakdown('BR-AE-01', 'AE', 'reverse charge',
      'EN 16931-1, 6.4.3.4.4, Table 9, BR-AE-1'),
    categoryTaxableAmount('BR-AE-08', 'AE', 'reverse charge', false, 'cii',
      'EN 16931-1, 6.4.3.4.4, Table 9, BR-AE-8'),
    exactlyOneBreakdown('BR-IC-01', 'K', 'intra-community supply',
      'EN 16931-1, 6.4.3.4.5, Table 10, BR-IC-1'),
    categoryTaxableAmount('BR-IC-08', 'K', 'intra-community supply', false, 'cii',
      'EN 16931-1, 6.4.3.4.5, Table 10, BR-IC-8'),
    exactlyOneBreakdown('BR-G-01', 'G', 'export outside the EU',
      'EN 16931-1, 6.4.3.4.6, Table 11, BR-G-1'),
    categoryTaxableAmount('BR-G-08', 'G', 'export outside the EU', false, 'cii',
      'EN 16931-1, 6.4.3.4.6, Table 11, BR-G-8'),
    exactlyOneBreakdown('BR-O-01', 'O', 'not subject to VAT',
      'EN 16931-1, 6.4.3.4.7, Table 12, BR-O-1'),
    categoryTaxableAmount('BR-O-08', 'O', 'not subject to VAT', false, 'neither',
      'EN 16931-1, 6.4.3.4.7, Table 12, BR-O-8'),
    categoryTaxableAmount('BR-AF-08', 'L',
      'IGIC, the general indirect tax of the Canary Islands', true, 'ubl-cii-silent',
      'EN 16931-1, 6.4.3.4.8, Table 13, BR-IG-8'),
    categoryTaxableAmount('BR-AG-08', 'M',
      'IPSI, the tax on production, services and importation in Ceuta and Melilla', true,
      'ubl-cii-silent', 'EN 16931-1, 6.4.3.4.8, Table 14, BR-IP-8'),
  ];
}

/**
 * `BR-CL-24`: the media type of an attached document is one of the six a receiver of a core
 * invoice has to be able to process. A media type is a supplementary component of a binary
 * object and not a business term.
 */
function mediaTypeAdmitted(): NativeRule {
  return {
    id: 'BR-CL-24',
    context: '/',
    terms: ['BT-125'],
    source: 'EN 16931-1, 6.5.11, Table 25, and 6.3, Table 2, BT-125',
    check: (context) => {
      const admitted = context.codeList('mime-code');
      for (const [path, value] of context.values(ATTACHMENT)) {
        if (value.mimeCode === undefined || admitted.contains(value.mimeCode)) {
          continue;
        }
        return 'The attached document (BT-125) at ' + path + ' is of the media type '
          + context.escape(value.mimeCode)
          + ', which is not on the mime-code snapshot this pack decides against.';
      }
      return undefined;
    },
  };
}

/**
 * `BR-CL-25`: the identification scheme of an electronic address is one of the electronic
 * address scheme identifiers the European Commission publishes.
 */
function electronicAddressScheme(): NativeRule {
  return {
    id: 'BR-CL-25',
    context: '/',
    terms: ['BT-34', 'BT-49'],
    source: 'EN 16931-1, 6.3, Table 2, BT-34 and BT-49',
    check: (context) => {
      const schemes = context.codeList('eas');
      for (const [pattern, name] of ADDRESSES) {
        for (const [path, value] of context.values(pattern)) {
          if (value.scheme === undefined || schemes.contains(value.scheme)) {
            continue;
          }
          return 'The ' + name + ' at ' + path + ' names the identification scheme '
            + context.escape(value.scheme)
            + ', which is not on the eas snapshot this pack decides against.';
        }
      }
      return undefined;
    },
  };
}

/**
 * `BR-CO-09`: a VAT identifier begins with the country code of the state that issued it.
 *
 * The question is about the first two characters of a value, and the language has no operator
 * that takes a part of one. The prefix is looked up in the country code snapshot the pack
 * decides against rather than in a pattern of two hundred and fifty alternatives, which is a
 * copy of a code list in the wrong place. Greece is the exception the standard states: its
 * VAT identifiers carry `EL` where ISO 3166-1 gives `GR`.
 */
function vatIdentifierCountry(): NativeRule {
  return {
    id: 'BR-CO-09',
    context: '/',
    terms: ['BT-31', 'BT-48', 'BT-63'],
    source: 'EN 16931-1, 6.4.2, Table 4, BR-CO-9',
    check: (context) => {
      const countries = context.codeList('iso-3166-1');
      for (const [pattern, name] of VAT_IDENTIFIERS) {
        for (const [path, text] of context.texts(pattern)) {
          const prefix = text.length < 2 ? text : text.slice(0, 2);
          if (prefix === GREECE || countries.contains(prefix)) {
            continue;
          }
          return 'The ' + name + ' at ' + path + ' is ' + context.escape(text)
            + ', and it begins with ' + context.escape(prefix)
            + ', which is not a country of the iso-3166-1 snapshot this pack decides against'
            + ' and is not the prefix EL that Greece uses.';
        }
      }
      return undefined;
    },
  };
}
