import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import ConsoleLayout from '@/layouts/ConsoleLayout.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', component: () => import('@/views/LoginView.vue'), meta: { public: true } },
    { path: '/', component: ConsoleLayout, children: [
      { path: '', redirect: '/dashboard' },
      { path: 'dashboard', component: () => import('@/views/DashboardView.vue'), meta: { title: '首页工作台' } },
      { path: 'customers', component: () => import('@/views/customer/CustomerListView.vue'), meta: { title: '客户管理', permission: 'customer:read' } },
      { path: 'customers/:id', component: () => import('@/views/customer/CustomerDetailView.vue'), meta: { title: '客户详情', permission: 'customer:read' } },
      { path: 'customer-auth', component: () => import('@/views/customer/CustomerAuthView.vue'), meta: { title: '客户认证', permission: 'customer:auth:read' } },
      { path: 'customer-accounts', component: () => import('@/views/customer/CustomerAccountView.vue'), meta: { title: '客户账号', permission: 'customer:account:read' } },
      { path: 'resources', component: () => import('@/views/PlaceholderView.vue'), meta: { title: '资源管理' } },
      { path: 'products', component: () => import('@/views/PlaceholderView.vue'), meta: { title: '产品管理' } },
      { path: 'orders', component: () => import('@/views/PlaceholderView.vue'), meta: { title: '订单与服务' } },
      { path: 'monitor', component: () => import('@/views/PlaceholderView.vue'), meta: { title: '监控中心' } },
      { path: 'logs', component: () => import('@/views/PlaceholderView.vue'), meta: { title: '日志中心' } },
      { path: 'alarms', component: () => import('@/views/PlaceholderView.vue'), meta: { title: '告警中心' } },
      { path: 'statistics', component: () => import('@/views/PlaceholderView.vue'), meta: { title: '数据统计' } },
      { path: 'system', component: () => import('@/views/SystemManagementView.vue'), meta: { title: '系统管理', permission: 'system:access' } }
    ]},
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
  ]
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  if (to.meta.public) return auth.loggedIn && to.path === '/login' ? '/dashboard' : true
  if (!auth.loggedIn) return { path: '/login', query: { redirect: to.fullPath } }
  if (!auth.user) { try { await auth.loadMe() } catch { auth.logout(); return '/login' } }
  const permission = to.meta.permission as string | undefined
  if (permission && !auth.hasPermission(permission)) return '/dashboard'
  return true
})

export default router
