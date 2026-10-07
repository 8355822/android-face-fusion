package com.pv.androidfacefusion;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.FutureTarget;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements SavedFacesAdapter.OnFaceActionListener {

    private static final String TAG = "MainActivity";

    private enum ImagePickerTarget {
        STANDARD_SOURCE,
        STANDARD_TARGET,
        ADD_SAVED_FACE,
        LIBRARY_TARGET
    }

    // Mode Switcher Views
    private MaterialButtonToggleGroup modeToggleGroup;
    private MaterialButton btnModeStandard, btnModeLibrary;
    private LinearLayout standardSwapContainer, librarySwapContainer;

    // Standard Swap Views
    private ImageView sourceImageView, resultImageView;
    private FaceOverlayImageView targetImageView;
    private HorizontalScrollView targetFaceChipScrollView;
    private ChipGroup targetFaceChipGroup;
    private TextView targetFaceStatusText;
    private List<FaceDetector.Face> targetFacesList = new ArrayList<>();
    private MaterialButton btnSelectSourceLocal, btnSelectSourceUrl;
    private MaterialButton btnSelectTargetLocal, btnSelectTargetUrl;
    private MaterialButton btnProcess, btnReset;

    // Library Views
    private MaterialButton btnAddSavedFace;
    private TextView emptyLibraryText;
    private RecyclerView savedFacesRecyclerView;
    private SavedFacesAdapter savedFacesAdapter;
    private FaceLibraryManager libraryManager;

    private FaceOverlayImageView libraryTargetImageView;
    private MaterialButton btnSelectLibraryTargetLocal, btnSelectLibraryTargetUrl;
    private TextView libraryTargetStatusText;
    private RecyclerView targetMappingRecyclerView;
    private TargetFaceMappingAdapter targetMappingAdapter;
    private MaterialButton btnLibraryProcess, btnLibraryReset;

    // Shared Result & Overlay Views
    private MaterialButton btnSaveResult, btnShareResult;
    private LinearProgressIndicator progressBar;
    private MaterialCardView resultCard;
    private FrameLayout downloadOverlay;
    private TextView overlayTitle, overlayStatus, overlayPercent;
    private LinearProgressIndicator overlayProgress;

    // Bitmaps
    private Bitmap sourceBitmap, targetBitmap, libraryTargetBitmap, resultBitmap;
    private List<FaceDetector.Face> libraryTargetFacesList = new ArrayList<>();

    // Models & Execution
    private FaceDetector faceDetector;
    private FaceEmbedder faceEmbedder;
    private FaceSwapper faceSwapper;
    private FaceFusionProcessor processor;
    private ExecutorService executorService;

    private ImagePickerTarget currentImageTarget = ImagePickerTarget.STANDARD_SOURCE;
    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private ActivityResultLauncher<String> permissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        libraryManager = new FaceLibraryManager(this);

        initViews();
        initModels();
        setupListeners();
        requestPermissions();
        loadSavedFaces();
    }

    private void initViews() {
        // Mode Switcher
        modeToggleGroup = findViewById(R.id.modeToggleGroup);
        btnModeStandard = findViewById(R.id.btnModeStandard);
        btnModeLibrary = findViewById(R.id.btnModeLibrary);
        standardSwapContainer = findViewById(R.id.standardSwapContainer);
        librarySwapContainer = findViewById(R.id.librarySwapContainer);

        // Standard Swap Views
        sourceImageView = findViewById(R.id.sourceImageView);
        targetImageView = findViewById(R.id.targetImageView);
        targetFaceChipScrollView = findViewById(R.id.targetFaceChipScrollView);
        targetFaceChipGroup = findViewById(R.id.targetFaceChipGroup);
        targetFaceStatusText = findViewById(R.id.targetFaceStatusText);
        btnSelectSourceLocal = findViewById(R.id.btnSelectSourceLocal);
        btnSelectSourceUrl = findViewById(R.id.btnSelectSourceUrl);
        btnSelectTargetLocal = findViewById(R.id.btnSelectTargetLocal);
        btnSelectTargetUrl = findViewById(R.id.btnSelectTargetUrl);
        btnProcess = findViewById(R.id.btnProcess);
        btnReset = findViewById(R.id.btnReset);

        // Library Views
        btnAddSavedFace = findViewById(R.id.btnAddSavedFace);
        emptyLibraryText = findViewById(R.id.emptyLibraryText);
        savedFacesRecyclerView = findViewById(R.id.savedFacesRecyclerView);
        savedFacesRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        savedFacesAdapter = new SavedFacesAdapter(this, libraryManager, this);
        savedFacesRecyclerView.setAdapter(savedFacesAdapter);

        libraryTargetImageView = findViewById(R.id.libraryTargetImageView);
        btnSelectLibraryTargetLocal = findViewById(R.id.btnSelectLibraryTargetLocal);
        btnSelectLibraryTargetUrl = findViewById(R.id.btnSelectLibraryTargetUrl);
        libraryTargetStatusText = findViewById(R.id.libraryTargetStatusText);
        targetMappingRecyclerView = findViewById(R.id.targetMappingRecyclerView);
        targetMappingRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        targetMappingAdapter = new TargetFaceMappingAdapter(this);
        targetMappingRecyclerView.setAdapter(targetMappingAdapter);
        btnLibraryProcess = findViewById(R.id.btnLibraryProcess);
        btnLibraryReset = findViewById(R.id.btnLibraryReset);

        // Shared Result & Overlay Views
        resultImageView = findViewById(R.id.resultImageView);
        btnSaveResult = findViewById(R.id.btnSaveResult);
        btnShareResult = findViewById(R.id.btnShareResult);
        progressBar = findViewById(R.id.progressBar);
        resultCard = findViewById(R.id.resultCard);

        downloadOverlay = findViewById(R.id.downloadOverlay);
        overlayTitle = findViewById(R.id.overlayTitle);
        overlayStatus = findViewById(R.id.overlayStatus);
        overlayPercent = findViewById(R.id.overlayPercent);
        overlayProgress = findViewById(R.id.overlayProgress);

        executorService = Executors.newSingleThreadExecutor();

        // Launchers
        imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    if (imageUri != null) {
                        loadImageFromUri(imageUri, currentImageTarget);
                    }
                }
            }
        );

        permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (!isGranted) {
                    Toast.makeText(this, "需要存储权限才能保存图片",
                        Toast.LENGTH_SHORT).show();
                }
            }
        );
    }

    private void loadSavedFaces() {
        executorService.execute(() -> {
            List<SavedFace> savedFaces = libraryManager.getSavedFaces();
            runOnUiThread(() -> {
                savedFacesAdapter.setSavedFaces(savedFaces);
                if (savedFaces.isEmpty()) {
                    emptyLibraryText.setVisibility(View.VISIBLE);
                    savedFacesRecyclerView.setVisibility(View.GONE);
                } else {
                    emptyLibraryText.setVisibility(View.GONE);
                    savedFacesRecyclerView.setVisibility(View.VISIBLE);
                }
                // Update mapping dropdowns if target image loaded
                if (libraryTargetBitmap != null && !libraryTargetFacesList.isEmpty()) {
                    targetMappingAdapter.updateSavedFaces(savedFaces);
                }
            });
        });
    }

    private void showOverlay(String title, String status) {
        downloadOverlay.setVisibility(View.VISIBLE);
        overlayTitle.setText(title);
        overlayStatus.setText(status);
        overlayPercent.setText("");
        overlayProgress.setIndeterminate(true);
    }

    private void updateOverlay(String status, int progress) {
        overlayStatus.setText(status);
        if (progress >= 0) {
            overlayProgress.setIndeterminate(false);
            overlayProgress.setProgressCompat(progress, true);
            overlayPercent.setText(progress + "%");
        } else {
            overlayProgress.setIndeterminate(true);
            overlayPercent.setText("");
        }
    }

    private void hideOverlay() {
        downloadOverlay.setVisibility(View.GONE);
    }

    private void initModels() {
        btnProcess.setEnabled(false);
        btnLibraryProcess.setEnabled(false);

        executorService.execute(() -> {
            try {
                ModelDownloader downloader = new ModelDownloader(this);
                boolean needsDownload = !downloader.areAllModelsDownloaded();

                runOnUiThread(() -> {
                    if (needsDownload) {
                        showOverlay("正在准备 AI 模型", "准备下载...");
                    } else {
                        showOverlay("正在加载 AI 模型", "初始化中...");
                    }
                });

                if (needsDownload) {
                    downloader.setCallback(new ModelDownloader.DownloadCallback() {
                        @Override
                        public void onProgress(String modelName, int progress) {
                            String displayName = getModelDisplayName(modelName);
                            runOnUiThread(() -> updateOverlay("正在下载 " + displayName, progress));
                        }

                        @Override
                        public void onComplete(String modelName) {
                            String displayName = getModelDisplayName(modelName);
                            runOnUiThread(() -> updateOverlay(displayName + " 已下载", 100));
                        }

                        @Override
                        public void onError(String modelName, String error) {
                            Log.e(TAG, "Download error: " + modelName + " - " + error);
                        }
                    });
                }

                runOnUiThread(() -> updateOverlay(needsDownload
                        ? "正在下载人脸检测模型（约 16 MB）"
                        : "正在加载人脸检测模型", -1));
                faceDetector = new FaceDetector(this);
                faceDetector.initialize();

                runOnUiThread(() -> updateOverlay(needsDownload
                        ? "正在下载人脸特征模型（约 166 MB）"
                        : "正在加载人脸特征模型", -1));
                faceEmbedder = new FaceEmbedder(this);
                faceEmbedder.initialize();

                runOnUiThread(() -> updateOverlay(needsDownload
                        ? "正在下载换脸模型（约 553 MB）"
                        : "正在加载换脸模型", -1));
                faceSwapper = new FaceSwapper(this);
                faceSwapper.initialize();

                processor = new FaceFusionProcessor(faceDetector, faceEmbedder, faceSwapper);

                runOnUiThread(() -> {
                    hideOverlay();
                    btnProcess.setEnabled(true);
                    btnLibraryProcess.setEnabled(true);
                    Toast.makeText(this, "模型加载完成！", Toast.LENGTH_SHORT).show();
                    if (targetBitmap != null) {
                        detectTargetFacesAsync(targetBitmap);
                    }
                    if (libraryTargetBitmap != null) {
                        detectLibraryTargetFacesAsync(libraryTargetBitmap);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    hideOverlay();
                    showError("模型加载失败。\n\n" +
                        "请检查：\n" +
                        "1. 网络连接是否正常\n" +
                        "2. 是否至少有 800MB 可用空间\n" +
                        "3. 防火墙是否允许访问 HuggingFace\n\n" +
                        "错误：" + e.getMessage());
                });
            }
        });
    }

    private String getModelDisplayName(String modelName) {
        switch (modelName) {
            case "det_10g.onnx": return "人脸检测模型";
            case "w600k_r50.onnx": return "人脸特征模型";
            case "inswapper_128.onnx": return "换脸模型";
            default: return modelName;
        }
    }

    private void setupListeners() {
        // Mode Switcher Listener
        modeToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btnModeStandard) {
                standardSwapContainer.setVisibility(View.VISIBLE);
                librarySwapContainer.setVisibility(View.GONE);
            } else if (checkedId == R.id.btnModeLibrary) {
                standardSwapContainer.setVisibility(View.GONE);
                librarySwapContainer.setVisibility(View.VISIBLE);
                loadSavedFaces();
            }
        });

        // Standard Swap Listeners
        btnSelectSourceLocal.setOnClickListener(v -> {
            currentImageTarget = ImagePickerTarget.STANDARD_SOURCE;
            openImagePicker();
        });

        btnSelectSourceUrl.setOnClickListener(v -> {
            currentImageTarget = ImagePickerTarget.STANDARD_SOURCE;
            showUrlInputDialog();
        });

        btnSelectTargetLocal.setOnClickListener(v -> {
            currentImageTarget = ImagePickerTarget.STANDARD_TARGET;
            openImagePicker();
        });

        btnSelectTargetUrl.setOnClickListener(v -> {
            currentImageTarget = ImagePickerTarget.STANDARD_TARGET;
            showUrlInputDialog();
        });

        btnProcess.setOnClickListener(v -> processFaceFusion());
        btnReset.setOnClickListener(v -> resetStandardSession());

        // Face Library Listeners
        btnAddSavedFace.setOnClickListener(v -> showAddSavedFaceSourceDialog());

        btnSelectLibraryTargetLocal.setOnClickListener(v -> {
            currentImageTarget = ImagePickerTarget.LIBRARY_TARGET;
            openImagePicker();
        });

        btnSelectLibraryTargetUrl.setOnClickListener(v -> {
            currentImageTarget = ImagePickerTarget.LIBRARY_TARGET;
            showUrlInputDialog();
        });

        btnLibraryProcess.setOnClickListener(v -> processLibraryFaceFusion());
        btnLibraryReset.setOnClickListener(v -> resetLibrarySession());

        // Result & Preview Listeners
        btnSaveResult.setOnClickListener(v -> saveResult());
        btnShareResult.setOnClickListener(v -> shareResult());

        sourceImageView.setOnClickListener(v -> openImagePreview(sourceBitmap));
        resultImageView.setOnClickListener(v -> openImagePreview(resultBitmap));
        targetImageView.setOnFaceSelectedListener(selectedIndices -> syncChipsWithOverlay());
    }

    private void openImagePreview(Bitmap bitmap) {
        if (bitmap == null) return;
        ImagePreviewActivity.setPendingBitmap(bitmap);
        startActivity(new Intent(this, ImagePreviewActivity.class));
    }

    private void resetStandardSession() {
        if (sourceBitmap != null) { sourceBitmap.recycle(); sourceBitmap = null; }
        if (targetBitmap != null) { targetBitmap.recycle(); targetBitmap = null; }
        if (resultBitmap != null) { resultBitmap.recycle(); resultBitmap = null; }

        sourceImageView.setImageDrawable(null);
        targetImageView.setImageDrawable(null);
        targetImageView.setFaces(null);
        targetFacesList.clear();

        targetFaceChipGroup.removeAllViews();
        targetFaceStatusText.setVisibility(View.GONE);
        targetFaceChipScrollView.setVisibility(View.GONE);

        resultImageView.setImageDrawable(null);
        resultCard.setVisibility(View.GONE);
    }

    private void resetLibrarySession() {
        if (libraryTargetBitmap != null) { libraryTargetBitmap.recycle(); libraryTargetBitmap = null; }
        if (resultBitmap != null) { resultBitmap.recycle(); resultBitmap = null; }

        libraryTargetImageView.setImageDrawable(null);
        libraryTargetImageView.setFaces(null);
        libraryTargetFacesList.clear();

        libraryTargetStatusText.setVisibility(View.GONE);
        targetMappingRecyclerView.setVisibility(View.GONE);
        targetMappingAdapter.setData(null, null, null);

        resultImageView.setImageDrawable(null);
        resultCard.setVisibility(View.GONE);
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private void showAddSavedFaceSourceDialog() {
        String[] options = {"手机相册图片", "网络图片网址"};
        new AlertDialog.Builder(this)
            .setTitle("添加人脸到库")
            .setItems(options, (dialog, which) -> {
                if (which == 0) {
                    currentImageTarget = ImagePickerTarget.ADD_SAVED_FACE;
                    openImagePicker();
                } else {
                    currentImageTarget = ImagePickerTarget.ADD_SAVED_FACE;
                    showUrlInputDialog();
                }
            })
            .show();
    }

    private void showUrlInputDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("输入图片网址");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        input.setHint("https://example.com/image.jpg");
        builder.setView(input);

        builder.setPositiveButton("加载", (dialog, which) -> {
            String url = input.getText().toString().trim();
            if (!url.isEmpty()) {
                loadImageFromUrl(url, currentImageTarget);
            } else {
                Toast.makeText(this, "请输入有效的网址", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("取消", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void loadImageFromUri(Uri uri, ImagePickerTarget target) {
        executorService.execute(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(uri);
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                inputStream.close();

                if (bitmap.getConfig() != Bitmap.Config.ARGB_8888) {
                    Bitmap converted = bitmap.copy(Bitmap.Config.ARGB_8888, false);
                    bitmap.recycle();
                    bitmap = converted;
                }

                if (bitmap.getWidth() > 1024 || bitmap.getHeight() > 1024) {
                    bitmap = ImageUtils.resizeImage(bitmap, 1024);
                }

                Bitmap finalBitmap = bitmap;
                runOnUiThread(() -> handleLoadedImage(finalBitmap, target));
            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> showError("图片加载失败：" + e.getMessage()));
            }
        });
    }

    private void loadImageFromUrl(String url, ImagePickerTarget target) {
        progressBar.setVisibility(View.VISIBLE);

        executorService.execute(() -> {
            try {
                FutureTarget<Bitmap> futureTarget = Glide.with(this)
                    .asBitmap()
                    .load(url)
                    .submit();

                Bitmap bitmap = futureTarget.get();

                if (bitmap.getConfig() != Bitmap.Config.ARGB_8888) {
                    Bitmap converted = bitmap.copy(Bitmap.Config.ARGB_8888, false);
                    bitmap.recycle();
                    bitmap = converted;
                }

                if (bitmap.getWidth() > 1024 || bitmap.getHeight() > 1024) {
                    bitmap = ImageUtils.resizeImage(bitmap, 1024);
                }

                Bitmap finalBitmap = bitmap;
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    handleLoadedImage(finalBitmap, target);
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    showError("从网址加载图片失败：" + e.getMessage());
                });
            }
        });
    }

    private void handleLoadedImage(Bitmap bitmap, ImagePickerTarget target) {
        switch (target) {
            case STANDARD_SOURCE:
                sourceBitmap = bitmap;
                sourceImageView.setImageBitmap(bitmap);
                break;
            case STANDARD_TARGET:
                targetBitmap = bitmap;
                targetImageView.setImageBitmap(bitmap);
                detectTargetFacesAsync(bitmap);
                break;
            case ADD_SAVED_FACE:
                processAddFaceToLibraryAsync(bitmap);
                break;
            case LIBRARY_TARGET:
                libraryTargetBitmap = bitmap;
                libraryTargetImageView.setImageBitmap(bitmap);
                detectLibraryTargetFacesAsync(bitmap);
                break;
        }
    }

    private void processAddFaceToLibraryAsync(Bitmap bitmap) {
        if (faceDetector == null || faceEmbedder == null) {
            Toast.makeText(this, "AI 模型还在加载中，请稍候", Toast.LENGTH_SHORT).show();
            return;
        }

        showOverlay("正在添加到人脸库", "正在检测人脸并提取特征...");

        executorService.execute(() -> {
            try {
                List<FaceDetector.Face> faces = faceDetector.detectFaces(bitmap);
                if (faces.isEmpty()) {
                    runOnUiThread(() -> {
                        hideOverlay();
                        showError("所选图片中未检测到人脸，请换一张五官清晰的图片。");
                    });
                    return;
                }

                if (faces.size() == 1) {
                    extractAndPromptSaveFace(bitmap, faces.get(0));
                } else {
                    runOnUiThread(() -> {
                        hideOverlay();
                        showMultiFaceSelectionDialogForLibrary(bitmap, faces);
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    hideOverlay();
                    showError("人脸处理失败：" + e.getMessage());
                });
            }
        });
    }

    private void showMultiFaceSelectionDialogForLibrary(Bitmap bitmap, List<FaceDetector.Face> faces) {
        String[] items = new String[faces.size()];
        for (int i = 0; i < faces.size(); i++) {
            items[i] = "👤 人脸 " + (i + 1) + "（置信度：" + Math.round(faces.get(i).score * 100) + "%）";
        }

        new AlertDialog.Builder(this)
            .setTitle("选择要保存的人脸")
            .setItems(items, (dialog, which) -> {
                showOverlay("正在添加到人脸库", "正在提取人脸特征...");
                executorService.execute(() -> {
                    try {
                        extractAndPromptSaveFace(bitmap, faces.get(which));
                    } catch (Exception e) {
                        e.printStackTrace();
                        runOnUiThread(() -> {
                            hideOverlay();
                            showError("人脸特征提取失败：" + e.getMessage());
                        });
                    }
                });
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void extractAndPromptSaveFace(Bitmap bitmap, FaceDetector.Face face) throws Exception {
        // Align face and extract embedding
        Bitmap alignedFace = ImageUtils.alignFace(bitmap, face.landmarks, 112);
        float[] embedding = faceEmbedder.getEmbedding(alignedFace);
        alignedFace.recycle(); // Free aligned face memory immediately

        // Crop face for thumbnail
        Bitmap faceCrop = cropFaceCrop(bitmap, face.bbox);

        runOnUiThread(() -> {
            hideOverlay();
            promptFaceNameAndSave(faceCrop, embedding);
        });
    }

    private Bitmap cropFaceCrop(Bitmap src, RectF bbox) {
        try {
            int left = Math.max(0, (int) bbox.left);
            int top = Math.max(0, (int) bbox.top);
            int right = Math.min(src.getWidth(), (int) bbox.right);
            int bottom = Math.min(src.getHeight(), (int) bbox.bottom);

            int width = right - left;
            int height = bottom - top;

            if (width > 0 && height > 0) {
                return Bitmap.createBitmap(src, left, top, width, height);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return src;
    }

    private void promptFaceNameAndSave(Bitmap faceCrop, float[] embedding) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("给人脸命名");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setHint("例如：张三、妈妈、某明星");
        builder.setView(input);

        builder.setPositiveButton("保存", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                name = "人脸 " + (savedFacesAdapter.getItemCount() + 1);
            }
            final String finalName = name;
            executorService.execute(() -> {
                libraryManager.saveFace(finalName, faceCrop, embedding);
                runOnUiThread(() -> {
                    Toast.makeText(this, "已保存人脸「" + finalName + "」到库中！", Toast.LENGTH_SHORT).show();
                    loadSavedFaces();
                });
            });
        });

        builder.setNegativeButton("取消", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    @Override
    public void onEditFaceName(SavedFace face) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("重命名人脸");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setText(face.getName());
        builder.setView(input);

        builder.setPositiveButton("更新", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!newName.isEmpty()) {
                executorService.execute(() -> {
                    libraryManager.updateFaceName(face.getId(), newName);
                    runOnUiThread(this::loadSavedFaces);
                });
            }
        });

        builder.setNegativeButton("取消", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    @Override
    public void onDeleteFace(SavedFace face) {
        new AlertDialog.Builder(this)
            .setTitle("删除人脸")
            .setMessage("确定要从人脸库中删除「" + face.getName() + "」吗？")
            .setPositiveButton("删除", (dialog, which) -> {
                executorService.execute(() -> {
                    libraryManager.deleteFace(face.getId());
                    runOnUiThread(this::loadSavedFaces);
                });
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void detectTargetFacesAsync(Bitmap bitmap) {
        if (faceDetector == null || bitmap == null) return;
        executorService.execute(() -> {
            try {
                List<FaceDetector.Face> faces = faceDetector.detectFaces(bitmap);
                runOnUiThread(() -> updateTargetFaceSelectionUI(faces));
            } catch (Exception e) {
                Log.e(TAG, "Error detecting target faces", e);
                runOnUiThread(() -> {
                    if (targetFaceStatusText != null) targetFaceStatusText.setVisibility(View.GONE);
                    if (targetFaceChipScrollView != null) targetFaceChipScrollView.setVisibility(View.GONE);
                });
            }
        });
    }

    private boolean isSyncingChips = false;

    private void updateTargetFaceSelectionUI(List<FaceDetector.Face> faces) {
        targetFacesList = (faces != null) ? faces : new ArrayList<>();
        targetImageView.setFaces(targetFacesList);

        targetFaceChipGroup.removeAllViews();
        targetFaceChipGroup.setOnCheckedStateChangeListener(null);

        if (targetFacesList.isEmpty()) {
            targetFaceStatusText.setText("目标图片中未检测到人脸");
            targetFaceStatusText.setVisibility(View.VISIBLE);
            targetFaceChipScrollView.setVisibility(View.GONE);
            return;
        }

        targetFaceStatusText.setVisibility(View.VISIBLE);

        if (targetFacesList.size() == 1) {
            targetFaceStatusText.setText("目标图片中检测到 1 张人脸：");
            targetFaceChipScrollView.setVisibility(View.VISIBLE);

            Chip chip = createFaceChip("👤 人脸 1", 0);
            chip.setChecked(true);
            targetFaceChipGroup.addView(chip);
        } else {
            targetFaceStatusText.setText(targetFacesList.size() + " 张人脸。点击图片上的人脸或下方标签可切换选择：");
            targetFaceChipScrollView.setVisibility(View.VISIBLE);

            Chip allChip = createFaceChip("✨ 全选（" + targetFacesList.size() + ")", -1);
            allChip.setChecked(true);
            targetFaceChipGroup.addView(allChip);

            for (int i = 0; i < targetFacesList.size(); i++) {
                Chip chip = createFaceChip("👤 人脸 " + (i + 1), i);
                chip.setChecked(true);
                targetFaceChipGroup.addView(chip);
            }
        }

        for (int i = 0; i < targetFaceChipGroup.getChildCount(); i++) {
            View child = targetFaceChipGroup.getChildAt(i);
            if (child instanceof Chip && child.getTag() instanceof Integer) {
                Chip chip = (Chip) child;
                int index = (Integer) chip.getTag();
                chip.setOnClickListener(v -> {
                    if (isSyncingChips) return;
                    if (index == -1) {
                        if (chip.isChecked()) {
                            targetImageView.setSelectedFaceIndex(-1);
                        } else {
                            targetImageView.setSelectedFaceIndices(new HashSet<>());
                        }
                    } else {
                        targetImageView.toggleFaceIndex(index);
                    }
                    syncChipsWithOverlay();
                });
            }
        }
    }

    private Chip createFaceChip(String label, int index) {
        Chip chip = new Chip(this);
        chip.setText(label);
        chip.setCheckable(true);
        chip.setClickable(true);
        chip.setTag(index);
        chip.setId(View.generateViewId());
        return chip;
    }

    private void syncChipsWithOverlay() {
        if (targetFaceChipGroup == null || targetFacesList == null) return;
        isSyncingChips = true;
        Set<Integer> selected = targetImageView.getSelectedFaceIndices();
        boolean allSelected = (selected.size() == targetFacesList.size() && !targetFacesList.isEmpty());

        for (int i = 0; i < targetFaceChipGroup.getChildCount(); i++) {
            View child = targetFaceChipGroup.getChildAt(i);
            if (child instanceof Chip && child.getTag() instanceof Integer) {
                Chip chip = (Chip) child;
                int tag = (Integer) chip.getTag();
                if (tag == -1) {
                    chip.setChecked(allSelected);
                } else {
                    chip.setChecked(selected.contains(tag));
                }
            }
        }
        isSyncingChips = false;
    }

    private void detectLibraryTargetFacesAsync(Bitmap bitmap) {
        if (faceDetector == null || bitmap == null) return;
        executorService.execute(() -> {
            try {
                List<FaceDetector.Face> faces = faceDetector.detectFaces(bitmap);
                libraryTargetFacesList = (faces != null) ? faces : new ArrayList<>();
                List<SavedFace> savedFaces = libraryManager.getSavedFaces();

                runOnUiThread(() -> {
                    libraryTargetImageView.setFaces(libraryTargetFacesList);
                    if (libraryTargetFacesList.isEmpty()) {
                        libraryTargetStatusText.setText("目标图片中未检测到人脸");
                        libraryTargetStatusText.setVisibility(View.VISIBLE);
                        targetMappingRecyclerView.setVisibility(View.GONE);
                    } else {
                        libraryTargetStatusText.setText(libraryTargetFacesList.size() + " 张人脸。请为每张人脸选择要用的库中人脸：");
                        libraryTargetStatusText.setVisibility(View.VISIBLE);
                        targetMappingRecyclerView.setVisibility(View.VISIBLE);
                        targetMappingAdapter.setData(bitmap, libraryTargetFacesList, savedFaces);
                    }
                });
            } catch (Exception e) {
                Log.e(TAG, "Error detecting library target faces", e);
                runOnUiThread(() -> {
                    libraryTargetStatusText.setVisibility(View.GONE);
                    targetMappingRecyclerView.setVisibility(View.GONE);
                });
            }
        });
    }

    private void processFaceFusion() {
        if (sourceBitmap == null || targetBitmap == null) {
            Toast.makeText(this, "请先选择源图片和目标图片",
                Toast.LENGTH_SHORT).show();
            return;
        }

        if (processor == null) {
            Toast.makeText(this, "模型还在加载中，请稍候",
                Toast.LENGTH_SHORT).show();
            return;
        }

        final Set<Integer> selectedFaceIndices = targetImageView.getSelectedFaceIndices();
        if (selectedFaceIndices == null || selectedFaceIndices.isEmpty()) {
            Toast.makeText(this, "请至少选择一张要替换的人脸",
                Toast.LENGTH_SHORT).show();
            return;
        }

        btnProcess.setEnabled(false);
        resultCard.setVisibility(View.GONE);
        showOverlay("正在换脸", "正在换脸...");

        executorService.execute(() -> {
            try {
                runOnUiThread(() -> updateOverlay("正在换脸...", -1));
                Bitmap result = processor.processFaceFusion(sourceBitmap, targetBitmap, selectedFaceIndices);
                resultBitmap = result;

                runOnUiThread(() -> {
                    hideOverlay();
                    btnProcess.setEnabled(true);
                    resultImageView.setImageBitmap(result);
                    resultCard.setVisibility(View.VISIBLE);
                    Toast.makeText(this, "换脸完成！", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    hideOverlay();
                    btnProcess.setEnabled(true);
                    showError("换脸失败：" + e.getMessage());
                });
            }
        });
    }

    private void processLibraryFaceFusion() {
        if (libraryTargetBitmap == null) {
            Toast.makeText(this, "请先选择目标照片", Toast.LENGTH_SHORT).show();
            return;
        }

        if (processor == null) {
            Toast.makeText(this, "模型还在加载中，请稍候", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<Integer, SavedFace> selectedMapping = targetMappingAdapter.getSelectedMapping();
        if (selectedMapping.isEmpty()) {
            Toast.makeText(this, "请至少从人脸库中选择一张人脸替换到目标照片",
                Toast.LENGTH_LONG).show();
            return;
        }

        Map<Integer, float[]> embeddingMap = new HashMap<>();
        for (Map.Entry<Integer, SavedFace> entry : selectedMapping.entrySet()) {
            if (entry.getValue() != null) {
                embeddingMap.put(entry.getKey(), entry.getValue().getEmbedding());
            }
        }

        if (embeddingMap.isEmpty()) {
            Toast.makeText(this, "还没有指定要替换的人脸", Toast.LENGTH_SHORT).show();
            return;
        }

        btnLibraryProcess.setEnabled(false);
        resultCard.setVisibility(View.GONE);
        showOverlay("正在用库中人脸换脸", "正在把指定的人脸替换到目标照片...");

        executorService.execute(() -> {
            try {
                runOnUiThread(() -> updateOverlay("正在替换指定的人脸...", -1));
                Bitmap result = processor.processFaceFusionWithMapping(libraryTargetBitmap, embeddingMap);
                resultBitmap = result;

                runOnUiThread(() -> {
                    hideOverlay();
                    btnLibraryProcess.setEnabled(true);
                    resultImageView.setImageBitmap(result);
                    resultCard.setVisibility(View.VISIBLE);
                    Toast.makeText(this, "多人脸替换完成！", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    hideOverlay();
                    btnLibraryProcess.setEnabled(true);
                    showError("使用人脸库换脸失败：" + e.getMessage());
                });
            }
        });
    }

    private void saveResult() {
        if (resultBitmap == null) {
            Toast.makeText(this, "没有可保存的结果", Toast.LENGTH_SHORT).show();
            return;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                return;
            }
        }

        executorService.execute(() -> {
            try {
                String fileName = "face_fusion_" + System.currentTimeMillis() + ".jpg";

                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);

                Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    OutputStream out = getContentResolver().openOutputStream(uri);
                    resultBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out);
                    out.close();

                    runOnUiThread(() ->
                        Toast.makeText(this, "图片已保存到「图片」文件夹", Toast.LENGTH_LONG).show()
                    );
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> showError("保存图片失败：" + e.getMessage()));
            }
        });
    }

    private void shareResult() {
        if (resultBitmap == null) {
            Toast.makeText(this, "没有可分享的结果", Toast.LENGTH_SHORT).show();
            return;
        }

        executorService.execute(() -> {
            try {
                File shareDir = new File(getCacheDir(), "shared_images");
                if (!shareDir.exists()) shareDir.mkdirs();
                File shareFile = new File(shareDir, "face_fusion_share.jpg");

                FileOutputStream out = new FileOutputStream(shareFile);
                resultBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out);
                out.close();

                Uri contentUri = FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", shareFile);

                runOnUiThread(() -> {
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("image/jpeg");
                    shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    startActivity(Intent.createChooser(shareIntent, "分享换脸结果"));
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> showError("分享图片失败：" + e.getMessage()));
            }
        });
    }

    private void requestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET)
            != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.INTERNET);
        }
    }

    private void showError(String message) {
        new AlertDialog.Builder(this)
            .setTitle("错误")
            .setMessage(message)
            .setPositiveButton("确定", null)
            .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown();

        if (faceDetector != null) faceDetector.close();
        if (faceEmbedder != null) faceEmbedder.close();
        if (faceSwapper != null) faceSwapper.close();

        if (sourceBitmap != null) sourceBitmap.recycle();
        if (targetBitmap != null) targetBitmap.recycle();
        if (libraryTargetBitmap != null) libraryTargetBitmap.recycle();
        if (resultBitmap != null) resultBitmap.recycle();
    }
}
