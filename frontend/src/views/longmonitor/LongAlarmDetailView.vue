<script setup lang="ts">
import { onMounted,ref } from 'vue'
import { useRoute,useRouter } from 'vue-router'
import { ElMessage,ElMessageBox } from 'element-plus'
import { longMonitorApi } from '@/api/longMonitor'
const route=useRoute(),router=useRouter(),row=ref<any>({}),loading=ref(false),id=Number(route.params.id),data=(r:any)=>r.data.data
async function load(){loading.value=true;try{row.value=data(await longMonitorApi.alarm(id))||{}}finally{loading.value=false}}
async function close(){const {value}=await ElMessageBox.prompt('请输入关闭原因','关闭告警',{inputValidator:v=>!!String(v||'').trim()||'必须填写原因'});await longMonitorApi.closeAlarm(id,String(value));ElMessage.success('告警已关闭');load()}onMounted(load)
</script><template><div class="page"><div class="head"><div><h2>长效告警详情</h2><p>查看异常快照、恢复信息和客户服务关系。</p></div><div><el-button @click="router.back()">返回</el-button><el-button v-if="row.status!=='CLOSED'" type="primary" @click="close">关闭告警</el-button></div></div><el-card shadow="never" v-loading="loading"><el-descriptions :column="2" border><el-descriptions-item v-for="(v,k) in row" :key="String(k)" :label="String(k)">{{typeof v==='object'?JSON.stringify(v):v}}</el-descriptions-item></el-descriptions></el-card></div></template><style scoped>.page{display:grid;gap:16px}.head{display:flex;justify-content:space-between}.head h2{margin:0 0 6px}.head p{margin:0;color:#7b8494}</style>