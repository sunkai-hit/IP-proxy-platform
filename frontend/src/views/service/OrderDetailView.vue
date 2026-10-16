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
 if(type==='provision'){router.push({path:'/service-provision',query:{orderId:String(id)}});return}
 const labels={confirm:'确认订单',cancel:'取消订单'}
 const {value}=await ElMessageBox.prompt('请输入业务原因',labels[type],
 {inputValidator:v=>!!String(v||'').trim()||'原因不能为空'})
 working.value=true
 try{
  if(type==='confirm')await orderServiceApi.confirmOrder(id,String(value).trim())
  else await orderServiceApi.cancelOrder(id,String(value).trim())
  ElMessage.success('订单状态已更新，请核查付款和后续交付')
  await load()
 }finally{working.value=false}
}
async function paymentConfirm(){
 const proof=await ElMessageBox.prompt('填写已核实到账的外部付款流水号或凭证编号','核实客户付款',{
  inputValidator:v=>!!String(v||'').trim()||'凭证号不能为空'})
 const reason=await ElMessageBox.prompt('填写实际查验渠道及凭证；该操作不会自动收款','核实收款',{
  inputValidator:v=>!!String(v||'').trim()||'核实依据不能为空'})
 working.value=true
 try{
  await orderServiceApi.confirmPayment(id,String(proof.value).trim(),String(reason.value).trim())
  ElMessage.success('已核实并记录收款凭证；供应商尚未自动采购，服务未自动开通')
  await load()
 }finally{working.value=false}
}
function goRenew(){
 const target=Number(row.value.renewal_service_id)
 if(!target)return ElMessage.warning('续费订单缺少合法的原服务引用')
 router.push({path:'/service-changes',query:{serviceId:String(target),renewalOrderId:String(id)}})
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
   <el-button @click="load">刷新</el-button>
   <el-button v-if="!detailError&&canChange&&['DRAFT','PENDING_CONFIRM'].includes(row.status)" :loading="working" @click="act('confirm')">确认订单</el-button>
   <el-button v-if="!detailError&&canPay&&!isPaid&&row.status==='PENDING_PROVISION'" :loading="working" type="primary" @click="paymentConfirm">核实客户付款</el-button>
   <el-button v-if="!detailError&&canProvision&&row.order_type==='NEW'&&row.status==='PENDING_PROVISION'"
     :disabled="!isPaid" type="success" @click="act('provision')">进入服务开通向导</el-button>
   <el-button v-if="!detailError&&canRenew&&row.order_type==='RENEW'&&row.status==='PENDING_PROVISION'"
     :disabled="!isPaid||!row.renewal_service_id" type="success" @click="goRenew">对原服务执行续费</el-button>
   <el-button v-if="!detailError&&canChange&&row.status&&!['PROVISIONED','COMPLETED','CANCELLED'].includes(row.status)"
     type="danger" plain :loading="working" @click="act('cancel')">取消订单</el-button>
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