package vd.code;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

public class FrameRenderer {

    public enum FrameStyle {
        NONE("Không viền (Ảnh gốc)"),
        OPPO_HASSELBLAD("Khung OPPO × Hasselblad (Giống ảnh mẫu của bạn)"),
        VIVO_ZEISS("Khung Vivo × ZEISS (Thanh lịch)"),
        XIAOMI_LEICA("Khung Xiaomi × Leica (Chấm đỏ cổ điển)"),
        DARK_PRO("Khung Đen Cinema (Chữ trắng nổi bật)");

        private final String displayName;

        FrameStyle(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    public static class FrameMetadata {
        public String make;
        public String model;
        public String lens;
        public String fNumber;
        public String shutter;
        public String iso;
        public String focal;
        public String dateTime;

        public String BuildSpecsLine() {
            StringBuilder sb = new StringBuilder();
            if (focal != null && !focal.isEmpty()) sb.append(focal).append("mm  ");
            if (fNumber != null && !fNumber.isEmpty()) sb.append("f/").append(fNumber).append("  ");
            if (shutter != null && !shutter.isEmpty()) sb.append(shutter).append("s  ");
            if (iso != null && !iso.isEmpty()) sb.append("ISO").append(iso);
            return sb.toString().trim();
        }
    }

    public static BufferedImage renderFramedImage(BufferedImage src, FrameStyle style, FrameMetadata meta) {
        if (src == null || style == FrameStyle.NONE) {
            return src;
        }

        return switch (style) {
            case OPPO_HASSELBLAD -> renderOppoHasselbladCard(src, meta);
            case VIVO_ZEISS -> renderBottomBarStyle(src, meta, Color.WHITE, new Color(25, 25, 25), "ZEISS");
            case XIAOMI_LEICA -> renderBottomBarStyle(src, meta, Color.WHITE, new Color(25, 25, 25), "LEICA");
            case DARK_PRO -> renderBottomBarStyle(src, meta, new Color(20, 22, 25), new Color(240, 240, 240), "PRO");
            default -> src;
        };
    }

    /**
     * Kiểu 1: Khung viền 4 cạnh trắng kiểu OPPO x Hasselblad
     * - Viền trên: Chữ HASSELBLAD in hoa giãn cách rộng ở chính giữa
     * - Viền dưới: Dòng 1 là tên máy (VD: OPPO Find X8 Ultra), dòng 2 là thông số chụp
     */
    private static BufferedImage renderOppoHasselbladCard(BufferedImage src, FrameMetadata meta) {
        int w = src.getWidth();
        int h = src.getHeight();
        int ref = Math.max(w, h);

        int sidePad = Math.max(24, (int) (ref * 0.045));
        int topPad = Math.max(50, (int) (ref * 0.095));
        int bottomPad = Math.max(70, (int) (ref * 0.135));

        int totalW = w + sidePad * 2;
        int totalH = h + topPad + bottomPad;

        BufferedImage out = new BufferedImage(totalW, totalH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = out.createGraphics();
        enableHighQuality(g2);

        // 1. Đổ nền trắng toàn khung
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, totalW, totalH);

        // 2. Vẽ ảnh gốc vào giữa
        g2.drawImage(src, sidePad, topPad, null);

        // 3. Vẽ chữ "H A S S E L B L A D" ở chính giữa viền trên
        g2.setColor(new Color(30, 30, 30));
        int brandFontSize = Math.max(14, (int) (topPad * 0.24));
        g2.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, brandFontSize));
        String topBrand = "H A S S E L B L A D";
        drawCenteredString(g2, topBrand, totalW / 2, topPad / 2 + brandFontSize / 3);

        // 4. Vẽ Tên Model máy ở nửa trên của viền đáy
        String modelTitle = (meta.model != null && !meta.model.isEmpty()) ? meta.model : "OPPO Flagship Camera";
        int modelFontSize = Math.max(15, (int) (bottomPad * 0.20));
        g2.setFont(new Font("SansSerif", Font.BOLD, modelFontSize));
        g2.setColor(new Color(20, 20, 20));
        int bottomCenterY = topPad + h + (bottomPad / 2);
        drawCenteredString(g2, modelTitle, totalW / 2, bottomCenterY - (int) (bottomPad * 0.08));

        // 5. Vẽ dòng thông số chụp ở dưới tên máy
        String specs = meta.BuildSpecsLine();
        if (specs.isEmpty()) specs = "23mm   f/1.8   1/250s   ISO100";
        int specFontSize = Math.max(12, (int) (bottomPad * 0.145));
        g2.setFont(new Font("SansSerif", Font.PLAIN, specFontSize));
        g2.setColor(new Color(110, 110, 110));
        drawCenteredString(g2, specs, totalW / 2, bottomCenterY + (int) (bottomPad * 0.20));

        g2.dispose();
        return out;
    }

