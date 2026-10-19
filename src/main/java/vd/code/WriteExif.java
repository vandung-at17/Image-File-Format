package vd.code;

import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.common.RationalNumber;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.jpeg.exif.ExifRewriter;
import org.apache.commons.imaging.formats.tiff.TiffField;
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata;
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants;
import org.apache.commons.imaging.formats.tiff.constants.TiffTagConstants;
import org.apache.commons.imaging.formats.tiff.write.TiffOutputSet;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WriteExif extends JFrame {

    // Danh sách quản lý ảnh và thẻ Thumbnail
    private final List<File> imageFiles = new ArrayList<>();
    private final List<JPanel> thumbnailCards = new ArrayList<>();
    private int selectedIndex = -1;
    private boolean isUpdatingMakeCombo = false;

    // Bộ nhớ tạm phục vụ tính năng Copy / Paste EXIF giữa các ảnh
    private ExifFormData exifClipboard = null;

    // Viền xanh cho ảnh đang được chọn trong danh sách dưới
    private static final Color SELECTED_BORDER_COLOR = new Color(0, 150, 255);
    private static final Border BORDER_SELECTED = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(SELECTED_BORDER_COLOR, 3),
            BorderFactory.createEmptyBorder(2, 2, 2, 2)
    );
    private static final Border BORDER_NORMAL = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
            BorderFactory.createEmptyBorder(4, 4, 4, 4)
    );

    // Các thành phần bên trái (Preview + Zoom + Thumbnail List)
    private final ZoomableImagePanel previewCanvas = new ZoomableImagePanel();
    private final JPanel thumbnailListPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
    private final JLabel lblZoomPercent = new JLabel("100%", SwingConstants.CENTER);
    private final JLabel lblImageInfo = new JLabel("Chưa chọn ảnh (Có thể kéo thả ảnh trực tiếp vào đây)");
    private final JLabel lblListCount = new JLabel("Tổng: 0 ảnh");

    // Các thành phần bên phải (Form nhập thông số)
    private final JLabel lblExifStatus = new JLabel("Hãy chọn hoặc kéo thả ảnh vào để bắt đầu...");
    private final JTextField txtFilePath = new JTextField();
    private final JTextField txtNewFileName = new JTextField();

    private static final Map<String, String[]> CAMERA_DATA = new LinkedHashMap<>();
    static {
        CAMERA_DATA.put("", new String[]{""});
        CAMERA_DATA.put("Canon", new String[]{
                "Canon EOS R5", "Canon EOS R6 Mark II", "Canon EOS RP", "Canon EOS 5D Mark IV", "Canon EOS 6D Mark II"
        });
        CAMERA_DATA.put("Sony", new String[]{
                "ILCE-7M4", "ILCE-7RM5", "ILCE-7SM3", "ILCE-7C", "ZV-E10"
        });
        CAMERA_DATA.put("Nikon", new String[]{
                "NIKON Z 8", "NIKON Z 6_2", "NIKON Z f", "NIKON D850", "NIKON D750"
        });
        CAMERA_DATA.put("Fujifilm", new String[]{
                "X-T5", "X-S20", "X100VI", "X-T30 II", "GFX100 II"
        });
        CAMERA_DATA.put("OPPO", new String[]{
                "OPPO Find X8 Ultra", "OPPO Find X8 Pro", "OPPO Find X7 Ultra"
        });
        CAMERA_DATA.put("Apple", new String[]{
                "iPhone 16 Pro Max", "iPhone 15 Pro Max", "iPhone 14 Pro Max", "iPhone 13 Pro"
        });
        CAMERA_DATA.put("Ricoh", new String[]{
                "RICOH GR III", "RICOH GR IIIx"
        });
    }

    private static final String[] COMMON_LENSES = {
            "",
            "RF 50mm F1.2L USM",
            "RF 24-70mm F2.8L IS USM",
            "FE 24-70mm F2.8 GM II",
            "FE 85mm F1.4 GM",
            "NIKKOR Z 24-70mm f/2.8 S",
            "XF 35mm F1.4 R",
            "Hasselblad Mobile Camera"
    };

    private final JComboBox<String> cbMake;
    private final JComboBox<String> cbModel;
    private final JComboBox<String> cbLens = new JComboBox<>(COMMON_LENSES);
    private final JTextField txtFNumber = new JTextField("");
    private final JTextField txtIso = new JTextField("");
    private final JTextField txtShutterNum = new JTextField("", 4);
    private final JTextField txtShutterDen = new JTextField("", 5);
    private final JTextField txtFocal = new JTextField("");

    // Bộ chọn Ngày & Giờ + Tăng giây tự động khi lưu hàng loạt
    private LocalDate selectedDate = LocalDate.now();
    private final JTextField txtDate = new JTextField(9);
    private final JSpinner timeSpinner;
    private final JSpinner spnStepSeconds = new JSpinner(new SpinnerNumberModel(5, 0, 3600, 1));

    private final JCheckBox chkBackup = new JCheckBox("Tạo bản sao lưu (_original_backup) trước khi ghi đè", true);
    private final JProgressBar progressBar = new JProgressBar(0, 100);

    private final JButton btnPasteExif = new JButton("📥 Paste EXIF");
    private final JButton btnSaveSingle = new JButton("💾 Lưu ảnh đang chọn");
    private final JButton btnSaveAll = new JButton("⚡ Áp dụng cho TẤT CẢ ảnh");
    private final JButton btnStripExif = new JButton("🧹 Xóa sạch EXIF ảnh này");

    public WriteExif() {
        setTitle("Chỉnh sửa thông số Image (EXIF Editor Pro)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1100, 700);
        setLocationRelativeTo(null);

        JPanel rootPanel = new JPanel(new BorderLayout(12, 12));
        rootPanel.setBorder(new EmptyBorder(10, 12, 10, 12));

        // =========================================================================
        // NỬA BÊN TRÁI: KHUNG PREVIEW CÓ ZOOM (TRÊN) + LIST THUMBNAIL (DƯỚI)
        // =========================================================================
        JPanel leftContainer = new JPanel(new BorderLayout(0, 8));

        JPanel mainPreviewWrapper = new JPanel(new BorderLayout());
        mainPreviewWrapper.setBorder(new TitledBorder("Xem trước ảnh (Lăn chuột để Zoom, kéo chuột để di chuyển)"));

        JPanel zoomToolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 3));
        JButton btnZoomOut = new JButton("➖");
        JButton btnZoomIn = new JButton("➕");
        JButton btnZoomFit = new JButton("🔄 Vừa khung");

        lblZoomPercent.setPreferredSize(new Dimension(52, 24));
        lblZoomPercent.setFont(new Font("SansSerif", Font.BOLD, 12));

        btnZoomIn.addActionListener(e -> previewCanvas.zoomBy(1.25));
        btnZoomOut.addActionListener(e -> previewCanvas.zoomBy(0.8));
        btnZoomFit.addActionListener(e -> previewCanvas.resetZoom());

        zoomToolbar.add(new JLabel("Zoom:"));
        zoomToolbar.add(lblZoomPercent);
        zoomToolbar.add(btnZoomOut);
        zoomToolbar.add(btnZoomIn);
        zoomToolbar.add(btnZoomFit);

        JPanel bottomInfoBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 3));
        lblImageInfo.setFont(new Font("SansSerif", Font.PLAIN, 12));
        lblImageInfo.setForeground(new Color(70, 70, 70));
        bottomInfoBar.add(lblImageInfo);

        mainPreviewWrapper.add(zoomToolbar, BorderLayout.NORTH);
        mainPreviewWrapper.add(previewCanvas, BorderLayout.CENTER);
        mainPreviewWrapper.add(bottomInfoBar, BorderLayout.SOUTH);

        // Thanh List Preview bên dưới
        JPanel bottomListWrapper = new JPanel(new BorderLayout(0, 4));
        bottomListWrapper.setBorder(new TitledBorder("Danh sách ảnh (Click để chọn — Phím Delete để bỏ)"));
        bottomListWrapper.setPreferredSize(new Dimension(0, 165));

        JPanel listControlBar = new JPanel(new BorderLayout());
        listControlBar.setBorder(new EmptyBorder(0, 6, 2, 6));
        lblListCount.setFont(new Font("SansSerif", Font.BOLD, 12));

        JPanel listButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton btnAddMore = new JButton("➕ Thêm ảnh");
        JButton btnRemoveSelected = new JButton("🗑️ Bỏ ảnh đang chọn");
        JButton btnClearAll = new JButton("🧹 Xóa hết list");

        btnAddMore.addActionListener(e -> chooseMultipleFiles(true));
        btnRemoveSelected.addActionListener(e -> removeSelectedImage());
        btnClearAll.addActionListener(e -> clearAllImages());

        listButtons.add(btnAddMore);
        listButtons.add(btnRemoveSelected);
        listButtons.add(btnClearAll);

        listControlBar.add(lblListCount, BorderLayout.WEST);
        listControlBar.add(listButtons, BorderLayout.EAST);

        thumbnailListPanel.setBackground(new Color(245, 247, 250));
        JScrollPane thumbScroll = new JScrollPane(
                thumbnailListPanel,
                JScrollPane.VERTICAL_SCROLLBAR_NEVER,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        );
        thumbScroll.getHorizontalScrollBar().setUnitIncrement(20);

        bottomListWrapper.add(listControlBar, BorderLayout.NORTH);
        bottomListWrapper.add(thumbScroll, BorderLayout.CENTER);

        leftContainer.add(mainPreviewWrapper, BorderLayout.CENTER);
        leftContainer.add(bottomListWrapper, BorderLayout.SOUTH);

        // =========================================================================
        // NỬA BÊN PHẢI: FORM NHẬP THÔNG SỐ EXIF
        // =========================================================================
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(new TitledBorder("Thông số chỉnh sửa EXIF"));
        formPanel.setPreferredSize(new Dimension(465, 0));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        lblExifStatus.setFont(new Font("SansSerif", Font.ITALIC, 12));
        lblExifStatus.setForeground(new Color(0, 102, 204));
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        formPanel.add(lblExifStatus, gbc);

        JPanel quickActionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        JButton btnCopyExif = new JButton("📋 Copy EXIF");
        btnCopyExif.addActionListener(e -> copyExifToClipboard());

        btnPasteExif.setEnabled(false);
        btnPasteExif.addActionListener(e -> pasteExifFromClipboard());

        JButton btnFillPreset = new JButton("✨ Điền mẫu R5");
        btnFillPreset.addActionListener(e -> fillSamplePreset());

        quickActionsPanel.add(btnCopyExif);
        quickActionsPanel.add(btnPasteExif);
        quickActionsPanel.add(btnFillPreset);

        gbc.gridy = 1;
        formPanel.add(quickActionsPanel, gbc);

        txtFilePath.setEditable(false);
        JButton btnBrowse = new JButton("📂 Chọn ảnh...");
        btnBrowse.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnBrowse.addActionListener(e -> chooseMultipleFiles(false));

        JPanel filePanel = new JPanel(new BorderLayout(5, 0));
        filePanel.add(txtFilePath, BorderLayout.CENTER);
        filePanel.add(btnBrowse, BorderLayout.EAST);
        addRow(formPanel, gbc, 2, "Ảnh đang chọn:", filePanel);

        addRow(formPanel, gbc, 3, "Tên file lưu:", txtNewFileName);

        String[] makes = CAMERA_DATA.keySet().toArray(new String[0]);
        cbMake = new JComboBox<>(makes);
        cbModel = new JComboBox<>(CAMERA_DATA.get(makes[0]));
        cbMake.setEditable(true);
        cbModel.setEditable(true);
        cbLens.setEditable(true);

        cbMake.addActionListener(e -> {
            if (isUpdatingMakeCombo) return;
            String make = String.valueOf(cbMake.getSelectedItem()).trim();
            if (CAMERA_DATA.containsKey(make)) {
                cbModel.setModel(new DefaultComboBoxModel<>(CAMERA_DATA.get(make)));
            }
        });

        addRow(formPanel, gbc, 4, "Hãng máy (Make):", cbMake);
        addRow(formPanel, gbc, 5, "Model máy:", cbModel);
        addRow(formPanel, gbc, 6, "Ống kính (Lens):", cbLens);

        addRow(formPanel, gbc, 7, "Khẩu độ (f/):", txtFNumber);
        addRow(formPanel, gbc, 8, "ISO:", txtIso);

        JPanel shutterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        shutterPanel.add(txtShutterNum);
        shutterPanel.add(new JLabel("  /  "));
        shutterPanel.add(txtShutterDen);
        shutterPanel.add(new JLabel(" giây"));
        addRow(formPanel, gbc, 9, "Tốc độ màn trập:", shutterPanel);

        addRow(formPanel, gbc, 10, "Tiêu cự (mm):", txtFocal);

        txtDate.setEditable(false);
        txtDate.setHorizontalAlignment(JTextField.CENTER);
        updateDateTextField();

        JButton btnCalendar = new JButton("📅 Lịch");
        btnCalendar.addActionListener(e -> {
            CalendarDialog dialog = new CalendarDialog(this, selectedDate);
            LocalDate picked = dialog.showDialog();
            if (picked != null) {
                selectedDate = picked;
                updateDateTextField();
            }
        });

        SpinnerDateModel timeModel = new SpinnerDateModel(new Date(), null, null, Calendar.SECOND);
        timeSpinner = new JSpinner(timeModel);
        timeSpinner.setEditor(new JSpinner.DateEditor(timeSpinner, "HH:mm:ss"));

        JPanel dateTimePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        dateTimePanel.add(txtDate);
        dateTimePanel.add(btnCalendar);
        dateTimePanel.add(new JLabel(" Giờ:"));
        dateTimePanel.add(timeSpinner);
        addRow(formPanel, gbc, 11, "Ngày giờ chụp:", dateTimePanel);

        JPanel stepTimePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        stepTimePanel.add(spnStepSeconds);
        stepTimePanel.add(new JLabel("giây / mỗi ảnh tiếp theo"));
        addRow(formPanel, gbc, 12, "Tăng giờ hàng loạt:", stepTimePanel);

        gbc.gridx = 0;
        gbc.gridy = 13;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(6, 6, 2, 6);
        formPanel.add(chkBackup, gbc);

        progressBar.setStringPainted(true);
        progressBar.setString("Sẵn sàng");
        gbc.gridy = 14;
        gbc.insets = new Insets(4, 6, 6, 6);
        formPanel.add(progressBar, gbc);

        btnSaveSingle.setFont(new Font("SansSerif", Font.BOLD, 13));
        btnSaveSingle.addActionListener(e -> saveCurrentImage());

        btnSaveAll.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnSaveAll.addActionListener(e -> saveAllImagesInBackground());

        btnStripExif.setForeground(new Color(180, 40, 40));
        btnStripExif.addActionListener(e -> stripExifCurrentImage());

        JPanel buttonPanel = new JPanel(new GridLayout(3, 1, 0, 6));
        buttonPanel.add(btnSaveSingle);
        buttonPanel.add(btnSaveAll);
        buttonPanel.add(btnStripExif);

        gbc.gridy = 15;
        gbc.insets = new Insets(4, 6, 6, 6);
        formPanel.add(buttonPanel, gbc);

        setupDragAndDrop(previewCanvas);
        setupDragAndDrop(thumbnailListPanel);

        rootPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "removeSelectedImage");
        rootPanel.getActionMap().put("removeSelectedImage", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (!isTypingInTextField()) {
                    removeSelectedImage();
                }
            }
        });

        rootPanel.add(leftContainer, BorderLayout.CENTER);
        rootPanel.add(formPanel, BorderLayout.EAST);
        add(rootPanel);
    }

    private boolean isTypingInTextField() {
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        return focusOwner instanceof JTextField;
    }

    private void setupDragAndDrop(Component targetComp) {
        new DropTarget(targetComp, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
            @Override
            @SuppressWarnings("unchecked")
            public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    Object transferData = dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (transferData instanceof List<?> droppedList) {
                        List<File> validJpegs = new ArrayList<>();
                        for (Object obj : droppedList) {
                            if (obj instanceof File f && f.isFile()) {
                                String name = f.getName().toLowerCase();
                                if ((name.endsWith(".jpg") || name.endsWith(".jpeg")) && !imageFiles.contains(f)) {
                                    imageFiles.add(f);
                                    validJpegs.add(f);
                                }
                            }
                        }
                        if (!validJpegs.isEmpty()) {
                            loadThumbnailsInBackground(validJpegs, selectedIndex < 0);
                        }
                    }
                    dtde.dropComplete(true);
                } catch (Exception ex) {
                    dtde.dropComplete(false);
                }
            }
        }, true);
    }

    private void copyExifToClipboard() {
        ExifFormData data = validateAndReadForm(false);
        if (data == null) return;
        exifClipboard = data;
        btnPasteExif.setEnabled(true);
        lblExifStatus.setText("📋 Đã Copy thông số EXIF vào bộ nhớ tạm!");
        lblExifStatus.setForeground(new Color(0, 128, 64));
    }

    private void pasteExifFromClipboard() {
        if (exifClipboard == null) return;
        resetFieldColors();

        isUpdatingMakeCombo = true;
        cbMake.setSelectedItem(exifClipboard.make);
        if (CAMERA_DATA.containsKey(exifClipboard.make)) {
            cbModel.setModel(new DefaultComboBoxModel<>(CAMERA_DATA.get(exifClipboard.make)));
        }
        cbModel.setSelectedItem(exifClipboard.model);
        isUpdatingMakeCombo = false;

        cbLens.setSelectedItem(exifClipboard.lens);
        txtFNumber.setText(trimTrailingZero(exifClipboard.fNumber));
        txtIso.setText(String.valueOf(exifClipboard.iso));
        txtShutterNum.setText(String.valueOf(exifClipboard.shutterNum));
        txtShutterDen.setText(String.valueOf(exifClipboard.shutterDen));
        txtFocal.setText(trimTrailingZero(exifClipboard.focal));

        Calendar cal = Calendar.getInstance();
        cal.setTime(exifClipboard.captureTimestamp);
        selectedDate = LocalDate.of(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH)
        );
        updateDateTextField();
        timeSpinner.setValue(exifClipboard.captureTimestamp);

        lblExifStatus.setText("📥 Đã dán thông số EXIF — Hãy bấm Lưu để áp dụng!");
        lblExifStatus.setForeground(new Color(0, 102, 204));
    }

    private void addRow(JPanel panel, GridBagConstraints gbc, int row, String label, Component comp) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 1;
        gbc.weightx = 0.30;
        panel.add(new JLabel(label), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.70;
        panel.add(comp, gbc);
    }

    private void updateDateTextField() {
        txtDate.setText(String.format("%04d-%02d-%02d",
                selectedDate.getYear(), selectedDate.getMonthValue(), selectedDate.getDayOfMonth()));
    }

    private void resetFormToEmpty() {
        resetFieldColors();
        isUpdatingMakeCombo = true;
        cbMake.setSelectedItem("");
        cbModel.setModel(new DefaultComboBoxModel<>(CAMERA_DATA.get("")));
        cbModel.setSelectedItem("");
        isUpdatingMakeCombo = false;

        cbLens.setSelectedItem("");
        txtFNumber.setText("");
        txtIso.setText("");
        txtShutterNum.setText("");
        txtShutterDen.setText("");
        txtFocal.setText("");

        selectedDate = LocalDate.now();
        updateDateTextField();
        timeSpinner.setValue(new Date());
    }

    private void fillSamplePreset() {
        resetFieldColors();
        isUpdatingMakeCombo = true;
        cbMake.setSelectedItem("Canon");
        cbModel.setModel(new DefaultComboBoxModel<>(CAMERA_DATA.get("Canon")));
        cbModel.setSelectedItem("Canon EOS R5");
        isUpdatingMakeCombo = false;

        cbLens.setSelectedItem("RF 50mm F1.2L USM");
        txtFNumber.setText("2.8");
        txtIso.setText("100");
        txtShutterNum.setText("1");
        txtShutterDen.setText("250");
        txtFocal.setText("50");

        lblExifStatus.setText("✨ Đã điền bộ thông số mẫu Canon R5");
        lblExifStatus.setForeground(new Color(0, 102, 204));
    }

    private void chooseMultipleFiles(boolean append) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Chọn một hoặc nhiều ảnh JPEG");
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new FileNameExtensionFilter("Ảnh JPEG (*.jpg, *.jpeg)", "jpg", "jpeg"));

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File[] files = chooser.getSelectedFiles();
            if (files == null || files.length == 0) return;

            if (!append) {
                imageFiles.clear();
                thumbnailListPanel.removeAll();
                thumbnailCards.clear();
                selectedIndex = -1;
            }

            List<File> newFiles = new ArrayList<>();
            for (File f : files) {
                if (!imageFiles.contains(f)) {
                    imageFiles.add(f);
                    newFiles.add(f);
                }
            }
            loadThumbnailsInBackground(newFiles, !append);
        }
    }

    private void loadThumbnailsInBackground(List<File> filesToLoad, boolean selectFirst) {
        setControlsEnabled(false);
        progressBar.setValue(0);
        progressBar.setString("Đang tải danh sách ảnh...");

        SwingWorker<Void, JPanel> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                int total = filesToLoad.size();
                for (int i = 0; i < total; i++) {
                    File file = filesToLoad.get(i);
                    JPanel card = createThumbnailCard(file);
                    publish(card);
                    int progress = (int) (((i + 1) * 100.0) / total);
                    setProgress(progress);
                }
                return null;
            }

            @Override
            protected void process(List<JPanel> chunks) {
                for (JPanel card : chunks) {
                    thumbnailCards.add(card);
                    thumbnailListPanel.add(card);
                }
                lblListCount.setText("Tổng: " + imageFiles.size() + " ảnh");
                thumbnailListPanel.revalidate();
                thumbnailListPanel.repaint();
            }

            @Override
            protected void done() {
                setControlsEnabled(true);
                progressBar.setValue(100);
                progressBar.setString("Đã tải xong " + imageFiles.size() + " ảnh");
                if (selectFirst && !imageFiles.isEmpty()) {
                    selectImageAt(0);
                } else if (selectedIndex < 0 && !imageFiles.isEmpty()) {
                    selectImageAt(0);
                }
            }
        };

        worker.addPropertyChangeListener(evt -> {
            if ("progress".equals(evt.getPropertyName())) {
                int val = (Integer) evt.getNewValue();
                progressBar.setValue(val);
                progressBar.setString("Đang nạp ảnh (" + val + "%)...");
            }
        });
        worker.execute();
    }

    private JPanel createThumbnailCard(File file) {
        JPanel card = new JPanel(new BorderLayout(0, 2));
        card.setPreferredSize(new Dimension(98, 96));
        card.setBackground(Color.WHITE);
        card.setBorder(BORDER_NORMAL);
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.setToolTipText(file.getName() + " (" + formatFileSize(file.length()) + ")");

        JLabel thumbImgLabel = new JLabel("", SwingConstants.CENTER);
        ImageIcon icon = createSubsampledThumbnail(file, 82, 64);
        if (icon != null) {
            thumbImgLabel.setIcon(icon);
        } else {
            thumbImgLabel.setText("Lỗi ảnh");
        }

        JLabel nameLabel = new JLabel(file.getName(), SwingConstants.CENTER);
        nameLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));

        card.add(thumbImgLabel, BorderLayout.CENTER);
        card.add(nameLabel, BorderLayout.SOUTH);

        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int idx = thumbnailCards.indexOf(card);
                if (idx >= 0) {
                    selectImageAt(idx);
                }
            }
        });
        return card;
    }

    private ImageIcon createSubsampledThumbnail(File file, int maxW, int maxH) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(file)) {
            if (iis == null) return null;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                int subsampling = Math.max(1, Math.min(width / (maxW * 2), height / (maxH * 2)));

                ImageReadParam param = reader.getDefaultReadParam();
                param.setSourceSubsampling(subsampling, subsampling, 0, 0);

                BufferedImage subsampled = reader.read(0, param);
                if (subsampled == null) return null;

                subsampled = applyExifOrientation(file, subsampled);

                double scale = Math.min((double) maxW / subsampled.getWidth(), (double) maxH / subsampled.getHeight());
                int w = Math.max(1, (int) (subsampled.getWidth() * scale));
                int h = Math.max(1, (int) (subsampled.getHeight() * scale));
                Image scaled = subsampled.getScaledInstance(w, h, Image.SCALE_SMOOTH);
                subsampled.flush();
                return new ImageIcon(scaled);
            } finally {
                reader.dispose();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private BufferedImage applyExifOrientation(File file, BufferedImage img) {
        if (img == null) return null;
        try {
            ImageMetadata meta = Imaging.getMetadata(file);
            if (meta instanceof JpegImageMetadata jpegMeta) {
                TiffField orientField = jpegMeta.findExifValueWithExactMatch(TiffTagConstants.TIFF_TAG_ORIENTATION);
                if (orientField != null) {
                    int orientation = orientField.getIntValue();
                    int w = img.getWidth();
                    int h = img.getHeight();
                    if (orientation == 6) {
                        BufferedImage rotated = new BufferedImage(h, w, img.getType() == 0 ? BufferedImage.TYPE_INT_RGB : img.getType());
                        Graphics2D g2d = rotated.createGraphics();
                        AffineTransform at = new AffineTransform();
                        at.translate(h, 0);
                        at.rotate(Math.PI / 2);
                        g2d.drawRenderedImage(img, at);
                        g2d.dispose();
                        img.flush();
                        return rotated;
                    } else if (orientation == 8) {
                        BufferedImage rotated = new BufferedImage(h, w, img.getType() == 0 ? BufferedImage.TYPE_INT_RGB : img.getType());
                        Graphics2D g2d = rotated.createGraphics();
                        AffineTransform at = new AffineTransform();
                        at.translate(0, w);
                        at.rotate(-Math.PI / 2);
                        g2d.drawRenderedImage(img, at);
                        g2d.dispose();
                        img.flush();
                        return rotated;
                    } else if (orientation == 3) {
                        BufferedImage rotated = new BufferedImage(w, h, img.getType() == 0 ? BufferedImage.TYPE_INT_RGB : img.getType());
                        Graphics2D g2d = rotated.createGraphics();
                        AffineTransform at = new AffineTransform();
                        at.translate(w, h);
                        at.rotate(Math.PI);
                        g2d.drawRenderedImage(img, at);
                        g2d.dispose();
                        img.flush();
                        return rotated;
                    }
                }
            }
        } catch (Exception ignored) {}
        return img;
    }

    private void removeSelectedImage() {
        if (selectedIndex < 0 || selectedIndex >= imageFiles.size()) return;
        imageFiles.remove(selectedIndex);
        JPanel removedCard = thumbnailCards.remove(selectedIndex);
        thumbnailListPanel.remove(removedCard);

        lblListCount.setText("Tổng: " + imageFiles.size() + " ảnh");
        thumbnailListPanel.revalidate();
        thumbnailListPanel.repaint();

        if (imageFiles.isEmpty()) {
            selectedIndex = -1;
            previewCanvas.setImage(null);
            txtFilePath.setText("");
            txtNewFileName.setText("");
            resetFormToEmpty();
            lblImageInfo.setText("Chưa chọn ảnh");
            lblExifStatus.setText("Danh sách trống.");
        } else {
            int nextIdx = Math.min(selectedIndex, imageFiles.size() - 1);
            selectImageAt(nextIdx);
        }
    }

    private void clearAllImages() {
        if (imageFiles.isEmpty()) return;
        imageFiles.clear();
        thumbnailCards.clear();
        thumbnailListPanel.removeAll();
        selectedIndex = -1;

        previewCanvas.setImage(null);
        txtFilePath.setText("");
        txtNewFileName.setText("");
        resetFormToEmpty();
        lblImageInfo.setText("Chưa chọn ảnh");
        lblListCount.setText("Tổng: 0 ảnh");
        lblExifStatus.setText("Đã xóa danh sách ảnh.");

        thumbnailListPanel.revalidate();
        thumbnailListPanel.repaint();
    }

    private void selectImageAt(int index) {
        if (index < 0 || index >= imageFiles.size()) return;
        selectedIndex = index;

        for (int i = 0; i < thumbnailCards.size(); i++) {
            JPanel card = thumbnailCards.get(i);
            if (i == selectedIndex) {
                card.setBorder(BORDER_SELECTED);
                card.setBackground(new Color(232, 244, 255));
            } else {
                card.setBorder(BORDER_NORMAL);
                card.setBackground(Color.WHITE);
            }
        }

        File currentFile = imageFiles.get(selectedIndex);
        txtFilePath.setText(currentFile.getAbsolutePath());

        String currentName = currentFile.getName();
        if (!currentName.startsWith("EXIF_")) {
            txtNewFileName.setText("EXIF_" + currentName);
        } else {
            txtNewFileName.setText(currentName);
        }

        try {
            BufferedImage rawImg = ImageIO.read(currentFile);
            BufferedImage img = applyExifOrientation(currentFile, rawImg);
            previewCanvas.setImage(img);
            if (img != null) {
                lblImageInfo.setText(String.format("Độ phân giải: %d x %d px   |   Dung lượng: %s   |   File: %s",
                        img.getWidth(), img.getHeight(), formatFileSize(currentFile.length()), currentFile.getName()));
            } else {
                lblImageInfo.setText("Không đọc được kích thước ảnh");
            }
        } catch (Exception ex) {
            previewCanvas.setImage(null);
            lblImageInfo.setText("Lỗi đọc ảnh");
        }

        readExifAndPopulateForm(currentFile);
    }

    private void readExifAndPopulateForm(File file) {
        resetFormToEmpty();

        try {
            ImageMetadata meta = Imaging.getMetadata(file);
            if (!(meta instanceof JpegImageMetadata jpegMeta) || jpegMeta.getExif() == null) {
                lblExifStatus.setText("⚠️ Ảnh chưa có EXIF — Hãy nhập mới hoặc bấm 'Paste EXIF'");
                lblExifStatus.setForeground(new Color(180, 100, 0));
                return;
            }

            boolean foundAny = false;
            TiffImageMetadata tiffMeta = jpegMeta.getExif();

            // 1. Hãng máy, Model & Ống kính
            TiffField makeField = tiffMeta.findField(TiffTagConstants.TIFF_TAG_MAKE);
            if (makeField != null && makeField.getStringValue() != null) {
                String makeVal = makeField.getStringValue().trim();
                if (!makeVal.isEmpty()) {
                    isUpdatingMakeCombo = true;
                    cbMake.setSelectedItem(makeVal);
                    if (CAMERA_DATA.containsKey(makeVal)) {
                        cbModel.setModel(new DefaultComboBoxModel<>(CAMERA_DATA.get(makeVal)));
                    }
                    isUpdatingMakeCombo = false;
                    foundAny = true;
                }
            }

            TiffField modelField = tiffMeta.findField(TiffTagConstants.TIFF_TAG_MODEL);
            if (modelField != null && modelField.getStringValue() != null) {
                String modelVal = modelField.getStringValue().trim();
                if (!modelVal.isEmpty()) {
                    cbModel.setSelectedItem(modelVal);
                    foundAny = true;
                }
            }

            TiffField lensField = tiffMeta.findField(ExifTagConstants.EXIF_TAG_LENS_MODEL);
            if (lensField != null && lensField.getStringValue() != null) {
                String lensVal = lensField.getStringValue().trim();
                if (!lensVal.isEmpty()) {
                    cbLens.setSelectedItem(lensVal);
                    foundAny = true;
                }
            }

            // 2. Khẩu độ
            TiffField fField = tiffMeta.findField(ExifTagConstants.EXIF_TAG_FNUMBER);
            if (fField != null) {
                double fVal = fField.getDoubleValue();
                if (fVal > 0) {
                    txtFNumber.setText(trimTrailingZero(fVal));
                    foundAny = true;
                }
            }

            // 3. ISO
            TiffField isoField = tiffMeta.findField(ExifTagConstants.EXIF_TAG_ISO);
            if (isoField != null) {
                int isoVal = isoField.getIntValue();
                if (isoVal > 0) {
                    txtIso.setText(String.valueOf(isoVal));
                    foundAny = true;
                }
            }

            // 4. Tốc độ màn trập
            TiffField expField = tiffMeta.findField(ExifTagConstants.EXIF_TAG_EXPOSURE_TIME);
            if (expField != null) {
                Object val = expField.getValue();
                RationalNumber rn = null;
                if (val instanceof RationalNumber r) {
                    rn = r;
                } else if (val instanceof RationalNumber[] arr && arr.length > 0) {
                    rn = arr[0];
                }
                if (rn != null) {
                    long num = Math.abs(rn.numerator);
                    long den = Math.abs(rn.divisor);
                    if (num > 0 && den > 0) {
                        long g = gcd(num, den);
                        txtShutterNum.setText(String.valueOf(num / g));
                        txtShutterDen.setText(String.valueOf(den / g));
                        foundAny = true;
                    }
                }
            }

            // 5. Tiêu cự
            TiffField focalField = tiffMeta.findField(ExifTagConstants.EXIF_TAG_FOCAL_LENGTH);
            if (focalField == null) {
                focalField = tiffMeta.findField(ExifTagConstants.EXIF_TAG_FOCAL_LENGTH_IN_35MM_FORMAT);
            }
            if (focalField != null) {
                double focalVal = focalField.getDoubleValue();
                if (focalVal > 0) {
                    txtFocal.setText(trimTrailingZero(focalVal));
                    foundAny = true;
                }
            }

            // 6. Ngày giờ chụp
            TiffField dateField = tiffMeta.findField(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL);
            if (dateField == null) {
                dateField = tiffMeta.findField(TiffTagConstants.TIFF_TAG_DATE_TIME);
            }
            if (dateField != null && dateField.getStringValue() != null) {
                String rawDate = dateField.getStringValue().trim();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss");
                Date parsed = sdf.parse(rawDate);
                Calendar cal = Calendar.getInstance();
                cal.setTime(parsed);
                selectedDate = LocalDate.of(
                        cal.get(Calendar.YEAR),
                        cal.get(Calendar.MONTH) + 1,
                        cal.get(Calendar.DAY_OF_MONTH)
                );
                updateDateTextField();
                timeSpinner.setValue(parsed);
                foundAny = true;
            }

            if (foundAny) {
                lblExifStatus.setText("✅ Đã tự động đọc thông số EXIF gốc");
                lblExifStatus.setForeground(new Color(0, 128, 64));
            } else {
                lblExifStatus.setText("⚠️ Ảnh chưa có thông số chụp — Hãy nhập mới hoặc bấm 'Paste EXIF'");
                lblExifStatus.setForeground(new Color(180, 100, 0));
            }
        } catch (Exception e) {
            lblExifStatus.setText("⚠️ Không đọc được EXIF cũ");
            lblExifStatus.setForeground(new Color(180, 100, 0));
        }
    }

    private static class ExifFormData {
        String make;
        String model;
        String lens;
        double fNumber;
        int iso;
        int shutterNum;
        int shutterDen;
        double focal;
        Date captureTimestamp;

        public String getFormattedDateTime(int addSeconds) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(captureTimestamp);
            if (addSeconds != 0) {
                cal.add(Calendar.SECOND, addSeconds);
            }
            return new SimpleDateFormat("yyyy:MM:dd HH:mm:ss").format(cal.getTime());
        }
    }

    private void resetFieldColors() {
        Color normalBg = UIManager.getColor("TextField.background");
        txtNewFileName.setBackground(normalBg);
        txtFNumber.setBackground(normalBg);
        txtIso.setBackground(normalBg);
        txtShutterNum.setBackground(normalBg);
        txtShutterDen.setBackground(normalBg);
        txtFocal.setBackground(normalBg);
    }

    private ExifFormData validateAndReadForm(boolean checkSingleFileName) {
        resetFieldColors();
        Color errorBg = new Color(255, 225, 225);

        if (checkSingleFileName) {
            String newName = txtNewFileName.getText().trim();
            if (newName.isEmpty() || newName.matches(".*[\\\\/:*?\"<>|].*")) {
                txtNewFileName.setBackground(errorBg);
                txtNewFileName.requestFocus();
                JOptionPane.showMessageDialog(this,
                        "Tên file lưu không được để trống và không chứa ký tự đặc biệt (\\ / : * ? \" < > |)!",
                        "Lỗi tên file", JOptionPane.WARNING_MESSAGE);
                return null;
            }
        }

        ExifFormData data = new ExifFormData();
        Object makeObj = cbMake.getSelectedItem();
        Object modelObj = cbModel.getSelectedItem();
        Object lensObj = cbLens.getSelectedItem();
        data.make = makeObj != null ? makeObj.toString().trim() : "";
        data.model = modelObj != null ? modelObj.toString().trim() : "";
        data.lens = lensObj != null ? lensObj.toString().trim() : "";

        if (data.make.isEmpty() || data.model.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Hãng máy và Model máy đang để trống!\nHãy chọn từ danh sách, bấm 'Paste EXIF' hoặc 'Điền mẫu R5'.",
                    "Thiếu thông số", JOptionPane.WARNING_MESSAGE);
            return null;
        }

        try {
            data.fNumber = Double.parseDouble(txtFNumber.getText().trim().replace(',', '.'));
            if (data.fNumber <= 0 || data.fNumber > 128) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            txtFNumber.setBackground(errorBg);
            txtFNumber.requestFocus();
            JOptionPane.showMessageDialog(this, "Khẩu độ (f/) phải là số dương hợp lệ (VD: 1.4, 2.8, 8)!", "Lỗi nhập liệu", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        try {
            data.iso = Integer.parseInt(txtIso.getText().trim());
            if (data.iso <= 0 || data.iso > 65535) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            txtIso.setBackground(errorBg);
            txtIso.requestFocus();
            JOptionPane.showMessageDialog(this, "ISO phải là số nguyên từ 1 đến 65535 (VD: 100, 400, 1600)!", "Lỗi nhập liệu", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        try {
            data.shutterNum = Integer.parseInt(txtShutterNum.getText().trim());
            if (data.shutterNum <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            txtShutterNum.setBackground(errorBg);
            txtShutterNum.requestFocus();
            JOptionPane.showMessageDialog(this, "Tử số màn trập phải là số nguyên lớn hơn 0!", "Lỗi nhập liệu", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        try {
            data.shutterDen = Integer.parseInt(txtShutterDen.getText().trim());
            if (data.shutterDen <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            txtShutterDen.setBackground(errorBg);
            txtShutterDen.requestFocus();
            JOptionPane.showMessageDialog(this, "Mẫu số màn trập phải là số nguyên lớn hơn 0 (Không được nhập 0)!", "Lỗi chia cho 0", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        try {
            data.focal = Double.parseDouble(txtFocal.getText().trim().replace(',', '.'));
            if (data.focal <= 0 || data.focal > 2000) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            txtFocal.setBackground(errorBg);
            txtFocal.requestFocus();
            JOptionPane.showMessageDialog(this, "Tiêu cự (mm) phải là số dương hợp lệ (VD: 24, 35, 50, 85)!", "Lỗi nhập liệu", JOptionPane.ERROR_MESSAGE);
            return null;
        }

        Date timeVal = (Date) timeSpinner.getValue();
        Calendar timeCal = Calendar.getInstance();
        timeCal.setTime(timeVal);

        Calendar fullCal = Calendar.getInstance();
        fullCal.set(selectedDate.getYear(), selectedDate.getMonthValue() - 1, selectedDate.getDayOfMonth(),
                timeCal.get(Calendar.HOUR_OF_DAY), timeCal.get(Calendar.MINUTE), timeCal.get(Calendar.SECOND));
        data.captureTimestamp = fullCal.getTime();

        return data;
    }

    private void saveCurrentImage() {
        if (selectedIndex < 0 || selectedIndex >= imageFiles.size()) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn ảnh trước!", "Lỗi", JOptionPane.WARNING_MESSAGE);
            return;
        }

        ExifFormData formData = validateAndReadForm(true);
        if (formData == null) return;

        String newName = txtNewFileName.getText().trim();
        if (!newName.toLowerCase().endsWith(".jpg") && !newName.toLowerCase().endsWith(".jpeg")) {
            newName += ".jpg";
        }

        try {
            File currentFile = imageFiles.get(selectedIndex);
            File updatedFile = applyExifAndRename(currentFile, newName, formData, 0);

            imageFiles.set(selectedIndex, updatedFile);
            txtFilePath.setText(updatedFile.getAbsolutePath());

            JPanel card = thumbnailCards.get(selectedIndex);
            card.setToolTipText(updatedFile.getName() + " (" + formatFileSize(updatedFile.length()) + ")");
            ((JLabel) card.getComponent(1)).setText(updatedFile.getName());

            progressBar.setValue(100);
            progressBar.setString("Đã lưu: " + updatedFile.getName());
            lblExifStatus.setText("✅ Đã lưu thông số EXIF mới vào ảnh");
            lblExifStatus.setForeground(new Color(0, 128, 64));

            JOptionPane.showMessageDialog(this,
                    "Đã cập nhật EXIF và lưu thành công:\n" + updatedFile.getAbsolutePath(),
                    "Hoàn tất", JOptionPane.INFORMATION_MESSAGE);

        } catch (AccessDeniedException ex) {
            JOptionPane.showMessageDialog(this,
                    "Không thể ghi đè file vì ảnh đang được mở bởi phần mềm khác!\nHãy đóng trình xem ảnh bên ngoài rồi thử lại.\nFile: " + ex.getFile(),
                    "Lỗi khóa File (File Locked)", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Lỗi khi ghi EXIF: " + ex.getMessage(),
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void stripExifCurrentImage() {
        if (selectedIndex < 0 || selectedIndex >= imageFiles.size()) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn ảnh trước!", "Lỗi", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Bạn có chắc chắn muốn XÓA SẠCH toàn bộ thông số EXIF của bức ảnh này không?",
                "Xác nhận xóa EXIF", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        try {
            File currentFile = imageFiles.get(selectedIndex);
            if (chkBackup.isSelected()) {
                backupOriginalFile(currentFile);
            }

            File tempFile = File.createTempFile("exif_strip_", ".jpg", currentFile.getParentFile());
            try {
                try (OutputStream os = new BufferedOutputStream(new FileOutputStream(tempFile))) {
                    new ExifRewriter().removeExifMetadata(currentFile, os);
                }
                moveWithRetry(tempFile, currentFile);
            } finally {
                if (tempFile.exists()) tempFile.delete();
            }

            resetFormToEmpty();
            lblExifStatus.setText("🧹 Đã xóa sạch toàn bộ EXIF khỏi bức ảnh này!");
            lblExifStatus.setForeground(new Color(180, 40, 40));
            JOptionPane.showMessageDialog(this, "Đã xóa sạch EXIF thành công!", "Hoàn tất", JOptionPane.INFORMATION_MESSAGE);

        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Lỗi khi xóa EXIF: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void saveAllImagesInBackground() {
        if (imageFiles.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Chưa có ảnh nào trong danh sách!", "Lỗi", JOptionPane.WARNING_MESSAGE);
            return;
        }

        ExifFormData formData = validateAndReadForm(false);
        if (formData == null) return;

        final int stepSeconds = (Integer) spnStepSeconds.getValue();
        setControlsEnabled(false);
        progressBar.setValue(0);

        SwingWorker<List<String>, Integer> worker = new SwingWorker<>() {
            @Override
            protected List<String> doInBackground() {
                List<String> errors = new ArrayList<>();
                int total = imageFiles.size();

                for (int i = 0; i < total; i++) {
                    File f = imageFiles.get(i);
                    String targetName = f.getName().startsWith("EXIF_") ? f.getName() : "EXIF_" + f.getName();
                    try {
                        File updated = applyExifAndRename(f, targetName, formData, i * stepSeconds);
                        imageFiles.set(i, updated);
                        publish(i);
                    } catch (Exception ex) {
                        errors.add(f.getName() + ": " + ex.getMessage());
                    }
                    setProgress((int) (((i + 1) * 100.0) / total));
                }
                return errors;
            }

            @Override
            protected void process(List<Integer> updatedIndices) {
                for (int idx : updatedIndices) {
                    if (idx >= 0 && idx < thumbnailCards.size()) {
                        File updated = imageFiles.get(idx);
                        JPanel card = thumbnailCards.get(idx);
                        card.setToolTipText(updated.getName() + " (" + formatFileSize(updated.length()) + ")");
                        ((JLabel) card.getComponent(1)).setText(updated.getName());
                    }
                }
            }

            @Override
            protected void done() {
                setControlsEnabled(true);
                selectImageAt(selectedIndex);
                try {
                    List<String> errors = get();
                    if (errors.isEmpty()) {
                        progressBar.setValue(100);
                        progressBar.setString("Hoàn tất 100% (" + imageFiles.size() + " ảnh)");
                        JOptionPane.showMessageDialog(WriteExif.this,
                                "Đã cập nhật EXIF thành công cho toàn bộ " + imageFiles.size() + " ảnh!",
                                "Hoàn tất", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        progressBar.setString("Hoàn tất (Có " + errors.size() + " ảnh lỗi)");
                        JOptionPane.showMessageDialog(WriteExif.this,
                                "Một số ảnh không thể ghi:\n" + String.join("\n", errors),
                                "Cảnh báo", JOptionPane.WARNING_MESSAGE);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(WriteExif.this,
                            "Lỗi xử lý hàng loạt: " + ex.getMessage(),
                            "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        worker.addPropertyChangeListener(evt -> {
            if ("progress".equals(evt.getPropertyName())) {
                int val = (Integer) evt.getNewValue();
                progressBar.setValue(val);
                progressBar.setString("Đang ghi EXIF (" + val + "%)...");
            }
        });
        worker.execute();
    }

    private void backupOriginalFile(File sourceFile) throws Exception {
        File backupDir = new File(sourceFile.getParentFile(), "_original_backup");
        if (!backupDir.exists()) {
            backupDir.mkdirs();
        }
        File backupFile = new File(backupDir, sourceFile.getName());
        if (!backupFile.exists()) {
            Files.copy(sourceFile.toPath(), backupFile.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    private File applyExifAndRename(File sourceFile, String newFileName, ExifFormData data, int offsetSeconds) throws Exception {
        if (!isRealJpeg(sourceFile)) {
            throw new IllegalArgumentException("File '" + sourceFile.getName()
                    + "' không phải ảnh JPEG chuẩn (có thể là ảnh PNG/HEIC/WebP bị đổi đuôi sang .jpg).");
        }

        if (chkBackup.isSelected()) {
            backupOriginalFile(sourceFile);
        }

        TiffOutputSet outputSet = null;
        ImageMetadata meta = Imaging.getMetadata(sourceFile);
        if (meta instanceof JpegImageMetadata jpegMeta) {
            TiffImageMetadata tiff = jpegMeta.getExif();
            if (tiff != null) outputSet = tiff.getOutputSet();
        }
        if (outputSet == null) outputSet = new TiffOutputSet();

        String dtString = data.getFormattedDateTime(offsetSeconds);

        var root = outputSet.getOrCreateRootDirectory();
        root.removeField(TiffTagConstants.TIFF_TAG_MAKE);
        root.add(TiffTagConstants.TIFF_TAG_MAKE, data.make);

        root.removeField(TiffTagConstants.TIFF_TAG_MODEL);
        root.add(TiffTagConstants.TIFF_TAG_MODEL, data.model);

        root.removeField(TiffTagConstants.TIFF_TAG_DATE_TIME);
        root.add(TiffTagConstants.TIFF_TAG_DATE_TIME, dtString);

        var exif = outputSet.getOrCreateExifDirectory();
        exif.removeField(ExifTagConstants.EXIF_TAG_FNUMBER);
        exif.add(ExifTagConstants.EXIF_TAG_FNUMBER, RationalNumber.valueOf(data.fNumber));

        exif.removeField(ExifTagConstants.EXIF_TAG_ISO);
        exif.add(ExifTagConstants.EXIF_TAG_ISO, (short) data.iso);

        exif.removeField(ExifTagConstants.EXIF_TAG_EXPOSURE_TIME);
        exif.add(ExifTagConstants.EXIF_TAG_EXPOSURE_TIME, new RationalNumber(data.shutterNum, data.shutterDen));

        exif.removeField(ExifTagConstants.EXIF_TAG_FOCAL_LENGTH);
        exif.add(ExifTagConstants.EXIF_TAG_FOCAL_LENGTH, RationalNumber.valueOf(data.focal));

        exif.removeField(ExifTagConstants.EXIF_TAG_FOCAL_LENGTH_IN_35MM_FORMAT);
        exif.add(ExifTagConstants.EXIF_TAG_FOCAL_LENGTH_IN_35MM_FORMAT, (short) Math.round(data.focal));

        if (!data.lens.isEmpty()) {
            exif.removeField(ExifTagConstants.EXIF_TAG_LENS_MODEL);
            exif.add(ExifTagConstants.EXIF_TAG_LENS_MODEL, data.lens);
        }

        exif.removeField(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL);
        exif.add(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL, dtString);

        exif.removeField(ExifTagConstants.EXIF_TAG_DATE_TIME_DIGITIZED);
        exif.add(ExifTagConstants.EXIF_TAG_DATE_TIME_DIGITIZED, dtString);

        File targetFile = new File(sourceFile.getParentFile(), newFileName);
        File tempFile = File.createTempFile("exif_temp_", ".jpg", sourceFile.getParentFile());

        try {
            try (OutputStream os = new BufferedOutputStream(new FileOutputStream(tempFile))) {
                new ExifRewriter().updateExifMetadataLossless(sourceFile, os, outputSet);
            }

            moveWithRetry(tempFile, targetFile);

            if (!sourceFile.equals(targetFile) && sourceFile.exists()) {
                Files.deleteIfExists(sourceFile.toPath());
            }
        } finally {
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
        return targetFile;
    }

    private void moveWithRetry(File src, File dst) throws Exception {
        int attempts = 0;
        while (true) {
            try {
                Files.move(src.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (FileSystemException ex) {
                attempts++;
                if (attempts >= 3) throw ex;
                Thread.sleep(120);
            }
        }
    }

    private boolean isRealJpeg(File file) {
        if (file.length() < 4) return false;
        try (FileInputStream fis = new FileInputStream(file)) {
            int b1 = fis.read();
            int b2 = fis.read();
            return (b1 == 0xFF && b2 == 0xD8);
        } catch (Exception e) {
            return false;
        }
    }

    private void setControlsEnabled(boolean enabled) {
        btnSaveSingle.setEnabled(enabled);
        btnSaveAll.setEnabled(enabled);
        btnStripExif.setEnabled(enabled);
    }

    private static long gcd(long a, long b) {
        while (b != 0) {
            long t = b;
            b = a % b;
            a = t;
        }
        return a;
    }

    private static String trimTrailingZero(double val) {
        if (val == (long) val) {
            return String.format("%d", (long) val);
        }
        return String.valueOf(val);
    }

    private static String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.1f KB", kb);
        return String.format("%.2f MB", kb / 1024.0);
    }

    private class ZoomableImagePanel extends JPanel {
        private BufferedImage image;
        private double zoomFactor = 1.0;
        private double offsetX = 0;
        private double offsetY = 0;
        private Point dragStart;

        public ZoomableImagePanel() {
            setBackground(new Color(35, 38, 42));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            addMouseWheelListener(e -> {
                if (image == null) return;
                if (e.getWheelRotation() < 0) {
                    zoomBy(1.15);
                } else {
                    zoomBy(0.87);
                }
            });

            MouseAdapter mouseHandler = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    dragStart = e.getPoint();
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (image == null || dragStart == null) return;
                    Point cur = e.getPoint();
                    offsetX += (cur.x - dragStart.x);
                    offsetY += (cur.y - dragStart.y);
                    dragStart = cur;
                    repaint();
                }
            };
            addMouseListener(mouseHandler);
            addMouseMotionListener(mouseHandler);
        }

        public void setImage(BufferedImage newImg) {
            if (this.image != null && this.image != newImg) {
                this.image.flush();
            }
            this.image = newImg;
            resetZoom();
        }

        public void zoomBy(double factor) {
            if (image == null) return;
            zoomFactor = Math.max(0.2, Math.min(zoomFactor * factor, 10.0));
            lblZoomPercent.setText(Math.round(zoomFactor * 100) + "%");
            repaint();
        }

        public void resetZoom() {
            zoomFactor = 1.0;
            offsetX = 0;
            offsetY = 0;
            lblZoomPercent.setText("100%");
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            if (image == null) {
                g2.setColor(Color.LIGHT_GRAY);
                String msg = "Kéo thả ảnh JPEG vào đây hoặc bấm '📂 Chọn ảnh...'";
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(msg, Math.max(10, (getWidth() - fm.stringWidth(msg)) / 2), getHeight() / 2);
                g2.dispose();
                return;
            }

            double baseScale = Math.min(
                    (double) (getWidth() - 20) / image.getWidth(),
                    (double) (getHeight() - 20) / image.getHeight()
            );
            double finalScale = baseScale * zoomFactor;

            int drawW = Math.max(1, (int) (image.getWidth() * finalScale));
            int drawH = Math.max(1, (int) (image.getHeight() * finalScale));

            int x = (int) ((getWidth() - drawW) / 2.0 + offsetX);
            int y = (int) ((getHeight() - drawH) / 2.0 + offsetY);

            g2.drawImage(image, x, y, drawW, drawH, null);
            g2.dispose();
        }
    }

    private static class CalendarDialog extends JDialog {
        private LocalDate selected;
        private final JComboBox<Integer> cbYear;
        private final JComboBox<String> cbMonth;
        private final JPanel daysPanel = new JPanel(new GridLayout(0, 7, 4, 4));

        public CalendarDialog(Frame owner, LocalDate initialDate) {
            super(owner, "Chọn ngày chụp", true);
            this.selected = initialDate;
            setSize(340, 310);
            setLocationRelativeTo(owner);
            setLayout(new BorderLayout(8, 8));

            JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 6));
            Integer[] years = new Integer[50];
            int currentYear = LocalDate.now().getYear();
            for (int i = 0; i < 50; i++) years[i] = currentYear - 35 + i;

            cbYear = new JComboBox<>(years);
            cbYear.setSelectedItem(initialDate.getYear());

            String[] months = {
                    "Tháng 1", "Tháng 2", "Tháng 3", "Tháng 4", "Tháng 5", "Tháng 6",
                    "Tháng 7", "Tháng 8", "Tháng 9", "Tháng 10", "Tháng 11", "Tháng 12"
            };
            cbMonth = new JComboBox<>(months);
            cbMonth.setSelectedIndex(initialDate.getMonthValue() - 1);

            cbYear.addActionListener(e -> renderCalendar());
            cbMonth.addActionListener(e -> renderCalendar());

            topPanel.add(cbMonth);
            topPanel.add(cbYear);
            add(topPanel, BorderLayout.NORTH);

            daysPanel.setBorder(new EmptyBorder(5, 10, 10, 10));
            add(daysPanel, BorderLayout.CENTER);

            renderCalendar();
        }

        private void renderCalendar() {
            daysPanel.removeAll();
            String[] headers = {"T2", "T3", "T4", "T5", "T6", "T7", "CN"};
            for (String h : headers) {
                JLabel lbl = new JLabel(h, SwingConstants.CENTER);
                lbl.setFont(new Font("SansSerif", Font.BOLD, 12));
                daysPanel.add(lbl);
            }

            int year = (int) cbYear.getSelectedItem();
            int month = cbMonth.getSelectedIndex() + 1;
            YearMonth ym = YearMonth.of(year, month);

            int firstDayOfWeek = ym.atDay(1).getDayOfWeek().getValue();
            for (int i = 1; i < firstDayOfWeek; i++) {
                daysPanel.add(new JLabel(""));
            }

            for (int day = 1; day <= ym.lengthOfMonth(); day++) {
                final int d = day;
                JButton btnDay = new JButton(String.valueOf(day));
                btnDay.setMargin(new Insets(2, 2, 2, 2));
                btnDay.setFocusPainted(false);

                if (year == selected.getYear() && month == selected.getMonthValue() && day == selected.getDayOfMonth()) {
                    btnDay.setFont(new Font("SansSerif", Font.BOLD, 12));
                    btnDay.setForeground(new Color(0, 102, 204));
                }

                btnDay.addActionListener(e -> {
                    selected = LocalDate.of(year, month, d);
                    dispose();
                });
                daysPanel.add(btnDay);
            }

            daysPanel.revalidate();
            daysPanel.repaint();
        }

        public LocalDate showDialog() {
            setVisible(true);
            return selected;
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        SwingUtilities.invokeLater(() -> new WriteExif().setVisible(true));
    }
}