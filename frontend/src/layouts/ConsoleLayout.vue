<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
const route = useRoute(); const router = useRouter(); const auth = useAuthStore()
const active = computed(() => route.path)
const menus = [['/dashboard','首页工作台'],['/customers','客户管理'],['/resources','资源管理'],['/products','产品管理'],['/orders','订单与服务'],['/monitor','监控中心'],['/logs','日志中心'],['/alarms','告警中心'],['/statistics','数据统计'],['/system','系统管理']]
function logout(){ auth.logout(); router.replace('/login') }
</script>
<template>
  <el-container class="console-shell">
    <el-aside width="236px" class="console-aside">
      <div class="console-brand"><span>IP</span><div><b>IP代理管理平台</b><small>Management Console</small></div></div>
      <el-menu :default-active="active" router class="console-menu">
        <el-menu-item v-for="item in menus" :key="item[0]" :index="item[0]">{{ item[1] }}</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="console-header"><div><span class="env-chip">M1</span><b>{{ route.meta.title }}</b></div><div class="header-user"><span>{{ auth.user?.displayName || auth.user?.username }}</span><el-button text @click="logout">退出登录</el-button></div></el-header>
      <el-main class="console-main"><router-view /></el-main>
    </el-container>
  </el-container>
</template>
