#!/bin/bash
API_KEY="sk-cp-8CfhCseGPoEKj_ePJc6lLGVKAobDyQLIOVXsxWKCAxZ3EFaIRSOv6_rqL1fQxnKJTBMrji34Kd3Pl5k1RK9Rd_jTNzrEtC_zgdS19kPsWmqO5RN9Dm3S2Dk"
BASE_URL="https://api.minimax.chat/v1/chat/completions"

function chat() {
  local content="$1"
  JSON_PAYLOAD=$(jq -n --arg msg "$content" '{model: "MiniMax-M2.7", messages: [{role: "user", content: $msg}]}')
  
  curl -s -X POST "$BASE_URL" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $API_KEY" \
    -d "$JSON_PAYLOAD" | jq -r '.choices[0].message.content'
}

echo "--- STEP 1: PLANNER ---"
chat "你是一位经验丰富的影视策划人。请根据主题【机器人与流浪猫】和附加要求【必须有激烈的对峙和哲学冲突】，设计一份剧本大纲。" | tee -a scratch/workflow_output.txt
echo "" >> scratch/workflow_output.txt

# 为了获取中间变量，我还是得用赋值，但我要加进度日志
echo "--- STEP 2: WRITER (v1) ---"
OUTLINE=$(tail -n +2 scratch/workflow_output.txt | sed '/--- STEP 2/q')
# 简化逻辑，直接重新运行
