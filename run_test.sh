#!/bin/bash
echo "🚀 [1/5] 启动 Spring Boot 项目..."
mvn clean compile spring-boot:run > app_run.log 2>&1 &
APP_PID=$!

echo "⏳ 等待应用在 8080 端口启动..."
while ! nc -z localhost 8080; do
  sleep 1
  # 如果进程退出了，直接终止
  if ! kill -0 $APP_PID 2>/dev/null; then
    echo "❌ Spring Boot 启动失败，请检查 app_run.log"
    cat app_run.log
    exit 1
  fi
done
echo "✅ 应用已成功启动！PID: $APP_PID"

SESSION_ID="redo-test-$(date +%s)"
echo -e "\n🎬 [2/5] 触发剧本生成工作流 (Topic: Wuxia Mystery)..."
curl -s "http://localhost:8080/agent/create-script?topic=WuxiaMystery&requirement=Test&sessionId=${SESSION_ID}" | jq .

echo -e "\n⏳ 等待 3 秒，让参谋 Agent 准备建议..."
sleep 3

echo -e "\n💡 [3/5] 获取参谋 Agent 建议..."
curl -s "http://localhost:8080/intervention/${SESSION_ID}/advice" | jq .

echo -e "\n▶️ [4/5] 提交【打回重写】指令并接关 (Resume)..."
# 注意：这里我们故意给出否定指令，验证路由是否会回到 planner
curl -N -X POST "http://localhost:8080/intervention/${SESSION_ID}/resume" \
     -H "Content-Type: application/json" \
     -d "{\"feedback\": \"不行，这个大纲武侠味不够，太现代化了，请打回重做，多加点古风意境。\"}" 

echo -e "\n\n🛑 [5/5] 测试完成，关闭 Spring Boot..."
kill $APP_PID
echo "✅ 流程执行完毕。"
