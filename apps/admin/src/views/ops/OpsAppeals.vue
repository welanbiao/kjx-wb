<template>
  <div class="kjx-page">
    <PageHeader
      title="申诉处理"
      lead="HandleAppealRequest：result 与 handleNote 必填。"
    />
    <div class="kjx-toolbar">
      <el-select
        v-model="status"
        clearable
        placeholder="状态"
      >
        <el-option
          label="待处理"
          value="PENDING"
        />
        <el-option
          label="已通过"
          value="APPROVED"
        />
        <el-option
          label="已驳回"
          value="REJECTED"
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
          prop="id"
          label="ID"
          width="80"
        />
        <el-table-column
          prop="appealType"
          label="类型"
          width="140"
        />
        <el-table-column
          prop="content"
          label="内容"
          min-width="200"
        />
        <el-table-column
          prop="status"
          label="状态"
          width="120"
        />
        <el-table-column
          label="操作"
          width="180"
        >
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'PENDING'"
              link
              type="primary"
              @click="open('APPROVED', row.id)"
            >
              通过
            </el-button>
            <el-button
              v-if="row.status === 'PENDING'"
              link
              type="danger"
              @click="open('REJECTED', row.id)"
            >
              驳回
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
      :title="dialog.result === 'APPROVED' ? '通过申诉' : '驳回申诉'"
      warning="将提交 result 与 handleNote。"
      reason-label="处理说明 handleNote"
      @confirm="onConfirm"
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
import { buildHandleAppeal } from "@/lib/review-actions";
import type { AdminAppeal, AppealStatus } from "@/lib/types";

const loading = ref(false);
const error = ref("");
const requestId = ref("");
const rows = ref<AdminAppeal[]>([]);
const total = ref(0);
const pageNo = ref(1);
const status = ref<AppealStatus | "">("PENDING");
const dialog = reactive({
  open: false,
  id: 0,
  result: "APPROVED" as "APPROVED" | "REJECTED",
});

function open(result: "APPROVED" | "REJECTED", id: number) {
  dialog.result = result;
  dialog.id = id;
  dialog.open = true;
}

async function load() {
  loading.value = true;
  try {
    const payload = await opsApi.pageAppeals({
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

async function onConfirm(payload: { reason: string }) {
  try {
    await opsApi.handleAppeal(dialog.id, buildHandleAppeal(dialog.result, payload.reason));
    dialog.open = false;
    ElMessage.success("已处理");
    await load();
  } catch (cause) {
    ElMessage.error(formatRequestFailure(cause).title);
  }
}

onMounted(load);
</script>
