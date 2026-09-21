<template>
  <div class="kjx-page">
    <PageHeader
      title="跨部门会员"
      lead="运营查询/录入不走 HOST 自助路径。"
    >
      <template #actions>
        <el-button
          type="primary"
          @click="$router.push('/ops/members/new')"
        >
          录入会员
        </el-button>
      </template>
    </PageHeader>
    <div class="kjx-toolbar">
      <el-input
        v-model="deptId"
        placeholder="部门 ID"
        clearable
      />
      <el-input
        v-model="douyinId"
        placeholder="抖音号"
        clearable
      />
      <el-input
        v-model="memberCode"
        placeholder="会员编码"
        clearable
      />
      <el-select
        v-model="status"
        clearable
        placeholder="状态"
      >
        <el-option
          label="未生效"
          value="NOT_STARTED"
        />
        <el-option
          label="有效"
          value="VALID"
        />
        <el-option
          label="过期"
          value="EXPIRED"
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
          label="编码"
          min-width="140"
        />
        <el-table-column
          prop="douyinId"
          label="抖音号"
          min-width="120"
        />
        <el-table-column
          prop="subscribedMonths"
          label="月"
          width="70"
        />
        <el-table-column
          prop="subscribedDays"
          label="天"
          width="70"
        />
        <el-table-column
          prop="status"
          label="状态"
          width="120"
        />
        <el-table-column
          label="操作"
          min-width="180"
        >
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              @click="$router.push(`/ops/members/${row.id}`)"
            >
              详情
            </el-button>
            <el-button
              link
              type="primary"
              @click="$router.push(`/ops/members/${row.id}/edit`)"
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
import type { AdminMember, MemberStatus } from "@/lib/types";

const loading = ref(false);
const error = ref("");
const requestId = ref("");
const rows = ref<AdminMember[]>([]);
const total = ref(0);
const pageNo = ref(1);
const deptId = ref("");
const douyinId = ref("");
const memberCode = ref("");
const status = ref<MemberStatus | "">("");

async function load() {
  loading.value = true;
  try {
    const payload = await opsApi.pageMembers({
      deptId: deptId.value ? Number(deptId.value) : undefined,
      douyinId: douyinId.value || undefined,
      memberCode: memberCode.value || undefined,
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

onMounted(load);
</script>
