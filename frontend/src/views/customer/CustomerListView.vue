<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { customerApi } from '@/api/customer'
import { useAuthStore } from '@/stores/auth'
const router=useRouter();const auth=useAuthStore();const rows=ref<any[]>([]);const total=ref(0);const loading=ref(false),loadError=ref(false),optionsError=ref(false),saving=ref(false),actingId=ref<number|null>(null),requestVersion=ref(0);const options=ref<any>({customerTypes:[],owners:[]});const dialog=ref(false);const editId=ref<number|null>(null)
const query=reactive({page:1,size:20,keyword:'',status:'',authStatus:'',customerTypeCode:''})
const form=reactive<any>({customerName:'',customerTypeCode:'ENTERPRISE',contactName:'',contactPhone:'',contactEmail:'',ownerUserId:null,remark:''})
const data=(r:any)=>r.data.data
async function load(){
 const version=++requestVersion.value,params={...query}
 loading.value=true;loadError.value=false;rows.value=[];total.value=0
 try{const result=data(await customerApi.list(params))||{}
  if(version===requestVersion.value){
   if(!Array.isArray(result.items)||!Number.isFinite(Number(result.total)))throw new Error('CUSTOMER_LIST_INVALID')
   rows.value=result.items;total.value=Number(result.total)
  }
 }catch{if(version===requestVersion.value)loadError.value=true}
 finally{if(version===requestVersion.value)loading.value=false}
}
async function loadOptions(){
 optionsError.value=false
 try{
  const received=data(await customerApi.options())
  if(!received||!Array.isArray(received.customerTypes)||!Array.isArray(received.owners))
   throw new Error('CUSTOMER_OPTIONS_INVALID')
  options.value=received
 }
 catch{optionsError.value=true;options.value={customerTypes:[],owners:[]}}
}
function resetForm(){Object.assign(form,{customerName:'',customerTypeCode:'ENTERPRISE',contactName:'',contactPhone:'',contactEmail:'',ownerUserId:null,remark:''})}
const validId=(v:any)=>Number.isSafeInteger(Number(v))&&Number(v)>0
let editVersion=0,editCode=''
function add(){
 if(loading.value||loadError.value||saving.value||actingId.value!==null||optionsError.value||
    !auth.hasPermission('customer:write'))return
 editVersion=requestVersion.value;editCode='';editId.value=null;resetForm();dialog.value=true
}
function edit(row:any){
 if(loading.value||loadError.value||saving.value||actingId.value!==null||optionsError.value||
    !auth.hasPermission('customer:write')||!validId(row?.id)||
    !rows.value.some(x=>x.id===row.id&&x.customer_code===row.customer_code&&x.status===row.status))return
 editVersion=requestVersion.value;editCode=String(row.customer_code)
 editId.value=Number(row.id)
 Object.assign(form,{customerName:row.customer_name,customerTypeCode:row.customer_type_code,contactName:row.contact_name||'',contactPhone:row.contact_phone||'',contactEmail:row.contact_email||'',ownerUserId:row.owner_user_id||null,remark:row.remark||''});dialog.value=true
}
async function save(){
 if(saving.value||actingId.value!==null||!dialog.value||loading.value||loadError.value||
    optionsError.value||!auth.hasPermission('customer:write'))return
 if(!form.customerName){ElMessage.warning('请输入客户名称');return}
 const id=editId.value,payload={...form}
 if(id!==null&&(requestVersion.value!==editVersion||!rows.value.some(x=>
   Number(x.id)===id&&String(x.customer_code)===editCode))){
  ElMessage.warning('客户编辑对象或列表已变化，请刷新并重新核对客户');return
 }
 saving.value=true
 try{
  if(id!==null)await customerApi.update(id,payload);else await customerApi.create(payload)
  ElMessage.success('客户档案保存成功，关联服务交付仍需独立核实')
  dialog.value=false;await load()
 }catch{ElMessage.error('客户档案未确认保存成功')}
 finally{saving.value=false}
}
async function action(row:any,type:'freeze'|'resume'|'disable'){
 const id=Number(row?.id),code=String(row?.customer_code||''),name=String(row?.customer_name||'')
 const status=String(row?.status||''),version=requestVersion.value
 const serviceCount=Number(row?.service_count),graceCount=Number(row?.grace_pending_count)
 const eligible=type==='freeze'?status==='ACTIVE':type==='resume'?status==='FROZEN':
   (status==='ACTIVE'||status==='FROZEN')
 const matches=(item:any)=>Number(item?.id)===id&&String(item?.customer_code||'')===code&&
   String(item?.customer_name||'')===name&&String(item?.status||'')===status&&
   Number(item?.service_count)===serviceCount&&Number(item?.grace_pending_count)===graceCount
 if(actingId.value!==null||saving.value||loading.value||loadError.value||
   !auth.hasPermission('customer:status')||!validId(id)||!code||!name||!eligible||
   row?.service_count==null||row?.grace_pending_count==null||
   !Number.isSafeInteger(serviceCount)||serviceCount<0||
   !Number.isSafeInteger(graceCount)||graceCount<0||!rows.value.some(matches)){
  ElMessage.warning('客户身份、管理状态或服务影响数未核实，禁止提交状态操作')
  return
 }
 const labels={freeze:'冻结',resume:'恢复',disable:'停用'}
 const stillCurrent=()=>requestVersion.value===version&&!loading.value&&!loadError.value&&
   auth.hasPermission('customer:status')&&rows.value.some(matches)
 actingId.value=id
 try{
  await ElMessageBox.confirm(
   `客户：${name}（${code}，ID ${id}），原状态：${status}。未终止服务：${serviceCount}个；宽限/释放待处理：${graceCount}个。该操作仅改变客户管理状态，不代表已停服/释放资源。请核实实际客户影响。`,
   `${labels[type]}客户 · 影响确认`,{type:'warning',confirmButtonText:'继续填写原因',cancelButtonText:'返回核对'})
  if(!stillCurrent()){ElMessage.warning('影响确认期间客户、状态或在用服务已变化，未提交状态操作');return}
  const {value}=await ElMessageBox.prompt(`客户：${name}（ID ${id}），原状态：${status}。请输入${labels[type]}原因`,
   `${labels[type]}客户`,{inputValidator:v=>!!String(v||'').trim()||'必须填写原因',
     confirmButtonText:'确认提交',cancelButtonText:'返回核对'})
  if(!stillCurrent()){ElMessage.warning('填写原因期间客户身份、状态或服务影响已变化，未提交状态操作');return}
  await customerApi[type](id,String(value).trim())
  ElMessage.success('客户管理状态变更已返回；关联服务的真实网络状态仍须单独核验')
  await load()
 }catch(e:any){
  if(e!=='cancel'&&e!=='close')
   ElMessage.error('客户状态变更未确认成功；可能已发生并发冲突，请核对原客户和审计后再重试')
 }finally{actingId.value=null}
}
function maskPhone(v:any){const x=String(v||'');return x.length>=7?x.slice(0,3)+'****'+x.slice(-4):(x?'已设置':'—')}
function goCustomer(row:any){if(!loading.value&&!loadError.value&&validId(row?.id)&&
   rows.value.some(x=>Number(x.id)===Number(row.id)&&x.customer_code===row.customer_code))
   void router.push('/customers/'+row.id)}
