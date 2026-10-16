<script setup lang="ts">
import{computed,onMounted,ref,watch}from'vue'
import{useRoute,useRouter}from'vue-router'
import{ElMessage,ElMessageBox}from'element-plus'
import{v12ResourceApi}from'@/api/v12Resource'
import{businessRelationsApi}from'@/api/businessRelations'
import{useAuthStore}from'@/stores/auth'
const route=useRoute(),router=useRouter(),auth=useAuthStore(),data=ref<any>({}),loading=ref(false),ipHistory=ref<any[]>([]),operations=ref<any[]>([]),memberPools=ref<any[]>([]),relatedServices=ref<any[]>([]),relationWarnings=ref<string[]>([]),detailError=ref(false),requestVersion=ref(0)
const type=computed(()=>String(route.meta.resourceType||'CENTOS')),id=computed(()=>Number(route.params.id))
async function load(){
 const version=++requestVersion.value
 const resourceType=type.value,resourceId=id.value
 loading.value=true;detailError.value=false;data.value={}
 ipHistory.value=[];operations.value=[];memberPools.value=[];relatedServices.value=[];relationWarnings.value=[]
 try{
  if(resourceType==='CENTOS'){
   const r=await v12ResourceApi.centosDetail(resourceId)
   if(version===requestVersion.value)data.value=r.data.data
  }else if(resourceType==='ROS'){
   const r=await v12ResourceApi.rosDetail(resourceId)
   if(version===requestVersion.value)data.value=r.data.data
  }else{
   const r=await v12ResourceApi.lineDetail(resourceId)
   if(version!==requestVersion.value)return
   data.value=r.data.data
   const [h,o,pl,sv]=await Promise.allSettled([
    v12ResourceApi.lineIpHistory(resourceId),v12ResourceApi.lineOperations(resourceId),
    auth.hasPermission('resource:pool:read')?businessRelationsApi.linePools(resourceId):Promise.resolve(null),
    auth.hasPermission('service:read')?businessRelationsApi.lineServices(resourceId):Promise.resolve(null)
   ])
   if(version!==requestVersion.value)return
   ipHistory.value=h.status==='fulfilled'?(h.value.data.data||[]):[]
   operations.value=o.status==='fulfilled'?(o.value.data.data||[]):[]
   memberPools.value=pl.status==='fulfilled'?(pl.value?.data?.data?.items||[]):[]
   relatedServices.value=sv.status==='fulfilled'?(sv.value?.data?.data?.items||[]):[]
   if(h.status==='rejected')relationWarnings.value.push('IP变化历史读取失败')
   if(o.status==='rejected')relationWarnings.value.push('运维操作日志读取失败')
   if(pl.status==='rejected')relationWarnings.value.push('资源池成员关系暂不可用')
   if(sv.status==='rejected')relationWarnings.value.push('关联服务详情暂不可用')
  }
 }catch{
  if(version===requestVersion.value){detailError.value=true;data.value={}}
 }finally{if(version===requestVersion.value)loading.value=false}
}
async function setLineType(){const current=data.value.line_type||'';const {value}=await ElMessageBox.prompt('请输入线路类型：SHARED（共享）或 LONG（长效）','设置线路类型',{inputValue:current,inputPattern:/^(SHARED|LONG)$/i,inputErrorMessage:'仅支持 SHARED 或 LONG'});const {value:reason}=await ElMessageBox.prompt('请输入修改原因','设置线路类型',{inputValidator:v=>!!String(v||'').trim()||'必须填写原因'});await v12ResourceApi.setLineType(id.value,String(value).toUpperCase() as 'SHARED'|'LONG',String(reason));ElMessage.success('线路类型已更新');load()}
function fmt(v:any){return v===null||v===undefined||v===''?'—':String(v)}
function lineTypeLabel(v:any){return v==='SHARED'?'共享':v==='LONG'?'长效':'未设置'}
watch(()=>route.fullPath,load)
onMounted(load)
</script>
<template><div class="page-stack" v-loading="loading"><div class="page-head"><div><el-button link @click="router.back()">← 返回列表</el-button><h1>{{type==='CENTOS'?'CentOS详情':type==='ROS'?'ROS详情':'家宽/线路详情'}}</h1><p>{{data.resource_code||data.resource_name}}</p></div><div v-if="type==='LINE'" class="actions"><el-button v-if="auth.hasPermission('resource:line:operate')" type="primary" @click="setLineType">设置线路类型</el-button></div></div>

