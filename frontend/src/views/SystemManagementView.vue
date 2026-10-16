<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { systemApi } from '@/api/system'
import { useAuthStore } from '@/stores/auth'
const auth=useAuthStore()

const tab=ref('users'); const users=ref<any[]>([]); const roles=ref<any[]>([]); const permissions=ref<any[]>([]); const dictTypes=ref<any[]>([]); const dictItems=ref<any[]>([]); const parameters=ref<any[]>([]); const loginLogs=ref<any[]>([]); const operationLogs=ref<any[]>([])
const selectedDictType=ref<any>(null); const selectedRole=ref<any>(null); const checkedPermissionIds=ref<number[]>([])
const userDialog=ref(false); const roleDialog=ref(false); const dictTypeDialog=ref(false); const dictItemDialog=ref(false); const paramDialog=ref(false)
const editId=ref<number|null>(null)
const usersError=ref(false),rolesError=ref(false),permissionsError=ref(false)
const loginLogsError=ref(false),operationLogsError=ref(false)
const userBusy=ref(false),rolePermissionBusy=ref(false),rolePermissionLoading=ref(false),rolePermissionError=ref(false)
const userListVersion=ref(0),rolePermissionVersion=ref(0),loginLogVersion=ref(0),operationLogVersion=ref(0)
const tabRights:Record<string,string>={users:'system:user:read',roles:'system:role:read',permissions:'system:permission:read',dict:'system:dict:read',params:'system:param:read',login:'system:login-log:read',audit:'system:operation-log:read'}
const userForm=reactive<any>({username:'',password:'',displayName:'',department:'',mobile:'',email:'',status:'ACTIVE',roleIds:[]})
const roleForm=reactive<any>({roleCode:'',roleName:'',dataScope:'ALL',description:'',status:'ACTIVE'})
const dictTypeForm=reactive<any>({dictCode:'',dictName:'',description:'',editable:true,status:'ACTIVE'})
const dictItemForm=reactive<any>({itemCode:'',itemName:'',itemValue:'',sortOrder:0,colorTag:'',isDefault:false,status:'ACTIVE',remark:''})
const paramForm=reactive<any>({paramGroup:'SYSTEM',paramCode:'',paramName:'',paramValue:'',valueType:'STRING',sensitive:false,description:'',status:'ACTIVE'})

const data=(r:any)=>r.data.data
async function loadUsers(){
 if(!auth.hasPermission('system:user:read'))return
 const version=++userListVersion.value
 usersError.value=false;users.value=[]
 try{const result=data(await systemApi.users());if(version===userListVersion.value)users.value=Array.isArray(result)?result:[]}
 catch{if(version===userListVersion.value){usersError.value=true;users.value=[]}}
}
async function loadRoles(){
 if(!auth.hasPermission('system:role:read'))return
 ++rolePermissionVersion.value;selectedRole.value=null;checkedPermissionIds.value=[];rolePermissionError.value=false
 rolesError.value=false;roles.value=[]
 try{const result=data(await systemApi.roles());roles.value=Array.isArray(result)?result:[]}
 catch{rolesError.value=true;roles.value=[]}
}
async function loadPermissions(){
 if(!auth.hasPermission('system:permission:read')){permissions.value=[];permissionsError.value=true;return}
 permissionsError.value=false;permissions.value=[]
 try{const result=data(await systemApi.permissions());permissions.value=Array.isArray(result)?result:[]}
 catch{permissionsError.value=true;permissions.value=[]}
}
async function loadDictTypes(){dictTypes.value=data(await systemApi.dictTypes());if(selectedDictType.value)selectedDictType.value=dictTypes.value.find(x=>x.id===selectedDictType.value.id)||null}
async function loadDictItems(row?:any){if(row)selectedDictType.value=row;if(!selectedDictType.value){dictItems.value=[];return}dictItems.value=data(await systemApi.dictItems(selectedDictType.value.id))}
async function loadParameters(){parameters.value=data(await systemApi.parameters())}
async function loadLoginLogs(){
 if(!auth.hasPermission('system:login-log:read'))return
 const version=++loginLogVersion.value;loginLogsError.value=false;loginLogs.value=[]
 try{const result=data(await systemApi.loginLogs());if(version===loginLogVersion.value)loginLogs.value=Array.isArray(result)?result:[]}
 catch{if(version===loginLogVersion.value){loginLogsError.value=true;loginLogs.value=[]}}
}
async function loadOperationLogs(){
 if(!auth.hasPermission('system:operation-log:read'))return
 const version=++operationLogVersion.value;operationLogsError.value=false;operationLogs.value=[]
 try{const result=data(await systemApi.operationLogs());if(version===operationLogVersion.value)operationLogs.value=Array.isArray(result)?result:[]}
 catch{if(version===operationLogVersion.value){operationLogsError.value=true;operationLogs.value=[]}}
}
async function loadActiveTab(){
 if(!auth.hasPermission(tabRights[tab.value]))return
 if(tab.value==='users'){await Promise.all([loadUsers(),auth.hasPermission('system:role:read')?loadRoles():Promise.resolve()]);return}
 if(tab.value==='roles'){await Promise.all([loadRoles(),loadPermissions()]);return}
 if(tab.value==='permissions'){await loadPermissions();return}
 if(tab.value==='dict'){await loadDictTypes();return}
 if(tab.value==='params'){await loadParameters();return}
 if(tab.value==='login'){await loadLoginLogs();return}
 if(tab.value==='audit'){await loadOperationLogs()}
}

