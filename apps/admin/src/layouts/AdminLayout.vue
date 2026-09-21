<template>
  <el-container class="shell">
    <el-aside width="220px">
      <div class="brand">
        叩君芯管理端
      </div>
      <el-menu
        router
        :default-active="active"
      >
        <el-menu-item index="/dashboard">
          工作台
        </el-menu-item>
        <el-menu-item index="/health">
          健康检查
        </el-menu-item>
        <el-menu-item index="/login">
          登录占位
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        WP-00C 骨架 · 非 RuoYi 源码
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useRoute } from "vue-router";

const route = useRoute();
const active = computed(() => route.path);
</script>

<style scoped>
.shell {
  min-height: 100vh;
}
.brand {
  padding: 20px 16px;
  font-weight: 600;
  color: #fff;
  background: #1f2d3d;
}
.header {
  display: flex;
  align-items: center;
  border-bottom: 1px solid #ebeef5;
  color: #606266;
}
:deep(.el-aside) {
  background: #1f2d3d;
}
:deep(.el-menu) {
  border-right: 0;
  background: #1f2d3d;
}
:deep(.el-menu-item) {
  color: #c0c4cc;
}
</style>
