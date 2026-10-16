<script setup lang="ts">
import { onMounted,ref,watch } from 'vue'
import { useRoute,useRouter } from 'vue-router'
import { ElMessage,ElMessageBox } from 'element-plus'
import { orderServiceApi } from '@/api/orderService'
import { http } from '@/api/http'
import { businessRelationsApi } from '@/api/businessRelations'
import { useAuthStore } from '@/stores/auth'
const route=useRoute(),router=useRouter(),auth=useAuthStore(),detail=ref<any>({}),credentials=ref<any[]>([]),bindingRows=ref<any[]>([]),bindingError=ref(false),bindingTruncated=ref(false),credentialError=ref(false),detailError=ref(false),loading=ref(false),requestVersion=ref(0),failoverPreview=ref<any>(null),previewBusy=ref(false),tab=ref('basic');let id=Number(route.params.id);const data=(r:any)=>r.data.data
async function load(){
 const version=++requestVersion.value,serviceId=id
 loading.value=true;detailError.value=false
 // Never leave the previous service's credentials, claim or actions on screen.
 detail.value={};credentials.value=[];bindingRows.value=[]
 bindingError.value=false;credentialError.value=false;bindingTruncated.value=false
 failoverPreview.value=null
 try{
  const response=await orderServiceApi.service(serviceId)
  if(version!==requestVersion.value)return
  detail.value=data(response)||{}
  const [cred,bindings]=await Promise.allSettled([
   auth.hasPermission('service:credential:read')?orderServiceApi.credentials(serviceId):Promise.resolve(null),
   businessRelationsApi.serviceBindings(serviceId)
  ])
  if(version!==requestVersion.value)return
  credentials.value=cred.status==='fulfilled'?(cred.value?.data?.data||[]):[]
  credentialError.value=auth.hasPermission('service:credential:read')&&cred.status==='rejected'
  bindingRows.value=bindings.status==='fulfilled'?(bindings.value.data.data.items||[]):[]
  bindingTruncated.value=bindings.status==='fulfilled'?!!bindings.value.data.data.truncated:false
  bindingError.value=bindings.status==='rejected'
 }catch{
  if(version===requestVersion.value){detailError.value=true;detail.value={};credentials.value=[];bindingRows.value=[]}
 }finally{if(version===requestVersion.value)loading.value=false}
}
async function previewFailover(candidate:any){
 const serviceId=id,version=requestVersion.value
 previewBusy.value=true;failoverPreview.value=null
 try{
  const r=await http.post(`/v12/services/${serviceId}/failover-preview`,{backupCandidateId:candidate.id})
  if(version===requestVersion.value)failoverPreview.value=r.data.data||{}
 }catch{ElMessage.error('人工切换预检失败；请核查候选归属和资源快照')}
 finally{previewBusy.value=false}
}
async function reset(){const {value}=await ElMessageBox.prompt('请输入重置原因','重置服务凭证',{inputValidator:v=>!!String(v||'').trim()||'必须填写原因'});const c=data(await orderServiceApi.resetCredential(id,String(value)));await ElMessageBox.alert(`类型：${c.type}\n账号：${c.account||'-'}\n新密钥：${c.secret}\n\n该密钥只在本次操作返回，请立即妥善保存。`,'一次性凭证',{confirmButtonText:'已保存'});load()}
async function addWhite(){const {value}=await ElMessageBox.prompt('请输入IPv4/IPv6地址','新增白名单');await orderServiceApi.addWhitelist(id,{ipAddress:String(value),remark:'后台添加'});ElMessage.success('已加入白名单');load()}
async function removeWhite(w:any){await orderServiceApi.removeWhitelist(id,w.id,'后台移除白名单');ElMessage.success('已停用');load()}
watch(()=>route.params.id,()=>{id=Number(route.params.id);tab.value='basic';load()})
onMounted(load)
</script>
<template><div class="page" v-loading="loading"><div class="head"><div><el-button link @click="router.push('/services')">← 返回服务列表</el-button><h2>{{detail.service_no||'服务详情'}}</h2><p>{{detail.customer_name}} · {{detail.product_name}} · {{detail.status}}</p></div></div><el-alert v-if="detailError" type="error" :closable="false" title="服务详情读取失败，无法核实当前服务状态、凭证或真实交付。"/><el-button v-if="detailError" @click="load">重新加载服务</el-button><el-card v-if="!detailError" shadow="never"><div style="display:flex;gap:12px;margin-bottom:12px"><el-button v-if="auth.hasPermission('service:change')&&(auth.hasPermission('service:renew')||auth.hasPermission('service:status'))" type="primary" @click="router.push({path:'/service-changes',query:{serviceId:id,action:auth.hasPermission('service:renew')?'renew':'suspend'}})">从此服务发起续费/变更</el-button><el-button v-if="auth.hasPermission('customer:read')" @click="router.push('/customers/'+detail.customer_id)">查看客户</el-button><el-button v-if="detail.order_id&&auth.hasPermission('order:read')" @click="router.push('/orders/'+detail.order_id)">订单详情</el-button><el-button @click="load">刷新</el-button></div><el-tabs v-model="tab"><el-tab-pane label="基本信息" name="basic"><el-descriptions :column="3" border><el-descriptions-item label="客户"><el-button v-if="auth.hasPermission('customer:read')" link type="primary" @click="router.push('/customers/'+detail.customer_id)">{{detail.customer_name}}</el-button><span v-else>{{detail.customer_name}}</span></el-descriptions-item><el-descriptions-item label="产品">{{detail.product_name}}</el-descriptions-item><el-descriptions-item label="产品类型">{{detail.product_type}}</el-descriptions-item><el-descriptions-item label="订单"><el-button v-if="detail.order_id&&auth.hasPermission('order:read')" link type="primary" @click="router.push('/orders/'+detail.order_id)">{{detail.order_no}}</el-button><span v-else>{{detail.order_no||'授权直接开通'}}</span></el-descriptions-item><el-descriptions-item label="套餐">{{detail.package_name||'-'}}</el-descriptions-item><el-descriptions-item label="交付方式"><el-tag>{{detail.delivery_mode==='EXTERNAL_PROXY'?'外部反向代理':'自有资源'}}</el-tag></el-descriptions-item><el-descriptions-item label="代码策略">{{detail.builtin_policy_code||'-'}}</el-descriptions-item><el-descriptions-item label="生效时间">{{detail.effective_at}}</el-descriptions-item><el-descriptions-item label="到期时间">{{detail.expire_at}}</el-descriptions-item><el-descriptions-item label="宽限天数快照">{{detail.grace_days_snapshot??'不适用/未配置'}}</el-descriptions-item><el-descriptions-item label="宽限截止">{{detail.grace_until_at||'—'}}</el-descriptions-item><el-descriptions-item label="到期处理阶段">{{detail.expiry_phase||'NONE'}}</el-descriptions-item><el-descriptions-item label="真实交付状态"><el-tag :type="detail.delivery_stage==='VERIFIED'?'success':'warning'">{{detail.delivery_stage||'UNVERIFIED'}}</el-tag></el-descriptions-item><el-descriptions-item label="最后真实验证">{{detail.last_delivery_verified_at||'未验证'}}</el-descriptions-item><template v-if="detail.delivery_mode==='EXTERNAL_PROXY'"><el-descriptions-item label="外部采购"><el-button v-if="detail.supplier_purchase_id&&auth.hasPermission('resource:purchase:read')" link type="primary" @click="router.push('/supplier-purchases/'+detail.supplier_purchase_id)">{{detail.purchase_no||'查看采购'}}</el-button><span v-else>{{detail.purchase_no||'—'}}</span></el-descriptions-item><el-descriptions-item label="反向代理"><el-button v-if="detail.reverse_proxy_id&&auth.hasPermission('resource:reverse-proxy:read')" link type="primary" @click="router.push('/reverse-proxies/'+detail.reverse_proxy_id)">{{detail.proxy_no||'查看代理'}}</el-button><span v-else>{{detail.proxy_no||'—'}}</span></el-descriptions-item><el-descriptions-item label="平台代理入口">{{detail.platform_host}}<span v-if="detail.platform_port">:{{detail.platform_port}}</span></el-descriptions-item></template></el-descriptions></el-tab-pane>
<el-tab-pane label="资源/交付绑定" name="resources">
 <el-alert type="info" :closable="false" title="资源名称从真实业务表关联，不用动态当前IP反推固定出口；管理面绑定ACTIVE不代表网络执行已经验证。"/>
 <el-alert v-if="bindingError" type="warning" :closable="false" title="资源业务关联查询失败，暂不能据此判定此服务没有绑定。"/>
 <el-alert v-if="bindingTruncated" type="info" :closable="false" title="仅显示最近200条资源绑定记录。"/>
 <el-table :data="bindingError?(detail.resourceBindings||[]):bindingRows" style="margin-top:12px" border stripe>
  <el-table-column prop="resource_type" label="资源类型" min-width="105"/>
  <el-table-column label="业务对象" min-width="210"><template #default="s">
   <el-button v-if="s.row.related_path" link type="primary" @click="router.push(s.row.related_path)">{{s.row.display_name||'关联对象'}}</el-button>
   <span v-else>{{s.row.display_name||('历史对象 #'+s.row.resource_id)}}</span>
  </template></el-table-column>
  <el-table-column prop="binding_role" label="作用" width="110"/>
  <el-table-column prop="status" label="管理面状态" min-width="115"/>
  <el-table-column prop="effective_at" label="生效时间" min-width="160"/>
  <el-table-column prop="released_at" label="解除时间" min-width="160"/>
 </el-table>
 </el-tab-pane>
