import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import ConsoleLayout from '@/layouts/ConsoleLayout.vue'

const router=createRouter({history:createWebHistory(),routes:[
 {path:'/login',component:()=>import('@/views/LoginView.vue'),meta:{public:true}},
 {path:'/',component:ConsoleLayout,children:[
  {path:'',redirect:'/dashboard'},
  {path:'dashboard',component:()=>import('@/views/DashboardView.vue'),meta:{title:'首页工作台'}},

  {path:'customers',component:()=>import('@/views/customer/CustomerListView.vue'),meta:{title:'客户列表',permission:'customer:read'}},
  {path:'customers/:id',component:()=>import('@/views/customer/CustomerDetailView.vue'),meta:{title:'客户详情',permission:'customer:read',menuActive:'/customers'}},
  {path:'customer-auth',component:()=>import('@/views/customer/CustomerAuthView.vue'),meta:{title:'客户认证',permission:'customer:auth:read'}},
  {path:'customer-accounts',component:()=>import('@/views/customer/CustomerAccountView.vue'),meta:{title:'客户账号',permission:'customer:account:read'}},

  {path:'resources',component:()=>import('@/views/resource/ResourceOverviewView.vue'),meta:{title:'资源概览',permission:'resource:read'}},
  {path:'resources/centos',component:()=>import('@/views/resource/V12ResourceListView.vue'),meta:{title:'CentOS列表',permission:'resource:read',resourceType:'CENTOS'}},
  {path:'resources/centos/:id',component:()=>import('@/views/resource/V12ResourceDetailView.vue'),meta:{title:'CentOS详情',permission:'resource:read',resourceType:'CENTOS',menuActive:'/resources/centos'}},
  {path:'resources/ros',component:()=>import('@/views/resource/V12ResourceListView.vue'),meta:{title:'ROS列表',permission:'resource:read',resourceType:'ROS'}},
  {path:'resources/ros/:id',component:()=>import('@/views/resource/V12ResourceDetailView.vue'),meta:{title:'ROS详情',permission:'resource:read',resourceType:'ROS',menuActive:'/resources/ros'}},
  {path:'resources/lines',component:()=>import('@/views/resource/V12ResourceListView.vue'),meta:{title:'家宽/线路列表',permission:'resource:read',resourceType:'LINE'}},
  {path:'resources/lines/:id',component:()=>import('@/views/resource/V12ResourceDetailView.vue'),meta:{title:'家宽/线路详情',permission:'resource:read',resourceType:'LINE',menuActive:'/resources/lines'}},
  {path:'resource-pools',component:()=>import('@/views/resource/ResourcePoolView.vue'),meta:{title:'资源池',permission:'resource:pool:read'}},
  {path:'resource-pools/:id',component:()=>import('@/views/resource/ResourcePoolDetailView.vue'),meta:{title:'资源池详情',permission:'resource:pool:read',menuActive:'/resource-pools'}},
  {path:'exclusive-allocations',component:()=>import('@/views/resource/ExclusiveResourceView.vue'),meta:{title:'独享资源',permission:'resource:exclusive:read'}},
  {path:'exclusive-allocations/:id',component:()=>import('@/views/resource/LegacyResourceDetailView.vue'),meta:{title:'独享资源详情',permission:'resource:exclusive:read',detailType:'EXCLUSIVE',menuActive:'/exclusive-allocations'}},
  {path:'domains',component:()=>import('@/views/resource/ResourceDomainView.vue'),meta:{title:'域名配置',permission:'resource:domain:read'}},
  {path:'domains/:id',component:()=>import('@/views/resource/LegacyResourceDetailView.vue'),meta:{title:'域名详情',permission:'resource:domain:read',detailType:'DOMAIN',menuActive:'/domains'}},
  {path:'suppliers',component:()=>import('@/views/resource/ResourceSupplierView.vue'),meta:{title:'外部供应商',permission:'resource:supplier:read'}},
  {path:'suppliers/:id',component:()=>import('@/views/resource/LegacyResourceDetailView.vue'),meta:{title:'供应商详情',permission:'resource:supplier:read',detailType:'SUPPLIER',menuActive:'/suppliers'}},
  {path:'supplier-purchases',component:()=>import('@/views/resource/SupplierPurchaseView.vue'),meta:{title:'外部套餐/资源采购',permission:'resource:purchase:read'}},
  {path:'supplier-purchases/:id',component:()=>import('@/views/resource/SupplierPurchaseDetailView.vue'),meta:{title:'外部采购详情',permission:'resource:purchase:read',menuActive:'/supplier-purchases'}},
  {path:'reverse-proxies',component:()=>import('@/views/resource/ReverseProxyView.vue'),meta:{title:'反向代理交付',permission:'resource:reverse-proxy:read'}},
  {path:'reverse-proxies/:id',component:()=>import('@/views/resource/ReverseProxyDetailView.vue'),meta:{title:'反向代理详情',permission:'resource:reverse-proxy:read',menuActive:'/reverse-proxies'}},

  {path:'products',component:()=>import('@/views/product/ProductListView.vue'),meta:{title:'产品列表',permission:'product:read'}},
  {path:'products/short-ip',component:()=>import('@/views/product/ProductListView.vue'),meta:{title:'短效IP',permission:'product:read',productType:'SHORT_IP'}},
  {path:'products/long-ip',component:()=>import('@/views/product/ProductListView.vue'),meta:{title:'长效IP',permission:'product:read',productType:'LONG_IP'}},
  {path:'products/exclusive-ip',component:()=>import('@/views/product/ProductListView.vue'),meta:{title:'独享IP',permission:'product:read',productType:'EXCLUSIVE_IP'}},
  {path:'products/vpn',component:()=>import('@/views/product/ProductListView.vue'),meta:{title:'VPN',permission:'product:read',productType:'VPN'}},
  {path:'products/tunnel',component:()=>import('@/views/product/ProductListView.vue'),meta:{title:'隧道',permission:'product:read',productType:'TUNNEL'}},
  {path:'products/:id',component:()=>import('@/views/product/ProductDetailView.vue'),meta:{title:'产品详情',permission:'product:read',menuActive:'/products'}},
  {path:'packages',component:()=>import('@/views/product/PackageView.vue'),meta:{title:'套餐管理',permission:'product:package:read'}},
  {path:'packages/:id',component:()=>import('@/views/product/PackageDetailView.vue'),meta:{title:'套餐详情',permission:'product:package:read',menuActive:'/packages'}},

  {path:'orders',component:()=>import('@/views/service/OrderListView.vue'),meta:{title:'订单管理',permission:'order:read'}},
  {path:'orders/:id',component:()=>import('@/views/service/OrderDetailView.vue'),meta:{title:'订单详情',permission:'order:read',menuActive:'/orders'}},
  {path:'service-provision',component:()=>import('@/views/service/ServiceProvisionView.vue'),meta:{title:'服务开通',permission:'service:provision'}},
  {path:'services',component:()=>import('@/views/service/ServiceListView.vue'),meta:{title:'服务实例',permission:'service:read'}},
  {path:'services/:id',component:()=>import('@/views/service/ServiceDetailView.vue'),meta:{title:'服务详情',permission:'service:read',menuActive:'/services'}},
  {path:'service-changes',component:()=>import('@/views/service/ServiceChangeView.vue'),meta:{title:'服务变更',permission:'service:change'}},
  {path:'service-releases',component:()=>import('@/views/service/ServiceReleaseView.vue'),meta:{title:'到期与资源释放',permission:'service:release'}},

  {path:'monitor',component:()=>import('@/views/runtime/MonitorView.vue'),meta:{title:'监控中心',permission:'monitor:read'}},
  {path:'monitor/:type/:id',component:()=>import('@/views/runtime/MonitorDetailView.vue'),meta:{title:'监控详情',permission:'monitor:read',menuActive:'/monitor'}},
  {path:'logs',component:()=>import('@/views/runtime/RuntimeLogView.vue'),meta:{title:'运行日志',permission:'runtime-log:access'}},

  {path:'long-monitor',component:()=>import('@/views/longmonitor/LongMonitorOverviewView.vue'),meta:{title:'长效代理监控',permission:'long-monitor:read'}},
  {path:'long-monitor/monitors',component:()=>import('@/views/longmonitor/LongMonitorListView.vue'),meta:{title:'长效监控配置',permission:'long-monitor:read'}},
  {path:'long-monitor/monitors/:id',component:()=>import('@/views/longmonitor/LongMonitorDetailView.vue'),meta:{title:'长效监控详情',permission:'long-monitor:read',menuActive:'/long-monitor/monitors'}},
  {path:'long-monitor/bots',component:()=>import('@/views/longmonitor/BotListView.vue'),meta:{title:'客户机器人',permission:'long-monitor:bot:read'}},
  {path:'long-monitor/bots/:id',component:()=>import('@/views/longmonitor/BotDetailView.vue'),meta:{title:'客户机器人详情',permission:'long-monitor:bot:read',menuActive:'/long-monitor/bots'}},
  {path:'long-monitor/alarms',component:()=>import('@/views/longmonitor/LongAlarmView.vue'),meta:{title:'长效代理告警',permission:'long-monitor:alarm:read'}},
  {path:'long-monitor/alarms/:id',component:()=>import('@/views/longmonitor/LongAlarmDetailView.vue'),meta:{title:'长效告警详情',permission:'long-monitor:alarm:read',menuActive:'/long-monitor/alarms'}},
  {path:'long-monitor/notifications',component:()=>import('@/views/longmonitor/NotificationView.vue'),meta:{title:'通知记录',permission:'long-monitor:notification:read'}},

  {path:'statistics',component:()=>import('@/views/runtime/StatisticsView.vue'),meta:{title:'数据统计',permission:'statistics:read'}},
  {path:'system',component:()=>import('@/views/SystemManagementView.vue'),meta:{title:'系统管理',permission:'system:access'}}
 ]},
 {path:'/:pathMatch(.*)*',redirect:'/dashboard'}
]})
router.beforeEach(async(to)=>{const auth=useAuthStore();if(to.meta.public)return auth.loggedIn&&to.path==='/login'?'/dashboard':true;if(!auth.loggedIn)return{path:'/login',query:{redirect:to.fullPath}};if(!auth.user){try{await auth.loadMe()}catch{auth.logout();return'/login'}}const permission=to.meta.permission as string|undefined;if(permission&&!auth.hasPermission(permission))return'/dashboard';return true})
export default router
