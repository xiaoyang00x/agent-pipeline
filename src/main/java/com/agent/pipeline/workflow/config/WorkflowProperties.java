package com.agent.pipeline.workflow.config;


import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 工作流干预策略配置属性
 */
@Component
@ConfigurationProperties(prefix = "agent.workflow.interruption")
public class WorkflowProperties {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WorkflowProperties.class);

    @jakarta.annotation.PostConstruct
    public void init() {
        log.info("⚙️ [工作流配置] 模式: {}, 断点列表: {}", mode, breakpoints);
    }

    /**
     * 模式：ALWAYS, NEVER, SELECTIVE
     */
    private String mode = "NEVER";

    /**
     * 需要中断的节点列表
     */
    private List<String> breakpoints;

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public List<String> getBreakpoints() {
        return breakpoints;
    }

    public void setBreakpoints(List<String> breakpoints) {
        this.breakpoints = breakpoints;
    }

    /**
     * 判断指定节点是否需要中断
     */
    public boolean shouldInterrupt(String nodeName) {
        if ("ALWAYS".equalsIgnoreCase(mode)) {
            return true;
        }
        if ("SELECTIVE".equalsIgnoreCase(mode) && breakpoints != null) {
            return breakpoints.contains(nodeName);
        }
        return false;
    }
}
