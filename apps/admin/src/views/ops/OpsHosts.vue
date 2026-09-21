<template>
  <div class="kjx-page">
    <PageHeader
      title="主播管理"
      lead="创建主播必须关联 adminUserId。HOST 看不到此页。"
    >
      <template #actions>
        <el-button
          type="primary"
          @click="$router.push('/ops/hosts/new')"
        >
          新建主播
        </el-button>
      </template>
    </PageHeader>
    <div class="kjx-toolbar">
      <el-input
        v-model="hostCode"
        placeholder="主播短码"
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
          prop="deptId"
          label="部门 ID"
          width="100"
        />
        <el-table-column
          prop="hostCode"
          label="短码"
          width="120"
        />
        <el-table-column
          prop="hostName"
          label="名称"
          min-width="140"
        />
        <el-table-column
          prop="dyAccount"
          label="抖音账号"
          min-width="140"
        />
        <el-table-column
          label="操作"
          width="120"
        >
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              @click="$router.push(`/ops/hosts/${row.deptId}`)"
            >
              编辑
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
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { opsApi } from "@/api/kjx";
import PageHeader from "@/components/PageHeader.vue";
import PageState from "@/components/PageState.vue";
import { formatRequestFailure } from "@/lib/errors";
import type { AdminHost } from "@/lib/types";

const loading = ref(false);
const error = ref("");
const requestId = ref("");
const rows = ref<AdminHost[]>([]);
const total = ref(0);
const pageNo = ref(1);
const hostCode = ref("");

async function load() {
  loading.value = true;
  try {
    const payload = await opsApi.pageHosts({
      hostCode: hostCode.value || undefined,
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
