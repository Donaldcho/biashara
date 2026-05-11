import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class SslSmokeTest {
    public static void main(String[] args) throws Exception {
        String u = args.length > 0 ? args[0]
                : "https://dl.google.com/dl/android/maven2/master-index.xml";
        HttpClient client = HttpClient.newBuilder().build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(u)).GET().build();
        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
        int code = response.statusCode();
        if (code < 200 || code >= 400) {
            throw new IllegalStateException("Unexpected HTTP status: " + code);
        }
        System.out.println("OK HTTP " + code + " from " + u);
    }
}
