package epic.dumpdex.suianc;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_CODE_FILE = 1001;

    private TextView tvApkPathShow;
    private TextView tvStatusBadge;
    private TextView tvPermissionBadge;
    private MaterialButton btnSelectFile, btnStart, btnReqPerm;
    private ImageButton btnInfo;
    private LinearLayout boxSelectApk;
    private LinearProgressIndicator progressIndicator;
    private TextView tvLog;
    private ScrollView scrollLog;

    private String selectedApkPath = "";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat logTimeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        applyCustomFont();
        checkPermissionsState();
    }

    private void initViews() {
        tvApkPathShow = findViewById(R.id.tvApkPathShow);
        tvStatusBadge = findViewById(R.id.tvStatusBadge);
        tvPermissionBadge = findViewById(R.id.tvPermissionBadge);
        btnSelectFile = findViewById(R.id.btnSelectFile);
        btnStart = findViewById(R.id.btnStart);
        btnReqPerm = findViewById(R.id.btnReqPerm);
        btnInfo = findViewById(R.id.btnInfo);
        boxSelectApk = findViewById(R.id.boxSelectApk);
        progressIndicator = findViewById(R.id.progressIndicator);
        tvLog = findViewById(R.id.tvLog);
        scrollLog = findViewById(R.id.scrollLog);

        updateStatusBadge(false);

        btnInfo.setOnClickListener(v -> {
    // 创建主容器
    LinearLayout container = new LinearLayout(this);
    container.setOrientation(LinearLayout.VERTICAL);
    int padding = dp2px(20);
    container.setPadding(padding, padding, padding, padding);

    // 1. 布局作者
    container.addView(createAboutItem("111.png", "布局：Mt论坛@Shiray\nUID: 155359"));

    // 2. 技术支持 1
    container.addView(createAboutItem("222.png", "技术支持1：Mt论坛@寒风科技\nUID: 111860"));

    // 3. 技术支持 2
    container.addView(createAboutItem("333.png", "技术支持2：Mt论坛@zskj2736472509\nUID: 110361"));

    // 4. 底部提示语
    TextView tvNote = new TextView(this);
    tvNote.setText("排名不分前后");
    tvNote.setTextSize(12);
    tvNote.setTextColor(0xFF79747E);
    LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
    );
    noteParams.topMargin = dp2px(12);
    tvNote.setLayoutParams(noteParams);
    container.addView(tvNote);

    // 弹出对话框
    new MaterialAlertDialogBuilder(this)
            .setTitle("关于")
            .setView(container)
            .setPositiveButton("确定", null)
            .show();
});



        View.OnClickListener selectFileListener = v -> FileUtils.openFilePicker(this, REQ_CODE_FILE);
        boxSelectApk.setOnClickListener(selectFileListener);
        btnSelectFile.setOnClickListener(selectFileListener);

        btnReqPerm.setOnClickListener(v -> requestStoragePermission());

        btnStart.setOnClickListener(v -> {
            if (selectedApkPath.isEmpty() || !new File(selectedApkPath).exists()) {
                Toast.makeText(this, "请先选择有效的 APK 文件！", Toast.LENGTH_SHORT).show();
                return;
            }

            btnStart.setEnabled(false);
            progressIndicator.setVisibility(View.VISIBLE);
            tvLog.setText(""); // 清空 "等待开始..."
            appendLog("正在分析 SO 配置文件...", "info");

            new Thread(() -> checkConfigAndDispatch(selectedApkPath)).start();
        });
    }

    /**
     * 加载 assets/fonts/JetBrainsMono-Regular.ttf 字体
     */
    private void applyCustomFont() {
        try {
            Typeface typeface = Typeface.createFromAsset(getAssets(), "fonts/JetBrainsMono-Regular.ttf");
            tvLog.setTypeface(typeface);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int dp2px(float dpValue) {
        float scale = getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }

    private void applyCapsuleStyle(TextView textView, int colorHex) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(GradientDrawable.RECTANGLE);
        gd.setCornerRadius(dp2px(16));
        int alphaColor = (colorHex & 0x00FFFFFF) | 0x20000000;
        gd.setColor(alphaColor);
        textView.setBackground(gd);
        textView.setTextColor(colorHex);
    }

    private void updateStatusBadge(boolean isReady) {
        if (isReady) {
            tvStatusBadge.setText("✔ 已就绪");
            applyCapsuleStyle(tvStatusBadge, 0xFF6750A4);
        } else {
            tvStatusBadge.setText("✔ 未选择文件");
            applyCapsuleStyle(tvStatusBadge, 0xFF79747E);
        }
    }

    private void updatePermissionBadge(boolean hasPerm) {
        if (hasPerm) {
            tvPermissionBadge.setText("权限: 已获取");
            applyCapsuleStyle(tvPermissionBadge, 0xFF388E3C);
        } else {
            tvPermissionBadge.setText("权限: 未检查");
            applyCapsuleStyle(tvPermissionBadge, 0xFFD32F2F);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CODE_FILE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                selectedApkPath = FileUtils.getPathFromUri(this, uri);
                if (selectedApkPath != null && !selectedApkPath.isEmpty()) {
                    tvApkPathShow.setText(new File(selectedApkPath).getName());
                    updateStatusBadge(true);
                }
            }
        }
    }

    private void checkPermissionsState() {
        boolean hasPermission;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            hasPermission = Environment.isExternalStorageManager();
        } else {
            hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
        updatePermissionBadge(hasPermission);
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivity(intent);
            }
        } else {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, 100);
        }
    }

    private void checkConfigAndDispatch(String apkPath) {
        try (ZipFile zipFile = new ZipFile(apkPath)) {
            JSONObject config = ElfConfigParser.parseConfigFromApk(zipFile, this::appendLog);

            if (config == null) {
                appendLog("未能提取到 SO 中的 Epic 解密配置！", "error");
                resetUi();
                return;
            }

            boolean hasSigProtection = config.has("certificate_md5");

            if (hasSigProtection) {
                mainHandler.post(() -> showModifyMd5Dialog(apkPath, config));
            } else {
                appendLog("未开启签名校验，跳过签名替换", "info");
                new Thread(() -> runUnifiedPipeline(apkPath, config, null)).start();
            }

        } catch (Exception e) {
            appendLog("解析 APK 异常: " + e.getMessage(), "error");
            resetUi();
        }
    }

    private void showModifyMd5Dialog(String apkPath, JSONObject config) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("签名校验重写")
                .setMessage("检测到此应用已开启 certificate_md5 签名校验，是否需要修改签名 MD5？")
                .setPositiveButton("是", (dialog, which) -> showMd5InputDialog(apkPath, config))
                .setNegativeButton("否", (dialog, which) -> {
                    new Thread(() -> runUnifiedPipeline(apkPath, config, null)).start();
                })
                .setCancelable(false)
                .show();
    }

    private void showMd5InputDialog(String apkPath, JSONObject config) {
        final EditText input = new EditText(this);
        input.setHint("32位 MD5 字符串");
        input.setText("e89b158e4bcf988ebd09eb83f5378e87");
        input.setSingleLine(true);

        new MaterialAlertDialogBuilder(this)
                .setTitle("输入签名 MD5")
                .setView(input)
                .setPositiveButton("确定", (dialog, which) -> {
                    String md5 = input.getText().toString().trim();
                    if (md5.length() != 32) {
                        Toast.makeText(this, "MD5 必须为 32 位字符串！", Toast.LENGTH_SHORT).show();
                        resetUi();
                        return;
                    }
                    new Thread(() -> runUnifiedPipeline(apkPath, config, md5)).start();
                })
                .setNegativeButton("取消", (dialog, which) -> {
                    new Thread(() -> runUnifiedPipeline(apkPath, config, null)).start();
                })
                .show();
    }

    private void runUnifiedPipeline(String apkPath, JSONObject config, @Nullable String targetMd5) {
    File apkFile = new File(apkPath);
    
    // 解析文件名（去除扩展名）
    String originalName = apkFile.getName();
    int dotIndex = originalName.lastIndexOf('.');
    String apkName = (dotIndex > 0) ? originalName.substring(0, dotIndex) : originalName;
    
    String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());

    // 获取所选文件所在同级目录
    File baseOutDir = apkFile.getParentFile();
    if (baseOutDir == null || !baseOutDir.exists()) {
        baseOutDir = new File("/sdcard");
    }

    // 输出格式：Name-Time-[还原].zip
    File finalZipFile = new File(baseOutDir, apkName + "-" + timestamp + "-[还原].zip");
    File tempWorkDir = new File(getExternalCacheDir(), "temp_epic_" + timestamp);
    if (!tempWorkDir.exists()) tempWorkDir.mkdirs();

    StringBuilder logTxt = new StringBuilder();
    logTxt.append("目标 APK: ").append(apkFile.getName()).append("\n");
    logTxt.append("处理时间: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date())).append("\n\n");

    String jsonFormatted;
    try {
        jsonFormatted = config.toString(4);
    } catch (Exception e) {
        jsonFormatted = config.toString();
    }
    logTxt.append("原始全局配置 JSON:\n").append(jsonFormatted).append("\n\n");

    String targetSoName = config.optString("so_name", "");

    // 存储修改后的 SO 文件，Key 为原始相对路径 (例如: lib/arm64-v8a/libEPIC.so)
    java.util.Map<String, byte[]> modifiedSoMap = new java.util.HashMap<>();

    try (ZipFile zipFile = new ZipFile(apkFile)) {
        // 1. 重写签名 MD5
        if (targetMd5 != null) {
            updateProgress(20, "正在重写签名 MD5...");
            appendLog("正在替换 certificate_md5 为 " + targetMd5, "info");

            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String entryName = entry.getName();

                boolean isTargetSo = false;
                if (!targetSoName.isEmpty()) {
                    isTargetSo = entryName.endsWith(targetSoName) || entryName.contains(targetSoName);
                } else {
                    isTargetSo = entryName.contains("libEPIC") || (entryName.startsWith("lib/") && entryName.endsWith(".so"));
                }

                if (isTargetSo) {
                    byte[] soBytes = readAllBytes(zipFile.getInputStream(entry));
                    SigBypassModifier.ModifyResult modRes = SigBypassModifier.modifyCertificateMd5(soBytes, targetMd5);

                    logTxt.append("【SO 修改日志 - ").append(entryName).append("】:\n");
                    if (modRes.success) {
                        // 保留完整相对路径写入 Map 内存，同时落地到临时文件夹
                        modifiedSoMap.put(entryName, modRes.modifiedSoData);

                        File modifiedSoFile = new File(tempWorkDir, entryName);
                        File parentFolder = modifiedSoFile.getParentFile();
                        if (parentFolder != null && !parentFolder.exists()) {
                            parentFolder.mkdirs();
                        }
                        try (FileOutputStream fos = new FileOutputStream(modifiedSoFile)) {
                            fos.write(modRes.modifiedSoData);
                        }
                        appendLog("签名修改成功: " + entryName, "success");
                        logTxt.append(modRes.logMessage).append("修改状态: 成功\n\n");
                    } else {
                        appendLog("签名修改跳过/未找到匹配特征: " + modRes.logMessage, "warn");
                        logTxt.append("修改状态: 跳过 (").append(modRes.logMessage).append(")\n\n");
                    }
                }
            }
        }

        // 2. DEX 还原
        if (config.has("dex_protection_method")) {
            updateProgress(40, "解密 DEX 文件中...");
            File dexOutSubDir = new File(tempWorkDir, "DEX");
            if (!dexOutSubDir.exists()) dexOutSubDir.mkdirs();

            DexProcessor.Result dexRes = DexProcessor.processDex(zipFile, config, dexOutSubDir, this::appendLog);
            logTxt.append("DEX: 成功还原 ").append(dexRes.dexCount).append(" 个 DEX 文件\n\n");
        }

        // 3. Assets 解密
        if (config.has("asset_protection_method")) {
            updateProgress(60, "解密 Assets 资源...");
            File assetZipTemp = new File(tempWorkDir, "Assets.zip");
            AssetProcessor.Result assetRes = AssetProcessor.processAssets(zipFile, config, assetZipTemp, this::appendLog);
            logTxt.append("Assets资源保护: 成功解密 ").append(assetRes.processedCount).append(" 个 Asset 资源\n\n");
        }

        // 4. ARSC / Manifest 修复
        if (config.has("resource_string_protection_method")) {
            updateProgress(75, "修复 ARSC 字符串池...");
            ZipEntry arscEntry = zipFile.getEntry("resources.arsc");
            ZipEntry manifestEntry = zipFile.getEntry("AndroidManifest.xml");

            byte[] arscBytes = arscEntry != null ? readAllBytes(zipFile.getInputStream(arscEntry)) : null;
            byte[] manifestBytes = manifestEntry != null ? readAllBytes(zipFile.getInputStream(manifestEntry)) : null;

            File arscOut = new File(tempWorkDir, "resources.arsc");
            File manifestOut = new File(tempWorkDir, "AndroidManifest.xml");

            ArscProcessor.Result arscRes = ArscProcessor.processArscAndManifest(arscBytes, manifestBytes, config, arscOut, manifestOut, this::appendLog);
            logTxt.append("ARSC资源保护: 还原 ").append(arscRes.arscModifiedCount).append(" 处 ARSC 字符串，").append(arscRes.axmlModifiedCount).append(" 处 Manifest 字符串\n\n");
        }

        // 5. AXML 布局解密
        if (config.has("axml_protection_method")) {
            updateProgress(85, "解密 res/ 布局 XML...");
            File resXmlZipTemp = new File(tempWorkDir, "ResXML.zip");
            AxmlProcessor.Result axmlRes = AxmlProcessor.processResXmls(zipFile, config, resXmlZipTemp, this::appendLog);
            logTxt.append("AXML布局保护: 解密 ").append(axmlRes.decryptedCount).append(" 个 XML 布局\n\n");
        }

        // 写入 说明.txt 到临时文件夹
        File logFile = new File(tempWorkDir, "说明.txt");
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(logFile), StandardCharsets.UTF_8))) {
            writer.print(logTxt.toString());
        }

        // 6. 打包所有解密结果及重构后的 SO 到单一压缩包
        updateProgress(95, "正在打包...");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(finalZipFile))) {
            compressFolderToZip(tempWorkDir, tempWorkDir, zos);
        }

        updateProgress(100, "处理完成");
        appendLog("处理完成！结果生成于:\n" + finalZipFile.getAbsolutePath(), "success");

    } catch (Exception e) {
        appendLog("处理过程发生异常: " + e.getMessage(), "error");
    } finally {
        deleteDirRecursively(tempWorkDir);
        appendLog("已清理残留", "info");
        resetUi();
    }
}


    private void compressFolderToZip(File rootDir, File currentFile, ZipOutputStream zos) throws IOException {
        if (currentFile.isDirectory()) {
            File[] files = currentFile.listFiles();
            if (files != null) {
                for (File f : files) compressFolderToZip(rootDir, f, zos);
            }
        } else {
            String entryName = rootDir.toURI().relativize(currentFile.toURI()).getPath();
            zos.putNextEntry(new ZipEntry(entryName));
            try (FileInputStream fis = new FileInputStream(currentFile)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = fis.read(buffer)) > 0) zos.write(buffer, 0, len);
            }
            zos.closeEntry();
        }
    }

    private void deleteDirRecursively(File fileOrDir) {
        if (fileOrDir != null && fileOrDir.exists()) {
            if (fileOrDir.isDirectory()) {
                File[] children = fileOrDir.listFiles();
                if (children != null) {
                    for (File child : children) deleteDirRecursively(child);
                }
            }
            fileOrDir.delete();
        }
    }

    /**
     * 按要求输出格式: [HH:mm:ss] 内容
     */
    private void appendLog(String msg, String type) {
        mainHandler.post(() -> {
            String currentTime = logTimeFormat.format(new Date());
            String logLine = "[" + currentTime + "] " + msg;

            if (tvLog.getText().length() == 0) {
                tvLog.setText(logLine);
            } else {
                tvLog.append("\n" + logLine);
            }
            scrollLog.post(() -> scrollLog.fullScroll(View.FOCUS_DOWN));
        });
    }

    private void updateProgress(int percent, String text) {
        mainHandler.post(() -> progressIndicator.setProgress(percent, true));
    }

    private void resetUi() {
        mainHandler.post(() -> {
            btnStart.setEnabled(true);
            progressIndicator.setVisibility(View.GONE);
        });
    }

    private static byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int len;
        while ((len = is.read(buffer)) != -1) baos.write(buffer, 0, len);
        return baos.toByteArray();
    }

    /**
 * 动态创建包含头像与描述信息的单行 View 布局
 */
private View createAboutItem(String assetImgName, String text) {
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    row.setGravity(android.view.Gravity.CENTER_VERTICAL);
    
    LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
    );
    rowParams.bottomMargin = dp2px(12);
    row.setLayoutParams(rowParams);

    // 头像 ImageView
    android.widget.ImageView ivAvatar = new android.widget.ImageView(this);
    int imgSize = dp2px(40); // 头像宽高 40dp
    LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(imgSize, imgSize);
    imgParams.rightMargin = dp2px(12);
    ivAvatar.setLayoutParams(imgParams);

    // 从 assets 目录读取图片
    try (InputStream is = getAssets().open(assetImgName)) {
        android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(is);
        ivAvatar.setImageBitmap(bitmap);
    } catch (IOException e) {
        e.printStackTrace();
    }

    // 描述 TextView
    TextView tvDesc = new TextView(this);
    tvDesc.setText(text);
    tvDesc.setTextSize(14);
    tvDesc.setTextColor(0xFF1D1B20); // M3 标准暗色文本
    tvDesc.setLayoutParams(new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1.0f
    ));

    row.addView(ivAvatar);
    row.addView(tvDesc);
    return row;
}
}
