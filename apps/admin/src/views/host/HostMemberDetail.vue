<template>
  <div class="kjx-page">
    <PageHeader title="会员详情">
      <template #actions>
        <el-button @click="onCode">
          查看编码
        </el-button>
        <el-button
          type="primary"
          @click="$router.push(`/self/members/${id}/edit`)"
        >
          编辑/续期
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
        <el-descriptions-item label="会员编码">
          {{ code || "点击查看编码" }}
        </el-descriptions-item>
        <el-descriptions-item label="抖音号">
          {{ member.douyinId }}
        </el-descriptions-item>
        <el-descriptions-item label="月数">
          {{ member.subscribedMonths }}
        </el-descriptions-item>
        <el-descriptions-item label="天数">
          {{ member.subscribedDays }}
        </el-descriptions-item>
        <el-descriptions-item label="到期">
          {{ member.expireAt }}
        </el-descriptions-item>
        <el-descriptions-item label="额度 / 已解锁 / 剩余">
          {{ member.availableCount }} / {{ member.unlockedCount }} / {{ member.remainCount }}
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          {{ member.status }}
        </el-descriptions-item>
      </el-descriptions>
    </PageState>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { ElMessage } from "element-plus";
import { selfApi } from "@/api/kjx";
import PageHeader from "@/components/PageHeader.vue";
import PageState from "@/components/PageState.vue";
import { formatRequestFailure } from "@/lib/errors";
import type { AdminMember } from "@/lib/types";

const route = useRoute();
const id = computed(() => Number(route.params.id));
const loading = ref(false);
const error = ref("");
const requestId = ref("");
const member = ref<AdminMember | null>(null);
const code = ref("");

async function load() {
  loading.value = true;
  try {
    const payload = await selfApi.pageMembers({ pageNo: 1, pageSize: 100 });
    member.value = payload.data.list.find((item) => item.id === id.value) ?? null;
    requestId.value = payload.requestId;
    if (!member.value) {
      error.value = "找不到本部门会员";
    }
  } catch (cause) {
    const failure = formatRequestFailure(cause);
    error.value = failure.title;
    requestId.value = failure.requestId;
  } finally {
    loading.value = false;
  }
}

async function onCode() {
  try {
    const payload = await selfApi.memberCode(id.value);
    code.value = payload.data.memberCode;
    requestId.value = payload.requestId;
    ElMessage.success("已读取会员编码");
  } catch (cause) {
    const failure = formatRequestFailure(cause);
    ElMessage.error(failure.title);
    requestId.value = failure.requestId;
  }
}

onMounted(load);
</script>
