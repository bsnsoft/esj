/**
 * An exact decimal number, held as an integer and a scale.
 *
 * The specification forbids binary floating point anywhere — in `values`, inside
 * `extensions`, in the canonical form and in the digests (sections 6.4 and 7.6) — and the
 * rule language asks for exact arithmetic with one named working precision for division.
 * `number` cannot do that, so a decimal here is a `bigint` of unscaled digits together with
 * the number of fraction digits it carries: `42.015` is `42015` at scale 3. Every operation
 * below is integer arithmetic on that pair and therefore exact, and the one operation that
 * cannot always be — division — names the precision it stops at.
 */

const TEN = 10n;

function pow10(exponent: number): bigint {
  return TEN ** BigInt(exponent);
}

/** The fraction digits a non-terminating division is computed to (`rules/README.md`). */
export const DIVISION_SCALE = 34;

/** An exact decimal number. */
export class Decimal {
  /** The digits without the decimal point, signed. */
  readonly unscaled: bigint;

  /** How many of those digits are fraction digits; never negative. */
  readonly scale: number;

  private constructor(unscaled: bigint, scale: number) {
    this.unscaled = unscaled;
    this.scale = scale;
  }

  /** Zero. */
  static readonly ZERO = new Decimal(0n, 0);

  /** One. */
  static readonly ONE = new Decimal(1n, 0);

  /**
   * Returns the decimal a string in the canonical form of the specification, section 6.4,
   * stands for.
   *
   * The caller has decided that the string is in that form; this reads it and does not
   * judge it, so that the one place that decides what a decimal looks like stays the
   * grammar of `grammars.ts`.
   *
   * @param text the canonical decimal
   * @return the number it stands for
   */
  static of(text: string): Decimal {
    const point = text.indexOf('.');
    if (point < 0) {
      return new Decimal(BigInt(text), 0);
    }
    const digits = text.slice(0, point) + text.slice(point + 1);
    return new Decimal(BigInt(digits), text.length - point - 1);
  }

  /** Returns the decimal a whole number stands for. */
  static ofInteger(value: number | bigint): Decimal {
    return new Decimal(BigInt(value), 0);
  }

  private static at(unscaled: bigint, scale: number): Decimal {
    return new Decimal(unscaled, scale);
  }

  /** Returns this number's digits raised to that scale, which must not be smaller. */
  private digitsAt(scale: number): bigint {
    return this.unscaled * pow10(scale - this.scale);
  }

  /** Returns the sum of this number and the other, exactly. */
  add(other: Decimal): Decimal {
    const scale = Math.max(this.scale, other.scale);
    return Decimal.at(this.digitsAt(scale) + other.digitsAt(scale), scale);
  }

  /** Returns this number minus the other, exactly. */
  subtract(other: Decimal): Decimal {
    const scale = Math.max(this.scale, other.scale);
    return Decimal.at(this.digitsAt(scale) - other.digitsAt(scale), scale);
  }

  /** Returns the product of this number and the other, exactly. */
  multiply(other: Decimal): Decimal {
    return Decimal.at(this.unscaled * other.unscaled, this.scale + other.scale);
  }

  /**
   * Returns this number divided by the other.
   *
   * A quotient whose decimal expansion terminates is returned exactly, however many
   * fraction digits it needs; one whose expansion does not terminate is computed to
   * {@link DIVISION_SCALE} fraction digits, half away from zero (`rules/README.md`,
   * Decimals). Which of the two a quotient is, is read off the fraction in its lowest terms
   * rather than found by dividing and looking: it terminates exactly where the denominator
   * has no prime factor but 2 and 5, and it then needs as many fraction digits as the
   * larger of the two exponents, shifted by the scales of the operands. Division by zero has
   * no answer and is `undefined`, which the rule engine reads as an absent value.
   *
   * @param other the divisor
   * @return the quotient, or `undefined` where the divisor is zero
   */
  divide(other: Decimal): Decimal | undefined {
    if (other.unscaled === 0n) {
      return undefined;
    }
    // this / other = (this.unscaled / other.unscaled) * 10^(other.scale - this.scale)
    const negative = other.unscaled < 0n;
    let numerator = negative ? -this.unscaled : this.unscaled;
    let denominator = negative ? -other.unscaled : other.unscaled;
    const common = greatestCommonDivisor(numerator < 0n ? -numerator : numerator, denominator);
    numerator /= common;
    denominator /= common;
    let twos = 0;
    while (denominator % 2n === 0n) {
      denominator /= 2n;
      twos++;
    }
    let fives = 0;
    while (denominator % 5n === 0n) {
      denominator /= 5n;
      fives++;
    }
    if (denominator !== 1n) {
      const shift = DIVISION_SCALE + other.scale - this.scale;
      const dividend = shift >= 0 ? this.unscaled * pow10(shift) : this.unscaled;
      const divisor = shift >= 0 ? other.unscaled : other.unscaled * pow10(-shift);
      return Decimal.at(roundedQuotient(dividend, divisor), DIVISION_SCALE).stripped();
    }
    // numerator / (2^twos * 5^fives) is numerator * 2^(digits - twos) * 5^(digits - fives)
    // over 10^digits, which is an integer over a power of ten: the exact quotient.
    const digits = Math.max(twos, fives);
    const unscaled = numerator * 2n ** BigInt(digits - twos) * 5n ** BigInt(digits - fives);
    const scale = digits + this.scale - other.scale;
    return (scale >= 0
      ? Decimal.at(unscaled, scale)
      : Decimal.at(unscaled * pow10(-scale), 0)).stripped();
  }

