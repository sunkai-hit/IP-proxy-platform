<script setup lang="ts">
import { onMounted,reactive,ref,watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { externalDeliveryApi } from '@/api/externalDelivery'
import { useAuthStore } from '@/stores/auth'

const router=useRouter(),auth=useAuthStore()
const rows=ref<any[]>([]),total=ref(0),loading=ref(false),saving=ref(false),drawer=ref(false)
const suppliers=ref<any[]>([]),customers=ref<any[]>([]),paidOrders=ref<any[]>([])
const loadError=ref(false),optionsError=ref(false),optionsLoading=ref(true),paidOrdersBusy=ref(false),paidOrdersError=ref(false)
let createSession=0
let listRequestVersion=0
const q=reactive<any>({page:1,size:20,keyword:'',status:''})
const form=reactive<any>({supplierId:null,customerId:null,sourceCustomerOrderId:null,supplierPackageName:'',supplierPackageNo:'',purchaseAccount:'',packageQuota:'',remainingQuota:'',purchaseCost:null,currency:'CNY',effectiveAt:'',expireAt:'',remark:''})
const data=(r:any)=>r.data.data
async function load(){
 const version=++listRequestVersion
 loading.value=true;loadError.value=false
 // Old purchase records are not trustworthy during a new filtered request.
 rows.value=[];total.value=0
 try{
  const d=data(await externalDeliveryApi.purchases({...q}))
  if(version===listRequestVersion){
   if(!d||!Array.isArray(d.items)||!Number.isFinite(Number(d.total)))throw new Error('PURCHASE_LIST_INVALID')
   rows.value=d.items;total.value=Number(d.total)
  }
 }catch{
  if(version===listRequestVersion){loadError.value=true;rows.value=[];total.value=0}
 }finally{if(version===listRequestVersion)loading.value=false}
}
async function loadOptions(){
 optionsLoading.value=true;optionsError.value=false
 try{
  const o=data(await externalDeliveryApi.options())
  if(!o||!Array.isArray(o.suppliers)||!Array.isArray(o.customers))throw new Error('PURCHASE_OPTIONS_INVALID')
  suppliers.value=o.suppliers
  customers.value=o.customers
 }catch{
  optionsError.value=true;suppliers.value=[];customers.value=[]
 }finally{optionsLoading.value=false}
}
let paidOrderRequestVersion=0
async function loadPaidOrders(){
 const version=++paidOrderRequestVersion,session=createSession
 const customerId=Number(form.customerId)
 form.sourceCustomerOrderId=null;paidOrders.value=[];paidOrdersError.value=false
 if(!Number.isSafeInteger(customerId)||customerId<=0){
  paidOrdersBusy.value=false;return
 }
 paidOrdersBusy.value=true
 try{
  const candidates=data(await externalDeliveryApi.paidOrdersForPurchase(customerId))
  if(version!==paidOrderRequestVersion||session!==createSession||
     Number(form.customerId)!==customerId||!drawer.value)return
  if(!Array.isArray(candidates))throw new Error('PAID_ORDERS_INVALID')
  paidOrders.value=candidates
 }catch{
  if(version===paidOrderRequestVersion&&session===createSession){
   paidOrdersError.value=true;paidOrders.value=[]
   ElMessage.warning('无法核实当前客户已付款订单，禁止保存采购登记')
  }
 }finally{
  if(version===paidOrderRequestVersion&&session===createSession)paidOrdersBusy.value=false
 }
}
function add(){
 if(saving.value||drawer.value||optionsError.value||optionsLoading.value||
   !auth.hasPermission('resource:purchase:write')){
  ElMessage.warning('采购登记尚未就绪，请重新核对后操作');return
 }
 ++createSession;++paidOrderRequestVersion
 paidOrdersBusy.value=false;paidOrdersError.value=false
 Object.assign(form,{supplierId:null,customerId:null,sourceCustomerOrderId:null,
  supplierPackageName:'',supplierPackageNo:'',purchaseAccount:'',packageQuota:'',
  remainingQuota:'',purchaseCost:null,currency:'CNY',effectiveAt:'',expireAt:'',remark:''})
 paidOrders.value=[];drawer.value=true
}
function closeDrawer(){
 if(saving.value)return
 ++createSession;++paidOrderRequestVersion
 drawer.value=false;paidOrders.value=[];paidOrdersBusy.value=false;paidOrdersError.value=false
}
async function save(){
 if(saving.value||!drawer.value||optionsError.value||optionsLoading.value||
    paidOrdersBusy.value||paidOrdersError.value||
    !auth.hasPermission('resource:purchase:write'))return
 if(!form.supplierId||!suppliers.value.some(x=>Number(x.id)===Number(form.supplierId))||
    !String(form.supplierPackageName).trim()){
  ElMessage.warning('请选择已经核实的供应商并填写供应商套餐');return
 }
 if(!form.expireAt){ElMessage.warning('请填写供应商采购到期时间，以验证能否覆盖客户服务期限');return}
 if(form.customerId&&!customers.value.some(x=>Number(x.id)===Number(form.customerId))){
  ElMessage.warning('当前采购客户不在已核实的可选列表中');return
 }
 if(form.customerId&&!form.sourceCustomerOrderId){
  ElMessage.warning('指定客户的采购记录必须关联已核实付款的客户订单');return
 }
 if(form.customerId&&!paidOrders.value.some(x=>Number(x.id)===Number(form.sourceCustomerOrderId))){
  ElMessage.warning('选中的付款订单不在当前客户的有效列表中，请重新核对');return
 }
 const version=createSession,paidVersion=paidOrderRequestVersion
 const payload={...form,customerId:form.customerId||null,
  sourceCustomerOrderId:form.customerId?form.sourceCustomerOrderId:null,
  purchaseCost:form.purchaseCost===''||form.purchaseCost==null?null:Number(form.purchaseCost),
  packageQuota:form.packageQuota||null,remainingQuota:form.remainingQuota||null}
 saving.value=true
 try{
  if(version!==createSession||paidVersion!==paidOrderRequestVersion||
     !drawer.value||Number(form.customerId||0)!==Number(payload.customerId||0)||
     Number(form.sourceCustomerOrderId||0)!==Number(payload.sourceCustomerOrderId||0))
    throw new Error('PURCHASE_FORM_STALE')
  const created=data(await externalDeliveryApi.createPurchase(payload))
  if(version===createSession)drawer.value=false
  ElMessage.success('采购登记已保存；不代表已自动向供应商付款或交付')
  if(created?.id)await router.push('/supplier-purchases/'+created.id)
  else await load()
 }catch{ElMessage.error('采购登记未确认成功，请检查采购记录及审计后再重试')}
 finally{saving.value=false}
}
watch(()=>form.customerId,loadPaidOrders)
onMounted(()=>{void load();void loadOptions()})
</script>
<template>
<div class="page">
 <div class="head"><div><h2>外部套餐 / 资源采购</h2><p>只登记运营已取得的供应商套餐。新增客户专属采购必须先核实客户付款；平台不自动购买。</p></div><el-button v-if="auth.hasPermission('resource:purchase:write')" type="primary" :disabled="optionsError||optionsLoading||saving||drawer" @click="add">登记采购</el-button></div>
 <el-alert v-if="loadError" type="error" :closable="false" title="采购列表查询失败，不能据此判断没有采购记录或客户资源。" />
 <el-button v-if="loadError" @click="load">重新加载采购列表</el-button>
 <el-alert v-if="optionsError" type="warning" :closable="false" title="供应商或客户选项读取失败，暂不能登记采购；已有采购记录仍可查询。" />
 <el-button v-if="optionsError" @click="loadOptions">重新加载采购选项</el-button>
 <el-card shadow="never">
  <div class="filters"><el-input v-model="q.keyword" placeholder="采购单号/套餐/客户" clearable/><el-select v-model="q.status" placeholder="状态" clearable><el-option v-for="x in ['DRAFT','ACTIVE','EXPIRED','DISABLED']" :key="x" :label="x" :value="x"/></el-select><el-button type="primary" @click="q.page=1;load()">查询</el-button></div>
  <el-table :data="rows" v-loading="loading" stripe>
   <el-table-column prop="purchase_no" label="采购编号" width="180"/><el-table-column prop="supplier_name" label="供应商"/><el-table-column prop="supplier_package_name" label="供应商套餐"/><el-table-column prop="customer_name" label="归属客户" min-width="130"><template #default="s">{{s.row.customer_name||'未指定客户的库存'}}</template></el-table-column>
   <el-table-column prop="effective_at" label="生效时间" width="180"/><el-table-column prop="expire_at" label="到期时间" width="180"/><el-table-column prop="status" label="状态" width="110"/>
   <el-table-column label="操作" width="90"><template #default="s"><el-button link type="primary" @click="router.push('/supplier-purchases/'+s.row.id)">详情</el-button></template></el-table-column>
  </el-table>
  <div class="pager"><el-pagination v-model:current-page="q.page" v-model:page-size="q.size" :total="total" layout="total,sizes,prev,pager,next" @change="load"/></div>
 </el-card>
 <el-drawer v-model="drawer" title="登记已有外部采购" size="560px" append-to-body
  :close-on-click-modal="!saving" :close-on-press-escape="!saving" :show-close="!saving" @closed="closeDrawer">
  <el-alert title="请先在供应商侧完成真实购买并保存凭证。指定客户时必须已核实该客户订单付款；成本和额度仅可选备注，不触发业务。" type="warning" :closable="false" show-icon/>
  <el-form :model="form" label-position="top" style="margin-top:18px">
   <el-form-item label="供应商" required><el-select v-model="form.supplierId" filterable style="width:100%" placeholder="请选择已有供应商"><el-option v-for="s in suppliers" :key="s.id" :label="s.name+' · '+s.code" :value="s.id"/></el-select></el-form-item>
   <el-form-item label="归属客户"><el-select v-model="form.customerId" clearable filterable style="width:100%" placeholder="未指定：库存登记"><el-option v-for="c in customers" :key="c.id" :label="c.name+' · '+c.code" :value="c.id"/></el-select></el-form-item>
   <el-alert v-if="paidOrdersError" type="error" :closable="false" title="客户付款订单读取失败，不能确认当前采购的付款来源。"/><el-form-item v-if="form.customerId" label="已付款客户订单" required><el-select v-model="form.sourceCustomerOrderId" :loading="paidOrdersBusy" :disabled="paidOrdersBusy||paidOrdersError" filterable style="width:100%" placeholder="只允许选择已核实付款的订单"><el-option v-for="o in paidOrders" :key="o.id" :label="o.order_no+' · '+o.total_amount+' '+o.currency" :value="o.id"/></el-select><el-text type="info" size="small">付款核实请在订单详情由授权人员完成；此处不会发起付款。</el-text></el-form-item>
   <el-form-item label="供应商套餐名称" required><el-input v-model="form.supplierPackageName" maxlength="255"/></el-form-item>
   <el-form-item label="供应商套餐编号（可选）"><el-input v-model="form.supplierPackageNo"/></el-form-item>
   <el-form-item label="采购账号（可选）"><el-input v-model="form.purchaseAccount"/></el-form-item>
   <el-form-item label="供应商生效时间"><el-date-picker v-model="form.effectiveAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ssZ" placeholder="可选" style="width:100%"/></el-form-item>
   <el-form-item label="供应商到期时间" required><el-date-picker v-model="form.expireAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ssZ" placeholder="必须据供应商实际有效期填写" style="width:100%"/></el-form-item>
   <el-form-item label="采购成本（选填，不参与业务）"><el-input-number v-model="form.purchaseCost" :min="0" :precision="2" style="width:100%"/></el-form-item>
   <el-form-item label="套餐额度（选填，不扣减）"><el-input v-model="form.packageQuota"/></el-form-item>
   <el-form-item label="剩余额度（选填，不扣减）"><el-input v-model="form.remainingQuota"/></el-form-item>
   <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="1000"/></el-form-item>
  </el-form>
  <template #footer><el-button :disabled="saving" @click="closeDrawer">取消</el-button><el-button type="primary" :loading="saving" :disabled="saving||optionsLoading||optionsError||paidOrdersBusy||paidOrdersError" @click="save">保存采购记录</el-button></template>
 </el-drawer>
</div>
</template>
<style scoped>
.page{display:grid;gap:16px}.head{display:flex;justify-content:space-between;align-items:center}.head h2{margin:0 0 6px}.head p{margin:0;color:#7b8494}.filters{display:flex;gap:10px;margin-bottom:14px}.filters>*{max-width:260px}.pager{display:flex;justify-content:flex-end;margin-top:14px}
</style>