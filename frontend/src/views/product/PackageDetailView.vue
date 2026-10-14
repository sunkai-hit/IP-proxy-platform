<script setup lang="ts">
import {onMounted,ref} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {ElMessage} from 'element-plus'
import {productApi} from '@/api/product'
import {businessRelationsApi} from '@/api/businessRelations'
import {useAuthStore} from '@/stores/auth'
const route=useRoute(),router=useRouter(),auth=useAuthStore(),id=Number(route.params.id)
const row=ref<any>({}),services=ref<any[]>([]),loading=ref(false)
const serviceError=ref(false),truncated=ref(false)
const data=(r:any)=>r.data.data
const fmt=(x:any)=>x==null||x===''?'—':String(x)
const fixed=()=>['LONG_IP','EXCLUSIVE_IP'].includes(row.value.product_type)
async function load(){
 loading.value=true
 try{
  row.value=data(await productApi.pkg(id))||{}
  services.value=[];serviceError.value=false;truncated.value=false
  if(auth.hasPermission('service:read')){
   try{
    const d=data(await businessRelationsApi.packageServices(id))
    services.value=d.items||[];truncated.value=!!d.truncated
   }catch{serviceError.value=true}
  }
 }catch{ElMessage.error('套餐详情加载失败，请检查权限与记录状态')}
 finally{loading.value=false}
}
onMounted(load)
</script>
<template>
<div class="page">
 <div class="head">
  <div>
   <el-button link @click="router.push('/packages')">← 返回套餐管理</el-button>
   <h2>{{row.package_name||'套餐详情'}}</h2>
   <p>{{row.package_code||'—'}} · {{row.product_name||''}} · {{row.status||''}}</p>
  </div>
  <div class="actions">
   <el-button v-if="row.product_id&&auth.hasPermission('product:read')"
    @click="router.push('/products/'+row.product_id)">所属产品</el-button>
   <el-button v-if="auth.hasPermission('product:package:write')"
    type="primary" @click="router.push({path:'/packages',query:{productId:String(row.product_id),editId:String(id)}})">在套餐管理中编辑</el-button>
   <el-button @click="load">刷新</el-button>
  </div>
 </div>
 <el-alert type="info" :closable="false" show-icon
  title="套餐定义未来销售条件，不是已经开通服务的实时合同。老服务期限、宽限和额度按下单/开通时的快照继续执行，修改套餐不回写存量。"/>
 <el-card v-loading="loading" shadow="never">
  <template #header><b>销售套餐主记录</b></template>
  <el-descriptions :column="3" border>
   <el-descriptions-item label="套餐编号">{{fmt(row.package_code)}}</el-descriptions-item>
   <el-descriptions-item label="套餐名称">{{fmt(row.package_name)}}</el-descriptions-item>
   <el-descriptions-item label="所属产品">
    <el-button v-if="row.product_id&&auth.hasPermission('product:read')" link type="primary"
     @click="router.push('/products/'+row.product_id)">{{fmt(row.product_name)}}</el-button>
    <span v-else>{{fmt(row.product_name)}}</span>
   </el-descriptions-item>
   <el-descriptions-item label="产品类型">{{fmt(row.product_type)}}</el-descriptions-item>
   <el-descriptions-item label="销售状态"><el-tag :type="row.status==='ACTIVE'?'success':'warning'">{{fmt(row.status)}}</el-tag></el-descriptions-item>
   <el-descriptions-item label="套餐周期(天)">{{fmt(row.service_days)}}</el-descriptions-item>
   <el-descriptions-item label="续费宽限(天)">
    <el-tag v-if="fixed()" :type="row.grace_days==null?'danger':row.grace_days===0?'warning':'info'">
     {{row.grace_days==null?'未配置，禁止猜默认':row.grace_days===0?'0（无宽限）':row.grace_days}}
    </el-tag><span v-else>不适用</span>
   </el-descriptions-item>
   <el-descriptions-item label="IP数量">{{fmt(row.ip_count)}}</el-descriptions-item>
   <el-descriptions-item label="提取配额">{{fmt(row.extract_quota)}}</el-descriptions-item>
   <el-descriptions-item label="流量(Byte)">{{fmt(row.traffic_quota_bytes)}}</el-descriptions-item>
   <el-descriptions-item label="并发上限">{{fmt(row.concurrency_limit)}}</el-descriptions-item>
   <el-descriptions-item label="设备数上限">{{fmt(row.device_limit)}}</el-descriptions-item>
   <el-descriptions-item label="白名单上限">{{fmt(row.whitelist_limit)}}</el-descriptions-item>
   <el-descriptions-item label="换IP上限">{{fmt(row.change_ip_limit)}}</el-descriptions-item>
   <el-descriptions-item label="价格">{{fmt(row.price)}} {{fmt(row.currency)}}</el-descriptions-item>
   <el-descriptions-item label="最近变更">{{fmt(row.updated_at)}}</el-descriptions-item>
   <el-descriptions-item label="记录版本">{{fmt(row.version)}}</el-descriptions-item>
  </el-descriptions>
 </el-card>
 <el-card shadow="never">
  <template #header><b>历史与当前服务实例（显示 {{services.length}} 条）</b></template>
  <el-alert type="warning" :closable="false"
   title="关联服务的grace_days_snapshot才是对应客户当次约定，不使用当前套餐grace_days反推老合同；0天应视为到期立即停服并受控回收。"/>
  <el-alert v-if="serviceError" type="warning" :closable="false"
   title="关联服务查询失败，不能据此认为尚未销售。"/>
  <el-alert v-if="truncated" type="info" :closable="false" title="最多展示最近200条，后续需按服务列表筛选。"/>
  <el-table v-if="auth.hasPermission('service:read')" :data="services" border stripe style="margin-top:12px"
    empty-text="未查到已经与该套餐关联的服务实例">
   <el-table-column prop="service_no" label="服务实例" min-width="175"/>
   <el-table-column prop="customer_name" label="客户" min-width="130"/>
   <el-table-column prop="status" label="业务状态" width="115"/>
   <el-table-column prop="delivery_stage" label="真实交付阶段" min-width="135"/>
   <el-table-column prop="expire_at" label="服务到期时刻" min-width="175"/>
   <el-table-column label="原合同宽限" width="120"><template #default="s">
    {{s.row.grace_days_snapshot==null?'待核对':s.row.grace_days_snapshot+'天'}}
   </template></el-table-column>
   <el-table-column prop="grace_until_at" label="出口保留截止" min-width="175"/>
   <el-table-column prop="expiry_phase" label="到期阶段" min-width="120"/>
   <el-table-column label="操作" width="110"><template #default="s">
    <el-button link type="primary" @click="router.push('/services/'+s.row.id)">服务详情</el-button>
   </template></el-table-column>
  </el-table>
  <el-alert v-else type="info" :closable="false" title="当前账号没有查看客户服务的权限。"/>
 </el-card>
</div>
</template>
<style scoped>
.page{display:grid;gap:16px}.head,.actions{display:flex;justify-content:space-between;align-items:center;gap:12px;flex-wrap:wrap}
.head h2{margin:5px 0}.head p{margin:0;color:#7b8494}
</style>