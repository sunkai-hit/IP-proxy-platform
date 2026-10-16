<script setup lang="ts">
import { onMounted,ref,watch } from 'vue'
import { useRoute,useRouter } from 'vue-router'
import { monitorApi } from '@/api/runtimeOps'
const route=useRoute(),router=useRouter(),rows=ref<any[]>([]),loading=ref(false),loadError=ref(false),requestVersion=ref(0),data=(r:any)=>r.data.data
let type=String(route.params.type).toUpperCase(),id=Number(route.params.id)
async function load(){
 const version=++requestVersion.value,resourceType=type,resourceId=id
 loading.value=true;loadError.value=false;rows.value=[]
 try{const result=data(await monitorApi.metrics(resourceType,resourceId,{hours:24}))||[];if(version===requestVersion.value)rows.value=result}
 catch{if(version===requestVersion.value){loadError.value=true;rows.value=[]}}
 finally{if(version===requestVersion.value)loading.value=false}
}
function fmt(v:any){return v?new Date(v).toLocaleString():'-'}
watch(()=>route.fullPath,()=>{type=String(route.params.type).toUpperCase();id=Number(route.params.id);load()})
onMounted(load)
</script><template><div class="page"><div class="head"><div><h2>{{type}} 监控详情</h2><p v-if="type==='CENTOS'">展示最近24小时CentOS主机指标及B/C段统计；管理动作与IP段聚合归属CentOS。</p><p v-else>展示最近24小时ROS运行与拨号指标；ROS不再承担B/C段统计及主备切换。</p></div><el-button @click="router.back()">返回</el-button></div><el-alert v-if="loadError" type="error" :closable="false" title="设备监控历史读取失败，不能据此认定设备正常或没有异常事件。"/><el-button v-if="loadError" @click="load">重新加载监控历史</el-button><el-card v-if="!loadError" shadow="never" v-loading="loading"><el-table :data="rows" empty-text="当前没有已上报的监控采样记录" stripe><el-table-column label="采集时间" width="190"><template #default="s">{{fmt(s.row.collected_at)}}</template></el-table-column><el-table-column prop="metric_code" label="指标" min-width="180"/><el-table-column label="值" min-width="160"><template #default="s">{{s.row.metric_value??s.row.metric_text??'-'}}</template></el-table-column><el-table-column prop="labels" label="标签" min-width="200"/></el-table></el-card></div></template><style scoped>.page{display:grid;gap:16px}.head{display:flex;justify-content:space-between}.head h2{margin:0 0 6px}.head p{margin:0;color:#7b8494}</style>
