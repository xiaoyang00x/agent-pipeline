#!/bin/bash
echo "🚀 [1/5] 启动 Spring Boot 项目..."
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

SESSION_ID="arch-test-$(date +%s)"
echo -e "\n🎬 [2/5] 触发剧本生成工作流 (Topic: 校园推理)..."
echo "  → 预期流程: START → planner → advisor → [断点] → 等待导演"
curl -s "http://localhost:8080/agent/create-script?topic=CampusMystery&requirement=Test&sessionId=${SESSION_ID}" | jq .

echo -e "\n⏳ 等待 3 秒..."
sleep 3

echo -e "\n💡 [3/5] 获取参谋建议（应来自 AdvisorNode 写入 State 的内容）..."
curl -s "http://localhost:8080/intervention/${SESSION_ID}/advice" | jq .

echo -e "\n▶️ [4/5] 提交导演正面反馈并接关..."
echo "  → 预期流程: writer → reviewer → END"
curl -N -X POST "http://localhost:8080/intervention/${SESSION_ID}/resume" \
     -H "Content-Type: application/json" \
     -d "{\"feedback\": \"大纲不错，请在剧本中突出密室推理的氛围感，加入翻转结局。\"}" 

echo -e "\n\n🛑 [5/5] 测试完成，关闭 Spring Boot..."
kill $APP_PID
echo "✅ 流程执行完毕。SessionID: ${SESSION_ID}"
