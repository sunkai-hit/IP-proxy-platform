<script setup lang="ts">
import { onMounted,ref } from 'vue'
import { useRoute,useRouter } from 'vue-router'
import { monitorApi } from '@/api/runtimeOps'
const route=useRoute(),router=useRouter(),type=String(route.params.type).toUpperCase(),id=Number(route.params.id),rows=ref<any[]>([]),loading=ref(false),data=(r:any)=>r.data.data
async function load(){loading.value=true;try{rows.value=data(await monitorApi.metrics(type,id,{hours:24}))||[]}finally{loading.value=false}}onMounted(load)
</script><template><div class="page"><div class="head"><div><h2>{{type}} 监控详情</h2><p>展示最近24小时主机级指标。V1.2监控中心仅保留CentOS与ROS。</p></div><el-button @click="router.back()">返回</el-button></div><el-card shadow="never" v-loading="loading"><el-table :data="rows" stripe><el-table-column prop="sampled_at" label="采集时间" width="190"/><el-table-column prop="metric_code" label="指标"/><el-table-column prop="metric_value" label="值"/><el-table-column prop="unit" label="单位"/><el-table-column prop="status" label="状态"/></el-table></el-card></div></template><style scoped>.page{display:grid;gap:16px}.head{display:flex;justify-content:space-between}.head h2{margin:0 0 6px}.head p{margin:0;color:#7b8494}</style>