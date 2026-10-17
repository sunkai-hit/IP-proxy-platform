<script setup lang="ts">
import {onMounted,reactive,ref} from 'vue'
import {useRouter} from 'vue-router'
import {ElMessage,ElMessageBox} from 'element-plus'
import {resourceApi} from '@/api/resource'
import {useAuthStore} from '@/stores/auth'

const router=useRouter(),auth=useAuthStore()
const rows=ref<any[]>([]),total=ref(0),loading=ref(false),loadError=ref(false),listVersion=ref(0)
const dialog=ref(false),editId=ref<number|null>(null),saving=ref(false),actionBusy=ref(false),formVersion=ref(0)
const options=ref<any>({regions:[],carriers:[]}),optionsError=ref(false),optionsBusy=ref(false)
const query=reactive({page:1,size:20,keyword:'',status:''})
const form=reactive<any>({supplierCode:'',supplierName:'',contactName:'',contactPhone:'',
  apiEndpoint:'',authTypeCode:'TOKEN',apiAccount:'',secret:'',regionCodes:[],carrierCodes:[],fetchMode:'MANUAL'})
const actions:{[key:string]:{label:string,permission:string}}={
 test:{label:'测试配置',permission:'resource:supplier:test'},
 enable:{label:'启用',permission:'resource:supplier:status'},
 pause:{label:'暂停',permission:'resource:supplier:status'},
 disable:{label:'停用',permission:'resource:supplier:status'},
 sync:{label:'获取外部资源',permission:'resource:sync'}
}
async function load(){
 const version=++listVersion.value,params={...query}
 loading.value=true;loadError.value=false;rows.value=[];total.value=0
 try{
  const d=(await resourceApi.suppliers(params)).data.data
  if(version!==listVersion.value)return
  if(!d||!Array.isArray(d.items)||!Number.isFinite(Number(d.total)))throw new Error('SUPPLIER_LIST_INVALID')
  rows.value=d.items;total.value=Number(d.total)
 }catch{if(version===listVersion.value){loadError.value=true;rows.value=[];total.value=0}}
 finally{if(version===listVersion.value)loading.value=false}
}
async function loadOptions(){
 optionsBusy.value=true;optionsError.value=false;options.value={regions:[],carriers:[]}
 try{
  const d=(await resourceApi.options()).data.data
  if(!d||!Array.isArray(d.regions)||!Array.isArray(d.carriers))throw new Error('SUPPLIER_OPTIONS_INVALID')
  options.value=d
 }catch{optionsError.value=true}
 finally{optionsBusy.value=false}
}
function codes(value:any):string[]{
 if(Array.isArray(value))return value.map(String)
 if(typeof value==='string'){try{const arr=JSON.parse(value);return Array.isArray(arr)?arr.map(String):[]}catch{return []}}
 return []
}
function reset(){Object.assign(form,{supplierCode:'',supplierName:'',contactName:'',contactPhone:'',
 apiEndpoint:'',authTypeCode:'TOKEN',apiAccount:'',secret:'',regionCodes:[],carrierCodes:[],fetchMode:'MANUAL'})}
