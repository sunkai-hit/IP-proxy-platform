<script setup lang="ts">
import { computed,onMounted,reactive,ref,watch } from 'vue'
import { useRoute,useRouter } from 'vue-router'
import { ElMessage,ElMessageBox } from 'element-plus'
import { v12ResourceApi } from '@/api/v12Resource'
import { businessRelationsApi } from '@/api/businessRelations'
import { useAuthStore } from '@/stores/auth'
const route=useRoute(),router=useRouter(),auth=useAuthStore(),poolId=ref(Number(route.params.id))
const detail=ref<any>({}),detailLoading=ref(false),detailError=ref(false)
const rows=ref<any[]>([]),total=ref(0),loading=ref(false),lineError=ref(false)
const activeTab=ref('ALL'),selected=ref<any[]>([]),operations=ref<any[]>([])
const poolServices=ref<any[]>([]),poolServicesTruncated=ref(false),usageLoadError=ref(false)
const operationLoadError=ref(false),operationLoading=ref(false),saving=ref(false)
const q=reactive({page:1,size:20,keyword:'',status:''})
const addDialog=ref(false),candidateRows=ref<any[]>([]),candidateTotal=ref(0)
const candidateLoading=ref(false),candidateError=ref(false),candidateSelected=ref<any[]>([])
const cq=reactive({page:1,size:20,keyword:'',type:''})
const canWrite=computed(()=>auth.hasPermission('resource:pool:write'))
const poolReady=computed(()=>!!detail.value.pool_code&&!detailError.value&&!detailLoading.value)
const data=(r:any)=>r.data.data
const metric=(v:any)=>v===undefined||v===null?'—':v
let detailVersion=0,lineVersion=0,operationVersion=0,usageVersion=0,candidateVersion=0
function lineTypeLabel(v:any){return v==='SHARED'?'共享':v==='LONG'?'长效':'未设置'}
function statusType(v:any){return v==='ONLINE'?'success':v==='ABNORMAL'?'warning':v==='OFFLINE'?'danger':'info'}
async function loadDetail(){
 const version=++detailVersion,id=poolId.value
 detailLoading.value=true;detailError.value=false;detail.value={}
 try{
  const d=data(await v12ResourceApi.pool(id))
  if(version===detailVersion&&id===poolId.value)detail.value=d||{}
 }catch{
  if(version===detailVersion&&id===poolId.value){detailError.value=true;detail.value={}}
 }finally{if(version===detailVersion)detailLoading.value=false}
}
async function loadLines(){
 if(!['ALL','SHARED','LONG'].includes(activeTab.value))return
 const version=++lineVersion,id=poolId.value,type=activeTab.value==='ALL'?'':activeTab.value
 loading.value=true;lineError.value=false;rows.value=[];total.value=0;selected.value=[]
 try{
  const d=data(await v12ResourceApi.poolLines(id,{...q,type}))||{}
  if(version===lineVersion&&id===poolId.value){rows.value=d.items||[];total.value=d.total??0}
 }catch{
  if(version===lineVersion&&id===poolId.value){lineError.value=true;rows.value=[];total.value=0}
 }finally{if(version===lineVersion)loading.value=false}
}
async function loadOperations(){
 const version=++operationVersion,id=poolId.value
 operationLoading.value=true;operationLoadError.value=false;operations.value=[]
 try{
  const d=data(await v12ResourceApi.poolOperations(id,200))
  if(version===operationVersion&&id===poolId.value)operations.value=d||[]
 }catch{
  if(version===operationVersion&&id===poolId.value){operations.value=[];operationLoadError.value=true}
 }finally{if(version===operationVersion)operationLoading.value=false}
}
async function loadPoolServices(){
 const version=++usageVersion,id=poolId.value
 poolServices.value=[];usageLoadError.value=false;poolServicesTruncated.value=false
 if(!auth.hasPermission('service:read'))return
 try{
  const d=data(await businessRelationsApi.poolServices(id))||{}
  if(version===usageVersion&&id===poolId.value){poolServices.value=d.items||[];poolServicesTruncated.value=!!d.truncated}
 }catch{
  if(version===usageVersion&&id===poolId.value){poolServices.value=[];usageLoadError.value=true}
 }
}
async function loadAll(){await Promise.all([loadDetail(),loadOperations(),loadPoolServices(),loadLines()])}
function resetFilter(){Object.assign(q,{page:1,size:20,keyword:'',status:''});void loadLines()}
async function openAdd(){
 if(!poolReady.value||!canWrite.value||saving.value)return
 addDialog.value=true;candidateRows.value=[];candidateSelected.value=[]
 Object.assign(cq,{page:1,size:20,keyword:'',type:''})
 await loadCandidates()
}
async function loadCandidates(){
 if(!addDialog.value)return
 const version=++candidateVersion,id=poolId.value
 candidateLoading.value=true;candidateError.value=false
 candidateRows.value=[];candidateTotal.value=0;candidateSelected.value=[]
 try{
  const d=data(await v12ResourceApi.candidatePoolLines(id,{...cq}))||{}
  if(version===candidateVersion&&id===poolId.value&&addDialog.value){
   candidateRows.value=d.items||[];candidateTotal.value=d.total??0
  }
 }catch{
  if(version===candidateVersion&&id===poolId.value){candidateError.value=true;candidateRows.value=[]}
 }finally{if(version===candidateVersion)candidateLoading.value=false}
}
async function addLines(){
 if(!canWrite.value||!poolReady.value||saving.value||candidateError.value||candidateLoading.value||!candidateSelected.value.length)return
 const id=poolId.value,version=candidateVersion,ids=candidateSelected.value.map((x:any)=>Number(x.id))
 try{
  const {value}=await ElMessageBox.prompt('请输入加入资源池的原因','加入线路',{inputValidator:v=>!!String(v||'').trim()||'必须填写原因'})
  if(id!==poolId.value||!poolReady.value||!addDialog.value||version!==candidateVersion||candidateError.value){
   ElMessage.warning('当前资源池或候选线路已变化，请重新核对');return
  }
  saving.value=true
  await v12ResourceApi.addPoolLines(id,ids,String(value).trim())
  ElMessage.success('线路已加入资源池');addDialog.value=false
  await loadAll()
 }catch(e:any){
  if(e!=='cancel'&&e!=='close')ElMessage.error('加入线路未确认成功，请重新查询后核对')
 }finally{saving.value=false}
}
async function removeLines(items:any[]){
 if(!canWrite.value||!poolReady.value||saving.value||loading.value||lineError.value||!items.length)return
 const id=poolId.value,version=lineVersion,ids=items.map((x:any)=>Number(x.id))
 try{
  const {value}=await ElMessageBox.prompt(`确认移出 ${ids.length} 条线路。若承载未释放固定出口或会清空在用服务的来源池，后端将拒绝本操作；请填写原因`,'移出线路',{inputValidator:v=>!!String(v||'').trim()||'必须填写原因'})
  if(id!==poolId.value||version!==lineVersion||!poolReady.value||lineError.value||
     !ids.every(x=>rows.value.some((r:any)=>Number(r.id)===x))){
   ElMessage.warning('当前资源池或线路成员已变化，请重新核对');return
  }
  saving.value=true
  await v12ResourceApi.removePoolLines(id,ids,String(value).trim())
  ElMessage.success('线路已移出资源池');await loadAll()
 }catch(e:any){
  if(e!=='cancel'&&e!=='close')ElMessage.error('移出线路未确认成功，请重新查询后核对')
 }finally{saving.value=false}
}
watch(activeTab,async()=>{
 q.page=1
 if(activeTab.value==='OPERATIONS')await loadOperations()
 else if(activeTab.value==='USAGE')await loadPoolServices()
 else await loadLines()
})
watch(()=>route.params.id,()=>{
 poolId.value=Number(route.params.id)
 ++detailVersion;++lineVersion;++operationVersion;++usageVersion;++candidateVersion
 activeTab.value='ALL';addDialog.value=false;detail.value={};rows.value=[]
 operations.value=[];poolServices.value=[];candidateRows.value=[]
 detailError.value=false;lineError.value=false;selected.value=[]
 void loadAll()
})
onMounted(loadAll)
</script>
<template><div class="page"><div class="head"><div><el-button link @click="router.back()">← 返回资源池</el-button><h2>{{detail.pool_name||'资源池详情'}}</h2><p>资源池按来源属性组织自有家宽线路；成员线路仅允许“共享 / 长效”，同一线路可属于多个符合来源属性的资源池。</p></div><el-button @click="loadAll">刷新</el-button></div>
<el-alert v-if="detailError" type="error" :closable="false" title="资源池详情读取失败，来源、线路数和服务占用均未知，不能依据旧池资料操作。"/><el-button v-if="detailError" @click="loadAll">重新加载资源池详情</el-button>
<el-card v-if="!detailError" shadow="never"><el-descriptions :column="4" border><el-descriptions-item label="资源池编号">{{detail.pool_code||'—'}}</el-descriptions-item><el-descriptions-item label="资源池名称">{{detail.pool_name||'—'}}</el-descriptions-item><el-descriptions-item label="省/市">{{detail.province_code||'—'}} / {{detail.city_code||'—'}}</el-descriptions-item><el-descriptions-item label="运营商">{{detail.carrier_code||'—'}}</el-descriptions-item><el-descriptions-item label="状态">{{detail.status||'—'}}</el-descriptions-item><el-descriptions-item label="说明" :span="3">{{detail.purpose||'—'}}</el-descriptions-item></el-descriptions></el-card>
<div class="metric-grid"><el-card shadow="never"><span>ROS数量</span><strong>{{metric(detail.ros_count)}}</strong></el-card><el-card shadow="never"><span>线路总数</span><strong>{{metric(detail.line_count)}}</strong></el-card><el-card shadow="never"><span>共享线路</span><strong>{{metric(detail.shared_line_count)}}</strong></el-card><el-card shadow="never"><span>长效线路</span><strong>{{metric(detail.long_line_count)}}</strong></el-card><el-card shadow="never"><span>在线 / 异常</span><strong>{{metric(detail.online_line_count)}} / {{metric(detail.abnormal_line_count)}}</strong></el-card><el-card shadow="never"><span>当前IP / 去重IP</span><strong>{{metric(detail.current_ip_count)}} / {{metric(detail.unique_ip_count)}}</strong></el-card><el-card shadow="never"><span>B段数量</span><strong>{{metric(detail.b_prefix_count)}}</strong></el-card><el-card shadow="never"><span>C段数量</span><strong>{{metric(detail.c_prefix_count)}}</strong></el-card></div>
<el-card shadow="never"><el-tabs v-model="activeTab"><el-tab-pane label="全部线路" name="ALL"/><el-tab-pane label="共享线路" name="SHARED"/><el-tab-pane label="长效线路" name="LONG"/><el-tab-pane label="使用情况" name="USAGE"/><el-tab-pane label="变更记录" name="OPERATIONS"/></el-tabs>
<template v-if="['ALL','SHARED','LONG'].includes(activeTab)"><el-alert v-if="lineError" type="error" :closable="false" title="资源池线路成员读取失败，不能据此认为没有可用线路或在用成员。"/><el-button v-if="lineError" @click="loadLines">重新加载线路成员</el-button><div class="toolbar"><el-input v-model="q.keyword" placeholder="线路编号/别名/IP" clearable style="width:240px" @keyup.enter="q.page=1;loadLines()"/><el-select v-model="q.status" placeholder="状态" clearable style="width:130px"><el-option label="在线" value="ONLINE"/><el-option label="异常" value="ABNORMAL"/><el-option label="离线" value="OFFLINE"/></el-select><el-button type="primary" @click="q.page=1;loadLines()">查询</el-button><el-button @click="resetFilter">重置</el-button><span class="spacer"/><el-button v-if="canWrite" type="primary" :disabled="!poolReady||saving" @click="openAdd">添加线路</el-button><el-button v-if="canWrite" :disabled="!selected.length||!poolReady||lineError||saving" type="danger" plain @click="removeLines(selected)">批量移出</el-button></div><el-table :data="rows" v-loading="loading" stripe border @selection-change="selected=$event"><el-table-column v-if="canWrite" type="selection" width="46"/><el-table-column prop="resource_code" label="线路编号" width="145"/><el-table-column prop="line_alias" label="线路别名" min-width="130"/><el-table-column prop="line_type" label="线路类型" width="90"><template #default="s"><el-tag :type="s.row.line_type==='LONG'?'warning':'info'">{{lineTypeLabel(s.row.line_type)}}</el-tag></template></el-table-column><el-table-column prop="ros_code" label="ROS" width="130"/><el-table-column prop="current_public_ip" label="当前IP" width="135"/><el-table-column prop="province_code" label="省" width="85"/><el-table-column prop="city_code" label="市" width="85"/><el-table-column prop="carrier_code" label="运营商" width="90"/><el-table-column label="状态" width="90"><template #default="s"><el-tag :type="statusType(s.row.online_status)">{{s.row.online_status}}</el-tag></template></el-table-column><el-table-column prop="latency_ms" label="延迟ms" width="80"/><el-table-column prop="bas_name" label="BAS/BRAS" min-width="130"/><el-table-column prop="joined_at" label="加入时间" min-width="165"/><el-table-column fixed="right" label="操作" width="130"><template #default="s"><el-button link type="primary" @click="router.push(`/resources/lines/${s.row.id}`)">线路详情</el-button><el-button v-if="canWrite" link type="danger" :disabled="!poolReady||lineError||saving" @click="removeLines([s.row])">移出</el-button></template></el-table-column></el-table><div class="pager"><el-pagination v-model:current-page="q.page" v-model:page-size="q.size" :total="total" layout="total,sizes,prev,pager,next" @change="loadLines"/></div></template>
<template v-else-if="activeTab==='USAGE'"><el-descriptions :column="3" border><el-descriptions-item label="当前服务数">{{metric(detail.usage?.service_count)}}</el-descriptions-item><el-descriptions-item label="当前客户数">{{metric(detail.usage?.customer_count)}}</el-descriptions-item><el-descriptions-item label="使用产品类型">{{detail.usage?.product_types||'—'}}</el-descriptions-item></el-descriptions><el-alert class="mt" type="info" :closable="false" title="这里仅展示直接绑定此资源池的在用服务，不把成员线路历史、动态IP或其他资源池的服务归属猜测为本池的真实交付。"/>
 <el-alert v-if="usageLoadError" type="warning" :closable="false" title="服务关系查询失败，不能据此认为资源池没有在用服务。"/>
 <el-alert v-if="poolServicesTruncated" type="info" :closable="false" title="仅显示最近200条关联绑定；更多请进入服务管理筛选。"/>
 <el-table v-if="auth.hasPermission('service:read')" :data="poolServices" border stripe :empty-text="usageLoadError?'关联数据读取失败，不能判定为空':'当前没有直接ACTIVE资源池绑定'">
  <el-table-column prop="service_no" label="服务编号" min-width="170"/>
  <el-table-column prop="customer_name" label="客户" min-width="140"/>
  <el-table-column prop="product_name" label="产品" min-width="130"/>
  <el-table-column prop="product_type" label="类型" width="110"/>
  <el-table-column prop="status" label="业务状态" width="110"/>
  <el-table-column prop="delivery_stage" label="交付核验" min-width="125"/>
  <el-table-column prop="binding_role" label="绑定作用" width="100"/>
  <el-table-column prop="expire_at" label="到期" min-width="165"/>
  <el-table-column label="操作" width="105"><template #default="s"><el-button link type="primary" @click="router.push('/services/'+s.row.id)">服务详情</el-button></template></el-table-column>
 </el-table>
 <el-alert v-else type="info" :closable="false" title="当前账号没有服务查看权限，仅展示资源池使用汇总。"/></template>
