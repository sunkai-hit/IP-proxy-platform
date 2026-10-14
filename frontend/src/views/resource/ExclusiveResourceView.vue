<script setup lang="ts">
import { onMounted,reactive,ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { resourceApi } from '@/api/resource'
import { useAuthStore } from '@/stores/auth'
const router=useRouter(),auth=useAuthStore(),rows=ref<any[]>([]),total=ref(0)
const loading=ref(false),drawer=ref(false),detail=ref<any>(null)
const query=reactive({page:1,size:20,keyword:'',status:''})
const fmt=(v:any)=>v==null||v===''?'—':String(v)
async function load(){
 loading.value=true
 try{const d=(await resourceApi.exclusive(query)).data.data;rows.value=d.items||[];total.value=d.total||0}
 finally{loading.value=false}
}
async function show(r:any){
 try{detail.value=(await resourceApi.exclusiveDetail(r.id)).data.data;drawer.value=true}
 catch{ElMessage.error('无法加载该独享记录，请核查关联服务和资源版本')}
}
function servicePath(r:any){return '/services/'+r.service_id}
function changePath(r:any){return {path:'/service-changes',query:{serviceId:String(r.service_id),action:'terminate'}}}
onMounted(load)
</script>
<template>
<div class="page">
 <div class="head"><div><h2>独享公网 IPv4 占用</h2>
  <p>历史独享分配记录和V1.2出口授权是不同事实。独享同客户跨服务、跨客户均不得复用相同公网IPv4。</p>
 </div></div>
 <el-alert type="warning" :closable="false" show-icon
  title="这里禁止直接释放资源；必须先由关联服务完成真实停服、平台代理解绑、IPv4回收核验及占用审计。旧记录缺出口快照时只读核对。"/>
 <el-card shadow="never">
  <div class="filters">
   <el-input v-model="query.keyword" placeholder="分配编号/客户名称" clearable/>
   <el-select v-model="query.status" placeholder="分配状态" clearable>
    <el-option v-for="x in ['AVAILABLE','LOCKED','ALLOCATED','PENDING_RECYCLE','DISABLED']" :key="x" :label="x" :value="x"/>
   </el-select>
   <el-button type="primary" @click="query.page=1;load()">查询</el-button>
  </div>
  <el-table :data="rows" v-loading="loading" stripe>
   <el-table-column label="独享分配号" min-width="185"><template #default="s">
    <el-button link type="primary" @click="show(s.row)">{{s.row.allocation_no}}</el-button>
   </template></el-table-column>
   <el-table-column prop="ip_address" label="历史IP记录" width="145"><template #default="s">{{fmt(s.row.ip_address)}}</template></el-table-column>
   <el-table-column prop="line_name" label="线路" min-width="130"/>
   <el-table-column prop="customer_name" label="客户" min-width="145"/>
   <el-table-column label="关联服务" min-width="155"><template #default="s">
    <el-button v-if="s.row.service_id&&auth.hasPermission('service:read')" link type="primary" @click="router.push(servicePath(s.row))">{{s.row.service_no||'查看服务'}}</el-button>
    <span v-else>{{s.row.service_id?'无服务查看权限':'缺关联'}}</span>
   </template></el-table-column>
   <el-table-column prop="status" label="历史分配状态" width="130"/>
   <el-table-column label="真实出口授权" min-width="155"><template #default="s">
    <el-tag :type="s.row.egress_claim_status==='NO_VERIFIED_CLAIM'?'danger':'warning'">
     {{s.row.egress_claim_status==='NO_VERIFIED_CLAIM'?'无有效出口快照':s.row.egress_claim_status}}
    </el-tag></template></el-table-column>
   <el-table-column prop="health_status" label="健康" width="90"/>
   <el-table-column prop="expire_at" label="记录到期" min-width="170"/>
   <el-table-column label="操作" width="235" fixed="right"><template #default="s">
    <el-button link type="primary" @click="show(s.row)">核对详情</el-button>
    <el-button v-if="s.row.service_id&&auth.hasPermission('service:change')&&auth.hasPermission('service:status')"
       link type="warning" @click="router.push(changePath(s.row))">从服务发起终止</el-button>
   </template></el-table-column>
  </el-table>
  <div class="pager"><el-pagination v-model:current-page="query.page" v-model:page-size="query.size"
    :total="total" layout="total, sizes, prev, pager, next" @change="load"/></div>
 </el-card>
 <el-drawer v-model="drawer" title="独享占用核对" size="580px">
  <template v-if="detail">
   <el-alert type="warning" :closable="false" title="这里只读展示历史分配及服务授权；缺真实停服和回收证明时，不能变更独享锁定状态。" />
   <el-descriptions :column="1" border style="margin-top:12px">
    <el-descriptions-item label="分配号">{{fmt(detail.allocation_no)}}</el-descriptions-item>
    <el-descriptions-item label="客户">{{fmt(detail.customer_name)}}</el-descriptions-item>
    <el-descriptions-item label="来源线路">{{fmt(detail.line_name)}}</el-descriptions-item>
    <el-descriptions-item label="历史IP">{{fmt(detail.ip_address)}}</el-descriptions-item>
    <el-descriptions-item label="历史状态">{{fmt(detail.status)}}</el-descriptions-item>
    <el-descriptions-item label="固定IPv4占用事实">{{detail.egress_claim_status==='NO_VERIFIED_CLAIM'?'缺有效出口占用快照，须先人工核对':fmt(detail.egress_claim_status)}}</el-descriptions-item>
    <el-descriptions-item label="服务">{{fmt(detail.service_no)}}</el-descriptions-item>
    <el-descriptions-item label="服务交付阶段">{{fmt(detail.delivery_stage)}}</el-descriptions-item>
    <el-descriptions-item label="服务宽限截至">{{fmt(detail.grace_until_at)}}</el-descriptions-item>
    <el-descriptions-item label="分配到期">{{fmt(detail.expire_at)}}</el-descriptions-item>
    <el-descriptions-item label="释放原因（历史记录）">{{fmt(detail.release_reason)}}</el-descriptions-item>
   </el-descriptions>
   <div style="display:flex;gap:10px;justify-content:flex-end;margin-top:18px">
    <el-button v-if="detail.service_id&&auth.hasPermission('service:read')"
     type="primary" @click="drawer=false;router.push(servicePath(detail))">查看服务出口与任务</el-button>
    <el-button @click="drawer=false">关闭</el-button>
   </div>
  </template>
 </el-drawer>
</div>
</template>
<style scoped>
.page{display:grid;gap:14px}.head h2{margin:0 0 6px}.head p{margin:0;color:#7b8494}
.filters{display:flex;gap:10px;margin-bottom:14px}.filters>*{max-width:280px}
.pager{display:flex;justify-content:flex-end;margin-top:14px}
</style>