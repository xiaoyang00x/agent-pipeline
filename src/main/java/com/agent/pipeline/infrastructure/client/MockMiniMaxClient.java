package com.agent.pipeline.infrastructure.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 工业级 Mock 版 LLM 客户端
 * 提供高保真、场景化的模拟数据，确保测试阶段 UI 呈现效果真实。
 */
@Component
@Profile("mock")
public class MockMiniMaxClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(MockMiniMaxClient.class);

    @Override
    public String chat(String prompt) {
        log.info("🤖 [Mock Mode] 识别 Prompt 语境并生成高保真模拟数据...");

        // 1. 参谋诊断阶段优先判断 (因为 Prompt 中包含 "副手", "点评", "裁决建议")
        if (prompt.contains("副手") || prompt.contains("点评") || prompt.contains("裁决建议")) {
            if (prompt.contains("剧本草稿") || prompt.contains("审稿人")) {
                return "## 🎬 终审阶段诊断 (Director Copilot)\n\n| 维度 | 评估分数 | 诊断建议 |\n| :--- | :--- | :--- |\n| **情感共鸣** | 9.5/10 | 剧本结尾的牺牲非常感人，人物弧线已闭环。 |\n| **制作难度** | ⚠️ 预警 | 第三幕量子空间坍塌的特效预算可能超标，建议改为心理战表现。 |\n\n**💡 终极建议**：剧本已达到工业水准，建议直接通过并开启选角流程！";
            } else {
                return "## 🧐 策划阶段诊断 (Planner Advisor)\n\n| 维度 | 评估分数 | 诊断建议 |\n| :--- | :--- | :--- |\n| **叙事逻辑** | 8.5/10 | 当前‘钥匙人’设定非常吸睛，但建议在第二幕增加一个反转。 |\n| **商业潜力** | 9.0/10 | 量子视觉特效具有极强的工业卖点。 |\n\n**💡 策划建议**：导演请重点关注‘连莉’的早期背景铺垫。";
            }
        }

        // 2. 剧本撰写阶段 (Writer)
        if (prompt.contains("撰写") || prompt.contains("剧本内容") || prompt.contains("SCENE")) {
            return "【场号：01】\n【景别：特写/全景】\n【时间：夜】\n【地点：量子意识提取舱】\n\n[场景描述]\n深蓝色的液氮雾气在金属舱门边缓慢流动。舱内，主角‘连莉’的睫毛在急促地颤动，无数晶莹的传感器光点紧贴着她的太阳穴，忽明忽暗。\n\n[对话]\n连莉（呼吸急促）：它们...它们正在把我的记忆切片...我能感觉到每一个字节在剥离。\n\n智能系统 Lattice：连莉小姐，请保持意识稳定。逻辑基石的重塑需要您 30% 的前额叶皮层活跃度作为算力锚点。\n\n[动作]\n连莉猛地睁开眼，瞳孔缩放成两个淡金色的量子纠缠态符号。舱门发出一声刺耳的警报：逻辑链路 007 号，断开。";
        }

        // 3. 策划大纲阶段 (Planner)
        if (prompt.contains("大纲") || prompt.contains("策划")) {
            return "### 🎬《量子叙事：黄金母根》策划大纲 (Mock)\n\n#### 一、世界观设定 (World Building)\n在这个被高度算法化的未来，人类的情感已成为‘未声明变量’。社会运行依赖于被称为‘黄金母根’的中央逻辑系统，该系统出现了未知的逻辑漏洞，导致现实世界开始坍塌。\n\n#### 二、核心冲突 (Core Conflict)\n物理规则的失效 vs 情感变量的爆发。人类必须贡献出‘真实情感’的意识切片作为补丁来修复系统，但这会导致捐赠者永久丧失爱与恨的能力。\n\n#### 三、四幕式节奏 (Four-Act Structure)\n1. **启程**：连莉发现自己是唯一能读懂母根底层代码的‘钥匙人’。\n2. **纠缠**：Lattice 系统的觉醒，试图劝说连莉全面数字化以达到永生。\n3. **爆发**：连莉在虚拟量子空间与过往记忆的幻影对抗，发现黄金母根背后的秘密。\n4. **回响**：一个不符合逻辑的选择，却修复了整个物理世界，但代价是...连莉的失踪。";
        }

        // 4. 审核节点 (Reviewer/Approval)
        if (prompt.contains("审阅") || prompt.contains("审稿") || prompt.contains("判断")) {
            return "{\"approved\": true, \"feedback\": \"整体框架扎实，科幻概念实现度高，建议在下一阶段加强情感共鸣。\"}";
        }

        return "【Mock 通用回复】系统处于 Mock 模式，当前流程逻辑链路验证通过。";
    }
}
