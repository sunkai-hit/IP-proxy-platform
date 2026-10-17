<script setup lang="ts">
import { onMounted,reactive,ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { v12ResourceApi } from '@/api/v12Resource'
import { useAuthStore } from '@/stores/auth'
const router=useRouter(),auth=useAuthStore(),rows=ref<any[]>([]),total=ref(0),loading=ref(false),loadError=ref(false),saving=ref(false),dialog=ref(false),editId=ref<number|null>(null)
const query=reactive({page:1,size:20,keyword:'',status:'',province:'',city:'',carrier:''})
const form=reactive<any>({poolCode:'',poolName:'',provinceCode:'',cityCode:'',carrierCode:'',purpose:''})
const data=(r:any)=>r.data.data
const validId=(v:any)=>Number.isSafeInteger(Number(v))&&Number(v)>0
function validPoolPage(d:any){
 if(!d||!Array.isArray(d.items)||!Number.isSafeInteger(Number(d.total))||Number(d.total)<0||d.items.length>Number(d.total))return false
 const ids=new Set<number>()
 return d.items.every((pool:any)=>{
  if(!validId(pool?.id)||ids.has(Number(pool.id))||
    !String(pool.pool_code||'').trim()||!String(pool.pool_name||'').trim())return false
  ids.add(Number(pool.id));return true
 })
}
let listVersion=0,editVersion=0
async function load(){
 const version=++listVersion
 loading.value=true;loadError.value=false;rows.value=[];total.value=0
 try{
  const d=data(await v12ResourceApi.pools({...query}))||{}
  if(version===listVersion){
   if(!validPoolPage(d))throw new Error('POOL_LIST_UNVERIFIED')
   rows.value=d.items;total.value=d.total
  }
 }catch{
  if(version===listVersion){loadError.value=true;rows.value=[];total.value=0}
 }finally{if(version===listVersion)loading.value=false}
}
function resetForm(){Object.assign(form,{poolCode:'',poolName:'',provinceCode:'',cityCode:'',carrierCode:'',purpose:''})}
function add(){
 if(saving.value||!auth.hasPermission('resource:pool:write'))return
 ++editVersion;editId.value=null;resetForm();dialog.value=true
}
async function edit(r:any){
 if(saving.value||!auth.hasPermission('resource:pool:write')||loading.value||loadError.value)return
 const version=++editVersion,selectedId=Number(r.id)
 dialog.value=false;resetForm()
 try{
  const d=data(await v12ResourceApi.pool(selectedId))
  if(version!==editVersion)return
  if(!validId(selectedId)||Number(d?.id)!==selectedId||
    String(d.pool_code||'').trim()!==String(r.pool_code||'').trim()||
    !String(d.pool_name||'').trim())throw new Error('POOL_EDIT_IDENTITY_CHANGED')
  editId.value=selectedId
  Object.assign(form,{poolCode:d.pool_code,poolName:d.pool_name,provinceCode:d.province_code||'',cityCode:d.city_code||'',carrierCode:d.carrier_code||'',purpose:d.purpose||''})
  dialog.value=true
 }catch{if(version===editVersion)ElMessage.error('资源池信息读取失败，不能使用旧资料修改')}
}
async function save(){
 if(saving.value||!auth.hasPermission('resource:pool:write')||loading.value||loadError.value)return
 if(!form.poolCode||!form.poolName){ElMessage.warning('请填写资源池编码和名称');return}
 const selectedId=editId.value,version=editVersion
 saving.value=true
 try{
  const payload={...form,lineIds:null}
  if(selectedId)await v12ResourceApi.updatePool(selectedId,payload)
  else await v12ResourceApi.createPool(payload)
  if(version===editVersion){dialog.value=false;ElMessage.success('资源池已保存');await load()}
 }catch{ElMessage.error('资源池保存未确认成功，请查询后重试')}
 finally{saving.value=false}
}
function resetQuery(){Object.assign(query,{page:1,size:20,keyword:'',status:'',province:'',city:'',carrier:''});void load()}
onMounted(load)
</script>
<template><div class="page"><div class="head"><div><h2>资源池</h2><p>资源池按省/市/运营商等资源来源属性组织自有家宽线路。池内线路仅区分“共享 / 长效”，独享IP、VPN、隧道不作为资源池线路类型。</p></div><el-button v-if="auth.hasPermission('resource:pool:write')" type="primary" :disabled="saving" @click="add">新增资源池</el-button></div>
<el-alert v-if="loadError" type="error" :closable="false" title="资源池列表查询失败，不能据此认为没有资源池或来源线路。" /><el-button v-if="loadError" @click="load">重新加载资源池列表</el-button>
<el-card shadow="never"><div class="filters"><el-input v-model="query.keyword" placeholder="资源池编码/名称" clearable/><el-input v-model="query.province" placeholder="省" clearable/><el-input v-model="query.city" placeholder="市" clearable/><el-input v-model="query.carrier" placeholder="运营商" clearable/><el-select v-model="query.status" placeholder="状态" clearable><el-option label="启用" value="ACTIVE"/><el-option label="停用" value="DISABLED"/><el-option label="异常" value="ABNORMAL"/></el-select><el-button type="primary" @click="query.page=1;load()">查询</el-button><el-button @click="resetQuery">重置</el-button></div></el-card>
<el-card shadow="never"><el-table :data="rows" v-loading="loading" stripe border><el-table-column prop="pool_code" label="资源池编号" width="150"/><el-table-column prop="pool_name" label="资源池名称" min-width="170"/><el-table-column prop="province_code" label="省" width="90"/><el-table-column prop="city_code" label="市" width="90"/><el-table-column prop="carrier_code" label="运营商" width="95"/><el-table-column prop="ros_count" label="ROS数" width="80"/><el-table-column prop="line_count" label="线路总数" width="90"/><el-table-column prop="shared_line_count" label="共享线路" width="90"/><el-table-column prop="long_line_count" label="长效线路" width="90"/><el-table-column prop="online_line_count" label="在线线路" width="90"/><el-table-column prop="abnormal_line_count" label="异常线路" width="90"/><el-table-column prop="current_ip_count" label="当前IP" width="85"/><el-table-column prop="unique_ip_count" label="去重IP" width="85"/><el-table-column prop="b_prefix_count" label="B段" width="70"/><el-table-column prop="c_prefix_count" label="C段" width="70"/><el-table-column prop="status" label="状态" width="90"/><el-table-column label="操作" width="160" fixed="right"><template #default="s"><el-button link type="primary" @click="router.push(`/resource-pools/${s.row.id}`)">详情/成员</el-button><el-button v-if="auth.hasPermission('resource:pool:write')" link :disabled="loading||loadError||saving" @click="edit(s.row)">编辑</el-button></template></el-table-column></el-table><div class="pager"><el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" layout="total,sizes,prev,pager,next" @change="load"/></div></el-card>
<el-dialog v-model="dialog" :title="editId?'编辑资源池':'新增资源池'" width="680px"><el-form label-width="105px"><el-form-item label="资源池编码" required><el-input v-model="form.poolCode" :disabled="!!editId" placeholder="如 QD-UNICOM-01"/></el-form-item><el-form-item label="资源池名称" required><el-input v-model="form.poolName" placeholder="如 青岛联通资源池"/></el-form-item><el-form-item label="省"><el-input v-model="form.provinceCode" placeholder="山东"/></el-form-item><el-form-item label="市"><el-input v-model="form.cityCode" placeholder="青岛；留空可表示省级资源池"/></el-form-item><el-form-item label="运营商"><el-input v-model="form.carrierCode" placeholder="联通"/></el-form-item><el-form-item label="说明"><el-input v-model="form.purpose" type="textarea" :rows="3"/></el-form-item><el-alert type="info" :closable="false" title="资源池不再按短效/长效/独享/VPN/隧道划分。保存后进入详情页维护共享/长效线路成员；同一线路可加入多个符合来源属性的资源池。"/></el-form><template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" :loading="saving" :disabled="saving" @click="save">保存</el-button></template></el-dialog></div></template>
<style scoped>.page{display:grid;gap:16px}.head{display:flex;justify-content:space-between}.head h2{margin:0 0 6px}.head p{margin:0;color:#7b8494;max-width:980px}.filters{display:flex;gap:10px;flex-wrap:wrap}.filters>*{max-width:210px}.pager{display:flex;justify-content:flex-end;margin-top:14px}</style>
