<script setup lang="ts">
import { onMounted,reactive,ref } from 'vue'
import { orderServiceApi } from '@/api/orderService'
const rows=ref<any[]>([]),total=ref(0);const q=reactive<any>({page:1,size:20,status:''});const data=(r:any)=>r.data.data
async function load(){const d=data(await orderServiceApi.releases(q));rows.value=d.items;total.value=d.total}onMounted(load)
</script>
<template><div class="page"><div><h2>到期与资源释放</h2><p>记录服务终止/到期后的凭证失效、资源解绑及独享资源待回收结果。</p></div><el-card shadow="never"><div class="filters"><el-select v-model="q.status" clearable placeholder="释放状态"><el-option v-for="x in ['PENDING','PROCESSING','SUCCESS','PARTIAL_SUCCESS','FAILED']" :key="x" :label="x" :value="x"/></el-select><el-button type="primary" @click="load">查询</el-button></div><el-table :data="rows"><el-table-column prop="release_no" label="释放单号" width="190"/><el-table-column prop="service_no" label="服务号" width="190"/><el-table-column prop="customer_name" label="客户"/><el-table-column prop="trigger_type" label="触发方式"/><el-table-column prop="status" label="状态"/><el-table-column prop="error_message" label="异常" min-width="220"/><el-table-column prop="created_at" label="时间" width="190"/></el-table><div class="pager"><el-pagination v-model:current-page="q.page" v-model:page-size="q.size" :total="total" layout="total,prev,pager,next" @change="load"/></div></el-card></div></template>
<style scoped>.page{display:grid;gap:16px}.page h2{margin:0 0 6px}.page p{margin:0;color:#7b8494}.filters{display:flex;gap:10px;margin-bottom:14px}.pager{display:flex;justify-content:flex-end;margin-top:14px}</style>
