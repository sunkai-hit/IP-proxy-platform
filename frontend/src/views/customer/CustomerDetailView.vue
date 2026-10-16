<script setup lang="ts">
import {onMounted,ref,watch} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {customerApi} from '@/api/customer'
import {businessRelationsApi} from '@/api/businessRelations'
import {useAuthStore} from '@/stores/auth'
const route=useRoute(),router=useRouter(),auth=useAuthStore();let id=Number(route.params.id)
const detail=ref<any>(null),credentials=ref<any[]>([]),orders=ref<any[]>([])
const ordersTruncated=ref(false),loading=ref(false),ordersError=ref(false),credentialsError=ref(false),detailError=ref(false),requestVersion=ref(0)
const fmt=(v:any)=>v===null||v===undefined||v===''?'—':String(v)
async function load(){
 const version=++requestVersion.value,customerId=id
 loading.value=true;detailError.value=false;ordersError.value=false;credentialsError.value=false
 detail.value=null;orders.value=[];credentials.value=[];ordersTruncated.value=false
 try{
  const response=await customerApi.detail(customerId)
  if(version!==requestVersion.value)return
  const record=response.data.data
  if(!record?.customer?.customer_code){detailError.value=true;return}
  detail.value=record
  const extra=await Promise.allSettled([
   auth.hasPermission('customer:credential:read')?customerApi.credentials(customerId):Promise.resolve(null),
   auth.hasPermission('order:read')?businessRelationsApi.customerOrders(customerId):Promise.resolve(null)
  ])
  if(version!==requestVersion.value)return
  if(extra[0].status==='fulfilled')credentials.value=extra[0].value?.data?.data||[]
  else credentialsError.value=true
  if(extra[1].status==='fulfilled'){
   orders.value=extra[1].value?.data?.data?.items||[]
   ordersTruncated.value=!!extra[1].value?.data?.data?.truncated
  }else ordersError.value=true
 }catch{if(version===requestVersion.value){detailError.value=true;detail.value=null}}
 finally{if(version===requestVersion.value)loading.value=false}
}
watch(()=>route.params.id,()=>{id=Number(route.params.id);load()})
function openOrder(newOrder=false){router.push({path:'/orders',query:{customerId:String(id),...(newOrder?{newOrder:'1'}:{})}})}
function openService(serviceId:number){router.push('/services/'+serviceId)}
function metric(v:any){return v==null?'—':v}
onMounted(load)
</script>
<template>
<div class="page" v-loading="loading">
 <el-alert v-if="detailError" type="error" :closable="false" title="客户详情读取失败，无法确认客户身份、订单权益或服务凭证。"/>
 <el-button v-if="detailError" @click="load">重新加载客户详情</el-button>
 <template v-if="detail">
 <div class="head">
  <div><el-button link @click="router.push('/customers')">← 返回客户列表</el-button>
   <h2>{{detail.customer.customer_name}}</h2><p>{{detail.customer.customer_code}}</p></div>
  <div class="head-actions">
   <el-tag>{{detail.customer.customer_type_code}}</el-tag>
   <el-tag type="success">{{detail.customer.status}}</el-tag>
   <el-button v-if="auth.hasPermission('order:read')&&auth.hasPermission('order:write')" type="primary" @click="openOrder(true)">为该客户创建订单</el-button>
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
    <el-table :data="orders" stripe border>
     <el-table-column prop="order_no" label="订单号" min-width="165"/>
     <el-table-column prop="order_type" label="业务类型" width="105"/>
     <el-table-column prop="status" label="订单状态" width="125"/>
     <el-table-column prop="payment_status" label="付款核实" min-width="120"/>
     <el-table-column prop="order_item_count" label="订单项" width="80"/>
     <el-table-column prop="service_count" label="已关联服务" width="95"/>
     <el-table-column label="金额" min-width="135"><template #default="s">{{s.row.total_amount}} {{s.row.currency}}</template></el-table-column>
     <el-table-column prop="created_at" label="创建时间" min-width="165"/>
     <el-table-column label="操作" width="105"><template #default="s"><el-button type="primary" link @click="router.push('/orders/'+s.row.id)">订单详情</el-button></template></el-table-column>
    </el-table>
   </el-tab-pane>
   <el-tab-pane v-if="auth.hasPermission('service:read')" label="服务实例">
    <el-alert type="info" :closable="false" title="服务列表只显示管理面状态；具体公网IPv4授权、真实交付和到期任务请进入服务详情核对。" style="margin-bottom:12px"/>
    <el-table :data="detail.services||[]" stripe border>
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
    <el-table :data="credentials" stripe>
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