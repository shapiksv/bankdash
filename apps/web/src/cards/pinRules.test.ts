import { describe, expect, it } from 'vitest'
import { checkPin, pinError } from './pinRules'

describe('checkPin', () => {
  // The same examples as account-service's PinPolicyTest.
  it.each([
    ['0000', 'ALL_SAME_DIGITS'],
    ['1234', 'CONSECUTIVE_DIGITS'],
    ['9876', 'CONSECUTIVE_DIGITS'],
    ['1212', 'REPEATING_PAIR'],
    ['1998', 'LOOKS_LIKE_YEAR'],
    ['2580', 'KEYPAD_PATTERN'],
    ['abcd', 'NOT_FOUR_DIGITS'],
    ['123', 'NOT_FOUR_DIGITS'],
    ['12345', 'NOT_FOUR_DIGITS'],
    ['', 'NOT_FOUR_DIGITS'],
    ['١٢٣٤', 'NOT_FOUR_DIGITS'],
    ['9999', 'ALL_SAME_DIGITS'],
    ['0123', 'CONSECUTIVE_DIGITS'],
    ['3210', 'CONSECUTIVE_DIGITS'],
    ['6969', 'REPEATING_PAIR'],
    ['2020', 'REPEATING_PAIR'],
    ['1950', 'LOOKS_LIKE_YEAR'],
    ['2029', 'LOOKS_LIKE_YEAR'],
    ['0852', 'KEYPAD_PATTERN'],
    ['1593', 'KEYPAD_PATTERN'],
    ['3951', 'KEYPAD_PATTERN'],
  ])('AC10: flags %s as %s', (pin, rule) => {
    expect(checkPin(pin)).toBe(rule)
  })

  it.each(['4826', '7391', '1949', '2030', '0192', '8901', '1213', '1122'])('AC10: accepts strong PIN %s', (pin) => {
    expect(checkPin(pin)).toBeUndefined()
  })

  it('AC10: gives the same message as the backend', () => {
    expect(pinError('1234')).toBe('PIN must not be a sequence of consecutive digits')
    expect(pinError('4826')).toBeUndefined()
  })
})
