<script setup lang="ts">
import { computed,onMounted,ref,watch } from 'vue'
import { useRoute,useRouter } from 'vue-router'
import { resourceApi } from '@/api/resource'
const route=useRoute(),router=useRouter(),row=ref<any>({}),loading=ref(false),loadError=ref(false),requestVersion=ref(0),kind=computed(()=>String(route.meta.detailType||''))
const title=computed(()=>kind.value==='EXCLUSIVE'?'独享资源详情':kind.value==='DOMAIN'?'域名详情':'外部供应商详情')
const data=(r:any)=>r.data.data
async function load(){
 const version=++requestVersion.value,resourceId=Number(route.params.id),type=kind.value
 loading.value=true;loadError.value=false;row.value={}
 try{
  const response=type==='EXCLUSIVE'?await resourceApi.exclusiveDetail(resourceId):
   type==='DOMAIN'?await resourceApi.domain(resourceId):await resourceApi.supplier(resourceId)
  if(version===requestVersion.value)row.value=data(response)||{}
 }catch{if(version===requestVersion.value){loadError.value=true;row.value={}}}
 finally{if(version===requestVersion.value)loading.value=false}
}
watch(()=>route.fullPath,load)
onMounted(load)
</script>
<template><div class="page"><div class="head"><div><h2>{{title}}</h2><p>展示当前记录完整业务信息和关联关系。</p></div><el-button @click="router.back()">返回</el-button></div><el-alert v-if="loadError" type="error" :closable="false" title="资源详情读取失败，无法核实当前配置与关联资源。"/><el-button v-if="loadError" @click="load">重新加载资源详情</el-button><el-card v-if="!loadError&&Object.keys(row).length" shadow="never" v-loading="loading"><el-descriptions :column="2" border><el-descriptions-item v-for="(v,k) in row" :key="String(k)" :label="String(k)"><span class="value">{{typeof v==='object'?JSON.stringify(v):v}}</span></el-descriptions-item></el-descriptions></el-card></div></template>
<style scoped>.page{display:grid;gap:16px}.head{display:flex;justify-content:space-between}.head h2{margin:0 0 6px}.head p{margin:0;color:#7b8494}.value{word-break:break-all}</style>