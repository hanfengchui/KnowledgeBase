package com.example.knowledgeassistant.tool;

import com.example.knowledgeassistant.dto.OrderInfo;
import com.example.knowledgeassistant.service.ToolExecutionRecorder;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class OrderTools {

    private final ToolExecutionRecorder recorder;
    private final Map<String, OrderInfo> sampleOrders = Map.of(
            "ORD-2026-0001",
            new OrderInfo(
                    "ORD-2026-0001",
                    "上海星河科技有限公司",
                    "已发货",
                    "12880.00 CNY",
                    "2026-04-27 18:00 前",
                    List.of("2026-04-22 已支付", "2026-04-23 仓库出库", "2026-04-24 顺丰揽收")
            ),
            "ORD-2026-0002",
            new OrderInfo(
                    "ORD-2026-0002",
                    "杭州云帆贸易有限公司",
                    "待财务确认",
                    "5690.00 CNY",
                    "付款确认后 2 个工作日内发货",
                    List.of("2026-04-24 已创建订单", "2026-04-24 等待对公到账确认")
            ),
            "ORD-2026-0003",
            new OrderInfo(
                    "ORD-2026-0003",
                    "北京北辰制造有限公司",
                    "退款处理中",
                    "2300.00 CNY",
                    "2026-04-29 前原路退回",
                    List.of("2026-04-20 已签收", "2026-04-22 用户申请退款", "2026-04-24 售后审核通过")
            )
    );

    public OrderTools(ToolExecutionRecorder recorder) {
        this.recorder = recorder;
    }

    @Tool(name = "queryOrder", description = "根据订单号查询业务系统中的订单、支付、物流或退款状态")
    public OrderInfo queryOrder(
            @ToolParam(description = "订单号，例如 ORD-2026-0001")
            String orderNo
    ) {
        String normalizedOrderNo = orderNo == null ? "" : orderNo.trim().toUpperCase(Locale.ROOT);
        OrderInfo result = sampleOrders.getOrDefault(normalizedOrderNo, new OrderInfo(
                normalizedOrderNo,
                "未知客户",
                "未找到",
                "0.00 CNY",
                "无",
                List.of("示例业务数据中未查询到该订单号")
        ));

        recorder.record("queryOrder", "{\"orderNo\":\"" + normalizedOrderNo + "\"}", result.toString());
        return result;
    }
}
