package vd.code;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;

import javax.swing.*;
import java.io.File;

public class ExifReader {

    public static void main(String[] args) {
        // Mở hộp thoại chọn file ảnh
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Chọn file ảnh để đọc thông số EXIF");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Ảnh (JPG, JPEG, PNG, TIFF, HEIC...)", "jpg", "jpeg", "png", "tiff", "tif", "heic", "webp"));

        int result = chooser.showOpenDialog(null);
        if (result != JFileChooser.APPROVE_OPTION) {
            System.out.println("Bạn đã hủy chọn file.");
            return;
        }

        File imageFile = chooser.getSelectedFile();
        readExif(imageFile);
    }

    private static void readExif(File file) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(file);
            System.out.println("===== THÔNG SỐ ẢNH: " + file.getName() + " =====\n");

            // Các thẻ quan trọng nhất (camera + thông số chụp)
            printTag(metadata, ExifIFD0Directory.class, ExifIFD0Directory.TAG_MAKE, "Hãng máy");
            printTag(metadata, ExifIFD0Directory.class, ExifIFD0Directory.TAG_MODEL, "Model máy");
            printTag(metadata, ExifSubIFDDirectory.class, ExifSubIFDDirectory.TAG_FNUMBER, "Khẩu độ (F)");
            printTag(metadata, ExifSubIFDDirectory.class, ExifSubIFDDirectory.TAG_ISO_EQUIVALENT, "ISO");
            printTag(metadata, ExifSubIFDDirectory.class, ExifSubIFDDirectory.TAG_EXPOSURE_TIME, "Tốc độ màn trập");
            printTag(metadata, ExifSubIFDDirectory.class, ExifSubIFDDirectory.TAG_35MM_FILM_EQUIV_FOCAL_LENGTH, "Tiêu cự");
            printTag(metadata, ExifIFD0Directory.class, ExifIFD0Directory.TAG_DATETIME, "Thời gian chụp");

            // Hiển thị chi tiết khẩu độ dạng f/2.8
            ExifSubIFDDirectory sub = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            if (sub != null && sub.containsTag(ExifSubIFDDirectory.TAG_FOCAL_LENGTH)) {
                double focal = sub.getDouble(ExifSubIFDDirectory.TAG_FOCAL_LENGTH);
                System.out.printf("%-20s: %.2f mm%n", "Tiêu cự (lens thật)", focal);
            }
            if (sub != null && sub.containsTag(ExifSubIFDDirectory.TAG_FNUMBER)) {
                double f = sub.getDouble(ExifSubIFDDirectory.TAG_FNUMBER);
                System.out.printf("%-20s: f/%.1f%n", "Khẩu độ (chi tiết)", f);
            }

            System.out.println("\n===== TOÀN BỘ METADATA =====");
            for (Directory dir : metadata.getDirectories()) {
                System.out.println("\n--- " + dir.getName() + " ---");
                for (Tag tag : dir.getTags()) {
                    System.out.printf("%-40s: %s%n", tag.getTagName(), tag.getDescription());
                }
            }

        } catch (Exception e) {
            JOptionPane.showMessageDialog(null,
                    "Không đọc được file: " + e.getMessage(),
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static void printTag(Metadata metadata, Class<? extends Directory> dirClass, int tagType, String label) {
        Directory dir = metadata.getFirstDirectoryOfType(dirClass);
        if (dir != null && dir.containsTag(tagType)) {
            System.out.printf("%-20s: %s%n", label, dir.getString(tagType));
        } else {
            System.out.printf("%-20s: (không có)%n", label);
        }
    }
}