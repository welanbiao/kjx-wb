<template>
  <div class="fields">
    <el-form-item
      v-if="showDouyin"
      label="抖音号"
      required
    >
      <el-input
        :model-value="model.douyinId"
        maxlength="128"
        :disabled="disableDouyin"
        aria-label="抖音号"
        @update:model-value="onField('douyinId', $event)"
      />
    </el-form-item>
    <el-form-item
      label="已订阅月数"
      required
    >
      <el-input-number
        :model-value="model.subscribedMonths"
        :min="1"
        :max="120"
        :step="1"
        controls-position="right"
        aria-label="已订阅月数"
        @update:model-value="onField('subscribedMonths', $event ?? null)"
      />
      <p class="kjx-hint">
        从抖音后台抄录，只用于绑定三要素。改月数不会改额度。
      </p>
    </el-form-item>
    <el-form-item
      label="累计已订阅天数"
      required
    >
      <el-input-number
        :model-value="model.subscribedDays"
        :min="0"
        :max="36500"
        :step="1"
        controls-position="right"
        aria-label="累计已订阅天数"
        @update:model-value="onField('subscribedDays', $event ?? null)"
      />
      <p class="kjx-hint">
        只用于额度。不得用月数换算，本页也不会自动填写。
      </p>
    </el-form-item>
    <el-form-item
      label="到期日期"
      required
    >
      <el-date-picker
        :model-value="model.expireDate"
        type="date"
        value-format="YYYY-MM-DD"
        placeholder="选择日期"
        aria-label="到期日期"
        @update:model-value="onField('expireDate', String($event ?? ''))"
      />
      <p class="kjx-hint">
        提交时展开为当天 23:59:59.999+08:00。不由月数或天数推导。
      </p>
    </el-form-item>
    <el-form-item label="备注">
      <el-input
        :model-value="model.remark"
        maxlength="255"
        @update:model-value="onField('remark', $event)"
      />
    </el-form-item>
  </div>
</template>

<script setup lang="ts">
import { patchMemberTerm, type FieldKey, type MemberTermInput } from "@/lib/member-form";

const props = withDefaults(
  defineProps<{
    model: MemberTermInput;
    showDouyin?: boolean;
    disableDouyin?: boolean;
  }>(),
  { showDouyin: true, disableDouyin: false },
);

const emit = defineEmits<{
  "update:model": [MemberTermInput];
}>();

function onField<K extends FieldKey>(key: K, value: MemberTermInput[K]) {
  emit("update:model", patchMemberTerm(props.model, key, value));
}
</script>
