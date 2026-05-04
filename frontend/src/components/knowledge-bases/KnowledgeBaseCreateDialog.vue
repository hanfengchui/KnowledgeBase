<template>
  <el-dialog :model-value="modelValue" title="新建知识库" width="520px" @update:model-value="$emit('update:modelValue', $event)">
    <el-form label-position="top">
      <el-form-item label="知识库名称">
        <el-input v-model="form.name" placeholder="例如：售后服务知识库" />
      </el-form-item>
      <el-form-item label="知识库代码">
        <el-input v-model="form.code" placeholder="可选，例如：after-sales" />
      </el-form-item>
      <el-form-item label="说明">
        <el-input v-model="form.description" type="textarea" :rows="4" placeholder="简要说明覆盖范围" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="loading" @click="$emit('submit')">
        创建并切换
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  form: {
    type: Object,
    required: true
  },
  loading: {
    type: Boolean,
    default: false
  }
})

defineEmits(['update:modelValue', 'submit'])
</script>
