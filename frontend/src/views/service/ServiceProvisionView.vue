<script setup lang="ts">
import { computed,onMounted,reactive,ref } from 'vue'
import { useRoute,useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { orderServiceApi } from '@/api/orderService'
import { useAuthStore } from '@/stores/auth'

const route=useRoute(),router=useRouter(),auth=useAuthStore()
const isRealVerified=(stage:any)=>stage==='VERIFIED'
const fullyVerified=(services:any[])=>services.length>0&&services.every(x=>isRealVerified(x.deliveryStage))
const listLoading=ref(false),loading=ref(false),submitting=ref(false),step=ref(0),listError=ref(false),previewError=ref(false),previewVersion=ref(0),listVersion=ref(0)
const orderRows=ref<any[]>([]),total=ref(0),snapshot=ref<any>(null),plans=ref<any[]>([]),result=ref<any>(null)
const q=reactive({page:1,size:20,keyword:'',status:'PENDING_PROVISION'})
const reason=ref('')
const unwrap=(r:any)=>r.data.data
const typeNames:Record<string,string>={SHORT_IP:'短效IP',LONG_IP:'长效IP',EXCLUSIVE_IP:'独享IP',VPN:'VPN',TUNNEL:'隧道'}
const modeNames:Record<string,string>={SELF_OWNED:'自有资源',EXTERNAL_PROXY:'外部反向代理'}
const order=computed(()=>snapshot.value?.order||{})
const items=computed<any[]>(()=>snapshot.value?.items||[])
const proxies=computed<any[]>(()=>snapshot.value?.availableReverseProxies||[])
const errors=computed(()=>plans.value.flatMap(p=>{
  const item=items.value.find(x=>Number(x.id)===p.orderItemId)
  if(!item)return ['找不到订单项']
  if(p.deliveryMode==='SELF_OWNED'){
    if(item.warning)return [item.product_name+': '+item.warning]
    if(item.precheckError)return [item.product_name+': '+item.precheckError]
    if(item.product_type==='EXCLUSIVE_IP'){
      const need=Number(item.exclusiveLinesPerService||1)*Number(item.service_count||1)
      if(Number(item.availableExclusiveLines||0)<need)return [item.product_name+': 独享线路不足']
    }
    return []
  }
  if(p.deliveryMode!=='EXTERNAL_PROXY')return ['请选择交付方式']
  if(p.reverseProxyIds.length!==Number(item.service_count))return [item.product_name+': 反向代理配置数量不正确']
  return p.reverseProxyIds.flatMap((id:number|null)=>!id?['请为'+item.product_name+'的每个服务选择反向代理']:!proxyEligible(proxies.value.find(x=>x.id===id))?['选择的反向代理状态或有效期不符合要求']:[])
}))
function dateOk(value:any,limit:any,before:boolean){if(!value||!limit)return true;const a=Date.parse(String(value)),b=Date.parse(String(limit));return !Number.isNaN(a)&&!Number.isNaN(b)&&(before?a<=b:a>=b)}
function proxyEligible(p:any){return !!p&&p.status==='ACTIVE'&&p.purchase_status==='ACTIVE'&&p.service_id==null&&dateOk(p.effective_at,order.value.expected_effective_at,true)&&dateOk(p.purchase_effective_at,order.value.expected_effective_at,true)&&dateOk(p.expire_at,order.value.expected_expire_at,false)&&dateOk(p.purchase_expire_at,order.value.expected_expire_at,false)}
function usedElsewhere(id:number,planIndex:number,slot:number){return plans.value.some((p,i)=>p.reverseProxyIds.some((x:any,j:number)=>x===id&&(i!==planIndex||j!==slot)))}
function proxyLabel(p:any){return [p.proxy_no,p.supplier_name,p.supplier_package_name,p.platform_host+':'+p.platform_port].filter(Boolean).join(' · ')}
async function loadOrders(){
 const version=++listVersion.value,params={...q}
 listLoading.value=true;listError.value=false;orderRows.value=[];total.value=0
 try{
  const d=unwrap(await orderServiceApi.orders(params))||{}
  if(version===listVersion.value){orderRows.value=d.items||[];total.value=d.total||0}
 }catch{if(version===listVersion.value){listError.value=true;ElMessage.error('待开通订单读取失败，不能据此认为没有待处理订单')}}
 finally{if(version===listVersion.value)listLoading.value=false}
}
async function openOrder(id:number){
 if(submitting.value)return
 const version=++previewVersion.value
 loading.value=true;previewError.value=false;snapshot.value=null;plans.value=[];result.value=null;step.value=0
 try{
  const v=unwrap(await orderServiceApi.provisionPreview(id))
  if(version!==previewVersion.value)return
  if(!v?.order?.id||Number(v.order.id)!==id)throw new Error('PREVIEW_ORDER_MISMATCH')
  snapshot.value=v;step.value=0;reason.value=''
  plans.value=(v.items||[]).map((it:any)=>({orderItemId:Number(it.id),deliveryMode:'SELF_OWNED',reverseProxyIds:[] as (number|null)[]}))
  await router.replace({path:'/service-provision',query:{orderId:String(id)}})
 }catch{if(version===previewVersion.value){previewError.value=true;ElMessage.error('开通预检失败，不能继续使用上一次付款或资源校验结果')}}
 finally{if(version===previewVersion.value)loading.value=false}
}
async function refreshPreview(){
 const id=Number(order.value.id),version=++previewVersion.value
 if(!id)return
 loading.value=true;previewError.value=false
 try{
  const updated=unwrap(await orderServiceApi.provisionPreview(id))
  if(version!==previewVersion.value)return
  if(!updated?.order?.id||Number(updated.order.id)!==id)throw new Error('PREVIEW_ORDER_MISMATCH')
  snapshot.value=updated;result.value=null;step.value=0
  ElMessage.info('付款与资源预检已刷新，请重新核对交付配置；提交仍由后端最终重验')
 }catch{
  if(version===previewVersion.value){
   snapshot.value=null;plans.value=[];result.value=null;step.value=0;previewError.value=true
   ElMessage.error('付款或资源预检刷新失败，已清除旧快照，禁止沿用旧信息开通')
  }
 }finally{if(version===previewVersion.value)loading.value=false}
}
function changeMode(p:any,mode:string,item:any){p.deliveryMode=mode;p.reverseProxyIds=mode==='EXTERNAL_PROXY'?Array(Number(item.service_count||1)).fill(null):[]}
function next(){if(!snapshot.value?.paymentReady){ElMessage.warning('客户付款尚未核实，不能执行订单开通');return}if(step.value===1&&errors.value.length){ElMessage.warning(errors.value[0]);return}step.value=Math.min(3,step.value+1)}
function back(){step.value=Math.max(0,step.value-1)}
function closeOrder(){if(submitting.value)return;++previewVersion.value;snapshot.value=null;plans.value=[];result.value=null;step.value=0;previewError.value=false;router.replace('/service-provision');loadOrders()}
async function confirm(){if(submitting.value||loading.value||previewError.value)return;if(!snapshot.value?.paymentReady){ElMessage.error('请先核实客户付款');return}if(errors.value.length){ElMessage.warning(errors.value[0]);step.value=1;return}
 if(!reason.value.trim()){ElMessage.warning('请输入开通原因');return}
 if(!auth.hasPermission('order:provision')){ElMessage.error('没有订单开通权限');return}
 submitting.value=true
 try{
  result.value=unwrap(await orderServiceApi.provisionOrder(Number(order.value.id),reason.value.trim(),plans.value))
  step.value=4
  if(fullyVerified(result.value?.services||[]))ElMessage.success('各服务实际交付核验通过')
  else ElMessage.warning('服务实例已登记，尚无全部服务真实交付验证；请逐项回查')
 }
 catch{ElMessage.error('开通未完成，订单保持待开通；请检查原因后重新预检')}finally{submitting.value=false}}
function copyText(s:any){if(s)navigator.clipboard?.writeText(String(s)).then(()=>ElMessage.success('已复制'))}
onMounted(()=>{loadOrders();const id=Number(route.query.orderId);if(id>0)openOrder(id)})
</script>
<template>
<div class="page-stack">
 <div class="page-head"><div><h1>服务开通</h1><p>订单已确认且付款已核实 → 交付配置 → 资源检查 → 确认开通 → 结果交付</p></div><el-button v-if="snapshot" @click="closeOrder">返回待开通订单</el-button></div>
 <template v-if="!snapshot">
  <el-alert v-if="previewError" type="error" :closable="false" title="订单付款及资源预检失败或已失效，旧快照已清除，不能继续开通。"/>
  <el-alert v-if="listError" type="error" :closable="false" title="待开通订单查询失败，无法确认是否存在待处理订单。"/>
  <el-button v-if="listError" @click="loadOrders">重新查询待开通订单</el-button>
  <el-alert type="info" :closable="false" title="此处只处理待开通订单。管理员特殊手工开通不属于日常订单流程，需单独授权。"/>
  <el-card shadow="never"><div class="filters"><el-input v-model="q.keyword" placeholder="订单号/客户" clearable style="width:280px" @keyup.enter="q.page=1;loadOrders()"/><el-button type="primary" @click="q.page=1;loadOrders()">查询</el-button></div>
   <el-table :data="orderRows" v-loading="listLoading" :empty-text="listError?'待开通订单读取失败':'暂无当前筛选的待开通订单'" stripe><el-table-column label="付款状态" width="118"><template #default="s"><el-tag :type="s.row.payment_status==='PAID_CONFIRMED'?'success':'warning'">{{s.row.payment_status==='PAID_CONFIRMED'?'已核实':'待核实'}}</el-tag></template></el-table-column><el-table-column prop="order_no" label="订单号" width="190"/><el-table-column prop="customer_name" label="客户" min-width="170"/><el-table-column prop="order_type" label="订单类型" width="100"/><el-table-column prop="item_total" label="订单项" width="80"/><el-table-column prop="service_total" label="服务实例数" width="105"/><el-table-column prop="expected_effective_at" label="预计生效" width="180"/><el-table-column prop="expected_expire_at" label="预计到期" width="180"/><el-table-column label="操作" width="120" fixed="right"><template #default="s"><el-button v-if="s.row.order_type==='NEW'" link type="primary" @click="openOrder(s.row.id)">进入开通</el-button><span v-else class="subtle">请走服务变更</span></template></el-table-column></el-table>
   <div class="pager"><el-pagination v-model:current-page="q.page" v-model:page-size="q.size" :total="total" layout="total,prev,pager,next" @change="loadOrders"/></div>
  </el-card>
 </template>
 <template v-else>
  <el-steps :active="step" finish-status="success" align-center><el-step title="订单确认"/><el-step title="交付配置"/><el-step title="资源检查"/><el-step title="开通确认"/><el-step title="开通结果"/></el-steps>
  <el-card shadow="never" v-loading="loading">
   <template #header><b>{{order.order_no}} · {{order.customer_name}}</b></template>
   <el-descriptions :column="3" border><el-descriptions-item label="订单状态">{{order.status}}</el-descriptions-item><el-descriptions-item label="预计生效">{{order.expected_effective_at||'按当前时间'}}</el-descriptions-item><el-descriptions-item label="预计到期">{{order.expected_expire_at||'按套餐周期'}}</el-descriptions-item></el-descriptions>
   <el-table :data="items" stripe style="margin-top:14px"><el-table-column prop="product_name" label="产品"/><el-table-column label="产品类型"><template #default="s">{{typeNames[s.row.product_type]||s.row.product_type}}</template></el-table-column><el-table-column prop="package_name" label="套餐"/><el-table-column prop="service_count" label="服务数" width="100"/></el-table>
  </el-card>
  <el-card v-if="step===0" shadow="never"><h3>核对订单和付款</h3><el-alert v-if="!snapshot.paymentReady" title="客户付款尚未核实，订单确认不等于付款成功；请到订单详情由授权人员核实付款后再回来刷新。" type="error" :closable="false" show-icon/><el-alert v-else title="订单付款凭证已由授权人员核实，实际开通仍须校验资源和真实数据面。" type="success" :closable="false"/><div class="filters" style="margin-top:8px"><el-button @click="router.push('/orders/'+order.id)">打开订单详情核实付款</el-button><el-button @click="refreshPreview">刷新付款与资源状态</el-button></div><p>客户、产品、套餐和服务数均来自已确认订单，不在本步骤重新编辑。进入下一步配置每个订单项的交付方式。</p><el-alert v-if="snapshot.alreadyProvisioned" title="订单已存在服务记录，系统会禁止再次重复开通，请先核查" type="error" show-icon/></el-card>
  <el-card v-if="step===1" shadow="never"><h3>配置订单项交付方式</h3>
   <div v-for="(p,i) in plans" :key="p.orderItemId" class="plan">
    <b>{{items[i]?.product_name}} · {{typeNames[items[i]?.product_type]}} · {{items[i]?.service_count}}个服务</b>
    <el-radio-group :model-value="p.deliveryMode" @update:model-value="changeMode(p,String($event),items[i])" style="margin:12px 0"><el-radio-button value="SELF_OWNED">自有资源</el-radio-button><el-radio-button value="EXTERNAL_PROXY">外部反向代理</el-radio-button></el-radio-group>
    <el-alert v-if="p.deliveryMode==='SELF_OWNED'" :type="items[i]?.warning?'warning':'info'" :closable="false" :title="items[i]?.warning||items[i]?.precheckError||items[i]?.deliveryAdvice||'按内置规则自动选择资源'"/>
    <template v-else><p class="subtle">请为每个服务实例选择一个尚未占用、客户一致且期限覆盖的平台代理入口。采购套餐自动关联。</p>
     <div v-for="(_,j) in p.reverseProxyIds" :key="j" class="proxy-choice"><span>服务 {{Number(j)+1}}</span><el-select v-model="p.reverseProxyIds[j]" filterable placeholder="选择可交付的反向代理" style="width:min(100%,680px)"><el-option v-for="proxy in proxies.filter(proxyEligible)" :key="proxy.id" :label="proxyLabel(proxy)" :value="proxy.id" :disabled="usedElsewhere(proxy.id,Number(i),Number(j))"/></el-select></div>
     <el-empty v-if="!proxies.filter(proxyEligible).length" description="暂无符合当前订单期限的可交付反向代理；请先完成供应商采购与反向代理配置"/>
    </template>
   </div>
  </el-card>
  <el-card v-if="step===2" shadow="never"><div class="filters" style="justify-content:space-between"><h3>资源预检</h3><el-button @click="refreshPreview">刷新资源预检</el-button></div><el-alert v-if="errors.length" type="error" :closable="false" title="存在需要处理的资源问题"/><el-alert v-else type="success" :closable="false" title="当前配置满足前端预检；提交时后端将重新检查资源与并发占用"/>
   <p v-for="(e,i) in errors" :key="i" class="error">• {{e}}</p>
   <el-descriptions :column="1" border style="margin-top:16px"><el-descriptions-item v-for="(p,i) in plans" :key="p.orderItemId" :label="items[i]?.product_name">{{modeNames[p.deliveryMode]}} · {{items[i]?.service_count}} 个实例 · {{p.deliveryMode==='EXTERNAL_PROXY'?p.reverseProxyIds.length+' 个代理入口':(items[i]?.resourcePrecheck?.poolCode||'独立产品资源')}} <span v-if="p.deliveryMode==='SELF_OWNED'&&items[i]?.resourcePrecheck?.lineCode">· 推荐线路 {{items[i]?.resourcePrecheck?.lineCode}}</span> <span v-if="items[i]?.product_type==='EXCLUSIVE_IP'&&p.deliveryMode==='SELF_OWNED'">· 独享可用线路 {{items[i]?.availableExclusiveLines||0}}</span></el-descriptions-item></el-descriptions>
  </el-card>
  <el-card v-if="step===3" shadow="never"><h3>确认执行开通</h3><el-alert type="warning" :closable="false" title="提交后平台执行开通编排并记录各实例结果；只有产品数据面真实核验通过才能显示已交付。订单和资源资格将由后端再次校验。"/>
   <el-descriptions :column="1" border style="margin-top:16px"><el-descriptions-item v-for="(p,i) in plans" :key="p.orderItemId" :label="items[i]?.product_name">{{modeNames[p.deliveryMode]}} · {{items[i]?.service_count}} 个服务</el-descriptions-item></el-descriptions>
   <el-form label-width="90px" style="margin-top:18px;max-width:720px"><el-form-item label="开通原因" required><el-input v-model="reason" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="请填写本次开通原因或业务说明"/></el-form-item></el-form>
  </el-card>
  <el-card v-if="step===4" shadow="never"><el-result :icon="fullyVerified(result?.services||[])?'success':'warning'"
   :title="fullyVerified(result?.services||[])?'真实交付已核验':'开通编排已返回，仍有服务待真实交付核验'"
   sub-title="仅首次显示一次性凭证；Mock/仅管理面状态不代表代理可用，请打开服务详情核验实际交付阶段"/><el-table :data="result?.services||[]" border><el-table-column prop="serviceNo" label="服务编号" min-width="180"/><el-table-column label="交付方式" width="125"><template #default="s">{{modeNames[s.row.deliveryMode]}}</template></el-table-column><el-table-column prop="status" label="状态" width="90"/><el-table-column label="交付确认" width="160"><template #default="s"><el-tag :type="isRealVerified(s.row.deliveryStage)?'success':'warning'">{{isRealVerified(s.row.deliveryStage)?'真实交付已验证':(s.row.deliveryStage||'UNVERIFIED')+' · 未证实真实交付'}}</el-tag></template></el-table-column><el-table-column label="一次性凭证" min-width="250"><template #default="s"><span>{{s.row.oneTimeCredential?.account||s.row.oneTimeCredential?.type}} · </span><el-button v-if="s.row.oneTimeCredential?.secret" link type="primary" @click="copyText(s.row.oneTimeCredential?.secret)">复制密钥（仅本次）</el-button><span v-else class="subtle">{{s.row.oneTimeCredential?.mask||'使用已有认证配置'}}</span></template></el-table-column><el-table-column label="操作" width="100"><template #default="s"><el-button link @click="router.push('/services/'+s.row.serviceId)">服务详情</el-button></template></el-table-column></el-table></el-card>
  <div v-if="step<4" class="footer"><el-button :disabled="step===0" @click="back">上一步</el-button><el-button v-if="step<3" type="primary" :disabled="!snapshot.paymentReady||snapshot.alreadyProvisioned||(step===1&&errors.length>0)||(step===2&&errors.length>0)" @click="next">下一步</el-button><el-button v-else type="primary" :loading="submitting" :disabled="loading||previewError||!snapshot.paymentReady||errors.length>0||!reason.trim()||!auth.hasPermission('order:provision')" @click="confirm">确认开通</el-button></div>
  <div v-else class="footer"><el-button @click="router.push('/orders/'+order.id)">查看订单与收款凭证</el-button><el-button type="primary" @click="closeOrder">返回待开通订单</el-button></div>
 </template>
</div>
</template>
<style scoped>
.page-stack{display:grid;gap:16px}.page-head{display:flex;justify-content:space-between;align-items:center}.page-head h1{margin:0 0 6px}.page-head p,.subtle{color:#667085}.filters,.footer{display:flex;gap:12px;align-items:center}.filters{margin-bottom:14px}.footer{justify-content:flex-end}.pager{display:flex;justify-content:flex-end;margin-top:14px}.plan{border:1px solid #e4e7ec;border-radius:8px;padding:18px;margin-bottom:14px}.plan b{display:block}.proxy-choice{display:flex;align-items:center;gap:12px;margin:12px 0}.proxy-choice span{width:64px;flex-shrink:0}.error{color:#d92d20}
</style>