package com.coara.proc;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
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
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.coara.proc.databinding.ActivityMainBinding;
import com.coara.proc.databinding.DialogLoadingBinding;
import com.coara.proc.databinding.DialogProcInfoBinding;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";
    private static final String PATH_PROC_SELF_WCHAN = "/proc/self/wchan";
    private static final String PATH_PROC_SELF_AUXV = "/proc/self/auxv";
    private static final int REQUEST_STORAGE_PERMISSION = 1001;
    private static final int BUFFER_SIZE = 16 * 1024;
    private static final long MIN_DIALOG_SPINNER_MS = 220L;

    private static final ProcExportEntry[] EXPORT_ENTRIES = new ProcExportEntry[]{
            new ProcExportEntry("proc_version.txt", "/proc/version", false),
            new ProcExportEntry("proc_cpuinfo.txt", "/proc/cpuinfo", false),
            new ProcExportEntry("proc_meminfo.txt", "/proc/meminfo", false),
            new ProcExportEntry("proc_stat.txt", "/proc/stat", false),
            new ProcExportEntry("proc_loadavg.txt", "/proc/loadavg", false),
            new ProcExportEntry("proc_uptime.txt", "/proc/uptime", false),
            new ProcExportEntry("proc_cmdline.txt", "/proc/cmdline", false),
            new ProcExportEntry("proc_filesystems.txt", "/proc/filesystems", false),
            new ProcExportEntry("proc_modules.txt", "/proc/modules", false),
            new ProcExportEntry("proc_interrupts.txt", "/proc/interrupts", false),
            new ProcExportEntry("proc_iomem.txt", "/proc/iomem", false),
            new ProcExportEntry("proc_ioports.txt", "/proc/ioports", false),
            new ProcExportEntry("proc_softirqs.txt", "/proc/softirqs", false),
            new ProcExportEntry("proc_buddyinfo.txt", "/proc/buddyinfo", false),
            new ProcExportEntry("proc_vmstat.txt", "/proc/vmstat", false),
            new ProcExportEntry("proc_zoneinfo.txt", "/proc/zoneinfo", false),
            new ProcExportEntry("proc_diskstats.txt", "/proc/diskstats", false),
            new ProcExportEntry("proc_mounts.txt", "/proc/mounts", false),
            new ProcExportEntry("proc_self_status.txt", "/proc/self/status", false),
            new ProcExportEntry("proc_self_maps.txt", "/proc/self/maps", false),
            new ProcExportEntry("proc_self_mountinfo.txt", "/proc/self/mountinfo", false),
            new ProcExportEntry("proc_self_mounts.txt", "/proc/self/mounts", false),
            new ProcExportEntry("proc_self_mountstats.txt", "/proc/self/mountstats", false),
            new ProcExportEntry("proc_self_io.txt", "/proc/self/io", false),
            new ProcExportEntry("proc_self_limits.txt", "/proc/self/limits", false),
            new ProcExportEntry("proc_self_oom_score.txt", "/proc/self/oom_score", false),
            new ProcExportEntry("proc_self_oom_adj.txt", "/proc/self/oom_adj", false),
            new ProcExportEntry("proc_self_oom_score_adj.txt", "/proc/self/oom_score_adj", false),
            new ProcExportEntry("proc_self_sched.txt", "/proc/self/sched", false),
            new ProcExportEntry("proc_self_sched_boost.txt", "/proc/self/sched_boost", false),
            new ProcExportEntry("proc_self_sched_boost_period_ms.txt", "/proc/self/sched_boost_period_ms", false),
            new ProcExportEntry("proc_self_sched_group_id.txt", "/proc/self/sched_group_id", false),
            new ProcExportEntry("proc_self_sched_init_task_load.txt", "/proc/self/sched_init_task_load", false),
            new ProcExportEntry("proc_self_sched_wake_up_idle.txt", "/proc/self/sched_wake_up_idle", false),
            new ProcExportEntry("proc_self_schedstat.txt", "/proc/self/schedstat", false),
            new ProcExportEntry("proc_self_smaps.txt", "/proc/self/smaps", false),
            new ProcExportEntry("proc_self_cgroup.txt", "/proc/self/cgroup", false),
            new ProcExportEntry("proc_self_cpuset.txt", "/proc/self/cpuset", false),
            new ProcExportEntry("proc_self_comm.txt", "/proc/self/comm", false),
            new ProcExportEntry("proc_self_wchan.txt", PATH_PROC_SELF_WCHAN, false),
            new ProcExportEntry("proc_self_auxv_summary.txt", PATH_PROC_SELF_AUXV, true),
            new ProcExportEntry("proc_self_environ.txt", "/proc/self/environ", false)
    };

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private IProcInfoService procInfoService;
    private ActivityMainBinding binding;
    private AlertDialog loadingDialog;
    private boolean serviceBound;
    private boolean pendingExportAfterPermission;

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

    private static final class ProcExportEntry {
        final String fileName;
        final String path;
        final boolean useAuxvSummary;

        ProcExportEntry(String fileName, String path, boolean useAuxvSummary) {
            this.fileName = fileName;
            this.path = path;
            this.useAuxvSummary = useAuxvSummary;
        }
    }

    private static final class ExportResult {
        final String savedLocation;

        ExportResult(String savedLocation) {
            this.savedLocation = savedLocation;
        }
    }

    private void showProcInfo(ProcInfoMethod method) {
        if (procInfoService == null) {
            Log.e(TAG, "Service not bound");
            Toast.makeText(MainActivity.this, "サービスが接続されていません", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoadingDialog("しばらくお待ちください");
        final long startTime = System.currentTimeMillis();

        executor.execute(() -> {
            try {
                String result = readProcInfo(method);

                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed < MIN_DIALOG_SPINNER_MS) {
                    Thread.sleep(MIN_DIALOG_SPINNER_MS - elapsed);
                }

                runOnUiThread(() -> {
                    dismissLoadingDialog();
                    showDialog(getReadableTitle(method), result);
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
                return "Unsupported method";
        }
    }

    private void showDialog(String title, String content) {
        DialogProcInfoBinding dialogBinding = DialogProcInfoBinding.inflate(LayoutInflater.from(this));
        final String safeContent = content == null ? "" : content;
        final String displayContent = safeContent.isEmpty() ? "（空の内容です）" : safeContent;

        dialogBinding.txtProcInfo.setText(displayContent);
        dialogBinding.txtProcInfo.setTextIsSelectable(true);
        dialogBinding.txtProcInfo.setFocusable(true);
        dialogBinding.txtProcInfo.setFocusableInTouchMode(true);
        dialogBinding.txtProcInfo.setLongClickable(true);
        dialogBinding.txtProcInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(dialogBinding.getRoot())
                .create();

        dialog.setOnShowListener(d -> {
            dialogBinding.btnCopyDialog.setOnClickListener(v -> copyToClipboard(title, safeContent));
            dialogBinding.btnCloseDialog.setOnClickListener(v -> dialog.dismiss());

            if (dialog.getWindow() != null) {
                dialog.getWindow().setLayout(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.MATCH_PARENT);
            }
        });

        dialog.show();
    }

    private String getReadableTitle(ProcInfoMethod method) {
        switch (method) {
            case PROC_VERSION:
                return "Proc Version";
            case PROC_CPUINFO:
                return "Proc CPU Info";
            case PROC_MEMINFO:
                return "Proc Mem Info";
            case PROC_SELF_STATUS:
                return "Proc Self Status";
            case PROC_SELF_MAPS:
                return "Proc Self Maps";
            case PROC_SELF_MOUNTINFO:
                return "Proc Self Mountinfo";
            case PROC_SELF_MOUNTS:
                return "Proc Self Mounts";
            case PROC_SELF_MOUNTSTATS:
                return "Proc Self Mountstats";
            case PROC_SELF_IO:
                return "Proc Self IO";
            case PROC_SELF_LIMITS:
                return "Proc Self Limits";
            case PROC_SELF_OOM_SCORE:
                return "Proc Self Oom Score";
            case PROC_SELF_OOM_ADJ:
                return "Proc Self Oom Adj";
            case PROC_SELF_OOM_SCORE_ADJ:
                return "Proc Self Oom Score Adj";
            case PROC_SELF_SCHED:
                return "Proc Self Sched";
            case PROC_SELF_SCHED_BOOST:
                return "Proc Self Sched Boost";
            case PROC_SELF_SCHED_BOOST_PERIOD_MS:
                return "Proc Self Sched Boost Period Ms";
            case PROC_SELF_SCHED_GROUP_ID:
                return "Proc Self Sched Group Id";
            case PROC_SELF_SCHED_INIT_TASK_LOAD:
                return "Proc Self Sched Init Task Load";
            case PROC_SELF_SCHED_WAKE_UP_IDLE:
                return "Proc Self Sched Wake Up Idle";
            case PROC_SELF_SCHEDSTAT:
                return "Proc Self Schedstat";
            case PROC_SELF_SMAP:
                return "Proc Self Smap";
            case PROC_SELF_WCHAN:
                return "Proc Self Wchan";
            case PROC_SELF_AUXV:
                return "Proc Self Auxv";
            default:
                return method.name();
        }
    }

    private void copyToClipboard(String label, String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            Toast.makeText(this, "コピーできませんでした", Toast.LENGTH_SHORT).show();
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text == null ? "" : text));
        Toast.makeText(this, "コピーしました", Toast.LENGTH_SHORT).show();
    }

    private void exportAllProcInfo() {
        if (procInfoService == null) {
            Toast.makeText(this, "サービスが接続されていません", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!checkStoragePermission()) {
            pendingExportAfterPermission = true;
            requestStoragePermission();
            return;
        }

        pendingExportAfterPermission = false;
        startAllExport();
    }

    private void startAllExport() {
        showLoadingDialog("しばらくお待ちください………");
        executor.execute(() -> {
            try {
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                String zipName = "proc_info_dump_" + timestamp + ".zip";
                ExportResult result = writeExportZip(zipName);

                runOnUiThread(() -> {
                    dismissLoadingDialog();
                    Toast.makeText(MainActivity.this, "エクスポート完了: " + result.savedLocation, Toast.LENGTH_LONG).show();
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

    private ExportResult writeExportZip(String zipName) throws IOException, RemoteException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return writeZipToMediaStore(zipName);
        }
        return writeZipToLegacyDownloads(zipName);
    }

    private ExportResult writeZipToLegacyDownloads(String zipName) throws IOException, RemoteException {
        File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!downloadDir.exists() && !downloadDir.mkdirs()) {
            throw new IOException("Unable to create Downloads directory");
        }

        File finalFile = new File(downloadDir, zipName);
        try (OutputStream raw = new FileOutputStream(finalFile);
             BufferedOutputStream buffered = new BufferedOutputStream(raw, BUFFER_SIZE);
             ZipOutputStream zos = new ZipOutputStream(buffered)) {
            writeProcEntries(zos);
        }
        return new ExportResult(finalFile.getAbsolutePath());
    }

    private ExportResult writeZipToMediaStore(String zipName) throws IOException, RemoteException {
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, zipName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "application/zip");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/");
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);

        Uri collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        Uri itemUri = getContentResolver().insert(collection, values);
        if (itemUri == null) {
            throw new IOException("Unable to insert into MediaStore");
        }

        try (OutputStream raw = getContentResolver().openOutputStream(itemUri);
             BufferedOutputStream buffered = raw == null ? null : new BufferedOutputStream(raw, BUFFER_SIZE)) {
            if (buffered == null) {
                throw new IOException("Unable to open MediaStore output stream");
            }
            try (ZipOutputStream zos = new ZipOutputStream(buffered)) {
                writeProcEntries(zos);
            }
        } catch (IOException e) {
            try {
                getContentResolver().delete(itemUri, null, null);
            } catch (Exception ignored) {
            }
            throw e;
        }

        values.clear();
        values.put(MediaStore.MediaColumns.IS_PENDING, 0);
        getContentResolver().update(itemUri, values, null, null);

        return new ExportResult("Downloads/" + zipName);
    }

    private void writeProcEntries(ZipOutputStream zos) throws IOException, RemoteException {
        for (ProcExportEntry entry : EXPORT_ENTRIES) {
            String content = entry.useAuxvSummary
                    ? procInfoService.getProcSelfAuxvSummary()
                    : procInfoService.readProcFile(entry.path);

            zos.putNextEntry(new ZipEntry(entry.fileName));
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
                Toast.makeText(this, "権限が許可されました", Toast.LENGTH_SHORT).show();
                if (pendingExportAfterPermission) {
                    pendingExportAfterPermission = false;
                    startAllExport();
                }
            } else {
                pendingExportAfterPermission = false;
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
