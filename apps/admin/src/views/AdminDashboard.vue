<template>
  <div class="kjx-page">
    <PageHeader
      title="工作台"
      lead="主播走自助路径，运营走跨部门路径。菜单隐藏不能替代后端校验。"
    />
    <el-descriptions
      :column="1"
      border
    >
      <el-descriptions-item label="当前账号">
        {{ session?.username }}
      </el-descriptions-item>
      <el-descriptions-item label="角色">
        {{ session?.roles.join(", ") }}
      </el-descriptions-item>
      <el-descriptions-item label="部门">
        {{ session?.deptId ?? "—" }}
      </el-descriptions-item>
      <el-descriptions-item label="API 模式">
        {{ mode }}
      </el-descriptions-item>
      <el-descriptions-item label="契约前缀">
        /admin-api/kjx
      </el-descriptions-item>
    </el-descriptions>
  </div>
</template>

<script setup lang="ts">
import PageHeader from "@/components/PageHeader.vue";
import { getApiMode } from "@/lib/env";
import { sessionState } from "@/lib/session";

const session = sessionState().session;
const mode = getApiMode();
</script>
