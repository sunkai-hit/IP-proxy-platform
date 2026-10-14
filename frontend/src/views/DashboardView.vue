<script setup lang="ts">
import {onMounted,ref} from 'vue'
import {useRouter} from 'vue-router'
import {http} from '@/api/http'
const router=useRouter(),data=ref<any>({}),loading=ref(true),error=ref('')
const fmt=(n:any)=>n===null||n===undefined?'—':n
const time=(t:any)=>t?new Date(t).toLocaleString():'未取得'
async function load(){
 loading.value=true;error.value=''
 try{data.value=(await http.get('/dashboard/overview')).data.data||{}}
 catch(e:any){data.value={};error.value=e?.response?.data?.message||'无法读取平台概览'}
 finally{loading.value=false}
}
onMounted(load)
</script>
<template>
<div class="page-stack">
 <div class="page-head">
  <div><h1>首页工作台</h1><p>按运营业务状态、真实交付验证和资源数据来源分别展示，不以管理面状态替代实际可用。</p></div>
  <el-button @click="load">刷新</el-button>
 </div>
 <el-alert v-if="error" type="error" :title="error" show-icon/>
 <el-alert type="warning" :closable="false" show-icon title="ACTIVE 是管理面业务状态；只有实际产品或代理执行回执经过验证，才能计为已验证交付。当前资源在线状态可能缺最新采样。" />
 <div class="meta">管理数据库读取时刻：{{time(data.snapshot_at)}} · 统计口径：当前状态快照，不是全部产品的真实网络探测</div>
 <el-skeleton :loading="loading" animated>
  <el-card shadow="never">
   <template #header><b>客户与服务</b></template>
   <div class="metric-grid">
    <div class="metric" @click="router.push('/customers')"><span>有效客户</span><strong>{{fmt(data.active_customers)}}</strong><small>主档 ACTIVE</small></div>
    <div class="metric" @click="router.push('/services')"><span>管理面 ACTIVE 服务</span><strong>{{fmt(data.active_services)}}</strong><small>不是已验证交付</small></div>
    <div class="metric" @click="router.push('/services')"><span>已验证交付服务</span><strong>{{fmt(data.verified_service_count)}}</strong><small>需服务交付阶段有真实回执</small></div>
    <div class="metric" @click="router.push('/services')"><span>管理面待验真</span><strong>{{fmt(data.management_unverified_services)}}</strong><small>禁止误报客户已经可用</small></div>
   </div>
  </el-card>
  <el-card shadow="never">
   <template #header><b>到期与跨系统待办</b></template>
   <div class="metric-grid">
    <div class="metric" @click="router.push('/service-releases')"><span>未来3天到期服务</span><strong>{{fmt(data.expiring_services_3d)}}</strong><small>主档 ACTIVE/SUSPENDED</small></div>
    <div class="metric" @click="router.push('/service-releases')"><span>到期待真实停服</span><strong>{{fmt(data.due_stop_services)}}</strong><small>不能以到期时间代替网关断连</small></div>
    <div class="metric" @click="router.push('/service-releases')"><span>已进入宽限持有</span><strong>{{fmt(data.grace_held_services)}}</strong><small>IP仍不可重复出售</small></div>
    <div class="metric" @click="router.push('/service-releases')"><span>待释放或释放受阻</span><strong>{{fmt(data.release_pending_services)}}</strong><small>真实回收后才解除出口锁</small></div>
    <div class="metric" @click="router.push('/service-releases')"><span>跨系统未解决任务</span><strong>{{fmt(data.unresolved_action_tasks)}}</strong><small>待联调、执行失败或需补偿</small></div>
    <div class="metric" @click="router.push('/supplier-purchases')"><span>3天内到期上游采购</span><strong>{{fmt(data.expiring_supplier_purchases)}}</strong><small>检查是否覆盖下游服务期限</small></div>
   </div>
  </el-card>
  <el-card shadow="never">
   <template #header><b>资源与监控（主档上报值）</b></template>
   <div class="metric-grid">
    <div class="metric" @click="router.push('/resources/centos')"><span>报在线CentOS</span><strong>{{fmt(data.online_centos)}}</strong><small>并非新鲜探测保证</small></div>
    <div class="metric" @click="router.push('/resources/ros')"><span>报在线ROS</span><strong>{{fmt(data.online_ros)}}</strong><small>仅拨号执行，不含主备</small></div>
    <div class="metric" @click="router.push('/resources/lines')"><span>报在线家宽/线路</span><strong>{{fmt(data.online_lines)}}</strong><small>可能存在旧上报</small></div>
    <div class="metric" @click="router.push('/resource-pools')"><span>当前去重公网IP</span><strong>{{fmt(data.available_unique_ips)}}</strong><small>在线家宽当前IP，非历史IP总量</small></div>
    <div class="metric" @click="router.push('/logs')"><span>今日提取返回IP</span><strong>{{fmt(data.today_returned_ips)}}</strong><small>提取日志，不能等于真实连接量</small></div>
    <div class="metric" @click="router.push('/monitor')"><span>平台活动告警</span><strong>{{fmt(data.active_alarms)}}</strong><small>监控/告警记录</small></div>
   </div>
  </el-card>
 </el-skeleton>
</div>
</template>
<style scoped>
.meta{color:#858f9e;font-size:12px}
.metric{cursor:pointer;padding:12px 10px;min-width:0;display:grid;gap:8px}
.metric:hover{background:var(--el-fill-color-light);border-radius:6px}
.metric span{font-size:13px;color:var(--el-text-color-secondary)}
.metric strong{font-size:24px;font-weight:650}
.metric small{color:var(--el-text-color-secondary);line-height:1.4}
</style>
