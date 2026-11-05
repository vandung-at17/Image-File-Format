package vd.code;

import com.sun.net.httpserver.HttpServer;
import java.awt.Desktop;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public class TikTokAuthTest {

    // Thông tin cấu hình Sandbox của bạn
    private static final String CLIENT_KEY = "sbawem9we3loqc4f1a";
    private static final String CLIENT_SECRET = "SUuBaUTEtQWkf0E6PqjMMx9i4628eM8l";
    private static final String REDIRECT_URI = "http://localhost:8080/callback/";

    private static String codeVerifier;

    public static void main(String[] args) throws Exception {
        // 1. Tạo code_verifier ngẫu nhiên chuẩn PKCE (64 ký tự)
        codeVerifier = generateCodeVerifier();

        // 2. Mặc định TikTok Desktop dùng HEX SHA-256 cho code_challenge.
        // (Nếu TikTok cấu hình chuẩn Base64URL, hàm dưới cũng hỗ trợ chuyển đổi dễ dàng)
        String codeChallenge = sha256Hex(codeVerifier);

        // 3. Khởi tạo Local Server tại cổng 8080 để tự động hứng mã code từ trình duyệt
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/callback/", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            String code = extractParam(query, "code");

            String responseText = "<html><body style='font-family:sans-serif;text-align:center;padding-top:50px;'>"
                    + "<h2>Da nhan ma xac thuc! Ban co the dong tab nay va xem ket qua trong Console cua IDE Java.</h2>"
                    + "</body></html>";
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            exchange.sendResponseHeaders(200, responseText.getBytes(StandardCharsets.UTF_8).length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseText.getBytes(StandardCharsets.UTF_8));
            }

            if (code != null) {
                System.out.println("\n[1] Da bat duoc Authorization Code: " + code);
                exchangeCodeForToken(code);
            } else {
                System.err.println("\n[!] Khong tim thay code. Query tra ve: " + query);
            }

            // Tắt server sau khi hoàn tất
            new Thread(() -> {
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                server.stop(0);
            }).start();
        });
        server.start();
        System.out.println("=== Local Server dang lang nghe tai " + REDIRECT_URI + " ===");

        // 4. Tạo URL ủy quyền và tự động bật trình duyệt
        String authUrl = "https://www.tiktok.com/v2/auth/authorize/?"
                + "client_key=" + CLIENT_KEY
                + "&scope=user.info.basic,video.publish,video.upload"
                + "&response_type=code"
                + "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8)
                + "&state=java_app"
                + "&code_challenge=" + codeChallenge
                + "&code_challenge_method=S256";

        System.out.println("Dang mo trinh duyet... Neu khong tu mo, hay copy link sau dan vao trinh duyet:\n" + authUrl);
        if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().browse(new URI(authUrl));
        }
    }

    private static void exchangeCodeForToken(String code) {
        try {
            String formBody = "client_key=" + URLEncoder.encode(CLIENT_KEY, StandardCharsets.UTF_8)
                    + "&client_secret=" + URLEncoder.encode(CLIENT_SECRET, StandardCharsets.UTF_8)
                    + "&code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
                    + "&grant_type=authorization_code"
                    + "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, StandardCharsets.UTF_8)
                    + "&code_verifier=" + URLEncoder.encode(codeVerifier, StandardCharsets.UTF_8);

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://open.tiktokapis.com/v2/oauth/token/"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("Cache-Control", "no-cache")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("\n[2] KET QUA TRA VE TU TIKTOK (Status " + response.statusCode() + "):");
            System.out.println(response.body());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String extractParam(String query, String key) {
        if (query == null) return null;
        for (String param : query.split("&")) {
            String[] pair = param.split("=", 2);
            if (pair.length == 2 && pair[0].equals(key)) {
                return URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static String generateCodeVerifier() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // Mã hóa SHA-256 dạng HEX (Chuẩn của TikTok Desktop Login Kit)
    private static String sha256Hex(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(input.getBytes(StandardCharsets.US_ASCII));
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // Dự phòng: Mã hóa SHA-256 dạng Base64URL (Chuẩn của TikTok Web)
    private static String sha256Base64Url(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(input.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }
}