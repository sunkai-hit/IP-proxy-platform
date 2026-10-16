<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { resourceApi } from '@/api/resource'
import { useAuthStore } from '@/stores/auth'
const auth=useAuthStore();const rows=ref<any[]>([]);const total=ref(0);const lines=ref<any[]>([]);const ips=ref<any[]>([]);const loading=ref(false),loadError=ref(false),refsError=ref(false),requestVersion=ref(0),saving=ref(false),refreshingId=ref<number|null>(null);const dialog=ref(false);const editId=ref<number|null>(null);const query=reactive({page:1,size:20,keyword:'',status:''});const form=reactive<any>({domainName:'',currentIpId:null,lineId:null,usageTypeCode:'',updateMode:'AUTO'})
async function load(){
 const version=++requestVersion.value,params={...query}
 loading.value=true;loadError.value=false;rows.value=[];total.value=0
 try{
  const result=(await resourceApi.domains(params)).data.data||{}
  if(version!==requestVersion.value)return
  rows.value=result.items||[];total.value=result.total||0
 }catch{if(version===requestVersion.value){loadError.value=true;rows.value=[];total.value=0}}
 finally{if(version===requestVersion.value)loading.value=false}
}
async function refs(){
 refsError.value=false
 const [linesResult,ipsResult]=await Promise.allSettled([
  resourceApi.baseList('LINE',{page:1,size:500,keyword:'',status:''}),
  resourceApi.baseList('IP',{page:1,size:500,keyword:'',status:'AVAILABLE',sourceType:''})
 ])
 lines.value=linesResult.status==='fulfilled'?(linesResult.value.data.data?.items||[]):[]
 ips.value=ipsResult.status==='fulfilled'?(ipsResult.value.data.data?.items||[]):[]
 refsError.value=linesResult.status==='rejected'||ipsResult.status==='rejected'
}
function add(){
 if(!auth.hasPermission('resource:domain:write'))return
 editId.value=null
 Object.assign(form,{domainName:'',currentIpId:null,lineId:null,usageTypeCode:'',updateMode:'AUTO'})
 dialog.value=true
}
function edit(r:any){
 if(!auth.hasPermission('resource:domain:write'))return
 editId.value=r.id
 Object.assign(form,{domainName:r.domain_name,currentIpId:r.current_ip_id||null,lineId:r.line_id||null,
  usageTypeCode:r.usage_type_code||'',updateMode:r.update_mode})
 dialog.value=true
}
async function save(){
 if(saving.value||!auth.hasPermission('resource:domain:write'))return
 if(refsError.value){ElMessage.error('线路或IP选项加载失败，当前禁止保存不确定的资源关联');return}
 if(!form.domainName||(!form.currentIpId&&!form.lineId)){
  ElMessage.warning('请输入域名并至少关联一个IP或线路');return
 }
 const id=editId.value,payload={...form}
 saving.value=true
 try{
  if(id!==null)await resourceApi.updateDomain(id,payload)
  else await resourceApi.createDomain(payload)
  ElMessage.success('平台域名配置已保存；外部DNS实际生效仍待供应商回执')
  dialog.value=false;await load()
 }catch{ElMessage.error('域名保存未确认成功，请检查后端结果')}
 finally{saving.value=false}
}
async function refresh(r:any){
 if(refreshingId.value!==null||!auth.hasPermission('resource:domain:refresh')||loadError.value)return
 const {value}=await ElMessageBox.prompt('仅更新平台目标IP；目前无已签收DNS适配器，绝不等同于外部DNS变更。请输入原因','刷新域名目标',
  {inputValidator:v=>!!String(v||'').trim()||'必须填写原因'})
 refreshingId.value=r.id
 try{
  const result=(await resourceApi.refreshDomain(r.id,String(value).trim())).data.data||{}
  ElMessage.info(result.message||'平台目标更新请求已返回；外部DNS实际生效仍未知')
  await load()
 }catch{ElMessage.error('域名刷新未确认成功，不能据此认定平台或DNS已更新')}
 finally{refreshingId.value=null}
}
onMounted(()=>{void load();void refs()})
</script><template><div class="page"><div class="head"><div><h2>域名配置</h2><p>维护域名与线路/IP的引用关系；DNS供应商接口未确认前，刷新只更新平台目标IP并进入待更新状态。</p></div><el-button v-if="auth.hasPermission('resource:domain:write')" type="primary" @click="add">新增域名</el-button></div><el-alert v-if="loadError" type="error" :closable="false" title="域名列表读取失败，不能据此认定没有配置域名或DNS已正常。"/><el-button v-if="loadError" @click="load">重新查询域名</el-button><el-alert v-if="refsError" type="warning" :closable="false" title="线路或IP选项读取失败，域名列表可查询，但保存资源关联暂不可用。"/><el-button v-if="refsError" @click="refs">重新加载资源选项</el-button><el-card shadow="never"><div class="filters"><el-input v-model="query.keyword" placeholder="域名" clearable/><el-select v-model="query.status" placeholder="状态" clearable><el-option v-for="x in ['NORMAL','PENDING_UPDATE','FAILED','DISABLED']" :key="x" :label="x" :value="x"/></el-select><el-button type="primary" @click="query.page=1;load()">查询</el-button></div><el-table :data="rows" v-loading="loading" :empty-text="loadError?'域名列表读取失败':'暂无已登记域名'" stripe><el-table-column prop="domain_name" label="域名" min-width="200"/><el-table-column prop="line_name" label="关联线路" min-width="150"/><el-table-column prop="current_ip" label="当前目标IP" width="150"/><el-table-column prop="usage_type_code" label="用途" width="120"/><el-table-column prop="update_mode" label="更新模式" width="100"/><el-table-column prop="status" label="状态" width="140"/><el-table-column prop="last_updated_at" label="最后刷新" min-width="170"/><el-table-column prop="last_error" label="说明" min-width="220" show-overflow-tooltip/><el-table-column label="操作" width="150" fixed="right"><template #default="s"><el-button v-if="auth.hasPermission('resource:domain:write')" link @click="edit(s.row)">编辑</el-button><el-button v-if="auth.hasPermission('resource:domain:refresh')" link type="primary" :loading="refreshingId===s.row.id" @click="refresh(s.row)">刷新目标IP</el-button></template></el-table-column></el-table><div class="pager"><el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" layout="total, prev, pager, next" @change="load"/></div></el-card><el-dialog v-model="dialog" :title="editId?'编辑域名':'新增域名'" width="620px"><el-form label-width="100px"><el-form-item label="域名" required><el-input v-model="form.domainName" :disabled="!!editId"/></el-form-item><el-form-item label="关联线路"><el-select v-model="form.lineId" clearable filterable style="width:100%"><el-option v-for="x in lines" :key="x.id" :label="`${x.resource_name||x.resource_code} / ${x.current_public_ip||'-'}`" :value="x.id"/></el-select></el-form-item><el-form-item label="固定IP"><el-select v-model="form.currentIpId" clearable filterable style="width:100%"><el-option v-for="x in ips" :key="x.id" :label="`${x.ip_address} / ${x.source_type}`" :value="x.id"/></el-select></el-form-item><el-form-item label="用途"><el-input v-model="form.usageTypeCode" placeholder="如 TUNNEL_ENTRY"/></el-form-item><el-form-item label="更新模式"><el-select v-model="form.updateMode"><el-option label="自动" value="AUTO"/><el-option label="人工" value="MANUAL"/><el-option label="API" value="API"/></el-select></el-form-item></el-form><template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" :loading="saving" :disabled="refsError" @click="save">保存</el-button></template></el-dialog></div></template><style scoped>.page{display:grid;gap:16px}.head{display:flex;justify-content:space-between}.head h2{margin:0 0 6px}.head p{margin:0;color:#7b8494;max-width:850px}.filters{display:flex;gap:10px;margin-bottom:14px}.filters>*{max-width:280px}.pager{display:flex;justify-content:flex-end;margin-top:14px}</style>
