import { test } from 'node:test';
import assert from 'node:assert/strict';
import { DIVISION_SCALE, Decimal } from '../src/decimal.ts';

/**
 * The decimal type, which is where the specification's rule that no number ever passes
 * through a binary floating point value is actually kept.
 */

test('a decimal is read and written in the canonical form of section 6.4', () => {
  for (const text of ['0', '100', '-100', '0.5', '42.015', '-0.01',
    '1111111111111111111111111111111111111111111111111111111111111111']) {
    assert.equal(Decimal.of(text).toString(), text);
  }
});

test('trailing fraction zeros and a negative zero are not canonical spellings', () => {
  assert.equal(Decimal.of('100.00').toString(), '100');
  assert.equal(Decimal.of('42.0150').toString(), '42.015');
  assert.equal(Decimal.of('-0').toString(), '0');
  assert.equal(Decimal.of('-0.00').toString(), '0');
});

test('arithmetic is exact, and a number a double would round keeps every digit', () => {
  assert.equal(Decimal.of('0.1').add(Decimal.of('0.2')).toString(), '0.3');
  assert.equal(Decimal.of('1.0000000000000001').add(Decimal.ZERO).toString(),
    '1.0000000000000001');
  assert.equal(Decimal.of('12345678901234567890').multiply(Decimal.ofInteger(2)).toString(),
    '24691357802469135780');
  assert.equal(Decimal.of('84.03').subtract(Decimal.of('84.02')).toString(), '0.01');
});

test('a division that terminates is exact, and one that does not names its precision', () => {
  assert.equal(Decimal.ofInteger(1).divide(Decimal.ofInteger(4))!.toString(), '0.25');
  const third = Decimal.ofInteger(1).divide(Decimal.ofInteger(3))!;
  assert.equal(third.decimals(), DIVISION_SCALE);
  assert.ok(third.toString().startsWith('0.3333333333'));
  assert.equal(Decimal.ofInteger(1).divide(Decimal.ZERO), undefined);
});

/** Divides two canonical decimals and writes the quotient canonically. */
function quotient(dividend: string, divisor: string): string | undefined {
  return Decimal.of(dividend).divide(Decimal.of(divisor))?.toString();
}

test('a quotient that terminates is exact however far beyond the working precision it ends', () => {
  const tiny = '0.' + '0'.repeat(34) + '1';
  assert.equal(quotient(tiny, '1'), tiny);
  assert.equal(quotient('0.' + '0'.repeat(32) + '1', '8'), '0.' + '0'.repeat(33) + '125');
  assert.equal(quotient('1', '1125899906842624'),
    '0.00000000000000088817841970012523233890533447265625');
  assert.equal(quotient('0.3', '1.2'), '0.25');
  assert.equal(quotient('100', '0.25'), '400');
  assert.equal(quotient('-0.3', '1.2'), '-0.25');
  assert.equal(quotient('7', '-7'), '-1');
  assert.equal(quotient('0', '3'), '0');
});

test('a quotient that does not terminate has 34 fraction digits, half away from zero', () => {
  assert.equal(quotient('2', '3'), '0.6666666666666666666666666666666667');
  assert.equal(quotient('-2', '3'), '-0.6666666666666666666666666666666667');
  assert.equal(quotient('2', '-3'), '-0.6666666666666666666666666666666667');
  assert.equal(quotient('1', '7'), '0.1428571428571428571428571428571429');
  assert.equal(quotient('10', '3'), '3.3333333333333333333333333333333333');
  assert.equal(quotient('1', '3000'), '0.0003333333333333333333333333333333');
});

test('rounding is half away from zero, and nothing else rounds', () => {
  assert.equal(Decimal.of('2.345').round(2).toString(), '2.35');
  assert.equal(Decimal.of('2.344').round(2).toString(), '2.34');
  assert.equal(Decimal.of('-2.345').round(2).toString(), '-2.35');
  assert.equal(Decimal.of('2.5').round(0).toString(), '3');
  assert.equal(Decimal.of('42.015').round(3).toString(), '42.015');
});

test('the VAT category amount of BR-CO-17 comes out exactly', () => {
  const taxable = Decimal.of('1000');
  const rate = Decimal.of('19');
  const amount = taxable.multiply(rate).divide(Decimal.ofInteger(100))!.round(2);
  assert.equal(amount.toString(), '190');
});

test('how many fraction digits a number needs is what it needs, not what a file spells', () => {
  assert.equal(Decimal.of('100').decimals(), 0);
  assert.equal(Decimal.of('100.5').decimals(), 1);
  assert.equal(Decimal.of('0').decimals(), 0);
});

test('comparison is numeric and ignores the scale a number was built at', () => {
  assert.equal(Decimal.of('10').compare(Decimal.of('9')), 1);
  assert.ok(Decimal.of('100').equals(Decimal.of('100.00')));
  assert.equal(Decimal.of('-1').signum(), -1);
  assert.equal(Decimal.of('-1').abs().toString(), '1');
});