    /**
     * Kiểu 2: Dải viền dưới chân ảnh kiểu Vivo ZEISS / Xiaomi Leica / Dark Mode
     * - Bên trái: Tên Model máy + Ngày giờ chụp
     * - Bên phải: Huy hiệu thương hiệu (ZEISS xanh hoặc Chấm đỏ Leica) + Thông số tiêu cự, khẩu, tốc, ISO
     */
    private static BufferedImage renderBottomBarStyle(BufferedImage src, FrameMetadata meta,
                                                      Color bgColor, Color textColor, String badgeType) {
        int w = src.getWidth();
        int h = src.getHeight();
        int barHeight = Math.max(65, (int) (Math.max(w, h) * 0.105));
        int padX = Math.max(24, (int) (w * 0.04));

        BufferedImage out = new BufferedImage(w, h + barHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = out.createGraphics();
        enableHighQuality(g2);

        // 1. Vẽ ảnh gốc ở trên và dải màu ở dưới
        g2.drawImage(src, 0, 0, null);
        g2.setColor(bgColor);
        g2.fillRect(0, h, w, barHeight);

        int titleFontSize = Math.max(14, (int) (barHeight * 0.25));
        int subFontSize = Math.max(11, (int) (barHeight * 0.18));

        int row1Y = h + (int) (barHeight * 0.44);
        int row2Y = h + (int) (barHeight * 0.76);

        // 2. Góc trái: Tên Model & Ống kính
        String modelText = (meta.model != null && !meta.model.isEmpty()) ? meta.model : "Flagship Camera";
        g2.setFont(new Font("SansSerif", Font.BOLD, titleFontSize));
        g2.setColor(textColor);
        g2.drawString(modelText, padX, row1Y);

        String subLeftText = (meta.lens != null && !meta.lens.isEmpty()) ? meta.lens : meta.dateTime;
        if (subLeftText == null || subLeftText.isEmpty()) subLeftText = " Master Photography";
        g2.setFont(new Font("SansSerif", Font.PLAIN, subFontSize));
        g2.setColor(new Color(130, 130, 130));
        g2.drawString(subLeftText, padX, row2Y);

        // 3. Góc phải: Thông số chụp + Ngày chụp
        String specs = meta.BuildSpecsLine();
        if (specs.isEmpty()) specs = "23mm  f/1.8  1/250s  ISO100";
        g2.setFont(new Font("SansSerif", Font.BOLD, (int) (titleFontSize * 0.92)));
        g2.setColor(textColor);
        FontMetrics fmSpecs = g2.getFontMetrics();
        int specsW = fmSpecs.stringWidth(specs);
        int rightEdge = w - padX;
        g2.drawString(specs, rightEdge - specsW, row1Y);

        String dateStr = (meta.dateTime != null && !meta.dateTime.isEmpty()) ? meta.dateTime : "";
        g2.setFont(new Font("SansSerif", Font.PLAIN, subFontSize));
        g2.setColor(new Color(130, 130, 130));
        FontMetrics fmDate = g2.getFontMetrics();
        g2.drawString(dateStr, rightEdge - fmDate.stringWidth(dateStr), row2Y);

        // 4. Vạch kẻ dọc phân cách & Logo huy hiệu (ZEISS / LEICA)
        int dividerX = rightEdge - specsW - (int) (barHeight * 0.35);
        g2.setColor(new Color(200, 200, 200));
        g2.setStroke(new BasicStroke(Math.max(1f, barHeight * 0.02f)));
        g2.drawLine(dividerX, h + (int) (barHeight * 0.25), dividerX, h + (int) (barHeight * 0.78));

        if ("ZEISS".equals(badgeType)) {
            int badgeSize = (int) (barHeight * 0.46);
            int badgeX = dividerX - badgeSize - (int) (barHeight * 0.25);
            int badgeY = h + (barHeight - badgeSize) / 2;
            g2.setColor(new Color(0, 72, 153)); // Xanh dương chuẩn ZEISS
            g2.fill(new RoundRectangle2D.Double(badgeX, badgeY, badgeSize, badgeSize, 6, 6));

            g2.setColor(Color.WHITE);
            g2.setFont(new Font("SansSerif", Font.BOLD, Math.max(9, (int) (badgeSize * 0.27))));
            drawCenteredString(g2, "ZEISS", badgeX + badgeSize / 2, badgeY + (int) (badgeSize * 0.60));
        } else if ("LEICA".equals(badgeType)) {
            int dotSize = (int) (barHeight * 0.44);
            int dotX = dividerX - dotSize - (int) (barHeight * 0.25);
            int dotY = h + (barHeight - dotSize) / 2;
            g2.setColor(new Color(220, 20, 30)); // Đỏ chuẩn Leica
            g2.fillOval(dotX, dotY, dotSize, dotSize);

            g2.setColor(Color.WHITE);
            g2.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, Math.max(9, (int) (dotSize * 0.34))));
            drawCenteredString(g2, "Leica", dotX + dotSize / 2, dotY + (int) (dotSize * 0.62));
        }

        g2.dispose();
        return out;
    }

    private static void drawCenteredString(Graphics2D g2, String text, int centerX, int y) {
        FontMetrics fm = g2.getFontMetrics();
        int x = centerX - (fm.stringWidth(text) / 2);
        g2.drawString(text, x, y);
    }

    private static void enableHighQuality(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }
}