<script setup lang="ts">
import {computed,onMounted,ref,watch} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {ElMessage,ElMessageBox} from 'element-plus'
import {orderServiceApi} from '@/api/orderService'
import {businessRelationsApi} from '@/api/businessRelations'
import {useAuthStore} from '@/stores/auth'

const route=useRoute(),router=useRouter(),auth=useAuthStore();let id=Number(route.params.id)
const row=ref<any>({}),relatedServices=ref<any[]>([]),relatedError=ref(false),truncated=ref(false)
const loading=ref(false),working=ref(false),tab=ref('items'),detailError=ref(false),requestVersion=ref(0)
const data=(r:any)=>r.data.data
const renewalTarget=computed(()=>relatedServices.value.find(s=>s.relation_type==='RENEWAL_TARGET'))
const isPaid=computed(()=>row.value.payment_status==='PAID_CONFIRMED')
const canChange=computed(()=>auth.hasPermission('order:status'))
const canPay=computed(()=>auth.hasPermission('order:payment:verify'))
const canProvision=computed(()=>auth.hasPermission('order:provision')&&auth.hasPermission('service:provision'))
const canRenew=computed(()=>auth.hasPermission('service:renew')&&auth.hasPermission('service:change'))
const fmt=(v:any)=>v==null||v===''?'—':String(v)
const orderReady=computed(()=>!loading.value&&!detailError.value&&id>0&&Number(row.value.id)===id)
function actionSnapshot(permission:string){
 if(!orderReady.value||working.value||!auth.hasPermission(permission)){
  ElMessage.warning('订单资料或操作权限尚未核实，不能提交')
  return null
 }
 return {id,version:requestVersion.value,number:String(row.value.order_no||id),
  status:row.value.status,payment:row.value.payment_status}
}
function sameOrder(s:{id:number,version:number,status:any,payment:any},permission:string){
 return orderReady.value&&id===s.id&&requestVersion.value===s.version&&
  row.value.status===s.status&&row.value.payment_status===s.payment&&
  auth.hasPermission(permission)
}