function copyForm(target:any,source:any){Object.keys(target).forEach(k=>target[k]=source?.[k] ?? (typeof target[k]==='boolean'?false:Array.isArray(target[k])?[]:''))}
function roleIds(row:any){return String(row.role_ids||'').split(',').filter(Boolean).map(Number)}
function newUser(){
 if(userBusy.value||!auth.hasPermission('system:user:write'))return
 editId.value=null;copyForm(userForm,{status:'ACTIVE',roleIds:[]});userDialog.value=true
}
function editUser(row:any){
 if(userBusy.value||usersError.value||!auth.hasPermission('system:user:write')||!users.value.some(u=>Number(u.id)===Number(row.id)))return
 editId.value=row.id;copyForm(userForm,{...row,displayName:row.display_name,roleIds:roleIds(row)});userDialog.value=true
}
async function saveUser(){
 if(userBusy.value||!userDialog.value||!auth.hasPermission('system:user:write'))return
 const id=editId.value
 if(id&&usersError.value)return
 if(!id&&!userForm.password){ElMessage.warning('新用户必须设置初始密码');return}
 const payload={...userForm,roleIds:[...userForm.roleIds]}
 userBusy.value=true
 try{
  if(id)await systemApi.updateUser(id,payload)
  else await systemApi.createUser(payload)
  if(tab.value==='users'){userDialog.value=false;userForm.password='';ElMessage.success('用户保存请求已返回，请核对账号状态和操作审计');await loadUsers()}
 }catch{ElMessage.error('用户保存未确认成功，请核对账号与操作审计后重试')}
 finally{userBusy.value=false}
}
async function resetPassword(row:any){
 const id=Number(row?.id),username=String(row?.username||''),version=userListVersion.value
 if(userBusy.value||usersError.value||!auth.hasPermission('system:user:write')||!users.value.some(u=>Number(u.id)===id&&u.username===username))return
 userBusy.value=true
 try{
  const {value}=await ElMessageBox.prompt(`目标管理员账号：${username}。请输入新密码`,'重置后台用户密码',{
   inputType:'password',inputPattern:/.{6,}/,inputErrorMessage:'密码至少6位'
  })
  if(tab.value!=='users'||usersError.value||userListVersion.value!==version||
   !users.value.some(u=>Number(u.id)===id&&u.username===username)||!auth.hasPermission('system:user:write')){
   ElMessage.warning('确认期间用户、账号信息或权限已变化，密码重置未提交');return
  }
  await systemApi.resetPassword(id,String(value))
  if(tab.value==='users')ElMessage.success('密码重置管理请求已返回，请核对操作审计')
 }catch(e:any){if(e!=='cancel'&&e!=='close')ElMessage.error('密码重置未确认成功，请先回查审计')}
 finally{userBusy.value=false}
}