<el-alert v-if="detailError" type="error" :closable="false" title="资源详情读取失败，无法确认当前设备或线路状态。请重新加载。"/><el-button v-if="detailError" @click="load">重试加载详情</el-button>
<template v-if="!detailError&&type==='CENTOS'"><el-card shadow="never"><template #header><b>基础与运行信息</b></template><el-descriptions :column="4" border><el-descriptions-item label="主机ID">{{fmt(data.resource_code)}}</el-descriptions-item><el-descriptions-item label="主机名称">{{fmt(data.resource_name)}}</el-descriptions-item><el-descriptions-item label="管理账号">{{fmt(data.host_account)}}</el-descriptions-item><el-descriptions-item label="管理IP">{{fmt(data.management_ip_text)}}</el-descriptions-item><el-descriptions-item label="地点">{{fmt(data.location_name)}}</el-descriptions-item><el-descriptions-item label="运营商">{{fmt(data.carrier_code)}}</el-descriptions-item><el-descriptions-item label="线路数量">{{fmt(data.line_count)}}</el-descriptions-item><el-descriptions-item label="B/C段">{{fmt(data.b_prefix_count)}} / {{fmt(data.c_prefix_count)}}</el-descriptions-item><el-descriptions-item label="CPU">{{fmt(data.cpu_usage)}}%</el-descriptions-item><el-descriptions-item label="内存">{{fmt(data.memory_usage)}}%</el-descriptions-item><el-descriptions-item label="系统负载">{{fmt(data.system_load)}}</el-descriptions-item><el-descriptions-item label="硬盘">{{fmt(data.disk_usage)}}%</el-descriptions-item><el-descriptions-item label="VPN支持">{{data.vpn_supported?'支持':'否'}}</el-descriptions-item><el-descriptions-item label="PPPoE服务器">{{fmt(data.pppoe_server_name)}}</el-descriptions-item><el-descriptions-item label="心跳时间">{{fmt(data.last_communicated_at)}}</el-descriptions-item><el-descriptions-item label="状态">{{fmt(data.online_status)}}</el-descriptions-item></el-descriptions><el-alert class="mt" type="info" :closable="false" title="当前阶段 CentOS 与 ROS 按 1:1 部署；管理动作及B/C段统计归属CentOS。"/></el-card>
<el-card shadow="never"><template #header><div class="card-head"><b>对应ROS</b><el-button v-if="data.ros?.id" link type="primary" @click="router.push(`/resources/ros/${data.ros.id}`)">查看ROS详情</el-button></div></template><el-empty v-if="!data.ros" description="当前CentOS未绑定ROS"/><el-descriptions v-else :column="4" border><el-descriptions-item label="ROS ID">{{fmt(data.ros.resource_code)}}</el-descriptions-item><el-descriptions-item label="记录名">{{fmt(data.ros.record_name)}}</el-descriptions-item><el-descriptions-item label="状态">{{fmt(data.ros.online_status)}}</el-descriptions-item><el-descriptions-item label="最近上报">{{fmt(data.ros.last_report_at)}}</el-descriptions-item><el-descriptions-item label="线路总数">{{fmt(data.ros.line_total)}}</el-descriptions-item><el-descriptions-item label="在线线路">{{fmt(data.ros.line_online)}}</el-descriptions-item><el-descriptions-item label="当前IP">{{fmt(data.ros.current_ip_count)}}</el-descriptions-item><el-descriptions-item label="BAS数量">{{fmt(data.ros.bas_count)}}</el-descriptions-item></el-descriptions></el-card></template>

