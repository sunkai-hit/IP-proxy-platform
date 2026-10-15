<script setup lang="ts">
import { onMounted,reactive,ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage,ElMessageBox } from 'element-plus'
import { orderServiceApi } from '@/api/orderService'
import { http } from '@/api/http'
import { useAuthStore } from '@/stores/auth'

const router=useRouter(),auth=useAuthStore(),tab=ref('queue')
const loading=ref(false),scanning=ref(false),queue=ref<any[]>([]),rows=ref<any[]>([]),total=ref(0),lastScan=ref<any>(null)
const q=reactive<any>({page:1,size:20,status:'',phase:''})
const taskDrawer=ref(false),taskLoading=ref(false),taskDetail=ref<any>(null),releasePlan=ref<any>(null)
const data=(r:any)=>r.data.data
const states:Record<string,string>={
  NONE:'未进入到期阶段',STOP_PENDING:'待真实停服',GRACE_HELD:'宽限保留（已验证停服）',
  RELEASE_PENDING:'待真实释放',RELEASE_BLOCKED:'释放受阻',RELEASED:'已核验释放',
  RENEW_RESTORE_PENDING:'续费恢复待核验'}
const reasons:Record<string,string>={
  GRACE_SNAPSHOT_MISSING:'缺少原合同宽限快照，禁止推断',
  NOT_DUE:'尚未到期',STOP_NOT_VERIFIED:'尚未核实真实停服',
  GRACE_HOLD_ACTIVE:'宽限期内仍需保留固定IP',
  RELEASE_NOT_VERIFIED:'需要真实产品与资源释放回执',
  CHECK_SERVICE_STATE:'需人工核对服务状态'}