function newRole(){editId.value=null;copyForm(roleForm,{dataScope:'ALL',status:'ACTIVE'});roleDialog.value=true}
function editRole(row:any){if(row.built_in)return;editId.value=row.id;copyForm(roleForm,{roleCode:row.role_code,roleName:row.role_name,dataScope:row.data_scope,description:row.description,status:row.status});roleDialog.value=true}
async function saveRole(){editId.value?await systemApi.updateRole(editId.value,roleForm):await systemApi.createRole(roleForm);roleDialog.value=false;ElMessage.success('角色已保存');await loadRoles()}
async function selectRole(row:any){
 const version=++rolePermissionVersion.value
 selectedRole.value=row||null;checkedPermissionIds.value=[];rolePermissionError.value=false;rolePermissionLoading.value=false
 if(!row||rolesError.value||permissionsError.value||!auth.hasPermission('system:role:read')||
  !roles.value.some(r=>Number(r.id)===Number(row.id)))return
 const roleId=Number(row.id)
 rolePermissionLoading.value=true
 try{
  const values=data(await systemApi.rolePermissions(roleId))
  if(version!==rolePermissionVersion.value||Number(selectedRole.value?.id)!==roleId||tab.value!=='roles')return
  if(!Array.isArray(values))throw new Error('ROLE_PERMISSION_RESPONSE_INVALID')
  checkedPermissionIds.value=values.map((x:any)=>Number(x.permission_id))
 }catch{
  if(version===rolePermissionVersion.value){rolePermissionError.value=true;checkedPermissionIds.value=[]}
 }finally{if(version===rolePermissionVersion.value)rolePermissionLoading.value=false}
}
async function saveRolePermissions(){
 const role=selectedRole.value,version=rolePermissionVersion.value
 if(!role||role.built_in||rolePermissionBusy.value||rolePermissionLoading.value||
  rolesError.value||permissionsError.value||rolePermissionError.value||
  !auth.hasPermission('system:role:write')||!auth.hasPermission('system:permission:read'))return
 const roleId=Number(role.id),roleCode=String(role.role_code||''),roleName=String(role.role_name||'')
 const ids=[...checkedPermissionIds.value].map(Number)
 if(ids.some(id=>!permissions.value.some(p=>Number(p.id)===id))){ElMessage.warning('授权目录不完整，禁止提交未知权限');return}
 rolePermissionBusy.value=true
 try{
  await ElMessageBox.confirm(`目标角色：${roleName}（${roleCode}）。确认将保存 ${ids.length} 项权限？这是管理平台授权变更。`,'确认角色授权',{
   type:'warning',confirmButtonText:'确认保存授权',cancelButtonText:'返回检查'
  })
  if(tab.value!=='roles'||rolePermissionVersion.value!==version||
   Number(selectedRole.value?.id)!==roleId||String(selectedRole.value?.role_code)!==roleCode||
   rolesError.value||permissionsError.value||rolePermissionError.value||
   !auth.hasPermission('system:role:write')||!auth.hasPermission('system:permission:read')){
   ElMessage.warning('确认期间目标角色或权限状态发生变化，未提交授权');return
  }
  await systemApi.saveRolePermissions(roleId,ids)
  if(tab.value==='roles'&&Number(selectedRole.value?.id)===roleId&&rolePermissionVersion.value===version){
   ElMessage.success('授权请求已返回，请核对审计与角色权限回读')
   await selectRole(selectedRole.value)
  }
 }catch(e:any){if(e!=='cancel'&&e!=='close')ElMessage.error('角色授权未确认成功，请先回查审计，避免重复授权')}
 finally{rolePermissionBusy.value=false}
}

