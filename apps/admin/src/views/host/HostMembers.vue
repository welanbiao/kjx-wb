<template>
  <div class="kjx-page">
    <PageHeader
      title="我的会员"
      lead="仅本部门。月份、天数、到期时间独立必填，前端不做换算。"
    >
      <template #actions>
        <el-button
          type="primary"
          @click="$router.push('/self/members/new')"
        >
          录入会员
        </el-button>
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
        v-model="filters.douyinId"
        placeholder="抖音号"
        clearable
      />
      <el-input
        v-model="filters.memberCode"
        placeholder="会员编码"
        clearable
      />
      <el-select
        v-model="filters.status"
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
      empty-text="没有本部门会员"
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
          prop="douyinId"
          label="抖音号"
          min-width="120"
        />
        <el-table-column
          prop="subscribedMonths"
          label="月数"
          width="80"
        />
        <el-table-column
          prop="subscribedDays"
          label="天数"
          width="80"
        />
        <el-table-column
          prop="expireAt"
          label="到期"
          min-width="200"
        />
        <el-table-column
          prop="availableCount"
          label="额度"
          width="80"
        />
        <el-table-column
          prop="status"
          label="状态"
          width="120"
        />
        <el-table-column
          label="操作"
          min-width="200"
        >
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              @click="$router.push(`/self/members/${row.id}`)"
            >
              详情
            </el-button>
            <el-button
              link
              type="primary"
              @click="$router.push(`/self/members/${row.id}/edit`)"
            >
              编辑/续期
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="pageNo"
        v-model:page-size="pageSize"
        layout="total, prev, pager, next"
        :total="total"
        @current-change="load"
      />
    </PageState>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import { selfApi } from "@/api/kjx";
import PageHeader from "@/components/PageHeader.vue";
import PageState from "@/components/PageState.vue";
import { saveBlobFile } from "@/lib/download";
import { formatRequestFailure } from "@/lib/errors";
import type { AdminMember, MemberStatus } from "@/lib/types";

const loading = ref(false);
const exporting = ref(false);
const error = ref("");
const requestId = ref("");
const rows = ref<AdminMember[]>([]);
const total = ref(0);
const pageNo = ref(1);
const pageSize = ref(20);
const filters = reactive({
  douyinId: "",
  memberCode: "",
  status: "" as MemberStatus | "",
});

async function load() {
  loading.value = true;
  error.value = "";
  try {
    const payload = await selfApi.pageMembers({
      douyinId: filters.douyinId || undefined,
      memberCode: filters.memberCode || undefined,
      status: filters.status || undefined,
      pageNo: pageNo.value,
      pageSize: pageSize.value,
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
  exporting.value = true;
  try {
    const file = await selfApi.exportMembers(filters.status || undefined);
    await saveBlobFile(file.blob, "members.xlsx");
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
