package com.conveult.gui;

import com.conveult.DocumentEngine;
import com.conveult.Format;
import com.conveult.ImageEngine;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.File;
import java.text.DecimalFormat;
import java.util.*;

/**
 * Modern Dark Mode JavaFX GUI for conve-ult Universal File Converter.
 */
public class ConveUltApp extends Application {

    private Stage primaryStage;
    private File selectedInputFile;
    private File selectedOutputFile;
    private List<File> lastGeneratedFiles = new ArrayList<>();

    // Single Conversion UI Controls
    private VBox dropzoneBox;
    private VBox fileCardBox;
    private Label fileNameLabel;
    private Label fileMetaLabel;
    private Label fileKindBadge;
    private ComboBox<FormatChoice> targetFormatCombo;
    private TextField outputPathField;
    private CheckBox overwriteCheckbox;

    // Image Options Controls
    private VBox imageOptionsBox;
    private Slider qualitySlider;
    private Label qualityValLabel;
    private TextField widthField;
    private TextField heightField;
    private CheckBox keepRatioCheckbox;
    private CheckBox grayscaleCheckbox;
    private ComboBox<String> rotateCombo;

    // Document Options Controls
    private VBox docOptionsBox;
    private TextField pageRangeField;
    private TextField docMarginField;

    // Action and Progress Controls
    private Button convertButton;
    private ProgressBar progressBar;
    private Label statusLabel;
    private HBox successActionBox;
    private Button openFolderButton;

    // Batch Mode Controls
    private ObservableList<BatchItem> batchItems = FXCollections.observableArrayList();
    private TableView<BatchItem> batchTable;
    private ComboBox<FormatChoice> batchTargetCombo;
    private TextField batchOutputDirField;
    private ProgressBar batchProgressBar;
    private Label batchStatusLabel;
    private Button batchConvertButton;

    public static void launchApp(String[] args) {
        Application.launch(ConveUltApp.class, args);
    }

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        stage.setTitle("conve-ult — Universal File Converter");
        stage.setMinWidth(800);
        stage.setMinHeight(620);

        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: " + DarkTheme.COLOR_BG_DARK + ";");

        // Top Header
        root.setTop(buildHeader());

        // Center Tabs (Single vs Batch)
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab singleTab = new Tab("  Single File  ", buildSingleConvertView());
        Tab batchTab = new Tab("  Batch Convert  ", buildBatchConvertView());
        tabPane.getTabs().addAll(singleTab, batchTab);

        root.setCenter(tabPane);

        // Bottom Footer
        root.setBottom(buildFooter());

        Scene scene = new Scene(root, 880, 700);
        DarkTheme.apply(scene);

