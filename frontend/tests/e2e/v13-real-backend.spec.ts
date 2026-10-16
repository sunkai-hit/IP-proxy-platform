import { test, expect } from '@playwright/test'

/**
 * Real Chromium + actual Spring Boot/PostgreSQL (CI mock adapters).
 * E2E proves rendered business-management pages and links work.
 * It NEVER proves real proxy/network provisioning or packet delivery.
 */
const base = 'http://127.0.0.1:5173'
const customerId = process.env.V13_E2E_CUSTOMER_ID
const multiOrderId = process.env.V13_E2E_ORDER_ID
const serviceId = process.env.V13_E2E_SERVICE_ID
const poolId = process.env.V13_E2E_POOL_ID
const unpaidOrderId = process.env.V13_E2E_UNPAID_ORDER_ID

test.describe('V1.3 actual browser management-console smoke', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto(base + '/login')
    await expect(page.getByRole('heading', { name: 'IP代理管理平台' })).toBeVisible()
    const inputs = page.locator('.login-card input')
    await inputs.nth(0).fill(process.env.V13_E2E_USER || 'admin')
    await inputs.nth(1).fill(process.env.V13_E2E_PASSWORD || 'admin123')
    const loginResponse = page.waitForResponse(
      response => response.url().endsWith('/api/admin/v1/auth/login'), { timeout: 10000 }
    )
    await page.getByRole('button', { name: '登录', exact: true }).click()
    const result = await loginResponse
    expect(result.status(), 'Vite-proxied login HTTP status: ' + await result.text()).toBe(200)
    await expect(page).toHaveURL(/\/dashboard$/, { timeout: 10000 })
    await expect(page.locator('.console-header')).toContainText('V1.3')
  })

  test('customer detail displays real PostgreSQL service and order links', async ({ page }) => {
    expect(customerId).toBeTruthy()
    await page.goto(base + '/customers/' + customerId)
    await expect(page.getByRole('heading', { name: 'V12 CI Customer' })).toBeVisible()
    await expect(page.getByRole('tab', { name: '订单明细' })).toBeVisible()
    await expect(page.getByRole('tab', { name: '服务实例' })).toBeVisible()
    await page.getByRole('tab', { name: '服务实例' }).click()
    await expect(page.locator('.el-tab-pane:visible .el-table__body tr').first()).toBeVisible()
    await page.getByRole('tab', { name: '订单明细' }).click()
    await expect(page.locator('.el-tab-pane:visible .el-table__body tr').first()).toBeVisible()
  })

  test('two-item order renders item children and three actual linked services', async ({ page }) => {
    expect(multiOrderId).toBeTruthy()
    await page.goto(base + '/orders/' + multiOrderId)
    await expect(page.getByText('订单主记录')).toBeVisible()
    await page.getByRole('tab', { name: '订单项明细' }).click()
    await expect(page.locator('.el-tab-pane:visible .el-table__body tr')).toHaveCount(2)
    await expect(page.getByRole('tab', { name: /实际关联服务 \(3\)/ })).toBeVisible()
    await page.getByRole('tab', { name: /实际关联服务 \(3\)/ }).click()
    await expect(page.locator('.el-tab-pane:visible .el-table__body tr')).toHaveCount(3)
    await page.locator('.el-tab-pane:visible').getByRole('button', { name: '服务详情' }).first().click()
    await expect(page).toHaveURL(/\/services\/\d+$/)
    await expect(page.getByRole('tab', { name: '基本信息' })).toBeVisible()
  })

  test('service detail renders delivery boundary and resource, task, IPv4 subtables', async ({ page }) => {
    expect(serviceId).toBeTruthy()
    await page.goto(base + '/services/' + serviceId)
    await expect(page.getByText('真实交付状态')).toBeVisible()
    await expect(page.getByText('MOCK_ONLY').first()).toBeVisible()
    await page.getByRole('tab', { name: '资源/交付绑定' }).click()
    await expect(page.getByText('管理面绑定ACTIVE不代表网络执行已经验证。', { exact: false })).toBeVisible()
    await page.getByRole('tab', { name: '出口IPv4授权' }).click()
    await expect(page.getByText('未验证的出口不能宣称已交付。', { exact: false })).toBeVisible()
    await page.getByRole('tab', { name: '执行任务' }).click()
    await expect(page.getByRole('columnheader', { name: '操作ID' })).toBeVisible()
  })

  test('CentOS/ROS/line detail route reuse shows the new resource rather than stale previous fields', async ({ page }) => {
    await page.goto(base + '/resources/lines')
    await expect(page.locator('.el-table__body tr').first()).toBeVisible()
    await page.locator('.el-table__body tr').first().getByRole('button', { name: '详情' }).click()
    await expect(page.getByRole('heading', { name: '家宽/线路详情' })).toBeVisible()
    await expect(page.getByText('线路当前拨号公网IP（非固定出口）')).toBeVisible()
    // Same Vue component is reused: without watching route.fullPath this kept
    // displaying the previous line after navigating to its ROS and CentOS.
    await page.locator('.el-descriptions').getByRole('button').first().click()
    await expect(page).toHaveURL(/\/resources\/ros\/\d+$/)
    await expect(page.getByRole('heading', { name: 'ROS详情' })).toBeVisible()
    await expect(page.getByText('ROS拨号运行信息')).toBeVisible()
    await expect(page.getByText('线路基础信息')).toHaveCount(0)
    await page.locator('.el-descriptions').getByRole('button').first().click()
    await expect(page).toHaveURL(/\/resources\/centos\/\d+$/)
    await expect(page.getByRole('heading', { name: 'CentOS详情' })).toBeVisible()
    await expect(page.getByText('基础与运行信息')).toBeVisible()
    await expect(page.getByText('ROS拨号运行信息')).toHaveCount(0)
  })

  test('line IP history load failure is explicit rather than presenting an empty history', async ({ page }) => {
    await page.route('**/api/admin/v1/v12/resources/lines/*/ip-history*', async route => {
      await route.fulfill({ status: 500, contentType: 'application/json',
        body: JSON.stringify({ code: 'TEST_UPSTREAM_FAILURE', message: 'synthetic failure' }) })
    })
    await page.goto(base + '/resources/lines')
    await page.locator('.el-table__body tr').first().getByRole('button', { name: '详情' }).click()
    await expect(page.getByRole('heading', { name: '家宽/线路详情' })).toBeVisible()
    await expect(page.getByText('IP变化历史读取失败', { exact: false })).toBeVisible()
    await expect(page.getByRole('tab', { name: 'IP变化历史' })).toBeVisible()
  })

  test('device detail request errors visibly and retry reloads actual PostgreSQL record', async ({ page }) => {
    const matcher='**/api/admin/v1/v12/resources/centos/*'
    await page.route(matcher, async route => {
      await route.fulfill({ status: 503, contentType: 'application/json',
        body: JSON.stringify({ code: 'CI_TEMPORARY_FAILURE', message: 'retry required' }) })
    })
    await page.goto(base + '/resources/centos')
    await page.locator('.el-table__body tr').first().getByRole('button', { name: '详情' }).click()
    await expect(page.getByText('资源详情读取失败，无法确认当前设备或线路状态。请重新加载。')).toBeVisible()
    await expect(page.getByText('基础与运行信息')).toHaveCount(0)
    await page.unroute(matcher)
    await page.getByRole('button', { name: '重试加载详情' }).click()
    await expect(page.getByText('基础与运行信息')).toBeVisible()
    await expect(page.getByText('资源详情读取失败，无法确认当前设备或线路状态。请重新加载。')).toHaveCount(0)
  })

  test('resource list HTTP failure cannot masquerade as an empty inventory and retry recovers', async ({ page }) => {
    const matcher = '**/api/admin/v1/v12/resources/lines?*'
    await page.route(matcher, async route => {
      await route.fulfill({ status: 503, contentType: 'application/json',
        body: JSON.stringify({ code: 'CI_RESOURCE_DOWN', message: 'synthetic read failure' }) })
    })
    await page.goto(base + '/resources/lines')
    await expect(page.getByText('资源列表查询失败，不能据此认为没有设备或线路。')).toBeVisible()
    await expect(page.getByText('资源查询失败', { exact: true })).toBeVisible()
    await page.unroute(matcher)
    await page.getByRole('button', { name: '重新查询资源' }).click()
    await expect(page.getByText('资源列表查询失败，不能据此认为没有设备或线路。')).toHaveCount(0)
    await expect(page.locator('.el-table__body tr').first()).toBeVisible()
  })

  test('order detail API failure cannot display a stale payment status and can retry', async ({ page }) => {
    expect(multiOrderId).toBeTruthy()
    const matcher = '**/api/admin/v1/orders/' + multiOrderId
    await page.route(matcher, route => route.fulfill({
      status: 503, contentType: 'application/json',
      body: JSON.stringify({ code: 'CI_ORDER_READ_FAILURE', message: 'synthetic outage' }),
    }))
    await page.goto(base + '/orders/' + multiOrderId)
    await expect(page.getByText('订单详情读取失败，无法核实付款、订单项和实际服务。')).toBeVisible()
    await expect(page.getByText('订单主记录')).toHaveCount(0)
    await expect(page.getByRole('button', { name: '进入服务开通向导' })).toHaveCount(0)
    await page.unroute(matcher)
    await page.getByRole('button', { name: '重新加载订单' }).click()
    await expect(page.getByText('订单主记录')).toBeVisible()
    await expect(page.getByText('订单详情读取失败，无法核实付款、订单项和实际服务。')).toHaveCount(0)
  })

  test('service detail API outage hides previous delivery evidence and can reload', async ({ page }) => {
    expect(serviceId).toBeTruthy()
    const matcher = '**/api/admin/v1/services/' + serviceId
    await page.route(matcher, route => route.fulfill({
      status: 503, contentType: 'application/json',
      body: JSON.stringify({ code: 'CI_SERVICE_READ_FAILURE', message: 'synthetic outage' }),
    }))
    await page.goto(base + '/services/' + serviceId)
    await expect(page.getByText('服务详情读取失败，无法核实当前服务状态、凭证或真实交付。')).toBeVisible()
    await expect(page.getByText('真实交付状态')).toHaveCount(0)
    await page.unroute(matcher)
    await page.getByRole('button', { name: '重新加载服务' }).click()
    await expect(page.getByText('真实交付状态')).toBeVisible()
    await expect(page.getByText('MOCK_ONLY').first()).toBeVisible()
  })

  test('service credential read error is not represented as no credentials', async ({ page }) => {
    expect(serviceId).toBeTruthy()
    await page.route('**/api/admin/v1/services/' + serviceId + '/credentials', route => route.fulfill({
      status: 503, contentType: 'application/json',
      body: JSON.stringify({ code: 'CI_CREDENTIAL_READ_FAILURE', message: 'synthetic outage' }),
    }))
    await page.goto(base + '/services/' + serviceId)
    await page.getByRole('tab', { name: '服务凭证' }).click()
    await expect(page.getByText('凭证查询失败或权限已变化，不能据此认定没有服务凭证。')).toBeVisible()
    await expect(page.getByText('凭证读取失败')).toBeVisible()
  })

  test('order and service lists show read failures and recover without misreporting zero records', async ({ page }) => {
    for (const [path, matcher, errorMessage, buttonName] of [
      ['/orders', '**/api/admin/v1/orders?*', '订单列表查询失败，不能据此认定客户没有订单。', '重新查询订单'],
      ['/services', '**/api/admin/v1/services?*', '服务列表查询失败，不能据此认定客户没有服务实例。', '重新查询服务'],
    ]) {
      await page.route(matcher, route => route.fulfill({
        status: 503, contentType: 'application/json',
        body: JSON.stringify({ code: 'CI_LIST_FAILURE', message: 'synthetic outage' }),
      }))
      await page.goto(base + path)
      await expect(page.getByText(errorMessage)).toBeVisible()
      await page.unroute(matcher)
      await page.getByRole('button', { name: buttonName }).click()
      await expect(page.getByText(errorMessage)).toHaveCount(0)
      await expect(page.locator('.el-table__body tr').first()).toBeVisible()
    }
  })

  test('SPA order-to-order navigation refreshes the actual record and item children', async ({ page }) => {
    expect(multiOrderId).toBeTruthy()
    expect(unpaidOrderId).toBeTruthy()
    expect(unpaidOrderId).not.toBe(multiOrderId)
    await page.goto(base + '/orders/' + multiOrderId)
    await expect(page.getByText('订单主记录')).toBeVisible()
    const firstOrderNo = (await page.locator('.head h2').textContent()) || ''
    await page.evaluate(async id => {
      const app = (document.querySelector('#app') as any).__vue_app__
      await app.config.globalProperties.$router.push('/orders/' + id)
    }, unpaidOrderId)
    await expect(page).toHaveURL(base + '/orders/' + unpaidOrderId)
    await expect(page.locator('.head h2')).not.toHaveText(firstOrderNo)
    await expect(page.getByText('订单主记录')).toBeVisible()
    await expect(page.getByText('未核实', { exact: true })).toBeVisible()
  })

  test('SPA service route change to a missing record never retains previous credentials or delivery', async ({ page }) => {
    expect(serviceId).toBeTruthy()
    await page.goto(base + '/services/' + serviceId)
    await expect(page.getByText('MOCK_ONLY').first()).toBeVisible()
    await page.evaluate(async () => {
      const app = (document.querySelector('#app') as any).__vue_app__
      await app.config.globalProperties.$router.push('/services/0')
    })
    await expect(page).toHaveURL(base + '/services/0')
    await expect(page.getByText('服务详情读取失败，无法核实当前服务状态、凭证或真实交付。')).toBeVisible()
    await expect(page.getByText('MOCK_ONLY')).toHaveCount(0)
    await expect(page.getByRole('tab', { name: '服务凭证' })).toHaveCount(0)
  })

  test('resource pool loads member/usage tabs from PostgreSQL', async ({ page }) => {
    expect(poolId).toBeTruthy()
    const operationRead = page.waitForResponse(response =>
      response.url().includes('/v12/resource-pools/' + poolId + '/operations') &&
      response.request().method() === 'GET')
    await page.goto(base + '/resource-pools/' + poolId)
    const operationResponse = await operationRead
    expect(operationResponse.status(), 'The pool audit query must not fail on real PostgreSQL').toBe(200)
    const audit = (await operationResponse.json()).data
    expect(Array.isArray(audit)).toBe(true)
    expect(audit.length).toBeGreaterThan(0)
    expect(audit[0]).toHaveProperty('created_at')
    await expect(page.getByRole('heading', { name: 'V12 Shared Pool' })).toBeVisible()
    await expect(page.getByRole('tab', { name: '全部线路' })).toBeVisible()
    await expect(page.locator('.el-table__body tr').first()).toBeVisible()
    await page.getByRole('tab', { name: '使用情况' }).click()
    await expect(page.getByText('这里仅展示直接绑定此资源池的在用服务', { exact: false })).toBeVisible()
    await page.getByRole('tab', { name: '变更记录' }).click()
    // ResourcePoolDetailView renders content below its self-closing tab
    // headers, unlike the order/customer pages that use el-tab-pane bodies.
    await expect(page.getByRole('columnheader', { name: '操作人' })).toBeVisible()
    await expect(page.locator('.el-table__body tr').first()).toBeVisible()
  })

  test('resource pool audit HTTP error must be visible and recoverable, not a false empty table', async ({ page }) => {
    expect(poolId).toBeTruthy()
    const auditUrl = '**/api/admin/v1/v12/resource-pools/' + poolId + '/operations*'
    await page.route(auditUrl, async route => {
      await route.fulfill({
        status: 500, contentType: 'application/json',
        body: JSON.stringify({ code: 'CI_FORCED_AUDIT_FAILURE', message: 'forced audit failure' }),
      })
    })
    await page.goto(base + '/resource-pools/' + poolId)
    await page.getByRole('tab', { name: '变更记录' }).click()
    await expect(page.getByText('变更记录查询失败或无权限，不能据此认为没有变更记录。')).toBeVisible()
    await expect(page.getByText('变更记录加载失败')).toBeVisible()

    await page.unroute(auditUrl)
    const recovered = page.waitForResponse(r =>
      r.url().includes('/v12/resource-pools/' + poolId + '/operations') &&
      r.request().method() === 'GET')
    await page.getByRole('button', { name: '重新加载变更记录' }).click()
    expect((await recovered).status()).toBe(200)
    await expect(page.getByText('变更记录查询失败或无权限，不能据此认为没有变更记录。')).toHaveCount(0)
    await expect(page.locator('.el-table__body tr').first()).toBeVisible()
  })

  test('eight primary business lists render through one authenticated session', async ({ page }) => {
    const paths = [
      '/customers', '/orders', '/services', '/resource-pools',
      '/supplier-purchases', '/reverse-proxies', '/products', '/packages',
    ]
    for (const path of paths) {
      await page.goto(base + path)
      expect(new URL(page.url()).pathname, 'Navigating to ' + path).toBe(path)
      await expect(page.locator('.console-main .el-table').first(),
        'Actual Element Plus table should render on ' + path).toBeVisible({ timeout: 10000 })
    }
  })

  test('additional 17 V1.3 management sections render in one real browser session', async ({ page }) => {
    // Route-level smoke only: no claim that every field or product network
    // interaction within these sections has been accepted.
    const routes = [
      '/resources', '/resources/centos', '/resources/ros', '/resources/lines',
      '/exclusive-allocations', '/domains', '/suppliers',
      '/customer-auth', '/customer-accounts', '/monitor', '/logs',
      '/long-monitor/monitors', '/long-monitor/bots',
      '/long-monitor/alarms', '/long-monitor/notifications',
      '/statistics', '/system',
    ]
    for (const route of routes) {
      await page.goto(base + route)
      await expect(page, 'router unexpectedly left ' + route).toHaveURL(base + route)
      await expect(page.locator('.console-main')).toBeVisible()
      await expect(page.locator('.console-main h1, .console-main h2, .console-main .el-card, .console-main .el-table').first(),
        'Missing visible business content at ' + route).toBeVisible({ timeout: 10000 })
    }
  })

  test('unpaid order cannot enter provisioning wizard or submit product actions', async ({ page }) => {
    expect(unpaidOrderId).toBeTruthy()
    const provisionPosts: string[] = []
    page.on('request', req => {
      if (req.method() === 'POST' && req.url().includes('/provision'))
        provisionPosts.push(req.url())
    })
    await page.goto(base + '/service-provision?orderId=' + unpaidOrderId)
    await expect(page.getByRole('heading', { name: '核对订单和付款' })).toBeVisible()
    await expect(page.getByText('客户付款尚未核实，订单确认不等于付款成功', { exact: false })).toBeVisible()
    await expect(page.getByRole('button', { name: '下一步' })).toBeDisabled()
    await expect(page.getByText('配置订单项交付方式')).toHaveCount(0)
    expect(provisionPosts).toHaveLength(0)
    await page.getByRole('button', { name: '打开订单详情核实付款' }).click()
    await expect(page).toHaveURL(base + '/orders/' + unpaidOrderId)
    await expect(page.getByText('未核实', { exact: true })).toBeVisible()
  })

  test('customer-to-new-order deep link preselects customer and supports master-child editing', async ({ page }) => {
    expect(customerId).toBeTruthy()
    await page.goto(base + '/customers/' + customerId)
    await page.getByRole('button', { name: '为该客户创建订单' }).click()
    await expect(page).toHaveURL(/\/orders\?/)
    await expect(page.getByRole('dialog', { name: '新增订单' })).toBeVisible()
    const dialog = page.getByRole('dialog', { name: '新增订单' })
    await expect(dialog.getByText('V12 CI Customer', { exact: false }).first()).toBeVisible()
    await expect(dialog.locator('.line-item')).toHaveCount(1)
    await dialog.getByRole('button', { name: '增加订单项' }).click()
    await expect(dialog.locator('.line-item')).toHaveCount(2)
    await dialog.getByRole('button', { name: '删除该项' }).last().click()
    await expect(dialog.locator('.line-item')).toHaveCount(1)
    await dialog.getByRole('button', { name: '取消' }).click()
  })

  test('expiry and supplier management keep real-network warnings visible', async ({ page }) => {
    await page.goto(base + '/service-releases')
    await expect(page.getByRole('heading', { name: '到期与资源释放' })).toBeVisible()
    await expect(page.getByText('没有实际停服、恢复或回收的产品/资源接口回执', { exact: false })).toBeVisible()
    await page.goto(base + '/supplier-purchases')
    await expect(page.getByRole('heading', { name: '外部套餐 / 资源采购' })).toBeVisible()
    await page.goto(base + '/reverse-proxies')
    await expect(page.getByRole('heading', { name: '反向代理交付' })).toBeVisible()
    await expect(page.getByText('仅配置登记不代表真实交付。', { exact: false })).toBeVisible()
  })
})
