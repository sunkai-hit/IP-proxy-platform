<script setup lang="ts">
import { computed,onMounted,ref } from 'vue'
import { useRoute,useRouter } from 'vue-router'
import { resourceApi } from '@/api/resource'
const route=useRoute(),router=useRouter(),row=ref<any>({}),loading=ref(false),kind=computed(()=>String(route.meta.detailType||'')),id=Number(route.params.id)
const title=computed(()=>kind.value==='EXCLUSIVE'?'独享资源详情':kind.value==='DOMAIN'?'域名详情':'外部供应商详情')
const data=(r:any)=>r.data.data
async function load(){loading.value=true;try{row.value=data(kind.value==='EXCLUSIVE'?await resourceApi.exclusiveDetail(id):kind.value==='DOMAIN'?await resourceApi.domain(id):await resourceApi.supplier(id))||{}}finally{loading.value=false}}
onMounted(load)
</script>
<template><div class="page"><div class="head"><div><h2>{{title}}</h2><p>展示当前记录完整业务信息和关联关系。</p></div><el-button @click="router.back()">返回</el-button></div><el-card shadow="never" v-loading="loading"><el-descriptions :column="2" border><el-descriptions-item v-for="(v,k) in row" :key="String(k)" :label="String(k)"><span class="value">{{typeof v==='object'?JSON.stringify(v):v}}</span></el-descriptions-item></el-descriptions></el-card></div></template>
<style scoped>.page{display:grid;gap:16px}.head{display:flex;justify-content:space-between}.head h2{margin:0 0 6px}.head p{margin:0;color:#7b8494}.value{word-break:break-all}</style>