<template v-else-if="!detailError&&type==='ROS'"><el-card shadow="never"><template #header><b>ROS拨号运行信息</b></template><el-descriptions :column="4" border><el-descriptions-item label="ROS ID">{{fmt(data.resource_code)}}</el-descriptions-item><el-descriptions-item label="对应CentOS"><el-button v-if="data.centos_id" link type="primary" @click="router.push('/resources/centos/'+data.centos_id)">{{fmt(data.centos_code)}}</el-button><span v-else>未绑定，需核对1:1关系</span></el-descriptions-item><el-descriptions-item label="记录名">{{fmt(data.record_name)}}</el-descriptions-item><el-descriptions-item label="状态">{{fmt(data.online_status)}}</el-descriptions-item><el-descriptions-item label="最近上报">{{fmt(data.last_report_at)}}</el-descriptions-item><el-descriptions-item label="CPU">{{fmt(data.cpu_usage)}}%</el-descriptions-item><el-descriptions-item label="内存">{{fmt(data.memory_usage)}}%</el-descriptions-item><el-descriptions-item label="系统负载">{{fmt(data.system_load)}}</el-descriptions-item><el-descriptions-item label="硬盘">{{fmt(data.disk_usage)}}%</el-descriptions-item><el-descriptions-item label="线路总数">{{fmt(data.line_total)}}</el-descriptions-item><el-descriptions-item label="在线线路">{{fmt(data.line_online)}}</el-descriptions-item><el-descriptions-item label="异常线路">{{fmt(data.line_abnormal)}}</el-descriptions-item><el-descriptions-item label="当前拨号IP">{{fmt(data.current_ip_count)}}</el-descriptions-item><el-descriptions-item label="BAS数量">{{fmt(data.bas_count)}}</el-descriptions-item><el-descriptions-item label="开机时长(秒)">{{fmt(data.uptime_seconds)}}</el-descriptions-item></el-descriptions><el-alert class="mt" type="info" :closable="false" title="ROS仅负责家宽线路拨号与拨号结果上报，不再承担主备、替换、自动切换等管理功能；其他管理动作在CentOS侧完成。"/></el-card><el-card shadow="never"><template #header><div class="card-head"><b>BAS/BRAS汇总</b><el-button link type="primary" @click="router.push({path:'/resources/lines',query:{rosId:id}})">查看全部线路</el-button></div></template><el-table :data="data.basStatistics||[]" border stripe><el-table-column prop="bas_name" label="BAS/BRAS"/><el-table-column prop="bas_address" label="地址"/><el-table-column prop="line_total" label="线路数"/><el-table-column prop="online_lines" label="在线线路"/><el-table-column prop="current_ip_count" label="当前IP数"/></el-table></el-card></template>

