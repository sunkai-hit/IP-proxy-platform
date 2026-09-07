<script setup lang="ts">
import{computed}from'vue'
import{useRoute,useRouter}from'vue-router'
import{useAuthStore}from'@/stores/auth'
const route=useRoute(),router=useRouter(),auth=useAuthStore()
const active=computed(()=>String(route.meta.menuActive||route.path))
function logout(){auth.logout();router.replace('/login')}
</script>
<template>
<el-container class="console-shell">
  <el-aside width="236px" class="console-aside">
    <div class="console-brand"><span>IP</span><div><b>IP代理管理平台</b><small>Management Console · V1.2</small></div></div>
    <el-menu :default-active="active" router class="console-menu">
      <el-menu-item index="/dashboard">首页工作台</el-menu-item>

      <el-sub-menu v-if="auth.hasPermission('customer:access')" index="customer">
        <template #title>客户管理</template>
        <el-menu-item v-if="auth.hasPermission('customer:read')" index="/customers">客户列表</el-menu-item>
        <el-menu-item v-if="auth.hasPermission('customer:auth:read')" index="/customer-auth">客户认证</el-menu-item>
        <el-menu-item v-if="auth.hasPermission('customer:account:read')" index="/customer-accounts">客户账号</el-menu-item>
      </el-sub-menu>

      <el-sub-menu v-if="auth.hasPermission('resource:access')" index="resource">
        <template #title>资源管理</template>
        <el-menu-item v-if="auth.hasPermission('resource:read')" index="/resources">资源概览</el-menu-item>
        <el-menu-item v-if="auth.hasPermission('resource:read')" index="/resources/centos">CentOS列表</el-menu-item>
        <el-menu-item v-if="auth.hasPermission('resource:read')" index="/resources/ros">ROS列表</el-menu-item>
        <el-menu-item v-if="auth.hasPermission('resource:read')" index="/resources/lines">家宽/线路列表</el-menu-item>
        <el-menu-item v-if="auth.hasPermission('resource:pool:read')" index="/resource-pools">资源池</el-menu-item>
        <el-menu-item v-if="auth.hasPermission('resource:exclusive:read')" index="/exclusive-allocations">独享资源</el-menu-item>
        <el-menu-item v-if="auth.hasPermission('resource:domain:read')" index="/domains">域名配置</el-menu-item>
        <el-menu-item v-if="auth.hasPermission('resource:supplier:read')" index="/suppliers">外部供应商</el-menu-item>
        <el-menu-item v-if="auth.hasPermission('resource:purchase:read')" index="/supplier-purchases">外部套餐/资源采购</el-menu-item>
        <el-menu-item v-if="auth.hasPermission('resource:reverse-proxy:read')" index="/reverse-proxies">反向代理交付</el-menu-item>
      </el-sub-menu>

      <el-sub-menu v-if="auth.hasPermission('product:access')" index="product">
        <template #title>产品管理</template>
        <el-menu-item index="/products">产品列表</el-menu-item>
        <el-menu-item index="/products/short-ip">短效IP</el-menu-item>
        <el-menu-item index="/products/long-ip">长效IP</el-menu-item>
        <el-menu-item index="/products/exclusive-ip">独享IP</el-menu-item>
        <el-menu-item index="/products/vpn">VPN</el-menu-item>
        <el-menu-item index="/products/tunnel">隧道</el-menu-item>
        <el-menu-item v-if="auth.hasPermission('product:package:read')" index="/packages">套餐管理</el-menu-item>
      </el-sub-menu>

      <el-sub-menu v-if="auth.hasPermission('order-service:access')" index="order-service">
        <template #title>订单与服务</template>
        <el-menu-item v-if="auth.hasPermission('order:read')" index="/orders">订单管理</el-menu-item>
        <el-menu-item v-if="auth.hasPermission('service:provision')" index="/service-provision">服务开通</el-menu-item>
        <el-menu-item v-if="auth.hasPermission('service:read')" index="/services">服务实例</el-menu-item>
        <el-menu-item v-if="auth.hasPermission('service:change:read')||auth.hasPermission('service:change')" index="/service-changes">服务变更</el-menu-item>
        <el-menu-item v-if="auth.hasPermission('service:release')" index="/service-releases">到期与资源释放</el-menu-item>
      </el-sub-menu>

      <el-menu-item v-if="auth.hasPermission('monitor:access')" index="/monitor">监控中心</el-menu-item>
      <el-menu-item v-if="auth.hasPermission('runtime-log:access')" index="/logs">运行日志</el-menu-item>

      <el-sub-menu v-if="auth.hasPermission('long-monitor:access')" index="long-monitor">
        <template #title>长效代理监控</template>
        <el-menu-item v-if="auth.hasPermission('long-monitor:read')" index="/long-monitor">监控概览</el-menu-item>
        <el-menu-item v-if="auth.hasPermission('long-monitor:read')" index="/long-monitor/monitors">监控配置</el-menu-item>
        <el-menu-item v-if="auth.hasPermission('long-monitor:bot:read')" index="/long-monitor/bots">客户机器人</el-menu-item>
        <el-menu-item v-if="auth.hasPermission('long-monitor:alarm:read')" index="/long-monitor/alarms">告警记录</el-menu-item>
        <el-menu-item v-if="auth.hasPermission('long-monitor:notification:read')" index="/long-monitor/notifications">通知记录</el-menu-item>
      </el-sub-menu>

      <el-menu-item v-if="auth.hasPermission('statistics:access')" index="/statistics">数据统计</el-menu-item>
      <el-menu-item v-if="auth.hasPermission('system:access')" index="/system">系统管理</el-menu-item>
    </el-menu>
  </el-aside>
  <el-container>
    <el-header class="console-header"><div><span class="env-chip">V1.2</span><b>{{route.meta.title}}</b></div><div class="header-user"><span>{{auth.user?.displayName||auth.user?.username}}</span><el-button text @click="logout">退出登录</el-button></div></el-header>
    <el-main class="console-main"><router-view/></el-main>
  </el-container>
</el-container>
</template>
