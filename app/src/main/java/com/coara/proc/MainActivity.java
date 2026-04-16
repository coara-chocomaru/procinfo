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
import android.widget.ListView;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.coara.proc.databinding.ActivityMainBinding;
import com.coara.proc.databinding.DialogLoadingBinding;
import com.coara.proc.databinding.DialogProcInfoBinding;
import com.coara.proc.databinding.DialogProcInfoSmapBinding;

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
    private static final String PATH_PROC_SELF_SMAP = "/proc/self/smaps";
    private static final String PATH_PROC_SELF_AUXV = "/proc/self/auxv";
    private static final int REQUEST_STORAGE_PERMISSION = 1001;
    private static final int BUFFER_SIZE = 16 * 1024;
    private static final int SMAP_CHUNK_CHARS = 4096;

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

        checkAndRequestStoragePermissionOnStartup();
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
            showSmapDialog();
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
            } catch (RemoteException | IOException | InterruptedException e) {
                Log.e(TAG, "Error fetching proc info", e);
                runOnUiThread(() -> {
                    dismissLoadingDialog();
                    Toast.makeText(MainActivity.this, "表示に失敗しました", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showSmapDialog() {
        showLoadingDialog("しばらくお待ちください");

        executor.execute(() -> {
            try {
                List<String> chunks = loadTextChunksDirect(PATH_PROC_SELF_SMAP);
                runOnUiThread(() -> {
                    dismissLoadingDialog();
                    showSmapChunkDialog(chunks);
                });
            } catch (IOException e) {
                Log.e(TAG, "Error loading smaps", e);
                runOnUiThread(() -> {
                    dismissLoadingDialog();
                    Toast.makeText(MainActivity.this, "表示に失敗しました", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private List<String> loadTextChunksDirect(String path) throws IOException {
        ArrayList<String> chunks = new ArrayList<>();
        try (InputStream in = new BufferedInputStream(new FileInputStream(path), BUFFER_SIZE);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), BUFFER_SIZE)) {
            char[] buffer = new char[BUFFER_SIZE];
            StringBuilder pending = new StringBuilder(BUFFER_SIZE * 2);
            int read;
            while ((read = reader.read(buffer)) != -1) {
                pending.append(buffer, 0, read);
                int fullChunkEnd = (pending.length() / SMAP_CHUNK_CHARS) * SMAP_CHUNK_CHARS;
                int offset = 0;
                while (offset < fullChunkEnd) {
                    chunks.add(pending.substring(offset, offset + SMAP_CHUNK_CHARS));
                    offset += SMAP_CHUNK_CHARS;
                }
                if (offset > 0) {
                    pending.delete(0, offset);
                }
            }
            if (pending.length() > 0) {
                chunks.add(pending.toString());
            }
        }
        if (chunks.isEmpty()) {
            chunks.add("");
        }
        return chunks;
    }

    private void showSmapChunkDialog(List<String> chunks) {
        DialogProcInfoSmapBinding dialogBinding = DialogProcInfoSmapBinding.inflate(LayoutInflater.from(this));
        dialogBinding.txtProcInfoTitle.setText("PROC_SELF_SMAP");
        dialogBinding.txtProcInfoMeta.setText("約 " + chunks.size() + " chunks");

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.dialog_proc_info_smap_chunk, chunks) {
            @Override
            public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                TextView view;
                if (convertView instanceof TextView) {
                    view = (TextView) convertView;
                } else {
                    view = (TextView) LayoutInflater.from(MainActivity.this).inflate(R.layout.dialog_proc_info_smap_chunk, parent, false);
                }
                String item = getItem(position);
                view.setTypeface(Typeface.MONOSPACE);
                view.setTextSize(11f);
                view.setTextIsSelectable(true);
                view.setText(item == null ? "" : item);
                return view;
            }
        };
        dialogBinding.listProcInfoChunks.setAdapter(adapter);
        dialogBinding.listProcInfoChunks.setItemsCanFocus(true);
        dialogBinding.listProcInfoChunks.setLongClickable(false);

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

    private String readProcInfo(ProcInfoMethod method) throws RemoteException, IOException {
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
        DialogProcInfoBinding dialogBinding = DialogProcInfoBinding.inflate(LayoutInflater.from(this));
        dialogBinding.txtProcInfo.setText(content);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(dialogBinding.getRoot())
                .create();
        dialogBinding.btnCloseDialog.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
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
            String content = PATH_PROC_SELF_AUXV.equals(path)
                    ? procInfoService.getProcSelfAuxvSummary()
                    : procInfoService.readProcFile(path);
            String fileName = path.substring(1).replace('/', '_') + ".txt";
            zos.putNextEntry(new ZipEntry(fileName));
            byte[] data = (content == null ? "Error" : content).getBytes(StandardCharsets.UTF_8);
            zos.write(data);
            zos.closeEntry();
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

    private void checkAndRequestStoragePermissionOnStartup() {
        if (!checkStoragePermission()) {
            requestStoragePermission();
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
