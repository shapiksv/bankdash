/**
 * Mirrors account-service's PinPolicy, so a weak PIN is flagged before it is sent. The server stays the authority:
 * its 422 carries the same rule codes and messages.
 */
export type PinRule =
  | 'NOT_FOUR_DIGITS'
  | 'ALL_SAME_DIGITS'
  | 'CONSECUTIVE_DIGITS'
  | 'REPEATING_PAIR'
  | 'LOOKS_LIKE_YEAR'
  | 'KEYPAD_PATTERN'
  | 'SAME_AS_CURRENT'

export const PIN_MESSAGES: Record<PinRule, string> = {
  NOT_FOUR_DIGITS: 'PIN must be exactly 4 digits',
  ALL_SAME_DIGITS: 'PIN must not repeat one digit',
  CONSECUTIVE_DIGITS: 'PIN must not be a sequence of consecutive digits',
  REPEATING_PAIR: 'PIN must not be a repeating pair of digits',
  LOOKS_LIKE_YEAR: 'PIN must not look like a year',
  KEYPAD_PATTERN: 'PIN must not be a line on the keypad',
  SAME_AS_CURRENT: 'New PIN must differ from the current PIN',
}

const KEYPAD_LINES = new Set([
  '2580', '0852', '1470', '0741', '3690', '0963', '1590', '0951', '3570', '0753', '1593', '3951',
])

function isConsecutive(pin: string, step: number): boolean {
  for (let i = 1; i < pin.length; i++) {
    if (pin.charCodeAt(i) - pin.charCodeAt(i - 1) !== step) return false
  }
  return true
}

/** The first rule the PIN breaks, in the backend's order, or undefined. SAME_AS_CURRENT needs the server. */
export function checkPin(pin: string): PinRule | undefined {
  if (!/^[0-9]{4}$/.test(pin)) return 'NOT_FOUR_DIGITS'
  if (new Set(pin).size === 1) return 'ALL_SAME_DIGITS'
  if (isConsecutive(pin, 1) || isConsecutive(pin, -1)) return 'CONSECUTIVE_DIGITS'
  if (pin[0] === pin[2] && pin[1] === pin[3]) return 'REPEATING_PAIR'
  const number = Number(pin)
  if (number >= 1950 && number <= 2029) return 'LOOKS_LIKE_YEAR'
  if (KEYPAD_LINES.has(pin)) return 'KEYPAD_PATTERN'
  return undefined
}

export function pinError(pin: string): string | undefined {
  const rule = checkPin(pin)
  return rule && PIN_MESSAGES[rule]
}