function newDictType(){editId.value=null;copyForm(dictTypeForm,{editable:true,status:'ACTIVE'});dictTypeDialog.value=true}
function editDictType(row:any){editId.value=row.id;copyForm(dictTypeForm,{dictCode:row.dict_code,dictName:row.dict_name,description:row.description,editable:row.editable,status:row.status});dictTypeDialog.value=true}
async function saveDictType(){editId.value?await systemApi.updateDictType(editId.value,dictTypeForm):await systemApi.createDictType(dictTypeForm);dictTypeDialog.value=false;ElMessage.success('字典类型已保存');await loadDictTypes()}
function newDictItem(){if(!selectedDictType.value){ElMessage.warning('请先选择字典类型');return}editId.value=null;copyForm(dictItemForm,{sortOrder:0,isDefault:false,status:'ACTIVE'});dictItemDialog.value=true}
function editDictItem(row:any){editId.value=row.id;copyForm(dictItemForm,{itemCode:row.item_code,itemName:row.item_name,itemValue:row.item_value,sortOrder:row.sort_order,colorTag:row.color_tag,isDefault:row.is_default,status:row.status,remark:row.remark});dictItemDialog.value=true}
async function saveDictItem(){editId.value?await systemApi.updateDictItem(editId.value,dictItemForm):await systemApi.createDictItem(selectedDictType.value.id,dictItemForm);dictItemDialog.value=false;ElMessage.success('字典项已保存');await loadDictItems()}

function newParam(){editId.value=null;copyForm(paramForm,{paramGroup:'SYSTEM',valueType:'STRING',sensitive:false,status:'ACTIVE'});paramDialog.value=true}
function editParam(row:any){editId.value=row.id;copyForm(paramForm,{paramGroup:row.param_group,paramCode:row.param_code,paramName:row.param_name,paramValue:row.param_value,valueType:row.value_type,sensitive:row.sensitive,description:row.description,status:row.status});paramDialog.value=true}
async function saveParam(){editId.value?await systemApi.updateParameter(editId.value,paramForm):await systemApi.createParameter(paramForm);paramDialog.value=false;ElMessage.success('系统参数已保存');await loadParameters()}

watch(tab,()=>{++rolePermissionVersion.value;selectedRole.value=null;checkedPermissionIds.value=[];void loadActiveTab()})
onMounted(()=>{
 const first=Object.keys(tabRights).find(t=>auth.hasPermission(tabRights[t]))
 if(!first)return
 if(first!==tab.value)tab.value=first
 else void loadActiveTab()
})
</script>

