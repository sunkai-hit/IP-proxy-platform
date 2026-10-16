import { test, expect } from '@playwright/test'

/**
 * V1.3 real Chromium + Spring Boot + PostgreSQL least-privilege acceptance.
 * A CI-only role inherits no admin privileges or proxy-network writes.
 */
const base = 'http://127.0.0.1:5173'
const customerId = process.env.V13_E2E_CUSTOMER_ID
const serviceId = process.env.V13_E2E_SERVICE_ID

test('read-only user cannot view sensitive subtables, execute changes or bypass HTTP guards', async ({ page }) => {
  expect(customerId).toBeTruthy()
  expect(serviceId).toBeTruthy()
  await page.goto(base + '/login')
  const inputs = page.locator('.login-card input')
  await inputs.nth(0).fill(process.env.V13_E2E_VIEWER_USER || 'ci_v13_readonly')
  await inputs.nth(1).fill(process.env.V13_E2E_VIEWER_PASSWORD || 'admin123')
  const login = page.waitForResponse(r => r.url().endsWith('/api/admin/v1/auth/login'))
  await page.getByRole('button', { name: '登录', exact: true }).click()
  expect((await login).status()).toBe(200)
  await expect(page).toHaveURL(/\/dashboard$/)

  // This user intentionally has customer:read, order:read, service:read,
  // but NO customer authentication/account, credential or service change rights.
  const customerResponse = page.waitForResponse(r =>
    r.url().endsWith('/api/admin/v1/customers/' + customerId) && r.request().method() === 'GET')
  await page.goto(base + '/customers/' + customerId)
  const customerPayload = (await (await customerResponse).json()).data
  expect(customerPayload.customer.customer_name).toBe('V12 CI Customer')
  expect(customerPayload).not.toHaveProperty('authRecords')
  expect(customerPayload).not.toHaveProperty('accounts')
  expect(customerPayload).toHaveProperty('services')
  await expect(page.getByRole('tab', { name: '订单明细' })).toBeVisible()
  await expect(page.getByRole('tab', { name: '服务实例' })).toBeVisible()
  await expect(page.getByRole('tab', { name: '认证记录' })).toHaveCount(0)
  await expect(page.getByRole('tab', { name: '客户账号' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '为该客户创建订单' })).toHaveCount(0)

  const serviceResponse = page.waitForResponse(r =>
    r.url().endsWith('/api/admin/v1/services/' + serviceId) && r.request().method() === 'GET')
  await page.goto(base + '/services/' + serviceId)
  const servicePayload = (await (await serviceResponse).json()).data
  for (const privateSubtable of ['credentials', 'changes', 'releaseRecords', 'longMonitors']) {
    expect(servicePayload, privateSubtable + ' leaked in HTTP JSON').not.toHaveProperty(privateSubtable)
  }
  await expect(page.getByRole('tab', { name: '凭证' })).toHaveCount(0)
  await expect(page.getByRole('tab', { name: '变更记录' })).toHaveCount(0)
  await expect(page.getByRole('tab', { name: '释放记录' })).toHaveCount(0)
  await expect(page.getByRole('tab', { name: '长效监控' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: /从此服务发起续费/ })).toHaveCount(0)

  // Vue route protection cannot substitute for the actual server RBAC.
  await page.goto(base + '/service-changes')
  await expect(page).toHaveURL(/\/dashboard$/)
  await page.goto(base + '/customer-auth')
  await expect(page).toHaveURL(/\/dashboard$/)
  await page.goto(base + '/resources/lines')
  await expect(page).toHaveURL(/\/dashboard$/)
  await page.goto(base + '/resource-pools')
  await expect(page).toHaveURL(/\/dashboard$/)
  await page.goto(base + '/resource-pools/1')
  await expect(page).toHaveURL(/\/dashboard$/)
  const denied = await page.evaluate(async ({ customerId, serviceId }) => {
    const bearer = localStorage.getItem('ipproxy_access_token')
    const headers = { Authorization: 'Bearer ' + bearer }
    const check = async (path: string, init: RequestInit = {}) => {
      const response = await fetch(path, { ...init, headers: {
        ...headers, ...(init.headers || {})
      } })
      const payload = await response.json()
      return { status: response.status, code: payload.code }
    }
    return await Promise.all([
      check('/api/admin/v1/customers/' + customerId + '/credentials'),
      check('/api/admin/v1/services/' + serviceId + '/credentials'),
      check('/api/admin/v1/services/' + serviceId + '/suspend', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ reason: 'CI read-only forbidden' })
      }),
      check('/api/admin/v1/v12/resources/lines'),
      check('/api/admin/v1/v12/resource-pools'),
      check('/api/admin/v1/v12/resource-pools/1/operations'),
      check('/api/admin/v1/runtime-logs/v12/operation')
    ])
  }, { customerId, serviceId })
  for (const result of denied) {
    // The project's existing BusinessException contract uses HTTP 400 and
    // AUTH_FORBIDDEN; asserting HTTP 403 here would misread a legitimate denial.
    expect(result.status).toBe(400)
    expect(result.code).toBe('AUTH_FORBIDDEN')
  }
})
