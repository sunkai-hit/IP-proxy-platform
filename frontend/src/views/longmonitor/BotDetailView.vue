<script setup lang="ts">
import { onMounted,ref } from 'vue'
import { useRoute,useRouter } from 'vue-router'
import { longMonitorApi } from '@/api/longMonitor'
import { useAuthStore } from '@/stores/auth'
const route=useRoute(),router=useRouter(),auth=useAuthStore(),row=ref<any>({}),loading=ref(false)
const data=(r:any)=>r.data.data
async function load(){loading.value=true;try{row.value=data(await longMonitorApi.bot(Number(route.params.id)))||{}}finally{loading.value=false}}
onMounted(load)
</script>
<template>
<div class="page">
 <div class="head"><div><h2>客户机器人详情</h2><p>客户钉钉/飞书机器人与已绑定的长效代理监控；Webhook、Token与Secret不回显。</p></div>
  <div class="actions"><el-button @click="router.push('/long-monitor/bots')">返回机器人列表</el-button><el-button @click="load">刷新</el-button></div>
 </div>
 <el-card shadow="never" v-loading="loading">
  <el-descriptions :column="2" border>
   <el-descriptions-item label="机器人编号">{{row.bot_no||'—'}}</el-descriptions-item>
   <el-descriptions-item label="所属客户">{{row.customer_name||'—'}}</el-descriptions-item>
   <el-descriptions-item label="名称">{{row.bot_name||'—'}}</el-descriptions-item>
   <el-descriptions-item label="类型">{{row.bot_type||'—'}}</el-descriptions-item>
   <el-descriptions-item label="Webhook">{{row.webhook_mask||'未配置'}}</el-descriptions-item>
   <el-descriptions-item label="Token">{{row.accessTokenConfigured?'已配置（不可回显）':'未配置'}}</el-descriptions-item>
   <el-descriptions-item label="Secret">{{row.secretConfigured?'已配置（不可回显）':'未配置'}}</el-descriptions-item>
   <el-descriptions-item label="启用">{{row.enabled?'是':'否'}}</el-descriptions-item>
   <el-descriptions-item label="最近测试">{{row.last_test_status||'未测试'}} / {{row.last_test_at||'—'}}</el-descriptions-item>
   <el-descriptions-item label="备注">{{row.remark||'—'}}</el-descriptions-item>
  </el-descriptions>
  <el-alert type="info" :closable="false" style="margin-top:12px"
   title="测试状态仅反映适配器或机器人发送回执，不表示服务的真实代理连接正常。"/>
 </el-card>
 <el-card v-if="auth.hasPermission('long-monitor:read')" shadow="never">
  <template #header><b>引用此机器人的监控配置</b></template>
  <el-table :data="row.monitorReferences||[]" stripe border empty-text="目前没有已关联的监控配置">
   <el-table-column label="监控编号" min-width="160"><template #default="s">
    <el-button link type="primary" @click="router.push('/long-monitor/monitors/'+s.row.id)">{{s.row.monitor_no}}</el-button>
   </template></el-table-column>
   <el-table-column prop="service_no" label="关联长效服务" min-width="170"/>
   <el-table-column prop="target_host" label="监控目标" min-width="150"/>
   <el-table-column prop="interval_seconds" label="检测周期(秒)" width="120"/>
   <el-table-column prop="failure_threshold" label="异常阈值" width="105"/>
   <el-table-column prop="recovery_threshold" label="恢复阈值" width="105"/>
   <el-table-column prop="current_status" label="检测状态" min-width="110"/>
   <el-table-column prop="enabled" label="启用" width="85"><template #default="s">{{s.row.enabled?'是':'否'}}</template></el-table-column>
  </el-table>
  <el-alert v-if="row.monitorReferencesTruncated" type="warning" :closable="false"
   title="仅展示最近200条监控引用，更多记录请进入监控管理筛选。"/>
 </el-card>
</div>
</template>
<style scoped>.page{display:grid;gap:16px}.head,.actions{display:flex;justify-content:space-between;align-items:center;gap:12px}.head h2{margin:0 0 6px}.head p{margin:0;color:#7b8494}</style>