<script setup lang="ts">
import {nextTick,onBeforeUnmount,onMounted,ref,watch} from 'vue'
import * as echarts from 'echarts'
import {ElMessage} from 'element-plus'
import {statisticsApi} from '@/api/runtimeOps'
import {useAuthStore} from '@/stores/auth'

type Group='centos'|'ros'|'lines'|'pools'|'customers'|'products'|'suppliers'
const auth=useAuthStore(),active=ref<Group>('centos'),loading=ref(false),calculating=ref(false),loadError=ref(false),overviewError=ref(false),requestVersion=ref(0)
const rows=ref<any[]>([]),overview=ref<any>({}),snapshotAt=ref(''),chartEl=ref<HTMLElement>(),chart=ref<echarts.ECharts>()
const range=ref<[Date,Date]>([new Date(Date.now()-24*3600_000),new Date()])
const tabs:{key:Group;label:string;meaning:string}[]=[
 {key:'centos',label:'CentOS统计',meaning:'B/C段只按CentOS关联家宽当前IPv4的/16、/24快照去重'},
 {key:'ros',label:'ROS统计',meaning:'ROS仅反映拨号与运行对象，不出现B/C段或自动主备'},
 {key:'lines',label:'家宽/线路统计',meaning:'SHARED/LONG家宽和当前线路状态，来源为资源主档'},
 {key:'pools',label:'资源池统计',meaning:'以来源池家宽线路成员统计；同一线路可多池，跨池不可直接相加'},
 {key:'customers',label:'客户使用统计',meaning:'IP提取和真实代理连接/流量是不同事件，缺使用事件显示未接入'},
 {key:'products',label:'产品服务统计',meaning:'ACTIVE为业务管理状态，不表示已通过真实数据面验证'},
 {key:'suppliers',label:'外部采购与代理统计',meaning:'历史供应商获取日志不同于现有采购及平台代理真实可用状态'}
]
const selected=()=>tabs.find(t=>t.key===active.value)
const fmt=(v:any)=>v?new Date(v).toLocaleString():'—'
function params(){return{from:range.value?.[0]?.toISOString(),to:range.value?.[1]?.toISOString()}}
function sourceState(r:any){
 if(r.usage_source_state==='NO_DATA_PLANE_EVENTS')return '无真实使用事件'
 if(r.usage_source_state==='LOG_EVENTS_PRESENT_UNVERIFIED')return '有使用日志，未核验网络'
 if(r.usage_source_state==='NO_EXTERNAL_FETCH_EVENTS')return '无供应商抓取日志'
 if(r.usage_source_state==='FETCH_LOG_EVENTS_PRESENT_UNVERIFIED')return '有抓取日志，未核验上游'
 return '管理面统计快照'
}
function usageValue(row:any,field:string){
 if(row.usage_source_state==='NO_DATA_PLANE_EVENTS'&&
  ['request_count','success_count','failure_count','unique_outbound_ip_count','upload_bytes','download_bytes'].includes(field))return '—'
 if(row.usage_source_state==='NO_EXTERNAL_FETCH_EVENTS'&&
  ['success_count','failure_count','received_resource_count','unique_ip_count','available_ip_count','imported_ip_count','avg_elapsed_ms'].includes(field))return '—'
 return row[field]??'—'
}
const chartFields:Record<Group,string[]>={
 centos:['centos_total','centos_online','b_prefix_count','c_prefix_count'],
 ros:['ros_total','ros_online'],
 lines:['line_total','line_online','line_abnormal'],
 pools:[],
 customers:['extract_count','returned_ip_count','request_count'],
 products:['active_service_count','extract_count','request_count'],
 suppliers:['fetch_count','received_resource_count']
}
function chartRows(){
 const byTime=new Map<string,Record<string,number|null>>()
 const fields=chartFields[active.value]
 for(const row of rows.value){
  if(!row.stat_hour)continue
  const t=new Date(row.stat_hour).toISOString()
  const metrics=byTime.get(t)||{}
  for(const field of fields){
   const missing=usageValue(row,field)==='—'
   if(missing||row[field]==null)continue
   metrics[field]=(metrics[field]??0)+Number(row[field])
  }
  byTime.set(t,metrics)
 }
 return [...byTime.entries()].sort((a,b)=>a[0].localeCompare(b[0]))
}
function draw(){
 chart.value?.dispose();chart.value=undefined
 if(!chartEl.value||active.value==='pools')return
 const fields=chartFields[active.value],history=chartRows()
 chart.value=echarts.init(chartEl.value)
 chart.value.setOption({
  tooltip:{trigger:'axis'},legend:{data:fields},grid:{left:56,right:26,top:52,bottom:55},
  xAxis:{type:'time'},yAxis:{type:'value'},
  series:fields.map(field=>({name:field,type:'line',connectNulls:false,showSymbol:true,
   data:history.map(([t,metrics])=>[t,metrics[field]??null])}))
 })
}
async function load(){
 const version=++requestVersion.value,group=active.value
 const dates=params()
 loading.value=true;loadError.value=false;overviewError.value=false
 rows.value=[];overview.value={};snapshotAt.value=''
 chart.value?.dispose();chart.value=undefined
 const api=group==='pools'?statisticsApi.poolsCurrent:
  (['centos','ros','lines'].includes(group)?()=>statisticsApi.resources(dates):
   group==='customers'?()=>statisticsApi.customers(dates):
   group==='products'?()=>statisticsApi.products(dates):
   ()=>statisticsApi.suppliers(dates))
 const [summary,result]=await Promise.allSettled([statisticsApi.overview(),api()])
 if(version!==requestVersion.value)return
 if(summary.status==='fulfilled')overview.value=summary.value.data.data||{}
 else overviewError.value=true
 if(result.status==='fulfilled'){
  rows.value=result.value.data.data||[]
  snapshotAt.value=group==='pools'?new Date().toISOString():''
 }else{
  loadError.value=true
  ElMessage.error('该统计分类加载失败，不能将读取失败当成零记录或零流量')
 }
 await nextTick()
 if(version===requestVersion.value){draw();loading.value=false}
}
async function recalculate(){
 calculating.value=true
 try{await statisticsApi.recalculate();ElMessage.success('已重算管理面小时统计，未接入数据面不能据此断言真实流量');await load()}
 finally{calculating.value=false}
}
watch(active,load);onMounted(load);onBeforeUnmount(()=>chart.value?.dispose())
</script>

