package com.example.aiinterview.tool;

import com.example.aiinterview.dto.OrderInfo;
import com.example.aiinterview.dto.ToolCallDto;
import com.example.aiinterview.service.ToolExecutionRecorder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderToolsTest {

    @Test
    void returnsDemoOrderAndRecordsToolCall() {
        ToolExecutionRecorder recorder = new ToolExecutionRecorder();
        OrderTools orderTools = new OrderTools(recorder);

        recorder.start();
        OrderInfo orderInfo = orderTools.queryOrder("ord-2026-0001");
        List<ToolCallDto> calls = recorder.drain();

        assertThat(orderInfo.status()).isEqualTo("已发货");
        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).name()).isEqualTo("queryOrder");
    }
}