async function load(){
 loading.value=true
 try{
  const [a,b]=await Promise.all([orderServiceApi.expiryQueue(q.phase),orderServiceApi.releases(q)])
  queue.value=data(a)||[];const out=data(b)||{};rows.value=out.items||[];total.value=out.total||0
 }catch{ElMessage.error('到期任务或释放记录加载失败')}
 finally{loading.value=false}
}
async function reconcile(){
 const {value}=await ElMessageBox.prompt(
  '只会扫描到期并登记待处理任务，不会假装停服、释放出口或自动向供应商采购。请填写操作原因。',
  '受控扫描到期任务',
  {type:'warning',inputValidator:v=>!!String(v||'').trim()||'需要审计原因'}
 )
 scanning.value=true
 try{
  lastScan.value=data(await orderServiceApi.reconcileExpiry(String(value).trim()))
  ElMessage.success('任务扫描已完成；真实停服与回收仍需要执行回执')
  await load()
 }catch{ElMessage.error('扫描未完成，请核查权限及后端日志')}
 finally{scanning.value=false}
}
async function showTask(row:any){
 if(!row.operationKey){ElMessage.warning('本服务尚未生成操作任务，请先执行受控扫描');return}
 taskLoading.value=true;taskDetail.value=null;releasePlan.value=null;taskDrawer.value=true
 try{
  taskDetail.value=data(await http.get(`/v12/delivery-actions/${encodeURIComponent(row.operationKey)}`))
 }catch{ElMessage.error('任务详情读取失败；真实代理状态不能推断为已完成')}
 finally{taskLoading.value=false}
}
async function inspectRelease(){
 if(!taskDetail.value?.operationKey||taskDetail.value?.action!=='RELEASE')return
 taskLoading.value=true;releasePlan.value=null
 try{
  releasePlan.value=data(await http.get(`/v12/release-inspections/${encodeURIComponent(taskDetail.value.operationKey)}`))
 }catch{ElMessage.warning('释放预检未通过：合同宽限、其他共享客户占用或产品端证明可能尚不满足条件')}
 finally{taskLoading.value=false}
}
onMounted(load)
</script>
<template>
 <div class="page">
  <div class="head">
   <div><h2>到期与资源释放</h2><p>到期发现 → 待执行任务 → 真实停服核验 → 宽限保留 → 真实释放。管理面状态不代表网络已停服。</p></div>
   <el-button v-if="auth.hasPermission('service:release')" type="primary" :loading="scanning" @click="reconcile">扫描到期任务</el-button>
  </div>
  <el-alert type="warning" :closable="false" title="没有实际停服、恢复或回收的产品/资源接口回执，不会把服务或公网IPv4标为已释放。宽限按原到期时刻加N×24小时计算。" show-icon/>
  <el-alert v-if="lastScan" type="info" :closable="false" style="margin-top:10px">
   <template #title>本次扫描：{{lastScan.scanned}} 条候选，新增 {{lastScan.created}} 个待核验任务，已有 {{lastScan.existing}} 个；实际执行 {{lastScan.realActionsExecuted}} 次</template>
   <span>{{lastScan.message}}</span>
  </el-alert>
  <el-card shadow="never" v-loading="loading">
   <el-tabs v-model="tab">
    <el-tab-pane name="queue" label="固定IP到期队列">
     <div class="filters"><el-select v-model="q.phase" clearable placeholder="到期阶段" style="width:230px">
      <el-option v-for="(title,code) in states" :key="code" :label="title" :value="code"/>
     </el-select><el-button @click="load">刷新队列</el-button><el-text type="info" size="small">仅显示最早到期的200条；正在处理的任务不会自动视为成功</el-text></div>
     <el-table :data="queue" stripe>
      <el-table-column prop="serviceNo" label="服务号" min-width="150"/>
      <el-table-column prop="productType" label="产品" width="105"/>
      <el-table-column prop="businessStatus" label="业务状态" width="115"/>
      <el-table-column prop="expireAt" label="到期时间" min-width="175"/>
      <el-table-column prop="graceDaysSnapshot" label="宽限(天)" width="90"><template #default="s">{{s.row.graceDaysSnapshot??'待核对'}}</template></el-table-column>
      <el-table-column prop="graceUntilAt" label="宽限截至" min-width="175"/>
      <el-table-column label="任务阶段" min-width="170"><template #default="s"><el-tag :type="s.row.expiryPhase==='RELEASED'?'success':'warning'">{{states[s.row.expiryPhase]||s.row.expiryPhase}}</el-tag></template></el-table-column>
      <el-table-column label="待办原因" min-width="220"><template #default="s">{{reasons[s.row.nextRequiredAction]||s.row.nextRequiredAction}}</template></el-table-column>
      <el-table-column label="实际操作任务" min-width="230"><template #default="s">
       <template v-if="s.row.operationKey"><div>{{s.row.actionType==='RELEASE'?'资源释放':'到期停服'}} · {{s.row.taskStatus||'未确认'}}</div><el-text type="info" size="small">{{s.row.taskStage||'—'}}</el-text></template>
       <el-text v-else type="info">未登记</el-text>
      </template></el-table-column>
      <el-table-column label="操作" width="165"><template #default="s">
       <el-button link type="primary" @click="router.push('/services/'+s.row.serviceId)">服务</el-button>
       <el-button link :disabled="!s.row.operationKey" @click="showTask(s.row)">任务</el-button>
      </template></el-table-column>
     </el-table>
    </el-tab-pane>
    <el-tab-pane name="release" label="释放结果记录">
     <div class="filters"><el-select v-model="q.status" clearable placeholder="释放状态" style="width:200px"><el-option v-for="x in ['PENDING','PROCESSING','SUCCESS','PARTIAL_SUCCESS','FAILED']" :key="x" :label="x" :value="x"/></el-select><el-button @click="load">查询</el-button></div>
     <el-table :data="rows"><el-table-column prop="release_no" label="释放单号" width="190"/><el-table-column prop="service_no" label="服务号" width="190"/><el-table-column prop="customer_name" label="客户"/><el-table-column prop="trigger_type" label="触发方式"/><el-table-column prop="status" label="状态"/><el-table-column prop="error_message" label="异常" min-width="220"/><el-table-column prop="created_at" label="时间" width="190"/></el-table>
     <div class="pager"><el-pagination v-model:current-page="q.page" v-model:page-size="q.size" :total="total" layout="total,prev,pager,next" @change="load"/></div>
    </el-tab-pane>
   </el-tabs>
  </el-card>
  <el-drawer v-model="taskDrawer" title="实际操作任务与交付核对" size="540px">
   <div v-loading="taskLoading">
    <el-alert type="warning" :closable="false" show-icon title="本页只展示任务台账和释放预检，不会下发代理操作或直接释放公网IPv4。真实操作需产品组环境及授权。" />
    <el-descriptions v-if="taskDetail" border :column="1" style="margin-top:16px">
     <el-descriptions-item label="业务任务编号">{{taskDetail.operationKey}}</el-descriptions-item>
     <el-descriptions-item label="服务编号">{{taskDetail.serviceId}}</el-descriptions-item>
     <el-descriptions-item label="动作">{{taskDetail.action}}</el-descriptions-item>
     <el-descriptions-item label="任务状态">{{taskDetail.taskStatus}}</el-descriptions-item>
     <el-descriptions-item label="执行阶段">{{taskDetail.stage}}</el-descriptions-item>
     <el-descriptions-item label="产品组操作编号">{{taskDetail.remoteOperationId||'尚未下发'}}</el-descriptions-item>
     <el-descriptions-item label="最近异常">{{taskDetail.lastErrorMessage||taskDetail.lastErrorCode||'无'}}</el-descriptions-item>
    </el-descriptions>
    <div v-if="taskDetail?.action==='RELEASE'" style="margin-top:16px">
     <el-button :loading="taskLoading" @click="inspectRelease">检查宽限及共享客户占用</el-button>
     <el-alert v-if="releasePlan" style="margin-top:12px" type="info" :closable="false" show-icon
      :title="releasePlan.ready?'仅满足业务释放预检，不代表代理已回池':'未满足释放条件'">
      <div v-for="claim in releasePlan.claims||[]" :key="claim.claimId">
       {{claim.ipv4}} · {{claim.releasePlan==='UNBIND_SERVICE_ONLY'?'仅解绑本服务':'最后占用，待产品组真实回短效核验'}} · 其他占用{{claim.otherProtectedClaims}}
      </div>
     </el-alert>
    </div>
   </div>
  </el-drawer>
 </div>
</template>
<style scoped>
.page{display:grid;gap:16px}.head{display:flex;justify-content:space-between;align-items:center}.head h2{margin:0 0 6px}.head p{margin:0;color:#7b8494}.filters{display:flex;gap:10px;align-items:center;margin-bottom:14px}.pager{display:flex;justify-content:flex-end;margin-top:14px}
</style>