<template>
<div class="page-stack">
 <el-card shadow="never">
  <div class="page-title"><div><h2>数据统计｜七类业务口径</h2><p>区别来源池与历史IP、管理面活跃与真实交付、无数据与数值为0。</p></div>
   <el-button v-if="auth.hasPermission('statistics:recalculate')" :loading="calculating" type="primary" @click="recalculate">重算管理面小时数据</el-button></div>
  <el-tabs v-model="active"><el-tab-pane v-for="t in tabs" :key="t.key" :label="t.label" :name="t.key"/></el-tabs>
  <el-alert :closable="false" type="info" :title="selected()?.meaning||''" show-icon/>
  <el-alert v-if="overviewError" type="warning" :closable="false" title="统计总览读取失败：顶部指标均为未知，不代表真实数值为0。" style="margin-top:12px"/>
  <el-alert v-if="loadError" type="error" :closable="false" title="当前分类统计读取失败，图表和记录均不可据此判断实际为0。" style="margin-top:12px"/>
  <div class="filter-row" style="margin-top:12px">
   <el-date-picker v-if="active!=='pools'" v-model="range" type="datetimerange" range-separator="至" start-placeholder="开始时间" end-placeholder="结束时间"/>
   <el-text v-else type="warning">资源池为当前成员快照，尚无逐池历史小时序列，日期范围不适用</el-text>
   <el-button type="primary" @click="load">刷新</el-button>
   <span style="margin-left:auto;color:#8a94a6">最近重算：{{fmt(overview?.lastCalculation?.started_at)}}</span>
  </div>
  <div class="metric-grid" style="margin-top:16px">
   <el-card shadow="never"><span>有效客户</span><strong>{{overview.active_customers??'—'}}</strong><small>主档ACTIVE</small></el-card>
   <el-card shadow="never"><span>业务 ACTIVE 服务</span><strong>{{overview.active_services??'—'}}</strong><small>不等于网络交付已核验</small></el-card>
   <el-card shadow="never"><span>今日提取返回IP</span><strong>{{overview.today_returned_ips??'—'}}</strong><small>IP提取日志，不是流量</small></el-card>
   <el-card shadow="never"><span>活动告警</span><strong>{{overview.active_alarms??'—'}}</strong><small>平台告警记录</small></el-card>
  </div>
 </el-card>
 <el-card v-if="active!=='pools'" shadow="never" v-loading="loading">
  <el-alert type="warning" :closable="false" title="图表按已入库的小时统计展示；缺少真实使用事件时留空，不以0伪装实际流量。资源快照不是历史设备原始采样。" style="margin-bottom:12px"/>
  <div ref="chartEl" style="height:320px"/>
 </el-card>
 <el-card shadow="never" v-loading="loading">
  <div style="margin-bottom:12px;display:flex;justify-content:space-between;gap:8px;align-items:center">
   <b>{{selected()?.label}}</b><el-text size="small">记录 {{rows.length}} 条；{{active==='pools'?'当前成员快照 '+fmt(snapshotAt):'小时快照/日志聚合'}}</el-text>
  </div>
  <el-table :data="rows" border stripe :empty-text="loadError?'统计读取失败':'该时段没有已生成的统计记录；不等于实际业务数值为0'">
   <template v-if="active==='centos'">
    <el-table-column label="快照小时" min-width="175"><template #default="s">{{fmt(s.row.stat_hour)}}</template></el-table-column>
    <el-table-column prop="centos_total" label="主机总数"/><el-table-column prop="centos_online" label="报在线主机"/>
    <el-table-column prop="b_prefix_count" label="B段 /16 去重"/><el-table-column prop="c_prefix_count" label="C段 /24 去重"/>
   </template>
   <template v-else-if="active==='ros'">
    <el-table-column label="快照小时" min-width="175"><template #default="s">{{fmt(s.row.stat_hour)}}</template></el-table-column>
    <el-table-column prop="ros_total" label="ROS数"/><el-table-column prop="ros_online" label="报在线ROS"/>
   </template>
   <template v-else-if="active==='lines'">
    <el-table-column label="快照小时" min-width="175"><template #default="s">{{fmt(s.row.stat_hour)}}</template></el-table-column>
    <el-table-column prop="line_total" label="线路数"/><el-table-column prop="line_online" label="报在线线路"/><el-table-column prop="line_abnormal" label="异常线路"/>
   </template>
   <template v-else-if="active==='pools'">
    <el-table-column prop="poolCode" label="来源池号" min-width="155"/>
    <el-table-column prop="poolName" label="池名称" min-width="160"/>
    <el-table-column label="省市/运营商" min-width="180"><template #default="s">{{[s.row.provinceCode,s.row.cityCode,s.row.carrierCode].filter(Boolean).join(' / ')||'未指定'}}</template></el-table-column>
    <el-table-column prop="lineCount" label="线路数"/><el-table-column prop="sharedLines" label="共享线路"/><el-table-column prop="longLines" label="长效线路"/>
    <el-table-column prop="onlineLines" label="报在线"/><el-table-column prop="currentDistinctIpv4" label="当前去重IPv4"/>
    <el-table-column prop="staleLines" label="采样超时/缺失"/>
    <el-table-column label="最近线路同步" min-width="175"><template #default="s">{{fmt(s.row.lastLineSyncAt)}}</template></el-table-column>
   </template>
   <template v-else-if="active==='customers'">
    <el-table-column label="小时" min-width="160"><template #default="s">{{fmt(s.row.stat_hour)}}</template></el-table-column>
    <el-table-column prop="customer_name" label="客户" min-width="140"/><el-table-column prop="service_no" label="服务编号" min-width="160"/>
    <el-table-column prop="extract_count" label="请求提取数"/><el-table-column prop="returned_ip_count" label="实际返回IP数"/>
    <el-table-column label="代理请求事件"><template #default="s">{{usageValue(s.row,'request_count')}}</template></el-table-column>
    <el-table-column label="上行字节"><template #default="s">{{usageValue(s.row,'upload_bytes')}}</template></el-table-column>
    <el-table-column label="下行字节"><template #default="s">{{usageValue(s.row,'download_bytes')}}</template></el-table-column>
    <el-table-column label="数据来源" min-width="190"><template #default="s">{{sourceState(s.row)}}</template></el-table-column>
   </template>
   <template v-else-if="active==='products'">
    <el-table-column label="小时" min-width="160"><template #default="s">{{fmt(s.row.stat_hour)}}</template></el-table-column>
    <el-table-column prop="product_name" label="产品" min-width="130"/><el-table-column prop="package_name" label="套餐" min-width="150"/>
    <el-table-column prop="active_service_count" label="业务ACTIVE数"/><el-table-column prop="extract_count" label="提取IP数"/>
    <el-table-column label="代理请求事件"><template #default="s">{{usageValue(s.row,'request_count')}}</template></el-table-column>
    <el-table-column label="数据来源" min-width="190"><template #default="s">{{sourceState(s.row)}}</template></el-table-column>
   </template>
   <template v-else>
    <el-table-column label="小时" min-width="160"><template #default="s">{{fmt(s.row.stat_hour)}}</template></el-table-column>
    <el-table-column prop="supplier_name" label="供应商" min-width="150"/><el-table-column prop="fetch_count" label="抓取记录"/>
    <el-table-column label="返回资源数"><template #default="s">{{usageValue(s.row,'received_resource_count')}}</template></el-table-column>
    <el-table-column label="平均耗时ms"><template #default="s">{{usageValue(s.row,'avg_elapsed_ms')}}</template></el-table-column>
    <el-table-column label="来源状态" min-width="210"><template #default="s">{{sourceState(s.row)}}</template></el-table-column>
   </template>
  </el-table>
 </el-card>
</div>
</template>
