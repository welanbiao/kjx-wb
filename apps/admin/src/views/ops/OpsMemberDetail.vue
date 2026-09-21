<template>
  <div class="kjx-page">
    <PageHeader title="会员详情">
      <template #actions>
        <el-button @click="$router.push(`/ops/members/${id}/edit`)">
          编辑
        </el-button>
        <el-button @click="openRecalc = true">
          重算
        </el-button>
        <el-button
          type="primary"
          @click="$router.push(`/ops/entitlements?memberId=${id}`)"
        >
          调整权益
        </el-button>
      </template>
    </PageHeader>
    <PageState
      :loading="loading"
      :error="error"
      :empty="!member"
      :request-id="requestId"
    >
      <el-descriptions
        v-if="member"
        :column="1"
        border
      >
        <el-descriptions-item label="ID">
          {{ member.id }}
        </el-descriptions-item>
        <el-descriptions-item label="部门">
          {{ member.deptId }} / {{ member.hostCode }}
        </el-descriptions-item>
        <el-descriptions-item label="编码">
          {{ member.memberCode }}
        </el-descriptions-item>
        <el-descriptions-item label="抖音号">
          {{ member.douyinId }}
        </el-descriptions-item>
        <el-descriptions-item label="月 / 天 / 到期">
          {{ member.subscribedMonths }} / {{ member.subscribedDays }} / {{ member.expireAt }}
        </el-descriptions-item>
        <el-descriptions-item label="额度 / 已解锁 / 剩余">
          {{ member.availableCount }} / {{ member.unlockedCount }} / {{ member.remainCount }}
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          {{ member.status }} · bindState={{ member.bindState }}
        </el-descriptions-item>
      </el-descriptions>
    </PageState>
    <ReasonDialog
      v-model="openRecalc"
      title="手动重算"
      warning="将请求后端重算派生字段，不会在浏览器里用月数换算天数。"
      @confirm="onRecalc"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { ElMessage } from "element-plus";
import { opsApi } from "@/api/kjx";
import PageHeader from "@/components/PageHeader.vue";
import PageState from "@/components/PageState.vue";
import ReasonDialog from "@/components/ReasonDialog.vue";
import { formatRequestFailure } from "@/lib/errors";
import type { AdminMember } from "@/lib/types";

const route = useRoute();
const id = computed(() => Number(route.params.id));
const loading = ref(false);
const error = ref("");
const requestId = ref("");
const member = ref<AdminMember | null>(null);
const openRecalc = ref(false);

async function load() {
  loading.value = true;
  try {
    const payload = await opsApi.getMember(id.value);
    member.value = payload.data;
    requestId.value = payload.requestId;
  } catch (cause) {
    const failure = formatRequestFailure(cause);
    error.value = failure.title;
    requestId.value = failure.requestId;
  } finally {
    loading.value = false;
  }
}

async function onRecalc(payload: { reason: string }) {
  try {
    const result = await opsApi.recalcMember(id.value, { reason: payload.reason });
    member.value = result.data;
    requestId.value = result.requestId;
    openRecalc.value = false;
    ElMessage.success("已重算");
  } catch (cause) {
    ElMessage.error(formatRequestFailure(cause).title);
  }
}

onMounted(load);
</script>
