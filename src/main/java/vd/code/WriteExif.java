package vd.code;

import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.common.RationalNumber;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata;
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants;
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;

import javax.swing.*;
import java.io.*;
import java.util.Scanner;

public class WriteExif {

    public static void main(String[] args) throws Exception {
        // 1. Chọn ảnh nguồn
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Chọn ảnh cần ghi EXIF");
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return;
        File src = chooser.getSelectedFile();

        // 2. Nhập thông số
        Scanner sc = new Scanner(System.in);
        System.out.print("Hãng máy: ");                 String make  = sc.nextLine();
        System.out.print("Model máy: ");                String model = sc.nextLine();
        System.out.print("Khẩu độ (VD 2.8): ");         double f     = sc.nextDouble();
        System.out.print("ISO (VD 100): ");             int iso      = sc.nextInt();
        System.out.print("Tử số màn trập (VD 1): ");    int tu       = sc.nextInt();
        System.out.print("Mẫu số màn trập (VD 250): "); int mau      = sc.nextInt();
        System.out.print("Tiêu cự mm (VD 50): ");       double focal = sc.nextDouble();
        sc.nextLine();
        System.out.print("Thời gian chụp (yyyy:MM:dd HH:mm:ss): ");
        String dt = sc.nextLine();

        // 3. Lấy EXIF hiện có để GIỮ các thẻ khác không liên quan
        TiffOutputSet outputSet = null;
        ImageMetadata meta = Imaging.getMetadata(src);
        if (meta instanceof JpegImageMetadata jpegMeta) {
            TiffImageMetadata tiff = jpegMeta.getExif();
            if (tiff != null) outputSet = tiff.getOutputSet();
        }
        if (outputSet == null) outputSet = new TiffOutputSet();

        // 4a. Make/Model nằm ở thư mục gốc IFD0
        var root = outputSet.getOrCreateRootDirectory();
        root.removeField(TiffTagConstants.TIFF_TAG_MAKE);
        root.add(TiffTagConstants.TIFF_TAG_MAKE, make);
        root.removeField(TiffTagConstants.TIFF_TAG_MODEL);
        root.add(TiffTagConstants.TIFF_TAG_MODEL, model);

        // 4b. Các thông số chụp nằm ở Exif SubIFD
        var exif = outputSet.getOrCreateExifDirectory();
        exif.removeField(ExifTagConstants.EXIF_TAG_FNUMBER);
        exif.add(ExifTagConstants.EXIF_TAG_FNUMBER, RationalNumber.valueOf(f));

        exif.removeField(ExifTagConstants.EXIF_TAG_ISO);
        exif.add(ExifTagConstants.EXIF_TAG_ISO, (short) iso);

        exif.removeField(ExifTagConstants.EXIF_TAG_EXPOSURE_TIME);
        exif.add(ExifTagConstants.EXIF_TAG_EXPOSURE_TIME, new RationalNumber(tu, mau));

        exif.removeField(ExifTagConstants.EXIF_TAG_FOCAL_LENGTH_IN_35MM_FORMAT);
        exif.add(ExifTagConstants.EXIF_TAG_FOCAL_LENGTH_IN_35MM_FORMAT, (short) focal);



        // 5. Ghi ra file MỚI (không đè file gốc), chế độ lossless
        File dst = new File(src.getParent(), "EXIF_" + src.getName());
        try (OutputStream os = new BufferedOutputStream(new FileOutputStream(dst))) {
            new ExifRewriter().updateExifMetadataLossless(src, os, outputSet);
        }
        System.out.println("Đã ghi xong: " + dst.getAbsolutePath());
    }
}
