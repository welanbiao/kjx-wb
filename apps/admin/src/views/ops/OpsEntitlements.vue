<template>
  <div class="kjx-page">
    <PageHeader
      title="权益人工调整"
      lead="仅使用冻结字段 action / workCode / reason。不猜测 WP-13 新字段。"
    />
    <el-form
      label-position="top"
      style="max-width: 560px"
      @submit.prevent="onSubmit"
    >
      <el-form-item
        label="会员 ID"
        required
      >
        <el-input-number
          v-model="memberId"
          :min="1"
        />
      </el-form-item>
      <el-button
        @click="loadMember"
      >
        查询会员
      </el-button>
      <el-descriptions
        v-if="member"
        :column="1"
        border
        style="margin: 16px 0"
      >
        <el-descriptions-item label="编码">
          {{ member.memberCode }}
        </el-descriptions-item>
        <el-descriptions-item label="额度 / 已解锁 / 剩余">
          {{ member.availableCount }} / {{ member.unlockedCount }} / {{ member.remainCount }}
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          {{ member.status }}
        </el-descriptions-item>
      </el-descriptions>
      <el-form-item
        label="动作"
        required
      >
        <el-select v-model="action">
          <el-option
            label="ADD"
            value="ADD"
          />
          <el-option
            label="REMOVE"
            value="REMOVE"
          />
        </el-select>
      </el-form-item>
      <el-form-item
        label="workCode"
        required
      >
        <el-input v-model="workCode" />
      </el-form-item>
      <el-form-item
        label="理由"
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
        二次确认后提交
      </el-button>
    </el-form>
    <p
      v-if="requestId"
      class="kjx-request-id"
    >
      requestId：{{ requestId }}
    </p>
    <ReasonDialog
      v-model="confirm"
      title="确认调整权益"
      warning="同事务增删 entitlement、重算并写 unlock_record(source=official_*)。WP-13 未落地时真实后端可能 404。"
      :require-reason="false"
      @confirm="submit"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRoute } from "vue-router";
import { ElMessage } from "element-plus";
import { toAdjustEntitlementBody } from "@/api/entitlement-adapter";
import { opsApi } from "@/api/kjx";
import PageHeader from "@/components/PageHeader.vue";
import ReasonDialog from "@/components/ReasonDialog.vue";
import { formatRequestFailure } from "@/lib/errors";
import type { AdminMember, EntitlementAction } from "@/lib/types";

const route = useRoute();
const memberId = ref<number | undefined>(
  route.query.memberId ? Number(route.query.memberId) : undefined,
);
const member = ref<AdminMember | null>(null);
const action = ref<EntitlementAction>("ADD");
const workCode = ref("");
const reason = ref("");
const busy = ref(false);
const confirm = ref(false);
const requestId = ref("");

async function loadMember() {
  if (!memberId.value) {
    ElMessage.error("会员 ID 必填");
    return;
  }
  try {
    const payload = await opsApi.getMember(memberId.value);
    member.value = payload.data;
    requestId.value = payload.requestId;
  } catch (cause) {
    ElMessage.error(formatRequestFailure(cause).title);
  }
}

function onSubmit() {
  try {
    toAdjustEntitlementBody({
      action: action.value,
      workCode: workCode.value,
      reason: reason.value,
    });
    confirm.value = true;
  } catch (cause) {
    ElMessage.error(cause instanceof Error ? cause.message : "校验失败");
  }
}

async function submit() {
  if (!memberId.value) {
    return;
  }
  busy.value = true;
  try {
    const payload = await opsApi.adjustEntitlement(memberId.value, {
      action: action.value,
      workCode: workCode.value,
      reason: reason.value,
    });
    member.value = payload.data;
    requestId.value = payload.requestId;
    confirm.value = false;
    ElMessage.success("已提交调整");
  } catch (cause) {
    ElMessage.error(formatRequestFailure(cause).title);
  } finally {
    busy.value = false;
  }
}

onMounted(() => {
  if (memberId.value) {
    void loadMember();
  }
});
</script>
