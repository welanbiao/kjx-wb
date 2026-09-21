<template>
  <div class="kjx-page">
    <PageHeader
      title="作品管理"
      lead="状态变更走 PUT /admin-api/kjx/work/{id} 的 status + reason，对齐 WP-12 状态机。"
    >
      <template #actions>
        <el-button
          type="primary"
          @click="$router.push('/ops/works/new')"
        >
          新建作品
        </el-button>
      </template>
    </PageHeader>
    <div class="kjx-toolbar">
      <el-input
        v-model="keyword"
        placeholder="名称或 workCode"
        clearable
      />
      <el-select
        v-model="status"
        clearable
        placeholder="状态"
      >
        <el-option
          v-for="item in statuses"
          :key="item"
          :label="item"
          :value="item"
        />
      </el-select>
      <el-button
        type="primary"
        @click="load"
      >
        查询
      </el-button>
    </div>
    <PageState
      :loading="loading"
      :error="error"
      :empty="!loading && rows.length === 0"
      :request-id="requestId"
    >
      <el-table
        :data="rows"
        stripe
      >
        <el-table-column
          prop="workCode"
          label="workCode"
          min-width="140"
        />
        <el-table-column
          prop="name"
          label="名称"
          min-width="140"
        />
        <el-table-column
          prop="workType"
          label="类型"
          width="90"
        />
        <el-table-column
          prop="status"
          label="状态"
          width="140"
        />
        <el-table-column
          prop="grayRatio"
          label="灰度"
          width="80"
        />
        <el-table-column
          prop="currentVersion"
          label="版本"
          width="100"
        />
        <el-table-column
          label="操作"
          min-width="280"
        >
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              @click="$router.push(`/ops/works/${row.id}`)"
            >
              编辑
            </el-button>
            <el-button
              v-for="action in workActions(row.status)"
              :key="action.to"
              link
              :type="action.danger ? 'danger' : 'primary'"
              @click="openStatus(row.id, action.to, action.label)"
            >
              {{ action.label }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="pageNo"
        layout="total, prev, pager, next"
        :total="total"
        @current-change="load"
      />
    </PageState>
    <ReasonDialog
      v-model="dialog.open"
      :title="dialog.label"
      warning="将提交 status 与 reason。不允许的迁移由后端拒绝。"
      @confirm="onStatus"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { opsApi } from "@/api/kjx";
import PageHeader from "@/components/PageHeader.vue";
import PageState from "@/components/PageState.vue";
import ReasonDialog from "@/components/ReasonDialog.vue";
import { formatRequestFailure } from "@/lib/errors";
import { workActions } from "@/lib/work-status";
import type { AdminWork, WorkStatus } from "@/lib/types";

const statuses: WorkStatus[] = [
  "DRAFT",
  "PENDING_REVIEW",
  "PUBLISHED",
  "GRAY",
  "UNPUBLISHED",
  "REJECTED",
];
const loading = ref(false);
const error = ref("");
const requestId = ref("");
const rows = ref<AdminWork[]>([]);
const total = ref(0);
const pageNo = ref(1);
const keyword = ref("");
const status = ref<WorkStatus | "">("");
const dialog = reactive({
  open: false,
  id: 0,
  to: "PUBLISHED" as WorkStatus,
  label: "",
});

function openStatus(id: number, to: WorkStatus, label: string) {
  dialog.id = id;
  dialog.to = to;
  dialog.label = label;
  dialog.open = true;
}

async function load() {
  loading.value = true;
  try {
    const payload = await opsApi.pageWorks({
      keyword: keyword.value || undefined,
      status: status.value || undefined,
      pageNo: pageNo.value,
      pageSize: 20,
    });
    rows.value = payload.data.list;
    total.value = payload.data.total;
    requestId.value = payload.requestId;
  } catch (cause) {
    const failure = formatRequestFailure(cause);
    error.value = failure.title;
    requestId.value = failure.requestId;
  } finally {
    loading.value = false;
  }
}

async function onStatus(payload: { reason: string }) {
  try {
    await opsApi.updateWork(dialog.id, { status: dialog.to, reason: payload.reason });
    dialog.open = false;
    ElMessage.success("状态已提交");
    await load();
  } catch (cause) {
    ElMessage.error(formatRequestFailure(cause).title);
  }
}

onMounted(load);
</script>
