<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const loading = ref(false)
const form = reactive({ username: 'admin', password: 'admin123' })

async function submit() {
  loading.value = true
  try {
    await auth.login(form.username, form.password)
    ElMessage.success('登录成功')
    await router.replace(String(route.query.redirect || '/dashboard'))
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '登录失败，请检查后端服务')
  } finally { loading.value = false }
}
</script>

<template>
  <div class="login-page">
    <div class="login-brand">
      <div class="brand-logo">IP</div><h1>IP代理管理平台</h1>
      <p>企业网络资源与代理服务管理控制台</p>
      <div class="arch-note">M1 工程骨架 · Vue 3 + Spring Boot + PostgreSQL + Redis</div>
    </div>
    <el-card class="login-card" shadow="always">
      <template #header><div class="login-title">登录控制台</div></template>
      <el-form label-position="top" @keyup.enter="submit">
        <el-form-item label="用户名"><el-input v-model="form.username" size="large" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="form.password" type="password" show-password size="large" /></el-form-item>
        <el-button type="primary" size="large" :loading="loading" style="width:100%" @click="submit">登录</el-button>
      </el-form>
      <div class="login-tip">开发环境默认账号：admin / admin123</div>
    </el-card>
  </div>
</template>
