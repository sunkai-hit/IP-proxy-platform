<script setup lang="ts">import { useRoute } from 'vue-router'; const route = useRoute()</script>
<template><div><div class="page-head"><div><h1>{{ route.meta.title }}</h1><p>正式 Vue 路由已建立，业务页面将在后续里程碑按 OpenAPI 接入。</p></div><el-tag>M1</el-tag></div><el-card><el-empty description="M1 仅完成工程骨架；当前业务原型请查看 /prototype 目录。" /></el-card></div></template>
