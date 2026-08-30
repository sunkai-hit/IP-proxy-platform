<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { systemApi } from '@/api/system'

const tab=ref('users'); const users=ref<any[]>([]); const roles=ref<any[]>([]); const permissions=ref<any[]>([]); const dictTypes=ref<any[]>([]); const dictItems=ref<any[]>([]); const parameters=ref<any[]>([]); const loginLogs=ref<any[]>([]); const operationLogs=ref<any[]>([])
const selectedDictType=ref<any>(null); const selectedRole=ref<any>(null); const checkedPermissionIds=ref<number[]>([])
const userDialog=ref(false); const roleDialog=ref(false); const dictTypeDialog=ref(false); const dictItemDialog=ref(false); const paramDialog=ref(false)
const editId=ref<number|null>(null)
const userForm=reactive<any>({username:'',password:'',displayName:'',department:'',mobile:'',email:'',status:'ACTIVE',roleIds:[]})
const roleForm=reactive<any>({roleCode:'',roleName:'',dataScope:'ALL',description:'',status:'ACTIVE'})
const dictTypeForm=reactive<any>({dictCode:'',dictName:'',description:'',editable:true,status:'ACTIVE'})
const dictItemForm=reactive<any>({itemCode:'',itemName:'',itemValue:'',sortOrder:0,colorTag:'',isDefault:false,status:'ACTIVE',remark:''})
const paramForm=reactive<any>({paramGroup:'SYSTEM',paramCode:'',paramName:'',paramValue:'',valueType:'STRING',sensitive:false,description:'',status:'ACTIVE'})

const data=(r:any)=>r.data.data
async function loadUsers(){users.value=data(await systemApi.users())}
async function loadRoles(){roles.value=data(await systemApi.roles())}
async function loadPermissions(){permissions.value=data(await systemApi.permissions())}
async function loadDictTypes(){dictTypes.value=data(await systemApi.dictTypes()); if(selectedDictType.value){selectedDictType.value=dictTypes.value.find(x=>x.id===selectedDictType.value.id)||null}}
async function loadDictItems(row?:any){if(row)selectedDictType.value=row;if(!selectedDictType.value){dictItems.value=[];return}dictItems.value=data(await systemApi.dictItems(selectedDictType.value.id))}
async function loadParameters(){parameters.value=data(await systemApi.parameters())}
async function loadLoginLogs(){loginLogs.value=data(await systemApi.loginLogs())}
async function loadOperationLogs(){operationLogs.value=data(await systemApi.operationLogs())}
async function loadAll(){await Promise.all([loadUsers(),loadRoles(),loadPermissions(),loadDictTypes(),loadParameters(),loadLoginLogs(),loadOperationLogs()])}

function copyForm(target:any, source:any){Object.keys(target).forEach(k=>target[k]=source?.[k] ?? (typeof target[k]==='boolean'?false:Array.isArray(target[k])?[]:''))}
function newUser(){editId.value=null;copyForm(userForm,{status:'ACTIVE',roleIds:[]});userDialog.value=true}
function editUser(row:any){editId.value=row.id;copyForm(userForm,{...row,displayName:row.display_name,roleIds:[]});userDialog.value=true}
async function saveUser(){if(!editId.value&&!userForm.password){ElMessage.warning('新用户必须设置初始密码');return}editId.value?await systemApi.updateUser(editId.value,userForm):await systemApi.createUser(userForm);userDialog.value=false;ElMessage.success('用户已保存');await loadUsers()}
async function resetPassword(row:any){const {value}=await ElMessageBox.prompt(`为 ${row.username} 设置新密码`,'重置密码',{inputType:'password',inputPattern:/.{6,}/,inputErrorMessage:'密码至少6位'});await systemApi.resetPassword(row.id,value);ElMessage.success('密码已重置')}

function newRole(){editId.value=null;copyForm(roleForm,{dataScope:'ALL',status:'ACTIVE'});roleDialog.value=true}
function editRole(row:any){editId.value=row.id;copyForm(roleForm,{roleCode:row.role_code,roleName:row.role_name,dataScope:row.data_scope,description:row.description,status:row.status});roleDialog.value=true}
async function saveRole(){editId.value?await systemApi.updateRole(editId.value,roleForm):await systemApi.createRole(roleForm);roleDialog.value=false;ElMessage.success('角色已保存');await loadRoles()}
async function selectRole(row:any){selectedRole.value=row;const rows=data(await systemApi.rolePermissions(row.id));checkedPermissionIds.value=rows.map((x:any)=>x.permission_id)}
async function saveRolePermissions(){if(!selectedRole.value)return;await systemApi.saveRolePermissions(selectedRole.value.id,checkedPermissionIds.value);ElMessage.success('角色权限已保存');await loadRoles()}

