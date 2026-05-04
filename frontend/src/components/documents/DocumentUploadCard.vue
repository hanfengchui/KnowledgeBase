<template>
  <article class="card">
    <div class="card-head">
      <div>
        <h3>文档入库</h3>
        <p>支持 `.txt / .md / .pdf / .docx`。上传和重建索引仍以当前知识库为边界。</p>
      </div>
      <div class="inline-actions">
        <el-button :loading="loadingDocuments" @click="$emit('refresh')">刷新列表</el-button>
        <el-button :loading="reindexing" :disabled="!canReindex" @click="$emit('reindex')">
          重建索引
        </el-button>
      </div>
    </div>

    <el-upload
      drag
      accept=".txt,.md,.pdf,.docx"
      :show-file-list="false"
      :http-request="(options) => $emit('upload', options)"
      :disabled="!canUpload"
    >
      <el-icon class="upload-icon"><UploadFilled /></el-icon>
      <div class="el-upload__text">拖拽文件到这里，或 <em>点击上传</em></div>
      <template #tip>
        <div class="el-upload__tip">
          {{ canUpload ? '上传后会自动解析、切分、写入向量或关键词检索索引。' : '当前账号在该知识库上没有上传权限。' }}
        </div>
      </template>
    </el-upload>
  </article>
</template>

<script setup>
import { UploadFilled } from '@element-plus/icons-vue'

defineProps({
  loadingDocuments: {
    type: Boolean,
    default: false
  },
  reindexing: {
    type: Boolean,
    default: false
  },
  canUpload: {
    type: Boolean,
    default: false
  },
  canReindex: {
    type: Boolean,
    default: false
  }
})

defineEmits(['refresh', 'reindex', 'upload'])
</script>
