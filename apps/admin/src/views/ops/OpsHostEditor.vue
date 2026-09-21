<template>
  <div class="kjx-page">
    <PageHeader
      :title="isEdit ? '编辑主播' : '新建主播'"
      lead="CreateHostRequest 必填 hostCode、hostName、adminUserId、reason。"
    />
    <el-form
      label-position="top"
      style="max-width: 560px"
      @submit.prevent="onSubmit"
    >
      <el-form-item
        v-if="!isEdit"
        label="主播短码"
        required
      >
        <el-input
          v-model="hostCode"
          maxlength="32"
        />
      </el-form-item>
      <el-form-item
        label="主播名称"
        required
      >
        <el-input
          v-model="hostName"
          maxlength="30"
        />
      </el-form-item>
      <el-form-item
        v-if="!isEdit"
        label="关联后台账号 adminUserId"
        required
      >
        <el-input-number
          v-model="adminUserId"
          :min="1"
          controls-position="right"
        />
      </el-form-item>
      <el-form-item
        v-if="!isEdit"
        label="上级部门 parentDeptId"
      >
        <el-input-number
          v-model="parentDeptId"
          :min="1"
          controls-position="right"
        />
      </el-form-item>
      <el-form-item label="抖音账号">
        <el-input v-model="dyAccount" />
      </el-form-item>
      <el-form-item label="联系方式">
        <el-input v-model="contact" />
      </el-form-item>
      <el-form-item label="备注">
        <el-input v-model="remark" />
      </el-form-item>
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
import PageHeader from "@/components/PageHeader.vue";
import { formatRequestFailure } from "@/lib/errors";
import { validateReason } from "@/lib/member-form";

const route = useRoute();
const router = useRouter();
const isEdit = Boolean(route.params.id);
const hostCode = ref("");
const hostName = ref("");
const adminUserId = ref<number | undefined>();
const parentDeptId = ref<number | undefined>();
const dyAccount = ref("");
const contact = ref("");
const remark = ref("");
const reason = ref("");
const busy = ref(false);

async function load() {
  if (!isEdit) {
    return;
  }
  const payload = await opsApi.getHost(Number(route.params.id));
  hostName.value = payload.data.hostName;
  dyAccount.value = payload.data.dyAccount ?? "";
  contact.value = payload.data.contact ?? "";
  remark.value = payload.data.remark ?? "";
}

async function onSubmit() {
  busy.value = true;
  try {
    const validReason = validateReason(reason.value);
    if (isEdit) {
      await opsApi.updateHost(Number(route.params.id), {
        hostName: hostName.value,
        dyAccount: dyAccount.value,
        contact: contact.value,
        remark: remark.value,
        reason: validReason,
      });
      ElMessage.success("已更新");
    } else {
      if (!adminUserId.value) {
        throw new Error("adminUserId 必填");
      }
      await opsApi.createHost({
        hostCode: hostCode.value.trim(),
        hostName: hostName.value.trim(),
        adminUserId: adminUserId.value,
        parentDeptId: parentDeptId.value,
        dyAccount: dyAccount.value,
        contact: contact.value,
        remark: remark.value,
        reason: validReason,
      });
      ElMessage.success("已创建主播并关联后台账号");
    }
    await router.push("/ops/hosts");
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
