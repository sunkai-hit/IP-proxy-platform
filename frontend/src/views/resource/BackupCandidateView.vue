<script setup lang="ts">
import {computed,onMounted,ref,watch} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {http} from '@/api/http'
import {orderServiceApi} from '@/api/orderService'
import {useAuthStore} from '@/stores/auth'

const route=useRoute(),router=useRouter(),auth=useAuthStore()
const serviceId=computed(()=>Number(route.params.id))
const service=ref<any>(null),rows=ref<any[]>([])
const loading=ref(false),serviceError=ref(false),candidateError=ref(false),requestVersion=ref(0)
const validService=computed(()=>!loading.value&&!serviceError.value&&Number(service.value?.id)===serviceId.value)
const visibleRows=computed(()=>validService.value&&!candidateError.value?rows.value:[])
const validCandidate=(c:any,id:number)=>c&&Number.isSafeInteger(Number(c.id))&&Number(c.id)>0&&
  Number(c.serviceId)===id&&Number.isSafeInteger(Number(c.lineId))&&Number(c.lineId)>0&&
  c.eligible===false&&c.dataSource==='MANAGEMENT_DB_UNVERIFIED'&&
  typeof c.reason==='string'&&!!c.reason.trim()
const evidence=(c:any)=>{
 if(c.lineDeleted===true)return '关联线路已删除，必须人工核对'
 if(!c.lineCode||!c.reportedIpv4||!c.lastSyncAt)return '线路来源/当前IP/同步时间不完整，状态未知'
 if(!c.expectedIpv4)return '候选预期IPv4缺失，不能判断一致性'
 if(c.expectedIpv4!==c.reportedIpv4)return '预期IPv4与最近上报IPv4不一致'
 return '管理面快照一致（不代表真实出口已核验）'
}
async function load(){
 const version=++requestVersion.value,id=serviceId.value
 service.value=null;rows.value=[];serviceError.value=false;candidateError.value=false;loading.value=true
 if(!Number.isSafeInteger(id)||id<=0){serviceError.value=true;loading.value=false;return}
 const results=await Promise.allSettled([
  orderServiceApi.service(id),http.get(`/v12/services/${id}/backup-candidates`)
 ])
 if(version!==requestVersion.value||id!==serviceId.value)return
 try{
  if(results[0].status!=='fulfilled'||Number(results[0].value.data?.data?.id)!==id){
   serviceError.value=true
  }else service.value=results[0].value.data.data
  if(results[1].status!=='fulfilled'||!Array.isArray(results[1].value.data?.data)){
   candidateError.value=true
  }else{
   const next=results[1].value.data.data
   const ids=new Set<number>()
   if(next.some((c:any)=>!validCandidate(c,id)||ids.has(Number(c.id))||(ids.add(Number(c.id)),false))){
    candidateError.value=true
   }else rows.value=next
  }
 }finally{loading.value=false}
}
watch(()=>route.params.id,()=>{void load()})
onMounted(()=>{void load()})
</script>

