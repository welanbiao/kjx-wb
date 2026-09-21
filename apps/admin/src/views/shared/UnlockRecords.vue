<template>
  <div class="kjx-page">
    <PageHeader
      title="解锁记录"
      :lead="ops
        ? '查询 GET /unlock-record/page；导出仅运营 GET /unlock-record/export，HOST 不得调用。'
        : 'HOST 仅查询本部门记录。契约写明可见；WP-13 控制器当前 requireOps 时会 403。'"
    >
      <template
        v-if="ops"
        #actions
      >
        <el-button
          :loading="exporting"
          @click="onExport"
        >
          导出
        </el-button>
      </template>
    </PageHeader>
    <div class="kjx-toolbar">
      <el-input
        v-if="ops"
        v-model="deptId"
        placeholder="部门 ID"
        clearable
      />
      <el-input
        v-model="memberId"
        placeholder="会员 ID"
        clearable
      />
      <el-input
        v-model="workCode"
        placeholder="workCode"
        clearable
      />
      <el-select
        v-model="result"
        clearable
        placeholder="结果"
      >
        <el-option
          label="成功"
          value="SUCCESS"
        />
        <el-option
          label="失败"
          value="FAILED"
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
          prop="deptId"
          label="部门"
          width="80"
        />
        <el-table-column
          prop="memberCode"
          label="会员编码"
          min-width="140"
        />
        <el-table-column
          prop="workCode"
          label="作品"
          min-width="140"
        />
        <el-table-column
          prop="source"
          label="来源"
          width="140"
        />
        <el-table-column
          prop="result"
          label="结果"
          width="100"
        />
        <el-table-column
          prop="unlockTime"
          label="时间"
          min-width="200"
        />
      </el-table>
      <el-pagination
        v-model:current-page="pageNo"
        layout="total, prev, pager, next"
        :total="total"
        @current-change="load"
      />
    </PageState>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { ElMessage } from "element-plus";
import { opsApi } from "@/api/kjx";
import PageHeader from "@/components/PageHeader.vue";
import PageState from "@/components/PageState.vue";
import { saveBlobFile } from "@/lib/download";
import { formatRequestFailure } from "@/lib/errors";
import type { UnlockRecord, UnlockResult } from "@/lib/types";

const route = useRoute();
const ops = computed(() => route.path.startsWith("/ops/"));
const loading = ref(false);
const exporting = ref(false);
const error = ref("");
const requestId = ref("");
const rows = ref<UnlockRecord[]>([]);
const total = ref(0);
const pageNo = ref(1);
const deptId = ref("");
const memberId = ref("");
const workCode = ref("");
const result = ref<UnlockResult | "">("");

function query() {
  return {
    deptId: ops.value && deptId.value ? Number(deptId.value) : undefined,
    memberId: memberId.value ? Number(memberId.value) : undefined,
    workCode: workCode.value || undefined,
    result: result.value || undefined,
  };
}

async function load() {
  loading.value = true;
  error.value = "";
  try {
    const payload = await opsApi.pageUnlocks({
      ...query(),
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

async function onExport() {
  if (!ops.value) {
    return;
  }
  exporting.value = true;
  try {
    const file = await opsApi.exportUnlocks(query());
    await saveBlobFile(file.blob, "unlock-records.xlsx");
    requestId.value = file.requestId;
    ElMessage.success("已导出");
  } catch (cause) {
    const failure = formatRequestFailure(cause);
    ElMessage.error(failure.title);
    requestId.value = failure.requestId;
  } finally {
    exporting.value = false;
  }
}

onMounted(load);
</script>
