<template>
  <article class="card card--span-2">
    <div class="card-head">
      <div>
        <h3>Agent 执行轨迹</h3>
        <p>展示本次问答的检索、工具、模型生成和权限控制步骤。</p>
      </div>
      <el-tag :type="statusType">{{ agentTrace?.status || 'idle' }}</el-tag>
    </div>
    <el-empty v-if="!agentTrace || !agentTrace.steps?.length" description="暂无执行轨迹" />
    <el-timeline v-else>
      <el-timeline-item
        v-for="step in agentTrace.steps"
        :key="`${agentTrace.runId}-${step.stepIndex}`"
        :timestamp="`${step.latencyMs} ms`"
        placement="top"
      >
        <div class="trace-item">
          <div class="trace-head">
            <strong>{{ step.stepIndex }}. {{ step.name }}</strong>
            <el-tag size="small" effect="plain">{{ step.stepType }}</el-tag>
          </div>
          <p v-if="step.outputSummary">{{ step.outputSummary }}</p>
          <small v-if="step.inputSummary">{{ step.inputSummary }}</small>
        </div>
      </el-timeline-item>
    </el-timeline>
  </article>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  agentTrace: {
    type: Object,
    default: null
  }
})

const statusType = computed(() => {
  if (props.agentTrace?.status === 'succeeded') {
    return 'success'
  }
  if (props.agentTrace?.status === 'failed') {
    return 'danger'
  }
  return 'info'
})
</script>