<template>
 <div class="candidate-page" v-loading="loading">
  <div class="head">
   <div><h2>备用候选管理 · 只读核对</h2>
    <p>按服务核查候选线路、预期IPv4、最近上报IPv4与管理来源；不支持新增、替换、删除或真实主备切换。</p></div>
   <div class="actions">
    <el-button @click="router.push('/services/'+serviceId)">返回服务详情</el-button>
    <el-button :loading="loading" @click="load">刷新核对</el-button>
   </div>
  </div>
  <el-alert type="warning" :closable="false" show-icon
    title="独立候选台账仅证明管理数据库中的历史和当前记录。候选健康、线路在线、IP一致均不是可用出口的真实核验，当前产品切换/回退接口未签署，禁止执行。"/>
  <el-alert v-if="serviceError" type="error" :closable="false" show-icon
    title="所属服务详情读取失败或身份不一致，禁止显示没有可靠服务归属的候选记录。"/>
  <el-alert v-if="candidateError" type="error" :closable="false" show-icon
    title="候选台账读取失败、存在跨服务记录或来源不可信，不能据此推断无候选、可切换或资源可回池。"/>
  <el-button v-if="serviceError||candidateError" :disabled="loading" @click="load">重新核对服务与候选</el-button>
  <el-card v-if="validService" shadow="never">
   <el-descriptions :column="3" border>
    <el-descriptions-item label="服务编号">{{service.service_no||'待核对'}}</el-descriptions-item>
    <el-descriptions-item label="所属客户">{{service.customer_name||'待核对'}}</el-descriptions-item>
    <el-descriptions-item label="产品类型">{{service.product_type||'待核对'}}</el-descriptions-item>
    <el-descriptions-item label="服务状态">{{service.status||'未知'}}</el-descriptions-item>
    <el-descriptions-item label="交付状态">{{service.delivery_stage||'UNVERIFIED'}}</el-descriptions-item>
    <el-descriptions-item label="候选记录数">{{candidateError?'不可核实':rows.length}}</el-descriptions-item>
   </el-descriptions>
  </el-card>
  <el-card v-if="validService" shadow="never">
   <template #header><b>服务级备用候选核对（不可执行）</b></template>
   <el-table :data="visibleRows" border stripe
    :empty-text="candidateError?'候选数据不可核实': '尚无该服务的备用候选管理记录，不表示可直接使用其他资源'">
    <el-table-column prop="id" label="候选ID" min-width="95"/>
    <el-table-column prop="lineCode" label="来源家宽/线路" min-width="155">
     <template #default="s">
      <el-button v-if="auth.hasPermission('resource:read')&&s.row.lineDeleted!==true&&s.row.lineId&&s.row.lineCode"
        link type="primary" @click="router.push('/resources/lines/'+s.row.lineId)">{{s.row.lineCode}}</el-button>
      <span v-else>{{s.row.lineCode||'线路来源缺失'}}</span>
     </template>
    </el-table-column>
    <el-table-column prop="expectedIpv4" label="候选预期IPv4" min-width="145">
     <template #default="s">{{s.row.expectedIpv4||'未知'}}</template>
    </el-table-column>
    <el-table-column prop="reportedIpv4" label="线路最近上报IPv4" min-width="155">
     <template #default="s">{{s.row.reportedIpv4||'未知'}}</template>
    </el-table-column>
    <el-table-column prop="candidateStatus" label="候选管理状态" min-width="122"/>
    <el-table-column prop="health" label="健康上报" min-width="106"/>
    <el-table-column prop="onlineStatus" label="线路在线上报" min-width="125">
     <template #default="s">{{s.row.onlineStatus||'未知'}}</template>
    </el-table-column>
    <el-table-column prop="checkedAt" label="最近候选检测" min-width="176">
     <template #default="s">{{s.row.checkedAt||'无有效检测'}}</template>
    </el-table-column>
    <el-table-column prop="lastSyncAt" label="线路最近同步" min-width="176">
     <template #default="s">{{s.row.lastSyncAt||'无来源时间'}}</template>
    </el-table-column>
    <el-table-column label="一致性核对" min-width="250"><template #default="s">
     <el-text :type="evidence(s.row).startsWith('管理面快照一致')?'info':'warning'">{{evidence(s.row)}}</el-text>
    </template></el-table-column>
    <el-table-column prop="reason" label="切换阻断" min-width="225"/>
   </el-table>
   <el-alert style="margin-top:16px" type="info" :closable="false"
    title="真实候选建档/维护、人工主备执行与回退必须等待IP资源API组、代理产品组明确能力与验收。本页不提供写操作。"/>
  </el-card>
 </div>
</template>

<style scoped>
.candidate-page{display:grid;gap:16px}.head{display:flex;justify-content:space-between;align-items:flex-start;gap:16px;flex-wrap:wrap}
.head h2{margin:0 0 8px}.head p{margin:0;color:#7b8494;max-width:760px}.actions{display:flex;gap:8px}
</style>
