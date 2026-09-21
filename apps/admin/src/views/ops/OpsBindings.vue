<template>
  <div class="kjx-page">
    <PageHeader
      title="绑定审核"
      lead="通过可带 remark；驳回与强制解绑必须 reason。HOST 无此入口。"
    />
    <div class="kjx-toolbar">
      <el-select
        v-model="status"
        clearable
        placeholder="状态"
      >
        <el-option
          label="待审"
          value="PENDING_REVIEW"
        />
        <el-option
          label="已匹配"
          value="MATCHED"
        />
        <el-option
          label="已驳回"
          value="REJECTED"
        />
        <el-option
          label="解绑中"
          value="UNBINDING"
        />
      </el-select>
      <el-input
        v-model="deptId"
        placeholder="部门 ID"
        clearable
      />
      <el-input
        v-model="memberCode"
        placeholder="会员编码"
        clearable
      />
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
          prop="deptId"
          label="部门"
          width="80"
        />
        <el-table-column
          prop="memberCode"
          label="编码"
          min-width="140"
        />
        <el-table-column
          prop="douyinIdInput"
          label="抖音号"
          min-width="120"
        />
        <el-table-column
          prop="claimMonths"
          label="声称月数"
          width="100"
        />
        <el-table-column
          prop="status"
          label="状态"
          width="140"
        />
        <el-table-column
          label="操作"
          min-width="220"
        >
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'PENDING_REVIEW'"
              link
              type="primary"
              @click="open('approve', row.id)"
            >
              通过
            </el-button>
            <el-button
              v-if="row.status === 'PENDING_REVIEW'"
              link
              type="danger"
              @click="open('reject', row.id)"
            >
              驳回
            </el-button>
            <el-button
              v-if="row.status === 'MATCHED'"
              link
              type="danger"
              @click="open('unbind', row.id)"
            >
              强制解绑
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
      :title="dialogTitle"
      :warning="dialogWarning"
      :require-reason="dialog.kind !== 'approve'"
      :remark-mode="dialog.kind === 'approve'"
      @confirm="onConfirm"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { opsApi } from "@/api/kjx";
import PageHeader from "@/components/PageHeader.vue";
import PageState from "@/components/PageState.vue";
import ReasonDialog from "@/components/ReasonDialog.vue";
import { formatRequestFailure } from "@/lib/errors";
import { buildApproveBinding, buildForceUnbind, buildRejectBinding } from "@/lib/review-actions";
import type { AdminBinding, BindingReviewStatus } from "@/lib/types";

const loading = ref(false);
const error = ref("");
const requestId = ref("");
const rows = ref<AdminBinding[]>([]);
const total = ref(0);
const pageNo = ref(1);
const status = ref<BindingReviewStatus | "">("PENDING_REVIEW");
const deptId = ref("");
const memberCode = ref("");
const dialog = reactive({
  open: false,
  kind: "approve" as "approve" | "reject" | "unbind",
  id: 0,
});

const dialogTitle = computed(() => {
  if (dialog.kind === "approve") {
    return "审核通过";
  }
  if (dialog.kind === "reject") {
    return "审核驳回";
  }
  return "强制解绑";
});
const dialogWarning = computed(() => {
  if (dialog.kind === "approve") {
    return "将提交 POST /binding/{id}/approve，可选 remark，不发短信。";
  }
  if (dialog.kind === "reject") {
    return "将提交 { reason }，必填。";
  }
  return "高风险操作，必须填写 reason。";
});

function open(kind: typeof dialog.kind, id: number) {
  dialog.kind = kind;
  dialog.id = id;
  dialog.open = true;
}

async function load() {
  loading.value = true;
  try {
    const payload = await opsApi.pageBindings({
      status: status.value || undefined,
      deptId: deptId.value ? Number(deptId.value) : undefined,
      memberCode: memberCode.value || undefined,
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

async function onConfirm(payload: { reason: string; remark: string }) {
  try {
    if (dialog.kind === "approve") {
      const body = buildApproveBinding(payload.remark);
      await opsApi.approveBinding(dialog.id, body.remark);
    } else if (dialog.kind === "reject") {
      await opsApi.rejectBinding(dialog.id, buildRejectBinding(payload.reason));
    } else {
      await opsApi.forceUnbind(dialog.id, buildForceUnbind(payload.reason));
    }
    dialog.open = false;
    ElMessage.success("已提交");
    await load();
  } catch (cause) {
    ElMessage.error(formatRequestFailure(cause).title);
  }
}

onMounted(load);
</script>
