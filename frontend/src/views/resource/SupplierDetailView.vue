<script setup lang="ts">
import {onMounted,ref,watch} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {resourceApi} from '@/api/resource'
import {externalDeliveryApi} from '@/api/externalDelivery'
import {useAuthStore} from '@/stores/auth'
import {ElMessage} from 'element-plus'

const route=useRoute(),router=useRouter(),auth=useAuthStore();let id=Number(route.params.id)
const row=ref<any>({}),purchases=ref<any[]>([]),total=ref(0),loading=ref(false),childError=ref(false),loadError=ref(false),requestVersion=ref(0)
const data=(r:any)=>r.data.data,fmt=(x:any)=>x==null||x===''?'—':String(x)
const mask=(x:any)=>x?('••••'+String(x).slice(-4)):'—'
async function load(){
 const version=++requestVersion.value,supplierId=id
 loading.value=true;loadError.value=false;childError.value=false
 row.value={};purchases.value=[];total.value=0
 try{
  const details=data(await resourceApi.supplier(supplierId))||{}
  if(version!==requestVersion.value)return
  row.value=details
  if(auth.hasPermission('resource:purchase:read')){
   try{
    const result=data(await externalDeliveryApi.purchases({page:1,size:200,supplierId}))||{}
    if(version!==requestVersion.value)return
    purchases.value=result.items||[];total.value=result.total||0
   }catch{if(version===requestVersion.value){childError.value=true;purchases.value=[];total.value=0}}
  }
 }catch{if(version===requestVersion.value){loadError.value=true;row.value={};purchases.value=[];total.value=0}}
 finally{if(version===requestVersion.value)loading.value=false}
}
watch(()=>route.params.id,()=>{id=Number(route.params.id);load()})
onMounted(load)
</script>
<template>
<div class="page">
 <div class="head">
  <div><el-button link @click="router.push('/suppliers')">← 返回供应商列表</el-button>
   <h2>{{row.supplier_name||'供应商详情'}}</h2>
   <p>供应商基本资料、已登记采购套餐及下游交付链；上游接口能力以实际适配器证据为准。</p></div>
  <el-button @click="load">刷新</el-button>
 </div>
 <el-alert v-if="loadError" type="error" :closable="false" title="供应商详情读取失败，无法核实接口状态、认证配置与下游采购。"/><el-button v-if="loadError" @click="load">重新加载供应商详情</el-button>
 <el-card v-if="!loadError&&row.supplier_code" shadow="never" v-loading="loading">
  <template #header><b>供应商主记录</b></template>
  <el-descriptions :column="3" border>
   <el-descriptions-item label="供应商编码">{{fmt(row.supplier_code)}}</el-descriptions-item>
   <el-descriptions-item label="供应商名称">{{fmt(row.supplier_name)}}</el-descriptions-item>
   <el-descriptions-item label="运营状态">{{fmt(row.status)}}</el-descriptions-item>
   <el-descriptions-item label="接口检测">{{fmt(row.interface_status)}}</el-descriptions-item>
   <el-descriptions-item label="认证类型">{{fmt(row.auth_type_code)}}</el-descriptions-item>
   <el-descriptions-item label="密钥">{{row.secret_configured?'已加密配置（不可回显）':'未配置'}}</el-descriptions-item>
   <el-descriptions-item label="联系人">{{fmt(row.contact_name)}}</el-descriptions-item>
   <el-descriptions-item label="联系电话（掩码）">{{mask(row.contact_phone)}}</el-descriptions-item>
   <el-descriptions-item label="采集模式">{{fmt(row.fetch_mode)}}</el-descriptions-item>
   <el-descriptions-item label="最近成功">{{fmt(row.last_success_at)}}</el-descriptions-item>
   <el-descriptions-item label="最近失败">{{fmt(row.last_failure_at)}}</el-descriptions-item>
   <el-descriptions-item label="创建时间">{{fmt(row.created_at)}}</el-descriptions-item>
  </el-descriptions>
 </el-card>
 <el-card v-if="!loadError&&row.supplier_code&&auth.hasPermission('resource:purchase:read')" shadow="never">
  <template #header><div class="head"><b>关联采购套餐（共 {{total}} 条）</b><el-button @click="router.push('/supplier-purchases')">进入采购管理</el-button></div></template>
  <el-alert type="info" :closable="false" title="采购记录、供应商接口状态和真实代理交付是不同事实。需要进入采购详情检查反向代理及客户服务。"/>
  <el-alert v-if="childError" type="warning" :closable="false" title="采购关联查询失败，不能据此认定供应商没有下游服务。"/>
  <el-table :data="purchases" border stripe style="margin-top:12px">
   <el-table-column prop="purchase_no" label="采购编号" min-width="170"/>
   <el-table-column prop="supplier_package_name" label="套餐名" min-width="150"/>
   <el-table-column prop="customer_name" label="客户归属" min-width="125"/>
   <el-table-column prop="effective_at" label="开始" min-width="160"/>
   <el-table-column prop="expire_at" label="结束" min-width="160"/>
   <el-table-column prop="status" label="登记状态" width="115"/>
   <el-table-column label="操作" width="110"><template #default="s"><el-button link type="primary" @click="router.push('/supplier-purchases/'+s.row.id)">采购详情</el-button></template></el-table-column>
  </el-table>
  <el-text v-if="total>200" type="warning">仅显示最近200条采购，其他记录请在采购列表筛选供应商。</el-text>
 </el-card>
</div>
</template>
<style scoped>
.page{display:grid;gap:16px}.head{display:flex;align-items:center;justify-content:space-between;gap:10px}.head h2{margin:8px 0}.head p{margin:0;color:#7b8494}
</style>