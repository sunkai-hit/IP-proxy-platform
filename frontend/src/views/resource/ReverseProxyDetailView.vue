<script setup lang="ts">
import { onMounted,ref } from 'vue'
import { useRoute,useRouter } from 'vue-router'
import { externalDeliveryApi } from '@/api/externalDelivery'
const route=useRoute(),router=useRouter(),row=ref<any>({}),loading=ref(false),id=Number(route.params.id),data=(r:any)=>r.data.data
async function load(){loading.value=true;try{row.value=data(await externalDeliveryApi.reverseProxy(id))||{}}finally{loading.value=false}}onMounted(load)
</script><template><div class="page"><div class="head"><div><h2>反向代理详情</h2><p>展示上游套餐、平台代理入口、客户服务绑定及运行状态。</p></div><el-button @click="router.back()">返回</el-button></div><el-card shadow="never" v-loading="loading"><el-descriptions :column="2" border><el-descriptions-item v-for="(v,k) in row" :key="String(k)" :label="String(k)">{{typeof v==='object'?JSON.stringify(v):v}}</el-descriptions-item></el-descriptions></el-card></div></template><style scoped>.page{display:grid;gap:16px}.head{display:flex;justify-content:space-between}.head h2{margin:0 0 6px}.head p{margin:0;color:#7b8494}</style>