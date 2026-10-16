<script setup lang="ts">
import {onMounted,reactive,ref} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {longMonitorApi} from '@/api/longMonitor'
import {useAuthStore} from '@/stores/auth'
const route=useRoute(),router=useRouter(),auth=useAuthStore()
const rows=ref<any[]>([]),total=ref(0),loading=ref(false)
const q=reactive<any>({page:1,size:20,status:'',customerId:route.query.customerId?Number(route.query.customerId):undefined,monitorId:route.query.monitorId?Number(route.query.monitorId):undefined})
const data=(r:any)=>r.data.data
async function load(){loading.value=true;try{const d=data(await longMonitorApi.notifications(q));rows.value=d.items||[];total.value=d.total||0}finally{loading.value=false}}
function clearScope(){q.customerId=undefined;q.monitorId=undefined;q.page=1;router.replace('/long-monitor/notifications');load()}
onMounted(load)
</script>
<template>
<div class="page"><div>
 <h2>通知记录</h2>
 <p>按长效监控、告警及客户查看钉钉/飞书发送结果。发送成功不代表代理目标已恢复。</p>
</div>
<el-card shadow="never">
 <div class="filters">
  <el-select v-model="q.status" placeholder="发送状态" clearable>
   <el-option v-for="x in ['SUCCESS','FAILED']" :key="x" :label="x" :value="x"/>
  </el-select>
  <el-tag v-if="q.customerId">客户 #{{q.customerId}}</el-tag>
  <el-tag v-if="q.monitorId">监控 #{{q.monitorId}}</el-tag>
  <el-button type="primary" @click="q.page=1;load()">查询</el-button>
  <el-button v-if="q.customerId||q.monitorId" @click="clearScope">查看全部通知</el-button>
 </div>
 <el-alert type="info" :closable="false" title="此处是发送结果记录，未上报不代表发送成功；如需确认网络恢复，必须检查关联监控的实际检测记录。"/>
 <el-table :data="rows" v-loading="loading" stripe style="margin-top:12px">
  <el-table-column prop="created_at" label="创建时间" min-width="168"/>
  <el-table-column prop="customer_name" label="客户" min-width="130"/>
  <el-table-column label="监控编号" min-width="172"><template #default="s">
   <el-button v-if="auth.hasPermission('long-monitor:read')" link type="primary" @click="router.push('/long-monitor/monitors/'+s.row.monitor_id)">{{s.row.monitor_no}}</el-button>
   <span v-else>{{s.row.monitor_no}}</span>
  </template></el-table-column>
  <el-table-column label="客户机器人" min-width="135"><template #default="s">
   <el-button v-if="s.row.bot_id&&auth.hasPermission('long-monitor:bot:read')" link type="primary" @click="router.push('/long-monitor/bots/'+s.row.bot_id)">{{s.row.bot_name||'查看机器人'}}</el-button>
   <span v-else>{{s.row.bot_name||'未指定'}}</span>
  </template></el-table-column>
  <el-table-column prop="channel" label="渠道" width="98"/>
  <el-table-column prop="notification_type" label="类型" width="110"/>
  <el-table-column label="发送状态" width="112"><template #default="s">
   <el-tag :type="s.row.send_status==='SUCCESS'?'success':'warning'">{{s.row.send_status||'UNKNOWN'}}</el-tag>
  </template></el-table-column>
  <el-table-column prop="error_message" label="发送摘要" min-width="185" show-overflow-tooltip/>
  <el-table-column label="关联告警" width="103"><template #default="s">
   <el-button v-if="s.row.alarm_id&&auth.hasPermission('long-monitor:alarm:read')" link type="primary" @click="router.push('/long-monitor/alarms/'+s.row.alarm_id)">告警详情</el-button>
  </template></el-table-column>
 </el-table>
 <div class="pager"><el-pagination v-model:current-page="q.page" v-model:page-size="q.size"
  :total="total" layout="total,sizes,prev,pager,next" @change="load"/></div>
</el-card></div>
</template>
<style scoped>.page{display:grid;gap:16px}h2{margin:0 0 6px}p{margin:0;color:#7b8494}.filters{display:flex;gap:10px;align-items:center;margin-bottom:14px;flex-wrap:wrap}.pager{display:flex;justify-content:flex-end;margin-top:14px}</style>