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
      check('/api/admin/v1/customers/' + customerId + '/freeze', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ reason: 'CI viewer cannot freeze customer' })
      }),
      check('/api/admin/v1/customers/' + customerId + '/disable', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ reason: 'CI viewer cannot disable customer' })
      }),
      check('/api/admin/v1/customer-auth?page=1&size=20'),
      check('/api/admin/v1/customer-auth/1'),
      check('/api/admin/v1/customer-auth/1/approve', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ opinion: 'CI viewer cannot approve KYC' })
      }),
      // A2 read-only actor must not plan, verify, dispatch or commit fixed-IP release.
      check('/api/admin/v1/services/' + serviceId + '/renew', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({days:30,renewalOrderId:1,reason:'CI viewer denied renewal'})
      }),
      check('/api/admin/v1/v12/service-expiry/reconcile', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({reason:'CI viewer cannot reconcile expiry',limit:5})
      }),
      check('/api/admin/v1/v12/release-inspections/CI-ROLE-DENIED'),
      check('/api/admin/v1/v12/release-actions/CI-ROLE-DENIED/dispatch', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }
      }),
      check('/api/admin/v1/v12/release-actions/CI-ROLE-DENIED/commit', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }
      }),
      // A4 supply purchases/proxy endpoints must enforce HTTP RBAC before
      // evaluating any customer/order/service or remote network state.
      check('/api/admin/v1/v12/external-delivery/purchases?page=1&size=20'),
      check('/api/admin/v1/v12/external-delivery/purchases/1'),
      check('/api/admin/v1/v12/external-delivery/purchases/1', {
        method: 'PUT', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({supplierId:1,customerId:customerId,supplierPackageName:'CI denied'})
      }),
      check('/api/admin/v1/v12/external-delivery/reverse-proxies?page=1&size=20'),
      check('/api/admin/v1/v12/external-delivery/reverse-proxies/1/apply', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({reason:'CI viewer cannot apply an upstream proxy'})
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


test('A1 viewer cannot confirm, cancel, pay or provision an actual order; PostgreSQL relations stay unchanged',async({page})=>{
  const unpaidId=process.env.V13_E2E_UNPAID_ORDER_ID
  expect(unpaidId,'A1 uses the confirmed but unpaid PostgreSQL order seeded by backend-smoke').toBeTruthy()
  await page.goto(base+'/login')
  const fields=page.locator('.login-card input')
  await fields.nth(0).fill(process.env.V13_E2E_VIEWER_USER||'ci_v13_readonly')
  await fields.nth(1).fill(process.env.V13_E2E_VIEWER_PASSWORD||'admin123')
  const login=page.waitForResponse(r=>r.url().endsWith('/api/admin/v1/auth/login'))
  await page.getByRole('button',{name:'登录',exact:true}).click()
  expect((await login).status()).toBe(200)
  await page.goto(base+'/orders/'+unpaidId)
  await expect(page.getByText('订单主记录')).toBeVisible()
  await expect(page.getByRole('button',{name:'核实客户付款'})).toHaveCount(0)
  await expect(page.getByRole('button',{name:'进入服务开通向导'})).toHaveCount(0)
  await expect(page.getByRole('button',{name:'取消订单'})).toHaveCount(0)
  const proof=await page.evaluate(async id=>{
    const token=localStorage.getItem('ipproxy_access_token')
    const basePath='/api/admin/v1/orders/'+id
    const headers={Authorization:'Bearer '+token,'Content-Type':'application/json'}
    const check=async(path:string,method='GET',body?:any)=>{
      const r=await fetch(path,{method,headers,body:body?JSON.stringify(body):undefined})
      return {status:r.status,payload:await r.json()}
    }
    const before=await check(basePath)
    const relationsBefore=await check('/api/admin/v1/relations/orders/'+id+'/services')
    const mutations=await Promise.all([
      check(basePath+'/confirm','POST',{reason:'CI read-only must not confirm'}),
      check(basePath+'/cancel','POST',{reason:'CI read-only must not cancel'}),
      check(basePath+'/payment-confirmation','POST',{paymentReference:'CI-UNTRUSTED-NO-PAY',reason:'CI viewer denied'}),
      check(basePath+'/provision','POST',{reason:'CI viewer denied',plans:[]}),
      check(basePath+'/provision-preview')
    ])
    const after=await check(basePath)
    const relationsAfter=await check('/api/admin/v1/relations/orders/'+id+'/services')
    return {before,after,relationsBefore,relationsAfter,mutations}
  },unpaidId)
  for(const snapshot of [proof.before,proof.after,proof.relationsBefore,proof.relationsAfter])
    expect(snapshot.status,JSON.stringify(snapshot)).toBe(200)
  for(const rejected of proof.mutations){
    expect(rejected.status,JSON.stringify(rejected)).toBe(400)
    expect(rejected.payload.code,JSON.stringify(rejected)).toBe('AUTH_FORBIDDEN')
  }
  const original=proof.before.payload.data,now=proof.after.payload.data
  expect(original.payment_status).not.toBe('PAID_CONFIRMED')
  expect(now.id).toBe(original.id)
  expect(now.order_no).toBe(original.order_no)
  expect(now.status).toBe(original.status)
  expect(now.payment_status).toBe(original.payment_status)
  expect(now.items).toEqual(original.items)
  expect(proof.relationsAfter.payload.data).toEqual(proof.relationsBefore.payload.data)
  expect(proof.relationsAfter.payload.data.items).toHaveLength(0)
})