<template v-else-if="!detailError"><el-card shadow="never"><template #header><div class="card-head"><b>线路基础信息</b><el-tag :type="data.line_type==='LONG'?'warning':'info'">{{lineTypeLabel(data.line_type)}}</el-tag></div></template><el-descriptions :column="4" border><el-descriptions-item label="线路ID">{{fmt(data.resource_code)}}</el-descriptions-item><el-descriptions-item label="线路别名">{{fmt(data.line_alias)}}</el-descriptions-item><el-descriptions-item label="所属ROS"><el-button v-if="data.ros_id" link type="primary" @click="router.push('/resources/ros/'+data.ros_id)">{{fmt(data.ros_code)}}</el-button><span v-else>{{fmt(data.ros_code)}}</span></el-descriptions-item><el-descriptions-item label="所属CentOS"><el-button v-if="data.centos_id" link type="primary" @click="router.push('/resources/centos/'+data.centos_id)">{{fmt(data.centos_code)}}</el-button><span v-else>{{fmt(data.centos_code)}}</span></el-descriptions-item><el-descriptions-item label="拨号账号（掩码）">{{fmt(data.broadband_account_mask)}}</el-descriptions-item><el-descriptions-item label="账号前缀">{{fmt(data.account_prefix)}}</el-descriptions-item><el-descriptions-item label="线路当前拨号公网IP（非固定出口）">{{fmt(data.current_public_ip_text)}}</el-descriptions-item><el-descriptions-item label="HTTP/SOCKS5">{{fmt(data.http_port)}} / {{fmt(data.socks5_port)}}</el-descriptions-item><el-descriptions-item label="域名">{{fmt(data.domain_name)}}</el-descriptions-item><el-descriptions-item label="线路类型">{{lineTypeLabel(data.line_type)}}</el-descriptions-item><el-descriptions-item label="状态">{{fmt(data.online_status)}} / {{fmt(data.dial_status)}}</el-descriptions-item><el-descriptions-item label="限制人数/在用">{{fmt(data.user_limit)}} / {{fmt(data.active_users)}}</el-descriptions-item><el-descriptions-item label="重拨/重启间隔">{{fmt(data.restart_interval_minutes)}} 分钟</el-descriptions-item><el-descriptions-item label="VPN支持">{{data.vpn_supported?'支持':'否'}}</el-descriptions-item><el-descriptions-item label="VPN重拨监控">{{data.vpn_redial_monitor?'启用':'关闭'}}</el-descriptions-item><el-descriptions-item label="BAS/BRAS">{{fmt(data.bas_name)}}</el-descriptions-item><el-descriptions-item label="BAS地址">{{fmt(data.bas_address)}}</el-descriptions-item><el-descriptions-item label="省/市">{{fmt(data.province_code)}} / {{fmt(data.city_code)}}</el-descriptions-item><el-descriptions-item label="运营商">{{fmt(data.carrier_code)}}</el-descriptions-item><el-descriptions-item label="平均延迟">{{fmt(data.latency_ms)}} ms</el-descriptions-item><el-descriptions-item label="资源池">{{fmt(data.pool_names)}}</el-descriptions-item><el-descriptions-item label="数据来源与时效">ROS上报 / {{fmt(data.last_ros_report_at)}}</el-descriptions-item><el-descriptions-item label="最近拨号">{{fmt(data.last_dial_at)}}</el-descriptions-item><el-descriptions-item label="ROS上报">{{fmt(data.last_ros_report_at)}}</el-descriptions-item></el-descriptions><el-alert class="mt" type="info" :closable="false" title="客户与线路的业务关系通过服务实例及资源绑定查询，不作为基础线路属性展示。资源池线路类型仅共享/长效。"/></el-card><el-card shadow="never"><el-alert v-if="relationWarnings.length" type="warning" :closable="false" :title="relationWarnings.join('；')"/><el-tabs><el-tab-pane v-if="auth.hasPermission('resource:pool:read')" label="所属资源池">
 <el-table :data="memberPools" border stripe empty-text="当前无有效池成员关系">
  <el-table-column prop="pool_code" label="资源池编号" min-width="150"/>
  <el-table-column prop="pool_name" label="池名称" min-width="150"/>
  <el-table-column prop="province_code" label="省" width="100"/>
  <el-table-column prop="city_code" label="市" width="100"/>
  <el-table-column prop="carrier_code" label="运营商" min-width="120"/>
  <el-table-column prop="joined_at" label="加入时间" min-width="165"/>
  <el-table-column label="操作" width="100"><template #default="s"><el-button link type="primary" @click="router.push('/resource-pools/'+s.row.id)">查看池</el-button></template></el-table-column>
 </el-table>
 </el-tab-pane>
 <el-tab-pane v-if="auth.hasPermission('service:read')" label="关联服务与客户">
  <el-alert type="info" :closable="false" title="客户归属仅由服务资源绑定或固定公网IPv4出口占用派生，不能通过线路主档的旧客户字段判断。"/>
  <el-table :data="relatedServices" border stripe empty-text="尚无直接线路绑定或固定出口占用记录">
   <el-table-column prop="service_no" label="服务编号" min-width="160"/>
   <el-table-column prop="customer_name" label="关联客户" min-width="140"/>
   <el-table-column prop="product_name" label="产品" min-width="120"/>
   <el-table-column prop="status" label="业务状态" width="115"/>
   <el-table-column prop="delivery_stage" label="交付验证阶段" min-width="130"/>
   <el-table-column label="关联依据" min-width="160"><template #default="s">{{s.row.direct_line_binding?'线路绑定 ':''}}{{s.row.active_egress_claim?'出口占用':''}}</template></el-table-column>
   <el-table-column label="操作" width="125"><template #default="s"><el-button link type="primary" @click="router.push('/services/'+s.row.id)">查看服务</el-button></template></el-table-column>
  </el-table>
 </el-tab-pane><el-tab-pane label="IP变化历史"><el-table :data="ipHistory" border stripe><el-table-column prop="ip_address" label="IP"/><el-table-column prop="change_type" label="变化类型"/><el-table-column prop="first_seen_at" label="首次出现"/><el-table-column prop="last_seen_at" label="最后出现"/></el-table></el-tab-pane><el-tab-pane label="运维操作日志"><el-table :data="operations" border stripe><el-table-column prop="operation_type" label="操作"/><el-table-column prop="operation_source" label="来源"/><el-table-column prop="result_status" label="结果"/><el-table-column prop="result_message" label="说明"/><el-table-column prop="created_at" label="时间"/></el-table></el-tab-pane></el-tabs></el-card></template></div></template>
<style scoped>.page-stack{display:grid;gap:16px}.page-head,.card-head,.actions{display:flex;justify-content:space-between;gap:10px}.page-head h1{margin:8px 0 4px}.page-head p{margin:0;color:#7b8495}.card-head{width:100%;align-items:center}.mt{margin-top:14px}</style>