  /** Returns this number without its sign. */
  abs(): Decimal {
    return this.unscaled < 0n ? Decimal.at(-this.unscaled, this.scale) : this;
  }

  /** Returns this number with the opposite sign. */
  negate(): Decimal {
    return Decimal.at(-this.unscaled, this.scale);
  }

  /** Returns -1, 0 or 1 as this number is negative, zero or positive. */
  signum(): number {
    return this.unscaled < 0n ? -1 : this.unscaled > 0n ? 1 : 0;
  }

  /** Returns -1, 0 or 1 as this number is smaller than, equal to or greater than the other. */
  compare(other: Decimal): number {
    const scale = Math.max(this.scale, other.scale);
    const left = this.digitsAt(scale);
    const right = other.digitsAt(scale);
    return left < right ? -1 : left > right ? 1 : 0;
  }

  /** Tells whether this number and the other are the same number, however they are scaled. */
  equals(other: Decimal): boolean {
    return this.compare(other) === 0;
  }

  /**
   * Returns this number rounded to that many fraction digits, half away from zero, which is
   * the rounding EN 16931-1 asks for and the only rounding this library does to a scale the
   * caller chooses.
   *
   * @param digits how many fraction digits the result carries
   * @return the rounded number
   */
  round(digits: number): Decimal {
    if (digits >= this.scale) {
      return this;
    }
    const divisor = pow10(this.scale - digits);
    return Decimal.at(roundedQuotient(this.unscaled, divisor), digits);
  }

  /**
   * Returns how many fraction digits this number needs, which is its scale with trailing
   * zeros removed. Zero needs none.
   */
  decimals(): number {
    return this.stripped().scale;
  }

  /** Returns the same number with no trailing fraction zero and no negative zero. */
  stripped(): Decimal {
    let unscaled = this.unscaled;
    let scale = this.scale;
    while (scale > 0 && unscaled % TEN === 0n) {
      unscaled /= TEN;
      scale--;
    }
    return Decimal.at(unscaled, scale);
  }

  /**
   * Returns the canonical form of the specification, section 6.4: plain notation, no
   * exponent, no trailing fraction zero, no leading zero in the integer part, and no sign
   * on zero.
   */
  toString(): string {
    const value = this.stripped();
    const negative = value.unscaled < 0n;
    let digits = (negative ? -value.unscaled : value.unscaled).toString();
    if (value.scale === 0) {
      return negative ? '-' + digits : digits;
    }
    if (digits.length <= value.scale) {
      digits = '0'.repeat(value.scale - digits.length + 1) + digits;
    }
    const point = digits.length - value.scale;
    const text = digits.slice(0, point) + '.' + digits.slice(point);
    return negative ? '-' + text : text;
  }
}

/** Returns the greatest common divisor of two integers that are not negative. */
function greatestCommonDivisor(left: bigint, right: bigint): bigint {
  let a = left;
  let b = right;
  while (b !== 0n) {
    [a, b] = [b, a % b];
  }
  return a;
}

/** Divides two integers and rounds the quotient half away from zero. */
function roundedQuotient(numerator: bigint, denominator: bigint): bigint {
  const negative = numerator < 0n !== denominator < 0n;
  const a = numerator < 0n ? -numerator : numerator;
  const b = denominator < 0n ? -denominator : denominator;
  const quotient = a / b;
  const twice = (a % b) * 2n;
  const rounded = twice >= b ? quotient + 1n : quotient;
  return negative ? -rounded : rounded;
}
