<script setup lang="ts">
import {computed,onMounted,ref,watch} from 'vue'
import {useRouter} from 'vue-router'
import {runtimeLogApi} from '@/api/runtimeLog'
import {useAuthStore} from '@/stores/auth'
import {ElMessage} from 'element-plus'

const auth=useAuthStore(),router=useRouter(),active=ref('extract'),loading=ref(false),loadError=ref(false),requestVersion=ref(0)
const rows=ref<any[]>([]),total=ref(0),page=ref(1),size=ref(20),keyword=ref(''),sourceScope=ref('')
const range=ref<[Date,Date]>([new Date(Date.now()-7*86400_000),new Date()])
const tabs=[
 {name:'extract',label:'01 IP提取',permission:'runtime-log:extract:read',help:'来自IP提取调用记录；返回IP数不是代理连接数或流量'},
 {name:'usage',label:'02 使用日志',permission:'runtime-log:usage:read',help:'只显示已回传的代理使用事件；没有事件不表示实际流量为0'},
 {name:'api',label:'03 API日志',permission:'runtime-log:api:read',help:'仅平台Open API调用日志，敏感参数不直接展示'},
 {name:'centos',label:'04 CentOS运行',permission:'runtime-log:access',help:'现阶段仅有CentOS监控采样，并非设备完整运行日志'},
 {name:'ros',label:'05 ROS运行',permission:'runtime-log:access',help:'现阶段仅有ROS监控采样，不含自动主备或B/C段'},
 {name:'dial',label:'06 线路拨号',permission:'runtime-log:access',help:'只展示来源线路中的DIAL-IP观测，失败/重试回执尚待资源API接入'},
 {name:'ip-change',label:'07 IP变化',permission:'runtime-log:access',help:'按相邻线路历史IP观测计算变化，不代表合同固定出口已实际迁移'},
 {name:'remote',label:'08 线路远程操作',permission:'runtime-log:access',help:'当前有平台操作审计，不等同真实CentOS/ROS动作已执行成功'},
 {name:'domain',label:'09 域名变更',permission:'runtime-log:access',help:'平台DNS操作记录不是第三方DNS已经同步成功的真实证明'},
 {name:'external',label:'10 外部代理',permission:'runtime-log:access',help:'供应商/平台代理配置操作记录，缺真实网关证明时仍处于未核验'},
 {name:'operation',label:'11 操作审计',permission:'system:operation-log:read',help:'独立审计权限；只展示脱敏对象、操作者与请求标识，不暴露原始前后JSON'}
]
const visibleTabs=computed(()=>tabs.filter(t=>auth.hasPermission(t.permission)))
const current=computed(()=>tabs.find(t=>t.name===active.value))
const extended=computed(()=>!['extract','usage','api'].includes(active.value))
function fmt(v:any){return v?new Date(v).toLocaleString():'—'}
async function load(){
 const version=++requestVersion.value,category=active.value
 loading.value=true;loadError.value=false;rows.value=[];total.value=0;sourceScope.value=''
 if(!visibleTabs.value.some(t=>t.name===category)){loadError.value=true;loading.value=false;return}
 const params:any={page:page.value,size:size.value,keyword:keyword.value}
 const isExtended=!['extract','usage','api'].includes(category)
 try{
  let result:any
  if(isExtended){
   params.from=range.value?.[0]?.toISOString()
   params.to=range.value?.[1]?.toISOString()
   result=await runtimeLogApi.extended(category,params)
  }else{
   const fn=category==='extract'?runtimeLogApi.extracts:
    category==='api'?runtimeLogApi.apis:runtimeLogApi.usage
   result=await fn(params)
  }
  if(version!==requestVersion.value)return
  const response=result?.data?.data
  // HTTP 200 with no credible page is NOT proof of zero runtime events.
  // Keep all eleven log categories fail-closed, including extended audit views.
  if(!response||!Array.isArray(response.items)||
     !Number.isSafeInteger(Number(response.total))||Number(response.total)<0||
     response.items.length>Number(response.total)||
     (response.sourceScope!=null&&typeof response.sourceScope!=='string'))
    throw new Error('RUNTIME_LOG_PAGE_UNVERIFIED')
  rows.value=response.items;total.value=Number(response.total)
  sourceScope.value=response.sourceScope||''
 }catch{
  if(version===requestVersion.value){
   loadError.value=true;rows.value=[];total.value=0;sourceScope.value=''
   ElMessage.error('日志读取失败：当前记录并非空列表或零事件的证明')
  }
 }finally{if(version===requestVersion.value)loading.value=false}
}
function search(){page.value=1;load()}
function reset(){keyword.value='';range.value=[new Date(Date.now()-7*86400_000),new Date()];search()}
function canNavigate(row:any){
 const path=String(row?.relatedPath||'')
 if(/^\/orders\/[0-9]+$/.test(path))return auth.hasPermission('order:read')
 if(/^\/services\/[0-9]+$/.test(path))return auth.hasPermission('service:read')
 if(/^\/resources\/lines\/[0-9]+$/.test(path))return auth.hasPermission('resource:read')
 if(/^\/resources\/(centos|ros)\/[0-9]+$/.test(path))return auth.hasPermission('resource:read')
 if(/^\/domains\/[0-9]+$/.test(path))return auth.hasPermission('resource:domain:read')
 if(/^\/reverse-proxies\/[0-9]+$/.test(path))return auth.hasPermission('resource:reverse-proxy:read')
 return false
}
function navigate(row:any){if(canNavigate(row))void router.push(row.relatedPath)}
watch(active,()=>{page.value=1;load()})
onMounted(()=>{if(!auth.hasPermission('runtime-log:extract:read')&&visibleTabs.value.length)active.value=visibleTabs.value[0].name;if(visibleTabs.value.length)load();else loadError.value=true})
</script>

