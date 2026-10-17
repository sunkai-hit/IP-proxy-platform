<script setup lang="ts">
import {onMounted,ref,watch} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {ElMessage} from 'element-plus'
import {customerApi} from '@/api/customer'
import {businessRelationsApi} from '@/api/businessRelations'
import {useAuthStore} from '@/stores/auth'
const route=useRoute(),router=useRouter(),auth=useAuthStore();let id=Number(route.params.id)
const detail=ref<any>(null),credentials=ref<any[]>([]),orders=ref<any[]>([])
const ordersTruncated=ref(false),loading=ref(false),ordersError=ref(false),serviceError=ref(false),credentialError=ref(false),detailError=ref(false),requestVersion=ref(0)
const fmt=(v:any)=>v===null||v===undefined||v===''?'—':String(v)
const validId=(v:any)=>Number.isSafeInteger(Number(v))&&Number(v)>0
function ownsRows(rows:any,customerId:number,numberField:string):boolean{
 if(!Array.isArray(rows))return false
 const ids=new Set<number>()
 for(const item of rows){
  const childId=Number(item?.id)
  if(!validId(childId)||ids.has(childId)||Number(item?.customer_id)!==customerId||
     !String(item?.[numberField]||'').trim())return false
  ids.add(childId)
 }
 return true
}
async function load(){
 const version=++requestVersion.value,customerId=id
 loading.value=true;detailError.value=false;ordersError.value=false;serviceError.value=false;credentialError.value=false
 detail.value=null;credentials.value=[];orders.value=[];ordersTruncated.value=false
 try{
  const result=(await customerApi.detail(customerId)).data.data
  if(version!==requestVersion.value)return
  if(!validId(customerId)||Number(result?.customer?.id)!==customerId||
     !String(result.customer.customer_code||'').trim())throw new Error('CUSTOMER_PARENT_IDENTITY_INVALID')
  if(auth.hasPermission('service:read')&&!ownsRows(result.services,customerId,'service_no')){
    serviceError.value=true
    detail.value={...result,services:[]}
  }else detail.value=result
  const [cred,ord]=await Promise.allSettled([
   auth.hasPermission('customer:credential:read')?customerApi.credentials(customerId):Promise.resolve(null),
   auth.hasPermission('order:read')?businessRelationsApi.customerOrders(customerId):Promise.resolve(null)
  ])
  if(version!==requestVersion.value)return
  if(cred.status==='fulfilled')credentials.value=cred.value?.data?.data||[]
  else credentialError.value=true
  if(auth.hasPermission('order:read')){
   const items=ord.status==='fulfilled'?ord.value?.data?.data?.items:null
   if(!ownsRows(items,customerId,'order_no')){
    ordersError.value=true;orders.value=[]
   }else{
    orders.value=items;ordersTruncated.value=!!ord.value?.data?.data?.truncated
   }
  }
 }catch{
  if(version===requestVersion.value){
   detailError.value=true;detail.value=null;credentials.value=[];orders.value=[];serviceError.value=false
  }
 }finally{if(version===requestVersion.value)loading.value=false}
}
function openOrder(newOrder=false){
 if(!loading.value&&!detailError.value&&Number(detail.value?.customer?.id)===id&&
    auth.hasPermission('order:read')&&(!newOrder||auth.hasPermission('order:write')))
  void router.push({path:'/orders',query:{customerId:String(id),...(newOrder?{newOrder:'1'}:{})}})
}
function openService(serviceId:number){
 if(!loading.value&&!serviceError.value&&auth.hasPermission('service:read')&&validId(serviceId)&&
   (detail.value?.services||[]).some((s:any)=>Number(s.id)===Number(serviceId)&&Number(s.customer_id)===id))
  void router.push('/services/'+serviceId)
}
function metric(v:any){return v==null?'—':v}
watch(()=>route.params.id,()=>{id=Number(route.params.id);load()})
onMounted(load)
</script>
<template>
<div class="page" v-loading="loading"><el-alert v-if="detailError" type="error" :closable="false" title="客户详情读取失败，无法确认客户归属、合同、服务或认证状态。"/><el-button v-if="detailError" @click="load">重新加载客户详情</el-button><template v-if="detail?.customer">
 <div class="head">
  <div><el-button link @click="router.push('/customers')">← 返回客户列表</el-button>
   <h2>{{detail.customer.customer_name}}</h2><p>{{detail.customer.customer_code}}</p></div>
  <div class="head-actions">
   <el-tag>{{detail.customer.customer_type_code}}</el-tag>
   <el-tag type="success">{{detail.customer.status}}</el-tag>
   <el-button v-if="auth.hasPermission('order:read')&&auth.hasPermission('order:write')" :disabled="loading||detailError" type="primary" @click="openOrder(true)">为该客户创建订单</el-button>
   <el-button @click="load">刷新</el-button>
  </div>
 </div>
 <el-card shadow="never">
  <el-descriptions :column="3" border>
   <el-descriptions-item label="客户编号">{{fmt(detail.customer.customer_code)}}</el-descriptions-item>
   <el-descriptions-item label="客户类型">{{fmt(detail.customer.customer_type_code)}}</el-descriptions-item>
   <el-descriptions-item label="认证状态">{{fmt(detail.customer.auth_status)}}</el-descriptions-item>
   <el-descriptions-item label="联系人">{{fmt(detail.customer.contact_name)}}</el-descriptions-item>
   <el-descriptions-item label="联系电话">{{fmt(detail.customer.contact_phone)}}</el-descriptions-item>
   <el-descriptions-item label="联系邮箱">{{fmt(detail.customer.contact_email)}}</el-descriptions-item>
   <el-descriptions-item label="业务负责人">{{fmt(detail.customer.owner_name)}}</el-descriptions-item>
   <el-descriptions-item label="创建时间">{{fmt(detail.customer.created_at)}}</el-descriptions-item>
   <el-descriptions-item label="备注">{{fmt(detail.customer.remark)}}</el-descriptions-item>
  </el-descriptions>
 </el-card>
 <div class="metrics">
  <el-card shadow="never"><small>服务总数</small><b>{{metric(detail.usage?.service_total)}}</b></el-card>
  <el-card shadow="never"><small>业务ACTIVE服务</small><b>{{metric(detail.usage?.active_service_count)}}</b></el-card>
  <el-card shadow="never"><small>IP提取调用</small><b>{{metric(detail.usage?.extract_call_count)}}</b></el-card>
  <el-card shadow="never"><small>实际返回IP数</small><b>{{metric(detail.usage?.extracted_ip_count)}}</b></el-card>
  <el-card shadow="never"><small>代理使用事件</small><b>{{Number(detail.usage?.usage_request_count)>0?detail.usage.usage_request_count:'未采集'}}</b></el-card>
 </div>
 <el-card shadow="never">
  <el-tabs>
   <el-tab-pane v-if="auth.hasPermission('order:read')" label="订单明细">
    <el-alert v-if="ordersError" title="订单关联明细暂不可用，不代表客户没有订单。" type="warning" :closable="false"/>
    <el-alert v-if="ordersTruncated" title="仅展示最近200条订单，请进入订单管理筛选查看其余记录。" type="info" :closable="false"/>
    <el-button style="margin-bottom:12px" @click="openOrder(false)">进入该客户订单管理</el-button>
    <el-table :data="orders" :empty-text="ordersError?'订单关系读取失败':'暂无已登记订单'" stripe border>
     <el-table-column prop="order_no" label="订单号" min-width="165"/>
     <el-table-column prop="order_type" label="业务类型" width="105"/>
     <el-table-column prop="status" label="订单状态" width="125"/>
     <el-table-column prop="payment_status" label="付款核实" min-width="120"/>
     <el-table-column prop="order_item_count" label="订单项" width="80"/>
     <el-table-column prop="service_count" label="已关联服务" width="95"/>
     <el-table-column label="金额" min-width="135"><template #default="s">{{s.row.total_amount}} {{s.row.currency}}</template></el-table-column>
     <el-table-column prop="created_at" label="创建时间" min-width="165"/>
     <el-table-column label="操作" width="105"><template #default="s"><el-button v-if="!ordersError&&validId(s.row.id)&&Number(s.row.customer_id)===id" type="primary" link @click="router.push('/orders/'+s.row.id)">订单详情</el-button></template></el-table-column>
    </el-table>
   </el-tab-pane>
   <el-tab-pane v-if="auth.hasPermission('service:read')" label="服务实例">
    <el-alert type="info" :closable="false" title="服务列表只显示管理面状态；具体公网IPv4授权、真实交付和到期任务请进入服务详情核对。" style="margin-bottom:12px"/>
    <el-alert v-if="serviceError" type="error" :closable="false" title="服务关联返回错误的客户归属、重复ID或缺少来源，不能据此认定该客户没有服务，也不能打开旧服务。"/>
    <el-table :data="detail.services||[]" :empty-text="serviceError?'服务关联归属不可核实':'暂无已登记服务'" stripe border>
     <el-table-column prop="service_no" label="服务编号" min-width="170"/>
     <el-table-column prop="product_name" label="产品" min-width="130"/>
     <el-table-column prop="package_name" label="套餐" min-width="130"/>
     <el-table-column prop="product_type" label="类型" width="110"/>
     <el-table-column prop="status" label="业务状态" width="110"/>
     <el-table-column prop="expire_at" label="到期时间" min-width="165"/>
     <el-table-column prop="credential_count" label="凭证数" width="88"/>
     <el-table-column prop="whitelist_count" label="白名单" width="88"/>
     <el-table-column label="操作" min-width="205"><template #default="s">
      <el-button link type="primary" @click="openService(s.row.id)">查看详情</el-button>
      <el-button v-if="auth.hasPermission('service:change')&&(auth.hasPermission('service:renew')||auth.hasPermission('service:status'))" link @click="router.push({path:'/service-changes',query:{serviceId:s.row.id,action:auth.hasPermission('service:renew')?'renew':'suspend'}})">续费/变更</el-button>
     </template></el-table-column>
    </el-table>
   </el-tab-pane>
   <el-tab-pane v-if="auth.hasPermission('customer:auth:read')" label="认证记录">
    <el-table :data="detail.authRecords||[]" stripe>
     <el-table-column prop="auth_no" label="认证编号"/>
     <el-table-column prop="auth_type_code" label="认证类型"/>
     <el-table-column prop="status" label="状态"/>
     <el-table-column prop="submitted_at" label="申请时间"/>
     <el-table-column prop="reviewer_name" label="审核人"/>
     <el-table-column prop="review_opinion" label="审核意见"/>
    </el-table>
   </el-tab-pane>
   <el-tab-pane v-if="auth.hasPermission('customer:account:read')" label="客户账号">
    <el-table :data="detail.accounts||[]" stripe>
     <el-table-column prop="username" label="用户名"/><el-table-column prop="account_type_code" label="账号类型"/>
     <el-table-column prop="status" label="状态"/><el-table-column prop="last_login_at" label="最后登录"/>
    </el-table>
   </el-tab-pane>
   <el-tab-pane v-if="auth.hasPermission('customer:credential:read')" label="服务凭证摘要">
    <el-alert title="凭证不返回Token、私钥或密码明文。密钥只能在实际签发后经授权一次性展示。" type="info" :closable="false"/>
    <el-alert v-if="credentialError" type="error" :closable="false" title="客户服务凭证摘要读取失败，不能据此认为该客户未配置凭证。"/>
    <el-table :data="credentials" :empty-text="credentialError?'凭证读取失败':'暂无已登记凭证摘要'" stripe>
     <el-table-column prop="service_no" label="服务编号"/><el-table-column prop="product_name" label="产品"/>
     <el-table-column prop="credential_type" label="类型"/><el-table-column prop="account_name" label="账号"/>
     <el-table-column prop="secret_mask" label="掩码"/><el-table-column prop="status" label="状态"/>
     <el-table-column label="查看服务" width="100"><template #default="s"><el-button v-if="auth.hasPermission('service:read')" link type="primary" @click="openService(s.row.service_id)">详情</el-button></template></el-table-column>
    </el-table>
   </el-tab-pane>
  </el-tabs>
 </el-card>
 </template>
</div>
</template>
<style scoped>
.page{display:grid;gap:16px}.head,.head-actions{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap}.head h2{margin:6px 0}.head p{margin:0;color:#7b8494}.metrics{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:12px}.metrics small{display:block;color:#7b8494}.metrics b{display:block;font-size:24px;margin-top:8px}@media(max-width:1100px){.metrics{grid-template-columns:repeat(2,1fr)}}
</style>