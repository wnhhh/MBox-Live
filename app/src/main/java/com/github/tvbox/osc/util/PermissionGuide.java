package com.github.tvbox.osc.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import com.github.tvbox.osc.ui.dialog.ConfirmDialog;
import com.hjq.permissions.Permission;
import com.hjq.permissions.XXPermissions;

import java.util.HashSet;
import java.util.Set;

/**
 * 特殊权限引导。
 * <p>
 * 「所有文件访问」({@code MANAGE_EXTERNAL_STORAGE})和「安装未知应用」({@code REQUEST_INSTALL_PACKAGES})
 * 都不是运行期权限:系统不会弹授权框,只能在设置页里手动打开。电视盒子/电视尤其如此
 * (首次启动完全没有提示,用户装完就发现下载存不进去、应用内更新也装不上)。
 * <p>
 * 所以这里在每次回到直播页时检查一遍,缺什么就弹一个说明清楚的确认框,点「去开启」直接跳对应的设置页;
 * 跳转做了三级兜底(带包名的页面 → 不带包名的全局页面 → 应用详情),因为部分定制电视 ROM
 * 缺少前两者。同一权限每次启动最多打扰一次,用户开启后不再出现。
 */
public final class PermissionGuide {

    private static final String KEY_STORAGE = "storage";
    private static final String KEY_INSTALL = "install";

    /** 本次启动已经引导过的项,避免从设置页返回后反复弹 */
    private static final Set<String> PROMPTED = new HashSet<>();

    private PermissionGuide() {
    }

    /** 是否已拿到「所有文件访问」(Android 11+);更低版本退化为读写运行期权限 */
    public static boolean hasStorageAccess(Context context) {
        if (context == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return XXPermissions.isGranted(context, Permission.WRITE_EXTERNAL_STORAGE);
    }

    /** 是否允许应用内安装 APK(Android 8+ 需要) */
    public static boolean canInstallApk(Context context) {
        if (context == null) return false;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true;
        try {
            return context.getPackageManager().canRequestPackageInstalls();
        } catch (Throwable th) {
            return true; // 查询失败不拦用户
        }
    }

    /**
     * 检查并引导。一次只弹一项,用户从设置页回来后({@link Activity#onResume})会再检查下一项。
     */
    public static void checkAndGuide(final Activity activity) {
        if (activity == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
                && (activity.isFinishing() || activity.isDestroyed())) {
            return;
        }

        if (!hasStorageAccess(activity)) {
            guide(activity, KEY_STORAGE,
                    "需要「所有文件访问」权限",
                    "下载的视频要存到本地、备份配置也要读写文件,没有这个权限会失败。\n\n"
                            + "点「去开启」会跳到系统页面,请打开「允许访问所有文件」;\n"
                            + "如果没跳过去,请手动进:设置 → 应用 → 权限管理 → MBox直播 → 允许访问所有文件。",
                    new Runnable() {
                        @Override
                        public void run() {
                            openStorageSettings(activity);
                        }
                    });
            return;
        }

        if (!canInstallApk(activity)) {
            guide(activity, KEY_INSTALL,
                    "需要「安装未知应用」权限",
                    "应用内检查更新、下载新版本后要能直接安装,否则每次都得手动找安装包。\n\n"
                            + "点「去开启」会跳到系统页面,请打开「允许来自此应用」;\n"
                            + "如果没跳过去,请手动进:设置 → 应用 → 权限管理 → MBox直播 → 安装未知应用。",
                    new Runnable() {
                        @Override
                        public void run() {
                            openInstallSettings(activity);
                        }
                    });
        }
    }

    private static void guide(final Activity activity, String key, String title, String message,
                              final Runnable onOpen) {
        if (!PROMPTED.add(key)) return; // 本次启动已经问过
        try {
            ConfirmDialog.show(activity, title, message, "去开启", onOpen);
        } catch (Throwable th) {
            AppBubble.toastLong(title);
        }
    }

    // ------------------------------------------------------------------
    // 跳设置页(带兜底)
    // ------------------------------------------------------------------

    private static void openStorageSettings(final Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            // Android 10 及以下:读写是运行期权限,直接申请
            XXPermissions.with(activity)
                    .permission(Permission.READ_EXTERNAL_STORAGE, Permission.WRITE_EXTERNAL_STORAGE)
                    .request(new com.hjq.permissions.OnPermissionCallback() {
                        @Override
                        public void onGranted(java.util.List<String> permissions, boolean all) {
                        }

                        @Override
                        public void onDenied(java.util.List<String> permissions, boolean never) {
                            if (never) AppBubble.toastLong("存储权限被拒绝,请在系统设置里开启");
                        }
                    });
            return;
        }
        Intent app = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:" + activity.getPackageName()));
        if (startSafe(activity, app)) return;
        if (startSafe(activity, new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))) return;
        openAppDetails(activity);
        AppBubble.toastLong("请到「应用信息 → 权限」里允许访问所有文件");
    }

    private static void openInstallSettings(final Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        Intent app = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + activity.getPackageName()));
        if (startSafe(activity, app)) return;
        if (startSafe(activity, new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES))) return;
        if (startSafe(activity, new Intent(Settings.ACTION_SECURITY_SETTINGS))) return;
        openAppDetails(activity);
        AppBubble.toastLong("请到「应用信息 → 安装未知应用」里允许");
    }

    private static void openAppDetails(Activity activity) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + activity.getPackageName()));
        if (!startSafe(activity, intent)) {
            AppBubble.toastLong("请手动到系统设置里给本应用授权");
        }
    }

    private static boolean startSafe(Activity activity, Intent intent) {
        if (activity == null || intent == null) return false;
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
            return true;
        } catch (Throwable th) {
            // 定制 ROM 常见:ActivityNotFoundException / SecurityException,继续走下一级兜底
            return false;
        }
    }

    /** 给下载/更新等业务方用:缺权限时直接跳设置并提示,返回 false 表示当前不能继续 */
    public static boolean requireInstallPermission(Activity activity) {
        if (canInstallApk(activity)) return true;
        AppBubble.toastLong("请先允许「安装未知应用」,再重试");
        openInstallSettings(activity);
        return false;
    }

    /** 文案辅助:缺哪些权限(用于设置页展示,可为空) */
    public static String missingSummary(Context context) {
        StringBuilder sb = new StringBuilder();
        if (!hasStorageAccess(context)) sb.append("所有文件访问");
        if (!canInstallApk(context)) {
            if (sb.length() > 0) sb.append("、");
            sb.append("安装未知应用");
        }
        return sb.length() == 0 ? "" : sb.toString();
    }
}
