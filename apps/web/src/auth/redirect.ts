/** Where to go after login: the page that sent us here, but only within the app (no open redirect). */
export function safeRedirect(from: string | null): string {
  return from && from.startsWith('/') && !from.startsWith('//') ? from : '/'
}
