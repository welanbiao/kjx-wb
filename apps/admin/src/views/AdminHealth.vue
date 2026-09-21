<template>
  <div>
    <h1>健康检查</h1>
    <el-descriptions
      :column="1"
      border
    >
      <el-descriptions-item label="应用">
        {{ title }}
      </el-descriptions-item>
      <el-descriptions-item label="版本">
        {{ version }}
      </el-descriptions-item>
      <el-descriptions-item label="API Base">
        {{ apiBase || "(未配置 VITE_API_BASE_URL)" }}
      </el-descriptions-item>
      <el-descriptions-item label="Public base">
        {{ publicBase }}
      </el-descriptions-item>
    </el-descriptions>
    <el-button
      style="margin-top: 16px"
      type="primary"
      :loading="loading"
      @click="onPing"
    >
      探测 /actuator/health
    </el-button>
    <el-alert
      v-if="error"
      style="margin-top: 16px"
      :title="error"
      type="error"
      show-icon
      :closable="false"
    />
    <div
      v-if="body"
      class="output"
    >
      {{ healthOutput }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { getApiBaseUrl, pingBackendHealth } from "@/lib/api";

const title = import.meta.env.VITE_APP_TITLE ?? "叩君芯管理端";
const version = import.meta.env.VITE_APP_VERSION ?? "0.0.0";
const publicBase = import.meta.env.BASE_URL;
const apiBase = getApiBaseUrl();
const loading = ref(false);
const status = ref<number | null>(null);
const body = ref("");
const error = ref("");
const healthOutput = computed(() => `HTTP ${status.value}\n${body.value}`);

async function onPing() {
  loading.value = true;
  error.value = "";
  body.value = "";
  try {
    const result = await pingBackendHealth();
    status.value = result.status;
    body.value = result.body;
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : "request failed";
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.output {
  margin-top: 16px;
  padding: 12px;
  background: #1f2d3d;
  color: #e5eaf3;
  border-radius: 8px;
  overflow: auto;
  white-space: pre-wrap;
}
</style>
