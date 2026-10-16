<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { customerApi } from '@/api/customer'
import { useAuthStore } from '@/stores/auth'

const auth=useAuthStore()
const rows=ref<any[]>([]),customers=ref<any[]>([]),options=ref<any>({accountTypes:[]}),total=ref(0)
const dialog=ref(false),listLoading=ref(false),listError=ref(false)
const customersError=ref(false),optionsError=ref(false),actionBusy=ref(false),createBusy=ref(false)
const listVersion=ref(0)
const query=reactive({page:1,size:20,keyword:'',status:''})
const form=reactive<any>({customerId:null,username:'',password:'',accountTypeCode:'DEFAULT'})
const data=(r:any)=>r.data.data

async function load(){
 const version=++listVersion.value
 listLoading.value=true;listError.value=false;rows.value=[];total.value=0
 try{
  const result=data(await customerApi.accountList({...query}))||{}
  if(version===listVersion.value){rows.value=result.items||[];total.value=result.total||0}
 }catch{
  if(version===listVersion.value){listError.value=true;rows.value=[];total.value=0}
 }finally{if(version===listVersion.value)listLoading.value=false}
}
async function init(){
 const [opts,clients]=await Promise.allSettled([
  customerApi.options(),customerApi.list({page:1,size:500})
 ])
 optionsError.value=opts.status==='rejected'
 customersError.value=clients.status==='rejected'
 options.value=opts.status==='fulfilled'?(data(opts.value)||{accountTypes:[]}):{accountTypes:[]}
 customers.value=clients.status==='fulfilled'?(data(clients.value)?.items||[]):[]
 await load()
}
async function retryChoices(){
 const [opts,clients]=await Promise.allSettled([
  customerApi.options(),customerApi.list({page:1,size:500})
 ])
 optionsError.value=opts.status==='rejected';customersError.value=clients.status==='rejected'
 options.value=opts.status==='fulfilled'?(data(opts.value)||{accountTypes:[]}):{accountTypes:[]}
 customers.value=clients.status==='fulfilled'?(data(clients.value)?.items||[]):[]
}
function add(){
 if(!auth.hasPermission('customer:account:write')||optionsError.value||customersError.value||createBusy.value||actionBusy.value){
  ElMessage.warning('客户或账号类型选项尚未核实，不能创建账号')
  return
 }
 Object.assign(form,{customerId:null,username:'',password:'',accountTypeCode:'DEFAULT'})
 dialog.value=true
}
async function save(){
 if(createBusy.value||actionBusy.value||!dialog.value||!auth.hasPermission('customer:account:write')||optionsError.value||customersError.value)return
 const payload={customerId:form.customerId,username:String(form.username||'').trim(),
  password:String(form.password||''),accountTypeCode:String(form.accountTypeCode||'')}
 if(!payload.customerId||!payload.username||payload.password.length<6||
    !customers.value.some(c=>Number(c.id)===Number(payload.customerId))||
    !options.value.accountTypes?.some((x:any)=>x.item_code===payload.accountTypeCode)){
  ElMessage.warning('请核对客户、账号类型、用户名和至少6位密码');return
 }
 createBusy.value=true
 try{
  await customerApi.createAccount(payload)
  form.password='';dialog.value=false
  ElMessage.success('客户账号创建请求已成功，请在列表核对')
  await load()
 }catch{ElMessage.error('创建账号未确认成功，请先核对账号及操作审计，避免重复创建')}
 finally{createBusy.value=false}
}
type AccountSnapshot={id:number;username:string;customerId:number;status:string;version:number}
function snapshot(row:any):AccountSnapshot|null{
 if(actionBusy.value||createBusy.value||listLoading.value||listError.value||!auth.hasPermission('customer:account:write'))return null
 if(!rows.value.some(r=>Number(r.id)===Number(row.id)))return null
 return {id:Number(row.id),username:String(row.username||''),customerId:Number(row.customer_id||row.customerId||0),
  status:String(row.status||''),version:listVersion.value}
}
function valid(s:AccountSnapshot){
 const current=rows.value.find(r=>Number(r.id)===s.id)
 return !listError.value&&!listLoading.value&&listVersion.value===s.version&&
  !!current&&String(current.username||'')===s.username&&
  Number(current.customer_id||current.customerId||0)===s.customerId&&current.status===s.status&&
  auth.hasPermission('customer:account:write')
}
function canceled(e:any){return e==='cancel'||e==='close'||e?.toString?.()==='cancel'}
async function reset(row:any){
 const s=snapshot(row);if(!s)return
 actionBusy.value=true
 try{
  const {value:password}=await ElMessageBox.prompt('目标账号：'+s.username+'。请输入新密码','重置密码',{
   inputType:'password',inputPattern:/.{6,}/,inputErrorMessage:'密码至少6位'
  })
  if(!valid(s)){ElMessage.warning('账号记录或权限已变化，密码重置未提交');return}
  const {value:reason}=await ElMessageBox.prompt('目标账号：'+s.username+'。请输入重置原因','操作审计',{
   inputValidator:(v)=>!!String(v||'').trim()||'必须填写原因'
  })
  if(!valid(s)){ElMessage.warning('账号记录或权限已变化，密码重置未提交');return}
  await customerApi.resetAccountPassword(s.id,String(password),String(reason).trim())
  ElMessage.success('密码重置管理请求已返回，请核对账号审计')
  await load()
 }catch(e:any){if(!canceled(e))ElMessage.error('密码重置未确认成功，请先核对审计，避免重复重置')}
 finally{actionBusy.value=false}
}
async function action(row:any,type:'freezeAccount'|'resumeAccount'|'disableAccount'){
 const s=snapshot(row);if(!s)return
 if(type==='freezeAccount'&&s.status!=='ACTIVE')return
 if(type==='resumeAccount'&&s.status!=='FROZEN')return
 if(type==='disableAccount'&&s.status==='DISABLED')return
 const label=type==='freezeAccount'?'冻结':type==='resumeAccount'?'恢复':'停用'
 actionBusy.value=true
 try{
  const {value}=await ElMessageBox.prompt('目标账号：'+s.username+'。请输入'+label+'原因','确认'+label+'客户账号',{
   inputValidator:(v)=>!!String(v||'').trim()||'必须填写原因'
  })
  if(!valid(s)){ElMessage.warning('确认期间账号、状态或权限已变化，操作未提交');return}
  await customerApi[type](s.id,String(value).trim())
  ElMessage.success(label+'请求已返回，请核对账号状态与审计')
  await load()
 }catch(e:any){if(!canceled(e))ElMessage.error(label+'未确认成功，请核对账号和操作审计后再重试')}
 finally{actionBusy.value=false}
}
onMounted(()=>{void init()})
</script>
<template><div class="page"><div class="head"><div><h2>客户账号</h2><p>管理客户登录/API账号。服务Token、VPN私钥等凭证不存放在此表。</p></div><el-button v-if="auth.hasPermission('customer:account:write')" type="primary" :disabled="optionsError||customersError||createBusy||actionBusy" @click="add">创建账号</el-button></div><el-alert v-if="listError" type="error" :closable="false" show-icon title="账号列表读取失败，当前账号状态未知，禁止依据旧记录执行账号操作。"/><el-button v-if="listError" @click="load">重新加载账号列表</el-button><el-alert v-if="optionsError||customersError" type="warning" :closable="false" show-icon title="客户或账号类型选项读取失败，现有账号列表仍可查询，但创建账号必须等选项恢复。"/><el-button v-if="optionsError||customersError" @click="retryChoices">重新加载创建选项</el-button><el-card shadow="never"><div class="filters"><el-input v-model="query.keyword" placeholder="用户名/客户" clearable/><el-select v-model="query.status" placeholder="状态" clearable><el-option label="正常" value="ACTIVE"/><el-option label="冻结" value="FROZEN"/><el-option label="停用" value="DISABLED"/></el-select><el-button type="primary" @click="query.page=1;load()">查询</el-button></div><el-table :data="rows" v-loading="listLoading" :empty-text="listError?'账号列表读取失败':'没有符合条件的账号记录'" stripe><el-table-column prop="username" label="用户名"/><el-table-column prop="customer_name" label="客户"/><el-table-column prop="customer_code" label="客户编号" width="160"/><el-table-column prop="account_type_code" label="账号类型" width="110"/><el-table-column prop="status" label="状态" width="90"/><el-table-column prop="last_login_at" label="最后登录" width="190"/><el-table-column label="操作" width="250"><template #default="s"><template v-if="auth.hasPermission('customer:account:write')"><el-button link :disabled="actionBusy||createBusy" @click="reset(s.row)">重置密码</el-button><el-button v-if="s.row.status==='ACTIVE'" link type="warning" :disabled="actionBusy||createBusy" @click="action(s.row,'freezeAccount')">冻结</el-button><el-button v-if="s.row.status==='FROZEN'" link type="success" :disabled="actionBusy||createBusy" @click="action(s.row,'resumeAccount')">恢复</el-button><el-button v-if="s.row.status!=='DISABLED'" link type="danger" :disabled="actionBusy||createBusy" @click="action(s.row,'disableAccount')">停用</el-button></template></template></el-table-column></el-table><div class="pager"><el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" layout="total, prev, pager, next" @change="load"/></div></el-card>
<el-dialog v-model="dialog" title="创建客户账号" width="560px"><el-form label-width="100px"><el-form-item label="客户"><el-select v-model="form.customerId" filterable style="width:100%"><el-option v-for="c in customers" :key="c.id" :label="`${c.customer_code} · ${c.customer_name}`" :value="c.id"/></el-select></el-form-item><el-form-item label="用户名"><el-input v-model="form.username"/></el-form-item><el-form-item label="初始密码"><el-input v-model="form.password" type="password" show-password/></el-form-item><el-form-item label="账号类型"><el-select v-model="form.accountTypeCode" style="width:100%"><el-option v-for="x in options.accountTypes" :key="x.item_code" :label="x.item_name" :value="x.item_code"/></el-select></el-form-item></el-form><template #footer><el-button :disabled="createBusy" @click="dialog=false">取消</el-button><el-button type="primary" :loading="createBusy" :disabled="optionsError||customersError" @click="save">创建</el-button></template></el-dialog></div></template>
<style scoped>.page{display:grid;gap:16px}.head{display:flex;justify-content:space-between}.head h2{margin:0 0 6px}.head p{margin:0;color:#7b8494}.filters{display:flex;gap:10px;margin-bottom:14px}.filters .el-input{max-width:320px}.filters .el-select{width:180px}.pager{display:flex;justify-content:flex-end;margin-top:14px}</style>
