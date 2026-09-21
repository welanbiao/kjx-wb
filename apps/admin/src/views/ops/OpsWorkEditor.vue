<template>
  <div class="kjx-page">
    <PageHeader
      :title="isEdit ? '编辑作品' : '新建作品'"
      lead="字段对齐 CreateWorkRequest / UpdateWorkRequest。状态机按钮在列表页。"
    />
    <el-form
      label-position="top"
      style="max-width: 640px"
      @submit.prevent="onSubmit"
    >
      <el-form-item
        v-if="!isEdit"
        label="workCode"
        required
      >
        <el-input v-model="workCode" />
      </el-form-item>
      <el-form-item
        label="名称"
        required
      >
        <el-input v-model="name" />
      </el-form-item>
      <el-form-item
        v-if="!isEdit"
        label="类型"
        required
      >
        <el-select v-model="workType">
          <el-option
            label="layout"
            value="layout"
          />
          <el-option
            label="game"
            value="game"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="摘要">
        <el-input
          v-model="summary"
          type="textarea"
        />
      </el-form-item>
      <el-form-item label="封面 URL">
        <el-input v-model="coverUrl" />
      </el-form-item>
      <el-form-item label="分类">
        <el-input v-model="category" />
      </el-form-item>
      <el-form-item label="accessForm">
        <el-select v-model="accessForm">
          <el-option
            v-for="item in ['A', 'B', 'C']"
            :key="item"
            :label="item"
            :value="item"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="灰度 grayRatio 0-100">
        <el-input-number
          v-model="grayRatio"
          :min="0"
          :max="100"
        />
      </el-form-item>
      <el-form-item label="排序 sortWeight">
        <el-input-number v-model="sortWeight" />
      </el-form-item>
      <el-form-item
        v-if="isEdit"
        label="当前状态"
      >
        <el-input
          :model-value="status"
          disabled
        />
      </el-form-item>
      <el-form-item
        v-if="isEdit"
        label="版本"
      >
        <span>{{ currentVersion || "—" }} / {{ contentVersion || "—" }}</span>
      </el-form-item>
      <el-form-item
        label="操作理由"
        required
      >
        <el-input
          v-model="reason"
          type="textarea"
          :rows="3"
        />
      </el-form-item>
      <el-button
        type="primary"
        native-type="submit"
        :loading="busy"
      >
        保存
      </el-button>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { opsApi } from "@/api/kjx";
import PageHeader from "@/components/PageHeader.vue";
import { formatRequestFailure } from "@/lib/errors";
import { validateReason } from "@/lib/member-form";
import type { AccessForm, WorkStatus, WorkType } from "@/lib/types";

const route = useRoute();
const router = useRouter();
const isEdit = Boolean(route.params.id);
const workCode = ref("");
const name = ref("");
const workType = ref<WorkType>("layout");
const summary = ref("");
const coverUrl = ref("");
const category = ref("");
const accessForm = ref<AccessForm>("A");
const grayRatio = ref(0);
const sortWeight = ref(0);
const status = ref<WorkStatus>("DRAFT");
const currentVersion = ref<string | null>(null);
const contentVersion = ref<string | null>(null);
const reason = ref("");
const busy = ref(false);

async function load() {
  if (!isEdit) {
    return;
  }
  const payload = await opsApi.getWork(Number(route.params.id));
  name.value = payload.data.name;
  summary.value = payload.data.summary ?? "";
  coverUrl.value = payload.data.coverUrl ?? "";
  category.value = payload.data.category ?? "";
  accessForm.value = payload.data.accessForm ?? "A";
  grayRatio.value = payload.data.grayRatio ?? 0;
  sortWeight.value = payload.data.sortWeight ?? 0;
  status.value = payload.data.status;
  currentVersion.value = payload.data.currentVersion;
  contentVersion.value = payload.data.contentVersion;
}

async function onSubmit() {
  busy.value = true;
  try {
    const validReason = validateReason(reason.value);
    if (isEdit) {
      await opsApi.updateWork(Number(route.params.id), {
        name: name.value,
        summary: summary.value,
        coverUrl: coverUrl.value,
        category: category.value,
        accessForm: accessForm.value,
        grayRatio: grayRatio.value,
        sortWeight: sortWeight.value,
        reason: validReason,
      });
    } else {
      await opsApi.createWork({
        workCode: workCode.value.trim(),
        name: name.value.trim(),
        workType: workType.value,
        summary: summary.value,
        coverUrl: coverUrl.value,
        category: category.value,
        accessForm: accessForm.value,
        grayRatio: grayRatio.value,
        sortWeight: sortWeight.value,
        reason: validReason,
      });
    }
    ElMessage.success("已保存");
    await router.push("/ops/works");
  } catch (cause) {
    ElMessage.error(formatRequestFailure(cause).title);
  } finally {
    busy.value = false;
  }
}

onMounted(() => {
  void load();
});
</script>