<template>
  <div class="system-page">
    <div class="page-head"><div><h2>系统基础能力</h2><p>用户、角色权限、字典参数与审计日志均已接入真实后端。</p></div><el-tag type="success">M2</el-tag></div>
    <el-card shadow="never">
      <el-tabs v-model="tab">
        <el-tab-pane v-if="auth.hasPermission('system:user:read')" label="用户管理" name="users">
          <div class="toolbar"><el-button v-if="auth.hasPermission('system:user:write')" type="primary" :disabled="userBusy" @click="newUser">新增用户</el-button><el-button @click="loadUsers">刷新</el-button></div><el-alert v-if="usersError" type="error" :closable="false" title="后台用户列表读取失败，账号和角色状态未知；禁止据此执行编辑或密码重置。"/><el-button v-if="usersError" @click="loadUsers">重新加载用户列表</el-button>
          <el-table :data="users" :empty-text="usersError?'后台用户读取失败':'暂无后台用户记录'" stripe><el-table-column prop="username" label="用户名"/><el-table-column prop="display_name" label="姓名"/><el-table-column prop="department" label="部门"/><el-table-column prop="role_names" label="角色"/><el-table-column prop="status" label="状态" width="100"/><el-table-column prop="last_login_at" label="最后登录" width="190"/><el-table-column label="操作" width="190"><template #default="s"><template v-if="auth.hasPermission('system:user:write')"><el-button link type="primary" :disabled="userBusy||usersError" @click="editUser(s.row)">编辑</el-button><el-button link :disabled="userBusy||usersError" @click="resetPassword(s.row)">重置密码</el-button></template></template></el-table-column></el-table>
        </el-tab-pane>
        <el-tab-pane v-if="auth.hasPermission('system:role:read')" label="角色与权限" name="roles">
          <div class="split">
            <div><div class="toolbar"><el-button v-if="auth.hasPermission('system:role:write')" type="primary" @click="newRole">新增角色</el-button><el-button @click="loadActiveTab">刷新角色与权限</el-button></div><el-alert v-if="rolesError" type="error" :closable="false" title="角色列表读取失败，不能核对或变更角色授权。"/><el-table :data="roles" highlight-current-row @current-change="selectRole"><el-table-column prop="role_code" label="编码"/><el-table-column prop="role_name" label="角色"><template #default="s"><span>{{ s.row.role_name }}</span><el-tag v-if="s.row.built_in" size="small" style="margin-left:6px">内置</el-tag></template></el-table-column><el-table-column prop="permission_count" label="权限数" width="90"/><el-table-column label="操作" width="80"><template #default="s"><el-button v-if="auth.hasPermission('system:role:write')" link :disabled="s.row.built_in||rolesError" @click.stop="editRole(s.row)">编辑</el-button></template></el-table-column></el-table></div>
            <div class="permission-panel"><h3>{{ selectedRole ? `授权：${selectedRole.role_name}` : '选择左侧角色进行授权' }}</h3><el-alert v-if="selectedRole?.built_in" title="超级管理员为系统内置角色，默认拥有全部系统权限，不允许在管理端修改。" type="warning" :closable="false"/><el-alert v-if="permissionsError||rolePermissionError||rolesError" title="权限目录、角色或授权记录读取失败，当前授权未知，不能依据空勾选保存。" type="error" :closable="false"/><el-button v-if="rolePermissionError&&selectedRole" @click="selectRole(selectedRole)">重新加载当前角色权限</el-button><el-checkbox-group v-model="checkedPermissionIds" :disabled="!selectedRole||selectedRole.built_in||!auth.hasPermission('system:role:write')||rolePermissionLoading||rolePermissionBusy||rolePermissionError||permissionsError||rolesError"><el-checkbox v-for="p in permissions" :key="p.id" :value="p.id">{{ p.permission_name }} <small>{{ p.permission_code }}</small></el-checkbox></el-checkbox-group><el-button v-if="selectedRole&&!selectedRole.built_in&&auth.hasPermission('system:role:write')" type="primary" :loading="rolePermissionBusy" :disabled="rolePermissionLoading||rolePermissionError||permissionsError||rolesError||rolePermissionBusy" @click="saveRolePermissions">保存授权</el-button></div>
          </div>
        </el-tab-pane>
        <el-tab-pane v-if="auth.hasPermission('system:permission:read')" label="权限目录" name="permissions"><el-alert title="权限编码由工程代码与数据库迁移脚本统一维护，管理端只负责角色授权。" type="info" :closable="false"/><el-table :data="permissions"><el-table-column prop="permission_code" label="权限编码"/><el-table-column prop="permission_name" label="名称"/><el-table-column prop="permission_type" label="类型"/><el-table-column prop="route_path" label="路由"/></el-table></el-tab-pane>
        <el-tab-pane v-if="auth.hasPermission('system:dict:read')" label="数据字典" name="dict"><div class="split"><div><div class="toolbar"><el-button type="primary" @click="newDictType">新增字典</el-button></div><el-table :data="dictTypes" highlight-current-row @current-change="loadDictItems"><el-table-column prop="dict_code" label="编码"/><el-table-column prop="dict_name" label="名称"/><el-table-column label="操作" width="70"><template #default="s"><el-button link @click.stop="editDictType(s.row)">编辑</el-button></template></el-table-column></el-table></div><div><div class="toolbar"><b>{{ selectedDictType?.dict_name || '请选择字典' }}</b><el-button type="primary" :disabled="!selectedDictType" @click="newDictItem">新增字典项</el-button></div><el-table :data="dictItems"><el-table-column prop="item_code" label="编码"/><el-table-column prop="item_name" label="名称"/><el-table-column prop="item_value" label="值"/><el-table-column prop="status" label="状态" width="90"/><el-table-column label="操作" width="70"><template #default="s"><el-button link @click="editDictItem(s.row)">编辑</el-button></template></el-table-column></el-table></div></div></el-tab-pane>
        <el-tab-pane v-if="auth.hasPermission('system:param:read')" label="系统参数" name="params"><div class="toolbar"><el-button type="primary" @click="newParam">新增参数</el-button><el-button @click="loadParameters">刷新</el-button></div><el-table :data="parameters"><el-table-column prop="param_group" label="分组"/><el-table-column prop="param_code" label="编码"/><el-table-column prop="param_name" label="名称"/><el-table-column prop="param_value" label="值"/><el-table-column prop="value_type" label="类型"/><el-table-column prop="status" label="状态" width="90"/><el-table-column label="操作" width="70"><template #default="s"><el-button link @click="editParam(s.row)">编辑</el-button></template></el-table-column></el-table></el-tab-pane>
        <el-tab-pane v-if="auth.hasPermission('system:login-log:read')" label="登录日志" name="login"><el-button @click="loadLoginLogs">刷新</el-button><el-alert v-if="loginLogsError" type="error" :closable="false" title="登录日志读取失败，无法据此认定无登录或拒绝记录。"/><el-button v-if="loginLogsError" @click="loadLoginLogs">重新加载登录日志</el-button><el-table :data="loginLogs" :empty-text="loginLogsError?'日志读取失败':'暂无采集的登录日志"><el-table-column prop="username" label="用户"/><el-table-column prop="login_at" label="时间"/><el-table-column prop="login_ip" label="IP"/><el-table-column prop="result" label="结果"/><el-table-column prop="failure_reason" label="失败原因"/><el-table-column prop="request_id" label="Request ID"/></el-table></el-tab-pane>
        <el-tab-pane v-if="auth.hasPermission('system:operation-log:read')" label="操作审计" name="audit"><el-button @click="loadOperationLogs">刷新</el-button><el-alert v-if="operationLogsError" type="error" :closable="false" title="操作审计读取失败，不能据此推断没有变更、拒绝或失败事件。"/><el-button v-if="operationLogsError" @click="loadOperationLogs">重新加载操作审计</el-button><el-table :data="operationLogs" :empty-text="operationLogsError?'审计读取失败':'暂无已记录的操作审计"><el-table-column prop="operator_name" label="操作人"/><el-table-column prop="module_code" label="模块"/><el-table-column prop="object_type" label="对象"/><el-table-column prop="operation" label="动作"/><el-table-column prop="reason" label="说明"/><el-table-column prop="occurred_at" label="时间"/><el-table-column prop="request_id" label="Request ID"/></el-table></el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="userDialog" :title="editId?'编辑用户':'新增用户'" width="560px"><el-form label-width="90px"><el-form-item label="用户名"><el-input v-model="userForm.username" :disabled="!!editId"/></el-form-item><el-form-item v-if="!editId" label="初始密码"><el-input v-model="userForm.password" type="password" show-password/></el-form-item><el-form-item label="姓名"><el-input v-model="userForm.displayName"/></el-form-item><el-form-item label="部门"><el-input v-model="userForm.department"/></el-form-item><el-form-item label="手机"><el-input v-model="userForm.mobile"/></el-form-item><el-form-item label="邮箱"><el-input v-model="userForm.email"/></el-form-item><el-form-item label="角色"><el-select v-model="userForm.roleIds" multiple style="width:100%"><el-option v-for="r in roles" :key="r.id" :label="r.role_name" :value="r.id"/></el-select></el-form-item><el-form-item label="状态"><el-select v-model="userForm.status"><el-option label="启用" value="ACTIVE"/><el-option label="冻结" value="FROZEN"/><el-option label="停用" value="DISABLED"/></el-select></el-form-item></el-form><template #footer><el-button :disabled="userBusy" @click="userDialog=false">取消</el-button><el-button type="primary" :loading="userBusy" :disabled="userBusy||!auth.hasPermission('system:user:write')" @click="saveUser">保存</el-button></template></el-dialog>
    <el-dialog v-model="roleDialog" :title="editId?'编辑角色':'新增角色'" width="520px"><el-form label-width="90px"><el-form-item label="角色编码"><el-input v-model="roleForm.roleCode" :disabled="!!editId"/></el-form-item><el-form-item label="角色名称"><el-input v-model="roleForm.roleName"/></el-form-item><el-form-item label="数据范围"><el-select v-model="roleForm.dataScope"><el-option label="全部" value="ALL"/><el-option label="本部门" value="DEPARTMENT"/><el-option label="本人" value="SELF"/></el-select></el-form-item><el-form-item label="说明"><el-input v-model="roleForm.description"/></el-form-item></el-form><template #footer><el-button @click="roleDialog=false">取消</el-button><el-button type="primary" @click="saveRole">保存</el-button></template></el-dialog>
    <el-dialog v-model="dictTypeDialog" :title="editId?'编辑字典':'新增字典'" width="520px"><el-form label-width="90px"><el-form-item label="编码"><el-input v-model="dictTypeForm.dictCode" :disabled="!!editId"/></el-form-item><el-form-item label="名称"><el-input v-model="dictTypeForm.dictName"/></el-form-item><el-form-item label="说明"><el-input v-model="dictTypeForm.description"/></el-form-item><el-form-item label="可编辑"><el-switch v-model="dictTypeForm.editable"/></el-form-item></el-form><template #footer><el-button @click="dictTypeDialog=false">取消</el-button><el-button type="primary" @click="saveDictType">保存</el-button></template></el-dialog>
    <el-dialog v-model="dictItemDialog" :title="editId?'编辑字典项':'新增字典项'" width="520px"><el-form label-width="90px"><el-form-item label="编码"><el-input v-model="dictItemForm.itemCode" :disabled="!!editId"/></el-form-item><el-form-item label="名称"><el-input v-model="dictItemForm.itemName"/></el-form-item><el-form-item label="值"><el-input v-model="dictItemForm.itemValue"/></el-form-item><el-form-item label="排序"><el-input-number v-model="dictItemForm.sortOrder"/></el-form-item><el-form-item label="默认"><el-switch v-model="dictItemForm.isDefault"/></el-form-item></el-form><template #footer><el-button @click="dictItemDialog=false">取消</el-button><el-button type="primary" @click="saveDictItem">保存</el-button></template></el-dialog>
    <el-dialog v-model="paramDialog" :title="editId?'编辑参数':'新增参数'" width="560px"><el-form label-width="90px"><el-form-item label="分组"><el-input v-model="paramForm.paramGroup"/></el-form-item><el-form-item label="编码"><el-input v-model="paramForm.paramCode" :disabled="!!editId"/></el-form-item><el-form-item label="名称"><el-input v-model="paramForm.paramName"/></el-form-item><el-form-item label="值"><el-input v-model="paramForm.paramValue" :type="paramForm.sensitive?'password':'text'" show-password/></el-form-item><el-form-item label="类型"><el-select v-model="paramForm.valueType"><el-option v-for="v in ['STRING','NUMBER','BOOLEAN','JSON','SECRET']" :key="v" :label="v" :value="v"/></el-select></el-form-item><el-form-item label="敏感"><el-switch v-model="paramForm.sensitive"/></el-form-item><el-form-item label="说明"><el-input v-model="paramForm.description"/></el-form-item></el-form><template #footer><el-button @click="paramDialog=false">取消</el-button><el-button type="primary" @click="saveParam">保存</el-button></template></el-dialog>
  </div>
</template>

<style scoped>
.system-page{display:flex;flex-direction:column;gap:16px}.page-head,.toolbar{display:flex;align-items:center;justify-content:space-between;gap:12px}.page-head h2{margin:0 0 6px}.page-head p{margin:0;color:#667085}.toolbar{justify-content:flex-start;margin:0 0 12px}.split{display:grid;grid-template-columns:1fr 1.15fr;gap:18px}.permission-panel{border-left:1px solid #ebeef5;padding-left:18px}.permission-panel .el-checkbox{display:block;margin:0 0 10px}.permission-panel small{color:#98a2b3;margin-left:8px}@media(max-width:1100px){.split{grid-template-columns:1fr}}
</style>
