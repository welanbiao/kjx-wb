<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="480px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <p class="kjx-lead">
      {{ warning }}
    </p>
    <el-form
      label-position="top"
      @submit.prevent="onConfirm"
    >
      <el-form-item
        v-if="remarkMode"
        label="备注（可选）"
      >
        <el-input
          v-model="remark"
          maxlength="255"
        />
      </el-form-item>
      <el-form-item
        v-if="requireReason"
        :label="reasonLabel"
        required
      >
        <el-input
          v-model="reason"
          type="textarea"
          :rows="4"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">
        取消
      </el-button>
      <el-button
        type="primary"
        :loading="busy"
        @click="onConfirm"
      >
        确认
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from "vue";
import { ElMessage } from "element-plus";
import { validateReason } from "@/lib/member-form";

const props = withDefaults(
  defineProps<{
    modelValue: boolean;
    title: string;
    warning: string;
    requireReason?: boolean;
    remarkMode?: boolean;
    reasonLabel?: string;
    busy?: boolean;
  }>(),
  {
    requireReason: true,
    remarkMode: false,
    reasonLabel: "操作理由（写入 AUDIT）",
    busy: false,
  },
);

const emit = defineEmits<{
  "update:modelValue": [boolean];
  confirm: [payload: { reason: string; remark: string }];
}>();

const reason = ref("");
const remark = ref("");

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      reason.value = "";
      remark.value = "";
    }
  },
);

function onConfirm() {
  try {
    const valid = props.requireReason ? validateReason(reason.value) : "";
    emit("confirm", { reason: valid, remark: remark.value.trim() });
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : "校验失败");
  }
}
</script>