<el-tab-pane label="出口IPv4授权" name="egress"><el-alert title="合同固定公网IPv4与线路当前IP不同；独享跨客户按公网IPv4排他。未验证的出口不能宣称已交付。" type="warning" :closable="false"/><el-table :data="detail.egressClaims||[]"><el-table-column prop="egress_ipv4" label="固定出口IPv4"/><el-table-column prop="claim_mode" label="共享/独享"/><el-table-column prop="claim_status" label="占用状态"/><el-table-column prop="source_line_id" label="来源线路"/><el-table-column prop="hold_until_at" label="保留截至"/><el-table-column prop="verified_at" label="真实验证"/></el-table></el-tab-pane><el-tab-pane label="人工备用候选" name="backups">
 <el-alert type="warning" :closable="false"
  title="仅服务级人工切换；当前真实资源API及产品切换/回退接口未签收，预检只读且任何候选都不可提交执行。"/>
 <el-table :data="detail.backupCandidates||[]" border stripe style="margin-top:12px">
  <el-table-column prop="resource_code" label="备用家宽/线路" min-width="160"/>
  <el-table-column prop="expected_ipv4" label="候选公网IPv4" min-width="145"/>
  <el-table-column prop="candidate_status" label="候选状态" width="110"/>
  <el-table-column prop="health_status" label="健康" width="90"/>
  <el-table-column prop="last_checked_at" label="最后检测" min-width="165"/>
  <el-table-column label="操作" width="155"><template #default="s">
   <el-button :loading="previewBusy" link type="primary" @click="previewFailover(s.row)">预检影响与冲突</el-button>
  </template></el-table-column>
 </el-table>
 <el-card v-if="failoverPreview" shadow="never" style="margin-top:16px">
  <template #header><b>人工切换影响预检（禁止直接执行）</b></template>
  <el-descriptions :column="2" border>
   <el-descriptions-item label="原合同IPv4">{{failoverPreview.fromIpv4||'缺快照待核对'}}</el-descriptions-item>
   <el-descriptions-item label="候选预期IPv4">{{failoverPreview.toIpv4||'未知'}}</el-descriptions-item>
   <el-descriptions-item label="候选线路当前上报IPv4">{{failoverPreview.candidateReportedIpv4||'未知'}}</el-descriptions-item>
   <el-descriptions-item label="原线路">{{failoverPreview.sourceLineCode||'待核对'}}</el-descriptions-item>
   <el-descriptions-item label="候选线路">{{failoverPreview.candidateLineCode||'未知'}}</el-descriptions-item>
   <el-descriptions-item label="原有平台代理入口">{{failoverPreview.currentPlatformEntry||'非反向代理/待核对'}}</el-descriptions-item>
   <el-descriptions-item label="地区变化">{{failoverPreview.sameRegion==null?'未知':failoverPreview.sameRegion?'同地区':'跨地区'}}</el-descriptions-item>
   <el-descriptions-item label="运营商变化">{{failoverPreview.sameCarrier==null?'未知':failoverPreview.sameCarrier?'同运营商':'不同运营商'}}</el-descriptions-item>
   <el-descriptions-item label="IPv4占用冲突">{{failoverPreview.ipv4Conflict?'存在冲突':'本次管理面未发现；仍须真实校验'}}</el-descriptions-item>
   <el-descriptions-item label="客户事前同意">{{failoverPreview.customerConsentRequired?'必须提供，当前无签收':'暂未由预检判定必须'}}</el-descriptions-item>
   <el-descriptions-item label="备用检测时间">{{failoverPreview.candidateLastCheckedAt||'无有效检测'}}</el-descriptions-item>
   <el-descriptions-item label="线路最近同步">{{failoverPreview.candidateLastSyncAt||'无来源时间'}}</el-descriptions-item>
  </el-descriptions>
  <el-alert type="warning" :closable="false" style="margin-top:12px"
    title="下列阻断原因必须全部处理，并取得真实切换回执和客户同意，才可能进入未来的执行流程。"/>
  <ul><li v-for="reason in failoverPreview.blockers||[]" :key="reason">{{reason}}</li></ul>
  <el-text type="info">{{failoverPreview.monitoringImpact}}</el-text>
  <el-button disabled block style="margin-top:12px">真实切换不可用（等待技术组联调）</el-button>
 </el-card>
