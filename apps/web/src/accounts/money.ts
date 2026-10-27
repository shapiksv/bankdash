const AMOUNT = /^(\d+)\.(\d{2})$/

/**
 * "12345678901234.89" → "$12,345,678,901,234.89". Works on the decimal string, so there is no rounding and no
 * floating point; USD with en-US grouping whatever the browser locale. Negative amounts do not exist yet.
 */
export function formatUsd(amount: string): string {
  const match = AMOUNT.exec(amount)
  if (!match) throw new Error(`Not a plain amount with 2 decimals: ${amount}`)
  const [, whole, cents] = match
  return `$${whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}.${cents}`
}
