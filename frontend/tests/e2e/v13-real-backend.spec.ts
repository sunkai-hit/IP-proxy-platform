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
})
