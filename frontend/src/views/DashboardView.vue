<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { http } from '@/api/http'
import type { ApiResponse } from '@/types/api'
interface Overview { m1Status: string; customerCount: number; userCount: number; database: string; cache: string }
const data = ref<Overview | null>(null); const loading = ref(true); const error = ref('')
onMounted(async () => { try { const response = await http.get<ApiResponse<Overview>>('/dashboard/overview'); data.value = response.data.data } catch (e: any) { error.value = e.response?.data?.message || '无法连接后端' } finally { loading.value = false } })
</script>
<template>
  <div>
    <div class="page-head"><div><h1>首页工作台</h1><p>M1 首次真实前后端联调页面</p></div><el-tag type="success">{{ data?.m1Status || 'CHECKING' }}</el-tag></div>
    <el-alert v-if="error" type="error" :title="error" show-icon class="mb16" />
    <el-skeleton :loading="loading" animated><div class="metric-grid">
      <el-card><span>客户表记录</span><strong>{{ data?.customerCount ?? '-' }}</strong><small>来自 PostgreSQL customer 表</small></el-card>
      <el-card><span>后台用户</span><strong>{{ data?.userCount ?? '-' }}</strong><small>M1 bootstrap 管理员</small></el-card>
      <el-card><span>数据库</span><strong class="text">{{ data?.database ?? '-' }}</strong><small>Flyway V1-V5</small></el-card>
      <el-card><span>缓存</span><strong class="text">{{ data?.cache ?? '-' }}</strong><small>Redis 已接入健康检查</small></el-card>
    </div></el-skeleton>
    <el-card class="mt16"><template #header><b>M1 完成边界</b></template><el-steps :active="4" finish-status="success" align-center><el-step title="工程结构" description="frontend / backend / prototype"/><el-step title="基础设施" description="PostgreSQL + Redis + Docker"/><el-step title="后端基础" description="Flyway + MyBatis + JWT + Swagger"/><el-step title="前端联调" description="登录 + Layout + Dashboard"/></el-steps></el-card>
  </div>
</template>