function add(){
 if(saving.value||loading.value||loadError.value||optionsError.value||optionsBusy.value||
   !auth.hasPermission('resource:supplier:write'))return
 ++formVersion.value;editId.value=null;reset();dialog.value=true
}
function edit(r:any){
 if(saving.value||loading.value||loadError.value||optionsError.value||optionsBusy.value||
    !auth.hasPermission('resource:supplier:write')||
    !rows.value.some(x=>Number(x.id)===Number(r?.id)&&x.supplier_code===r.supplier_code))return
 ++formVersion.value;editId.value=Number(r.id)
 reset()
 Object.assign(form,{supplierCode:r.supplier_code,supplierName:r.supplier_name,
  contactName:r.contact_name||'',contactPhone:r.contact_phone||'',apiEndpoint:r.api_endpoint,
  authTypeCode:r.auth_type_code,apiAccount:r.api_account||'',secret:'',
  regionCodes:codes(r.region_codes),carrierCodes:codes(r.carrier_codes),
  fetchMode:r.fetch_mode})
 dialog.value=true
}
async function save(){
 if(!dialog.value||saving.value||actionBusy.value||loading.value||loadError.value||
    optionsError.value||optionsBusy.value||!auth.hasPermission('resource:supplier:write'))return
 if(!form.supplierCode||!form.supplierName||!form.apiEndpoint||!form.authTypeCode){
  ElMessage.warning('请填写必填项');return
 }
 const id=editId.value,version=formVersion.value
 if(id&&!rows.value.some(x=>Number(x.id)===id&&x.supplier_code===form.supplierCode)){
  ElMessage.error('编辑目标已变化，禁止直接保存');return
 }
 const payload={...form,regionCodes:[...form.regionCodes],carrierCodes:[...form.carrierCodes]}
 saving.value=true
 try{
  if(id)await resourceApi.updateSupplier(id,payload)
  else await resourceApi.createSupplier(payload)
  if(version===formVersion.value){dialog.value=false;form.secret=''}
  ElMessage.success('供应商配置写入请求已返回，请核对列表和操作审计')
  await load()
 }catch{ElMessage.error('供应商配置未确认保存成功；请核对供应商与审计记录再重试')}
 finally{saving.value=false}
}
function closeDialog(){if(saving.value)return;++formVersion.value;dialog.value=false;form.secret=''}
async function action(r:any,type:'test'|'enable'|'pause'|'disable'|'sync'){
 const a=actions[type]
 const supplierId=Number(r?.id),code=String(r?.supplier_code||''),name=String(r?.supplier_name||'')
 if(!a||actionBusy.value||saving.value||loading.value||loadError.value||
   !auth.hasPermission(a.permission)||!Number.isSafeInteger(supplierId)||supplierId<=0||
   !rows.value.some(x=>Number(x.id)===supplierId&&x.supplier_code===code&&x.status===r.status))return
 const version=listVersion.value,status=String(r.status||'')
 actionBusy.value=true
 try{
  const {value}=await ElMessageBox.prompt(
   `目标供应商：${name}（${code}，ID ${supplierId}）。当前状态：${status}。请填写「${a.label}」原因；停用/暂停前须核对关联采购与服务影响。`,a.label,
   {inputValidator:v=>!!String(v||'').trim()||'必须填写原因',
    confirmButtonText:'确认提交',cancelButtonText:'返回检查'}
  )
  if(listVersion.value!==version||loadError.value||loading.value||
     !auth.hasPermission(a.permission)||!rows.value.some(x=>
      Number(x.id)===supplierId&&x.supplier_code===code&&x.status===status)){
   ElMessage.warning('确认期间供应商记录、状态或查询范围发生变化，未提交操作');return
  }
  const reason=String(value).trim()
  if(type==='test'){
   const res=(await resourceApi.testSupplier(supplierId,reason)).data.data
   ElMessage.info('供应商接口测试请求已返回：'+String(res?.message||'请核查接口结果'))
  }else if(type==='enable')await resourceApi.enableSupplier(supplierId,reason)
  else if(type==='pause')await resourceApi.pauseSupplier(supplierId,reason)
  else if(type==='disable')await resourceApi.disableSupplier(supplierId,reason)
  else{
   const d=(await resourceApi.createSync({sourceType:'SUPPLIER',supplierId,
       targetPoolId:null,reason})).data.data
   if(!Number.isSafeInteger(Number(d?.taskId))||Number(d.taskId)<=0)
     throw new Error('SYNC_TASK_ID_INVALID')
   ElMessage.warning(`外部资源获取任务已登记（任务ID ${d.taskId}），请在同步任务详情核对完成/失败与来源证据`)
  }
  if(type!=='test'&&type!=='sync')ElMessage.success('供应商状态变更请求已返回，请核对列表与关联业务审计')
  if(version===listVersion.value)await load()
 }catch(e:any){
  if(e!=='cancel'&&e!=='close')ElMessage.error('供应商操作未确认成功；请先核对任务或审计，避免盲目重复执行')
 }finally{actionBusy.value=false}
}
onMounted(()=>{void loadOptions();void load()})
</script><template><div class="page"><div class="head"><div><h2>外部供应商</h2><p>只保存供应商接入配置和加密凭证；实际接口协议通过 ResourceAdapter 对接，供应商密钥不会回显。</p></div><el-button v-if="auth.hasPermission('resource:supplier:write')" type="primary"  :disabled="saving||actionBusy||loading||loadError||optionsError||optionsBusy" @click="add">新增供应商</el-button></div><el-alert v-if="loadError" type="error" :closable="false" title="供应商列表读取失败，不能据此判断供应商不存在、可停用或可同步。"/><el-button v-if="loadError" @click="load">重新查询供应商列表</el-button><el-alert v-if="optionsError" type="warning" :closable="false" title="地区与运营商选项读取失败，暂停供应商新增与编辑；现有供应商列表仍可查询。"/><el-button v-if="optionsError" @click="loadOptions">重试加载地区与运营商</el-button><el-card shadow="never"><div class="filters"><el-input v-model="query.keyword" placeholder="编码/名称" clearable/><el-select v-model="query.status" placeholder="状态" clearable><el-option label="启用" value="ACTIVE"/><el-option label="暂停" value="PAUSED"/><el-option label="停用" value="DISABLED"/></el-select><el-button type="primary" @click="query.page=1;load()">查询</el-button></div><el-table :data="rows" v-loading="loading" :empty-text="loadError?'供应商查询失败':'暂无已登记供应商'" stripe><el-table-column prop="supplier_code" label="编码" width="140"/><el-table-column label="供应商" min-width="160"><template #default="s"><el-button link type="primary" @click="router.push('/suppliers/'+s.row.id)">{{s.row.supplier_name}}</el-button></template></el-table-column><el-table-column prop="api_endpoint" label="接口地址" min-width="220" show-overflow-tooltip/><el-table-column prop="auth_type_code" label="认证" width="100"/><el-table-column prop="fetch_mode" label="获取模式" width="100"/><el-table-column label="密钥" width="85"><template #default="s"><el-tag :type="s.row.secret_configured?'success':'info'">{{s.row.secret_configured?'已配置':'未配置'}}</el-tag></template></el-table-column><el-table-column prop="interface_status" label="接口状态" width="100"/><el-table-column prop="status" label="状态" width="90"/><el-table-column label="操作" min-width="280" fixed="right"><template #default="s"><el-button v-if="auth.hasPermission('resource:supplier:write')" link :disabled="actionBusy||saving||loading||loadError||optionsError||optionsBusy" @click="edit(s.row)">编辑</el-button><el-button v-if="auth.hasPermission('resource:supplier:test')" link type="primary" :disabled="actionBusy||saving||loading||loadError" @click="action(s.row,'test')">测试</el-button><el-button v-if="auth.hasPermission('resource:sync')&&s.row.status==='ACTIVE'" link type="primary" :disabled="actionBusy||saving||loading||loadError" @click="action(s.row,'sync')">获取资源</el-button><el-button v-if="auth.hasPermission('resource:supplier:status')&&s.row.status==='PAUSED'" link type="success" :disabled="actionBusy||saving||loading||loadError" @click="action(s.row,'enable')">启用</el-button><el-button v-if="auth.hasPermission('resource:supplier:status')&&s.row.status==='ACTIVE'" link type="warning" :disabled="actionBusy||saving||loading||loadError" @click="action(s.row,'pause')">暂停</el-button><el-button v-if="auth.hasPermission('resource:supplier:status')&&s.row.status!=='DISABLED'" link type="danger" :disabled="actionBusy||saving||loading||loadError" @click="action(s.row,'disable')">停用</el-button></template></el-table-column></el-table><div class="pager"><el-pagination v-model:current-page="query.page" v-model:page-size="query.size"  :total="total" layout="total, sizes, prev, pager, next" @change="load"/></div></el-card><el-dialog v-model="dialog" :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving" :title="editId?'编辑供应商':'新增供应商'" width="680px"><el-form label-width="105px"><el-form-item label="供应商编码" required><el-input v-model="form.supplierCode" :disabled="!!editId"/></el-form-item><el-form-item label="供应商名称" required><el-input v-model="form.supplierName"/></el-form-item><el-form-item label="联系人"><el-input v-model="form.contactName"/></el-form-item><el-form-item label="联系电话"><el-input v-model="form.contactPhone"/></el-form-item><el-form-item label="API地址" required><el-input v-model="form.apiEndpoint"/></el-form-item><el-form-item label="认证类型" required><el-input v-model="form.authTypeCode" placeholder="TOKEN / API_KEY / ..."/></el-form-item><el-form-item label="API账号"><el-input v-model="form.apiAccount"/></el-form-item><el-form-item label="密钥"><el-input v-model="form.secret" type="password" show-password :placeholder="editId?'留空表示不修改':'输入供应商密钥'"/></el-form-item><el-form-item label="获取模式"><el-select v-model="form.fetchMode"><el-option label="定时" value="SCHEDULED"/><el-option label="实时" value="REALTIME"/><el-option label="人工" value="MANUAL"/></el-select></el-form-item><el-form-item label="地区"><el-select v-model="form.regionCodes" multiple style="width:100%"><el-option v-for="x in options.regions" :key="x.item_code" :label="x.item_name" :value="x.item_code"/></el-select></el-form-item><el-form-item label="运营商"><el-select v-model="form.carrierCodes" multiple style="width:100%"><el-option v-for="x in options.carriers" :key="x.item_code" :label="x.item_name" :value="x.item_code"/></el-select></el-form-item></el-form><template #footer><el-button :disabled="saving" @click="closeDialog">取消</el-button><el-button type="primary" :loading="saving" :disabled="saving||optionsError||optionsBusy" @click="save">保存</el-button></template></el-dialog></div></template><style scoped>.page{display:grid;gap:16px}.head{display:flex;justify-content:space-between}.head h2{margin:0 0 6px}.head p{margin:0;color:#7b8494;max-width:850px}.filters{display:flex;gap:10px;margin-bottom:14px}.filters>*{max-width:280px}.pager{display:flex;justify-content:flex-end;margin-top:14px}</style>
