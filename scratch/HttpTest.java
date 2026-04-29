import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class HttpTest {
    public static void main(String[] args) throws Exception {
        String apiKey = "sk-cp-8CfhCseGPoEKj_ePJc6lLGVKAobDyQLIOVXsxWKCAxZ3EFaIRSOv6_rqL1fQxnKJTBMrji34Kd3Pl5k1RK9Rd_jTNzrEtC_zgdS19kPsWmqO5RN9Dm3S2Dk";
        String requestBody = "{\"model\": \"MiniMax-M2.7\", \"messages\": [{\"role\": \"user\", \"content\": \"hi\"}]}";
        
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.minimax.chat/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();
        
        System.out.println("Sending...");
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("Status: " + response.statusCode());
        System.out.println("Body: " + response.body());
    }
}
