#!/bin/bash
echo "🚀 [1/8] 启动 Spring Boot 项目..."
mvn clean compile spring-boot:run > app_run.log 2>&1 &
APP_PID=$!

echo "⏳ 等待应用在 8080 端口启动..."
while ! nc -z localhost 8080; do
  sleep 1
  if ! kill -0 $APP_PID 2>/dev/null; then
    echo "❌ Spring Boot 启动失败，请检查 app_run.log"
    cat app_run.log
    exit 1
  fi
done
echo "✅ 应用已成功启动！PID: $APP_PID"

SESSION_ID="copilot-test-$(date +%s)"
echo -e "\n🎬 [2/8] 触发剧本生成工作流 (Topic: 校园推理)..."
echo "  → 预期流程: START → planner → [断点1] 等待导演"
curl -s "http://localhost:8080/agent/create-script?topic=CampusMystery&requirement=Test&sessionId=${SESSION_ID}" | jq .

echo -e "\n⏳ 等待 8 秒，让全知参谋 (Copilot) 分析大纲..."
sleep 8

echo -e "\n💡 [3/8] 获取参谋针对大纲的建议..."
curl -s "http://localhost:8080/intervention/${SESSION_ID}/advice" | jq .

echo -e "\n▶️ [4/8] 提交第一轮干预指令（同意大纲），系统将执行 Writer -> Reviewer..."
echo "  → 预期流程: writer(耗时较长) → reviewer → [断点2] 等待导演"
curl -N -X POST "http://localhost:8080/intervention/${SESSION_ID}/resume" \
     -H "Content-Type: application/json" \
     -d "{\"feedback\": \"大纲不错，请在剧本中突出密室推理的氛围感，加入翻转结局。\"}" 

echo -e "\n\n⏳ 等待 10 秒，让全知参谋 (Copilot) 分析写好的剧本和机器审稿意见..."
sleep 10

echo -e "\n💡 [5/8] 获取参谋针对最终剧本的终审建议..."
curl -s "http://localhost:8080/intervention/${SESSION_ID}/advice" | jq .

echo -e "\n▶️ [6/8] 提交第二轮干预指令（强制通过），直接完结项目..."
echo "  → 预期流程: END"
curl -N -X POST "http://localhost:8080/intervention/${SESSION_ID}/resume" \
     -H "Content-Type: application/json" \
     -d "{\"feedback\": \"我觉得写得挺好的，不用管AI审稿人，强制通过！\"}" 

echo -e "\n\n🛑 [7/8] 测试完成，关闭 Spring Boot..."
kill $APP_PID
echo "✅ [8/8] 完整两轮 HITL 协作流程执行完毕。SessionID: ${SESSION_ID}"
