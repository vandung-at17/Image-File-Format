package vd.code;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TikTokPostTester {

    private static final String ACCESS_TOKEN = "act.srnukV4X6G5VJFa7ON2DiITTLM7opRx7rMMTqIohcYmfnujf5HT2jsDgiu8S!6549.s1";

    // Đặt = true nếu đã bật "Tài khoản riêng tư" trên điện thoại (Đăng thẳng lên tường)
    // Đặt = false nếu tài khoản đang Công khai (Gửi vào Hộp thư -> Thông báo hệ thống)
    private static final boolean USE_DIRECT_POST = false;

    public static void main(String[] args) throws Exception {
        System.out.println("=== [1] KIEM TRA CREATOR INFO ===");
        queryCreatorInfo();

        File sampleFile = new File("D:/test.mp4");
        if (sampleFile.exists()) {
            System.out.println("\n=== [2] DANG TAI FILE LEN TIKTOK ===");
            String newPublishId = uploadVideo(sampleFile);

            // Chỉ kiểm tra trạng thái khi lấy được publish_id mới thành công
            if (newPublishId != null) {
                System.out.println("\n=== [3] DANG CHO TIKTOK XU LY VIDEO (Ma: " + newPublishId + ") ===");
                for (int i = 1; i <= 5; i++) {
                    Thread.sleep(4000); // Chờ 4 giây mỗi lần hỏi
                    System.out.print("Lan kiem tra " + i + ": ");
                    String status = checkPostStatus(newPublishId);
                    if (status.contains("PUBLISH_COMPLETE") || status.contains("SEND_TO_USER_INBOX") || status.contains("FAILED")) {
                        break;
                    }
                }
            }
        } else {
            System.out.println("\n[!] Khong tim thay file D:/test.mp4");
        }
    }

    public static void queryCreatorInfo() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://open.tiktokapis.com/v2/post/publish/creator_info/query/"))
                .header("Authorization", "Bearer " + ACCESS_TOKEN)
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println("Status: " + response.statusCode());
        System.out.println("Response: " + response.body());
    }

    public static String uploadVideo(File videoFile) throws Exception {
        long fileSize = videoFile.length();
        HttpClient client = HttpClient.newHttpClient();

        String apiUrl;
        String initJson;

        if (USE_DIRECT_POST) {
            // Đăng thẳng lên kênh (Yêu cầu tài khoản TikTok phải bật chế độ "Tài khoản riêng tư")
            apiUrl = "https://open.tiktokapis.com/v2/post/publish/video/init/";
            initJson = "{"
                    + "\"post_info\": {"
                    + "  \"title\": \"Test upload tu Java App\","
                    + "  \"privacy_level\": \"SELF_ONLY\""
                    + "},"
                    + "\"source_info\": {"
                    + "  \"source\": \"FILE_UPLOAD\","
                    + "  \"video_size\": " + fileSize + ","
                    + "  \"chunk_size\": " + fileSize + ","
                    + "  \"total_chunk_count\": 1"
                    + "}"
                    + "}";
        } else {
            // Gửi vào Hộp thư nháp (Chạy được với mọi tài khoản Công khai hoặc Riêng tư)
            apiUrl = "https://open.tiktokapis.com/v2/post/publish/inbox/video/init/";
            initJson = "{"
                    + "\"source_info\": {"
                    + "  \"source\": \"FILE_UPLOAD\","
                    + "  \"video_size\": " + fileSize + ","
                    + "  \"chunk_size\": " + fileSize + ","
                    + "  \"total_chunk_count\": 1"
                    + "}"
                    + "}";
        }

        HttpRequest initReq = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Authorization", "Bearer " + ACCESS_TOKEN)
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(initJson, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> initRes = client.send(initReq, HttpResponse.BodyHandlers.ofString());
        System.out.println("Init Response: " + initRes.body());

        String publishId = extractJsonValue(initRes.body(), "publish_id");
        String uploadUrl = extractJsonValue(initRes.body(), "upload_url");
        if (uploadUrl == null || publishId == null) {
            System.err.println("-> LOI: Khong lay duoc upload_url. Neu dung USE_DIRECT_POST = true, hay dam bao tai khoan TikTok da bat 'Tai khoan rieng tu'!");
            return null;
        }
        uploadUrl = uploadUrl.replace("\\u0026", "&");

        byte[] fileBytes = Files.readAllBytes(videoFile.toPath());
        String contentRange = "bytes 0-" + (fileSize - 1) + "/" + fileSize;

        HttpRequest putReq = HttpRequest.newBuilder()
                .uri(URI.create(uploadUrl))
                .header("Content-Type", "video/mp4")
                .header("Content-Range", contentRange)
                .PUT(HttpRequest.BodyPublishers.ofByteArray(fileBytes))
                .build();

        HttpResponse<String> putRes = client.send(putReq, HttpResponse.BodyHandlers.ofString());
        System.out.println("Upload File Status: " + putRes.statusCode());

        return publishId;
    }

    public static String checkPostStatus(String publishId) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        String jsonBody = "{\"publish_id\": \"" + publishId + "\"}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://open.tiktokapis.com/v2/post/publish/status/fetch/"))
                .header("Authorization", "Bearer " + ACCESS_TOKEN)
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println(response.body());
        return response.body();
    }

    private static String extractJsonValue(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }
}