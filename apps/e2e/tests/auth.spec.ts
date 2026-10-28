import { expect, test } from '../support/fixtures.ts'
import { newUserInput, signUp } from '../support/api.ts'

// From before spec-driven development: no ticket, hence "AUTH:".

test('AUTH: signs up with the form, lands on the Dashboard and stays logged in after a reload', async ({ page }) => {
  const input = newUserInput()
  await page.goto('/signup')
  await page.getByLabel('Full name').fill(input.fullName)
  await page.getByLabel('Email').fill(input.email)
  await page.getByLabel('Password').fill(input.password)
  await page.getByRole('button', { name: 'Sign up' }).click()

  await expect(page).toHaveURL('/')
  await expect(page.getByRole('heading', { name: 'My Cards' })).toBeVisible()
  await page.reload()
  await expect(page).toHaveURL('/')
  await expect(page.getByRole('heading', { name: 'My Cards' })).toBeVisible()
})

test('AUTH: logs in with the form', async ({ page, request }) => {
  // Signed up in a separate request context, so the page has no refresh cookie.
  const user = await signUp(request, { waitForAccounts: false })
  await page.goto('/login')
  await page.getByLabel('Email').fill(user.email)
  await page.getByLabel('Password').fill(user.password)
  await page.getByRole('button', { name: 'Log in' }).click()

  await expect(page).toHaveURL('/')
  await expect(page.getByRole('heading', { name: 'My Cards' })).toBeVisible()
})

test('AUTH: rejects a wrong password', async ({ page, request }) => {
  const user = await signUp(request, { waitForAccounts: false })
  await page.goto('/login')
  await page.getByLabel('Email').fill(user.email)
  await page.getByLabel('Password').fill(`${user.password}-wrong`)
  await page.getByRole('button', { name: 'Log in' }).click()

  await expect(page.getByRole('alert')).toHaveText('Invalid email or password')
  await expect(page).toHaveURL(/\/login/)
})

test('AUTH: logs out from the account menu and stays logged out', async ({ page, user }) => {
  await page.goto('/')
  await expect(page.getByRole('heading', { name: 'My Cards' })).toBeVisible()
  await page.getByRole('button', { name: 'Account menu' }).click()
  await expect(page.getByRole('menu')).toContainText(user.email)
  await page.getByRole('menuitem', { name: 'Log out' }).click()

  await expect(page).toHaveURL(/\/login/)
  await page.goto('/')
  await expect(page).toHaveURL(/\/login/)
  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible()
})