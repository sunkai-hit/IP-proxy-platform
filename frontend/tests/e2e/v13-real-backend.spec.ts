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
      const recovery = page.waitForResponse(r =>
        new URL(r.url()).pathname === '/api/admin/v1' + path &&
        r.request().method() === 'GET')
      await page.getByRole('button', { name: buttonName }).click()
      const response = await recovery
      expect(response.status()).toBe(200)
      const payload = (await response.json()).data
      expect(Array.isArray(payload.items)).toBe(true)
      await expect(page.getByText(errorMessage)).toHaveCount(0)
      await expect(page.locator('.el-table').first()).toBeVisible()
      if (payload.items.length) await expect(page.locator('.el-table__body tr').first()).toBeVisible()
    }
  })

  test('order ledger is readable when optional customer/product selectors fail and unpaid provision is not offered', async ({ page }) => {
    await page.route('**/api/admin/v1/customers?*', route => route.fulfill({
      status: 503, contentType: 'application/json',
      body: JSON.stringify({ code: 'CI_CUSTOMER_LOOKUP_DOWN', message: 'synthetic failure' }),
    }))
    await page.route('**/api/admin/v1/products?*', route => route.fulfill({
      status: 503, contentType: 'application/json',
      body: JSON.stringify({ code: 'CI_PRODUCT_LOOKUP_DOWN', message: 'synthetic failure' }),
    }))
    await page.goto(base + '/orders')
    await expect(page.getByText('客户或产品筛选选项读取失败；订单列表仍可独立查询。')).toBeVisible()
    await expect(page.locator('.el-table__body tr').first()).toBeVisible()
    // The unpaid order should have a detail link, not a misleading provision shortcut.
    const row = page.locator('.el-table__body tr').filter({ hasText: '待核实' }).first()
    if (await row.count()) await expect(row.getByRole('button', { name: '开通', exact: true })).toHaveCount(0)
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

  test('monitor object outage is visible and never counted as healthy zero devices', async ({ page }) => {
    await page.route('**/api/admin/v1/monitor/objects?*', route => route.fulfill({
      status: 503, contentType: 'application/json',
      body: JSON.stringify({ code: 'CI_MONITOR_DOWN', message: 'synthetic outage' }),
    }))
    await page.goto(base + '/monitor')
    await expect(page.getByText('监控对象查询失败，不能据此认定没有设备或线路。')).toBeVisible()
    await expect(page.getByText('监控读取失败')).toBeVisible()
    await page.getByRole('tab', { name: 'ROS监控' }).click()
    await expect(page.getByText('监控对象查询失败，不能据此认定没有设备或线路。')).toBeVisible()
  })

  test('monitor summary outage reports unknown rather than healthy / abnormal zero', async ({ page }) => {
    await page.route('**/api/admin/v1/monitor/overview', route => route.fulfill({
      status: 503, contentType: 'application/json',
      body: JSON.stringify({ code: 'CI_OVERVIEW_DOWN', message: 'synthetic outage' }),
    }))
    await page.goto(base + '/monitor')
    await expect(page.getByText('监控总览加载失败，健康/异常统计未知，不能按零显示。')).toBeVisible()
    await expect(page.locator('.metrics strong')).toHaveCount(4)
    await expect(page.locator('.metrics strong').first()).toHaveText('—')
  })

  test('statistics category outage keeps records unknown instead of reporting actual zero usage', async ({ page }) => {
    await page.route('**/api/admin/v1/statistics/resources?*', route => route.fulfill({
      status: 503, contentType: 'application/json',
      body: JSON.stringify({ code: 'CI_STATS_DOWN', message: 'synthetic outage' }),
    }))
    await page.goto(base + '/statistics')
    await expect(page.getByText('当前分类统计读取失败，图表和记录均不可据此判断实际为0。')).toBeVisible()
    await expect(page.getByText('统计读取失败', { exact: true })).toBeVisible()
    await page.getByRole('tab', { name: '资源池统计' }).click()
    await expect(page.getByText('当前分类统计读取失败，图表和记录均不可据此判断实际为0。')).toHaveCount(0)
  })

  test('eleven-category log center shows source read errors rather than a normal empty ledger', async ({ page }) => {
    await page.route('**/api/admin/v1/runtime-logs/ip-extract?*', route => route.fulfill({
      status: 503, contentType: 'application/json',
      body: JSON.stringify({ code: 'CI_LOG_DOWN', message: 'synthetic outage' }),
    }))
    await page.goto(base + '/logs')
    await expect(page.getByText('日志分类读取失败或权限不足，不能据此认定没有日志事件。')).toBeVisible()
    await expect(page.getByText('日志数据读取失败', { exact: true })).toBeVisible()
    await expect(page.getByRole('tab', { name: '02 使用日志' })).toBeVisible()
  })

  test('long monitor alert and notification API outages cannot imply recovery or delivery', async ({ page }) => {
    await page.route('**/api/admin/v1/long-monitor/alarms?*', route => route.fulfill({
      status: 503, contentType: 'application/json',
      body: JSON.stringify({ code: 'CI_ALARM_DOWN', message: 'synthetic outage' }),
    }))
    await page.goto(base + '/long-monitor/alarms')
    await expect(page.getByText('长效告警查询失败，不能据此认定告警已经恢复或不存在。')).toBeVisible()
    await page.route('**/api/admin/v1/long-monitor/notifications?*', route => route.fulfill({
      status: 503, contentType: 'application/json',
      body: JSON.stringify({ code: 'CI_NOTIFICATION_DOWN', message: 'synthetic outage' }),
    }))
    await page.goto(base + '/long-monitor/notifications')
    await expect(page.getByText('通知记录查询失败，不能据此认定消息发送成功或不存在。')).toBeVisible()
  })

  test('expiry queue failure never looks like no pending stop or release work', async ({ page }) => {
    await page.route('**/api/admin/v1/v12/service-expiry/queue?*', route => route.fulfill({
      status: 503, contentType: 'application/json',
      body: JSON.stringify({ code: 'CI_EXPIRY_QUEUE_DOWN', message: 'synthetic outage' }),
    }))
    await page.goto(base + '/service-releases')
    await expect(page.getByText('到期任务队列读取失败，不能据此认定没有待停服或待释放任务。')).toBeVisible()
    await expect(page.getByText('到期队列读取失败', { exact: true })).toBeVisible()
  })

  test('alarm detail HTTP503 hides the close action and navigation cannot retain prior alarm', async ({ page }) => {
    const first='**/api/admin/v1/long-monitor/alarms/900991'
    const next='**/api/admin/v1/long-monitor/alarms/900992'
    await page.route(first, route => route.fulfill({
      status:503,contentType:'application/json',
      body:JSON.stringify({code:'CI_ALARM_DOWN',message:'synthetic error'}),
    }))
    await page.goto(base + '/long-monitor/alarms/900991')
    await expect(page.getByText('告警详情读取失败，不能据此判断告警已恢复、已归档或可执行归档。')).toBeVisible()
    await expect(page.getByRole('button',{name:'确认归档'})).toHaveCount(0)
    await page.unroute(first)
    await page.route(first,route=>route.fulfill({
      status:200,contentType:'application/json',
      body:JSON.stringify({code:'OK',data:{id:900991,alarm_no:'CI-ALARM-900991',status:'OPEN',monitor_status:'UNKNOWN'}}),
    }))
    await page.getByRole('button',{name:'重新加载告警详情'}).click()
    await expect(page.getByRole('heading',{name:'CI-ALARM-900991'})).toBeVisible()
    await expect(page.getByRole('button',{name:'确认归档'})).toBeVisible()
    await page.route(next,route=>route.fulfill({
      status:503,contentType:'application/json',
      body:JSON.stringify({code:'CI_ALARM_DOWN',message:'synthetic error'}),
    }))
    await page.evaluate(async()=>{
      const app=(document.querySelector('#app') as any).__vue_app__
      await app.config.globalProperties.$router.push('/long-monitor/alarms/900992')
    })
    await expect(page.getByText('告警详情读取失败，不能据此判断告警已恢复、已归档或可执行归档。')).toBeVisible()
    await expect(page.getByRole('button',{name:'确认归档'})).toHaveCount(0)
    await expect(page.getByRole('heading',{name:'CI-ALARM-900991'})).toHaveCount(0)
  })

  test('bot detail route changes never retain previous webhook mask or monitor children', async ({ page }) => {
    const first='**/api/admin/v1/long-monitor/bots/900991'
    await page.route(first,route=>route.fulfill({
      status:200,contentType:'application/json',
      body:JSON.stringify({code:'OK',data:{
        id:900991,bot_no:'CI-BOT-900991',bot_name:'ci-bot',webhook_mask:'CI-WEBHOOK-MASK',
        accessTokenConfigured:true,secretConfigured:true,
        monitorReferences:[{id:900991,monitor_no:'CI-REF-900991'}],
      }}),
    }))
    await page.goto(base + '/long-monitor/bots/900991')
    await expect(page.getByText('CI-WEBHOOK-MASK')).toBeVisible()
    await expect(page.getByRole('button',{name:'CI-REF-900991'})).toBeVisible()
    await page.route('**/api/admin/v1/long-monitor/bots/900992',route=>route.fulfill({
      status:503,contentType:'application/json',
      body:JSON.stringify({code:'CI_BOT_DOWN',message:'synthetic error'}),
    }))
    await page.evaluate(async()=>{
      const app=(document.querySelector('#app') as any).__vue_app__
      await app.config.globalProperties.$router.push('/long-monitor/bots/900992')
    })
    await expect(page.getByText('机器人详情读取失败，不能据此认定凭证未配置或监控没有绑定。')).toBeVisible()
    await expect(page.getByText('CI-WEBHOOK-MASK')).toHaveCount(0)
    await expect(page.getByText('CI-REF-900991')).toHaveCount(0)
    await expect(page.getByRole('button',{name:'重新加载机器人'})).toBeVisible()
  })

  test('long monitor detail route changes clear old checked history before failed new read', async ({ page }) => {
    await page.route('**/api/admin/v1/long-monitor/monitors/900991',route=>route.fulfill({
      status:200,contentType:'application/json',
      body:JSON.stringify({code:'OK',data:{id:900991,monitor_no:'CI-MONITOR-900991',
        customer_id:1,service_id:1,checkHistory:[{checked_at:'2026-10-16T00:00:00Z',response_code:'CI-CHECK-200'}]}}),
    }))
    await page.goto(base + '/long-monitor/monitors/900991')
    await expect(page.getByText('CI-MONITOR-900991')).toBeVisible()
    await expect(page.getByText('CI-CHECK-200')).toBeVisible()
    await page.route('**/api/admin/v1/long-monitor/monitors/900992',route=>route.fulfill({
      status:503,contentType:'application/json',
      body:JSON.stringify({code:'CI_MONITOR_DOWN',message:'synthetic error'}),
    }))
    await page.evaluate(async()=>{
      const app=(document.querySelector('#app') as any).__vue_app__
      await app.config.globalProperties.$router.push('/long-monitor/monitors/900992')
    })
    await expect(page.getByText('长效监控详情读取失败，不能据此认定目标健康或没有检测记录。')).toBeVisible()
    await expect(page.getByText('CI-MONITOR-900991')).toHaveCount(0)
    await expect(page.getByText('CI-CHECK-200')).toHaveCount(0)
  })

  test('CentOS-to-ROS monitor metric detail route reuse cannot show previous CPU samples', async ({ page }) => {
    await page.route('**/api/admin/v1/monitor/objects/CENTOS/900991/metrics?*',route=>route.fulfill({
      status:200,contentType:'application/json',
      body:JSON.stringify({code:'OK',data:[{collected_at:'2026-10-16T00:00:00Z',
        metric_code:'CI-CENTOS-CPU',metric_value:99}]}),
    }))
    await page.goto(base + '/monitor/centos/900991')
    await expect(page.getByText('CI-CENTOS-CPU')).toBeVisible()
    await page.route('**/api/admin/v1/monitor/objects/ROS/900992/metrics?*',route=>route.fulfill({
      status:503,contentType:'application/json',
      body:JSON.stringify({code:'CI_METRICS_DOWN',message:'synthetic error'}),
    }))
    await page.evaluate(async()=>{
      const app=(document.querySelector('#app') as any).__vue_app__
      await app.config.globalProperties.$router.push('/monitor/ros/900992')
    })
    await expect(page.getByText('设备监控历史读取失败，不能据此认定设备正常或没有异常事件。')).toBeVisible()
    await expect(page.getByText('CI-CENTOS-CPU')).toHaveCount(0)
    await expect(page.getByRole('heading',{name:'ROS 监控详情'})).toBeVisible()
  })

  test('bot send test failing HTTP cannot appear as success, and bot list shows source errors', async ({ page }) => {
    const listing='**/api/admin/v1/long-monitor/bots?*'
    await page.route(listing,route=>route.fulfill({
      status:200,contentType:'application/json',
      body:JSON.stringify({code:'OK',data:{items:[{
        id:900991,bot_no:'CI-BOT-900991',bot_name:'CI Bot',bot_type:'DINGTALK',
        customer_name:'CI Customer',enabled:true,
      }],total:1}}),
    }))
    await page.route('**/api/admin/v1/long-monitor/bots/900991/test',route=>route.fulfill({
      status:503,contentType:'application/json',
      body:JSON.stringify({code:'CI_BOT_SEND_FAILURE',message:'not delivered'}),
    }))
    await page.goto(base + '/long-monitor/bots')
    await expect(page.getByText('CI-BOT-900991')).toBeVisible()
    await page.getByRole('button',{name:'测试',exact:true}).click()
    await expect(page.getByText('机器人测试未确认成功，请检查发送回执')).toBeVisible()
    await page.unroute(listing)
    await page.route(listing,route=>route.fulfill({
      status:503,contentType:'application/json',
      body:JSON.stringify({code:'CI_BOT_LIST_DOWN',message:'unavailable'}),
    }))
    await page.getByRole('button',{name:'查询',exact:true}).click()
    await expect(page.getByText('机器人列表读取失败，不能据此认定没有客户机器人或凭证。')).toBeVisible()
    await expect(page.getByText('CI-BOT-900991')).toHaveCount(0)
  })

  test('long monitor unknown adapter response does not claim real network detection success', async ({ page }) => {
    await page.route('**/api/admin/v1/long-monitor/monitors?*',route=>route.fulfill({
      status:200,contentType:'application/json',
      body:JSON.stringify({code:'OK',data:{items:[{
        id:900991,monitor_no:'CI-MON-900991',customer_name:'CI Customer',
        service_no:'CI-SERVICE-900991',target_host:'127.0.0.1',target_port:8080,
        current_status:'UNKNOWN',enabled:true,
      }],total:1}}),
    }))
    await page.route('**/api/admin/v1/long-monitor/monitors/900991/check',route=>route.fulfill({
      status:200,contentType:'application/json',
      body:JSON.stringify({code:'OK',data:{adapterStatus:'UNKNOWN',after:'UNKNOWN'}}),
    }))
    await page.goto(base + '/long-monitor/monitors')
    await expect(page.getByText('CI-MON-900991')).toBeVisible()
    await page.getByRole('button',{name:'检测',exact:true}).click()
    await expect(page.getByText('检测适配器未返回真实可验证结果，状态仍为 UNKNOWN')).toBeVisible()
  })

  test('reverse proxy details fail closed and cannot expose apply or disable buttons', async ({ page }) => {
    const first='**/api/admin/v1/v12/external-delivery/reverse-proxies/900991'
    await page.route(first,route=>route.fulfill({
      status:503,contentType:'application/json',
      body:JSON.stringify({code:'CI_PROXY_DOWN',message:'synthetic network error'}),
    }))
    await page.goto(base+'/reverse-proxies/900991')
    await expect(page.getByText('反向代理详情读取失败，禁止推断配置已生效或网关可连接。')).toBeVisible()
    await expect(page.getByRole('button',{name:'应用配置'})).toHaveCount(0)
    await expect(page.getByRole('button',{name:'受控停用'})).toHaveCount(0)
    await page.unroute(first)
    await page.route(first,route=>route.fulfill({
      status:200,contentType:'application/json',
      body:JSON.stringify({code:'OK',data:{id:900991,proxy_no:'CI-PROXY-900991',status:'PENDING'}}),
    }))
    await page.getByRole('button',{name:'重新加载反向代理'}).click()
    await expect(page.getByRole('heading',{name:'CI-PROXY-900991'})).toBeVisible()
    await expect(page.getByRole('button',{name:'应用配置'})).toBeVisible()
    await page.route('**/api/admin/v1/v12/external-delivery/reverse-proxies/900992',route=>route.fulfill({
      status:503,contentType:'application/json',
      body:JSON.stringify({code:'CI_PROXY_DOWN',message:'synthetic network error'}),
    }))
    await page.evaluate(async()=>{
      const app=(document.querySelector('#app') as any).__vue_app__
      await app.config.globalProperties.$router.push('/reverse-proxies/900992')
    })
    await expect(page.getByText('反向代理详情读取失败，禁止推断配置已生效或网关可连接。')).toBeVisible()
    await expect(page.getByText('CI-PROXY-900991')).toHaveCount(0)
    await expect(page.getByRole('button',{name:'应用配置'})).toHaveCount(0)
  })

  test('supplier purchase route reuse drops previous paid-order and proxy associations', async ({ page }) => {
    await page.route('**/api/admin/v1/v12/external-delivery/purchases/900991',route=>route.fulfill({
      status:200,contentType:'application/json',
      body:JSON.stringify({code:'OK',data:{id:900991,purchase_no:'CI-PURCHASE-900991',
        customer_id:1,source_customer_order_id:10,customer_name:'CI Customer'}}),
    }))
    await page.route('**/api/admin/v1/relations/supplier-purchases/900991/proxies',route=>route.fulfill({
      status:200,contentType:'application/json',
      body:JSON.stringify({code:'OK',data:{items:[{id:900991,proxy_no:'CI-CHILD-PROXY-900991'}],total:1}}),
    }))
    await page.route('**/api/admin/v1/relations/supplier-purchases/900991/services',route=>route.fulfill({
      status:200,contentType:'application/json',
      body:JSON.stringify({code:'OK',data:{items:[],total:0}}),
    }))
    await page.goto(base+'/supplier-purchases/900991')
    await expect(page.getByRole('heading',{name:'CI-PURCHASE-900991'})).toBeVisible()
    await expect(page.getByText('CI-CHILD-PROXY-900991')).toBeVisible()
    await page.route('**/api/admin/v1/v12/external-delivery/purchases/900992',route=>route.fulfill({
      status:503,contentType:'application/json',
      body:JSON.stringify({code:'CI_PURCHASE_DOWN',message:'synthetic error'}),
    }))
    await page.evaluate(async()=>{
      const app=(document.querySelector('#app') as any).__vue_app__
      await app.config.globalProperties.$router.push('/supplier-purchases/900992')
    })
    await expect(page.getByText('采购详情读取失败，不能据此认定已付款、无关联服务或已可实际交付。')).toBeVisible()
    await expect(page.getByText('CI-CHILD-PROXY-900991')).toHaveCount(0)
    await expect(page.getByRole('button',{name:'基于此采购登记代理'})).toHaveCount(0)
  })

  test('package detail stale linked services do not survive a failed new package read', async ({ page }) => {
    await page.route('**/api/admin/v1/packages/900991',route=>route.fulfill({
      status:200,contentType:'application/json',
      body:JSON.stringify({code:'OK',data:{id:900991,package_code:'CI-PKG-900991',
        package_name:'CI PACKAGE A',product_type:'LONG_IP',product_id:1}}),
    }))
    await page.route('**/api/admin/v1/relations/packages/900991/services',route=>route.fulfill({
      status:200,contentType:'application/json',
      body:JSON.stringify({code:'OK',data:{items:[{id:900991,service_no:'CI-PKG-SVC-900991'}]}}),
    }))
    await page.goto(base+'/packages/900991')
    await expect(page.getByRole('heading',{name:'CI PACKAGE A'})).toBeVisible()
    await expect(page.getByText('CI-PKG-SVC-900991')).toBeVisible()
    await page.route('**/api/admin/v1/packages/900992',route=>route.fulfill({
      status:503,contentType:'application/json',
      body:JSON.stringify({code:'CI_PACKAGE_DOWN',message:'synthetic error'}),
    }))
    await page.evaluate(async()=>{
      const app=(document.querySelector('#app') as any).__vue_app__
      await app.config.globalProperties.$router.push('/packages/900992')
    })
    await expect(page.getByText('套餐详情读取失败，不能核实原套餐期限、宽限规则或已售服务。')).toBeVisible()
    await expect(page.getByText('CI-PKG-SVC-900991')).toHaveCount(0)
    await expect(page.getByRole('button',{name:'在套餐管理中编辑'})).toHaveCount(0)
  })

  test('product detail failed read prevents writes and shows no previous configurable fields', async ({ page }) => {
    await page.route('**/api/admin/v1/products/900991',route=>route.fulfill({
      status:200,contentType:'application/json',
      body:JSON.stringify({code:'OK',data:{id:900991,product_code:'CI-PROD-900991',
        product_name:'CI PRODUCT A',product_type:'SHORT_IP',access_mode_code:'HTTP',
        protocol_codes:['HTTP'],auth_mode_codes:['TOKEN'],type_config:{}}}),
    }))
    await page.goto(base+'/products/900991')
    await expect(page.getByRole('heading',{name:/CI PRODUCT A/})).toBeVisible()
    await expect(page.getByText('CI-PROD-900991').first()).toBeVisible()
    await page.route('**/api/admin/v1/products/900992',route=>route.fulfill({
      status:503,contentType:'application/json',
      body:JSON.stringify({code:'CI_PRODUCT_DOWN',message:'synthetic error'}),
    }))
    await page.evaluate(async()=>{
      const app=(document.querySelector('#app') as any).__vue_app__
      await app.config.globalProperties.$router.push('/products/900992')
    })
    await expect(page.getByText('产品详情读取失败，不能确认当前类型、专项参数或可售状态。')).toBeVisible()
    await expect(page.getByText('CI-PROD-900991')).toHaveCount(0)
    await expect(page.getByRole('button',{name:'保存',exact:true})).toHaveCount(0)
    await expect(page.getByRole('button',{name:'保存专项参数'})).toHaveCount(0)
  })

  test('legacy resource details re-fetch when switching domain or exclusive route', async ({ page }) => {
    await page.route('**/api/admin/v1/exclusive-allocations/900991',route=>route.fulfill({
      status:200,contentType:'application/json',
      body:JSON.stringify({code:'OK',data:{id:900991,allocation_no:'CI-EXCLUSIVE-900991',status:'HELD'}}),
    }))
    await page.goto(base+'/exclusive-allocations/900991')
    await expect(page.getByText('CI-EXCLUSIVE-900991')).toBeVisible()
    await page.route('**/api/admin/v1/domains/900992',route=>route.fulfill({
      status:503,contentType:'application/json',
      body:JSON.stringify({code:'CI_DOMAIN_DOWN',message:'synthetic error'}),
    }))
    await page.evaluate(async()=>{
      const app=(document.querySelector('#app') as any).__vue_app__
      await app.config.globalProperties.$router.push('/domains/900992')
    })
    await expect(page.getByText('资源详情读取失败，无法核实当前配置与关联资源。')).toBeVisible()
    await expect(page.getByText('CI-EXCLUSIVE-900991')).toHaveCount(0)
  })

  test('supplier detail route failure clears previous supplier auth and purchase table', async ({ page }) => {
    await page.route('**/api/admin/v1/suppliers/900991',route=>route.fulfill({
      status:200,contentType:'application/json',
      body:JSON.stringify({code:'OK',data:{id:900991,supplier_code:'CI-SUPPLIER-900991',
        supplier_name:'CI Supplier A',secret_configured:true,interface_status:'UNVERIFIED'}}),
    }))
    await page.route('**/api/admin/v1/v12/external-delivery/purchases?*',route=>route.fulfill({
      status:200,contentType:'application/json',
      body:JSON.stringify({code:'OK',data:{items:[{id:900991,purchase_no:'CI-SUPPLIER-CHILD-900991'}],total:1}}),
    }))
    await page.goto(base+'/suppliers/900991')
    await expect(page.getByRole('heading',{name:'CI Supplier A'})).toBeVisible()
    await expect(page.getByText('CI-SUPPLIER-CHILD-900991')).toBeVisible()
    await page.route('**/api/admin/v1/suppliers/900992',route=>route.fulfill({
      status:503,contentType:'application/json',
      body:JSON.stringify({code:'CI_SUPPLIER_DOWN',message:'synthetic error'}),
    }))
    await page.evaluate(async()=>{
      const app=(document.querySelector('#app') as any).__vue_app__
      await app.config.globalProperties.$router.push('/suppliers/900992')
    })
    await expect(page.getByText('供应商详情读取失败，无法核实接口状态、认证配置与下游采购。')).toBeVisible()
    await expect(page.getByText('CI-SUPPLIER-CHILD-900991')).toHaveCount(0)
    await expect(page.getByText('已加密配置（不可回显）')).toHaveCount(0)
  })

  test('product options and packages failure are distinct from product not configured', async ({ page }) => {
    await page.route('**/api/admin/v1/products/900993',route=>route.fulfill({
      status:200,contentType:'application/json',
      body:JSON.stringify({code:'OK',data:{id:900993,product_code:'CI-PROD-900993',
        product_name:'CI PRODUCT OPTIONS',product_type:'SHORT_IP',access_mode_code:'HTTP',
        protocol_codes:[],auth_mode_codes:[],type_config:{}}}),
    }))
    await page.route('**/api/admin/v1/products/options',route=>route.fulfill({
      status:503,contentType:'application/json',
      body:JSON.stringify({code:'CI_OPTIONS_DOWN',message:'unavailable'}),
    }))
    await page.route('**/api/admin/v1/packages?*',route=>route.fulfill({
      status:503,contentType:'application/json',
      body:JSON.stringify({code:'CI_PACKAGES_DOWN',message:'unavailable'}),
    }))
    await page.goto(base+'/products/900993')
    await expect(page.getByRole('heading',{name:/CI PRODUCT OPTIONS/})).toBeVisible()
    await expect(page.getByText('产品配置选项查询失败，保存前须核实协议和认证方式。')).toBeVisible()
    await expect(page.getByText('关联套餐读取失败，不能认定该产品尚未配置销售套餐。')).toBeVisible()
    await expect(page.getByRole('button',{name:'保存',exact:true})).toBeDisabled()
  })

  test('domain list survives failed line/IP option loaders, and association save is disabled', async ({ page }) => {
    await page.route('**/api/admin/v1/resources/lines?*',route=>route.fulfill({
      status:503,contentType:'application/json',
      body:JSON.stringify({code:'CI_LINE_OPTIONS_DOWN',message:'synthetic error'}),
    }))
    await page.route('**/api/admin/v1/resources/ips?*',route=>route.fulfill({
      status:503,contentType:'application/json',
      body:JSON.stringify({code:'CI_IP_OPTIONS_DOWN',message:'synthetic error'}),
    }))
    await page.goto(base+'/domains')
    await expect(page.getByText('线路或IP选项读取失败，域名列表可查询，但保存资源关联暂不可用。')).toBeVisible()
    await expect(page.getByRole('heading',{name:'域名配置'})).toBeVisible()
    await page.getByRole('button',{name:'新增域名'}).click()
    await expect(page.getByRole('dialog')).toBeVisible()
    await expect(page.getByRole('dialog').getByRole('button',{name:'保存'})).toBeDisabled()
  })

  test('customer detail failures cannot retain previous customer identity and credentials', async ({ page }) => {
    expect(customerId).toBeTruthy()
    await page.goto(base+'/customers/'+customerId)
    await expect(page.getByRole('heading',{name:'V12 CI Customer'})).toBeVisible()
    await page.route('**/api/admin/v1/customers/900992',route=>route.fulfill({
      status:503,contentType:'application/json',
      body:JSON.stringify({code:'CI_CUSTOMER_DOWN',message:'synthetic error'}),
    }))
    await page.evaluate(async()=>{
      const app=(document.querySelector('#app') as any).__vue_app__
      await app.config.globalProperties.$router.push('/customers/900992')
    })
    await expect(page.getByText('客户详情读取失败，无法确认客户归属、合同、服务或认证状态。')).toBeVisible()
    await expect(page.getByText('V12 CI Customer')).toHaveCount(0)
    await expect(page.getByRole('button',{name:'为该客户创建订单'})).toHaveCount(0)
  })

  test('customer credentials API 503 is reported independently of existing customer and orders', async ({ page }) => {
    expect(customerId).toBeTruthy()
    await page.route('**/api/admin/v1/customers/'+customerId+'/credentials',route=>route.fulfill({
      status:503,contentType:'application/json',
      body:JSON.stringify({code:'CI_CUSTOMER_CREDENTIALS_DOWN',message:'synthetic error'}),
    }))
    await page.goto(base+'/customers/'+customerId)
    await expect(page.getByRole('heading',{name:'V12 CI Customer'})).toBeVisible()
    await page.getByRole('tab',{name:'服务凭证摘要'}).click()
    await expect(page.getByText('客户服务凭证摘要读取失败，不能据此认为该客户未配置凭证。')).toBeVisible()
    await expect(page.getByText('凭证读取失败',{exact:true})).toBeVisible()
  })

  test('customer auth list outage does not report no pending applications', async ({ page }) => {
    await page.route('**/api/admin/v1/customer-auth?*',route=>route.fulfill({
      status:503,contentType:'application/json',
      body:JSON.stringify({code:'CI_KYC_LIST_DOWN',message:'synthetic error'}),
    }))
    await page.goto(base+'/customer-auth')
    await expect(page.getByText('客户认证列表读取失败，不能据此认为没有待审核申请。')).toBeVisible()
    await expect(page.getByText('认证列表读取失败',{exact:true})).toBeVisible()
  })

  test('customer list outage cannot hide customers or overdue work', async ({ page }) => {
    await page.route('**/api/admin/v1/customers?*',route=>route.fulfill({
      status:503,contentType:'application/json',
      body:JSON.stringify({code:'CI_CUSTOMER_LIST_DOWN',message:'synthetic error'}),
    }))
    await page.goto(base+'/customers')
    await expect(page.getByText('客户列表读取失败，不能据此认定没有客户或没有未终止服务。')).toBeVisible()
    await expect(page.getByText('客户列表读取失败',{exact:true})).toBeVisible()
  })

  test('customer option outage does not suppress the real customer ledger', async ({ page }) => {
    await page.route('**/api/admin/v1/customers/options',route=>route.fulfill({
      status:503,contentType:'application/json',
      body:JSON.stringify({code:'CI_CUSTOMER_OPTIONS_DOWN',message:'synthetic error'}),
    }))
    await page.goto(base+'/customers')
    await expect(page.getByText('客户类型/负责人选项读取失败，列表仍可查询；保存客户暂不可用。')).toBeVisible()
    await expect(page.getByText('V12 CI Customer')).toBeVisible()
    await page.getByRole('button',{name:'新增客户'}).click()
    await expect(page.getByRole('dialog').getByRole('button',{name:'保存'})).toBeDisabled()
  })

  test('customer KYC details cannot display stale sensitive application material after HTTP outage', async ({ page }) => {
    await page.route('**/api/admin/v1/customer-auth?*',route=>route.fulfill({
      status:200,contentType:'application/json',
      body:JSON.stringify({code:'OK',data:{items:[{id:900991,auth_no:'CI-KYC-900991',
        customer_name:'CI Customer',status:'PENDING'}],total:1}}),
    }))
    await page.route('**/api/admin/v1/customer-auth/900991',route=>route.fulfill({
      status:503,contentType:'application/json',
      body:JSON.stringify({code:'CI_KYC_DETAIL_DOWN',message:'synthetic outage'}),
    }))
    await page.goto(base+'/customer-auth')
    await expect(page.getByText('CI-KYC-900991')).toBeVisible()
    await page.getByRole('button',{name:'详情'}).click()
    await expect(page.getByText('认证详情读取失败，不能确认资料、审核状态或历史意见。')).toBeVisible()
    await expect(page.getByText('证件/主体编号',{exact:true})).toHaveCount(0)
    await expect(page.getByText('附件数量',{exact:true})).toHaveCount(0)
  })

  test('failed order provisioning precheck cannot reuse old paid or network eligibility snapshot', async ({page})=>{
    expect(multiOrderId).toBeTruthy()
    await page.route('**/api/admin/v1/orders/'+multiOrderId+'/provision-preview',route=>route.fulfill({
      status:503,contentType:'application/json',
      body:JSON.stringify({code:'CI_PRECHECK_DOWN',message:'unavailable'})
    }))
    await page.goto(base+'/service-provision?orderId='+multiOrderId)
    await expect(page.getByText('订单付款及资源预检失败或已失效，旧快照已清除，不能继续开通。')).toBeVisible()
    await expect(page.getByRole('button',{name:'确认开通'})).toHaveCount(0)
    await expect(page.getByRole('button',{name:'下一步'})).toHaveCount(0)
  })

  test('failed refresh invalidates previously paid order preview and blocks provisioning', async ({page})=>{
    expect(multiOrderId).toBeTruthy()
    const matcher='**/api/admin/v1/orders/'+multiOrderId+'/provision-preview'
    let calls=0
    await page.route(matcher,route=>{
      calls++
      if(calls>1)return route.fulfill({
        status:503,contentType:'application/json',
        body:JSON.stringify({code:'CI_REFRESH_DOWN',message:'unavailable'})
      })
      return route.fulfill({
        status:200,contentType:'application/json',
        body:JSON.stringify({code:'OK',data:{order:{id:Number(multiOrderId),order_no:'CI-PROVISION-TEST'},
          paymentReady:true,alreadyProvisioned:false,items:[{id:900991,service_count:1,
           product_type:'SHORT_IP',product_name:'CI SHORT',delivery_mode:'SELF_OWNED'}],
          availableReverseProxies:[]}})
      })
    })
    await page.goto(base+'/service-provision?orderId='+multiOrderId)
    await expect(page.getByText('CI-PROVISION-TEST')).toBeVisible()
    await page.getByRole('button',{name:'刷新付款与资源状态'}).click()
    await expect(page.getByText('订单付款及资源预检失败或已失效，旧快照已清除，不能继续开通。')).toBeVisible()
    await expect(page.getByText('CI-PROVISION-TEST')).toHaveCount(0)
    await expect(page.getByRole('button',{name:'确认开通'})).toHaveCount(0)
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

  test('A-stage paid purchase to proxy to originating order: links and offline delivery safety', async ({ page }) => {
    // CI seeds a customer-paid purchase and a PENDING proxy in PostgreSQL.
    // Check the actual UI chain; never enable a real product or upstream write.
    await page.goto(base + '/supplier-purchases')
    const purchaseRow = page.locator('.el-table__body tr').filter({ hasText: 'CI-PUR-R3' })
    await expect(purchaseRow).toBeVisible()
    await purchaseRow.getByRole('button', { name: '详情' }).click()
    await expect(page.getByRole('heading', { name: 'CI-PUR-R3' })).toBeVisible()
    await expect(page.getByText('PAID_CONFIRMED', { exact: true })).toBeVisible()
    await expect(page.getByRole('tab', { name: /反向代理明细 \(1\)/ })).toBeVisible()

    const proxyRow = page.locator('.el-table__body tr').filter({ hasText: 'CI-PROXY-R3' })
    await expect(proxyRow).toBeVisible()
    await proxyRow.getByRole('button', { name: '代理详情' }).click()
    await expect(page.getByRole('heading', { name: 'CI-PROXY-R3' })).toBeVisible()
    await expect(page.getByText('配置保存、状态ACTIVE或适配器收到传输ACK', { exact: false })).toBeVisible()

    // Management-side "apply" must fail closed while the real gateway is absent.
    await page.getByRole('button', { name: '应用配置' }).click()
    const prompt = page.locator('.el-message-box')
    await expect(prompt).toBeVisible()
    await prompt.locator('input').fill('CI offline delivery rejection verification')
    const denied = page.waitForResponse(response =>
      response.request().method() === 'POST' &&
      /\/v12\/external-delivery\/reverse-proxies\/\d+\/apply$/.test(new URL(response.url()).pathname))
    // Element Plus may render its default confirm label in English; use the
    // primary action in the prompt rather than assuming a localized button name.
    await prompt.locator('.el-message-box__btns .el-button--primary').click()
    const rejected = await denied
    expect(rejected.status()).toBe(400)
    expect((await rejected.json()).code).toBe('EXTERNAL_PROXY_DATA_PLANE_UNVERIFIED')
    await expect(page.getByText('平台代理操作未确认成功，不能据此认定已应用或已停用')).toBeVisible()
    await expect(page.getByText('PENDING', { exact: true })).toBeVisible()

    // Navigate back to the source purchase then through its paid order link.
    await page.getByRole('button', { name: 'CI-PUR-R3' }).click()
    await expect(page.getByRole('heading', { name: 'CI-PUR-R3' })).toBeVisible()
    await page.locator('.el-descriptions').getByRole('button').first().click()
    await expect(page).toHaveURL(/\/orders\/\d+$/)
    await expect(page.getByText('订单主记录')).toBeVisible()
  })

  test('A purchase ledger failure clears stale rows and offers PostgreSQL-backed retry', async ({ page }) => {
    await page.goto(base + '/supplier-purchases')
    await expect(page.getByText('CI-PUR-R3', { exact: true })).toBeVisible()
    const url = '**/api/admin/v1/v12/external-delivery/purchases*'
    await page.route(url, route => route.fulfill({
      status:503, contentType:'application/json',
      body:JSON.stringify({code:'CI_PURCHASE_LIST_DOWN',message:'synthetic outage'})
    }))
    await page.getByRole('button', { name: '查询' }).click()
    await expect(page.getByText('采购列表查询失败，不能据此判断没有采购记录或客户资源。')).toBeVisible()
    await expect(page.getByText('CI-PUR-R3', { exact: true })).toHaveCount(0)
    await page.unroute(url)
    await page.getByRole('button', { name: '重新加载采购列表' }).click()
    await expect(page.getByText('采购列表查询失败，不能据此判断没有采购记录或客户资源。')).toHaveCount(0)
    await expect(page.getByText('CI-PUR-R3', { exact: true })).toBeVisible()
  })

  test('A purchase option failure disables new registration without hiding existing ledger', async ({ page }) => {
    const url = '**/api/admin/v1/v12/external-delivery/options*'
    await page.route(url, route => route.fulfill({
      status:503, contentType:'application/json',
      body:JSON.stringify({code:'CI_PURCHASE_OPTIONS_DOWN',message:'synthetic outage'})
    }))
    await page.goto(base + '/supplier-purchases')
    await expect(page.getByText('供应商或客户选项读取失败，暂不能登记采购；已有采购记录仍可查询。')).toBeVisible()
    await expect(page.getByRole('button', { name: '登记采购' })).toBeDisabled()
    await expect(page.getByText('CI-PUR-R3', { exact: true })).toBeVisible()
    await page.unroute(url)
    const reloaded=page.waitForResponse(r=>
      r.request().method()==='GET' && new URL(r.url()).pathname.endsWith('/v12/external-delivery/options'))
    await page.getByRole('button', { name: '重新加载采购选项' }).click()
    expect((await reloaded).status()).toBe(200)
    await expect(page.getByRole('button', { name: '登记采购' })).toBeEnabled()
  })

  test('A proxy ledger failure clears stale records and recovers after real backend retry', async ({ page }) => {
    await page.goto(base + '/reverse-proxies')
    await expect(page.getByText('CI-PROXY-R3', { exact: true })).toBeVisible()
    const url = '**/api/admin/v1/v12/external-delivery/reverse-proxies*'
    await page.route(url, route => route.fulfill({
      status:503, contentType:'application/json',
      body:JSON.stringify({code:'CI_PROXY_LIST_DOWN',message:'synthetic outage'})
    }))
    await page.getByRole('button', { name: '查询' }).click()
    await expect(page.getByText('平台代理列表查询失败，不能据此判断没有代理或客户服务。')).toBeVisible()
    await expect(page.getByText('CI-PROXY-R3', { exact: true })).toHaveCount(0)
    await page.unroute(url)
    await page.getByRole('button', { name: '重新加载平台代理列表' }).click()
    await expect(page.getByText('平台代理列表查询失败，不能据此判断没有代理或客户服务。')).toHaveCount(0)
    await expect(page.getByText('CI-PROXY-R3', { exact: true })).toBeVisible()
  })

  test('A proxy list apply cannot show network success on actual backend refusal', async ({ page }) => {
    await page.goto(base + '/reverse-proxies')
    const proxy = page.locator('.el-table__body tr').filter({hasText:'CI-PROXY-R3'})
    await expect(proxy).toBeVisible()
    await expect(proxy.getByText('PENDING', { exact:true })).toBeVisible()
    await proxy.getByRole('button',{name:'应用',exact:true}).click()
    const prompt=page.locator('.el-message-box')
    await expect(prompt).toBeVisible()
    await prompt.locator('input').fill('CI safe offline list action')
    const refused=page.waitForResponse(r=>
      r.request().method()==='POST' &&
      /\/v12\/external-delivery\/reverse-proxies\/\d+\/apply$/.test(new URL(r.url()).pathname))
    await prompt.locator('.el-message-box__btns .el-button--primary').click()
    const response=await refused
    expect(response.status()).toBe(400)
    expect((await response.json()).code).toBe('EXTERNAL_PROXY_DATA_PLANE_UNVERIFIED')
    await expect(page.getByText('平台代理操作未确认成功，不能据此认定已应用、已停用或连通')).toBeVisible()
    await expect(proxy.getByText('PENDING', { exact:true })).toBeVisible()
    await expect(page.getByText('真实网关验证通过')).toHaveCount(0)
  })

  test('A2 service change fails closed if service state cannot be queried, then recovers from PostgreSQL', async ({ page }) => {
    expect(serviceId).toBeTruthy()
    const api='**/api/admin/v1/services?*'
    await page.route(api, route => route.fulfill({
      status:503, contentType:'application/json',
      body:JSON.stringify({code:'CI_SERVICE_CHOICES_DOWN',message:'synthetic outage'})
    }))
    await page.goto(base+'/service-changes?serviceId='+serviceId+'&action=suspend')
    await expect(page.getByText('服务清单或目标服务读取失败，不能确认当前服务、到期与宽限状态；禁止执行生命周期变更。')).toBeVisible()
    await expect(page.getByRole('button',{name:'预览并确认暂停'})).toBeDisabled()
    await expect(page.getByText('业务状态',{exact:true})).toHaveCount(0)
    await page.unroute(api)
    const reloaded=page.waitForResponse(r=>r.request().method()==='GET'&&
      new URL(r.url()).pathname==='/api/admin/v1/services')
    await page.getByRole('button',{name:'重新加载服务清单'}).click()
    expect((await reloaded).status()).toBe(200)
    await expect(page.getByText('服务清单或目标服务读取失败，不能确认当前服务、到期与宽限状态；禁止执行生命周期变更。')).toHaveCount(0)
    await expect(page.getByText('业务状态',{exact:true})).toBeVisible()
  })

  test('A2 canceling the confirmed change cannot submit a network or management write', async ({ page }) => {
    expect(serviceId).toBeTruthy()
    await page.goto(base+'/service-changes?serviceId='+serviceId+'&action=suspend')
    await expect(page.getByText('业务状态',{exact:true})).toBeVisible()
    const attempts:string[]=[]
    page.on('request',request=>{
      if(request.method()==='POST' && /\/api\/admin\/v1\/services\/\d+\/(suspend|resume|renew|terminate)$/.test(new URL(request.url()).pathname))
        attempts.push(request.url())
    })
    await page.getByPlaceholder('填写可审计的业务原因').fill('CI A2 preview cancellation test')
    await page.getByRole('button',{name:'预览并确认暂停'}).click()
    const prompt=page.locator('.el-message-box')
    await expect(prompt).toContainText('核对服务与影响后确认')
    await prompt.getByRole('button',{name:'返回修改'}).click()
    await expect(prompt).toHaveCount(0)
    expect(attempts).toHaveLength(0)
  })

  test('A5 monitor overview outage shows unknown not healthy zero and retries real backend', async ({ page }) => {
    await page.goto(base+'/long-monitor')
    await expect(page.getByText('监控总数',{exact:true})).toBeVisible()
    const api='**/api/admin/v1/long-monitor/overview'
    await page.route(api,route=>route.fulfill({
      status:503,contentType:'application/json',
      body:JSON.stringify({code:'CI_MONITOR_OVERVIEW_DOWN',message:'synthetic outage'})
    }))
    await page.getByRole('button',{name:'刷新总览'}).click()
    await expect(page.getByText('长效监控总览读取失败，统计值未知，不能据此认定没有异常代理、开放告警或失败通知。')).toBeVisible()
    await expect(page.locator('.cards .el-card strong')).toHaveCount(6)
    for(const item of await page.locator('.cards .el-card strong').all())await expect(item).toHaveText('—')
    await page.unroute(api)
    const reloaded=page.waitForResponse(r=>r.request().method()==='GET'&&
      new URL(r.url()).pathname==='/api/admin/v1/long-monitor/overview')
    await page.getByRole('button',{name:'重新加载监控总览'}).click()
    expect((await reloaded).status()).toBe(200)
    await expect(page.getByText('长效监控总览读取失败，统计值未知，不能据此认定没有异常代理、开放告警或失败通知。')).toHaveCount(0)
    await expect(page.locator('.cards .el-card strong').first()).toHaveText(/^\d+$/)
  })

  test('A2 release task detail and inspection errors remain unknown until explicit retry', async ({ page }) => {
    const taskKey='CI-RELEASE-OP-900991'
    const queueApi='**/api/admin/v1/v12/service-expiry/queue?*'
    const taskApi='**/api/admin/v1/v12/delivery-actions/'+taskKey
    const planApi='**/api/admin/v1/v12/release-inspections/'+taskKey
    await page.route(queueApi,route=>route.fulfill({
      status:200,contentType:'application/json',
      body:JSON.stringify({code:'OK',data:[{
        serviceId:900991,serviceNo:'CI-RELEASE-SERVICE-900991',productType:'EXCLUSIVE_IP',
        businessStatus:'SUSPENDED',expiryPhase:'RELEASE_PENDING',nextRequiredAction:'RELEASE_NOT_VERIFIED',
        operationKey:taskKey,actionType:'RELEASE',taskStatus:'PENDING',taskStage:'WAITING_RECEIPT'
      }]})
    }))
    await page.route(taskApi,route=>route.fulfill({
      status:503,contentType:'application/json',
      body:JSON.stringify({code:'CI_TASK_DOWN',message:'synthetic task outage'})
    }))
    await page.goto(base+'/service-releases')
    await expect(page.getByText('CI-RELEASE-SERVICE-900991')).toBeVisible()
    await page.getByRole('button',{name:'任务',exact:true}).click()
    await expect(page.getByText('任务详情读取失败或任务身份不匹配，不能据此认定已停服、已释放或不存在异常。')).toBeVisible()
    await expect(page.getByText('业务任务编号')).toHaveCount(0)
    await page.unroute(taskApi)
    await page.route(taskApi,route=>route.fulfill({
      status:200,contentType:'application/json',
      body:JSON.stringify({code:'OK',data:{
        operationKey:taskKey,serviceId:900991,action:'RELEASE',taskStatus:'PENDING',stage:'WAITING_RECEIPT'
      }})
    }))
    await page.getByRole('button',{name:'重新读取任务详情'}).click()
    await expect(page.getByText(taskKey,{exact:true})).toBeVisible()
    await page.route(planApi,route=>route.fulfill({
      status:503,contentType:'application/json',
      body:JSON.stringify({code:'CI_INSPECTION_DOWN',message:'synthetic precheck outage'})
    }))
    await page.getByRole('button',{name:'检查宽限及共享客户占用'}).click()
    await expect(page.getByText('释放预检读取失败，宽限、共享客户占用及产品回执条件均为未知，禁止据此判断可释放。')).toBeVisible()
    await expect(page.getByText('仅满足业务释放预检，不代表代理已回池')).toHaveCount(0)
    await page.unroute(planApi)
    await page.route(planApi,route=>route.fulfill({
      status:200,contentType:'application/json',
      body:JSON.stringify({code:'OK',data:{ready:false,claims:[{
        claimId:9001,ipv4:'198.51.100.43',releasePlan:'UNBIND_SERVICE_ONLY',otherProtectedClaims:2
      }]}})
    }))
    await page.getByRole('button',{name:'重新检查释放条件'}).click()
    await expect(page.getByText('未满足释放条件')).toBeVisible()
    await expect(page.getByText(/198\.51\.100\.43/)).toBeVisible()
  })

  test('A2 stale task detail response cannot replace next task or leak into reopened drawer', async ({ page }) => {
    const oldKey='CI-OLD-TASK-900991',newKey='CI-NEW-TASK-900992'
    await page.route('**/api/admin/v1/v12/service-expiry/queue?*',route=>route.fulfill({
      status:200,contentType:'application/json',
      body:JSON.stringify({code:'OK',data:[
        {serviceId:900991,serviceNo:'CI-OLD-SVC',operationKey:oldKey,actionType:'STOP',taskStatus:'PENDING'},
        {serviceId:900992,serviceNo:'CI-NEW-SVC',operationKey:newKey,actionType:'STOP',taskStatus:'PENDING'}
      ]})
    }))
    await page.route('**/api/admin/v1/v12/delivery-actions/'+oldKey,async route=>{
      await new Promise(resolve=>setTimeout(resolve,1300))
      await route.fulfill({status:200,contentType:'application/json',
        body:JSON.stringify({code:'OK',data:{operationKey:oldKey,action:'STOP',taskStatus:'CI-OLD-STATE'}})})
    })
    await page.route('**/api/admin/v1/v12/delivery-actions/'+newKey,route=>route.fulfill({
      status:200,contentType:'application/json',
      body:JSON.stringify({code:'OK',data:{operationKey:newKey,action:'STOP',taskStatus:'CI-NEW-STATE'}})
    }))
    await page.goto(base+'/service-releases')
    await expect(page.getByText('CI-OLD-SVC')).toBeVisible()
    await page.locator('.el-table__body tr').filter({hasText:'CI-OLD-SVC'}).getByRole('button',{name:'任务'}).click()
    await expect(page.getByText('实际操作任务与交付核对')).toBeVisible()
    await page.locator('.el-drawer__close-btn').click()
    // Element Plus keeps the drawer node mounted after the close transition.
    await expect(page.locator('.el-drawer')).toBeHidden()
    await page.locator('.el-table__body tr').filter({hasText:'CI-NEW-SVC'}).getByRole('button',{name:'任务'}).click()
    await expect(page.getByText(newKey,{exact:true})).toBeVisible()
    await expect(page.getByText('CI-NEW-STATE')).toBeVisible()
    await page.waitForTimeout(1600)
    await expect(page.getByText(oldKey,{exact:true})).toHaveCount(0)
    await expect(page.getByText('CI-OLD-STATE')).toHaveCount(0)
  })

  test('A5 notification ledger follows same-component alarm scope changes and clears old rows', async ({ page }) => {
    const seen:string[]=[]
    await page.route('**/api/admin/v1/long-monitor/notifications?*',route=>{
      const uri=new URL(route.request().url())
      const customer=uri.searchParams.get('customerId')||''
      const monitor=uri.searchParams.get('monitorId')||''
      seen.push(customer+':'+monitor)
      const suffix=customer&&monitor?customer+'-'+monitor:'ALL'
      return route.fulfill({status:200,contentType:'application/json',
        body:JSON.stringify({code:'OK',data:{total:1,items:[{
          id:1,created_at:'2026-10-16T06:00:00Z',customer_name:'CI-CUST-'+suffix,
          monitor_no:'CI-NOTIFY-'+suffix,send_status:'FAILED',channel:'DINGTALK',
          notification_type:'ALARM',monitor_id:Number(monitor)||1
        }]}})
      })
    })
    await page.goto(base+'/long-monitor/notifications?customerId=11&monitorId=101')
    await expect(page.getByText('CI-NOTIFY-11-101')).toBeVisible()
    await page.evaluate(async ()=>{
      const app=(document.querySelector('#app') as any).__vue_app__
      await app.config.globalProperties.$router.push('/long-monitor/notifications?customerId=22&monitorId=202')
    })
    await expect(page.getByText('CI-NOTIFY-22-202')).toBeVisible()
    await expect(page.getByText('CI-NOTIFY-11-101')).toHaveCount(0)
    await expect(page.getByText('客户 #22')).toBeVisible()
    await expect(page.getByText('监控 #202')).toBeVisible()
    await page.getByRole('button',{name:'查看全部通知'}).click()
    await expect(page.getByText('CI-NOTIFY-ALL')).toBeVisible()
    await expect(page.getByText('客户 #22')).toHaveCount(0)
    expect(seen).toEqual(expect.arrayContaining(['11:101','22:202',':']))
  })

  test('A5 alarm archive cancellation never sends close and does not claim network recovery', async ({ page }) => {
    const alarmId=900991
    await page.route('**/api/admin/v1/long-monitor/alarms/'+alarmId,route=>route.fulfill({
      status:200,contentType:'application/json',
      body:JSON.stringify({code:'OK',data:{
        id:alarmId,alarm_no:'CI-ALARM-CANCEL-900991',status:'OPEN',
        monitor_status:'ABNORMAL',customer_id:1,monitor_id:1,
        opened_at:'2026-10-16T06:00:00Z'
      }})
    }))
    const writes:string[]=[]
    page.on('request',req=>{
      if(req.method()==='POST'&&/\/long-monitor\/alarms\/900991\/close$/.test(new URL(req.url()).pathname))
        writes.push(req.url())
    })
    await page.goto(base+'/long-monitor/alarms/'+alarmId)
    await expect(page.getByRole('heading',{name:'CI-ALARM-CANCEL-900991'})).toBeVisible()
    await page.getByRole('button',{name:'确认归档'}).click()
    await expect(page.locator('.el-message-box')).toContainText('告警归档影响确认')
    await page.locator('.el-message-box__btns .el-button:not(.el-button--primary)').click()
    await expect(page.locator('.el-message-box')).toHaveCount(0)
    expect(writes).toHaveLength(0)
    await expect(page.getByText('ABNORMAL',{exact:true})).toBeVisible()
    await page.getByRole('button',{name:'确认归档'}).click()
    await page.locator('.el-message-box__btns .el-button--primary').click()
    await expect(page.locator('.el-message-box').last()).toContainText('关闭告警')
    await page.locator('.el-message-box').last().locator('.el-message-box__btns .el-button:not(.el-button--primary)').click()
    await expect(page.locator('.el-message-box')).toHaveCount(0)
    expect(writes).toHaveLength(0)
    await expect(page.getByRole('button',{name:'确认归档'})).toBeEnabled()
  })

  test('A3 pool ledger API failure never presents stale source pools as empty or valid', async ({ page }) => {
    expect(poolId).toBeTruthy()
    await page.goto(base+'/resource-pools')
    await expect(page.getByText('V12 Shared Pool')).toBeVisible()
    const url='**/api/admin/v1/v12/resource-pools?*'
    await page.route(url,route=>route.fulfill({
      status:503,contentType:'application/json',
      body:JSON.stringify({code:'CI_POOL_LEDGER_DOWN',message:'offline synthetic outage'})
    }))
    await page.getByRole('button',{name:'查询'}).click()
    await expect(page.getByText('资源池列表查询失败，不能据此认为没有资源池或来源线路。')).toBeVisible()
    await expect(page.getByText('V12 Shared Pool')).toHaveCount(0)
    await page.unroute(url)
    const retry=page.waitForResponse(r=>r.request().method()==='GET'&&
      new URL(r.url()).pathname==='/api/admin/v1/v12/resource-pools')
    await page.getByRole('button',{name:'重新加载资源池列表'}).click()
    expect((await retry).status()).toBe(200)
    await expect(page.getByText('V12 Shared Pool')).toBeVisible()
  })

  test('A3 pool master failure displays unknown metrics and disables line mutations until retry', async ({ page }) => {
    expect(poolId).toBeTruthy()
    const url='**/api/admin/v1/v12/resource-pools/'+poolId
    await page.goto(base+'/resource-pools/'+poolId)
    await expect(page.getByRole('heading',{name:'V12 Shared Pool'})).toBeVisible()
    await page.route(url,route=>route.fulfill({
      status:503,contentType:'application/json',
      body:JSON.stringify({code:'CI_POOL_MASTER_DOWN',message:'offline synthetic outage'})
    }))
    await page.getByRole('button',{name:'刷新'}).click()
    await expect(page.getByText('资源池详情读取失败，来源、线路数和服务占用均未知，不能依据旧池资料操作。')).toBeVisible()
    await expect(page.locator('.metric-grid strong').first()).toHaveText('—')
    await expect(page.getByRole('heading',{name:'V12 Shared Pool'})).toHaveCount(0)
    const add=page.getByRole('button',{name:'添加线路'})
    await expect(add).toBeDisabled()
    await page.unroute(url)
    await page.getByRole('button',{name:'重新加载资源池详情'}).click()
    await expect(page.getByRole('heading',{name:'V12 Shared Pool'})).toBeVisible()
    await expect(page.getByText('资源池详情读取失败，来源、线路数和服务占用均未知，不能依据旧池资料操作。')).toHaveCount(0)
    await expect(add).toBeEnabled()
  })

  test('A3 pool member list outage explicitly blocks source member removal and can retry', async ({ page }) => {
    expect(poolId).toBeTruthy()
    await page.goto(base+'/resource-pools/'+poolId)
    await expect(page.getByRole('heading',{name:'V12 Shared Pool'})).toBeVisible()
    const url='**/api/admin/v1/v12/resource-pools/'+poolId+'/lines?*'
    await page.route(url,route=>route.fulfill({
      status:503,contentType:'application/json',
      body:JSON.stringify({code:'CI_POOL_MEMBER_DOWN',message:'offline synthetic outage'})
    }))
    await page.getByRole('button',{name:'查询'}).click()
    await expect(page.getByText('资源池线路成员读取失败，不能据此认为没有可用线路或在用成员。')).toBeVisible()
    await expect(page.locator('.el-table__body tr').filter({hasText:'CI-'})).toHaveCount(0)
    await page.unroute(url)
    await page.getByRole('button',{name:'重新加载线路成员'}).click()
    await expect(page.getByText('资源池线路成员读取失败，不能据此认为没有可用线路或在用成员。')).toHaveCount(0)
    await expect(page.locator('.el-table__body tr').first()).toBeVisible()
  })

  test('A3 SPA pool identity change ignores delayed old pool response', async ({ page }) => {
    expect(poolId).toBeTruthy()
    await page.goto(base+'/resource-pools/'+poolId)
    await expect(page.getByRole('heading',{name:'V12 Shared Pool'})).toBeVisible()
    const oldId=900992,newId=900993
    await page.route('**/api/admin/v1/v12/resource-pools/'+oldId,async route=>{
      await new Promise(r=>setTimeout(r,1000))
      await route.fulfill({status:200,contentType:'application/json',
        body:JSON.stringify({code:'OK',data:{
          id:oldId,pool_code:'CI-OLD-POOL',pool_name:'CI OLD Pool',ros_count:99,line_count:99
        }})})
    })
    await page.route('**/api/admin/v1/v12/resource-pools/'+newId,route=>route.fulfill({
      status:200,contentType:'application/json',
      body:JSON.stringify({code:'OK',data:{
        id:newId,pool_code:'CI-NEW-POOL',pool_name:'CI NEW Pool',ros_count:1,line_count:2
      }})
    }))
    await page.evaluate(async()=>{
      const router=(document.querySelector('#app') as any).__vue_app__.config.globalProperties.$router
      await router.push('/resource-pools/900992')
    })
    await page.evaluate(async()=>{
      const router=(document.querySelector('#app') as any).__vue_app__.config.globalProperties.$router
      await router.push('/resource-pools/900993')
    })
    await expect(page.getByRole('heading',{name:'CI NEW Pool'})).toBeVisible()
    await expect(page.getByText('CI-NEW-POOL',{exact:true})).toBeVisible()
    await page.waitForTimeout(1300)
    await expect(page.getByRole('heading',{name:'CI OLD Pool'})).toHaveCount(0)
    await expect(page.getByText('CI-OLD-POOL',{exact:true})).toHaveCount(0)
    await expect(page.locator('.metric-grid strong').first()).toHaveText('1')
  })

  test('A service credential reset confirmation cannot retarget to a different service via SPA route', async ({ page }) => {
    expect(serviceId).toBeTruthy()
    await page.goto(base+'/services/'+serviceId)
    await page.getByRole('tab',{name:'服务凭证'}).click()
    const reset=page.getByRole('button',{name:'重置凭证'})
    await expect(reset).toBeEnabled()
    const posted:string[]=[]
    page.on('request',req=>{
      if(req.method()==='POST'&&/\/services\/\d+\/credentials\/reset$/.test(new URL(req.url()).pathname))
        posted.push(req.url())
    })
    await reset.click()
    const modal=page.locator('.el-message-box').last()
    await expect(modal).toContainText('重置服务凭证')
    await modal.locator('input').fill('CI cross-service credential safety')
    await page.evaluate(async id=>{
      await (document.querySelector('#app') as any).__vue_app__.config.globalProperties.$router.push('/services/'+id)
    },Number(serviceId)+900000)
    await modal.locator('.el-message-box__btns .el-button--primary').click()
    expect(posted).toHaveLength(0)
    await expect(page.getByText('确认期间服务已变化，凭证重置未提交，请重新核对')).toBeVisible()
    await expect(page.getByText('一次性凭证',{exact:true})).toHaveCount(0)
  })

  test('A whitelist add confirmation cannot write onto the newly navigated service', async ({ page }) => {
    expect(serviceId).toBeTruthy()
    await page.goto(base+'/services/'+serviceId)
    await page.getByRole('tab',{name:'白名单'}).click()
    const add=page.getByRole('button',{name:'新增白名单'})
    await expect(add).toBeEnabled()
    const posted:string[]=[]
    page.on('request',req=>{
      if(req.method()==='POST'&&/\/services\/\d+\/whitelist$/.test(new URL(req.url()).pathname))
        posted.push(req.url())
    })
    await add.click()
    const modal=page.locator('.el-message-box').last()
    await expect(modal).toContainText('新增白名单')
    await modal.locator('input').fill('203.0.113.77')
    await page.evaluate(async id=>{
      await (document.querySelector('#app') as any).__vue_app__.config.globalProperties.$router.push('/services/'+id)
    },Number(serviceId)+900000)
    await modal.locator('.el-message-box__btns .el-button--primary').click()
    expect(posted).toHaveLength(0)
    await expect(page.getByText('确认期间服务已变化，白名单未提交，请重新核对')).toBeVisible()
  })

  test('A whitelist removal requires impact confirmation and cancel cannot write DELETE', async ({ page }) => {
    expect(serviceId).toBeTruthy()
    const getApi='**/api/admin/v1/services/'+serviceId
    await page.route(getApi,async route=>{
      const response=await route.fetch()
      const json=await response.json()
      json.data.whitelist=[{id:900991,ip_address:'198.51.100.99',status:'ACTIVE',remark:'CI only'}]
      await route.fulfill({response,json})
    })
    await page.goto(base+'/services/'+serviceId)
    await page.getByRole('tab',{name:'白名单'}).click()
    await expect(page.getByText('198.51.100.99',{exact:true})).toBeVisible()
    const deletes:string[]=[]
    page.on('request',req=>{
      if(req.method()==='DELETE'&&/\/services\/\d+\/whitelist\/\d+$/.test(new URL(req.url()).pathname))
        deletes.push(req.url())
    })
    await page.getByRole('button',{name:'停用'}).click()
    const modal=page.locator('.el-message-box').last()
    await expect(modal).toContainText('停用白名单')
    await expect(modal).toContainText('198.51.100.99')
    await modal.getByRole('button',{name:'返回修改'}).click()
    expect(deletes).toHaveLength(0)
    await expect(page.getByText('198.51.100.99',{exact:true})).toBeVisible()
  })

  test('A manual failover precheck failure cannot show stale network impact or allow execution', async ({ page }) => {
    expect(serviceId).toBeTruthy()
    const serviceUrl='**/api/admin/v1/services/'+serviceId
    await page.route(serviceUrl,async route=>{
      const response=await route.fetch()
      const json=await response.json()
      json.data.backupCandidates=[{
        id:900991,resource_code:'CI-PREVIEW-ONLY-LINE',expected_ipv4:'203.0.113.88',
        candidate_status:'PENDING',health_status:'UNKNOWN'
      }]
      await route.fulfill({response,json})
    })
    const previewUrl='**/api/admin/v1/v12/services/'+serviceId+'/failover-preview'
    await page.route(previewUrl,route=>route.fulfill({
      status:503,contentType:'application/json',
      body:JSON.stringify({code:'CI_PREVIEW_DOWN',message:'offline preview refused'})
    }))
    await page.goto(base+'/services/'+serviceId)
    await page.getByRole('tab',{name:'人工备用候选'}).click()
    await expect(page.getByText('CI-PREVIEW-ONLY-LINE')).toBeVisible()
    await page.getByRole('button',{name:'预检影响与冲突'}).click()
    await expect(page.getByText('人工切换预检失败，不能使用之前的候选冲突或网络影响结论。')).toBeVisible()
    await expect(page.getByText('人工切换影响预检（禁止直接执行）')).toHaveCount(0)
    await expect(page.getByRole('button',{name:/真实切换不可用/})).toHaveCount(0)
  })

  test('A1 order cancellation approval cannot retarget after SPA navigation', async ({ page }) => {
    expect(unpaidOrderId).toBeTruthy()
    expect(multiOrderId).toBeTruthy()
    const writes:string[]=[]
    page.on('request',r=>{
      if(r.method()==='POST'&&/\/api\/admin\/v1\/orders\/\d+\/cancel$/.test(new URL(r.url()).pathname))
        writes.push(r.url())
    })
    await page.goto(base+'/orders/'+unpaidOrderId)
    await expect(page.getByRole('button',{name:'取消订单'})).toBeEnabled()
    await page.getByRole('button',{name:'取消订单'}).click()
    const modal=page.locator('.el-message-box').last()
    await expect(modal).toContainText('取消订单')
    await modal.locator('input').fill('CI A1 cross-order cancel should be rejected')
    await page.evaluate(async next=>{
      await (document.querySelector('#app') as any).__vue_app__.config.globalProperties.$router.push('/orders/'+next)
    },multiOrderId)
    await expect(page).toHaveURL(base+'/orders/'+multiOrderId)
    await modal.locator('.el-message-box__btns .el-button--primary').click()
    expect(writes).toHaveLength(0)
    await expect(page.getByText('确认期间订单、状态或权限已变化；没有提交订单操作，请重新核对')).toBeVisible()
  })

  test('A1 payment reference approval cannot retarget to another order', async ({ page }) => {
    expect(unpaidOrderId).toBeTruthy()
    expect(multiOrderId).toBeTruthy()
    const writes:string[]=[]
    page.on('request',r=>{
      if(r.method()==='POST'&&/\/api\/admin\/v1\/orders\/\d+\/payment-confirmation$/.test(new URL(r.url()).pathname))
        writes.push(r.url())
    })
    await page.goto(base+'/orders/'+unpaidOrderId)
    await page.getByRole('button',{name:'核实客户付款'}).click()
    const modal=page.locator('.el-message-box').last()
    await expect(modal).toContainText('核实客户付款')
    await modal.locator('input').fill('CI-A1-SYNTHETIC-PAYMENT-DO-NOT-SUBMIT')
    await page.evaluate(async next=>{
      await (document.querySelector('#app') as any).__vue_app__.config.globalProperties.$router.push('/orders/'+next)
    },multiOrderId)
    await modal.locator('.el-message-box__btns .el-button--primary').click()
    await expect(page.getByText('核款期间订单或付款状态已变化，未提交付款确认')).toBeVisible()
    expect(writes).toHaveLength(0)
    await expect(page.getByText('核实收款',{exact:true})).toHaveCount(0)
  })

  test('A1 payment second approval step cannot use an obsolete order identity', async ({ page }) => {
    expect(unpaidOrderId).toBeTruthy()
    expect(multiOrderId).toBeTruthy()
    const writes:string[]=[]
    page.on('request',r=>{
      if(r.method()==='POST'&&/\/api\/admin\/v1\/orders\/\d+\/payment-confirmation$/.test(new URL(r.url()).pathname))
        writes.push(r.url())
    })
    await page.goto(base+'/orders/'+unpaidOrderId)
    await page.getByRole('button',{name:'核实客户付款'}).click()
    const first=page.locator('.el-message-box').last()
    await first.locator('input').fill('CI-A1-PAYMENT-PROOF-NO-WRITE')
    await first.locator('.el-message-box__btns .el-button--primary').click()
    const second=page.locator('.el-message-box').last()
    await expect(second).toContainText('核实收款')
    await second.locator('input').fill('CI checked external payment reference')
    await page.evaluate(async next=>{
      await (document.querySelector('#app') as any).__vue_app__.config.globalProperties.$router.push('/orders/'+next)
    },multiOrderId)
    await second.locator('.el-message-box__btns .el-button--primary').click()
    await expect(page.getByText('核款期间订单或付款状态已变化，未提交付款确认')).toBeVisible()
    expect(writes).toHaveLength(0)
  })

  test('A1 order confirmation review cannot confirm another order if page switches', async ({ page }) => {
    expect(unpaidOrderId).toBeTruthy()
    expect(multiOrderId).toBeTruthy()
    // Display a synthetic DRAFT snapshot only to test the UI confirmation guard.
    // Never send a write to PostgreSQL from this fixture.
    const url='**/api/admin/v1/orders/'+unpaidOrderId
    await page.route(url,async route=>{
      const response=await route.fetch()
      const json=await response.json()
      json.data.status='DRAFT'
      await route.fulfill({response,json})
    })
    const writes:string[]=[]
    page.on('request',r=>{
      if(r.method()==='POST'&&/\/api\/admin\/v1\/orders\/\d+\/confirm$/.test(new URL(r.url()).pathname))
        writes.push(r.url())
    })
    await page.goto(base+'/orders/'+unpaidOrderId)
    await expect(page.getByRole('button',{name:'确认订单'})).toBeEnabled()
    await page.getByRole('button',{name:'确认订单'}).click()
    const modal=page.locator('.el-message-box').last()
    await expect(modal).toContainText('确认订单')
    await modal.locator('input').fill('CI A1 ensure original order only')
    await page.evaluate(async next=>{
      await (document.querySelector('#app') as any).__vue_app__.config.globalProperties.$router.push('/orders/'+next)
    },multiOrderId)
    await modal.locator('.el-message-box__btns .el-button--primary').click()
    await expect(page.getByText('确认期间订单、状态或权限已变化；没有提交订单操作，请重新核对')).toBeVisible()
    expect(writes).toHaveLength(0)
  })

  test('A1 persisted order-to-service-to-binding chain uses actual PostgreSQL relations',async({page})=>{
    expect(multiOrderId).toBeTruthy()
    const relations=page.waitForResponse(r=>r.request().method()==='GET'&&
      new URL(r.url()).pathname==='/api/admin/v1/relations/orders/'+multiOrderId+'/services')
    await page.goto(base+'/orders/'+multiOrderId)
    const reply=await relations
    expect(reply.status()).toBe(200)
    const linked=(await reply.json()).data
    expect(linked.items).toHaveLength(3)
    expect(new Set(linked.items.map((x:any)=>Number(x.id))).size).toBe(3)
    await page.getByRole('tab',{name:/实际关联服务 \(3\)/}).click()
    const link=page.locator('.el-tab-pane:visible').getByRole('button',{name:'服务详情'}).first()
    const sid=Number(linked.items[0].id)
    const serviceReply=page.waitForResponse(r=>r.request().method()==='GET'&&
      new URL(r.url()).pathname==='/api/admin/v1/services/'+sid)
    const bindReply=page.waitForResponse(r=>r.request().method()==='GET'&&
      new URL(r.url()).pathname==='/api/admin/v1/relations/services/'+sid+'/bindings')
    await link.click()
    await expect(page).toHaveURL(base+'/services/'+sid)
    const rawService=await serviceReply
    expect(rawService.status()).toBe(200)
    const persisted=(await rawService.json()).data
    expect(Number(persisted.order_id)).toBe(Number(multiOrderId))
    expect(Number(persisted.id)).toBe(sid)
    expect(Array.isArray(persisted.actionTasks)).toBe(true)
    const rawBindings=await bindReply
    expect(rawBindings.status()).toBe(200)
    const bindings=(await rawBindings.json()).data
    expect(Array.isArray(bindings.items)).toBe(true)
    await page.getByRole('tab',{name:'资源/交付绑定'}).click()
    await expect(page.getByText('管理面绑定ACTIVE不代表网络执行已经验证。',{exact:false})).toBeVisible()
    await page.getByRole('tab',{name:'执行任务'}).click()
    await expect(page.getByRole('columnheader',{name:'操作ID'})).toBeVisible()
  })

  test('A1 provision wizard follows same-component order changes and never reuses paid snapshot',async({page})=>{
    expect(multiOrderId).toBeTruthy()
    expect(unpaidOrderId).toBeTruthy()
    const paid='**/api/admin/v1/orders/'+multiOrderId+'/provision-preview'
    const unpaid='**/api/admin/v1/orders/'+unpaidOrderId+'/provision-preview'
    await page.route(paid,route=>route.fulfill({status:200,contentType:'application/json',
      body:JSON.stringify({code:'OK',data:{
        order:{id:Number(multiOrderId),order_no:'CI-PAID-PREVIEW',status:'PENDING_PROVISION'},
        paymentReady:true,alreadyProvisioned:false,
        items:[{id:990011,service_count:1,product_name:'CI Paid product',product_type:'SHORT_IP'}],
        availableReverseProxies:[]
      }})}))
    await page.route(unpaid,route=>route.fulfill({status:200,contentType:'application/json',
      body:JSON.stringify({code:'OK',data:{
        order:{id:Number(unpaidOrderId),order_no:'CI-UNPAID-PREVIEW',status:'PENDING_PROVISION'},
        paymentReady:false,alreadyProvisioned:false,
        items:[{id:990012,service_count:1,product_name:'CI Unpaid product',product_type:'SHORT_IP'}],
        availableReverseProxies:[]
      }})}))
    await page.goto(base+'/service-provision?orderId='+multiOrderId)
    await expect(page.getByText('CI-PAID-PREVIEW')).toBeVisible()
    await expect(page.getByRole('button',{name:'下一步'})).toBeEnabled()
    await page.evaluate(async id=>{
      await (document.querySelector('#app') as any).__vue_app__.config.globalProperties.$router.push('/service-provision?orderId='+id)
    },unpaidOrderId)
    await expect(page.getByText('CI-UNPAID-PREVIEW')).toBeVisible()
    await expect(page.getByRole('button',{name:'下一步'})).toBeDisabled()
    await expect(page.getByText('CI-PAID-PREVIEW')).toHaveCount(0)
    await expect(page.getByText('CI Paid product')).toHaveCount(0)
  })

  test('A1 delayed old provision preview cannot replace a newly selected order',async({page})=>{
    expect(multiOrderId).toBeTruthy()
    expect(unpaidOrderId).toBeTruthy()
    const paid='**/api/admin/v1/orders/'+multiOrderId+'/provision-preview'
    const unpaid='**/api/admin/v1/orders/'+unpaidOrderId+'/provision-preview'
    await page.route(paid,async route=>{
      await new Promise(resolve=>setTimeout(resolve,900))
      await route.fulfill({status:200,contentType:'application/json',
        body:JSON.stringify({code:'OK',data:{
          order:{id:Number(multiOrderId),order_no:'CI-DELAYED-OLD'},
          paymentReady:true,alreadyProvisioned:false,items:[],availableReverseProxies:[]
        }})})
    })
    await page.route(unpaid,route=>route.fulfill({status:200,contentType:'application/json',
      body:JSON.stringify({code:'OK',data:{
        order:{id:Number(unpaidOrderId),order_no:'CI-FAST-NEW'},
        paymentReady:false,alreadyProvisioned:false,items:[],availableReverseProxies:[]
      }})}))
    await page.goto(base+'/service-provision?orderId='+multiOrderId)
    await page.evaluate(async id=>{
      await (document.querySelector('#app') as any).__vue_app__.config.globalProperties.$router.push('/service-provision?orderId='+id)
    },unpaidOrderId)
    await expect(page.getByText('CI-FAST-NEW')).toBeVisible()
    await page.waitForTimeout(1100)
    await expect(page.getByText('CI-DELAYED-OLD')).toHaveCount(0)
    await expect(page.getByRole('button',{name:'下一步'})).toBeDisabled()
  })

  test('A1 provision return separates synthetic service receipt from persisted relation retry',async({page})=>{
    expect(multiOrderId).toBeTruthy()
    const orderId=Number(multiOrderId)
    await page.route('**/api/admin/v1/orders/'+multiOrderId+'/provision-preview',route=>route.fulfill({
      status:200,contentType:'application/json',
      body:JSON.stringify({code:'OK',data:{
        order:{id:orderId,order_no:'CI-A1-OFFLINE-PREVIEW',status:'PENDING_PROVISION'},
        paymentReady:true,alreadyProvisioned:false,
        items:[{id:990011,service_count:1,product_type:'SHORT_IP',product_name:'CI A1'},
        ],availableReverseProxies:[]
      }})
    }))
    const postUrl='**/api/admin/v1/orders/'+multiOrderId+'/provision'
    const writes:string[]=[]
    await page.route(postUrl,route=>{
      writes.push(route.request().url())
      return route.fulfill({status:200,contentType:'application/json',
        body:JSON.stringify({code:'OK',data:{services:[{
          serviceId:990081,serviceNo:'CI-MOCK-INSTANCE',deliveryStage:'MOCK_ONLY',
          status:'ACTIVE',deliveryMode:'SELF_OWNED'
        }]}})
      })
    })
    const rel='**/api/admin/v1/relations/orders/'+multiOrderId+'/services'
    await page.route(rel,route=>route.fulfill({status:503,contentType:'application/json',
      body:JSON.stringify({code:'CI_RELATION_DOWN',message:'forced read outage'})}))
    await page.goto(base+'/service-provision?orderId='+multiOrderId)
    await expect(page.getByText('CI-A1-OFFLINE-PREVIEW')).toBeVisible()
    for(let i=0;i<3;i++)await page.getByRole('button',{name:'下一步'}).click()
    await page.getByPlaceholder('请填写本次开通原因或业务说明').fill('CI simulated management receipt only')
    await page.getByRole('button',{name:'确认开通'}).click()
    await expect(page.getByText('数据库服务关联回查（管理面持久记录）')).toBeVisible()
    await expect(page.getByText('订单关联服务回查失败或权限不足，不能据此认为没有生成服务。')).toBeVisible()
    expect(writes).toHaveLength(1)
    await page.unroute(rel)
    await page.route(rel,route=>route.fulfill({status:200,contentType:'application/json',
      body:JSON.stringify({code:'OK',data:{items:[{
        id:990081,service_no:'CI-MOCK-INSTANCE',relation_type:'PROVISIONED_INSTANCE',
        status:'ACTIVE',delivery_stage:'MOCK_ONLY'
      }],total:1,truncated:false}})
    }))
    await page.getByRole('button',{name:'重新回查持久服务关系'}).click()
    await expect(page.getByText('CI-MOCK-INSTANCE',{exact:true}).last()).toBeVisible()
    await expect(page.getByText('订单关联服务回查失败或权限不足，不能据此认为没有生成服务。')).toHaveCount(0)
    await expect(page.getByText('管理面记录一致不代表外部代理可连接或数据面已交付。',{exact:false})).toBeVisible()
  })

  test('A1 audited payment and provisioning resolve back to exact PostgreSQL order with redacted fields',async({page})=>{
    expect(multiOrderId).toBeTruthy()
    const id=Number(multiOrderId)
    await page.goto(base+'/logs')
    await page.getByRole('tab',{name:/11 操作审计/}).click()
    await page.getByPlaceholder('客户、服务号、对象编号、事件/请求标识').fill(String(id))
    const auditResponse=page.waitForResponse(r=>r.request().method()==='GET'&&
      new URL(r.url()).pathname==='/api/admin/v1/runtime-logs/v12/operation'&&
      new URL(r.url()).searchParams.get('keyword')===String(id))
    await page.getByRole('button',{name:'查询'}).click()
    const response=await auditResponse
    expect(response.status()).toBe(200)
    const data=(await response.json()).data
    expect(data.sourceScope).toBe('REDACTED_OPERATION_AUDIT_NO_RAW_JSON')
    const payment=data.items.find((x:any)=>x.eventType==='PAYMENT_RECEIPT_CONFIRMED'&&x.objectType==='BIZ_ORDER')
    const provision=data.items.find((x:any)=>x.eventType==='PROVISION_V12'&&x.objectType==='ORDER')
    for(const entry of [payment,provision]){
      expect(entry).toBeTruthy()
      expect(entry.objectCode).toBe(String(id))
      expect(entry.relatedPath).toBe('/orders/'+id)
      expect(entry.result).toBe('SUCCESS')
      expect(entry).not.toHaveProperty('reason')
      expect(entry).not.toHaveProperty('before_data')
      expect(entry).not.toHaveProperty('after_data')
      expect(entry).not.toHaveProperty('payment_reference')
    }
    const rows=page.locator('.el-table__body tr')
    const paidRow=rows.filter({hasText:'PAYMENT_RECEIPT_CONFIRMED'})
    await expect(paidRow).toBeVisible()
    await expect(rows.filter({hasText:'PROVISION_V12'})).toBeVisible()
    await paidRow.getByRole('button',{name:'查看'}).click()
    await expect(page).toHaveURL(base+'/orders/'+id)
    await expect(page.getByText('订单主记录')).toBeVisible()
    await page.getByRole('tab',{name:/实际关联服务 \(3\)/}).click()
    await expect(page.locator('.el-tab-pane:visible .el-table__body tr')).toHaveCount(3)
  })

  test('A2 backend business FAILED release is never presented as completed service termination',async({page})=>{
    expect(serviceId).toBeTruthy()
    const sid=Number(serviceId)
    await page.route('**/api/admin/v1/services?*',route=>route.fulfill({
      status:200,contentType:'application/json',body:JSON.stringify({
        code:'OK',data:{items:[{id:sid,service_no:'CI-A2-FAILED-LIFECYCLE',customer_name:'CI Customer',
          customer_id:Number(customerId),product_name:'CI Long Product',product_type:'LONG_IP',
          status:'ACTIVE',delivery_stage:'MOCK_ONLY',expire_at:'2026-10-20T00:00:00Z'}],total:1}
      })
    }))
    let submitted=0
    await page.route('**/api/admin/v1/services/'+sid+'/terminate',route=>{
      submitted++
      return route.fulfill({status:200,contentType:'application/json',
        body:JSON.stringify({code:'OK',data:{status:'FAILED',message:'EXTERNAL_RELEASE_NOT_VERIFIED'}})})
    })
    await page.goto(base+'/service-changes?serviceId='+sid+'&action=terminate')
    await expect(page.getByText('CI-A2-FAILED-LIFECYCLE').first()).toBeVisible()
    await page.getByPlaceholder('填写可审计的业务原因').fill('CI A2 mock product release not verified')
    await page.getByRole('button',{name:'预览并确认终止'}).click()
    const box=page.locator('.el-message-box').last()
    await expect(box).toContainText('核对服务与影响后确认')
    await box.locator('.el-message-box__btns .el-button--primary').click()
    await expect(page.getByText('后端返回未完成状态，不能认定已停服、恢复或释放')).toBeVisible()
    await expect(page.getByText('EXTERNAL_RELEASE_NOT_VERIFIED')).toBeVisible()
    await expect(page.getByText('操作未确认成功',{exact:true})).toBeVisible()
    expect(submitted).toBe(1)
    await expect(page.getByText(/管理面结果已返回（并非真实交付验收）/)).toHaveCount(0)
  })

  test('A2 lifecycle result cannot leak into a different selected service while backend response is delayed',async({page})=>{
    expect(serviceId).toBeTruthy()
    const sid=Number(serviceId),other=sid+900000
    await page.route('**/api/admin/v1/services?*',route=>route.fulfill({
      status:200,contentType:'application/json',body:JSON.stringify({
        code:'OK',data:{items:[
          {id:sid,service_no:'CI-A2-OLD-SERVICE',customer_name:'CI Customer',customer_id:Number(customerId),
            product_name:'CI Long',status:'ACTIVE',delivery_stage:'MOCK_ONLY'},
          {id:other,service_no:'CI-A2-NEW-SERVICE',customer_name:'CI Customer',customer_id:Number(customerId),
            product_name:'CI Long',status:'ACTIVE',delivery_stage:'MOCK_ONLY'}
        ],total:2}
      })
    }))
    await page.route('**/api/admin/v1/services/'+sid+'/terminate',async route=>{
      await new Promise(resolve=>setTimeout(resolve,900))
      await route.fulfill({status:200,contentType:'application/json',
        body:JSON.stringify({code:'OK',data:{status:'FAILED',message:'OLD_SERVICE_NOT_RELEASED'}})})
    })
    await page.goto(base+'/service-changes?serviceId='+sid+'&action=terminate')
    await page.getByPlaceholder('填写可审计的业务原因').fill('CI A2 delayed old service')
    await page.getByRole('button',{name:'预览并确认终止'}).click()
    await page.locator('.el-message-box').last().locator('.el-message-box__btns .el-button--primary').click()
    await page.locator('.el-select').first().click()
    await page.getByRole('option',{name:/CI-A2-NEW-SERVICE/}).click()
    await expect(page.getByText('CI-A2-NEW-SERVICE').first()).toBeVisible()
    await page.waitForTimeout(1200)
    await expect(page.getByText('OLD_SERVICE_NOT_RELEASED')).toHaveCount(0)
    await expect(page.getByText('操作未确认成功',{exact:true})).toHaveCount(0)
    await expect(page.getByText(/管理面结果已返回（并非真实交付验收）/)).toHaveCount(0)
  })

  test('A2 expiry scan confirmation is serialized; cancellation has no database write',async({page})=>{
    await page.goto(base+'/service-releases')
    const sent:string[]=[]
    page.on('request',r=>{
      if(r.method()==='POST'&&new URL(r.url()).pathname.includes('/service-expiry/reconcile'))sent.push(r.url())
    })
    const trigger=page.getByRole('button',{name:'扫描到期任务'})
    await trigger.click()
    const box=page.locator('.el-message-box').last()
    await expect(box).toContainText('受控扫描到期任务')
    await expect(trigger).toBeDisabled()
    await box.locator('.el-message-box__btns .el-button').first().click()
    expect(sent).toHaveLength(0)
    await expect(trigger).toBeEnabled()
    await expect(page.getByText('任务扫描已完成',{exact:false})).toHaveCount(0)
  })
  test('A UI-06 resource overview outage removes stale metrics and legacy sync-task navigation',async({page})=>{
    const api='**/api/admin/v1/resources/overview'
    let unavailable=false
    await page.route(api,route=>route.fulfill({
      status:unavailable?503:200,contentType:'application/json',
      body:unavailable?JSON.stringify({code:'CI_RESOURCE_OVERVIEW_DOWN',message:'synthetic outage'}):
        JSON.stringify({code:'OK',data:{centos_total:1737,centos_online:2,ros_total:2,ros_online:2,
          line_total:3,line_online:3,active_pool_total:1,supplier_total:1,
          exclusive_active_total:1,sync_failed_24h:0,recentSyncTasks:[]}})
    }))
    await page.goto(base+'/resources')
    await expect(page.getByText('1737',{exact:true})).toBeVisible()
    await expect(page.getByRole('button',{name:'查看全部'})).toHaveCount(0)
    unavailable=true
    await page.getByRole('button',{name:'刷新'}).click()
    await expect(page.getByText('资源概览读取失败，当前资源数量与同步状态未知，请重新加载；不能沿用上次统计。')).toBeVisible()
    await expect(page.getByText('1737',{exact:true})).toHaveCount(0)
    await expect(page.getByText('—',{exact:true}).first()).toBeVisible()
    await page.unroute(api)
    const response=page.waitForResponse(r=>r.request().method()==='GET'&&
      new URL(r.url()).pathname==='/api/admin/v1/resources/overview')
    await page.getByRole('button',{name:'重新加载资源概览'}).click()
    expect((await response).status()).toBe(200)
    await expect(page.getByText('资源概览读取失败，当前资源数量与同步状态未知，请重新加载；不能沿用上次统计。')).toHaveCount(0)
  })

  test('A UI-05 customer account ledger outage cannot expose stale accounts and retries actual backend',async({page})=>{
    const api='**/api/admin/v1/customer-accounts?*'
    let unavailable=false
    await page.route(api,route=>route.fulfill({
      status:unavailable?503:200,contentType:'application/json',
      body:unavailable?JSON.stringify({code:'CI_ACCOUNT_LEDGER_DOWN',message:'synthetic outage'}):
        JSON.stringify({code:'OK',data:{items:[{id:9100001,username:'CI-ACCOUNT-SAFE',
          customer_id:Number(customerId),customer_name:'V12 CI Customer',status:'ACTIVE',account_type_code:'DEFAULT'}],total:1}})
    }))
    await page.goto(base+'/customer-accounts')
    await expect(page.getByText('CI-ACCOUNT-SAFE',{exact:true})).toBeVisible()
    unavailable=true
    await page.getByRole('button',{name:'查询'}).click()
    await expect(page.getByText('账号列表读取失败，当前账号状态未知，禁止依据旧记录执行账号操作。')).toBeVisible()
    await expect(page.getByText('CI-ACCOUNT-SAFE',{exact:true})).toHaveCount(0)
    await expect(page.getByRole('button',{name:'重置密码'})).toHaveCount(0)
    await page.unroute(api)
    const response=page.waitForResponse(r=>r.request().method()==='GET'&&
      new URL(r.url()).pathname==='/api/admin/v1/customer-accounts')
    await page.getByRole('button',{name:'重新加载账号列表'}).click()
    expect((await response).status()).toBe(200)
    await expect(page.getByText('账号列表读取失败，当前账号状态未知，禁止依据旧记录执行账号操作。')).toHaveCount(0)
  })

  test('A UI-05 failed customer choices block new account creation without hiding the account ledger',async({page})=>{
    await page.route('**/api/admin/v1/customers/options',route=>route.fulfill({
      status:503,contentType:'application/json',body:JSON.stringify({code:'CI_ACCOUNT_OPTIONS_DOWN',message:'synthetic outage'})
    }))
    await page.route('**/api/admin/v1/customer-accounts?*',route=>route.fulfill({
      status:200,contentType:'application/json',
      body:JSON.stringify({code:'OK',data:{items:[{id:9100002,username:'CI-ACCOUNT-EXISTING',
        customer_id:Number(customerId),customer_name:'V12 CI Customer',status:'ACTIVE',account_type_code:'DEFAULT'}],total:1}})
    }))
    await page.goto(base+'/customer-accounts')
    await expect(page.getByText('CI-ACCOUNT-EXISTING',{exact:true})).toBeVisible()
    await expect(page.getByText('客户或账号类型选项读取失败，现有账号列表仍可查询，但创建账号必须等选项恢复。')).toBeVisible()
    await expect(page.getByRole('button',{name:'创建账号'})).toBeDisabled()
    await page.unroute('**/api/admin/v1/customers/options')
    const response=page.waitForResponse(r=>r.request().method()==='GET'&&
      new URL(r.url()).pathname==='/api/admin/v1/customers/options')
    await page.getByRole('button',{name:'重新加载创建选项'}).click()
    expect((await response).status()).toBe(200)
    await expect(page.getByRole('button',{name:'创建账号'})).toBeEnabled()
  })

  test('A UI-05 customer account freeze confirmation cancellation has no management write',async({page})=>{
    const api='**/api/admin/v1/customer-accounts?*'
    await page.route(api,route=>route.fulfill({
      status:200,contentType:'application/json',
      body:JSON.stringify({code:'OK',data:{items:[{id:9100003,username:'CI-ACCOUNT-CANCEL',
        customer_id:Number(customerId),customer_name:'V12 CI Customer',status:'ACTIVE',account_type_code:'DEFAULT'}],total:1}})
    }))
    const writes:string[]=[]
    page.on('request',req=>{
      if(req.method()==='POST'&&/\/customer-accounts\/9100003\/(freeze|disable|reset-password)$/.test(new URL(req.url()).pathname))writes.push(req.url())
    })
    await page.goto(base+'/customer-accounts')
    await expect(page.getByText('CI-ACCOUNT-CANCEL',{exact:true})).toBeVisible()
    await page.getByRole('button',{name:'冻结',exact:true}).click()
    const prompt=page.locator('.el-message-box').last()
    await expect(prompt).toContainText('CI-ACCOUNT-CANCEL')
    await expect(page.getByRole('button',{name:'重置密码'})).toBeDisabled()
    await prompt.locator('.el-message-box__btns .el-button').first().click()
    await expect(prompt).toHaveCount(0)
    expect(writes).toHaveLength(0)
  })

  test('A UI-11 exclusive allocation ledger outage hides stale inventory and retries against PostgreSQL',async({page})=>{
    const api='**/api/admin/v1/exclusive-allocations?*'
    let unavailable=false
    await page.route(api,route=>route.fulfill({
      status:unavailable?503:200,contentType:'application/json',
      body:unavailable?JSON.stringify({code:'CI_EXCLUSIVE_LEDGER_DOWN',message:'synthetic outage'}):
       JSON.stringify({code:'OK',data:{items:[{id:920001,allocation_no:'CI-EXCLUSIVE-ALLOCATION-A',
         ip_address:'198.51.100.11',egress_claim_status:'NO_VERIFIED_CLAIM',status:'ALLOCATED'}],total:1}})
    }))
    await page.goto(base+'/exclusive-allocations')
    await expect(page.getByRole('button',{name:'CI-EXCLUSIVE-ALLOCATION-A'})).toBeVisible()
    unavailable=true
    await page.getByRole('button',{name:'查询'}).click()
    await expect(page.getByText('独享占用列表读取失败，当前历史分配和出口授权状态未知，不能据此判断可用或可释放。')).toBeVisible()
    await expect(page.getByText('CI-EXCLUSIVE-ALLOCATION-A')).toHaveCount(0)
    await expect(page.getByRole('button',{name:'核对详情'})).toHaveCount(0)
    await page.unroute(api)
    const response=page.waitForResponse(r=>r.request().method()==='GET'&&
      new URL(r.url()).pathname==='/api/admin/v1/exclusive-allocations')
    await page.getByRole('button',{name:'重新加载独享占用列表'}).click()
    expect((await response).status()).toBe(200)
    await expect(page.getByText('独享占用列表读取失败，当前历史分配和出口授权状态未知，不能据此判断可用或可释放。')).toHaveCount(0)
  })

  test('A UI-11 detail identity mismatch fails closed and a fresh verified record can be retried',async({page})=>{
    await page.route('**/api/admin/v1/exclusive-allocations?*',route=>route.fulfill({
      status:200,contentType:'application/json',body:JSON.stringify({code:'OK',data:{
        items:[{id:920002,allocation_no:'CI-EXCLUSIVE-920002',status:'ALLOCATED',
          egress_claim_status:'NO_VERIFIED_CLAIM'}],total:1}})
    }))
    let mismatch=true
    await page.route('**/api/admin/v1/exclusive-allocations/920002',route=>route.fulfill({
      status:200,contentType:'application/json',body:JSON.stringify({code:'OK',data:{
        id:mismatch?920999:920002,allocation_no:mismatch?'CI-FOREIGN-920999':'CI-EXCLUSIVE-920002',
        status:'ALLOCATED',egress_claim_status:'NO_VERIFIED_CLAIM',service_no:'CI-SERVICE-920002'}})
    }))
    await page.goto(base+'/exclusive-allocations')
    await page.getByRole('button',{name:'CI-EXCLUSIVE-920002'}).click()
    const drawer=page.locator('.el-drawer')
    await expect(drawer.getByText('当前独享占用详情读取失败或与所选记录不一致，固定公网IPv4的占用、宽限及释放状态均不可据此确认。')).toBeVisible()
    await expect(drawer.getByText('CI-FOREIGN-920999')).toHaveCount(0)
    await expect(drawer.getByText('CI-SERVICE-920002')).toHaveCount(0)
    mismatch=false
    await drawer.getByRole('button',{name:'重新加载独享详情'}).click()
    await expect(drawer.getByText('CI-SERVICE-920002')).toBeVisible()
    await expect(drawer.getByText('CI-FOREIGN-920999')).toHaveCount(0)
    await expect(drawer.getByText('缺有效出口占用快照，须先人工核对')).toBeVisible()
  })

  test('A UI-11 delayed old allocation detail never overwrites new drawer after closing and reopening',async({page})=>{
    await page.route('**/api/admin/v1/exclusive-allocations?*',route=>route.fulfill({
      status:200,contentType:'application/json',body:JSON.stringify({code:'OK',data:{
        items:[{id:920003,allocation_no:'CI-EXCLUSIVE-OLD',status:'ALLOCATED'},
          {id:920004,allocation_no:'CI-EXCLUSIVE-NEW',status:'ALLOCATED'}],total:2}})
    }))
    await page.route('**/api/admin/v1/exclusive-allocations/920003',async route=>{
      await new Promise(resolve=>setTimeout(resolve,950))
      await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({code:'OK',
        data:{id:920003,allocation_no:'CI-EXCLUSIVE-OLD',service_no:'CI-STALE-SERVICE-OLD',
          egress_claim_status:'NO_VERIFIED_CLAIM'}})})
    })
    await page.route('**/api/admin/v1/exclusive-allocations/920004',route=>route.fulfill({
      status:200,contentType:'application/json',body:JSON.stringify({code:'OK',
        data:{id:920004,allocation_no:'CI-EXCLUSIVE-NEW',service_no:'CI-CURRENT-SERVICE-NEW',
          egress_claim_status:'NO_VERIFIED_CLAIM'}})
    }))
    await page.goto(base+'/exclusive-allocations')
    await page.getByRole('button',{name:'CI-EXCLUSIVE-OLD'}).click()
    const drawer=page.locator('.el-drawer')
    await expect(drawer.getByText('正在核对当前独享占用与关联服务……')).toBeVisible()
    await drawer.locator('.el-drawer__close-btn').click()
    await expect(drawer).not.toBeVisible()
    await page.getByRole('button',{name:'CI-EXCLUSIVE-NEW'}).click()
    await expect(drawer.getByText('CI-CURRENT-SERVICE-NEW')).toBeVisible()
    await page.waitForTimeout(1100)
    await expect(drawer.getByText('CI-STALE-SERVICE-OLD')).toHaveCount(0)
    await expect(drawer.getByText('CI-CURRENT-SERVICE-NEW')).toBeVisible()
  })

  test('A UI-11 a failed current detail request shows unknown and never exposes a service action',async({page})=>{
    await page.route('**/api/admin/v1/exclusive-allocations?*',route=>route.fulfill({
      status:200,contentType:'application/json',body:JSON.stringify({code:'OK',data:{
        items:[{id:920005,allocation_no:'CI-EXCLUSIVE-UNREADABLE',status:'ALLOCATED',
          service_id:12345,service_no:'CI-UNVERIFIED-SERVICE'}],total:1}})
    }))
    await page.route('**/api/admin/v1/exclusive-allocations/920005',route=>route.fulfill({
      status:503,contentType:'application/json',
      body:JSON.stringify({code:'CI_EXCLUSIVE_DETAIL_DOWN',message:'synthetic outage'})
    }))
    await page.goto(base+'/exclusive-allocations')
    await page.getByRole('button',{name:'CI-EXCLUSIVE-UNREADABLE'}).click()
    const drawer=page.locator('.el-drawer')
    await expect(drawer.getByText('当前独享占用详情读取失败或与所选记录不一致，固定公网IPv4的占用、宽限及释放状态均不可据此确认。')).toBeVisible()
    await expect(drawer.getByRole('button',{name:'查看服务出口与任务'})).toHaveCount(0)
    await expect(drawer.getByText('CI-UNVERIFIED-SERVICE')).toHaveCount(0)
  })

})