async function load(){
 const version=++requestVersion.value,orderId=id
 loading.value=true;detailError.value=false;relatedError.value=false;truncated.value=false
 row.value={};relatedServices.value=[]
 try{
  const detail=data(await orderServiceApi.order(orderId))||{}
  if(version!==requestVersion.value)return
  row.value=detail
  if(auth.hasPermission('service:read')){
   try{
    const sub=data(await businessRelationsApi.orderServices(orderId))
    if(version!==requestVersion.value)return
    relatedServices.value=sub.items||[];truncated.value=!!sub.truncated
   }catch{
    if(version===requestVersion.value){relatedError.value=true;relatedServices.value=[]}
   }
  }
 }catch{
  if(version===requestVersion.value){detailError.value=true;row.value={};relatedServices.value=[]}
 }finally{if(version===requestVersion.value)loading.value=false}
}
async function act(type:'confirm'|'cancel'|'provision'){
 const permission=type==='provision'?'order:provision':'order:status'
 const snapshot=actionSnapshot(permission)
 if(!snapshot)return
 if(type==='provision'){
  if(!canProvision.value||snapshot.status!=='PENDING_PROVISION'||!isPaid.value||
     row.value.order_type!=='NEW')return
  await router.push({path:'/service-provision',query:{orderId:String(snapshot.id)}})
  return
 }
 if(type==='confirm'&&!['DRAFT','PENDING_CONFIRM'].includes(snapshot.status))return
 if(type==='cancel'&&['PROVISIONED','COMPLETED','CANCELLED'].includes(snapshot.status))return
 const labels={confirm:'确认订单',cancel:'取消订单'}
 working.value=true
 try{
  const {value}=await ElMessageBox.prompt(`订单：${snapshot.number}。请输入业务原因`,labels[type],{
   inputValidator:v=>!!String(v||'').trim()||'原因不能为空'
  })
  if(!sameOrder(snapshot,permission)){
   ElMessage.warning('确认期间订单、状态或权限已变化；没有提交订单操作，请重新核对')
   return
  }
  if(type==='confirm')await orderServiceApi.confirmOrder(snapshot.id,String(value).trim())
  else await orderServiceApi.cancelOrder(snapshot.id,String(value).trim())
  if(sameOrder(snapshot,permission)){
   ElMessage.success('订单状态变更请求已返回，请核查付款和后续交付')
   await load()
  }else ElMessage.warning('原订单状态变更请求已返回；当前页面已切换，请回原订单回查')
 }catch(e:any){
  if(e!=='cancel'&&e!=='close')ElMessage.error('订单变更未确认成功，请查询原订单审计和状态，避免重复提交')
 }finally{working.value=false}
}
async function paymentConfirm(){
 const snapshot=actionSnapshot('order:payment:verify')
 if(!snapshot||snapshot.status!=='PENDING_PROVISION'||snapshot.payment==='PAID_CONFIRMED')return
 working.value=true
 try{
  const proof=await ElMessageBox.prompt(`订单：${snapshot.number}。填写已核实到账的外部付款流水号或凭证编号`,'核实客户付款',{
   inputValidator:v=>!!String(v||'').trim()||'凭证号不能为空'
  })
  if(!sameOrder(snapshot,'order:payment:verify')){
   ElMessage.warning('核款期间订单或付款状态已变化，未提交付款确认')
   return
  }
  const reason=await ElMessageBox.prompt(`订单：${snapshot.number}。填写实际查验渠道及凭证；该操作不会自动收款`,'核实收款',{
   inputValidator:v=>!!String(v||'').trim()||'核实依据不能为空'
  })
  if(!sameOrder(snapshot,'order:payment:verify')){
   ElMessage.warning('核款期间订单或付款状态已变化，未提交付款确认')
   return
  }
  await orderServiceApi.confirmPayment(snapshot.id,String(proof.value).trim(),String(reason.value).trim())
  if(sameOrder(snapshot,'order:payment:verify')){
   ElMessage.success('付款凭证核实请求已返回；供应商尚未自动采购，服务未自动开通')
   await load()
  }else ElMessage.warning('原订单付款核实请求已返回，当前页面已切换；请回原订单查看审计凭证')
 }catch(e:any){
  if(e!=='cancel'&&e!=='close')ElMessage.error('付款核实未确认成功，请核对原始流水与审计，不要重复提交')
 }finally{working.value=false}
}
function goRenew(){
 if(!orderReady.value||working.value||!canRenew.value||!isPaid.value||
    row.value.order_type!=='RENEW'||row.value.status!=='PENDING_PROVISION')return
 const target=Number(row.value.renewal_service_id)
 if(!target)return ElMessage.warning('续费订单缺少合法的原服务引用')
 void router.push({path:'/service-changes',query:{serviceId:String(target),renewalOrderId:String(id)}})
}
watch(()=>route.params.id,()=>{id=Number(route.params.id);tab.value='items';load()})
onMounted(load)
</script>
<template>
<div class="page">
 <div class="head">
  <div><el-button link @click="router.push('/orders')">← 返回订单管理</el-button>
   <h2>{{row.order_no||'订单详情'}}</h2>
   <p>客户购买意图 → 确认 → 收款核实 → 实际开通或续费 → 实例回查。</p>
  </div>
  <div class="actions">
   <el-button :disabled="working" @click="load">刷新</el-button>
   <el-button v-if="!detailError&&canChange&&['DRAFT','PENDING_CONFIRM'].includes(row.status)" :loading="working" :disabled="!orderReady||working" @click="act('confirm')">确认订单</el-button>
   <el-button v-if="!detailError&&canPay&&!isPaid&&row.status==='PENDING_PROVISION'" :loading="working" :disabled="!orderReady||working" type="primary" @click="paymentConfirm">核实客户付款</el-button>
   <el-button v-if="!detailError&&canProvision&&row.order_type==='NEW'&&row.status==='PENDING_PROVISION'"
     :disabled="!orderReady||working||!isPaid" type="success" @click="act('provision')">进入服务开通向导</el-button>
   <el-button v-if="!detailError&&canRenew&&row.order_type==='RENEW'&&row.status==='PENDING_PROVISION'"
     :disabled="!orderReady||working||!isPaid||!row.renewal_service_id" type="success" @click="goRenew">对原服务执行续费</el-button>
   <el-button v-if="!detailError&&canChange&&row.status&&!['PROVISIONED','COMPLETED','CANCELLED'].includes(row.status)"
     type="danger" plain :loading="working" :disabled="!orderReady||working" @click="act('cancel')">取消订单</el-button>
  </div>
 </div>
 <el-alert v-if="detailError" type="error" :closable="false" title="订单详情读取失败，无法核实付款、订单项和实际服务。"/>
 <el-button v-if="detailError" @click="load">重新加载订单</el-button>
 <el-alert v-if="!detailError" type="warning" :closable="false" show-icon title="订单确认不等于已到账；付款核实也不等于已向供应商采购、已延长服务或已真实交付。"/>
 <el-card v-if="!detailError" v-loading="loading" shadow="never">
  <template #header><b>订单主记录</b></template>
  <el-descriptions :column="3" border>
   <el-descriptions-item label="订单编号">{{fmt(row.order_no)}}</el-descriptions-item>
   <el-descriptions-item label="订单类型">{{fmt(row.order_type)}}</el-descriptions-item>
   <el-descriptions-item label="订单状态">{{fmt(row.status)}}</el-descriptions-item>
   <el-descriptions-item label="所属客户">
    <el-button v-if="auth.hasPermission('customer:read')" link type="primary"
      @click="router.push('/customers/'+row.customer_id)">{{row.customer_name}}</el-button>
    <span v-else>{{fmt(row.customer_name)}}</span>
   </el-descriptions-item>
   <el-descriptions-item label="订单金额">{{fmt(row.total_amount)}} {{fmt(row.currency)}}</el-descriptions-item>
   <el-descriptions-item label="付款核实">
    <el-tag :type="isPaid?'success':'warning'">{{isPaid?'已核实':'未核实'}}</el-tag>
   </el-descriptions-item>
   <el-descriptions-item label="付款核实时间">{{fmt(row.payment_verified_at)}}</el-descriptions-item>
   <el-descriptions-item label="预计生效">{{fmt(row.expected_effective_at)}}</el-descriptions-item>
   <el-descriptions-item label="预计到期">{{fmt(row.expected_expire_at)}}</el-descriptions-item>
   <el-descriptions-item v-if="row.order_type==='RENEW'" label="续费原服务">
    <el-button v-if="row.renewal_service_id&&auth.hasPermission('service:read')" link type="primary"
     @click="router.push('/services/'+row.renewal_service_id)">{{renewalTarget?.service_no||'查看原服务'}}</el-button>
    <span v-else>{{row.renewal_service_id?'服务权限不足':'未绑定原服务'}}</span>
   </el-descriptions-item>
   <el-descriptions-item label="备注" :span="3">{{fmt(row.remark)}}</el-descriptions-item>
  </el-descriptions>
 </el-card>
 <el-card v-if="!detailError" shadow="never">
  <el-tabs v-model="tab">
   <el-tab-pane name="items" label="订单项明细">
    <el-table :data="row.items||[]" stripe border>
     <el-table-column prop="line_no" label="行号" width="70"/>
     <el-table-column prop="product_name" label="产品" min-width="140"/>
     <el-table-column prop="product_type" label="类型" min-width="110"/>
     <el-table-column prop="package_name" label="套餐" min-width="140"/>
     <el-table-column prop="quantity" label="数量" width="85"/>
     <el-table-column prop="service_count" label="服务实例数" width="115"/>
     <el-table-column label="金额" min-width="125"><template #default="s">{{s.row.line_amount}} {{fmt(row.currency)}}</template></el-table-column>
    </el-table>
   </el-tab-pane>
   <el-tab-pane v-if="auth.hasPermission('service:read')" name="services"
      :label="'实际关联服务 ('+relatedServices.length+')'">
    <el-alert v-if="relatedError" type="warning" :closable="false" title="服务关系查询失败，不能据此认定没有实例或续费服务。"/>
    <el-alert v-if="truncated" type="info" :closable="false" title="这里仅显示最近200条关联服务。"/>
    <el-alert type="info" :closable="false" title="新增订单显示实际创建的服务实例；续费订单显示原服务作为续费目标，不会虚构一条新服务。"/>
    <el-table :data="relatedServices" stripe border style="margin-top:12px">
     <el-table-column prop="service_no" label="服务号" min-width="180"/>
     <el-table-column label="关联关系" min-width="140"><template #default="s">{{s.row.relation_type==='RENEWAL_TARGET'?'本单续费目标':'本单生成实例'}}</template></el-table-column>
     <el-table-column prop="product_name" label="产品" min-width="140"/>
     <el-table-column prop="status" label="业务状态" width="125"/>
     <el-table-column prop="delivery_stage" label="真实交付阶段" min-width="145"/>
     <el-table-column prop="expire_at" label="服务到期" min-width="175"/>
     <el-table-column label="操作" width="200"><template #default="s">
      <el-button link type="primary" @click="router.push('/services/'+s.row.id)">服务详情</el-button>
      <el-button v-if="canRenew&&row.order_type==='RENEW'&&isPaid"
       link type="success" @click="goRenew">办理本单续费</el-button>
     </template></el-table-column>
    </el-table>
   </el-tab-pane>
  </el-tabs>
 </el-card>
</div>
</template>
<style scoped>
.page{display:grid;gap:16px}.head,.actions{display:flex;align-items:center;justify-content:space-between;gap:10px;flex-wrap:wrap}.head h2{margin:6px 0}.head p{margin:0;color:#7b8494}
</style>