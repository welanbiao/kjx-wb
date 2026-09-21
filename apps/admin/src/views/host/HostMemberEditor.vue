<template>
  <div class="kjx-page">
    <PageHeader
      :title="isEdit ? '编辑/续期会员' : '录入会员'"
      lead="subscribedMonths、subscribedDays、expireAt 三者独立必填，页面不会互相改写。"
    />
    <el-form
      label-position="top"
      style="max-width: 560px"
      @submit.prevent="onSubmit"
    >
      <MemberTermFields
        v-model:model="form"
        :disable-douyin="isEdit"
      />
      <el-form-item
        v-if="isEdit"
        label="操作理由"
        required
      >
        <el-input
          v-model="reason"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
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
    <p
      v-if="requestId"
      class="kjx-request-id"
    >
      requestId：{{ requestId }}
    </p>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { selfApi } from "@/api/kjx";
import MemberTermFields from "@/components/MemberTermFields.vue";
import PageHeader from "@/components/PageHeader.vue";
import { expireDatePart } from "@/lib/expire-at";
import { formatRequestFailure } from "@/lib/errors";
import { emptyMemberTerm, validateCreateMember, validateUpdateMember } from "@/lib/member-form";

const route = useRoute();
const router = useRouter();
const isEdit = Boolean(route.params.id);
const form = ref(emptyMemberTerm());
const reason = ref("");
const busy = ref(false);
const requestId = ref("");

async function load() {
  if (!isEdit) {
    return;
  }
  const id = Number(route.params.id);
  const payload = await selfApi.pageMembers({ pageNo: 1, pageSize: 100 });
  const member = payload.data.list.find((item) => item.id === id);
  requestId.value = payload.requestId;
  if (!member) {
    ElMessage.error("找不到本部门会员，请从列表进入");
    await router.replace("/self/members");
    return;
  }
  form.value = {
    douyinId: member.douyinId,
    subscribedMonths: member.subscribedMonths,
    subscribedDays: member.subscribedDays,
    expireDate: expireDatePart(member.expireAt),
    remark: member.remark ?? "",
  };
}

async function onSubmit() {
  busy.value = true;
  try {
    if (isEdit) {
      const body = validateUpdateMember(form.value, reason.value);
      const payload = await selfApi.updateMember(Number(route.params.id), body);
      requestId.value = payload.requestId;
      ElMessage.success("已更新");
      await router.push(`/self/members/${payload.data.id}`);
    } else {
      const body = validateCreateMember(form.value);
      const payload = await selfApi.createMember(body);
      requestId.value = payload.requestId;
      ElMessage.success(`已生成编码 ${payload.data.memberCode}`);
      await router.push(`/self/members/${payload.data.memberId}`);
    }
  } catch (cause) {
    const failure = formatRequestFailure(cause);
    ElMessage.error(failure.title);
    requestId.value = failure.requestId;
  } finally {
    busy.value = false;
  }
}

onMounted(() => {
  void load();
});
</script>
