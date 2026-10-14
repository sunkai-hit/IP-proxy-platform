<script setup lang="ts">
import { onMounted,reactive,ref,watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { externalDeliveryApi } from '@/api/externalDelivery'
import { useAuthStore } from '@/stores/auth'

const router=useRouter(),auth=useAuthStore()
const rows=ref<any[]>([]),total=ref(0),loading=ref(false),saving=ref(false),drawer=ref(false)
const suppliers=ref<any[]>([]),customers=ref<any[]>([]),paidOrders=ref<any[]>([])
const q=reactive<any>({page:1,size:20,keyword:'',status:''})
const form=reactive<any>({supplierId:null,customerId:null,sourceCustomerOrderId:null,supplierPackageName:'',supplierPackageNo:'',purchaseAccount:'',packageQuota:'',remainingQuota:'',purchaseCost:null,currency:'CNY',effectiveAt:'',expireAt:'',remark:''})
const data=(r:any)=>r.data.data
async function load(){
 loading.value=true
 try{const d=data(await externalDeliveryApi.purchases(q));rows.value=d.items||[];total.value=d.total||0}
 finally{loading.value=false}
}
async function loadOptions(){
 const o=data(await externalDeliveryApi.options())
 suppliers.value=o.suppliers||[]
 customers.value=o.customers||[]
}
async function loadPaidOrders(){
 form.sourceCustomerOrderId=null;paidOrders.value=[]
 if(!form.customerId)return
 try{
  paidOrders.value=data(await externalDeliveryApi.paidOrdersForPurchase(form.customerId))||[]
 }catch{ElMessage.warning('无法读取该客户已核实付款的订单，请检查权限或付款状态')}
}
function add(){Object.assign(form,{supplierId:null,customerId:null,sourceCustomerOrderId:null,supplierPackageName:'',supplierPackageNo:'',purchaseAccount:'',packageQuota:'',remainingQuota:'',purchaseCost:'',currency:'CNY',remark:''});paidOrders.value=[];drawer.value=true}
async function save(){
 if(!form.supplierId||!String(form.supplierPackageName).trim()){ElMessage.warning('请填写供应商和供应商套餐');return}
 if(!form.expireAt){ElMessage.warning('请填写供应商采购到期时间，以验证能否覆盖客户服务期限');return}
 if(form.customerId&&!form.sourceCustomerOrderId){ElMessage.warning('指定客户的采购记录必须关联已核实付款的客户订单');return}
 saving.value=true
 try{
  const payload={...form,customerId:form.customerId||null,sourceCustomerOrderId:form.customerId?form.sourceCustomerOrderId:null,purchaseCost:form.purchaseCost===''||form.purchaseCost==null?null:Number(form.purchaseCost),packageQuota:form.packageQuota||null,remainingQuota:form.remainingQuota||null}
  await externalDeliveryApi.createPurchase(payload)
  ElMessage.success('供应商采购记录已保存；不代表平台自动向供应商付款')
  drawer.value=false;await load()
 }finally{saving.value=false}
}
watch(()=>form.customerId,loadPaidOrders)
onMounted(async()=>{await Promise.all([load(),loadOptions()])})
</script>
<template>
<div class="page">
 <div class="head"><div><h2>外部套餐 / 资源采购</h2><p>只登记运营已取得的供应商套餐。新增客户专属采购必须先核实客户付款；平台不自动购买。</p></div><el-button v-if="auth.hasPermission('resource:purchase:write')" type="primary" @click="add">登记采购</el-button></div>
 <el-card shadow="never">
  <div class="filters"><el-input v-model="q.keyword" placeholder="采购单号/套餐/客户" clearable/><el-select v-model="q.status" placeholder="状态" clearable><el-option v-for="x in ['DRAFT','ACTIVE','EXPIRED','DISABLED']" :key="x" :label="x" :value="x"/></el-select><el-button type="primary" @click="q.page=1;load()">查询</el-button></div>
  <el-table :data="rows" v-loading="loading" stripe>
   <el-table-column prop="purchase_no" label="采购编号" width="180"/><el-table-column prop="supplier_name" label="供应商"/><el-table-column prop="supplier_package_name" label="供应商套餐"/><el-table-column prop="customer_name" label="归属客户" min-width="130"><template #default="s">{{s.row.customer_name||'未指定客户的库存'}}</template></el-table-column>
   <el-table-column prop="effective_at" label="生效时间" width="180"/><el-table-column prop="expire_at" label="到期时间" width="180"/><el-table-column prop="status" label="状态" width="110"/>
   <el-table-column label="操作" width="90"><template #default="s"><el-button link type="primary" @click="router.push('/supplier-purchases/'+s.row.id)">详情</el-button></template></el-table-column>
  </el-table>
  <div class="pager"><el-pagination v-model:current-page="q.page" v-model:page-size="q.size" :total="total" layout="total,sizes,prev,pager,next" @change="load"/></div>
 </el-card>
 <el-drawer v-model="drawer" title="登记已有外部采购" size="560px" append-to-body>
  <el-alert title="请先在供应商侧完成真实购买并保存凭证。指定客户时必须已核实该客户订单付款；成本和额度仅可选备注，不触发业务。" type="warning" :closable="false" show-icon/>
  <el-form :model="form" label-position="top" style="margin-top:18px">
   <el-form-item label="供应商" required><el-select v-model="form.supplierId" filterable style="width:100%" placeholder="请选择已有供应商"><el-option v-for="s in suppliers" :key="s.id" :label="s.name+' · '+s.code" :value="s.id"/></el-select></el-form-item>
   <el-form-item label="归属客户"><el-select v-model="form.customerId" clearable filterable style="width:100%" placeholder="未指定：库存登记"><el-option v-for="c in customers" :key="c.id" :label="c.name+' · '+c.code" :value="c.id"/></el-select></el-form-item>
   <el-form-item v-if="form.customerId" label="已付款客户订单" required><el-select v-model="form.sourceCustomerOrderId" filterable style="width:100%" placeholder="只允许选择已核实付款的订单"><el-option v-for="o in paidOrders" :key="o.id" :label="o.order_no+' · '+o.total_amount+' '+o.currency" :value="o.id"/></el-select><el-text type="info" size="small">付款核实请在订单详情由授权人员完成；此处不会发起付款。</el-text></el-form-item>
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
  <template #footer><el-button @click="drawer=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存采购记录</el-button></template>
 </el-drawer>
</div>
</template>
<style scoped>
.page{display:grid;gap:16px}.head{display:flex;justify-content:space-between;align-items:center}.head h2{margin:0 0 6px}.head p{margin:0;color:#7b8494}.filters{display:flex;gap:10px;margin-bottom:14px}.filters>*{max-width:260px}.pager{display:flex;justify-content:flex-end;margin-top:14px}
</style>