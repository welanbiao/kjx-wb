<template>
  <div class="kjx-page">
    <PageHeader title="没有权限" />
    <el-alert
      title="前端已拒绝该地址。这不是授权，真实接口仍由后端校验。"
      type="warning"
      show-icon
      :closable="false"
    />
    <el-button
      style="margin-top: 16px"
      type="primary"
      @click="$router.push('/dashboard')"
    >
      返回工作台
    </el-button>
  </div>
</template>

<script setup lang="ts">
import PageHeader from "@/components/PageHeader.vue";
</script>
