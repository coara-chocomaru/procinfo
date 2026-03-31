package com.coara.proc;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.Toast;
import com.coara.proc.databinding.ActivityMainBinding;
import com.coara.proc.databinding.DialogLoadingBinding;
import com.coara.proc.databinding.DialogProcInfoBinding;
import android.os.Environment;
import android.os.Build;
import android.Manifest;
import android.content.pm.PackageManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import android.widget.LinearLayout;
import android.widget.Button;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";

    private IProcInfoService procInfoService;
    private ActivityMainBinding binding;
    private AlertDialog loadingDialog;

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
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
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

        LinearLayout linearLayoutMain = findViewById(R.id.linearLayoutMain);
        Button allExportButton = new Button(this);
        allExportButton.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        allExportButton.setText("All Export");
        allExportButton.setOnClickListener(v -> exportAllProcInfo());
        linearLayoutMain.addView(allExportButton);
    }

    private enum ProcInfoMethod {
        PROC_VERSION, PROC_CPUINFO, PROC_MEMINFO,
        PROC_SELF_STATUS, PROC_SELF_MAPS, PROC_SELF_MOUNTINFO, PROC_SELF_MOUNTS, PROC_SELF_MOUNTSTATS,
        PROC_SELF_IO, PROC_SELF_LIMITS, PROC_SELF_OOM_SCORE, PROC_SELF_OOM_ADJ, PROC_SELF_OOM_SCORE_ADJ,
        PROC_SELF_SCHED, PROC_SELF_SCHED_BOOST, PROC_SELF_SCHED_BOOST_PERIOD_MS, PROC_SELF_SCHED_GROUP_ID,
        PROC_SELF_SCHED_INIT_TASK_LOAD, PROC_SELF_SCHED_WAKE_UP_IDLE, PROC_SELF_SCHEDSTAT, PROC_SELF_SMAP
    }

    private void showProcInfo(ProcInfoMethod method) {
        if (procInfoService == null) {
            Log.e(TAG, "Service not bound");
            Toast.makeText(MainActivity.this, "サービスが接続されていません", Toast.LENGTH_SHORT).show();
            return;
        }

        runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            DialogLoadingBinding loadingBinding = DialogLoadingBinding.inflate(LayoutInflater.from(MainActivity.this));
            loadingBinding.txtLoadingMessage.setText("しばらくお待ちください");
            builder.setView(loadingBinding.getRoot());
            builder.setCancelable(false);
            loadingDialog = builder.create();
            loadingDialog.show();
        });

        final long MIN_DISPLAY_TIME = 1000;
        final long startTime = System.currentTimeMillis();

        new Thread(() -> {
            try {
                String result = "";
                switch (method) {
                    case PROC_VERSION:
                        result = procInfoService.getProcVersion();
                        break;
                    case PROC_CPUINFO:
                        result = procInfoService.getProcCPUInfo();
                        break;
                    case PROC_MEMINFO:
                        result = procInfoService.getProcMemInfo();
                        break;
                    case PROC_SELF_STATUS:
                        result = procInfoService.getProcSelfStatus();
                        break;
                    case PROC_SELF_MAPS:
                        result = procInfoService.getProcSelfMaps();
                        break;
                    case PROC_SELF_MOUNTINFO:
                        result = procInfoService.getProcSelfMountinfo();
                        break;
                    case PROC_SELF_MOUNTS:
                        result = procInfoService.getProcSelfMounts();
                        break;
                    case PROC_SELF_MOUNTSTATS:
                        result = procInfoService.getProcSelfMountstats();
                        break;
                    case PROC_SELF_IO:
                        result = procInfoService.getProcSelfIO();
                        break;
                    case PROC_SELF_LIMITS:
                        result = procInfoService.getProcSelfLimits();
                        break;
                    case PROC_SELF_OOM_SCORE:
                        result = procInfoService.getProcSelfOomScore();
                        break;
                    case PROC_SELF_OOM_ADJ:
                        result = procInfoService.getProcSelfOomAdj();
                        break;
                    case PROC_SELF_OOM_SCORE_ADJ:
                        result = procInfoService.getProcSelfOomScoreAdj();
                        break;
                    case PROC_SELF_SCHED:
                        result = procInfoService.getProcSelfSched();
                        break;
                    case PROC_SELF_SCHED_BOOST:
                        result = procInfoService.getProcSelfSchedBoost();
                        break;
                    case PROC_SELF_SCHED_BOOST_PERIOD_MS:
                        result = procInfoService.getProcSelfSchedBoostPeriodMs();
                        break;
                    case PROC_SELF_SCHED_GROUP_ID:
                        result = procInfoService.getProcSelfSchedGroupId();
                        break;
                    case PROC_SELF_SCHED_INIT_TASK_LOAD:
                        result = procInfoService.getProcSelfSchedInitTaskLoad();
                        break;
                    case PROC_SELF_SCHED_WAKE_UP_IDLE:
                        result = procInfoService.getProcSelfSchedWakeUpIdle();
                        break;
                    case PROC_SELF_SCHEDSTAT:
                        result = procInfoService.getProcSelfSchedstat();
                        break;
                    case PROC_SELF_SMAP:
                        result = procInfoService.getProcSelfSmap();
                        break;
                }

            
                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed < MIN_DISPLAY_TIME) {
                    Thread.sleep(MIN_DISPLAY_TIME - elapsed);
                }

                String finalResult = result;
                runOnUiThread(() -> {
                    if (loadingDialog != null && loadingDialog.isShowing()) {
                        loadingDialog.dismiss();
                    }
                    showDialog(method.name(), finalResult);
                });
            } catch (RemoteException | InterruptedException e) {
                Log.e(TAG, "Error fetching proc info", e);
                runOnUiThread(() -> {
                    if (loadingDialog != null && loadingDialog.isShowing()) {
                        loadingDialog.dismiss();
                    }
                });
            }
        }).start();
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
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        DialogLoadingBinding loadingBinding = DialogLoadingBinding.inflate(LayoutInflater.from(MainActivity.this));
        loadingBinding.txtLoadingMessage.setText("しばらくお待ちください………");
        builder.setView(loadingBinding.getRoot());
        builder.setCancelable(false);
        loadingDialog = builder.create();
        loadingDialog.show();

        new Thread(() -> {
            try {
                String[] procPaths = {
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
                    "/proc/self/environ"
                };
                File tempDir = new File(getCacheDir(), "proc_dump");
                if (tempDir.exists()) {
                    deleteRecursive(tempDir);
                }
                tempDir.mkdirs();
                for (String path : procPaths) {
                    String content = procInfoService.readProcFile(path);
                    String fileName = path.substring(1).replace('/', '_') + ".txt";
                    File txtFile = new File(tempDir, fileName);
                    try (FileWriter writer = new FileWriter(txtFile)) {
                        writer.write(content != null ? content : "Error");
                    } catch (Exception ignored) {}
                }
                File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!downloadDir.exists()) {
                    downloadDir.mkdirs();
                }
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                String zipName = "proc_info_dump_" + timestamp + ".zip";
                File zipFile = new File(downloadDir, zipName);
                zipDirectory(tempDir, zipFile);
                deleteRecursive(tempDir);
                runOnUiThread(() -> {
                    if (loadingDialog != null && loadingDialog.isShowing()) {
                        loadingDialog.dismiss();
                    }
                    Toast.makeText(MainActivity.this, "エクスポート完了: " + zipFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                Log.e(TAG, "Export error", e);
                runOnUiThread(() -> {
                    if (loadingDialog != null && loadingDialog.isShowing()) {
                        loadingDialog.dismiss();
                    }
                    Toast.makeText(MainActivity.this, "エクスポート失敗", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private boolean checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1001);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "権限が許可されました。もう一度All Exportをタップしてください。", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "ストレージ権限が必要です", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void zipDirectory(File sourceDir, File zipFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            File[] files = sourceDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        try (FileInputStream fis = new FileInputStream(file)) {
                            ZipEntry entry = new ZipEntry(file.getName());
                            zos.putNextEntry(entry);
                            byte[] buffer = new byte[8192];
                            int len;
                            while ((len = fis.read(buffer)) > 0) {
                                zos.write(buffer, 0, len);
                            }
                            zos.closeEntry();
                        }
                    }
                }
            }
        }
    }

    private void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }

    @Override
    protected void onDestroy() {
        unbindService(serviceConnection);
        super.onDestroy();
    }
}
