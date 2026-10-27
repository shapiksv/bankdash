import { describe, expect, it } from 'vitest'
import { formatUsd } from './money'

describe('formatUsd', () => {
  it('AC7: formats 1250.50 as $1,250.50', () => {
    expect(formatUsd('1250.50')).toBe('$1,250.50')
  })

  it('AC7: formats 300.00 and 0.00', () => {
    expect(formatUsd('300.00')).toBe('$300.00')
    expect(formatUsd('0.00')).toBe('$0.00')
  })

  it('AC8: formats 12345678901234.89 exactly', () => {
    expect(formatUsd('12345678901234.89')).toBe('$12,345,678,901,234.89')
  })

  it('groups at every thousand', () => {
    expect(formatUsd('999.99')).toBe('$999.99')
    expect(formatUsd('1000.00')).toBe('$1,000.00')
    expect(formatUsd('100000.10')).toBe('$100,000.10')
    expect(formatUsd('1000000.00')).toBe('$1,000,000.00')
  })

  it('rejects malformed amounts', () => {
    for (const bad of ['', '12', '12.5', '12.345', '-1.00', '1e3', '1,000.00', ' 1.00']) {
      expect(() => formatUsd(bad), bad).toThrow()
    }
  })
})
