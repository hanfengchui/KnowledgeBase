<template>
  <el-dialog :model-value="modelValue" title="分配角色" width="560px" @update:model-value="$emit('update:modelValue', $event)">
    <el-form label-position="top">
      <el-form-item label="用户">
        <el-input :model-value="userLabel" disabled />
      </el-form-item>
      <el-form-item v-if="isPlatformAdmin" label="平台角色">
        <el-select v-model="form.platformRoleCodes" multiple collapse-tags class="dialog-select">
          <el-option v-for="item in platformRoleOptions" :key="item.code" :label="item.name" :value="item.code" />
        </el-select>
      </el-form-item>
      <el-form-item label="租户角色">
        <el-select v-model="form.tenantRoleCodes" multiple collapse-tags class="dialog-select">
          <el-option v-for="item in tenantRoleOptions" :key="item.code" :label="item.name" :value="item.code" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="loading" @click="$emit('submit')">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  isPlatformAdmin: {
    type: Boolean,
    default: false
  },
  userLabel: {
    type: String,
    default: ''
  },
  form: {
    type: Object,
    required: true
  },
  platformRoleOptions: {
    type: Array,
    default: () => []
  },
  tenantRoleOptions: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  }
})

defineEmits(['update:modelValue', 'submit'])
</script>
