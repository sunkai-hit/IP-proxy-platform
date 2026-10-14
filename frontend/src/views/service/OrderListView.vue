<script setup lang="ts">
import { onMounted,reactive,ref,watch } from 'vue'
import { useRoute,useRouter } from 'vue-router'
import { ElMessage,ElMessageBox } from 'element-plus'
import { orderServiceApi } from '@/api/orderService'
import { customerApi } from '@/api/customer'
import { productApi } from '@/api/product'
import { useAuthStore } from '@/stores/auth'
const route=useRoute(),router=useRouter(),auth=useAuthStore(),rows=ref<any[]>([]),total=ref(0),loading=ref(false),dialog=ref(false),saving=ref(false),customers=ref<any[]>([]),products=ref<any[]>([]),orderLines=ref<any[]>([]),renewTargets=ref<any[]>([])
const q=reactive<any>({page:1,size:20,keyword:'',status:'',customerId:route.query.customerId?Number(route.query.customerId):null});const form=reactive<any>({customerId:null,orderType:'NEW',currency:'CNY',remark:'',productId:null,packageId:null,quantity:1,serviceCount:1,renewalServiceId:null})
const data=(r:any)=>r.data.data
async function load(){loading.value=true;try{const d=data(await orderServiceApi.orders(q));rows.value=d.items;total.value=d.total}finally{loading.value=false}}
async function refs(){customers.value=data(await customerApi.list({page:1,size:200,status:'ACTIVE'})).items;products.value=data(await productApi.products({page:1,size:200,status:'ACTIVE'})).items}
let renewLookupVersion=0
const requestedRenewalServiceId=ref<number|null>(null)
async function loadRenewTargets(){
 const request=++renewLookupVersion
 renewTargets.value=[];form.renewalServiceId=null
 if(!form.customerId||form.orderType!=='RENEW')return
 const customerId=Number(form.customerId)
 try{
  const d=data(await orderServiceApi.services({page:1,size:200,customerId}))
  const found=(d.items||[]).filter((v:any)=>
   Number(v.customer_id)===customerId&&!['TERMINATED','PROVISION_FAILED'].includes(v.status))
  const targetId=requestedRenewalServiceId.value
  if(targetId&&!found.some((x:any)=>Number(x.id)===targetId)){
   // A deep-linked service must work even if it is outside the first 200 rows.
   const svc=data(await orderServiceApi.service(targetId))
   if(Number(svc?.customer_id)===customerId &&
      !['TERMINATED','PROVISION_FAILED'].includes(svc.status))found.unshift(svc)
  }
  if(request!==renewLookupVersion||Number(form.customerId)!==customerId||form.orderType!=='RENEW')return
  renewTargets.value=found
  if(targetId&&found.some((x:any)=>Number(x.id)===targetId))
   form.renewalServiceId=targetId
  else if(targetId)ElMessage.warning('原服务不可续费或不属于当前客户，请重新选择')
 }catch{
  if(request===renewLookupVersion)ElMessage.warning('无法查询该客户可续费的服务，请核对权限与服务状态')
 }
}
let lineNumber=0
function newOrderLine(){return {key:++lineNumber,productId:null as number|null,packageId:null as number|null,quantity:1,serviceCount:1,packages:[] as any[]}}
function addOrderLine(){orderLines.value.push(newOrderLine())}
function removeOrderLine(key:number){if(orderLines.value.length>1)orderLines.value=orderLines.value.filter(x=>x.key!==key)}
async function loadLinePackages(line:any){
 const productId=Number(line.productId)
 line.packageId=null;line.packages=[]
 if(!productId)return
 try{
  const r=data(await productApi.packages({page:1,size:200,status:'ACTIVE',productId}))
  if(Number(line.productId)===productId)line.packages=r.items||[]
 }catch{ElMessage.error('无法加载所选产品的在售套餐')}
}
function add(fromRenewal=false){
 const prefilled=fromRenewal&&route.query.orderType==='RENEW'
 requestedRenewalServiceId.value=prefilled&&Number(route.query.renewalServiceId)>0?
  Number(route.query.renewalServiceId):null
 Object.assign(form,{customerId:q.customerId||null,orderType:prefilled?'RENEW':'NEW',currency:'CNY',
  remark:'',productId:null,packageId:null,quantity:1,serviceCount:1,renewalServiceId:null})
 orderLines.value=[newOrderLine()];renewTargets.value=[];dialog.value=true
}
async function save(){
 if(saving.value)return
 if(!form.customerId){ElMessage.warning('请选择客户');return}
 const target=form.orderType==='RENEW'?renewTargets.value.find((x:any)=>Number(x.id)===Number(form.renewalServiceId)):null
 if(form.orderType==='RENEW'&&!target){ElMessage.warning('续费订单必须指定同客户的原服务');return}
 if(!['NEW','RENEW'].includes(form.orderType)){ElMessage.warning('其他变更请从已有服务发起');return}
 if(form.orderType==='NEW'&&orderLines.value.some(x=>!x.productId||!x.packageId||x.quantity<1||x.serviceCount<1||x.serviceCount>100)){
  ElMessage.warning('请为每个订单项选择产品、在售套餐并填写1至100个服务实例');return
 }
 const items=target?[{productId:target.product_id,packageId:target.package_id,quantity:1,serviceCount:1}]:
  orderLines.value.map(x=>({productId:x.productId,packageId:x.packageId,quantity:x.quantity,serviceCount:x.serviceCount}))
 if(target&&!target.package_id){ElMessage.warning('原服务没有可续费套餐，请先核对');return}
 saving.value=true
 try{
  const created=data(await orderServiceApi.createOrder({customerId:form.customerId,orderType:form.orderType,
   renewalServiceId:target?.id||null,currency:form.currency,remark:form.remark,items}))
  dialog.value=false
  ElMessage.success('订单已创建，请在详情中确认并核实付款后继续')
  if(created?.id)await router.push('/orders/'+created.id)
  else await load()
 }catch{ElMessage.error('订单创建未完成，请保留订单项核对后重试')}
 finally{saving.value=false}
}
async function action(r:any,type:string){if(type==='provision'){router.push({path:'/service-provision',query:{orderId:String(r.id)}});return}const name:any={confirm:'确认订单',cancel:'取消订单'};const {value}=await ElMessageBox.prompt('请输入'+name[type]+'原因',name[type],{inputValidator:(v)=>!!String(v||'').trim()||'必须填写原因'});if(type==='confirm')await orderServiceApi.confirmOrder(r.id,String(value));if(type==='cancel')await orderServiceApi.cancelOrder(r.id,String(value));ElMessage.success('操作成功');load()}
watch([()=>form.customerId,()=>form.orderType],loadRenewTargets);onMounted(async()=>{await refs();await load();if(route.query.newOrder==='1'&&auth.hasPermission('order:write'))add(route.query.orderType==='RENEW')})
</script>
<template><div class="page"><div class="head"><div><h2>订单管理</h2><p>订单记录客户购买意图；资源占用和凭证只在服务开通阶段产生。</p></div><el-button v-if="auth.hasPermission('order:write')" type="primary" @click="add()">新增订单</el-button></div><el-card shadow="never"><div class="filters"><el-input v-model="q.keyword" placeholder="订单号/客户" clearable/><el-select v-model="q.customerId" clearable filterable placeholder="筛选客户" style="width:220px"><el-option v-for="c in customers" :key="c.id" :label="c.customer_name" :value="c.id"/></el-select><el-select v-model="q.status" placeholder="状态" clearable><el-option v-for="s in ['PENDING_CONFIRM','PENDING_PROVISION','PROVISIONED','COMPLETED','CANCELLED']" :key="s" :label="s" :value="s"/></el-select><el-button type="primary" @click="q.page=1;load()">查询</el-button></div><el-table :data="rows" v-loading="loading" stripe><el-table-column label="订单号" width="195"><template #default="s"><el-button link type="primary" @click="router.push('/orders/'+s.row.id)">{{s.row.order_no}}</el-button></template></el-table-column><el-table-column prop="customer_name" label="客户" min-width="160"/><el-table-column prop="order_type" label="类型" width="100"/><el-table-column label="金额" width="120"><template #default="s">{{ s.row.total_amount }} {{ s.row.currency }}</template></el-table-column><el-table-column prop="item_total" label="订单项" width="80"/><el-table-column prop="service_total" label="服务数" width="80"/><el-table-column prop="status" label="状态" width="150"/><el-table-column label="付款核实" width="125"><template #default="s"><el-tag :type="s.row.payment_status==='PAID_CONFIRMED'?'success':'warning'">{{s.row.payment_status==='PAID_CONFIRMED'?'已核实':'待核实'}}</el-tag></template></el-table-column><el-table-column prop="created_at" label="创建时间" width="190"/><el-table-column label="操作" width="300" fixed="right"><template #default="s"><el-button v-if="auth.hasPermission('order:status')&&s.row.status==='PENDING_CONFIRM'" link type="primary" @click="action(s.row,'confirm')">确认</el-button><el-button v-if="auth.hasPermission('order:provision')&&s.row.status==='PENDING_PROVISION'&&s.row.order_type==='NEW'" link type="success" @click="action(s.row,'provision')">开通</el-button><el-button v-if="auth.hasPermission('order:status')&&!['PROVISIONED','COMPLETED','CANCELLED'].includes(s.row.status)" link type="danger" @click="action(s.row,'cancel')">取消</el-button></template></el-table-column></el-table><div class="pager"><el-pagination v-model:current-page="q.page" v-model:page-size="q.size" :total="total" layout="total,sizes,prev,pager,next" @change="load"/></div></el-card>
<el-dialog v-model="dialog" title="新增订单" width="680px"><el-form label-width="110px"><el-form-item label="客户" required><el-select v-model="form.customerId" filterable style="width:100%"><el-option v-for="x in customers" :key="x.id" :label="`${x.customer_code} · ${x.customer_name}`" :value="x.id"/></el-select></el-form-item><el-form-item label="订单类型"><el-select v-model="form.orderType" style="width:100%"><el-option label="NEW · 新购" value="NEW"/><el-option label="RENEW · 原服务续费" value="RENEW"/></el-select></el-form-item><el-form-item v-if="form.orderType==='RENEW'" label="续费服务" required>
 <el-select v-model="form.renewalServiceId" filterable placeholder="选择该客户需要续费的服务（单服务单订单）" style="width:100%">
  <el-option v-for="svc in renewTargets" :key="svc.id" :value="svc.id" :label="`${svc.service_no} · ${svc.product_name} / ${svc.package_name||'无套餐'}`"/>
 </el-select>
 </el-form-item><el-alert v-if="form.orderType==='RENEW'" type="info" :closable="false" title="续费订单直接继承原服务的产品和套餐、数量固定为1。确认订单不是付款；请先核实客户收款，再办理供应商续购和真实恢复。" style="margin-bottom:12px"/>
 <div v-if="form.orderType==='NEW'" class="line-items">
 <div class="line-head"><b>订单项明细（{{orderLines.length}}项）</b><el-button type="primary" plain @click="addOrderLine">增加订单项</el-button></div>
 <el-card v-for="(line,index) in orderLines" :key="line.key" shadow="never" class="line-item">
  <div class="line-head"><b>第{{index+1}}项</b><el-button v-if="orderLines.length>1" type="danger" link @click="removeOrderLine(line.key)">删除该项</el-button></div>
  <el-form-item label="产品" required><el-select v-model="line.productId" filterable style="width:100%" @change="loadLinePackages(line)">
   <el-option v-for="x in products" :key="x.id" :label="`${x.product_name} (${x.product_type})`" :value="x.id"/>
  </el-select></el-form-item>
  <el-form-item label="套餐" required><el-select v-model="line.packageId" filterable placeholder="选择当前产品的在售套餐" style="width:100%">
   <el-option v-for="x in line.packages" :key="x.id" :label="`${x.package_name} / ${x.price} ${x.currency}`" :value="x.id"/>
  </el-select></el-form-item>
  <el-form-item label="购买数量"><el-input-number v-model="line.quantity" :min="1"/></el-form-item>
  <el-form-item label="生成服务数"><el-input-number v-model="line.serviceCount" :min="1" :max="100"/></el-form-item>
 </el-card>
 </div><el-form-item label="备注"><el-input v-model="form.remark" type="textarea"/></el-form-item></el-form><template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="save">创建订单</el-button></template></el-dialog></div></template>
<style scoped>.page{display:grid;gap:16px}.head{display:flex;justify-content:space-between}.head h2{margin:0 0 6px}.head p{margin:0;color:#7b8494}.filters{display:flex;gap:10px;margin-bottom:14px}.filters .el-input,.filters .el-select{max-width:260px}.pager{display:flex;justify-content:flex-end;margin-top:14px}.line-items{margin:10px 0 18px}.line-head{display:flex;justify-content:space-between;align-items:center;margin-bottom:10px}.line-item{margin:10px 0}</style>
