package com.coara.proc;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.graphics.Typeface;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.coara.proc.databinding.ActivityMainBinding;
import com.coara.proc.databinding.DialogLoadingBinding;
import com.coara.proc.databinding.DialogProcInfoBinding;
import com.coara.proc.databinding.DialogProcInfoLargeBinding;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private static final String PATH_PROC_SELF_WCHAN = "/proc/self/wchan";
    private static final String PATH_PROC_SELF_AUXV = "/proc/self/auxv";
    private static final String PATH_PROC_SELF_SMAPS = "/proc/self/smaps";
    private static final int REQUEST_STORAGE_PERMISSION = 1001;
    private static final int BUFFER_SIZE = 16 * 1024;
    private static final int LARGE_TEXT_THRESHOLD = 256 * 1024;
    private static final int LARGE_TEXT_CHUNK_CHARS = 8192;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private IProcInfoService procInfoService;
    private ActivityMainBinding binding;
    private AlertDialog loadingDialog;
    private boolean serviceBound;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            procInfoService = IProcInfoService.Stub.asInterface(service);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            procInfoService = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Intent intent = new Intent(this, ProcInfoService.class);
        serviceBound = bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        binding.btnProcVersion.setOnClickListener(v -> showProcInfo(ProcInfoMethod.PROC_VERSION));
        binding.btnProcCPUInfo.setOnClickListener(v -> showProcInfo(ProcInfoMethod.PROC_CPUINFO));
        binding.btnProcMemInfo.setOnClickListener(v -> showProcInfo(ProcInfoMethod.PROC_MEMINFO));
        binding.btnProcSelfStatus.setOnClickListener(v -> showProcInfo(ProcInfoMethod.PROC_SELF_STATUS));
        binding.btnProcSelfMaps.setOnClickListener(v -> showProcInfo(ProcInfoMethod.PROC_SELF_MAPS));
        binding.btnProcSelfMountinfo.setOnClickListener(v -> showProcInfo(ProcInfoMethod.PROC_SELF_MOUNTINFO));
        binding.btnProcSelfMounts.setOnClickListener(v -> showProcInfo(ProcInfoMethod.PROC_SELF_MOUNTS));
        binding.btnProcSelfMountstats.setOnClickListener(v -> showProcInfo(ProcInfoMethod.PROC_SELF_MOUNTSTATS));
        binding.btnProcSelfIO.setOnClickListener(v -> showProcInfo(ProcInfoMethod.PROC_SELF_IO));
        binding.btnProcSelfLimits.setOnClickListener(v -> showProcInfo(ProcInfoMethod.PROC_SELF_LIMITS));
        binding.btnProcSelfOomScore.setOnClickListener(v -> showProcInfo(ProcInfoMethod.PROC_SELF_OOM_SCORE));
        binding.btnProcSelfOomAdj.setOnClickListener(v -> showProcInfo(ProcInfoMethod.PROC_SELF_OOM_ADJ));
        binding.btnProcSelfOomScoreAdj.setOnClickListener(v -> showProcInfo(ProcInfoMethod.PROC_SELF_OOM_SCORE_ADJ));
        binding.btnProcSelfSched.setOnClickListener(v -> showProcInfo(ProcInfoMethod.PROC_SELF_SCHED));
        binding.btnProcSelfSchedBoost.setOnClickListener(v -> showProcInfo(ProcInfoMethod.PROC_SELF_SCHED_BOOST));
        binding.btnProcSelfSchedBoostPeriodMs.setOnClickListener(v -> showProcInfo(ProcInfoMethod.PROC_SELF_SCHED_BOOST_PERIOD_MS));
        binding.btnProcSelfSchedGroupId.setOnClickListener(v -> showProcInfo(ProcInfoMethod.PROC_SELF_SCHED_GROUP_ID));
        binding.btnProcSelfSchedInitTaskLoad.setOnClickListener(v -> showProcInfo(ProcInfoMethod.PROC_SELF_SCHED_INIT_TASK_LOAD));
        binding.btnProcSelfSchedWakeUpIdle.setOnClickListener(v -> showProcInfo(ProcInfoMethod.PROC_SELF_SCHED_WAKE_UP_IDLE));
        binding.btnProcSelfSchedstat.setOnClickListener(v -> showProcInfo(ProcInfoMethod.PROC_SELF_SCHEDSTAT));
        binding.btnProcSelfSmap.setOnClickListener(v -> showProcInfo(ProcInfoMethod.PROC_SELF_SMAP));
        binding.btnProcSelfWchan.setOnClickListener(v -> showProcInfo(ProcInfoMethod.PROC_SELF_WCHAN));
        binding.btnProcSelfAuxv.setOnClickListener(v -> showProcInfo(ProcInfoMethod.PROC_SELF_AUXV));

        LinearLayout linearLayoutMain = findViewById(R.id.linearLayoutMain);
        Button allExportButton = new Button(this);
        allExportButton.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        allExportButton.setText("All Export");
        allExportButton.setOnClickListener(v -> exportAllProcInfo());
        linearLayoutMain.addView(allExportButton);
    }

    private enum ProcInfoMethod {
        PROC_VERSION,
        PROC_CPUINFO,
        PROC_MEMINFO,
        PROC_SELF_STATUS,
        PROC_SELF_MAPS,
        PROC_SELF_MOUNTINFO,
        PROC_SELF_MOUNTS,
        PROC_SELF_MOUNTSTATS,
        PROC_SELF_IO,
        PROC_SELF_LIMITS,
        PROC_SELF_OOM_SCORE,
        PROC_SELF_OOM_ADJ,
        PROC_SELF_OOM_SCORE_ADJ,
        PROC_SELF_SCHED,
        PROC_SELF_SCHED_BOOST,
        PROC_SELF_SCHED_BOOST_PERIOD_MS,
        PROC_SELF_SCHED_GROUP_ID,
        PROC_SELF_SCHED_INIT_TASK_LOAD,
        PROC_SELF_SCHED_WAKE_UP_IDLE,
        PROC_SELF_SCHEDSTAT,
        PROC_SELF_SMAP,
        PROC_SELF_WCHAN,
        PROC_SELF_AUXV
    }

    private void showProcInfo(ProcInfoMethod method) {
        if (method == ProcInfoMethod.PROC_SELF_SMAP) {
            showLargeProcFileDialog("PROC_SELF_SMAP", PATH_PROC_SELF_SMAPS);
            return;
        }

        if (procInfoService == null) {
            Log.e(TAG, "Service not bound");
            Toast.makeText(MainActivity.this, "サービスが接続されていません", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoadingDialog("しばらくお待ちください");
        final long startTime = System.currentTimeMillis();
        final long minDisplayTime = 1000L;

        executor.execute(() -> {
            try {
                String result = readProcInfo(method);

                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed < minDisplayTime) {
                    Thread.sleep(minDisplayTime - elapsed);
                }

                final String finalResult = result;
                runOnUiThread(() -> {
                    dismissLoadingDialog();
                    showDialog(method.name(), finalResult);
                });
            } catch (RemoteException | InterruptedException e) {
                Log.e(TAG, "Error fetching proc info", e);
                runOnUiThread(() -> {
                    dismissLoadingDialog();
                    Toast.makeText(MainActivity.this, "表示に失敗しました", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private String readProcInfo(ProcInfoMethod method) throws RemoteException {
        switch (method) {
            case PROC_VERSION:
                return procInfoService.getProcVersion();
            case PROC_CPUINFO:
                return procInfoService.getProcCPUInfo();
            case PROC_MEMINFO:
                return procInfoService.getProcMemInfo();
            case PROC_SELF_STATUS:
                return procInfoService.getProcSelfStatus();
            case PROC_SELF_MAPS:
                return procInfoService.getProcSelfMaps();
            case PROC_SELF_MOUNTINFO:
                return procInfoService.getProcSelfMountinfo();
            case PROC_SELF_MOUNTS:
                return procInfoService.getProcSelfMounts();
            case PROC_SELF_MOUNTSTATS:
                return procInfoService.getProcSelfMountstats();
            case PROC_SELF_IO:
                return procInfoService.getProcSelfIO();
            case PROC_SELF_LIMITS:
                return procInfoService.getProcSelfLimits();
            case PROC_SELF_OOM_SCORE:
                return procInfoService.getProcSelfOomScore();
            case PROC_SELF_OOM_ADJ:
                return procInfoService.getProcSelfOomAdj();
            case PROC_SELF_OOM_SCORE_ADJ:
                return procInfoService.getProcSelfOomScoreAdj();
            case PROC_SELF_SCHED:
                return procInfoService.getProcSelfSched();
            case PROC_SELF_SCHED_BOOST:
                return procInfoService.getProcSelfSchedBoost();
            case PROC_SELF_SCHED_BOOST_PERIOD_MS:
                return procInfoService.getProcSelfSchedBoostPeriodMs();
            case PROC_SELF_SCHED_GROUP_ID:
                return procInfoService.getProcSelfSchedGroupId();
            case PROC_SELF_SCHED_INIT_TASK_LOAD:
                return procInfoService.getProcSelfSchedInitTaskLoad();
            case PROC_SELF_SCHED_WAKE_UP_IDLE:
                return procInfoService.getProcSelfSchedWakeUpIdle();
            case PROC_SELF_SCHEDSTAT:
                return procInfoService.getProcSelfSchedstat();
            case PROC_SELF_SMAP:
                return procInfoService.getProcSelfSmap();
            case PROC_SELF_WCHAN:
                return procInfoService.readProcFile(PATH_PROC_SELF_WCHAN);
            case PROC_SELF_AUXV:
                return procInfoService.getProcSelfAuxvSummary();
            default:
                return "Unsupported proc method";
        }
    }

    private void showDialog(String title, String content) {
        if (content != null && content.length() >= LARGE_TEXT_THRESHOLD) {
            showLargeTextDialog(title, content);
            return;
        }

        DialogProcInfoBinding dialogBinding = DialogProcInfoBinding.inflate(LayoutInflater.from(this));
        dialogBinding.txtProcInfo.setText(content);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(dialogBinding.getRoot())
                .create();
        dialogBinding.btnCloseDialog.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showLargeProcFileDialog(String title, String sourcePath) {
        showLoadingDialog("しばらくお待ちください");
        executor.execute(() -> {
            try {
                List<String> chunks = loadTextChunks(sourcePath);
                long bytes = fileSizeSafe(sourcePath);
                runOnUiThread(() -> {
                    dismissLoadingDialog();
                    showLargeTextDialog(title + " (" + bytes + " bytes)", chunks);
                });
            } catch (IOException e) {
                Log.e(TAG, "Error loading large proc file", e);
                runOnUiThread(() -> {
                    dismissLoadingDialog();
                    Toast.makeText(MainActivity.this, "表示に失敗しました", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showLargeTextDialog(String title, String content) {
        showLargeTextDialog(title, chunkText(content));
    }

    private void showLargeTextDialog(String title, List<String> chunks) {
        DialogProcInfoLargeBinding dialogBinding = DialogProcInfoLargeBinding.inflate(LayoutInflater.from(this));
        dialogBinding.txtProcInfoTitle.setText(title);
        dialogBinding.txtProcInfoMeta.setText("chunks=" + chunks.size() + ", chunkSize≈" + LARGE_TEXT_CHUNK_CHARS + " chars");

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, chunks) {
            @Override
            public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTypeface(Typeface.MONOSPACE);
                view.setTextIsSelectable(true);
                view.setPadding(24, 16, 24, 16);
                view.setText(getItem(position));
                return view;
            }
        };
        dialogBinding.listProcInfoChunks.setAdapter(adapter);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogBinding.getRoot())
                .create();
        dialogBinding.btnCloseDialog.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    android.view.WindowManager.LayoutParams.MATCH_PARENT);
        }
    }

    private List<String> chunkText(String content) {
        ArrayList<String> chunks = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            chunks.add("");
            return chunks;
        }
        int start = 0;
        int length = content.length();
        while (start < length) {
            int end = Math.min(start + LARGE_TEXT_CHUNK_CHARS, length);
            chunks.add(content.substring(start, end));
            start = end;
        }
        return chunks;
    }

    private List<String> loadTextChunks(String sourcePath) throws IOException {
        ArrayList<String> chunks = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(sourcePath), StandardCharsets.UTF_8), BUFFER_SIZE)) {
            char[] buffer = new char[BUFFER_SIZE];
            StringBuilder builder = new StringBuilder(BUFFER_SIZE * 2);
            int read;
            while ((read = reader.read(buffer)) != -1) {
                builder.append(buffer, 0, read);
                while (builder.length() >= LARGE_TEXT_CHUNK_CHARS) {
                    chunks.add(builder.substring(0, LARGE_TEXT_CHUNK_CHARS));
                    builder.delete(0, LARGE_TEXT_CHUNK_CHARS);
                }
            }
            if (builder.length() > 0) {
                chunks.add(builder.toString());
            }
        }
        if (chunks.isEmpty()) {
            chunks.add("");
        }
        return chunks;
    }

    private long fileSizeSafe(String sourcePath) {
        File file = new File(sourcePath);
        return file.exists() ? file.length() : -1L;
    }

    private String resolveProcPath(String path) {
        if ("/proc/self/smap".equals(path)) {
            return PATH_PROC_SELF_SMAPS;
        }
        return path;
    }

    private void writeZipEntryFromProcPath(ZipOutputStream zos, String zipEntryName, String procPath) throws IOException {
        String resolvedPath = resolveProcPath(procPath);
        zos.putNextEntry(new ZipEntry(zipEntryName));
        try (InputStream in = new BufferedInputStream(new FileInputStream(resolvedPath), BUFFER_SIZE)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int len;
            while ((len = in.read(buffer)) != -1) {
                zos.write(buffer, 0, len);
            }
        }
        zos.closeEntry();
    }

    private void exportAllProcInfo() {
        if (procInfoService == null) {
            Toast.makeText(this, "サービスが接続されていません", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!checkStoragePermission()) {
            requestStoragePermission();
            return;
        }

        showLoadingDialog("しばらくお待ちください………");
        executor.execute(() -> {
            try {
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                String zipName = "proc_info_dump_" + timestamp + ".zip";
                File zipFile = createOutputZip(zipName);

                final String savedPath = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                        ? new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), zipName).getAbsolutePath()
                        : zipFile.getAbsolutePath();

                runOnUiThread(() -> {
                    dismissLoadingDialog();
                    Toast.makeText(MainActivity.this, "エクスポート完了: " + savedPath, Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                Log.e(TAG, "Export error", e);
                runOnUiThread(() -> {
                    dismissLoadingDialog();
                    Toast.makeText(MainActivity.this, "エクスポート失敗", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private File createOutputZip(String zipName) throws IOException, RemoteException {
        File tempZip = new File(getCacheDir(), zipName);
        try (OutputStream os = new FileOutputStream(tempZip);
             ZipOutputStream zos = new ZipOutputStream(os)) {
            writeProcEntries(zos);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return publishZipToDownloads(tempZip, zipName);
        }

        File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!downloadDir.exists() && !downloadDir.mkdirs()) {
            throw new IOException("Unable to create Downloads directory");
        }
        File finalFile = new File(downloadDir, zipName);
        if (tempZip.renameTo(finalFile)) {
            return finalFile;
        }

        try (InputStream in = new BufferedInputStream(new FileInputStream(tempZip), BUFFER_SIZE);
             OutputStream out = new FileOutputStream(finalFile)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        tempZip.delete();
        return finalFile;
    }

    private File publishZipToDownloads(File cacheZip, String displayName) throws IOException {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "application/zip");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/");
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

        Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri itemUri = getContentResolver().insert(collection, values);
        if (itemUri == null) {
            throw new IOException("Unable to insert into MediaStore");
        }

        try (InputStream in = new BufferedInputStream(new FileInputStream(cacheZip), BUFFER_SIZE);
             OutputStream out = getContentResolver().openOutputStream(itemUri)) {
            if (out == null) {
                throw new IOException("Unable to open MediaStore output stream");
            }
            byte[] buffer = new byte[BUFFER_SIZE];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
        }

        values.clear();
        values.put(MediaStore.MediaColumns.IS_PENDING, 0);
        getContentResolver().update(itemUri, values, null, null);

        //noinspection ResultOfMethodCallIgnored
        cacheZip.delete();
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), displayName);
    }

    private void writeProcEntries(ZipOutputStream zos) throws IOException, RemoteException {
        final String[] procPaths = {
                "/proc/version",
                "/proc/cpuinfo",
                "/proc/meminfo",
                "/proc/stat",
                "/proc/loadavg",
                "/proc/uptime",
                "/proc/cmdline",
                "/proc/filesystems",
                "/proc/modules",
                "/proc/interrupts",
                "/proc/iomem",
                "/proc/ioports",
                "/proc/softirqs",
                "/proc/buddyinfo",
                "/proc/vmstat",
                "/proc/zoneinfo",
                "/proc/diskstats",
                "/proc/mounts",
                "/proc/self/status",
                "/proc/self/maps",
                "/proc/self/mountinfo",
                "/proc/self/mounts",
                "/proc/self/mountstats",
                "/proc/self/io",
                "/proc/self/limits",
                "/proc/self/oom_score",
                "/proc/self/oom_adj",
                "/proc/self/oom_score_adj",
                "/proc/self/sched",
                "/proc/self/sched_boost",
                "/proc/self/sched_boost_period_ms",
                "/proc/self/sched_group_id",
                "/proc/self/sched_init_task_load",
                "/proc/self/sched_wake_up_idle",
                "/proc/self/schedstat",
                "/proc/self/smap",
                "/proc/self/smaps",
                "/proc/self/cgroup",
                "/proc/self/cpuset",
                "/proc/self/comm",
                PATH_PROC_SELF_WCHAN,
                PATH_PROC_SELF_AUXV,
                "/proc/self/environ"
        };

        for (String path : procPaths) {
            String fileName = path.substring(1).replace('/', '_') + ".txt";
            if (PATH_PROC_SELF_AUXV.equals(path)) {
                String content = procInfoService.getProcSelfAuxvSummary();
                zos.putNextEntry(new ZipEntry(fileName));
                byte[] data = (content == null ? "Error" : content).getBytes(StandardCharsets.UTF_8);
                zos.write(data);
                zos.closeEntry();
                continue;
            }

            try {
                writeZipEntryFromProcPath(zos, fileName, path);
            } catch (IOException directReadFailed) {
                Log.w(TAG, "Direct stream failed for " + path + ", falling back to service", directReadFailed);
                String content = procInfoService.readProcFile(resolveProcPath(path));
                zos.putNextEntry(new ZipEntry(fileName));
                byte[] data = (content == null ? "Error" : content).getBytes(StandardCharsets.UTF_8);
                zos.write(data);
                zos.closeEntry();
            }
        }
    }

    private boolean checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return true;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "権限が許可されました。もう一度All Exportをタップしてください。", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "ストレージ権限が必要です", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showLoadingDialog(String message) {
        runOnUiThread(() -> {
            dismissLoadingDialog();
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            DialogLoadingBinding loadingBinding = DialogLoadingBinding.inflate(LayoutInflater.from(MainActivity.this));
            loadingBinding.txtLoadingMessage.setText(message);
            builder.setView(loadingBinding.getRoot());
            builder.setCancelable(false);
            loadingDialog = builder.create();
            loadingDialog.show();
        });
    }

    private void dismissLoadingDialog() {
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
        }
        loadingDialog = null;
    }

    @Override
    protected void onDestroy() {
        dismissLoadingDialog();
        executor.shutdownNow();
        if (serviceBound) {
            try {
                unbindService(serviceConnection);
            } catch (IllegalArgumentException ignored) {
            }
            serviceBound = false;
        }
        super.onDestroy();
    }
}
