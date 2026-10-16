<script setup lang="ts">
import {onMounted,ref} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {ElMessage} from 'element-plus'
import {externalDeliveryApi} from '@/api/externalDelivery'
import {businessRelationsApi} from '@/api/businessRelations'
import {useAuthStore} from '@/stores/auth'

const route=useRoute(),router=useRouter(),auth=useAuthStore()
const id=Number(route.params.id),row=ref<any>({}),loading=ref(false),tab=ref('proxies')
const proxies=ref<any[]>([]),services=ref<any[]>([])
const proxyWarning=ref(false),serviceWarning=ref(false),proxyTruncated=ref(false),serviceTruncated=ref(false)
const data=(r:any)=>r.data.data
const fmt=(v:any)=>v==null||v===''?'—':String(v)
const mask=(v:any)=>v?('••••'+String(v).slice(-4)):'—'
async function load(){
 loading.value=true
 try{
  row.value=data(await externalDeliveryApi.purchase(id))||{}
  const [p,s]=await Promise.allSettled([
   auth.hasPermission('resource:reverse-proxy:read')?businessRelationsApi.purchaseProxies(id):Promise.resolve(null),
   auth.hasPermission('service:read')?businessRelationsApi.purchaseServices(id):Promise.resolve(null)
  ])
  proxyWarning.value=p.status==='rejected';serviceWarning.value=s.status==='rejected'
  proxies.value=p.status==='fulfilled'?(p.value?.data?.data?.items||[]):[]
  services.value=s.status==='fulfilled'?(s.value?.data?.data?.items||[]):[]
  proxyTruncated.value=p.status==='fulfilled'?!!p.value?.data?.data?.truncated:false
  serviceTruncated.value=s.status==='fulfilled'?!!s.value?.data?.data?.truncated:false
 }catch{ElMessage.error('采购详情读取失败，暂不能推断该采购无下游服务')}
 finally{loading.value=false}
}
onMounted(load)
</script>
<template>
<div class="page">
 <div class="head">
  <div><el-button link @click="router.push('/supplier-purchases')">← 返回采购列表</el-button>
   <h2>{{row.purchase_no||'外部采购详情'}}</h2>
   <p>供应商采购 → 平台反向代理 → 客户服务。上游资源是否真实可用，以供应商和网关验证为准。</p></div>
  <div class="actions"><el-button @click="load">刷新</el-button>
 <el-button v-if="row.customer_id&&row.source_customer_order_id&&auth.hasPermission('resource:reverse-proxy:write')"
 type="primary" @click="router.push({path:'/reverse-proxies',query:{newProxy:'1',purchaseId:String(id)}})">基于此采购登记代理</el-button>
 <el-button v-if="auth.hasPermission('resource:reverse-proxy:read')" @click="router.push('/reverse-proxies')">代理列表</el-button></div>
 </div>
 <el-alert type="warning" :closable="false" title="采购登记与平台代理ACTIVE只是平台记录；没有供应商真实有效期和代理通断回执时，不能据此认定客户服务已交付。" show-icon/>
 <el-card shadow="never" v-loading="loading">
  <template #header><b>采购主记录</b></template>
  <el-descriptions :column="3" border>
   <el-descriptions-item label="采购编号">{{fmt(row.purchase_no)}}</el-descriptions-item>
   <el-descriptions-item label="供应商">{{fmt(row.supplier_name)}} ({{fmt(row.supplier_code)}})</el-descriptions-item>
   <el-descriptions-item label="供应商套餐">{{fmt(row.supplier_package_name)}}</el-descriptions-item>
   <el-descriptions-item label="套餐编号">{{fmt(row.supplier_package_no)}}</el-descriptions-item>
   <el-descriptions-item label="授权客户">{{fmt(row.customer_name||'未指定客户的库存')}}</el-descriptions-item>
   <el-descriptions-item label="登记状态"><el-tag :type="row.status==='ACTIVE'?'success':'warning'">{{fmt(row.status)}}</el-tag></el-descriptions-item>
   <el-descriptions-item label="供应商生效">{{fmt(row.effective_at)}}</el-descriptions-item>
   <el-descriptions-item label="供应商到期">{{fmt(row.expire_at)}}</el-descriptions-item>
   <el-descriptions-item label="采购账号（掩码）">{{mask(row.purchase_account)}}</el-descriptions-item>
   <el-descriptions-item label="客户收款关联">
    <el-button v-if="row.source_customer_order_id&&auth.hasPermission('order:read')" link type="primary" @click="router.push('/orders/'+row.source_customer_order_id)">{{row.source_customer_order_no||'查看付款来源订单'}}</el-button>
    <span v-else>{{row.source_customer_order_id?'需订单权限查看':'未关联（库存/存量）'}}</span>
   </el-descriptions-item>
   <el-descriptions-item label="付款核实记录">{{row.source_customer_payment_status||'无关联来源'}}</el-descriptions-item>
   <el-descriptions-item label="供应商认证">{{row.secretConfigured?'已配置（不可回显）':'未配置'}}</el-descriptions-item>
   <el-descriptions-item label="登记说明" :span="2">{{fmt(row.remark)}}</el-descriptions-item>
  </el-descriptions>
  <el-alert style="margin-top:14px" type="info" :closable="false" title="成本、套餐额度和剩余额度均是选填登记信息，既不触发扣费也不自动扣减额度。"/>
  <el-descriptions :column="3" border style="margin-top:12px">
   <el-descriptions-item label="成本（可选）">{{fmt(row.purchase_cost)}} {{row.purchase_cost==null?'':fmt(row.currency)}}</el-descriptions-item>
   <el-descriptions-item label="套餐额度（可选）">{{fmt(row.package_quota)}}</el-descriptions-item>
   <el-descriptions-item label="剩余额度（可选）">{{fmt(row.remaining_quota)}}</el-descriptions-item>
  </el-descriptions>
 </el-card>
 <el-card shadow="never">
  <el-tabs v-model="tab">
   <el-tab-pane v-if="auth.hasPermission('resource:reverse-proxy:read')" name="proxies" :label="'反向代理明细 ('+proxies.length+')'">
    <el-alert v-if="proxyWarning" type="warning" :closable="false" title="代理关联查询失败，不能据此认定采购尚未被使用。"/>
    <el-alert v-if="proxyTruncated" type="info" :closable="false" title="只列出最近200条代理，完整记录请到反向代理管理查询。"/>
    <el-table :data="proxies" border stripe empty-text="当前未登记反向代理">
     <el-table-column prop="proxy_no" label="代理编号" min-width="175"/>
     <el-table-column prop="customer_name" label="客户" min-width="125"/>
     <el-table-column prop="service_no" label="关联服务" min-width="155"/>
     <el-table-column prop="protocol_code" label="协议" width="95"/>
     <el-table-column label="平台入口" min-width="165"><template #default="s">{{s.row.platform_host}}:{{s.row.platform_port}}</template></el-table-column>
     <el-table-column prop="status" label="配置状态" width="110"/>
     <el-table-column prop="last_checked_at" label="最后检查" min-width="160"/>
     <el-table-column label="操作" width="100"><template #default="s"><el-button link type="primary" @click="router.push('/reverse-proxies/'+s.row.id)">代理详情</el-button></template></el-table-column>
    </el-table>
   </el-tab-pane>
   <el-tab-pane v-if="auth.hasPermission('service:read')" name="services" :label="'使用中及历史服务 ('+services.length+')'">
    <el-alert v-if="serviceWarning" type="warning" :closable="false" title="服务关系查询未完成，不能当成零使用量或无客户权益。"/>
    <el-alert type="info" :closable="false" title="来自服务对采购或反向代理的明确引用；关联存在不等于平台真正完成网络交付。" style="margin-top:10px"/>
    <el-table :data="services" border stripe empty-text="当前没有登记此采购引用的服务">
     <el-table-column prop="service_no" label="服务号" min-width="160"/>
     <el-table-column prop="customer_name" label="客户" min-width="130"/>
     <el-table-column prop="product_name" label="产品" min-width="135"/>
     <el-table-column prop="product_type" label="产品类型" min-width="110"/>
     <el-table-column prop="status" label="业务状态" width="110"/>
     <el-table-column prop="delivery_stage" label="交付验证" min-width="120"/>
     <el-table-column prop="expire_at" label="客户到期" min-width="165"/>
     <el-table-column label="操作" width="100"><template #default="s"><el-button link type="primary" @click="router.push('/services/'+s.row.id)">服务详情</el-button></template></el-table-column>
    </el-table>
    <el-alert v-if="serviceTruncated" type="info" :closable="false" title="仅显示最近200条服务。"/>
   </el-tab-pane>
  </el-tabs>
 </el-card>
</div>
</template>
<style scoped>
.page{display:grid;gap:16px}.head,.actions{display:flex;justify-content:space-between;align-items:center;gap:12px}.head h2{margin:6px 0}.head p{margin:0;color:#7b8494}
</style>