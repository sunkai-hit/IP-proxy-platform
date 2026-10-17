<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { customerApi } from '@/api/customer'
import { useAuthStore } from '@/stores/auth'
const auth=useAuthStore()
const rows=ref<any[]>([]),customers=ref<any[]>([]),options=ref<any>({authTypes:[]})
const total=ref(0),loading=ref(false),loadError=ref(false),optionsError=ref(false),optionsLoading=ref(true)
const requestVersion=ref(0),optionsVersion=ref(0),detailLoading=ref(false),detailError=ref(false),detailVersion=ref(0),busy=ref(false)
const detail=ref<any>(null),detailDialog=ref(false),createDialog=ref(false)
const query=reactive({page:1,size:20,keyword:'',status:''})
const form=reactive<any>({customerId:null,authTypeCode:'ENTERPRISE',subjectName:'',documentNo:'',attachmentRefs:''})
let createOptionsVersion=-1
const data=(r:any)=>r.data.data
const positiveId=(v:any)=>Number.isSafeInteger(Number(v))&&Number(v)>0
function identity(row:any){
 return {id:Number(row?.id),authNo:String(row?.auth_no||''),customerId:Number(row?.customer_id),
  customerName:String(row?.customer_name||''),status:String(row?.status||'')}
}
function validIdentity(x:any){return positiveId(x.id)&&!!x.authNo&&positiveId(x.customerId)&&!!x.customerName&&!!x.status}
function matches(row:any,x:any){const a=identity(row);return validIdentity(a)&&a.id===x.id&&a.authNo===x.authNo&&
 a.customerId===x.customerId&&a.customerName===x.customerName&&a.status===x.status}
