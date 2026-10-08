/**
 * The naming convention the TypeScript view is generated with, apart from the script that
 * writes it so that a test can hold it to the specification without generating anything.
 *
 * A repeatable group's slug is a plural, and a generator forms the name of one instance from
 * it in one of two ways: a trailing `s` is dropped, and a trailing `ies` becomes `y`
 * (`SPEC.md` section 10, rule 3 of the slugs). A slug in any other shape names no instance,
 * so it is refused rather than used as it stands: a view that called one invoice line
 * `InvoiceLines` would read wrongly in every program written against it.
 */

import type { RegistryTerm } from '../src/registry.ts';
import { isRepeatable } from '../src/registry.ts';

/**
 * Returns the singular of a plural stem, or `null` where the stem has neither plural form.
 */
export function singular(stem: string): string | null {
  if (stem.length > 3 && stem.endsWith('ies')) {
    return stem.slice(0, stem.length - 3) + 'y';
  }
  if (stem.length > 1 && stem.endsWith('s')) {
    return stem.slice(0, stem.length - 1);
  }
  return null;
}

/**
 * Returns the stem that names one instance of a business group: the singular of the slug of
 * a repeatable group, the slug of any other.
 *
 * @throws Error where a repeatable group's slug is no plural a singular can be formed from
 */
export function instanceStem(term: RegistryTerm): string {
  if (!isRepeatable(term)) {
    return term.slug;
  }
  const one = singular(term.slug);
  if (one === null) {
    throw new Error('the slug of the repeatable group ' + term.id + ' is ' + term.slug
      + ', which is no plural: a slug ending in s or ies names the list of its instances'
      + ' (SPEC.md section 10)');
  }
  return one;
}
