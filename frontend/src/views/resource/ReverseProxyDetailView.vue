<script setup lang="ts">
import {onMounted,ref} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {ElMessage,ElMessageBox} from 'element-plus'
import {externalDeliveryApi} from '@/api/externalDelivery'
import {useAuthStore} from '@/stores/auth'

const route=useRoute(),router=useRouter(),auth=useAuthStore(),id=Number(route.params.id)
const row=ref<any>({}),loading=ref(false),busy=ref(false)
const data=(r:any)=>r.data.data,fmt=(v:any)=>v==null||v===''?'—':String(v)
const mask=(v:any)=>v?('••••'+String(v).slice(-4)):'—'
async function load(){
 loading.value=true
 try{row.value=data(await externalDeliveryApi.reverseProxy(id))||{}}
 finally{loading.value=false}
}
async function action(kind:'apply'|'test'|'disable'){
 const title={apply:'应用平台代理配置',test:'验证平台代理连通性',disable:'真实停用平台代理入口'}[kind]
 const {value}=await ElMessageBox.prompt('请输入操作原因。没有真实网关验证的返回将被后端拒绝，配置登记状态不等于成功。',title,
  {inputValidator:(v:string)=>!!String(v||'').trim()||'原因不能为空'})
 busy.value=true
 try{
  if(kind==='apply')await externalDeliveryApi.applyReverseProxy(id,String(value).trim())
  if(kind==='test')await externalDeliveryApi.testReverseProxy(id,String(value).trim())
  if(kind==='disable')await externalDeliveryApi.disableReverseProxy(id,String(value).trim())
  ElMessage.success('已收到并通过后端真实回执校验')
  await load()
 }finally{busy.value=false}
}
onMounted(load)
</script>
<template>
<div class="page">
 <div class="head">
  <div>
   <el-button link @click="router.push('/reverse-proxies')">← 返回代理列表</el-button>
   <h2>{{row.proxy_no||'反向代理详情'}}</h2>
   <p>供应商采购 → 平台反向代理 → 客户服务。代理配置状态和真实网络可连接性须分开核对。</p>
  </div>
  <div class="actions">
   <el-button @click="load">刷新</el-button>
   <el-button v-if="auth.hasPermission('resource:reverse-proxy:test')" :loading="busy" @click="action('test')">真实连通性验证</el-button>
   <el-button v-if="auth.hasPermission('resource:reverse-proxy:status')&&row.status!=='ACTIVE'" :loading="busy" type="primary" @click="action('apply')">应用配置</el-button>
   <el-button v-if="auth.hasPermission('resource:reverse-proxy:status')&&row.status!=='DISABLED'" :loading="busy" type="danger" plain @click="action('disable')">受控停用</el-button>
  </div>
 </div>
 <el-alert type="warning" :closable="false" show-icon
   title="配置保存、状态ACTIVE或适配器收到传输ACK，不足以证明真实交付；未通过真实网关核验时后台操作必须拒绝。"/>
 <el-card shadow="never" v-loading="loading">
  <template #header><b>平台代理主记录</b></template>
  <el-descriptions :column="3" border>
   <el-descriptions-item label="代理编号">{{fmt(row.proxy_no)}}</el-descriptions-item>
   <el-descriptions-item label="协议">{{fmt(row.protocol_code)}}</el-descriptions-item>
   <el-descriptions-item label="登记状态"><el-tag :type="row.status==='ACTIVE'?'success':'warning'">{{fmt(row.status)}}</el-tag></el-descriptions-item>
   <el-descriptions-item label="采购上游">
    <el-button v-if="auth.hasPermission('resource:purchase:read')" link type="primary" @click="router.push('/supplier-purchases/'+row.purchase_id)">{{fmt(row.purchase_no)}}</el-button>
    <span v-else>{{fmt(row.purchase_no)}}</span>
   </el-descriptions-item>
   <el-descriptions-item label="供应商">{{fmt(row.supplier_name)}}</el-descriptions-item>
   <el-descriptions-item label="客户">
    <el-button v-if="auth.hasPermission('customer:read')" link type="primary" @click="router.push('/customers/'+row.customer_id)">{{fmt(row.customer_name)}}</el-button>
    <span v-else>{{fmt(row.customer_name)}}</span>
   </el-descriptions-item>
   <el-descriptions-item label="服务">
    <el-button v-if="row.service_id&&auth.hasPermission('service:read')" link type="primary" @click="router.push('/services/'+row.service_id)">{{fmt(row.service_no)}}</el-button>
    <span v-else>{{fmt(row.service_no)}}</span>
   </el-descriptions-item>
   <el-descriptions-item label="平台入口">{{fmt(row.platform_host)}}:{{fmt(row.platform_port)}}</el-descriptions-item>
   <el-descriptions-item label="平台账户（掩码）">{{mask(row.platform_account)}}</el-descriptions-item>
   <el-descriptions-item label="上游凭证">{{row.upstreamSecretConfigured?'加密配置，禁止回显':'未配置'}}</el-descriptions-item>
   <el-descriptions-item label="平台凭证">{{row.platformSecretConfigured?'加密配置，禁止回显':'未配置'}}</el-descriptions-item>
   <el-descriptions-item label="供应商生效">{{fmt(row.effective_at)}}</el-descriptions-item>
   <el-descriptions-item label="代理配置截止">{{fmt(row.expire_at)}}</el-descriptions-item>
   <el-descriptions-item label="最近验证时间">{{fmt(row.last_checked_at)}}</el-descriptions-item>
   <el-descriptions-item label="备注">{{fmt(row.remark)}}</el-descriptions-item>
  </el-descriptions>
 </el-card>
 <el-card shadow="never">
  <template #header><b>代理操作/网关验证记录</b></template>
  <el-alert type="info" :closable="false" title="记录中的管理面创建与修改不代表网关实际启停；使用动作和服务详情核查真实验证来源。"/>
  <el-table :data="row.operationLogs||[]" border stripe style="margin-top:12px" empty-text="暂无平台代理操作日志">
   <el-table-column prop="operation_type" label="动作" min-width="145"/>
   <el-table-column prop="status" label="登记结果" width="115"/>
   <el-table-column prop="operator_name" label="操作人" min-width="125"/>
   <el-table-column prop="message" label="信息" min-width="225" show-overflow-tooltip/>
   <el-table-column prop="created_at" label="操作时间" min-width="165"/>
  </el-table>
 </el-card>
</div>
</template>
<style scoped>
.page{display:grid;gap:16px}.head,.actions{display:flex;align-items:center;justify-content:space-between;gap:10px;flex-wrap:wrap}.head h2{margin:8px 0}.head p{margin:0;color:#7b8494}.actions{max-width:640px}
</style>