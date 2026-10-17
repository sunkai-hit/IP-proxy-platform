<script setup lang="ts">
import {computed,onMounted,reactive,ref,watch} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {ElMessage,ElMessageBox} from 'element-plus'
import {orderServiceApi} from '@/api/orderService'
import {businessRelationsApi} from '@/api/businessRelations'
import {useAuthStore} from '@/stores/auth'
const route=useRoute(),router=useRouter(),auth=useAuthStore()
const loading=ref(false),saving=ref(false),confirming=ref(false),loadError=ref(false),serviceLoadVersion=ref(0),services=ref<any[]>([]),paidRenewalOrders=ref<any[]>([]),result=ref<any>(null)
const paidTermDays=ref<number|null>(null),renewOrderError=ref('')
const renewalLookupLoading=ref(false),renewalOrderChecking=ref(false)
let renewalLookupVersion=0,renewalDetailVersion=0
const dayMs=24*60*60*1000
function parsedDate(value:any){const time=Date.parse(String(value||''));return Number.isFinite(time)?time:null}
const renewalImpact=computed(()=>{
 const svc=current.value
 if(!svc||form.action!=='renew')return {blocker:'',newExpire:''}
 const fixed=['LONG_IP','EXCLUSIVE_IP'].includes(svc.product_type)
 const old=parsedDate(svc.expire_at)
 if(old===null)return {blocker:'原合同到期时间缺失，必须核对',newExpire:''}
 const now=Date.now()
 if(fixed){
  if(svc.grace_days_snapshot==null)return {blocker:'原合同宽限快照缺失，不能按当前套餐猜测',newExpire:''}
  const grace=Number(svc.grace_days_snapshot)
  const graceUntil=parsedDate(svc.grace_until_at)??old+grace*dayMs
  if(!Number.isFinite(grace)||grace<0)return {blocker:'原合同宽限异常，需先核对',newExpire:''}
  if(now>graceUntil)return {blocker:'原固定IP宽限已结束，必须走重新开通流程',newExpire:''}
 }
 const base=fixed?old:Math.max(old,now)
 const end=base+Number(form.days)*dayMs
 if(!Number.isFinite(end)||end<=now)return {blocker:'本单续费时长不足以覆盖当前时间',newExpire:''}
 return {blocker:'',newExpire:new Date(end).toISOString()}
})
const initialAction=String(route.query.action||'renew')
const form=reactive({serviceId:route.query.serviceId?Number(route.query.serviceId):null as number|null,
 action:['renew','suspend','resume','terminate'].includes(initialAction)?initialAction:'renew',
 days:30,renewalOrderId:null as number|null,reason:''})
const data=(r:any)=>r.data.data
const current=computed(()=>services.value.find(s=>s.id===form.serviceId)||null)
const actionLabels:Record<string,string>={renew:'续费',suspend:'暂停',resume:'恢复',terminate:'终止'}
const canAct=computed(()=>auth.hasPermission('service:change')&&
 (form.action==='renew'?auth.hasPermission('service:renew'):auth.hasPermission('service:status')))
