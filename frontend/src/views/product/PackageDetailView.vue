<script setup lang="ts">
import { onMounted,ref } from 'vue'
import { useRoute,useRouter } from 'vue-router'
import { productApi } from '@/api/product'
const route=useRoute(),router=useRouter(),row=ref<any>({}),loading=ref(false),data=(r:any)=>r.data.data
async function load(){loading.value=true;try{row.value=data(await productApi.pkg(Number(route.params.id)))||{}}finally{loading.value=false}}onMounted(load)
</script><template><div class="page"><div class="head"><div><h2>套餐详情</h2><p>套餐描述销售周期、额度和价格，不承载可配置的资源调度策略。</p></div><el-button @click="router.back()">返回</el-button></div><el-card shadow="never" v-loading="loading"><el-descriptions :column="2" border><el-descriptions-item v-for="(v,k) in row" :key="String(k)" :label="String(k)">{{typeof v==='object'?JSON.stringify(v):v}}</el-descriptions-item></el-descriptions></el-card></div></template><style scoped>.page{display:grid;gap:16px}.head{display:flex;justify-content:space-between}.head h2{margin:0 0 6px}.head p{margin:0;color:#7b8494}</style>