        stage.setScene(scene);
        stage.show();
    }

    // =========================================================================
    // 1. HEADER & FOOTER
    // =========================================================================

    private Node buildHeader() {
        HBox header = new HBox(16);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(16, 24, 16, 24));
        header.setStyle("-fx-background-color: " + DarkTheme.COLOR_SURFACE + "; -fx-border-color: " + DarkTheme.COLOR_BORDER + "; -fx-border-width: 0 0 1 0;");

        // Brand Icon
        SVGPath icon = DarkTheme.createIcon(DarkTheme.ICON_CONVERT, DarkTheme.COLOR_ACCENT, 1.2);

        VBox titleBox = new VBox(2);
        HBox nameRow = new HBox(8);
        nameRow.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("conve-ult");
        title.getStyleClass().add("label-title");

        Label badge = new Label("v1.0.0");
        badge.getStyleClass().add("badge");

        nameRow.getChildren().addAll(title, badge);

        Label subtitle = new Label("Universal File Converter • Java • Apache POI • PDFBox");
        subtitle.getStyleClass().add("label-subtitle");

        titleBox.getChildren().addAll(nameRow, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button formatsBtn = new Button("Supported Formats");
        formatsBtn.getStyleClass().add("button-secondary");
        formatsBtn.setGraphic(DarkTheme.createIcon(DarkTheme.ICON_INFO, DarkTheme.COLOR_TEXT_MUTED, 0.9));
        formatsBtn.setOnAction(e -> showFormatsDialog());

        header.getChildren().addAll(icon, titleBox, spacer, formatsBtn);
        return header;
    }

    private Node buildFooter() {
        HBox footer = new HBox(16);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(8, 24, 8, 24));
        footer.setStyle("-fx-background-color: " + DarkTheme.COLOR_SURFACE + "; -fx-border-color: " + DarkTheme.COLOR_BORDER + "; -fx-border-width: 1 0 0 0;");

        Label engineLabel = new Label("Engines: Apache POI 5.2.5 • Apache PDFBox 3.0.1 • TwelveMonkeys ImageIO");
        engineLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748b;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label localLabel = new Label("100% Local & Offline • Zero Telemetry");
        localLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #10b981; -fx-font-weight: bold;");

        footer.getChildren().addAll(engineLabel, spacer, localLabel);
        return footer;
    }

    // =========================================================================
    // 2. SINGLE CONVERT VIEW
    // =========================================================================

    private Node buildSingleConvertView() {
        VBox layout = new VBox(16);
        layout.setPadding(new Insets(20, 24, 20, 24));

        ScrollPane scrollPane = new ScrollPane(layout);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: " + DarkTheme.COLOR_BG_DARK + "; -fx-background-color: transparent;");

        // 1. Input Section: Dropzone or File Summary
        layout.getChildren().add(buildInputSection());

        // 2. Output Settings Section
        layout.getChildren().add(buildSettingsSection());

        // 3. Action and Result Section
        layout.getChildren().add(buildActionSection());

        return scrollPane;
    }

    private Node buildInputSection() {
        StackPane container = new StackPane();

        // State A: Empty Dropzone
        dropzoneBox = new VBox(12);
        dropzoneBox.getStyleClass().add("dropzone");
        dropzoneBox.setPrefHeight(150);

        SVGPath cloudIcon = DarkTheme.createIcon(DarkTheme.ICON_UPLOAD, DarkTheme.COLOR_ACCENT, 1.8);

        Label dropTitle = new Label("Drag & drop your file here");
        dropTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #e2e8f0;");

        Label dropSub = new Label("or browse from your system (Images: PNG, JPG, WEBP, GIF, TIFF • Docs: PDF, DOCX, ODT, HTML, MD, TXT)");
        dropSub.getStyleClass().add("label-muted");

        Button browseBtn = new Button("Browse File...");
        browseBtn.getStyleClass().add("button-primary");
        browseBtn.setGraphic(DarkTheme.createIcon(DarkTheme.ICON_FOLDER, "#ffffff", 0.9));
        browseBtn.setOnAction(e -> chooseSingleFile());

        dropzoneBox.getChildren().addAll(cloudIcon, dropTitle, dropSub, browseBtn);

        // State B: File Selected Card
        fileCardBox = new VBox(12);
        fileCardBox.getStyleClass().add("card");
        fileCardBox.setVisible(false);

        HBox topRow = new HBox(12);
        topRow.setAlignment(Pos.CENTER_LEFT);

        SVGPath fileIcon = DarkTheme.createIcon(DarkTheme.ICON_FILE, DarkTheme.COLOR_ACCENT, 1.4);

        VBox metaBox = new VBox(4);
        HBox nameLine = new HBox(8);
        nameLine.setAlignment(Pos.CENTER_LEFT);

        fileNameLabel = new Label("sample.md");
        fileNameLabel.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        fileKindBadge = new Label("DOCUMENT");
        fileKindBadge.getStyleClass().add("badge");

        nameLine.getChildren().addAll(fileNameLabel, fileKindBadge);

        fileMetaLabel = new Label("/path/to/sample.md • 12.4 KB");
        fileMetaLabel.getStyleClass().add("label-muted");

        metaBox.getChildren().addAll(nameLine, fileMetaLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button changeBtn = new Button("Change");
        changeBtn.getStyleClass().add("button-secondary");
        changeBtn.setOnAction(e -> chooseSingleFile());

        Button clearBtn = new Button();
        clearBtn.getStyleClass().add("button-ghost");
        clearBtn.setGraphic(DarkTheme.createIcon(DarkTheme.ICON_TRASH, DarkTheme.COLOR_DANGER, 0.9));
        clearBtn.setTooltip(new Tooltip("Clear selected file"));
        clearBtn.setOnAction(e -> clearSelectedFile());

        topRow.getChildren().addAll(fileIcon, metaBox, spacer, changeBtn, clearBtn);
        fileCardBox.getChildren().add(topRow);

        container.getChildren().addAll(dropzoneBox, fileCardBox);

        // Setup Drag & Drop handlers on dropzone
        setupDragAndDrop(dropzoneBox);
        setupDragAndDrop(fileCardBox);

        return container;
    }

    private void setupDragAndDrop(VBox targetBox) {
        targetBox.setOnDragOver(event -> {
            if (event.getGestureSource() != targetBox && event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });

        targetBox.setOnDragEntered(event -> {
            if (event.getGestureSource() != targetBox && event.getDragboard().hasFiles()) {
                targetBox.getStyleClass().add("dropzone-hover");
            }
            event.consume();
        });

        targetBox.setOnDragExited(event -> {
            targetBox.getStyleClass().remove("dropzone-hover");
            event.consume();
        });

        targetBox.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                List<File> files = db.getFiles();
                if (!files.isEmpty()) {
                    setInputFile(files.get(0));
                    success = true;
                }
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    private Node buildSettingsSection() {
        VBox card = new VBox(14);
        card.getStyleClass().add("card");

        Label sectionTitle = new Label("Conversion Settings");
        sectionTitle.getStyleClass().add("label-section");

        // Target Format & Destination Grid
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(12);

        Label targetLabel = new Label("Target Format:");
        targetLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #cbd5e1;");

        targetFormatCombo = new ComboBox<>();
        targetFormatCombo.setPrefWidth(220);
        targetFormatCombo.setOnAction(e -> onTargetFormatChanged());

        Label outputLabel = new Label("Output File:");
        outputLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #cbd5e1;");

        HBox outputBox = new HBox(8);
        HBox.setHgrow(outputBox, Priority.ALWAYS);
        outputPathField = new TextField();
        outputPathField.setPromptText("Select target format or browse output path...");
        HBox.setHgrow(outputPathField, Priority.ALWAYS);

        Button browseOutputBtn = new Button("Browse...");
        browseOutputBtn.getStyleClass().add("button-secondary");
        browseOutputBtn.setOnAction(e -> chooseOutputFile());

        outputBox.getChildren().addAll(outputPathField, browseOutputBtn);

        grid.add(targetLabel, 0, 0);
        grid.add(targetFormatCombo, 1, 0);
        grid.add(outputLabel, 0, 1);
        grid.add(outputBox, 1, 1);

        ColumnConstraints col0 = new ColumnConstraints();
        col0.setMinWidth(110);
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(col0, col1);

        overwriteCheckbox = new CheckBox("Overwrite existing file if it exists");
        overwriteCheckbox.setSelected(true);

        // Contextual Option Panels
        imageOptionsBox = buildImageOptionsPanel();
        docOptionsBox = buildDocumentOptionsPanel();

        card.getChildren().addAll(sectionTitle, grid, overwriteCheckbox, imageOptionsBox, docOptionsBox);
        return card;
    }

    private VBox buildImageOptionsPanel() {
        VBox box = new VBox(10);
        box.getStyleClass().add("card-elevated");
        box.setVisible(false);
        box.setManaged(false);

        Label optTitle = new Label("Image Optimization Options");
        optTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #c7d2fe;");

        // Row 1: Quality slider
        HBox qualityRow = new HBox(12);
        qualityRow.setAlignment(Pos.CENTER_LEFT);

        Label qLabel = new Label("Quality:");
        qLabel.setPrefWidth(80);

        qualitySlider = new Slider(10, 100, 85);
        qualitySlider.setPrefWidth(260);
        qualitySlider.setBlockIncrement(5);

        qualityValLabel = new Label("85%");
        qualityValLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #818cf8;");
        qualityValLabel.setPrefWidth(50);

        qualitySlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            qualityValLabel.setText(newVal.intValue() + "%");
        });

        grayscaleCheckbox = new CheckBox("Grayscale (B&W)");

        qualityRow.getChildren().addAll(qLabel, qualitySlider, qualityValLabel, grayscaleCheckbox);

        // Row 2: Dimensions & Rotation
        HBox dimRow = new HBox(12);
        dimRow.setAlignment(Pos.CENTER_LEFT);

        Label sizeLabel = new Label("Resize:");
        sizeLabel.setPrefWidth(80);

        widthField = new TextField();
        widthField.setPromptText("Width (px)");
        widthField.setPrefWidth(100);

        Label xLabel = new Label("×");
        xLabel.setStyle("-fx-text-fill: #94a3b8;");

        heightField = new TextField();
        heightField.setPromptText("Height (px)");
        heightField.setPrefWidth(100);

        keepRatioCheckbox = new CheckBox("Keep Ratio");
        keepRatioCheckbox.setSelected(true);

        Label rotLabel = new Label("Rotate:");
        rotLabel.setStyle("-fx-padding: 0 0 0 16px;");

        rotateCombo = new ComboBox<>(FXCollections.observableArrayList("0°", "90° Clockwise", "180°", "270°"));
        rotateCombo.getSelectionModel().selectFirst();

        dimRow.getChildren().addAll(sizeLabel, widthField, xLabel, heightField, keepRatioCheckbox, rotLabel, rotateCombo);

        box.getChildren().addAll(optTitle, qualityRow, dimRow);
        return box;
    }

    private VBox buildDocumentOptionsPanel() {
        VBox box = new VBox(10);
        box.getStyleClass().add("card-elevated");
        box.setVisible(false);
        box.setManaged(false);

        Label optTitle = new Label("Document Layout Options");
        optTitle.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #c7d2fe;");

        HBox row = new HBox(16);
        row.setAlignment(Pos.CENTER_LEFT);

        Label pagesLabel = new Label("Page Range:");
        pageRangeField = new TextField();
        pageRangeField.setPromptText("all or 1-3, 5");
        pageRangeField.setText("all");
        pageRangeField.setPrefWidth(120);

        Label marginLabel = new Label("Margin (pt):");
        docMarginField = new TextField();
        docMarginField.setPromptText("40");
        docMarginField.setText("40");
        docMarginField.setPrefWidth(80);

        row.getChildren().addAll(pagesLabel, pageRangeField, marginLabel, docMarginField);
        box.getChildren().addAll(optTitle, row);
        return box;
    }

    private Node buildActionSection() {
        VBox card = new VBox(14);
        card.getStyleClass().add("card");

        HBox actionRow = new HBox(16);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        convertButton = new Button("Convert File");
        convertButton.getStyleClass().add("button-primary");
        convertButton.setGraphic(DarkTheme.createIcon(DarkTheme.ICON_CONVERT, "#ffffff", 1.0));
        convertButton.setDisable(true);
        convertButton.setOnAction(e -> executeSingleConversion());

        progressBar = new ProgressBar(0);
        progressBar.setVisible(false);
        progressBar.setPrefWidth(220);

        statusLabel = new Label("Select or drop a file to begin.");
        statusLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #94a3b8;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Success action button (Open Folder)
        successActionBox = new HBox(8);
        successActionBox.setVisible(false);

        openFolderButton = new Button("Open Folder");
        openFolderButton.getStyleClass().add("button-secondary");
        openFolderButton.setGraphic(DarkTheme.createIcon(DarkTheme.ICON_FOLDER, DarkTheme.COLOR_ACCENT, 0.9));
        openFolderButton.setOnAction(e -> openContainingFolder());

        successActionBox.getChildren().add(openFolderButton);

        actionRow.getChildren().addAll(convertButton, progressBar, statusLabel, spacer, successActionBox);
        card.getChildren().add(actionRow);
        return card;
    }

    // =========================================================================
    // 3. BATCH CONVERT VIEW
    // =========================================================================

    private Node buildBatchConvertView() {
        VBox layout = new VBox(16);
        layout.setPadding(new Insets(20, 24, 20, 24));

        // Top Toolbar
        HBox toolbar = new HBox(12);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Button addFilesBtn = new Button("Add Files...");
        addFilesBtn.getStyleClass().add("button-secondary");
        addFilesBtn.setGraphic(DarkTheme.createIcon(DarkTheme.ICON_FILE, DarkTheme.COLOR_TEXT_PRIMARY, 0.9));
        addFilesBtn.setOnAction(e -> addBatchFiles());

        Button addDirBtn = new Button("Add Folder...");
        addDirBtn.getStyleClass().add("button-secondary");
        addDirBtn.setGraphic(DarkTheme.createIcon(DarkTheme.ICON_FOLDER, DarkTheme.COLOR_TEXT_PRIMARY, 0.9));
        addDirBtn.setOnAction(e -> addBatchDirectory());

        Button clearBatchBtn = new Button("Clear List");
        clearBatchBtn.getStyleClass().add("button-ghost");
        clearBatchBtn.setGraphic(DarkTheme.createIcon(DarkTheme.ICON_TRASH, DarkTheme.COLOR_DANGER, 0.9));
        clearBatchBtn.setOnAction(e -> batchItems.clear());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label targetLabel = new Label("Convert all to:");
        targetLabel.setStyle("-fx-font-weight: bold;");

        batchTargetCombo = new ComboBox<>();
        populateBatchFormats();
        batchTargetCombo.getSelectionModel().selectFirst();

        toolbar.getChildren().addAll(addFilesBtn, addDirBtn, clearBatchBtn, spacer, targetLabel, batchTargetCombo);

        // Table
        batchTable = new TableView<>(batchItems);
        batchTable.setPrefHeight(340);
        batchTable.setPlaceholder(new Label("No files added. Click 'Add Files' or drag & drop files here."));

        TableColumn<BatchItem, String> nameCol = new TableColumn<>("File Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("fileName"));
        nameCol.setPrefWidth(240);

        TableColumn<BatchItem, String> sizeCol = new TableColumn<>("Size");
        sizeCol.setCellValueFactory(new PropertyValueFactory<>("sizeFormatted"));
        sizeCol.setPrefWidth(90);

        TableColumn<BatchItem, String> fromCol = new TableColumn<>("Input Format");
        fromCol.setCellValueFactory(new PropertyValueFactory<>("inputFormatName"));
        fromCol.setPrefWidth(120);

        TableColumn<BatchItem, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(new PropertyValueFactory<>("status"));
        statusCol.setPrefWidth(160);

        batchTable.getColumns().addAll(nameCol, sizeCol, fromCol, statusCol);

        // Output Directory Bar
        HBox outDirRow = new HBox(12);
        outDirRow.setAlignment(Pos.CENTER_LEFT);

        Label outDirLabel = new Label("Output Folder:");
        outDirLabel.setStyle("-fx-font-weight: bold;");

        batchOutputDirField = new TextField();
        batchOutputDirField.setPromptText("Defaults to source folder...");
        HBox.setHgrow(batchOutputDirField, Priority.ALWAYS);

        Button browseOutDirBtn = new Button("Browse...");
        browseOutDirBtn.getStyleClass().add("button-secondary");
        browseOutDirBtn.setOnAction(e -> chooseBatchOutputDir());

        outDirRow.getChildren().addAll(outDirLabel, batchOutputDirField, browseOutDirBtn);

        // Bottom Action Bar
        HBox actionRow = new HBox(16);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        batchConvertButton = new Button("Convert All Files");
        batchConvertButton.getStyleClass().add("button-primary");
        batchConvertButton.setGraphic(DarkTheme.createIcon(DarkTheme.ICON_CONVERT, "#ffffff", 1.0));
        batchConvertButton.setOnAction(e -> executeBatchConversion());

        batchProgressBar = new ProgressBar(0);
        batchProgressBar.setVisible(false);
        batchProgressBar.setPrefWidth(250);

        batchStatusLabel = new Label("Add files to begin batch conversion.");
        batchStatusLabel.getStyleClass().add("label-muted");

        actionRow.getChildren().addAll(batchConvertButton, batchProgressBar, batchStatusLabel);

        layout.getChildren().addAll(toolbar, batchTable, outDirRow, actionRow);
        return layout;
    }

    private void populateBatchFormats() {
        batchTargetCombo.getItems().clear();
        batchTargetCombo.getItems().add(new FormatChoice("PDF Document (.pdf)", Format.PDF));
        batchTargetCombo.getItems().add(new FormatChoice("DOCX Word Document (.docx)", Format.DOCX));
        batchTargetCombo.getItems().add(new FormatChoice("ODT OpenDocument (.odt)", Format.ODT));
        batchTargetCombo.getItems().add(new FormatChoice("HTML Web Page (.html)", Format.HTML));
        batchTargetCombo.getItems().add(new FormatChoice("Markdown (.md)", Format.MD));
        batchTargetCombo.getItems().add(new FormatChoice("Plain Text (.txt)", Format.TXT));
        batchTargetCombo.getItems().add(new FormatChoice("PNG Image (.png)", Format.PNG));
        batchTargetCombo.getItems().add(new FormatChoice("JPEG Image (.jpg)", Format.JPG));
        batchTargetCombo.getItems().add(new FormatChoice("WebP Image (.webp)", Format.WEBP));
    }

    // =========================================================================
    // 4. LOGIC & USER ACTIONS
    // =========================================================================

    private void chooseSingleFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select File to Convert");
        if (selectedInputFile != null && selectedInputFile.getParentFile() != null) {
            chooser.setInitialDirectory(selectedInputFile.getParentFile());
        }
        File file = chooser.showOpenDialog(primaryStage);
        if (file != null) {
            setInputFile(file);
        }
    }

    private void setInputFile(File file) {
        if (!file.exists() || file.isDirectory()) return;
        this.selectedInputFile = file;

        fileNameLabel.setText(file.getName());
        fileMetaLabel.setText(file.getAbsolutePath() + " • " + formatBytes(file.length()));

        Format fmt = Format.fromFile(file);
        if (fmt != null) {
            fileKindBadge.setText(fmt.getKind().name() + " (" + fmt.name() + ")");
        } else {
            fileKindBadge.setText("FILE");
        }

        // Switch to File Selected state
        dropzoneBox.setVisible(false);
        fileCardBox.setVisible(true);

        // Populate compatible target formats
        populateTargetFormats(fmt);

        // Enable Convert button
        convertButton.setDisable(false);
        statusLabel.setText("Ready to convert");
        statusLabel.setStyle("-fx-text-fill: #94a3b8;");
        successActionBox.setVisible(false);
    }

    private void clearSelectedFile() {
        this.selectedInputFile = null;
        this.selectedOutputFile = null;
        dropzoneBox.setVisible(true);
        fileCardBox.setVisible(false);
        targetFormatCombo.getItems().clear();
        outputPathField.clear();
        convertButton.setDisable(true);
        statusLabel.setText("Select or drop a file to begin.");
        statusLabel.setStyle("-fx-text-fill: #94a3b8;");
        successActionBox.setVisible(false);
        imageOptionsBox.setVisible(false);
        imageOptionsBox.setManaged(false);
        docOptionsBox.setVisible(false);
        docOptionsBox.setManaged(false);
    }

    private void populateTargetFormats(Format inFmt) {
        targetFormatCombo.getItems().clear();

        if (inFmt == null) {
            // Default generic list
            for (Format f : Format.values()) {
                targetFormatCombo.getItems().add(new FormatChoice(f.name() + " (." + f.getPrimaryExtension() + ")", f));
            }
        } else if (inFmt.isImage()) {
            // Image targets + PDF
            targetFormatCombo.getItems().add(new FormatChoice("PNG Image (.png)", Format.PNG));
            targetFormatCombo.getItems().add(new FormatChoice("JPEG Image (.jpg)", Format.JPG));
            targetFormatCombo.getItems().add(new FormatChoice("WebP Image (.webp)", Format.WEBP));
            targetFormatCombo.getItems().add(new FormatChoice("PDF Document (.pdf)", Format.PDF));
            targetFormatCombo.getItems().add(new FormatChoice("GIF Image (.gif)", Format.GIF));
            targetFormatCombo.getItems().add(new FormatChoice("BMP Bitmap (.bmp)", Format.BMP));
            targetFormatCombo.getItems().add(new FormatChoice("TIFF Image (.tiff)", Format.TIFF));
        } else {
            // Document targets
            targetFormatCombo.getItems().add(new FormatChoice("PDF Document (.pdf)", Format.PDF));
            targetFormatCombo.getItems().add(new FormatChoice("DOCX Word (.docx)", Format.DOCX));
            targetFormatCombo.getItems().add(new FormatChoice("ODT OpenDocument (.odt)", Format.ODT));
            targetFormatCombo.getItems().add(new FormatChoice("HTML Web Page (.html)", Format.HTML));
            targetFormatCombo.getItems().add(new FormatChoice("Markdown (.md)", Format.MD));
            targetFormatCombo.getItems().add(new FormatChoice("Plain Text (.txt)", Format.TXT));
            targetFormatCombo.getItems().add(new FormatChoice("DOC Word 97-2004 (.doc)", Format.DOC));

            // If PDF input, can also convert to images!
            if (inFmt == Format.PDF) {
                targetFormatCombo.getItems().add(new FormatChoice("PNG Image (.png)", Format.PNG));
                targetFormatCombo.getItems().add(new FormatChoice("JPEG Image (.jpg)", Format.JPG));
                targetFormatCombo.getItems().add(new FormatChoice("WebP Image (.webp)", Format.WEBP));
            }
        }

        // Auto-select first target different from input format
        FormatChoice selection = null;
        for (FormatChoice fc : targetFormatCombo.getItems()) {
            if (fc.format != inFmt) {
                selection = fc;
                break;
            }
        }
        if (selection == null && !targetFormatCombo.getItems().isEmpty()) {
            selection = targetFormatCombo.getItems().get(0);
        }
        if (selection != null) {
            targetFormatCombo.getSelectionModel().select(selection);
            updateSuggestedOutputPath(selection.format);
        }
    }

    private void onTargetFormatChanged() {
        FormatChoice fc = targetFormatCombo.getValue();
        if (fc == null || selectedInputFile == null) return;

        updateSuggestedOutputPath(fc.format);

        // Show/hide contextual options
        boolean isTargetImage = fc.format.isImage();
        imageOptionsBox.setVisible(isTargetImage);
        imageOptionsBox.setManaged(isTargetImage);

        boolean isDocOp = fc.format.isDocument() || (selectedInputFile.getName().toLowerCase().endsWith(".pdf"));
        docOptionsBox.setVisible(isDocOp);
        docOptionsBox.setManaged(isDocOp);
    }

    private void updateSuggestedOutputPath(Format targetFormat) {
        if (selectedInputFile == null || targetFormat == null) return;
        String baseName = getBaseName(selectedInputFile);
        File parent = selectedInputFile.getParentFile();
        File suggested = new File(parent != null ? parent : new File("."), baseName + "." + targetFormat.getPrimaryExtension());
        this.selectedOutputFile = suggested;
        outputPathField.setText(suggested.getAbsolutePath());
    }

    private void chooseOutputFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Output File As");
        if (selectedOutputFile != null) {
            if (selectedOutputFile.getParentFile() != null) {
                chooser.setInitialDirectory(selectedOutputFile.getParentFile());
            }
            chooser.setInitialFileName(selectedOutputFile.getName());
        }
        File file = chooser.showSaveDialog(primaryStage);
        if (file != null) {
            this.selectedOutputFile = file;
            outputPathField.setText(file.getAbsolutePath());
        }
    }

    // =========================================================================
    // 5. ASYNC CONVERSION EXECUTION
    // =========================================================================

    private void executeSingleConversion() {
        if (selectedInputFile == null) return;

        String outPath = outputPathField.getText().trim();
        if (outPath.isEmpty()) {
            showError("Please specify an output file destination.");
            return;
        }

        File outFile = new File(outPath);
        this.selectedOutputFile = outFile;

        if (outFile.exists() && !overwriteCheckbox.isSelected()) {
            showError("Output file already exists. Check 'Overwrite' to replace it.");
            return;
        }

        FormatChoice targetChoice = targetFormatCombo.getValue();
        Format targetFmt = targetChoice != null ? targetChoice.format : Format.fromFile(outFile);

        // Prepare image options
        ImageEngine.Options imgOpts = new ImageEngine.Options();
        imgOpts.quality = (float) (qualitySlider.getValue() / 100.0);
        imgOpts.grayscale = grayscaleCheckbox.isSelected();
        imgOpts.keepAspectRatio = keepRatioCheckbox.isSelected();

        String wText = widthField.getText().trim();
        if (!wText.isEmpty()) {
            try { imgOpts.width = Integer.parseInt(wText); } catch (NumberFormatException ignored) {}
        }
        String hText = heightField.getText().trim();
        if (!hText.isEmpty()) {
            try { imgOpts.height = Integer.parseInt(hText); } catch (NumberFormatException ignored) {}
        }

        String rot = rotateCombo.getValue();
        if (rot != null) {
            if (rot.startsWith("90")) imgOpts.rotateDegrees = 90;
            else if (rot.startsWith("180")) imgOpts.rotateDegrees = 180;
            else if (rot.startsWith("270")) imgOpts.rotateDegrees = 270;
            else imgOpts.rotateDegrees = 0;
        }

        String pageRange = pageRangeField.getText().trim();
        if (!pageRange.isEmpty()) imgOpts.pageRange = pageRange;

        Float margin = 40f;
        try {
            margin = Float.parseFloat(docMarginField.getText().trim());
        } catch (Exception ignored) {}

        final Float finalMargin = margin;

        // UI state: Running
        convertButton.setDisable(true);
        progressBar.setVisible(true);
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        statusLabel.setText("Converting " + selectedInputFile.getName() + "...");
        statusLabel.setStyle("-fx-text-fill: #818cf8;");
        successActionBox.setVisible(false);

        long startTime = System.currentTimeMillis();

        Task<List<File>> conversionTask = new Task<>() {
            @Override
            protected List<File> call() throws Exception {
                Format inFmt = Format.fromFile(selectedInputFile);
                String inExt = getExt(selectedInputFile);
                String outExt = getExt(outFile);

                boolean isImageOp = (inFmt != null && inFmt.isImage() && (targetFmt != null && targetFmt.isImage() || "pdf".equalsIgnoreCase(outExt)))
                        || ("pdf".equalsIgnoreCase(inExt) && targetFmt != null && targetFmt.isImage());

                List<File> generated = new ArrayList<>();
                if (isImageOp) {
                    generated = ImageEngine.convert(selectedInputFile, outFile, targetFmt, imgOpts);
                } else {
                    DocumentEngine.convert(selectedInputFile, outFile, targetFmt, imgOpts, null, null, finalMargin);
                    generated.add(outFile);
                }
                return generated;
            }
        };

        conversionTask.setOnSucceeded(e -> {
            long elapsed = System.currentTimeMillis() - startTime;
            lastGeneratedFiles = conversionTask.getValue();
            progressBar.setVisible(false);
            convertButton.setDisable(false);

            if (!lastGeneratedFiles.isEmpty()) {
                File mainOut = lastGeneratedFiles.get(0);
                long newLen = mainOut.length();
                statusLabel.setText(String.format("✔ Converted in %d ms (%s)", elapsed, formatBytes(newLen)));
                statusLabel.setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold;");
                successActionBox.setVisible(true);
            }
        });

        conversionTask.setOnFailed(e -> {
            progressBar.setVisible(false);
            convertButton.setDisable(false);
            Throwable ex = conversionTask.getException();
            String msg = ex != null ? ex.getMessage() : "Unknown conversion failure";
            statusLabel.setText("✘ Failed: " + msg);
            statusLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold;");
            showError("Conversion Error: " + msg);
        });

        new Thread(conversionTask).start();
    }

    private void openContainingFolder() {
        if (lastGeneratedFiles.isEmpty()) return;
        File f = lastGeneratedFiles.get(0);
        File parent = f.getParentFile();
        if (parent == null || !parent.exists()) return;

        // Run asynchronously in a background thread so the JavaFX UI never blocks or freezes
        new Thread(() -> {
            boolean opened = false;
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

            // 1. Native platform file manager launch (xdg-open on Linux)
            if (os.contains("linux") || os.contains("nix")) {
                try {
                    new ProcessBuilder("xdg-open", parent.getAbsolutePath()).start();
                    opened = true;
                } catch (Exception ignored) {
                    try {
                        new ProcessBuilder("gio", "open", parent.getAbsolutePath()).start();
                        opened = true;
                    } catch (Exception ignored2) {}
                }
            } else if (os.contains("mac")) {
                try {
                    new ProcessBuilder("open", parent.getAbsolutePath()).start();
                    opened = true;
                } catch (Exception ignored) {}
            } else if (os.contains("win")) {
                try {
                    new ProcessBuilder("explorer.exe", parent.getAbsolutePath()).start();
                    opened = true;
                } catch (Exception ignored) {}
            }

            // 2. Safe fallback to AWT Desktop if native process was not dispatched
            if (!opened && Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                try {
                    Desktop.getDesktop().open(parent);
                } catch (Exception ex) {
                    Platform.runLater(() -> showError("Unable to open folder: " + ex.getMessage()));
                }
            }
        }, "conve-ult-open-folder").start();
    }

    // =========================================================================
    // 6. BATCH CONVERSION
    // =========================================================================

    private void addBatchFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Files to Add");
        List<File> files = chooser.showOpenMultipleDialog(primaryStage);
        if (files != null) {
            for (File f : files) {
                batchItems.add(new BatchItem(f));
            }
            batchStatusLabel.setText(batchItems.size() + " files in queue.");
        }
    }

    private void addBatchDirectory() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Folder with Files");
        File dir = chooser.showDialog(primaryStage);
        if (dir != null && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                int added = 0;
                for (File f : files) {
                    if (f.isFile() && !f.getName().startsWith(".")) {
                        batchItems.add(new BatchItem(f));
                        added++;
                    }
                }
                batchStatusLabel.setText(batchItems.size() + " files in queue (" + added + " added).");
            }
        }
    }

    private void chooseBatchOutputDir() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Destination Folder");
        File dir = chooser.showDialog(primaryStage);
        if (dir != null) {
            batchOutputDirField.setText(dir.getAbsolutePath());
        }
    }

    private void executeBatchConversion() {
        if (batchItems.isEmpty()) {
            showError("Please add files to batch list first.");
            return;
        }

        FormatChoice targetChoice = batchTargetCombo.getValue();
        if (targetChoice == null) {
            showError("Please select a target format.");
            return;
        }

        Format targetFmt = targetChoice.format;
        String targetExt = targetFmt.getPrimaryExtension();

        String outDirStr = batchOutputDirField.getText().trim();
        File outDir = !outDirStr.isEmpty() ? new File(outDirStr) : null;
        if (outDir != null && !outDir.exists()) outDir.mkdirs();

        batchConvertButton.setDisable(true);
        batchProgressBar.setVisible(true);
        batchProgressBar.setProgress(0);

        List<BatchItem> itemsCopy = new ArrayList<>(batchItems);
        int total = itemsCopy.size();

        Task<Void> batchTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                int completed = 0;
                for (int i = 0; i < total; i++) {
                    BatchItem item = itemsCopy.get(i);
                    File in = item.getFile();
                    File destDir = outDir != null ? outDir : in.getParentFile();
                    File out = new File(destDir, getBaseName(in) + "." + targetExt);

                    updateMessage(String.format("Converting %d of %d: %s", (i + 1), total, in.getName()));
                    updateProgress(i, total);

                    Platform.runLater(() -> item.setStatus("Processing..."));

                    try {
                        Format inFmt = Format.fromFile(in);
                        String inExt = getExt(in);
                        String outExt = getExt(out);

                        boolean isImageOp = (inFmt != null && inFmt.isImage() && (targetFmt.isImage() || "pdf".equalsIgnoreCase(outExt)))
                                || ("pdf".equalsIgnoreCase(inExt) && targetFmt.isImage());

                        if (isImageOp) {
                            ImageEngine.convert(in, out, targetFmt, new ImageEngine.Options());
                        } else {
                            DocumentEngine.convert(in, out, targetFmt, new ImageEngine.Options(), null, null, 40f);
                        }
                        Platform.runLater(() -> item.setStatus("✔ Done"));
                    } catch (Exception ex) {
                        Platform.runLater(() -> item.setStatus("✘ Failed: " + ex.getMessage()));
                    }

                    completed++;
                    updateProgress(completed, total);
                }
                return null;
            }
        };

        batchProgressBar.progressProperty().bind(batchTask.progressProperty());
        batchStatusLabel.textProperty().bind(batchTask.messageProperty());

        batchTask.setOnSucceeded(e -> {
            batchProgressBar.progressProperty().unbind();
            batchStatusLabel.textProperty().unbind();
            batchProgressBar.setVisible(false);
            batchConvertButton.setDisable(false);
            batchStatusLabel.setText(String.format("✔ Batch completed: %d files processed.", total));
            batchStatusLabel.setStyle("-fx-text-fill: #10b981; -fx-font-weight: bold;");
        });

        batchTask.setOnFailed(e -> {
            batchProgressBar.progressProperty().unbind();
            batchStatusLabel.textProperty().unbind();
            batchProgressBar.setVisible(false);
            batchConvertButton.setDisable(false);
            batchStatusLabel.setText("Batch stopped due to an error.");
            batchStatusLabel.setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold;");
        });

        new Thread(batchTask).start();
    }

    // =========================================================================
    // 7. FORMATS DIALOG & UTILITIES
    // =========================================================================

    private void showFormatsDialog() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(primaryStage);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("Supported Formats & Engines");

        VBox content = new VBox(16);
        content.setPadding(new Insets(20));
        content.setPrefWidth(540);

        Label header = new Label("Universal Format Compatibility");
        header.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #ffffff;");

        VBox imgSection = new VBox(6);
        Label imgTitle = new Label("1. IMAGE FORMATS (Powered by TwelveMonkeys & Java AWT)");
        imgTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #818cf8;");
        Label imgList = new Label("• PNG (.png): Lossless, full alpha transparency, resizing\n" +
                "• JPEG (.jpg, .jpeg): Lossy compression, quality sliders, rotation\n" +
                "• WebP (.webp): Ultra-high modern web compression\n" +
                "• PDF (.pdf): Image-to-PDF & PDF-to-Image multi-page rasterization\n" +
                "• GIF (.gif), BMP (.bmp), TIFF (.tiff, .tif): Quantization and legacy support");
        imgList.setStyle("-fx-font-size: 12px; -fx-text-fill: #cbd5e1;");
        imgSection.getChildren().addAll(imgTitle, imgList);

        VBox docSection = new VBox(6);
        Label docTitle = new Label("2. DOCUMENT FORMATS (Powered by Apache POI & Apache PDFBox 3.0)");
        docTitle.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #34d399;");
        Label docList = new Label("• PDF (.pdf): Clean typography, tables, headings, code blocks\n" +
                "• Word DOCX (.docx): Rich styles, bullet lists, shaded tables\n" +
                "• Word DOC (.doc): Word 97-2004 binary format support\n" +
                "• OpenDocument ODT (.odt): Open-standard XML packaging\n" +
                "• HTML (.html, .htm): Modern responsive HTML5 markup\n" +
                "• Markdown (.md): GitHub Flavored Markdown with syntax tables\n" +
                "• Plain Text (.txt): Universal structured ASCII text");
        docList.setStyle("-fx-font-size: 12px; -fx-text-fill: #cbd5e1;");
        docSection.getChildren().addAll(docTitle, docList);

        content.getChildren().addAll(header, imgSection, docSection);

        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        DarkTheme.apply(dialog.getDialogPane().getScene());

        dialog.showAndWait();
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(primaryStage);
        alert.setTitle("conve-ult Error");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        DarkTheme.apply(alert.getDialogPane().getScene());
        alert.showAndWait();
    }

    private static String getBaseName(File f) {
        String name = f.getName();
        int idx = name.lastIndexOf('.');
        return idx > 0 ? name.substring(0, idx) : name;
    }

    private static String getExt(File f) {
        String name = f.getName();
        int idx = name.lastIndexOf('.');
        return idx > 0 ? name.substring(idx + 1).toLowerCase(Locale.ROOT) : "";
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return new DecimalFormat("#,##0.#").format(bytes / Math.pow(1024, exp)) + " " + pre + "B";
    }

    // =========================================================================
    // 8. INNER HELPER MODELS
    // =========================================================================

    public static class FormatChoice {
        public final String label;
        public final Format format;

        public FormatChoice(String label, Format format) {
            this.label = label;
            this.format = format;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public static class BatchItem {
        private final File file;
        private String status = "Queued";

        public BatchItem(File file) {
            this.file = file;
        }

        public File getFile() {
            return file;
        }

        public String getFileName() {
            return file.getName();
        }

        public String getSizeFormatted() {
            return formatBytes(file.length());
        }

        public String getInputFormatName() {
            Format fmt = Format.fromFile(file);
            return fmt != null ? fmt.name() : getExt(file).toUpperCase();
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}