function tag(status:string){return status==='ACTIVE'?'success':status==='FROZEN'?'warning':'info'}
onMounted(()=>{void load();void loadOptions()})
</script>
<template><div class="page"><div class="head"><div><h2>客户管理</h2><p>维护客户档案、认证状态、业务负责人及关联账号。</p></div><el-button v-if="auth.hasPermission('customer:write')" type="primary"  :disabled="loading||loadError||actingId!==null||saving||optionsError" @click="add">新增客户</el-button></div>
<el-alert v-if="loadError" type="error" :closable="false" title="客户列表读取失败，不能据此认定没有客户或没有未终止服务。"/><el-button v-if="loadError" @click="load">重新查询客户</el-button><el-alert v-if="optionsError" type="warning" :closable="false" title="客户类型/负责人选项读取失败，列表仍可查询；保存客户暂不可用。"/><el-button v-if="optionsError" @click="loadOptions">重新加载客户选项</el-button><el-card shadow="never"><div class="filters"><el-input v-model="query.keyword" placeholder="客户编号/名称/联系人" clearable @keyup.enter="query.page=1;load()"/><el-select v-model="query.customerTypeCode" placeholder="客户类型" clearable><el-option v-for="x in options.customerTypes" :key="x.item_code" :label="x.item_name" :value="x.item_code"/></el-select><el-select v-model="query.status" placeholder="客户状态" clearable><el-option label="正常" value="ACTIVE"/><el-option label="冻结" value="FROZEN"/><el-option label="停用" value="DISABLED"/></el-select><el-select v-model="query.authStatus" placeholder="认证状态" clearable><el-option label="未认证" value="UNVERIFIED"/><el-option label="待审核" value="PENDING"/><el-option label="已通过" value="APPROVED"/><el-option label="已驳回" value="REJECTED"/></el-select><el-button type="primary" @click="query.page=1;load()">查询</el-button></div>
<el-table :data="rows" v-loading="loading" :empty-text="loadError?'客户列表读取失败':'暂无已登记客户'" stripe><el-table-column prop="customer_code" label="客户编号" width="160"/><el-table-column label="客户名称" min-width="180"><template #default="s"><el-button link type="primary"  :disabled="loading||loadError" @click="goCustomer(s.row)">{{s.row.customer_name}}</el-button></template></el-table-column><el-table-column prop="customer_type_code" label="类型" width="90"/><el-table-column prop="contact_name" label="联系人" width="100"/><el-table-column label="联系电话" width="135"><template #default="s">{{maskPhone(s.row.contact_phone)}}</template></el-table-column><el-table-column prop="owner_name" label="负责人" width="110"/>
<el-table-column label="未终止服务" width="106"><template #default="s">{{s.row.service_count??'—'}}</template></el-table-column>
<el-table-column prop="next_expire_at" label="下次到期" min-width="180"><template #default="s">{{s.row.next_expire_at||'—'}}</template></el-table-column>
<el-table-column label="宽限/释放待办" width="132"><template #default="s"><el-tag v-if="Number(s.row.grace_pending_count)>0" type="warning">{{s.row.grace_pending_count}}</el-tag><span v-else>{{s.row.grace_pending_count==null?'—':'0'}}</span></template></el-table-column><el-table-column label="认证" width="100"><template #default="s"><el-tag size="small">{{ s.row.auth_status }}</el-tag></template></el-table-column><el-table-column label="状态" width="90"><template #default="s"><el-tag :type="tag(s.row.status)">{{ s.row.status }}</el-tag></template></el-table-column><el-table-column label="操作" width="250" fixed="right"><template #default="s"><el-button link type="primary"  :disabled="loading||loadError" @click="goCustomer(s.row)">详情</el-button><el-button v-if="auth.hasPermission('customer:write')" link  :disabled="loading||loadError||actingId!==null||saving||optionsError" @click="edit(s.row)">编辑</el-button><el-button v-if="auth.hasPermission('customer:status')&&s.row.status==='ACTIVE'" link type="warning"  :disabled="loading||loadError||actingId!==null||saving" @click="action(s.row,'freeze')">冻结</el-button><el-button v-if="auth.hasPermission('customer:status')&&s.row.status==='FROZEN'" link type="success"  :disabled="loading||loadError||actingId!==null||saving" @click="action(s.row,'resume')">恢复</el-button><el-button v-if="auth.hasPermission('customer:status')&&s.row.status!=='DISABLED'" link type="danger"  :disabled="loading||loadError||actingId!==null||saving" @click="action(s.row,'disable')">停用</el-button></template></el-table-column></el-table>
<div class="pager"><el-pagination v-model:current-page="query.page" v-model:page-size="query.size" layout="total, sizes, prev, pager, next" :total="total" :page-sizes="[20,50,100]" @change="load"/></div></el-card>
<el-dialog v-model="dialog" :title="editId?'编辑客户':'新增客户'" width="620px"
 :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving"><el-form label-width="100px"><el-form-item label="客户名称" required><el-input v-model="form.customerName"/></el-form-item><el-form-item label="客户类型" required><el-select v-model="form.customerTypeCode" style="width:100%"><el-option v-for="x in options.customerTypes" :key="x.item_code" :label="x.item_name" :value="x.item_code"/></el-select></el-form-item><el-form-item label="联系人"><el-input v-model="form.contactName"/></el-form-item><el-form-item label="联系电话"><el-input v-model="form.contactPhone"/></el-form-item><el-form-item label="联系邮箱"><el-input v-model="form.contactEmail"/></el-form-item><el-form-item label="业务负责人"><el-select v-model="form.ownerUserId" clearable style="width:100%"><el-option v-for="u in options.owners" :key="u.id" :label="u.display_name||u.username" :value="u.id"/></el-select></el-form-item><el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="3"/></el-form-item></el-form><template #footer><el-button :disabled="saving" @click="dialog=false">取消</el-button><el-button type="primary" :loading="saving" :disabled="saving||optionsError||loading||loadError||actingId!==null" @click="save">保存</el-button></template></el-dialog></div></template>
<style scoped>.page{display:grid;gap:16px}.head{display:flex;justify-content:space-between;align-items:flex-start}.head h2{margin:0 0 6px}.head p{margin:0;color:#7b8494}.filters{display:grid;grid-template-columns:1.5fr repeat(3,1fr) auto;gap:10px;margin-bottom:14px}.pager{display:flex;justify-content:flex-end;margin-top:14px}@media(max-width:1100px){.filters{grid-template-columns:1fr 1fr}}</style>
