<script setup lang="ts">
import {computed,onMounted,reactive,ref} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {ElMessage,ElMessageBox} from 'element-plus'
import {externalDeliveryApi} from '@/api/externalDelivery'
import {useAuthStore} from '@/stores/auth'

const auth=useAuthStore(),router=useRouter(),route=useRoute()
const rows=ref<any[]>([]),total=ref(0),loading=ref(false),saving=ref(false),drawer=ref(false)
const purchases=ref<any[]>([]),services=ref<any[]>([])
const q=reactive<any>({page:1,size:20,keyword:'',status:''})
const form=reactive<any>({purchaseId:null,customerId:null,serviceId:null,protocolCode:'HTTP',
 upstreamHost:'',upstreamPort:8080,upstreamAccount:'',upstreamSecret:'',
 platformHost:'',platformPort:8080,platformAccount:'',platformSecret:'',
 expireAt:'',remark:''})
const data=(r:any)=>r.data.data
const selectedPurchase=computed(()=>purchases.value.find(x=>Number(x.id)===Number(form.purchaseId)))
const isPayEligible=computed(()=>Boolean(selectedPurchase.value?.customer_id))
function maskHost(x:any){return x?'••••'+String(x).slice(-4):'—'}
async function load(){loading.value=true;try{
 const d=data(await externalDeliveryApi.reverseProxies(q))
 rows.value=d.items||[];total.value=d.total||0
}finally{loading.value=false}}
let purchaseLookupVersion=0
async function openCreate(){
 ++purchaseLookupVersion
 Object.assign(form,{purchaseId:null,customerId:null,serviceId:null,protocolCode:'HTTP',
  upstreamHost:'',upstreamPort:8080,upstreamAccount:'',upstreamSecret:'',
  platformHost:'',platformPort:8080,platformAccount:'',platformSecret:'',
  expireAt:'',remark:''})
 const options=data(await externalDeliveryApi.options())
 purchases.value=options.purchases||[]
 services.value=[]
 const purchaseId=Number(route.query.purchaseId||0)
 if(purchaseId>0&&purchases.value.some(x=>Number(x.id)===purchaseId))form.purchaseId=purchaseId
 if(form.purchaseId)await purchaseChanged()
 drawer.value=true
}
async function purchaseChanged(){
 const version=++purchaseLookupVersion
 form.serviceId=null;services.value=[]
 const p=selectedPurchase.value
 const purchaseId=Number(p?.id)
 const customerId=Number(p?.customer_id)
 form.customerId=customerId||null
 if(!customerId)return
 try{
  const o=data(await externalDeliveryApi.options(customerId))
  // Do not show services from the previous customer if the selected
  // supplier purchase changed before the response was received.
  if(version!==purchaseLookupVersion||Number(form.purchaseId)!==purchaseId||
     Number(form.customerId)!==customerId)return
  services.value=o.services||[]
 }catch{
  if(version===purchaseLookupVersion)
   ElMessage.error('无法读取采购客户的服务清单，禁止猜测服务归属')
 }
}
async function save(){
 if(saving.value)return
 if(!selectedPurchase.value){ElMessage.warning('请选择已经登记的供应商客户专属采购');return}
 if(!isPayEligible.value){ElMessage.warning('库存采购没有关联付款客户，不允许直接创建客户反向代理');return}
 if(Number(form.customerId)!==Number(selectedPurchase.value.customer_id)||
    (form.serviceId&&!services.value.some(x=>Number(x.id)===Number(form.serviceId)))){
  ElMessage.warning('采购归属或目标服务已变化，请重新选择');return
 }
 if(!form.upstreamHost.trim()||!form.platformHost.trim()||!form.upstreamPort||!form.platformPort){
  ElMessage.warning('请填写有效的上游与平台代理入口');return
 }
 saving.value=true
 try{
  const payload={...form,customerId:Number(selectedPurchase.value.customer_id),purchaseId:Number(form.purchaseId)}
  const result=data(await externalDeliveryApi.createReverseProxy(payload))
  drawer.value=false
  ElMessage.success('平台代理配置已登记，尚未通过真实网关交付验证')
  if(result?.id)await router.push('/reverse-proxies/'+result.id)
  else await load()
 }finally{saving.value=false}
}
async function action(r:any,type:'apply'|'test'|'disable'){
 const title={apply:'应用平台代理配置',test:'真实网关连通性验证',disable:'受控停用平台代理'}[type]
 const {value}=await ElMessageBox.prompt('请输入原因；只有网关真实回执验证通过后操作才可能成功。',title,{
   inputValidator:v=>!!String(v||'').trim()||'必须填写原因'
 })
 if(type==='apply')await externalDeliveryApi.applyReverseProxy(r.id,String(value))
 if(type==='test')await externalDeliveryApi.testReverseProxy(r.id,String(value))
 if(type==='disable')await externalDeliveryApi.disableReverseProxy(r.id,String(value))
 ElMessage.success('真实网关验证通过')
 await load()
}
onMounted(async()=>{await load();if(route.query.newProxy==='1'&&auth.hasPermission('resource:reverse-proxy:write'))await openCreate()})
</script>
<template>
<div class="page">
 <div class="head"><div><h2>反向代理交付</h2>
  <p>客户已付款的专属供应商采购 → 平台反向代理配置 → 客户服务。仅配置登记不代表真实交付。</p>
 </div><el-button v-if="auth.hasPermission('resource:reverse-proxy:write')" type="primary" @click="openCreate">登记反向代理</el-button></div>
 <el-alert type="warning" :closable="false" title="仅能使用同客户、已核实付款来源的有效采购；无网关真实验证回执时无法将平台代理标记为已交付。"/>
 <el-card shadow="never">
  <div class="filters">
   <el-input v-model="q.keyword" placeholder="代理编号/客户/平台入口" clearable/>
   <el-select v-model="q.status" clearable placeholder="登记状态">
    <el-option v-for="s in ['PENDING','ACTIVE','ABNORMAL','DISABLED']" :key="s" :value="s" :label="s"/>
   </el-select>
   <el-button type="primary" @click="q.page=1;load()">查询</el-button>
  </div>
  <el-table :data="rows" v-loading="loading" stripe>
   <el-table-column label="代理编号" min-width="180"><template #default="s">
    <el-button link type="primary" @click="router.push('/reverse-proxies/'+s.row.id)">{{s.row.proxy_no}}</el-button>
   </template></el-table-column>
   <el-table-column prop="customer_name" label="客户" min-width="120"/>
   <el-table-column prop="supplier_name" label="供应商" min-width="120"/>
   <el-table-column label="上游地址（脱敏）" min-width="120"><template #default="s">{{maskHost(s.row.upstream_host)}}</template></el-table-column>
   <el-table-column label="平台代理入口" min-width="170"><template #default="s">{{s.row.platform_host}}:{{s.row.platform_port}}</template></el-table-column>
   <el-table-column prop="protocol_code" label="协议" width="90"/>
   <el-table-column prop="status" label="登记状态" width="110"/>
   <el-table-column label="操作" width="225"><template #default="s">
    <el-button link type="primary" @click="router.push('/reverse-proxies/'+s.row.id)">详情</el-button>
    <el-button v-if="auth.hasPermission('resource:reverse-proxy:test')" link @click="action(s.row,'test')">真实测试</el-button>
    <el-button v-if="auth.hasPermission('resource:reverse-proxy:status')&&s.row.status!=='ACTIVE'" link type="success" @click="action(s.row,'apply')">应用</el-button>
    <el-button v-if="auth.hasPermission('resource:reverse-proxy:status')&&s.row.status!=='DISABLED'" link type="danger" @click="action(s.row,'disable')">停用</el-button>
   </template></el-table-column>
  </el-table>
  <div class="pager"><el-pagination v-model:current-page="q.page" v-model:page-size="q.size" :total="total" layout="total,sizes,prev,pager,next" @change="load"/></div>
 </el-card>
 <el-drawer v-model="drawer" title="登记客户平台代理配置" size="580px" append-to-body>
  <el-alert type="warning" :closable="false" title="此操作只保存平台代理配置，不会自动采购、应用网关或完成服务交付；库存采购不得绕过客户收款。"/>
  <el-form :model="form" label-position="top" style="margin-top:14px">
   <el-form-item label="客户专属已付款采购" required>
    <el-select v-model="form.purchaseId" clearable filterable style="width:100%" placeholder="选择客户已付款来源采购" @change="purchaseChanged">
     <el-option v-for="p in purchases.filter(x=>x.customer_id)" :key="p.id" :value="p.id"
      :label="p.code+' · '+p.name+' · '+p.supplier_name"/>
    </el-select>
   </el-form-item>
   <el-alert v-if="form.purchaseId&&!isPayEligible" type="error" :closable="false" title="此采购未绑定专属客户，不可用于当前操作。"/>
   <el-form-item label="所属客户"><el-input :model-value="selectedPurchase?.customer_id?String(selectedPurchase.customer_id):'待选择采购'" disabled/></el-form-item>
   <el-form-item label="关联已有客户服务（可选）">
    <el-select v-model="form.serviceId" clearable filterable style="width:100%" placeholder="只显示采购对应客户的服务">
     <el-option v-for="svc in services" :key="svc.id" :label="svc.name" :value="svc.id"/>
    </el-select>
   </el-form-item>
   <el-form-item label="代理协议" required><el-select v-model="form.protocolCode" style="width:100%">
    <el-option label="HTTP" value="HTTP"/><el-option label="SOCKS5" value="SOCKS5"/>
   </el-select></el-form-item>
   <el-form-item label="供应商上游地址" required><el-input v-model="form.upstreamHost"/></el-form-item>
   <el-form-item label="供应商上游端口" required><el-input-number v-model="form.upstreamPort" :min="1" :max="65535"/></el-form-item>
   <el-form-item label="上游账号（可选）"><el-input v-model="form.upstreamAccount"/></el-form-item>
   <el-form-item label="上游密钥（选填，不回显）"><el-input v-model="form.upstreamSecret" type="password" show-password autocomplete="new-password"/></el-form-item>
   <el-form-item label="平台代理域名或IP" required><el-input v-model="form.platformHost"/></el-form-item>
   <el-form-item label="平台代理端口" required><el-input-number v-model="form.platformPort" :min="1" :max="65535"/></el-form-item>
   <el-form-item label="平台账号（可选）"><el-input v-model="form.platformAccount"/></el-form-item>
   <el-form-item label="平台代理密钥（选填，不回显）"><el-input v-model="form.platformSecret" type="password" show-password autocomplete="new-password"/></el-form-item>
   <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2"/></el-form-item>
  </el-form>
  <template #footer><el-button @click="drawer=false">取消</el-button>
   <el-button type="primary" :disabled="!isPayEligible" :loading="saving" @click="save">保存代理配置（未交付）</el-button>
  </template>
 </el-drawer>
</div>
</template>
<style scoped>
.page{display:grid;gap:14px}.head,.filters{display:flex;justify-content:space-between;align-items:center;gap:10px;flex-wrap:wrap}
.head h2{margin:0 0 6px}.head p{margin:0;color:#7b8494}.filters>*{max-width:265px}
.pager{display:flex;justify-content:flex-end;margin-top:14px}
</style>