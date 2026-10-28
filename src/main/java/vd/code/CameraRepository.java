package vd.code;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class CameraRepository {

    private static final Map<String, String[]> CAMERA_DATA = new LinkedHashMap<>();

    static {
        CAMERA_DATA.put("", new String[]{""});

        CAMERA_DATA.put("Xiaomi", new String[]{
                "Xiaomi 15 Ultra", "Xiaomi 15 Pro", "Xiaomi 14 Ultra", "Xiaomi 14 Pro",
                "Xiaomi 13 Ultra", "Xiaomi 13 Pro", "Xiaomi 12S Ultra"
        });

        CAMERA_DATA.put("OPPO", new String[]{
                "OPPO Find X8 Ultra", "OPPO Find X8 Pro", "OPPO Find X7 Ultra",
                "OPPO Find X6 Pro", "OPPO Reno12 Pro"
        });

        CAMERA_DATA.put("vivo", new String[]{
                "vivo X200 Pro", "vivo X100 Ultra", "vivo X100 Pro",
                "vivo X100s Pro", "vivo X90 Pro+"
        });

        CAMERA_DATA.put("Huawei", new String[]{
                "Huawei Pura 70 Ultra", "Huawei Pura 70 Pro+", "Huawei Mate 60 Pro+",
                "Huawei P60 Art", "Huawei Mate 50 Pro"
        });

        CAMERA_DATA.put("Canon", new String[]{
                "Canon EOS R5 Mark II", "Canon EOS R5", "Canon EOS R6 Mark II",
                "Canon EOS RP", "Canon EOS 5D Mark IV"
        });

        CAMERA_DATA.put("Sony", new String[]{
                "ILCE-7M4", "ILCE-7RM5", "ILCE-7SM3", "ILCE-7C", "ILCE-7CM2", "ZV-E10", "ILCE-7R6"
        });

        CAMERA_DATA.put("Nikon", new String[]{
                "NIKON Z 8", "NIKON Z 9", "NIKON Z 6_3", "NIKON Z f", "NIKON D850"
        });

        CAMERA_DATA.put("Fujifilm", new String[]{
                "X-T5", "X100VI", "X-S20", "X-T30 II", "GFX100 II"
        });

        CAMERA_DATA.put("Apple", new String[]{
                "iPhone 16 Pro Max", "iPhone 16 Pro", "iPhone 15 Pro Max", "iPhone 14 Pro Max"
        });

        CAMERA_DATA.put("Ricoh", new String[]{
                "RICOH GR IIIx", "RICOH GR III"
        });
    }

    public static final String[] COMMON_LENSES = {
            "",
            "Leica Summilux 23mm f/1.6",
            "Hasselblad Main Camera 1-inch",
            "Zeiss 1-inch 23mm Main Camera",
            "Huawei XMAGE Ultra Aperture Camera",
            "RF 50mm F1.2L USM",
            "RF 24-70mm F2.8L IS USM",
            "FE 24-70mm F2.8 GM II",
            "FE 85mm F1.4 GM",
            "NIKKOR Z 24-70mm f/2.8 S",
            "XF 35mm F1.4 R"
    };

    public static Map<String, String[]> getAllCameras() {
        return Collections.unmodifiableMap(CAMERA_DATA);
    }

    public static String[] getMakes() {
        return CAMERA_DATA.keySet().toArray(new String[0]);
    }

    public static String[] getModelsForMake(String make) {
        String matchedKey = matchMakeIgnoreCase(make);
        if (matchedKey != null && CAMERA_DATA.containsKey(matchedKey)) {
            return CAMERA_DATA.get(matchedKey);
        }
        return new String[]{""};
    }

    /**
     * Tìm tên hãng chuẩn trong danh sách bất kể chữ hoa/thường trong EXIF
     * Ví dụ: "SONY" -> "Sony", "NIKON CORPORATION" -> "Nikon", "HUAWEI" -> "Huawei"
     */
    public static String matchMakeIgnoreCase(String rawMake) {
        if (rawMake == null) return "";
        String cleaned = rawMake.trim();
        if (cleaned.isEmpty()) return "";

        for (String key : CAMERA_DATA.keySet()) {
            if (key.isEmpty()) continue;
            if (key.equalsIgnoreCase(cleaned) || cleaned.toUpperCase().contains(key.toUpperCase())) {
                return key;
            }
        }
        return null;
    }
}