const blocked=computed(()=>loading.value||saving.value||confirming.value||loadError.value||renewalLookupLoading.value||renewalOrderChecking.value||!!renewOrderError.value||!current.value||!canAct.value||!form.serviceId||!form.reason.trim()||(form.action==='renew'&&(
 form.days<1||!form.renewalOrderId||!auth.hasPermission('service:renew')||
 paidTermDays.value===null||form.days!==paidTermDays.value||!!renewalImpact.value.blocker
)))
const warning=computed(()=>current.value?.delivery_stage!=='VERIFIED'?'当前管理面可能尚未完成真实数据面验证；操作返回不等于客户真实可访问。':'请确认变更对客户使用与占用出口的影响。')
async function load(){
 const version=++serviceLoadVersion.value
 // Invalidate in-flight paid-order and detail replies BEFORE starting the service refresh.
 ++renewalLookupVersion;++renewalDetailVersion
 renewalLookupLoading.value=false;renewalOrderChecking.value=false
 loading.value=true;loadError.value=false;services.value=[]
 paidRenewalOrders.value=[];form.renewalOrderId=null;paidTermDays.value=null
 renewOrderError.value=''
 try {
  const r=data(await orderServiceApi.services({page:1,size:200}))||{}
  if(version!==serviceLoadVersion.value)return
  const next=r.items||[]
  if(form.serviceId&&!next.some((x:any)=>Number(x.id)===Number(form.serviceId))){
   const detail=data(await orderServiceApi.service(form.serviceId))
   if(version!==serviceLoadVersion.value)return
   if(detail?.id)next.unshift(detail)
  }
  if(version===serviceLoadVersion.value)services.value=next
 }catch{
  if(version===serviceLoadVersion.value){
   loadError.value=true;services.value=[]
   paidRenewalOrders.value=[];form.renewalOrderId=null;paidTermDays.value=null
  }
 }finally{if(version===serviceLoadVersion.value)loading.value=false}
}
async function reloadServices(){
 await load()
 if(!loadError.value)await loadRenewalOrders()
}
async function loadRenewalOrders(){
 const version=++renewalLookupVersion
 ++renewalDetailVersion;renewalOrderChecking.value=false
 const serviceId=Number(form.serviceId)
 paidRenewalOrders.value=[];form.renewalOrderId=null;paidTermDays.value=null;renewOrderError.value=''
 renewalLookupLoading.value=false
 if(form.action!=='renew'||loadError.value||loading.value||!auth.hasPermission('order:read')||!current.value)return
 renewalLookupLoading.value=true
 try{
  const d=data(await businessRelationsApi.paidRenewalOrders(serviceId))
  if(version!==renewalLookupVersion||Number(form.serviceId)!==serviceId||form.action!=='renew')return
  paidRenewalOrders.value=d.items||[]
  if(d.truncated)ElMessage.warning('当前服务已付款续费订单超过200笔；较早订单请至订单管理核对')
  const requested=Number(route.query.renewalOrderId)
  if(requested>0&&paidRenewalOrders.value.some(o=>Number(o.id)===requested)){
    form.renewalOrderId=requested
    await selectRenewalOrder()
  }
 }catch{
  if(version===renewalLookupVersion){
   renewOrderError.value='已付款续费订单列表读取失败，不能据此认为没有可用续费订单'
   ElMessage.warning('无法读取已付款的续费订单，请检查订单查询权限')
  }
 }finally{if(version===renewalLookupVersion)renewalLookupLoading.value=false}
}
async function selectRenewalOrder(){
 const version=++renewalDetailVersion
 const orderId=Number(form.renewalOrderId)
 const serviceId=Number(form.serviceId)
 paidTermDays.value=null;renewOrderError.value='';renewalOrderChecking.value=false
 if(!orderId||form.action!=='renew'||!paidRenewalOrders.value.some(o=>Number(o.id)===orderId))return
 renewalOrderChecking.value=true
 try{
  const order=data(await orderServiceApi.order(orderId))
  if(version!==renewalDetailVersion||Number(form.renewalOrderId)!==orderId||Number(form.serviceId)!==serviceId||form.action!=='renew')return
  if(order?.order_type!=='RENEW'||order?.payment_status!=='PAID_CONFIRMED'||
    order?.status!=='PENDING_PROVISION'||Number(order?.renewal_service_id)!==Number(form.serviceId)||
    Number(order?.customer_id)!==Number(current.value?.customer_id)){
   renewOrderError.value='已选订单状态、付款或原服务归属不符，请回订单详情核对'
   return
  }
  let snapshot=order?.items?.[0]?.package_snapshot
  if(typeof snapshot==='string')snapshot=JSON.parse(snapshot)
  const days=Number(snapshot?.service_days)
  if((order?.items||[]).length!==1||!Number.isInteger(days)||days<=0){
   renewOrderError.value='续费订单缺少有效套餐期限快照，禁止使用默认30天'
   return
  }
  paidTermDays.value=days
  form.days=days
 }catch{
  if(version===renewalDetailVersion&&Number(form.renewalOrderId)===orderId&&Number(form.serviceId)===serviceId&&form.action==='renew')
   renewOrderError.value='无法确认续费订单套餐期限快照，必须核对订单详情'
 }finally{if(version===renewalDetailVersion)renewalOrderChecking.value=false}
}
async function submit(){
  if(blocked.value||confirming.value){ElMessage.warning('请先确认服务、权限、订单与操作原因');return}
  const s=current.value
  const beforeVersion=serviceLoadVersion.value
  const frozen={serviceId:Number(form.serviceId),action:form.action,days:Number(form.days),
   renewalOrderId:Number(form.renewalOrderId),reason:form.reason.trim(),status:s?.status,
   serviceNo:s?.service_no||form.serviceId}
  const facts=[
    `服务：${s?.service_no||form.serviceId} / ${s?.customer_name||''}`,
    `动作：${actionLabels[form.action]}`,
    `当前状态：${s?.status||'未知'}；真实交付：${s?.delivery_stage||'UNVERIFIED'}`,
    `到期：${s?.expire_at||'未知'}；宽限截至：${s?.grace_until_at||'未配置'}`,
    form.action==='renew'?`已付款续费订单：${form.renewalOrderId}；续费时长：${form.days}天；实际产品恢复须取得真实验证`:'该动作可能影响当前客户访问与资源绑定',
    `原因：${form.reason.trim()}`
  ].join('\n')
  confirming.value=true
  try{
   await ElMessageBox.confirm(facts,'核对服务与影响后确认',{type:frozen.action==='terminate'?'warning':'info',confirmButtonText:'确认提交',cancelButtonText:'返回修改'})
  }catch{return}
  finally{confirming.value=false}
  if(blocked.value||beforeVersion!==serviceLoadVersion.value||
    Number(form.serviceId)!==frozen.serviceId||form.action!==frozen.action||
    Number(form.days)!==frozen.days||Number(form.renewalOrderId)!==frozen.renewalOrderId||
    form.reason.trim()!==frozen.reason||current.value?.status!==frozen.status){
    ElMessage.warning('确认期间服务、订单或动作已变化，请重新核对后提交')
    return
  }
  saving.value=true;result.value=null
  try{
    let r:any
    if(frozen.action==='renew')r=await orderServiceApi.renew(frozen.serviceId,frozen.days,frozen.reason,frozen.renewalOrderId)
    else if(frozen.action==='suspend')r=await orderServiceApi.suspend(frozen.serviceId,frozen.reason)
    else if(frozen.action==='resume')r=await orderServiceApi.resume(frozen.serviceId,frozen.reason)
    else r=await orderServiceApi.terminate(frozen.serviceId,frozen.reason)
    if(beforeVersion!==serviceLoadVersion.value||Number(form.serviceId)!==frozen.serviceId||
       form.action!==frozen.action){
      ElMessage.warning('原服务管理请求已返回，但页面已切换服务或操作；请到原服务回查结果')
      return
    }
    const returned=r?.data?.data||{}
    if(['FAILED','PARTIAL_SUCCESS','RELEASE_BLOCKED'].includes(String(returned.status||''))){
      result.value={action:actionLabels[frozen.action],service:frozen.serviceNo,failed:true,
        message:returned.message||returned.errorMessage||'产品/资源动作未完成或未核验，请查询原服务任务'}
      ElMessage.error('后端返回未完成状态，不能认定已停服、恢复或释放')
      await load()
      return
    }
    result.value={action:actionLabels[frozen.action],service:frozen.serviceNo,
       managementResult:returned.status||'管理请求已返回',verified:false}
    ElMessage.warning('管理面请求已返回；实际网络状态和合同期限请独立回查')
    await load()
  }catch(err:any){
    if(beforeVersion===serviceLoadVersion.value&&Number(form.serviceId)===frozen.serviceId&&form.action===frozen.action){
      result.value={action:actionLabels[frozen.action],service:frozen.serviceNo,failed:true,
        message:err?.response?.data?.message||err?.message||'请求失败'}
      ElMessage.error('变更未确认成功，请查询任务与服务状态，不要盲目重复执行')
    }else ElMessage.warning('原服务变更请求未确认；页面已切换，请回原服务查阅任务及审计')
  }finally{saving.value=false}
}
watch(()=>route.query.serviceId,v=>{if(v)form.serviceId=Number(v)})
watch(()=>route.query.action,v=>{const a=String(v||'');if(['renew','suspend','resume','terminate'].includes(a))form.action=a})
watch(()=>form.serviceId,()=>{result.value=null;void loadRenewalOrders()})
watch(()=>form.action,()=>{result.value=null;void loadRenewalOrders()})
onMounted(reloadServices)
</script>
<template>
  <div class="page">
    <div class="head"><div><h2>服务生命周期变更</h2><p>按客户与服务业务编号选择对象 → 预览影响 → 明确确认 → 回查真实状态</p></div><div><el-button :disabled="loading||saving||confirming" @click="reloadServices">重新核对服务与续费订单</el-button><el-button @click="router.push('/services')">返回服务列表</el-button></div></div>
    <el-alert :title="warning" type="warning" :closable="false" show-icon/>
    <el-alert v-if="loadError" type="error" :closable="false"
      title="服务清单或目标服务读取失败，不能确认当前服务、到期与宽限状态；禁止执行生命周期变更。" />
    <el-button v-if="loadError" @click="reloadServices">重新加载服务清单</el-button>
    <el-card v-loading="loading" shadow="never">
      <el-form label-width="110px">
        <el-form-item label="客户服务" required>
          <el-select v-model="form.serviceId" filterable placeholder="按服务号或客户选择（不手填ID）" style="width:100%">
            <el-option v-for="s in services" :key="s.id" :value="s.id" :label="`${s.service_no} · ${s.customer_name} · ${s.product_name||s.product_type}`"/>
          </el-select>
        </el-form-item>
        <el-descriptions v-if="current" :column="2" border style="margin-bottom:18px">
          <el-descriptions-item label="所属客户">{{current.customer_name}}</el-descriptions-item>
          <el-descriptions-item label="产品/套餐">{{current.product_name}} / {{current.package_name||'未指定'}}</el-descriptions-item>
          <el-descriptions-item label="业务状态">{{current.status}}</el-descriptions-item>
          <el-descriptions-item v-if="current.order_id" label="来源订单"><el-button v-if="auth.hasPermission('order:read')" link type="primary" @click="router.push('/orders/'+current.order_id)">{{current.order_no||'查看订单'}}</el-button><span v-else>订单权限不足</span></el-descriptions-item>
          <el-descriptions-item label="真实交付阶段">{{current.delivery_stage||'UNVERIFIED'}}</el-descriptions-item>
          <el-descriptions-item label="到期时间">{{current.expire_at||'—'}}</el-descriptions-item>
          <el-descriptions-item label="宽限截止">{{current.grace_until_at||'—'}}</el-descriptions-item>
          <el-descriptions-item v-if="['LONG_IP','EXCLUSIVE_IP'].includes(current.product_type)" label="原合同宽限">{{current.grace_days_snapshot==null?'历史快照缺失，待人工核对':current.grace_days_snapshot+'天（不是当前套餐宽限）'}}</el-descriptions-item>
        </el-descriptions>
        <el-form-item label="变更动作"><el-radio-group v-model="form.action"><el-radio-button value="renew">续费</el-radio-button><el-radio-button value="suspend">暂停</el-radio-button><el-radio-button value="resume">恢复</el-radio-button><el-radio-button value="terminate">终止</el-radio-button></el-radio-group></el-form-item>
        <el-alert v-if="form.action==='renew'&&(renewalLookupLoading||renewalOrderChecking)" type="info" :closable="false" title="正在重新核验已付款续费订单及套餐期限，核验完成前禁止提交。" style="margin-bottom:12px"/>
        <el-form-item v-if="form.action==='renew'" label="已付款续费订单" required>
          <el-select v-model="form.renewalOrderId" filterable :disabled="renewalLookupLoading||loading||loadError" placeholder="选择已核实付款且绑定此服务的续费订单" style="width:100%" @change="selectRenewalOrder">
            <el-option v-for="o in paidRenewalOrders" :key="o.id" :value="o.id" :label="`${o.order_no} · ${o.total_amount} ${o.currency}`"/>
          </el-select>
        </el-form-item>
        <el-alert v-if="form.action==='renew'&&form.serviceId&&!renewalLookupLoading&&!renewOrderError&&!paidRenewalOrders.length" type="warning" :closable="false" style="margin-bottom:16px">
         <template #title>没有此服务已核实付款的续费订单。请先新建续费订单、确认订单并由授权人员核实收款；不能直接改服务期限。</template>
         <el-button style="margin-top:8px" @click="router.push({path:'/orders',query:{customerId:String(current.customer_id),newOrder:'1',orderType:'RENEW',renewalServiceId:String(form.serviceId)}})">为此客户新建续费订单</el-button>
        </el-alert>
        <el-form-item v-if="form.action==='renew'" label="订单期限（天）" required>
          <el-input-number v-model="form.days" :min="1" :disabled="true"/>
          <el-text type="info" style="margin-left:10px">以已付款订单的套餐期限快照为准，不得自行修改</el-text>
        </el-form-item>
        <el-alert v-if="form.action==='renew'&&renewOrderError" type="error" :closable="false" :title="renewOrderError" style="margin-bottom:12px"/>
        <el-alert v-if="form.action==='renew'&&renewalImpact.blocker" type="warning" :closable="false" :title="renewalImpact.blocker" style="margin-bottom:12px"/>
        <el-alert v-if="form.action==='renew'&&paidTermDays!==null&&renewalImpact.newExpire" type="info" :closable="false"
          :title="'按原服务合同试算的新到期（UTC）：'+renewalImpact.newExpire+'；实际结果以服务端验证后的执行记录为准'" style="margin-bottom:12px"/>
        <el-button v-if="form.action==='renew'&&form.renewalOrderId&&auth.hasPermission('order:read')" link type="primary"
          style="margin:0 0 12px" @click="router.push('/orders/'+form.renewalOrderId)">查看此已付款续费订单</el-button>
        <el-form-item label="操作原因" required><el-input v-model="form.reason" type="textarea" :rows="3" maxlength="1000" show-word-limit placeholder="填写可审计的业务原因"/></el-form-item>
        <el-form-item><el-button type="primary" :disabled="blocked" :loading="saving||confirming" @click="submit">预览并确认{{actionLabels[form.action]}}</el-button><el-button v-if="form.serviceId" @click="router.push(`/services/${form.serviceId}`)">查看服务详情与任务</el-button></el-form-item>
      </el-form>
    </el-card>
    <el-alert v-if="result" :type="result.failed?'error':'warning'" :closable="false">
      <template #title>{{result.failed?'操作未确认成功':`${result.action}：管理面结果已返回（并非真实交付验收）`}}</template>
      <div v-if="result.failed">{{result.message}}</div><div v-else>服务：{{result.service}} · {{result.managementResult}}。请打开服务详情核验外部适配器回执、实际出口与客户端访问。</div>
    </el-alert>
  </div>
</template>
<style scoped>.page{display:grid;gap:16px}.head{display:flex;justify-content:space-between;align-items:center}.head h2{margin:0 0 6px}.head p{margin:0;color:#7b8494}</style>