<template>
<div class="page-stack">
 <el-card shadow="never">
  <div class="page-title"><div><h2>业务日志中心｜十一类入口</h2>
   <p>日志按业务来源分开。记录存在不等于远端执行成功；尚未接入的真实运行事件不以模拟数据替代。</p></div>
  </div>
  <el-tabs v-model="active" tab-position="top">
   <el-tab-pane v-for="t in visibleTabs" :key="t.name" :label="t.label" :name="t.name"/>
  </el-tabs>
  <el-alert :title="current?.help||''" type="info" :closable="false" show-icon/>
  <el-alert v-if="loadError" type="error" :closable="false" title="日志分类读取失败或权限不足，不能据此认定没有日志事件。" style="margin-top:12px"/>
  <div class="filter-row" style="margin-top:12px;gap:10px">
   <el-input v-model="keyword" clearable placeholder="客户、服务号、对象编号、事件/请求标识" style="width:310px" @keyup.enter="search"/>
   <el-date-picker v-if="extended" v-model="range" type="datetimerange" range-separator="至"
      start-placeholder="开始时间" end-placeholder="结束时间" style="max-width:430px"/>
   <el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button>
  </div>
 </el-card>
 <el-card shadow="never">
  <div v-if="sourceScope" style="margin-bottom:12px;color:#7c8799">
   记录来源：{{sourceScope}}。仅提供可验证的现有存储字段，未接入内容不是零事件的证明。
  </div>
  <el-table :data="rows" v-loading="loading" border stripe
    :empty-text="loadError?'日志数据读取失败':'该分类与筛选条件下没有已采集的记录；不代表外部系统没有发生事件'">
   <template v-if="extended">
    <el-table-column label="时间" min-width="165"><template #default="s">{{fmt(s.row.eventTime)}}</template></el-table-column>
    <el-table-column prop="objectType" label="对象类型" width="125"/>
    <el-table-column prop="objectCode" label="业务对象" min-width="175"/>
    <el-table-column prop="eventType" label="事件/指标" min-width="150"/>
    <el-table-column label="记录结果" min-width="110"><template #default="s">{{s.row.result??'无执行回执'}}</template></el-table-column>
    <el-table-column prop="summary" label="脱敏摘要" min-width="220"/>
    <el-table-column prop="operationKey" label="远端/请求编号" min-width="165"/>
    <el-table-column label="来源类别" min-width="210"><template #default="s">{{s.row.source}}</template></el-table-column>
    <el-table-column label="关联业务" width="100"><template #default="s">
     <el-button v-if="canNavigate(s.row)" link type="primary" @click="navigate(s.row)">查看</el-button>
     <span v-else>—</span></template></el-table-column>
   </template>
   <template v-else-if="active==='extract'">
    <el-table-column prop="request_id" label="Request ID" min-width="185"/>
    <el-table-column prop="customer_name" label="客户" min-width="150"/>
    <el-table-column prop="service_no" label="服务" min-width="160"/>
    <el-table-column prop="product_name" label="产品" min-width="120"/>
    <el-table-column prop="client_ip" label="客户端IP" min-width="130"/>
    <el-table-column prop="requested_count" label="请求IP数" width="100"/>
    <el-table-column prop="returned_count" label="返回IP数" width="100"/>
    <el-table-column prop="result" label="结果" width="95"/>
    <el-table-column prop="error_code" label="错误码" min-width="120"/>
    <el-table-column label="时间" min-width="165"><template #default="s">{{fmt(s.row.occurred_at)}}</template></el-table-column>
   </template>
   <template v-else-if="active==='api'">
    <el-table-column prop="request_id" label="Request ID" min-width="185"/>
    <el-table-column prop="customer_name" label="客户" min-width="150"/>
    <el-table-column prop="service_no" label="服务" min-width="160"/>
    <el-table-column prop="method" label="方法" width="85"/>
    <el-table-column prop="path" label="接口" min-width="225"/>
    <el-table-column prop="response_status" label="HTTP" width="80"/>
    <el-table-column prop="result" label="结果" width="95"/>
    <el-table-column prop="error_code" label="错误码" min-width="130"/>
    <el-table-column label="时间" min-width="165"><template #default="s">{{fmt(s.row.occurred_at)}}</template></el-table-column>
   </template>
   <template v-else>
    <el-table-column prop="request_id" label="Request ID" min-width="185"/>
    <el-table-column prop="customer_name" label="客户" min-width="145"/>
    <el-table-column prop="service_no" label="服务" min-width="150"/>
    <el-table-column prop="client_ip" label="客户端IP" min-width="125"/>
    <el-table-column prop="outbound_ip" label="出站IP" min-width="125"/>
    <el-table-column prop="protocol_code" label="协议" width="90"/>
    <el-table-column prop="upload_bytes" label="上传Byte" min-width="105"/>
    <el-table-column prop="download_bytes" label="下载Byte" min-width="105"/>
    <el-table-column prop="result" label="结果" width="95"/>
    <el-table-column label="时间" min-width="165"><template #default="s">{{fmt(s.row.occurred_at)}}</template></el-table-column>
   </template>
  </el-table>
  <div style="display:flex;justify-content:flex-end;margin-top:16px">
   <el-pagination v-model:current-page="page" v-model:page-size="size" :total="total"
      :page-sizes="[20,50,100]" layout="total, sizes, prev, pager, next" @change="load"/>
  </div>
 </el-card>
</div>
</template>
