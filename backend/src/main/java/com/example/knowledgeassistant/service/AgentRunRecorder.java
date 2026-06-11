package com.example.knowledgeassistant.service;

import com.example.knowledgeassistant.dto.AgentStepDto;
import com.example.knowledgeassistant.dto.AgentTraceDto;
import com.example.knowledgeassistant.security.CurrentUser;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AgentRunRecorder {

    private final JdbcTemplate jdbcTemplate;
    private final AuditLogService auditLogService;

    public AgentRunRecorder(JdbcTemplate jdbcTemplate, AuditLogService auditLogService) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditLogService = auditLogService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AgentRun start(CurrentUser currentUser, UUID knowledgeBaseId, String question) {
        UUID runId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO agent_runs (
                    id, tenant_id, user_id, knowledge_base_id, question, status
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                runId,
                currentUser == null ? null : currentUser.tenantId(),
                currentUser == null ? null : currentUser.userId(),
                knowledgeBaseId,
                question,
                "running"
        );
        return new AgentRun(runId, Instant.now());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(
            AgentRun run,
            boolean usedRag,
            boolean usedTools,
            int retrievedCount,
            int toolCallCount,
            String status,
            String errorMessage
    ) {
        long latencyMs = Duration.between(run.startedAt(), Instant.now()).toMillis();
        jdbcTemplate.update("""
                UPDATE agent_runs
                SET status = ?,
                    used_rag = ?,
                    used_tools = ?,
                    retrieved_count = ?,
                    tool_call_count = ?,
                    latency_ms = ?,
                    error_message = ?,
                    completed_at = now()
                WHERE id = ?
                """,
                status,
                usedRag,
                usedTools,
                retrievedCount,
                toolCallCount,
                latencyMs,
                auditLogService.summarize(errorMessage),
                run.id()
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AgentStep recordStep(
            AgentRun run,
            String stepType,
            String name,
            String inputSummary,
            String outputSummary,
            long latencyMs,
            String status
    ) {
        int stepIndex = run.nextStepIndex();
        jdbcTemplate.update("""
                INSERT INTO agent_steps (
                    id, run_id, step_index, step_type, name, status,
                    input_summary, output_summary, latency_ms, completed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                """,
                UUID.randomUUID(),
                run.id(),
                stepIndex,
                stepType,
                name,
                status,
                auditLogService.summarize(inputSummary),
                auditLogService.summarize(outputSummary),
                latencyMs
        );

        AgentStep step = new AgentStep(
                stepIndex,
                stepType,
                name,
                status,
                auditLogService.summarize(inputSummary),
                auditLogService.summarize(outputSummary),
                latencyMs
        );
        run.addStep(step);
        return step;
    }

    public AgentTraceDto toTrace(AgentRun run, String status) {
        List<AgentStepDto> steps = run.steps().stream()
                .map(step -> new AgentStepDto(
                        step.stepIndex(),
                        step.stepType(),
                        step.name(),
                        step.status(),
                        step.inputSummary(),
                        step.outputSummary(),
                        step.latencyMs()
                ))
                .toList();
        return new AgentTraceDto(run.id(), status, steps);
    }

    public String summarizeSources(int count) {
        return count <= 0 ? "未召回知识库片段" : "召回知识库片段数=" + count;
    }

    public String summarizeTools(int count) {
        return count <= 0 ? "未调用工具" : "工具调用次数=" + count;
    }

    public String summarizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return auditLogService.summarize(value);
    }

    public record AgentRun(UUID id, Instant startedAt, List<AgentStep> steps) {
        public AgentRun(UUID id, Instant startedAt) {
            this(id, startedAt, new ArrayList<>());
        }

        private int nextStepIndex() {
            return steps.size() + 1;
        }

        private void addStep(AgentStep step) {
            steps.add(step);
        }
    }

    public record AgentStep(
            int stepIndex,
            String stepType,
            String name,
            String status,
            String inputSummary,
            String outputSummary,
            long latencyMs
    ) {
    }
}
