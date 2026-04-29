#!/bin/bash
echo "🚀 [1/11] 启动 Spring Boot 项目..."
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

SESSION_ID="final-test-$(date +%s)"
echo -e "\n🎬 [2/11] 触发剧本生成工作流 (Topic: 科幻惊悚)..."
echo "  → 预期流程: START → planner → [断点1] 等待导演"
curl -s "http://localhost:8080/agent/create-script?topic=SciFiThriller&requirement=Test&sessionId=${SESSION_ID}" | jq .

echo -e "\n⏳ 等待 8 秒，让全知参谋 (Copilot) 分析大纲..."
sleep 8

echo -e "\n💡 [3/11] 获取参谋针对第一版大纲的建议..."
curl -s "http://localhost:8080/intervention/${SESSION_ID}/advice" | jq .

echo -e "\n▶️ [4/11] 提交导演的第一次打回指令（测试无限死循环 Bug 是否已修复）..."
echo "  → 预期流程: 路由回 planner → planner重新产出大纲并清理指令 → [断点1再次触发] 等待导演"
curl -N -X POST "http://localhost:8080/intervention/${SESSION_ID}/resume" \
     -H "Content-Type: application/json" \
     -d "{\"feedback\": \"不行，科幻元素太少了，打回重做。\"}" 

echo -e "\n\n⏳ 等待 15 秒，让系统重写大纲并让参谋再次分析..."
sleep 15

echo -e "\n💡 [5/11] 获取参谋针对【第二版大纲】的建议..."
curl -s "http://localhost:8080/intervention/${SESSION_ID}/advice" | jq .

echo -e "\n▶️ [6/11] 提交导演对新大纲的同意指令，系统将进入编剧环节..."
echo "  → 预期流程: writer(耗时较长) → reviewer → director_approval(新增的导演拍板节点) → [断点2] 等待导演"
curl -N -X POST "http://localhost:8080/intervention/${SESSION_ID}/resume" \
     -H "Content-Type: application/json" \
     -d "{\"feedback\": \"这次通过了，继续往下写剧本。\"}" 

echo -e "\n\n⏳ 等待 10 秒，让全知参谋分析写好的剧本..."
sleep 10

echo -e "\n💡 [7/11] 获取参谋针对最终剧本的终审建议..."
curl -s "http://localhost:8080/intervention/${SESSION_ID}/advice" | jq .

echo -e "\n▶️ [8/11] 提交最终的导演拍板指令（强制完结项目）..."
echo "  → 预期流程: END"
curl -N -X POST "http://localhost:8080/intervention/${SESSION_ID}/resume" \
     -H "Content-Type: application/json" \
     -d "{\"feedback\": \"写得很棒，正式通过！\"}" 

echo -e "\n\n🛑 [9/11] 测试完成，关闭 Spring Boot..."
kill $APP_PID
echo "✅ [10/11] 包含打回重写和最终拍板的三轮终极测试执行完毕。SessionID: ${SESSION_ID}"
