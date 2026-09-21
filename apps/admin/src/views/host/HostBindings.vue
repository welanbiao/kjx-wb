<template>
  <div class="kjx-page">
    <PageHeader
      title="绑定状态"
      lead="HOST 只读本部门绑定。审核、驳回、强制解绑在运营路径。"
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
          prop="memberCode"
          label="会员编码"
          min-width="140"
        />
        <el-table-column
          prop="douyinIdInput"
          label="提交抖音号"
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
          prop="createTime"
          label="提交时间"
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
import { onMounted, ref } from "vue";
import { selfApi } from "@/api/kjx";
import PageHeader from "@/components/PageHeader.vue";
import PageState from "@/components/PageState.vue";
import { formatRequestFailure } from "@/lib/errors";
import type { AdminBinding, BindingReviewStatus } from "@/lib/types";

const loading = ref(false);
const error = ref("");
const requestId = ref("");
const rows = ref<AdminBinding[]>([]);
const total = ref(0);
const pageNo = ref(1);
const status = ref<BindingReviewStatus | "">("");
const memberCode = ref("");

async function load() {
  loading.value = true;
  error.value = "";
  try {
    const payload = await selfApi.pageBindings({
      status: status.value || undefined,
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

onMounted(load);
</script>
