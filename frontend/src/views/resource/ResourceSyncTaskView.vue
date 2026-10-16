<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { resourceApi } from '@/api/resource'
import { useAuthStore } from '@/stores/auth'
const auth=useAuthStore();const rows=ref<any[]>([]);const total=ref(0);const loading=ref(false);const drawer=ref(false);const task=ref<any>(null);const details=ref<any[]>([]);const loadError=ref(false),taskError=ref(false),detailsError=ref(false),detailLoading=ref(false),requestVersion=ref(0),detailVersion=ref(0),retrying=ref(false);const query=reactive({page:1,size:20,sourceType:'',status:''})
async function load(){
 const version=++requestVersion.value,params={...query}
 loading.value=true;loadError.value=false;rows.value=[];total.value=0
 try{
  const d=(await resourceApi.syncTasks(params)).data.data||{}
  if(version===requestVersion.value){rows.value=d.items||[];total.value=d.total||0}
 }catch{if(version===requestVersion.value)loadError.value=true}
 finally{if(version===requestVersion.value)loading.value=false}
}
async function show(r:any){
 const version=++detailVersion.value,taskId=r.id
 drawer.value=true;detailLoading.value=true;taskError.value=false;detailsError.value=false
 task.value=null;details.value=[]
 const [head,children]=await Promise.allSettled([resourceApi.syncTask(taskId),resourceApi.syncDetails(taskId)])
 if(version!==detailVersion.value)return
 if(head.status==='fulfilled')task.value=head.value.data.data||null
 else taskError.value=true
 if(children.status==='fulfilled')details.value=children.value.data.data||[]
 else detailsError.value=true
 detailLoading.value=false
}
async function retry(r:any){
 if(retrying.value||!auth.hasPermission('resource:sync-task:retry')||!['FAILED','PARTIAL_SUCCESS'].includes(r.status))return
 const {value}=await ElMessageBox.prompt('请输入重试原因','重试同步任务',{inputValidator:v=>!!String(v||'').trim()||'必须填写原因'})
 retrying.value=true
 try{
  const d=(await resourceApi.retrySync(r.id,String(value).trim())).data.data
  if(!d?.taskId){ElMessage.warning('重试请求尚无可查询任务号，不得视为执行成功');return}
  ElMessage.success('已登记新的重试任务 '+d.taskId+'；源资源实际同步结果需在新任务中核查')
  await load()
 }catch{ElMessage.error('同步重试提交失败或回执未知，请先查单避免重复触发')}
 finally{retrying.value=false}
}
onMounted(load)
</script><template><div class="page"><div><h2>资源同步任务</h2><p>记录基础资源和外部资源的同步过程、标准化结果与失败明细，支持问题追踪和失败任务重试。</p></div><el-alert v-if="loadError" type="error" :closable="false" title="同步任务列表读取失败，不能认定没有失败任务或未同步资源。"/><el-button v-if="loadError" @click="load">重新查询同步任务</el-button><el-card shadow="never"><div class="filters"><el-select v-model="query.sourceType" placeholder="同步类型" clearable><el-option v-for="x in ['CENTOS','ROS','LINE','IP','SUPPLIER']" :key="x" :label="x" :value="x"/></el-select><el-select v-model="query.status" placeholder="状态" clearable><el-option v-for="x in ['PENDING','RUNNING','SUCCESS','PARTIAL_SUCCESS','FAILED','CANCELLED']" :key="x" :label="x" :value="x"/></el-select><el-button type="primary" @click="query.page=1;load()">查询</el-button></div><el-table :data="rows" v-loading="loading" :empty-text="loadError?'同步任务查询失败':'暂无已记录同步任务'" stripe><el-table-column prop="task_no" label="任务编号" min-width="190"/><el-table-column prop="source_type" label="类型" width="100"/><el-table-column prop="supplier_name" label="供应商" min-width="140"/><el-table-column prop="status" label="状态" width="130"/><el-table-column prop="received_count" label="接收" width="80"/><el-table-column prop="normalized_count" label="导入" width="80"/><el-table-column prop="available_count" label="可用" width="80"/><el-table-column prop="triggered_by_name" label="触发人" width="100"/><el-table-column prop="created_at" label="创建时间" min-width="175"/><el-table-column label="操作" width="130" fixed="right"><template #default="s"><el-button link type="primary" @click="show(s.row)">详情</el-button><el-button v-if="auth.hasPermission('resource:sync-task:retry')&&['FAILED','PARTIAL_SUCCESS'].includes(s.row.status)" :loading="retrying" link type="warning" @click="retry(s.row)">重试</el-button></template></el-table-column></el-table><div class="pager"><el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" layout="total, sizes, prev, pager, next" @change="load"/></div></el-card><el-drawer v-model="drawer" title="同步任务详情" size="760px"><el-alert v-if="taskError" type="error" :closable="false" title="同步任务主记录读取失败，状态未知。"/><el-alert v-if="detailsError" type="warning" :closable="false" title="同步任务明细读取失败，不能认定所有记录均成功处理。"/><el-descriptions v-if="!taskError&&task" :column="2" border><el-descriptions-item label="任务编号">{{task.task_no}}</el-descriptions-item><el-descriptions-item label="状态">{{task.status}}</el-descriptions-item><el-descriptions-item label="类型">{{task.source_type}}</el-descriptions-item><el-descriptions-item label="供应商">{{task.supplier_name||'—'}}</el-descriptions-item><el-descriptions-item label="接收">{{task.received_count}}</el-descriptions-item><el-descriptions-item label="导入">{{task.normalized_count}}</el-descriptions-item><el-descriptions-item label="错误" :span="2">{{task.error_message||'—'}}</el-descriptions-item></el-descriptions><el-table v-if="!taskError" :data="details" v-loading="detailLoading" :empty-text="detailsError?'同步明细读取失败':'暂无已返回同步明细'" style="margin-top:16px"><el-table-column prop="source_record_id" label="源记录"/><el-table-column prop="resource_type" label="资源类型"/><el-table-column prop="resource_id" label="资源ID"/><el-table-column prop="process_status" label="处理状态"/><el-table-column prop="available_flag" label="可用"/><el-table-column prop="error_message" label="错误" min-width="180"/></el-table></el-drawer></div></template><style scoped>.page{display:grid;gap:16px}.page h2{margin:0 0 6px}.page p{margin:0;color:#7b8494}.filters{display:flex;gap:10px;margin-bottom:14px}.filters>*{max-width:260px}.pager{display:flex;justify-content:flex-end;margin-top:14px}</style>