function newDictType(){editId.value=null;copyForm(dictTypeForm,{editable:true,status:'ACTIVE'});dictTypeDialog.value=true}
function editDictType(row:any){editId.value=row.id;copyForm(dictTypeForm,{dictCode:row.dict_code,dictName:row.dict_name,description:row.description,editable:row.editable,status:row.status});dictTypeDialog.value=true}
async function saveDictType(){editId.value?await systemApi.updateDictType(editId.value,dictTypeForm):await systemApi.createDictType(dictTypeForm);dictTypeDialog.value=false;ElMessage.success('字典类型已保存');await loadDictTypes()}
function newDictItem(){if(!selectedDictType.value){ElMessage.warning('请先选择字典类型');return}editId.value=null;copyForm(dictItemForm,{sortOrder:0,isDefault:false,status:'ACTIVE'});dictItemDialog.value=true}
function editDictItem(row:any){editId.value=row.id;copyForm(dictItemForm,{itemCode:row.item_code,itemName:row.item_name,itemValue:row.item_value,sortOrder:row.sort_order,colorTag:row.color_tag,isDefault:row.is_default,status:row.status,remark:row.remark});dictItemDialog.value=true}
async function saveDictItem(){editId.value?await systemApi.updateDictItem(editId.value,dictItemForm):await systemApi.createDictItem(selectedDictType.value.id,dictItemForm);dictItemDialog.value=false;ElMessage.success('字典项已保存');await loadDictItems()}

function newParam(){editId.value=null;copyForm(paramForm,{paramGroup:'SYSTEM',valueType:'STRING',sensitive:false,status:'ACTIVE'});paramDialog.value=true}
function editParam(row:any){editId.value=row.id;copyForm(paramForm,{paramGroup:row.param_group,paramCode:row.param_code,paramName:row.param_name,paramValue:row.param_value,valueType:row.value_type,sensitive:row.sensitive,description:row.description,status:row.status});paramDialog.value=true}
async function saveParam(){editId.value?await systemApi.updateParameter(editId.value,paramForm):await systemApi.createParameter(paramForm);paramDialog.value=false;ElMessage.success('系统参数已保存');await loadParameters()}

onMounted(loadAll)
</script>

