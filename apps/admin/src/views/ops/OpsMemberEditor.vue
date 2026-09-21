<template>
  <div class="kjx-page">
    <PageHeader
      :title="isEdit ? '编辑会员' : '运营录入会员'"
      lead="运营录入必须带 deptId 与 reason。月/天/到期互不推导。"
    />
    <el-form
      label-position="top"
      style="max-width: 560px"
      @submit.prevent="onSubmit"
    >
      <el-form-item
        v-if="!isEdit"
        label="目标主播部门 deptId"
        required
      >
        <el-input-number
          v-model="deptId"
          :min="1"
          controls-position="right"
        />
      </el-form-item>
      <MemberTermFields
        v-model:model="form"
        :disable-douyin="isEdit"
      />
      <el-form-item
        label="操作理由"
        required
      >
        <el-input
          v-model="reason"
          type="textarea"
          :rows="3"
          maxlength="500"
        />
      </el-form-item>
      <el-button
        type="primary"
        native-type="submit"
        :loading="busy"
      >
        提交
      </el-button>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { opsApi } from "@/api/kjx";
import MemberTermFields from "@/components/MemberTermFields.vue";
import PageHeader from "@/components/PageHeader.vue";
import { expireDatePart } from "@/lib/expire-at";
import { formatRequestFailure } from "@/lib/errors";
import { emptyMemberTerm, validateCreateMember, validateUpdateMember } from "@/lib/member-form";

const route = useRoute();
const router = useRouter();
const isEdit = Boolean(route.params.id);
const form = ref(emptyMemberTerm());
const deptId = ref<number | undefined>();
const reason = ref("");
const busy = ref(false);

async function load() {
  if (!isEdit) {
    return;
  }
  const payload = await opsApi.getMember(Number(route.params.id));
  form.value = {
    douyinId: payload.data.douyinId,
    subscribedMonths: payload.data.subscribedMonths,
    subscribedDays: payload.data.subscribedDays,
    expireDate: expireDatePart(payload.data.expireAt),
    remark: payload.data.remark ?? "",
  };
}

async function onSubmit() {
  busy.value = true;
  try {
    if (isEdit) {
      await opsApi.updateMember(Number(route.params.id), validateUpdateMember(form.value, reason.value));
      ElMessage.success("已更新");
      await router.push(`/ops/members/${route.params.id}`);
    } else {
      if (!deptId.value) {
        throw new Error("deptId 必填");
      }
      const created = validateCreateMember(form.value);
      const payload = await opsApi.createMember({
        ...created,
        deptId: deptId.value,
        reason: reason.value,
      });
      ElMessage.success(`已生成 ${payload.data.memberCode}`);
      await router.push(`/ops/members/${payload.data.memberId}`);
    }
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