async function load(){
 const version=++requestVersion.value,params={...query}
 loading.value=true;loadError.value=false;rows.value=[];total.value=0
 try{
  const result=data(await customerApi.authList(params))
  if(version===requestVersion.value){
   if(!result||!Array.isArray(result.items)||!Number.isSafeInteger(Number(result.total))||
      Number(result.total)<0)throw new Error('KYC_LIST_INVALID')
   rows.value=result.items;total.value=Number(result.total)
  }
 }catch{if(version===requestVersion.value){loadError.value=true;rows.value=[];total.value=0}}
 finally{if(version===requestVersion.value)loading.value=false}
}
async function init(){
 const version=++optionsVersion.value
 optionsLoading.value=true;optionsError.value=false;customers.value=[];options.value={authTypes:[]}
 const listRequest=load()
 const [opts,customersResponse]=await Promise.allSettled([customerApi.options(),customerApi.list({page:1,size:500})])
 if(version===optionsVersion.value){
  try{
   if(opts.status!=='fulfilled'||customersResponse.status!=='fulfilled')throw new Error('KYC_OPTIONS_DOWN')
   const o=data(opts.value),c=data(customersResponse.value)
   if(!o||!Array.isArray(o.authTypes)||!c||!Array.isArray(c.items)||
      !Number.isSafeInteger(Number(c.total))||Number(c.total)<c.items.length)throw new Error('KYC_OPTIONS_INVALID')
   options.value=o;customers.value=c.items
  }catch{optionsError.value=true;options.value={authTypes:[]};customers.value=[]}
  finally{optionsLoading.value=false}
 }
 await listRequest
}
function openCreate(){
 if(busy.value||loading.value||loadError.value||optionsLoading.value||optionsError.value||
    !auth.hasPermission('customer:write'))return
 createOptionsVersion=optionsVersion.value
 Object.assign(form,{customerId:null,authTypeCode:'ENTERPRISE',subjectName:'',documentNo:'',attachmentRefs:''})
 createDialog.value=true
}
async function open(row:any){
 const snapshot=identity(row),version=++detailVersion.value
 if(busy.value||loading.value||loadError.value||!validIdentity(snapshot)||
    !rows.value.some(r=>matches(r,snapshot))){ElMessage.warning('认证申请身份未核实，请刷新列表');return}
 detailDialog.value=true;detail.value=null;detailError.value=false;detailLoading.value=true
 try{
  const result=data(await customerApi.authDetail(snapshot.id))
  if(version===detailVersion.value&&detailDialog.value){
   if(!matches(result,snapshot))throw new Error('KYC_DETAIL_IDENTITY_MISMATCH')
   detail.value=result
  }
 }catch{if(version===detailVersion.value){detailError.value=true;detail.value=null}}
 finally{if(version===detailVersion.value)detailLoading.value=false}
}
function closeDetail(){detailVersion.value++;detail.value=null;detailError.value=false;detailLoading.value=false}
async function create(){
 if(busy.value||!createDialog.value||loading.value||loadError.value||optionsLoading.value||
    optionsError.value||createOptionsVersion!==optionsVersion.value||
    !auth.hasPermission('customer:write'))return
 const customerId=Number(form.customerId),customer=customers.value.find(c=>Number(c.id)===customerId)
 if(!positiveId(customerId)||!customer||customer.customer_type_code!==form.authTypeCode||
    !options.value.authTypes.some((x:any)=>x.item_code===form.authTypeCode)){
  ElMessage.warning('客户归属或认证类型尚未核实，请重新选择');return
 }
 const payload={authTypeCode:form.authTypeCode,submittedData:{subjectName:form.subjectName,documentNo:form.documentNo},
  attachmentRefs:String(form.attachmentRefs||'').split(',').map((x:string)=>x.trim()).filter(Boolean)}
 busy.value=true
 try{
  await customerApi.createAuth(customerId,payload)
  createDialog.value=false;ElMessage.success('认证申请已创建');await load()
 }catch{ElMessage.error('认证申请未确认创建成功')}
 finally{busy.value=false}
}
async function review(row:any,approve:boolean){
 const snapshot=identity(row),version=requestVersion.value
 const current=()=>version===requestVersion.value&&!loading.value&&!loadError.value&&
  auth.hasPermission('customer:auth:review')&&rows.value.some(r=>matches(r,snapshot))
 if(busy.value||!validIdentity(snapshot)||!['PENDING','REVIEWING'].includes(snapshot.status)||
    !current()){ElMessage.warning('认证申请身份、状态或权限未核实，禁止审核');return}
 busy.value=true
 try{
  const label=approve?'审核通过':'审核驳回'
  const {value}=await ElMessageBox.prompt(
   `认证申请：${snapshot.authNo}（ID ${snapshot.id}），客户：${snapshot.customerName}（ID ${snapshot.customerId}），原状态：${snapshot.status}。请输入审核意见。`,
   label,{inputValue:approve?'资料核验通过':'',
    inputValidator:v=>approve||!!String(v||'').trim()||'驳回必须填写原因',
    confirmButtonText:'确认提交',cancelButtonText:'取消审核'})
  if(!current()){ElMessage.warning('审核确认期间申请身份、状态或列表已变化，未提交操作');return}
  const latest=data(await customerApi.authDetail(snapshot.id))
  if(!current()||!matches(latest,snapshot)){
   ElMessage.warning('认证申请详情或审核状态已变化，请重新核对后再审核');return
  }
  if(approve)await customerApi.approveAuth(snapshot.id,String(value||''))
  else await customerApi.rejectAuth(snapshot.id,String(value||''))
  ElMessage.success('审核请求获得服务端确认，客户实际权限仍以当前状态为准')
  detailDialog.value=false;closeDetail();await load()
 }catch(e:any){
  if(e!=='cancel'&&e!=='close')
   ElMessage.error('认证审核或审核前核对未确认成功；请回查申请、状态和审计')
 }finally{busy.value=false}
}
onMounted(init)
</script>
<template><div class="page"><div class="head"><div><h2>客户认证</h2><p>区分个人/企业认证，保留完整申请与审核记录。</p></div><el-button v-if="auth.hasPermission('customer:write')" type="primary" :disabled="optionsLoading||optionsError||loading||loadError||busy" @click="openCreate">新建认证申请</el-button></div><el-alert title="客户认证保留申请、审核和附件引用；普通详情只展示业务摘要，不直接输出证件原文或原始资料JSON。" type="info" :closable="false"/>
<el-alert v-if="loadError" type="error" :closable="false" title="客户认证列表读取失败，不能据此认为没有待审核申请。"/><el-button v-if="loadError" @click="load">重新查询认证申请</el-button><el-alert v-if="optionsError" type="warning" :closable="false" title="客户或认证类型选项读取失败或不可信，当前不能安全新建认证申请。"/><el-button v-if="optionsError" :disabled="optionsLoading||busy" @click="init">重新加载认证选项</el-button><el-card shadow="never"><div class="filters"><el-input v-model="query.keyword" placeholder="认证编号/客户" clearable/><el-select v-model="query.status" placeholder="审核状态" clearable><el-option label="待审核" value="PENDING"/><el-option label="审核中" value="REVIEWING"/><el-option label="已通过" value="APPROVED"/><el-option label="已驳回" value="REJECTED"/></el-select><el-button type="primary" :disabled="busy" @click="query.page=1;load()">查询</el-button></div><el-table :data="rows" v-loading="loading" :empty-text="loadError?'认证列表读取失败':'暂无已登记认证申请'" stripe><el-table-column prop="auth_no" label="认证编号" width="180"/><el-table-column prop="customer_name" label="客户"/><el-table-column prop="auth_type_code" label="类型" width="100"/><el-table-column prop="status" label="状态" width="100"/><el-table-column prop="submitted_at" label="提交时间" width="190"/><el-table-column prop="reviewer_name" label="审核人" width="110"/><el-table-column label="操作" width="180"><template #default="s"><el-button link :disabled="busy||loading||loadError" @click="open(s.row)">详情</el-button><template v-if="auth.hasPermission('customer:auth:review')&&['PENDING','REVIEWING'].includes(s.row.status)"><el-button link type="success" :disabled="busy||loading||loadError" @click="review(s.row,true)">通过</el-button><el-button link type="danger" :disabled="busy||loading||loadError" @click="review(s.row,false)">驳回</el-button></template></template></el-table-column></el-table><div class="pager"><el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" layout="total, prev, pager, next" @change="load"/></div></el-card>
<el-dialog v-model="createDialog" title="新建认证申请" width="600px" :close-on-click-modal="!busy" :close-on-press-escape="!busy" :show-close="!busy"><el-form label-width="120px"><el-form-item label="客户"><el-select v-model="form.customerId" filterable style="width:100%" @change="(id:number)=>{const c=customers.find(x=>x.id===id);if(c)form.authTypeCode=c.customer_type_code}"><el-option v-for="c in customers" :key="c.id" :label="`${c.customer_code} · ${c.customer_name}`" :value="c.id"/></el-select></el-form-item><el-form-item label="认证类型"><el-select v-model="form.authTypeCode" style="width:100%"><el-option v-for="x in options.authTypes" :key="x.item_code" :label="x.item_name" :value="x.item_code"/></el-select></el-form-item><el-form-item label="认证主体名称"><el-input v-model="form.subjectName"/></el-form-item><el-form-item label="证件/主体编号"><el-input v-model="form.documentNo"/></el-form-item><el-form-item label="附件引用"><el-input v-model="form.attachmentRefs" placeholder="多个引用用逗号分隔"/></el-form-item></el-form><template #footer><el-button :disabled="busy" @click="createDialog=false">取消</el-button><el-button type="primary" :loading="busy" :disabled="optionsLoading||optionsError||loading||loadError||busy" @click="create">提交</el-button></template></el-dialog>
<el-dialog v-model="detailDialog" title="认证详情" width="680px" @closed="closeDetail"><el-alert v-if="detailLoading" type="info" :closable="false" title="正在核对当前认证申请身份与资料。"/><el-alert v-if="detailError" type="error" :closable="false" title="认证详情读取失败，不能确认资料、审核状态或历史意见。"/><template v-if="detail"><el-descriptions :column="2" border><el-descriptions-item label="认证编号">{{ detail.auth_no }}</el-descriptions-item><el-descriptions-item label="客户">{{ detail.customer_name }}</el-descriptions-item><el-descriptions-item label="类型">{{ detail.auth_type_code }}</el-descriptions-item><el-descriptions-item label="状态">{{ detail.status }}</el-descriptions-item><el-descriptions-item label="审核人">{{ detail.reviewer_name||'—' }}</el-descriptions-item><el-descriptions-item label="审核意见">{{ detail.review_opinion||'—' }}</el-descriptions-item></el-descriptions><h4>申请资料摘要（敏感信息不回显）</h4>
<el-descriptions :column="2" border>
 <el-descriptions-item label="认证主体">{{detail.subject_name||'未提供'}}</el-descriptions-item>
 <el-descriptions-item label="证件/主体编号">{{detail.document_number_masked||'未填写'}}</el-descriptions-item>
 <el-descriptions-item label="附件数量">{{detail.attachment_count??'—'}}</el-descriptions-item>
 <el-descriptions-item label="申请时间">{{detail.submitted_at||'—'}}</el-descriptions-item>
 <el-descriptions-item label="审核时间">{{detail.reviewed_at||'未审核'}}</el-descriptions-item>
</el-descriptions>
<el-alert type="info" :closable="false" style="margin-top:10px" title="附件引用及证件原文不在普通详情中直接展示；资料查看须通过授权材料渠道。"/></template></el-dialog></div></template>
<style scoped>.page{display:grid;gap:14px}.head{display:flex;justify-content:space-between}.head h2{margin:0 0 6px}.head p{margin:0;color:#7b8494}.filters{display:flex;gap:10px;margin-bottom:14px}.filters .el-input{max-width:320px}.filters .el-select{width:180px}.pager{display:flex;justify-content:flex-end;margin-top:14px}pre{background:#f6f7f9;padding:12px;border-radius:6px;white-space:pre-wrap}</style>
