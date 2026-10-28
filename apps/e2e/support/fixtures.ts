import { test as base } from '@playwright/test'
import { Api, signUp, type User } from './api.ts'

/**
 * `user`: a fresh user per test, signed up through `page.request`, so the page starts logged in (see signUp).
 * `api`: the real API as that user.
 */
export const test = base.extend<{ user: User; api: Api }>({
  user: async ({ page }, use) => {
    await use(await signUp(page.request))
  },
  api: async ({ page, user }, use) => {
    await use(Api.of(page.request, user))
  },
})

export { expect } from '@playwright/test'