<script setup lang="ts">
import {onMounted,ref,watch} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {ElMessage,ElMessageBox} from 'element-plus'
import {longMonitorApi} from '@/api/longMonitor'
import {useAuthStore} from '@/stores/auth'
const route=useRoute(),router=useRouter(),auth=useAuthStore(),row=ref<any>({}),loading=ref(false),loadError=ref(false),closing=ref(false),requestVersion=ref(0),data=(r:any)=>r.data.data
let id=Number(route.params.id)
async function load(){
 const version=++requestVersion.value,alarmId=id
 loading.value=true;loadError.value=false;row.value={}
 try{const detail=data(await longMonitorApi.alarm(alarmId))||{};if(version===requestVersion.value)row.value=detail}
 catch{if(version===requestVersion.value){loadError.value=true;row.value={}}}
 finally{if(version===requestVersion.value)loading.value=false}
}
async function close(){
 if(closing.value||loading.value||loadError.value||!row.value.alarm_no||
    row.value.status==='CLOSED'||!auth.hasPermission('long-monitor:alarm:close'))return
 const alarmId=id,version=requestVersion.value
 const alarmNo=row.value.alarm_no,status=row.value.status
 closing.value=true
 try{
  await ElMessageBox.confirm('关闭只代表人工归档，不会改变代理网络状态或检测结果。请先核查监控及服务。','告警归档影响确认',{type:'warning'})
  const {value}=await ElMessageBox.prompt('请输入归档原因','关闭告警',{inputValidator:v=>!!String(v||'').trim()||'必须填写原因'})
  if(id!==alarmId||version!==requestVersion.value||loadError.value||
     row.value.alarm_no!==alarmNo||row.value.status!==status||
     !auth.hasPermission('long-monitor:alarm:close')){
   ElMessage.warning('归档确认期间告警信息已变化，请重新查询')
   return
  }
  await longMonitorApi.closeAlarm(alarmId,String(value).trim())
  if(id!==alarmId||version!==requestVersion.value)return
  ElMessage.success('已归档告警；真实网络恢复仍以检测结果为准')
  await load()
 }catch(e:any){
  if(e==='cancel'||e==='close')return
  if(id===alarmId&&version===requestVersion.value)
   ElMessage.error('告警归档未确认成功；请重新查询，不能按已关闭处理')
 }finally{closing.value=false}
}
watch(()=>route.params.id,()=>{id=Number(route.params.id);load()})
onMounted(load)
</script>
<template>
<div class="page">
 <div class="head">
  <div><el-button link @click="router.push('/long-monitor/alarms')">← 返回告警列表</el-button>
    <h2>{{row.alarm_no||'长效告警详情'}}</h2>
    <p>异常 → 通知 → 检测恢复 → 人工归档。归档不能代替真实网络恢复。</p>
  </div>
  <div class="actions"><el-button @click="load">刷新</el-button>
   <el-button v-if="!loadError&&!loading&&!!row.alarm_no&&auth.hasPermission('long-monitor:alarm:close')&&row.status!=='CLOSED'"
     type="warning" :loading="closing" :disabled="closing" @click="close">确认归档</el-button>
  </div>
 </div>
 <el-alert type="warning" :closable="false"
  title="告警状态和监控当前状态分别记录；人工关闭告警不会重新建立代理连接或清除真实故障。"/>
 <el-alert v-if="loadError" type="error" :closable="false" title="告警详情读取失败，不能据此判断告警已恢复、已归档或可执行归档。"/><el-button v-if="loadError" @click="load">重新加载告警详情</el-button>
 <el-card v-if="!loadError&&row.alarm_no" v-loading="loading" shadow="never">
  <template #header><b>告警主记录</b></template>
  <el-descriptions :column="2" border>
   <el-descriptions-item label="告警编号">{{row.alarm_no||'—'}}</el-descriptions-item>
   <el-descriptions-item label="告警类型">{{row.alarm_type||'—'}}</el-descriptions-item>
   <el-descriptions-item label="告警状态"><el-tag :type="row.status==='OPEN'?'danger':'info'">{{row.status||'—'}}</el-tag></el-descriptions-item>
   <el-descriptions-item label="检测当前状态">{{row.monitor_status||'UNKNOWN'}}</el-descriptions-item>
   <el-descriptions-item label="所属客户">
    <el-button v-if="auth.hasPermission('customer:read')" link type="primary" @click="router.push('/customers/'+row.customer_id)">{{row.customer_name}}</el-button>
    <span v-else>{{row.customer_name||'—'}}</span>
   </el-descriptions-item>
   <el-descriptions-item label="关联服务">
    <el-button v-if="auth.hasPermission('service:read')" link type="primary" @click="router.push('/services/'+row.service_id)">{{row.service_no}}</el-button>
    <span v-else>{{row.service_no||'—'}}</span>
   </el-descriptions-item>
   <el-descriptions-item label="监控配置">
    <el-button v-if="auth.hasPermission('long-monitor:read')" link type="primary" @click="router.push('/long-monitor/monitors/'+row.monitor_id)">{{row.monitor_no}}</el-button>
    <span v-else>{{row.monitor_no||'—'}}</span>
   </el-descriptions-item>
   <el-descriptions-item label="通知机器人">
    <el-button v-if="row.bot_id&&auth.hasPermission('long-monitor:bot:read')" link type="primary" @click="router.push('/long-monitor/bots/'+row.bot_id)">{{row.bot_name||row.bot_no}}</el-button>
    <span v-else>{{row.bot_name||'未设置'}}</span>
   </el-descriptions-item>
   <el-descriptions-item label="检测目标">{{row.target_host||'—'}}:{{row.target_port||'—'}}</el-descriptions-item>
   <el-descriptions-item label="连续失败/恢复阈值">{{row.failure_threshold||'—'}} / {{row.recovery_threshold||'—'}}</el-descriptions-item>
   <el-descriptions-item label="告警发生">{{row.opened_at||'—'}}</el-descriptions-item>
   <el-descriptions-item label="检测恢复">{{row.recovered_at||'尚无恢复证据'}}</el-descriptions-item>
   <el-descriptions-item label="最近检测">{{row.last_checked_at||'尚无检测回执'}}</el-descriptions-item>
   <el-descriptions-item label="归档时间">{{row.closed_at||'未归档'}}</el-descriptions-item>
   <el-descriptions-item label="归档原因" :span="2">{{row.close_reason||'—'}}</el-descriptions-item>
  </el-descriptions>
 </el-card>
 <el-card v-if="!loadError&&row.alarm_no" shadow="never">
  <template #header><b>通知与回查</b></template>
  <p>通知结果可在通知记录中按客户、监控编号核对；服务真实网络状态需回到监控详情确认。</p>
  <div class="actions">
   <el-button v-if="auth.hasPermission('long-monitor:notification:read')"
    @click="router.push({path:'/long-monitor/notifications',query:{customerId:String(row.customer_id),monitorId:String(row.monitor_id)}})">查看通知记录</el-button>
   <el-button v-if="auth.hasPermission('long-monitor:read')"
    @click="router.push('/long-monitor/monitors/'+row.monitor_id)">检测历史</el-button>
  </div>
 </el-card>
</div>
</template>
<style scoped>.page{display:grid;gap:16px}.head,.actions{display:flex;justify-content:space-between;align-items:center;gap:12px;flex-wrap:wrap}.head h2{margin:6px 0}.head p{margin:0;color:#7b8494}</style>