<template>
  <div class="kjx-page">
    <PageHeader
      title="我的主播资料"
      lead="来自 GET /admin-api/kjx/self/host，仅本部门。"
    />
    <PageState
      :loading="loading"
      :error="error"
      :empty="!host"
      :request-id="requestId"
      empty-text="没有主播资料"
    >
      <el-descriptions
        v-if="host"
        :column="1"
        border
      >
        <el-descriptions-item label="部门 ID">
          {{ host.deptId }}
        </el-descriptions-item>
        <el-descriptions-item label="主播短码">
          {{ host.hostCode }}
        </el-descriptions-item>
        <el-descriptions-item label="名称">
          {{ host.hostName }}
        </el-descriptions-item>
        <el-descriptions-item label="抖音号">
          {{ host.dyAccount || "—" }}
        </el-descriptions-item>
        <el-descriptions-item label="联系方式">
          {{ host.contact || "—" }}
        </el-descriptions-item>
      </el-descriptions>
    </PageState>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { selfApi } from "@/api/kjx";
import PageHeader from "@/components/PageHeader.vue";
import PageState from "@/components/PageState.vue";
import { formatRequestFailure } from "@/lib/errors";
import type { HostSelfInfo } from "@/lib/types";

const loading = ref(false);
const error = ref("");
const requestId = ref("");
const host = ref<HostSelfInfo | null>(null);

async function load() {
  loading.value = true;
  error.value = "";
  try {
    const payload = await selfApi.host();
    host.value = payload.data;
    requestId.value = payload.requestId;
  } catch (cause) {
    const failure = formatRequestFailure(cause);
    error.value = failure.title;
    requestId.value = failure.requestId;
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>
