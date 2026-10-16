<script setup lang="ts">
import { onMounted,ref } from 'vue'
import { useRouter } from 'vue-router'
import { longMonitorApi } from '@/api/longMonitor'
import { useAuthStore } from '@/stores/auth'

const router=useRouter(),auth=useAuthStore()
const d=ref<any>({}),loading=ref(false),loadError=ref(false),requestVersion=ref(0)
const data=(r:any)=>r.data.data
const count=(v:any)=>v===null||v===undefined||v===''?'—':v
async function load(){
 const version=++requestVersion.value
 loading.value=true;loadError.value=false;d.value={}
 try{
  const summary=data(await longMonitorApi.overview())||{}
  if(version===requestVersion.value)d.value=summary
 }catch{
  if(version===requestVersion.value){loadError.value=true;d.value={}}
 }finally{if(version===requestVersion.value)loading.value=false}
}
onMounted(load)
</script>
<template>
<div class="page">
 <div class="head">
  <div><h2>长效代理监控</h2><p>面向长效代理客户的服务保障：代理异常后通知客户自己的钉钉或飞书机器人。</p></div>
  <el-button @click="load" :loading="loading">刷新总览</el-button>
 </div>
 <el-alert type="warning" :closable="false" title="这里是平台监控记录汇总；缺失采样或外部探测时不表示真实代理连接健康。" />
 <el-alert v-if="loadError" type="error" :closable="false"
   title="长效监控总览读取失败，统计值未知，不能据此认定没有异常代理、开放告警或失败通知。" />
 <el-button v-if="loadError" @click="load">重新加载监控总览</el-button>
 <div class="cards" v-loading="loading">
  <el-card shadow="never"><b>监控总数</b><strong>{{count(d.monitor_total)}}</strong></el-card>
  <el-card shadow="never"><b>启用监控</b><strong>{{count(d.monitor_enabled)}}</strong></el-card>
  <el-card shadow="never"><b>异常代理</b><strong>{{count(d.monitor_abnormal)}}</strong></el-card>
  <el-card shadow="never"><b>开放告警</b><strong>{{count(d.alarm_open)}}</strong></el-card>
  <el-card shadow="never"><b>启用机器人</b><strong>{{count(d.bot_enabled)}}</strong></el-card>
  <el-card shadow="never"><b>24h通知失败</b><strong>{{count(d.notification_failed_24h)}}</strong></el-card>
 </div>
 <el-card shadow="never"><el-space wrap>
  <el-button v-if="auth.hasPermission('long-monitor:read')" type="primary" @click="router.push('/long-monitor/monitors')">监控配置</el-button>
  <el-button v-if="auth.hasPermission('long-monitor:bot:read')" @click="router.push('/long-monitor/bots')">客户机器人</el-button>
  <el-button v-if="auth.hasPermission('long-monitor:alarm:read')" @click="router.push('/long-monitor/alarms')">告警记录</el-button>
  <el-button v-if="auth.hasPermission('long-monitor:notification:read')" @click="router.push('/long-monitor/notifications')">通知记录</el-button>
 </el-space></el-card>
</div>
</template>
<style scoped>
.page{display:grid;gap:16px}.head{display:flex;justify-content:space-between;align-items:center;gap:12px}.head h2{margin:0 0 6px}.head p{margin:0;color:#7b8494}
.cards{display:grid;grid-template-columns:repeat(3,1fr);gap:14px}.cards strong{display:block;font-size:28px;margin-top:12px}
</style>
