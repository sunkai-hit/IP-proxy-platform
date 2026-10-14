<script setup lang="ts">
import {computed,onMounted,reactive,ref,watch} from 'vue'
import {useRoute,useRouter} from 'vue-router'
import {ElMessage,ElMessageBox} from 'element-plus'
import {orderServiceApi} from '@/api/orderService'
import {useAuthStore} from '@/stores/auth'
const route=useRoute(),router=useRouter(),auth=useAuthStore()
const loading=ref(false),saving=ref(false),services=ref<any[]>([]),result=ref<any>(null)
const form=reactive({serviceId:route.query.serviceId?Number(route.query.serviceId):null as number|null,action:'renew',days:30,reason:''})
const data=(r:any)=>r.data.data
const current=computed(()=>services.value.find(s=>s.id===form.serviceId)||null)
const actionLabels:Record<string,string>={renew:'续费',suspend:'暂停',resume:'恢复',terminate:'终止'}
const blocked=computed(()=>!form.serviceId||!form.reason.trim()||(form.action==='renew'&&form.days<1))
const warning=computed(()=>current.value?.delivery_stage!=='VERIFIED'?'当前管理面可能尚未完成真实数据面验证；操作返回不等于客户真实可访问。':'请确认变更对客户使用与占用出口的影响。')
async function load(){
  loading.value=true
  try {
    const r=data(await orderServiceApi.services({page:1,size:200}))
    services.value=r.items||[]
    if(form.serviceId&&!services.value.some(x=>x.id===form.serviceId)){
      const detail=data(await orderServiceApi.service(form.serviceId))
      if(detail?.id)services.value.unshift(detail)
    }
  }finally{loading.value=false}
}
async function submit(){
  if(blocked.value){ElMessage.warning('请选择服务、填写原因并检查续费天数');return}
  const s=current.value
  const facts=[
    `服务：${s?.service_no||form.serviceId} / ${s?.customer_name||''}`,
    `动作：${actionLabels[form.action]}`,
    `当前状态：${s?.status||'未知'}；真实交付：${s?.delivery_stage||'UNVERIFIED'}`,
    `到期：${s?.expire_at||'未知'}；宽限截至：${s?.grace_until_at||'未配置'}`,
    form.action==='renew'?`续费时长：${form.days}天；真实恢复与采购期限仍需后端校验`:'该动作可能影响当前客户访问与资源绑定',
    `原因：${form.reason.trim()}`
  ].join('\n')
  await ElMessageBox.confirm(facts,'核对服务与影响后确认',{type:form.action==='terminate'?'warning':'info',confirmButtonText:'确认提交',cancelButtonText:'返回修改'})
  saving.value=true;result.value=null
  try{
    let r:any
    if(form.action==='renew')r=await orderServiceApi.renew(form.serviceId!,form.days,form.reason)
    else if(form.action==='suspend')r=await orderServiceApi.suspend(form.serviceId!,form.reason)
    else if(form.action==='resume')r=await orderServiceApi.resume(form.serviceId!,form.reason)
    else r=await orderServiceApi.terminate(form.serviceId!,form.reason)
    result.value={action:actionLabels[form.action],service:s?.service_no||form.serviceId,managementResult:r?.data?.data?.status||'操作已受理',verified:false}
    ElMessage.success('管理面请求已返回；请核查真实交付/访问状态')
    await load()
  }catch(err:any){result.value={action:actionLabels[form.action],failed:true,message:err?.response?.data?.message||err?.message||'请求失败'};ElMessage.error('变更未确认成功，请查询任务与服务状态')}
  finally{saving.value=false}
}
watch(()=>route.query.serviceId,v=>{if(v)form.serviceId=Number(v)})
onMounted(load)
</script>
<template>
  <div class="page">
    <div class="head"><div><h2>服务生命周期变更</h2><p>按客户与服务业务编号选择对象 → 预览影响 → 明确确认 → 回查真实状态</p></div><el-button @click="router.push('/services')">返回服务列表</el-button></div>
    <el-alert :title="warning" type="warning" :closable="false" show-icon/>
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
          <el-descriptions-item label="真实交付阶段">{{current.delivery_stage||'UNVERIFIED'}}</el-descriptions-item>
          <el-descriptions-item label="到期时间">{{current.expire_at||'—'}}</el-descriptions-item>
          <el-descriptions-item label="宽限截止">{{current.grace_until_at||'—'}}</el-descriptions-item>
        </el-descriptions>
        <el-form-item label="变更动作"><el-radio-group v-model="form.action"><el-radio-button value="renew">续费</el-radio-button><el-radio-button value="suspend">暂停</el-radio-button><el-radio-button value="resume">恢复</el-radio-button><el-radio-button value="terminate">终止</el-radio-button></el-radio-group></el-form-item>
        <el-form-item v-if="form.action==='renew'" label="续费天数" required><el-input-number v-model="form.days" :min="1"/></el-form-item>
        <el-form-item label="操作原因" required><el-input v-model="form.reason" type="textarea" :rows="3" maxlength="1000" show-word-limit placeholder="填写可审计的业务原因"/></el-form-item>
        <el-form-item><el-button type="primary" :disabled="blocked||!auth.hasPermission('service:change')" :loading="saving" @click="submit">预览并确认{{actionLabels[form.action]}}</el-button><el-button v-if="form.serviceId" @click="router.push(`/services/${form.serviceId}`)">查看服务详情与任务</el-button></el-form-item>
      </el-form>
    </el-card>
    <el-alert v-if="result" :type="result.failed?'error':'warning'" :closable="false">
      <template #title>{{result.failed?'操作未确认成功':`${result.action}：管理面结果已返回（并非真实交付验收）`}}</template>
      <div v-if="result.failed">{{result.message}}</div><div v-else>服务：{{result.service}} · {{result.managementResult}}。请打开服务详情核验外部适配器回执、实际出口与客户端访问。</div>
    </el-alert>
  </div>
</template>
<style scoped>.page{display:grid;gap:16px}.head{display:flex;justify-content:space-between;align-items:center}.head h2{margin:0 0 6px}.head p{margin:0;color:#7b8494}</style>