<template v-else><el-alert v-if="operationLoadError" class="mt" type="error" :closable="false" title="变更记录查询失败或无权限，不能据此认为没有变更记录。"/><el-button v-if="operationLoadError" class="mt" @click="loadOperations">重新加载变更记录</el-button><el-table :data="operations" v-loading="operationLoading" :empty-text="operationLoadError?'变更记录加载失败':'暂无变更记录'" stripe border><el-table-column prop="created_at" label="时间" min-width="165"/><el-table-column prop="operator_name" label="操作人" width="120"/><el-table-column prop="operation" label="操作" width="150"/><el-table-column prop="reason" label="原因" min-width="220"/><el-table-column prop="result" label="结果" width="90"/><el-table-column prop="error_message" label="错误信息" min-width="180"/></el-table></template></el-card>
<el-dialog v-model="addDialog" title="添加线路成员" width="980px"><el-alert type="info" :closable="false" title="这里只显示共享/长效线路，并自动按当前资源池的省/市/运营商来源属性筛选。"/><div class="toolbar mt"><el-input v-model="cq.keyword" placeholder="线路编号/别名/IP" clearable style="width:240px"/><el-select v-model="cq.type" placeholder="线路类型" clearable style="width:140px"><el-option label="共享" value="SHARED"/><el-option label="长效" value="LONG"/></el-select><el-button type="primary" @click="cq.page=1;loadCandidates()">查询</el-button></div><el-alert v-if="candidateError" type="error" :closable="false" title="候选线路查询失败，不能加入未核实的成员。"/><el-button v-if="candidateError" @click="loadCandidates">重新加载候选线路</el-button><el-table :data="candidateRows" v-loading="candidateLoading" stripe border @selection-change="candidateSelected=$event"><el-table-column type="selection" width="46"/><el-table-column prop="resource_code" label="线路编号" width="145"/><el-table-column prop="line_alias" label="线路别名"/><el-table-column prop="line_type" label="类型" width="90"><template #default="s">{{lineTypeLabel(s.row.line_type)}}</template></el-table-column><el-table-column prop="ros_code" label="ROS"/><el-table-column prop="current_public_ip" label="当前IP"/><el-table-column prop="province_code" label="省"/><el-table-column prop="city_code" label="市"/><el-table-column prop="carrier_code" label="运营商"/><el-table-column prop="online_status" label="状态"/></el-table><div class="pager"><el-pagination v-model:current-page="cq.page" v-model:page-size="cq.size" :total="candidateTotal" layout="total,prev,pager,next" @change="loadCandidates"/></div><template #footer><el-button @click="addDialog=false">取消</el-button><el-button type="primary" :disabled="!candidateSelected.length||!poolReady||candidateError||candidateLoading||saving" :loading="saving" @click="addLines">加入资源池</el-button></template></el-dialog></div></template>
<style scoped>.page{display:grid;gap:16px}.head{display:flex;justify-content:space-between}.head h2{margin:8px 0 6px}.head p{margin:0;color:#7b8494;max-width:980px}.metric-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px}.metric-grid span{display:block;color:#7b8494;font-size:13px}.metric-grid strong{display:block;margin-top:8px;font-size:24px;color:#1677ff}.toolbar{display:flex;gap:10px;align-items:center;flex-wrap:wrap;margin:4px 0 14px}.spacer{flex:1}.pager{display:flex;justify-content:flex-end;margin-top:14px}.mt{margin-top:14px}@media(max-width:1200px){.metric-grid{grid-template-columns:repeat(2,1fr)}}</style>