</el-tab-pane><el-tab-pane label="执行任务" name="tasks"><el-table :data="detail.actionTasks||[]"><el-table-column prop="operation_key" label="操作ID" width="180"/><el-table-column prop="action_type" label="动作"/><el-table-column prop="task_status" label="任务状态"/><el-table-column prop="stage" label="真实阶段"/><el-table-column prop="attempt_count" label="尝试次数"/><el-table-column prop="last_error_message" label="异常原因"/></el-table></el-tab-pane><el-tab-pane v-if="auth.hasPermission('service:credential:read')" label="服务凭证" name="credential"><div class="actions"><el-button v-if="auth.hasPermission('service:credential:reset')" type="primary" @click="reset">重置凭证</el-button></div><el-alert type="info" :closable="false" title="历史页面只展示掩码；新密钥只在创建/重置成功时返回一次。"/><el-alert v-if="credentialError" type="error" :closable="false" title="凭证查询失败或权限已变化，不能据此认定没有服务凭证。"/><el-table :data="credentials" :empty-text="credentialError?'凭证读取失败':'暂无服务凭证'" style="margin-top:12px"><el-table-column prop="credential_type" label="类型"/><el-table-column prop="account_name" label="账号"/><el-table-column prop="secret_mask" label="密钥掩码"/><el-table-column prop="status" label="状态"/><el-table-column prop="expire_at" label="到期时间"/></el-table></el-tab-pane>
<el-tab-pane label="白名单" name="whitelist"><div class="actions"><el-button v-if="auth.hasPermission('service:whitelist')" type="primary" @click="addWhite">新增白名单</el-button></div><el-table :data="detail.whitelist||[]"><el-table-column prop="ip_address" label="IP地址"/><el-table-column prop="status" label="状态"/><el-table-column prop="remark" label="备注"/><el-table-column label="操作"><template #default="s"><el-button v-if="auth.hasPermission('service:whitelist')&&s.row.status==='ACTIVE'" link type="danger" @click="removeWhite(s.row)">停用</el-button></template></el-table-column></el-table></el-tab-pane>
<el-tab-pane v-if="auth.hasPermission('service:change:read')" label="变更记录" name="changes"><el-table :data="detail.changes||[]"><el-table-column prop="change_no" label="变更号"/><el-table-column prop="change_type" label="类型"/><el-table-column prop="status" label="状态"/><el-table-column prop="reason" label="原因"/><el-table-column prop="created_at" label="时间"/></el-table></el-tab-pane>
<el-tab-pane v-if="auth.hasPermission('long-monitor:read')" label="长效监控" name="long"><el-alert v-if="detail.product_type!=='LONG_IP'" type="info" :closable="false" title="仅长效IP服务需要创建客户专项监控。"/><el-table v-else :data="detail.longMonitors||[]"><el-table-column prop="monitor_no" label="监控编号"/><el-table-column label="目标"><template #default="s">{{s.row.target_host}}:{{s.row.target_port}}</template></el-table-column><el-table-column prop="current_status" label="状态"/><el-table-column prop="last_checked_at" label="最近检测"/><el-table-column label="操作"><template #default="s"><el-button link type="primary" @click="router.push(`/long-monitor/monitors/${s.row.id}`)">查看</el-button></template></el-table-column></el-table></el-tab-pane>
<el-tab-pane v-if="auth.hasPermission('service:release')" label="释放记录" name="release"><el-table :data="detail.releaseRecords||[]"><el-table-column prop="release_no" label="释放编号"/><el-table-column prop="trigger_type" label="触发方式"/><el-table-column prop="status" label="状态"/><el-table-column prop="created_at" label="时间"/><el-table-column prop="error_message" label="错误"/></el-table></el-tab-pane></el-tabs></el-card></div></template>
<style scoped>.page{display:grid;gap:16px}.head h2{margin:8px 0}.head p{margin:0;color:#7b8494}.actions{margin-bottom:12px}</style>
