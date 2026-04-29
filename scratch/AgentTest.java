import java.util.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class AgentTest {
    private static final String API_KEY = "sk-cp-8CfhCseGPoEKj_ePJc6lLGVKAobDyQLIOVXsxWKCAxZ3EFaIRSOv6_rqL1fQxnKJTBMrji34Kd3Pl5k1RK9Rd_jTNzrEtC_zgdS19kPsWmqO5RN9Dm3S2Dk";
    private static final String BASE_URL = "https://api.minimax.chat/v1/chat/completions";
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();

    public static void main(String[] args) throws Exception {
        String topic = "机器人与流浪猫";
        String requirement = "必须有激烈的对峙和哲学冲突";
        
        System.out.println("--- STEP 1: PLANNER ---");
        String plannerPrompt = String.format("你是一位经验丰富的影视策划人。请根据以下主题和附加要求，为一部微电影设计一份详详尽的【剧本大纲】。主题：%s，附加要求：%s", topic, requirement);
        String outline = chat(plannerPrompt);
        System.out.println("PLANNER OUTPUT:\n" + outline + "\n");

        System.out.println("--- STEP 2: WRITER (v1) ---");
        String writerPrompt = String.format("你是一位专业的微电影编剧。请根据以下【剧本大纲】，撰写出具体的【剧本内容】。剧本内容需要包含具体的场景和对白。大纲：\n%s", outline);
        String scriptV1 = chat(writerPrompt);
        System.out.println("WRITER V1 OUTPUT:\n" + scriptV1 + "\n");

        System.out.println("--- STEP 3: REVIEWER (v1) ---");
        String reviewerPrompt = String.format("你是一位严苛的影视项目制片人。请比对以下【策划大纲】和【剧本草稿】，判断剧本是否严重偏题。请以 JSON 格式返回: {\"approved\": boolean, \"feedback\": \"string\"}。大纲：\n%s\n\n草稿：\n%s", outline, scriptV1);
        String reviewV1 = chat(reviewerPrompt);
        System.out.println("REVIEWER V1 OUTPUT:\n" + reviewV1 + "\n");
        
        // Assume it needs one more iteration to be interesting
        System.out.println("--- STEP 4: WRITER (v2) ---");
        String writerV2Prompt = String.format("请根据审稿意见修改剧本。意见：%s\n原有剧本：\n%s", reviewV1, scriptV1);
        String scriptV2 = chat(writerV2Prompt);
        System.out.println("WRITER V2 OUTPUT:\n" + scriptV2 + "\n");

        System.out.println("--- STEP 5: REVIEWER (v2) ---");
        String reviewerV2Prompt = String.format("请再次审阅修改后的剧本。大纲：\n%s\n\n新剧本：\n%s", outline, scriptV2);
        String reviewV2 = chat(reviewerV2Prompt);
        System.out.println("REVIEWER V2 OUTPUT:\n" + reviewV2 + "\n");
    }

    private static String chat(String content) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("model", "MiniMax-M2.7");
        body.put("messages", List.of(Map.of("role", "user", "content", content)));
        
        String json = mapper.writeValueAsString(body);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + API_KEY)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode root = mapper.readTree(response.body());
        return root.path("choices").get(0).path("message").path("content").asText();
    }
}