<template>
  <div class="system-page">
    <div class="page-head"><div><h2>系统基础能力</h2><p>用户、角色权限、字典参数与审计日志均已接入真实后端。</p></div><el-tag type="success">M2</el-tag></div>
    <el-card shadow="never">
      <el-tabs v-model="tab">
        <el-tab-pane label="用户管理" name="users">
          <div class="toolbar"><el-button type="primary" @click="newUser">新增用户</el-button><el-button @click="loadUsers">刷新</el-button></div>
          <el-table :data="users" stripe><el-table-column prop="username" label="用户名"/><el-table-column prop="display_name" label="姓名"/><el-table-column prop="department" label="部门"/><el-table-column prop="role_names" label="角色"/><el-table-column prop="status" label="状态" width="100"/><el-table-column prop="last_login_at" label="最后登录" width="190"/><el-table-column label="操作" width="190"><template #default="s"><el-button link type="primary" @click="editUser(s.row)">编辑</el-button><el-button link @click="resetPassword(s.row)">重置密码</el-button></template></el-table-column></el-table>
        </el-tab-pane>
        <el-tab-pane label="角色与权限" name="roles">
          <div class="split"><div><div class="toolbar"><el-button type="primary" @click="newRole">新增角色</el-button></div><el-table :data="roles" highlight-current-row @current-change="selectRole"><el-table-column prop="role_code" label="编码"/><el-table-column prop="role_name" label="角色"/><el-table-column prop="permission_count" label="权限数" width="90"/><el-table-column label="操作" width="80"><template #default="s"><el-button link @click.stop="editRole(s.row)">编辑</el-button></template></el-table-column></el-table></div><div class="permission-panel"><h3>{{ selectedRole ? `授权：${selectedRole.role_name}` : '选择左侧角色进行授权' }}</h3><el-checkbox-group v-model="checkedPermissionIds"><el-checkbox v-for="p in permissions" :key="p.id" :value="p.id">{{ p.permission_name }} <small>{{ p.permission_code }}</small></el-checkbox></el-checkbox-group><el-button v-if="selectedRole" type="primary" @click="saveRolePermissions">保存授权</el-button></div></div>
        </el-tab-pane>
        <el-tab-pane label="权限目录" name="permissions"><el-alert title="权限编码由工程代码与数据库迁移脚本统一维护，管理端只负责角色授权。" type="info" :closable="false"/><el-table :data="permissions"><el-table-column prop="permission_code" label="权限编码"/><el-table-column prop="permission_name" label="名称"/><el-table-column prop="permission_type" label="类型"/><el-table-column prop="route_path" label="路由"/></el-table></el-tab-pane>
        <el-tab-pane label="数据字典" name="dict"><div class="split"><div><div class="toolbar"><el-button type="primary" @click="newDictType">新增字典</el-button></div><el-table :data="dictTypes" highlight-current-row @current-change="loadDictItems"><el-table-column prop="dict_code" label="编码"/><el-table-column prop="dict_name" label="名称"/><el-table-column label="操作" width="70"><template #default="s"><el-button link @click.stop="editDictType(s.row)">编辑</el-button></template></el-table-column></el-table></div><div><div class="toolbar"><b>{{ selectedDictType?.dict_name || '请选择字典' }}</b><el-button type="primary" :disabled="!selectedDictType" @click="newDictItem">新增字典项</el-button></div><el-table :data="dictItems"><el-table-column prop="item_code" label="编码"/><el-table-column prop="item_name" label="名称"/><el-table-column prop="item_value" label="值"/><el-table-column prop="status" label="状态" width="90"/><el-table-column label="操作" width="70"><template #default="s"><el-button link @click="editDictItem(s.row)">编辑</el-button></template></el-table-column></el-table></div></div></el-tab-pane>
        <el-tab-pane label="系统参数" name="params"><div class="toolbar"><el-button type="primary" @click="newParam">新增参数</el-button><el-button @click="loadParameters">刷新</el-button></div><el-table :data="parameters"><el-table-column prop="param_group" label="分组"/><el-table-column prop="param_code" label="编码"/><el-table-column prop="param_name" label="名称"/><el-table-column prop="param_value" label="值"/><el-table-column prop="value_type" label="类型"/><el-table-column prop="status" label="状态" width="90"/><el-table-column label="操作" width="70"><template #default="s"><el-button link @click="editParam(s.row)">编辑</el-button></template></el-table-column></el-table></el-tab-pane>
        <el-tab-pane label="登录日志" name="login"><el-button @click="loadLoginLogs">刷新</el-button><el-table :data="loginLogs"><el-table-column prop="username" label="用户"/><el-table-column prop="login_at" label="时间"/><el-table-column prop="login_ip" label="IP"/><el-table-column prop="result" label="结果"/><el-table-column prop="failure_reason" label="失败原因"/><el-table-column prop="request_id" label="Request ID"/></el-table></el-tab-pane>
        <el-tab-pane label="操作审计" name="audit"><el-button @click="loadOperationLogs">刷新</el-button><el-table :data="operationLogs"><el-table-column prop="operator_name" label="操作人"/><el-table-column prop="module_code" label="模块"/><el-table-column prop="object_type" label="对象"/><el-table-column prop="operation" label="动作"/><el-table-column prop="reason" label="说明"/><el-table-column prop="occurred_at" label="时间"/><el-table-column prop="request_id" label="Request ID"/></el-table></el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="userDialog" :title="editId?'编辑用户':'新增用户'" width="560px"><el-form label-width="90px"><el-form-item label="用户名"><el-input v-model="userForm.username" :disabled="!!editId"/></el-form-item><el-form-item v-if="!editId" label="初始密码"><el-input v-model="userForm.password" type="password" show-password/></el-form-item><el-form-item label="姓名"><el-input v-model="userForm.displayName"/></el-form-item><el-form-item label="部门"><el-input v-model="userForm.department"/></el-form-item><el-form-item label="手机"><el-input v-model="userForm.mobile"/></el-form-item><el-form-item label="邮箱"><el-input v-model="userForm.email"/></el-form-item><el-form-item label="角色"><el-select v-model="userForm.roleIds" multiple style="width:100%"><el-option v-for="r in roles" :key="r.id" :label="r.role_name" :value="r.id"/></el-select></el-form-item><el-form-item label="状态"><el-select v-model="userForm.status"><el-option label="启用" value="ACTIVE"/><el-option label="冻结" value="FROZEN"/><el-option label="停用" value="DISABLED"/></el-select></el-form-item></el-form><template #footer><el-button @click="userDialog=false">取消</el-button><el-button type="primary" @click="saveUser">保存</el-button></template></el-dialog>
    <el-dialog v-model="roleDialog" :title="editId?'编辑角色':'新增角色'" width="520px"><el-form label-width="90px"><el-form-item label="角色编码"><el-input v-model="roleForm.roleCode" :disabled="!!editId"/></el-form-item><el-form-item label="角色名称"><el-input v-model="roleForm.roleName"/></el-form-item><el-form-item label="数据范围"><el-select v-model="roleForm.dataScope"><el-option label="全部" value="ALL"/><el-option label="本部门" value="DEPARTMENT"/><el-option label="本人" value="SELF"/></el-select></el-form-item><el-form-item label="说明"><el-input v-model="roleForm.description"/></el-form-item></el-form><template #footer><el-button @click="roleDialog=false">取消</el-button><el-button type="primary" @click="saveRole">保存</el-button></template></el-dialog>
    <el-dialog v-model="dictTypeDialog" :title="editId?'编辑字典':'新增字典'" width="520px"><el-form label-width="90px"><el-form-item label="编码"><el-input v-model="dictTypeForm.dictCode" :disabled="!!editId"/></el-form-item><el-form-item label="名称"><el-input v-model="dictTypeForm.dictName"/></el-form-item><el-form-item label="说明"><el-input v-model="dictTypeForm.description"/></el-form-item><el-form-item label="可编辑"><el-switch v-model="dictTypeForm.editable"/></el-form-item></el-form><template #footer><el-button @click="dictTypeDialog=false">取消</el-button><el-button type="primary" @click="saveDictType">保存</el-button></template></el-dialog>
    <el-dialog v-model="dictItemDialog" :title="editId?'编辑字典项':'新增字典项'" width="520px"><el-form label-width="90px"><el-form-item label="编码"><el-input v-model="dictItemForm.itemCode" :disabled="!!editId"/></el-form-item><el-form-item label="名称"><el-input v-model="dictItemForm.itemName"/></el-form-item><el-form-item label="值"><el-input v-model="dictItemForm.itemValue"/></el-form-item><el-form-item label="排序"><el-input-number v-model="dictItemForm.sortOrder"/></el-form-item><el-form-item label="默认"><el-switch v-model="dictItemForm.isDefault"/></el-form-item></el-form><template #footer><el-button @click="dictItemDialog=false">取消</el-button><el-button type="primary" @click="saveDictItem">保存</el-button></template></el-dialog>
    <el-dialog v-model="paramDialog" :title="editId?'编辑参数':'新增参数'" width="560px"><el-form label-width="90px"><el-form-item label="分组"><el-input v-model="paramForm.paramGroup"/></el-form-item><el-form-item label="编码"><el-input v-model="paramForm.paramCode" :disabled="!!editId"/></el-form-item><el-form-item label="名称"><el-input v-model="paramForm.paramName"/></el-form-item><el-form-item label="值"><el-input v-model="paramForm.paramValue" :type="paramForm.sensitive?'password':'text'" show-password/></el-form-item><el-form-item label="类型"><el-select v-model="paramForm.valueType"><el-option v-for="v in ['STRING','NUMBER','BOOLEAN','JSON','SECRET']" :key="v" :label="v" :value="v"/></el-select></el-form-item><el-form-item label="敏感"><el-switch v-model="paramForm.sensitive"/></el-form-item><el-form-item label="说明"><el-input v-model="paramForm.description"/></el-form-item></el-form><template #footer><el-button @click="paramDialog=false">取消</el-button><el-button type="primary" @click="saveParam">保存</el-button></template></el-dialog>
  </div>
</template>

<style scoped>
.system-page{display:flex;flex-direction:column;gap:16px}.page-head,.toolbar{display:flex;align-items:center;justify-content:space-between;gap:12px}.page-head h2{margin:0 0 6px}.page-head p{margin:0;color:#667085}.toolbar{justify-content:flex-start;margin:0 0 12px}.split{display:grid;grid-template-columns:1fr 1.15fr;gap:18px}.permission-panel{border-left:1px solid #ebeef5;padding-left:18px}.permission-panel .el-checkbox{display:block;margin:0 0 10px}.permission-panel small{color:#98a2b3;margin-left:8px}@media(max-width:1100px){.split{grid-template-columns:1fr}}
</style>
