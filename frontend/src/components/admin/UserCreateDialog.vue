<template>
  <el-dialog :model-value="modelValue" title="创建用户" width="540px" @update:model-value="$emit('update:modelValue', $event)">
    <el-form label-position="top">
      <el-form-item v-if="isPlatformAdmin" label="所属租户">
        <el-select v-model="form.tenantId" class="dialog-select" placeholder="请选择租户">
          <el-option v-for="tenant in availableTenants" :key="tenant.id" :label="tenant.name" :value="tenant.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="用户名">
        <el-input v-model="form.username" />
      </el-form-item>
      <el-form-item label="显示名">
        <el-input v-model="form.displayName" />
      </el-form-item>
      <el-form-item label="邮箱">
        <el-input v-model="form.email" />
      </el-form-item>
      <el-form-item label="初始密码">
        <el-input v-model="form.password" type="password" show-password />
      </el-form-item>
      <el-form-item label="租户角色">
        <el-select v-model="form.tenantRoleCodes" multiple collapse-tags class="dialog-select">
          <el-option v-for="item in tenantRoleOptions" :key="item.code" :label="item.name" :value="item.code" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="loading" @click="$emit('submit')">创建</el-button>
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
  availableTenants: {
    type: Array,
    default: () => []
  },
  form: {
    type: Object,
    required: true
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
