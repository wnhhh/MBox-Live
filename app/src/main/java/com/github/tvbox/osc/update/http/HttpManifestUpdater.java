package com.github.tvbox.osc.update.http;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.github.tvbox.osc.di.AppCompositionRoot;
import com.github.tvbox.osc.update.UpdateInfo;
import com.github.tvbox.osc.update.UpdateManager;
import com.github.tvbox.osc.update.Updater;
import com.github.tvbox.osc.update.UpdaterConfig;
import com.github.tvbox.osc.util.AppLog;
import com.github.tvbox.osc.util.HeavyTaskUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 自建更新源（HTTP JSON 清单）实现。
 * <p>
 * GitHub Releases 只适合公开仓库的正式发版，自用包的签名/版本号都在本地，没法从上游仓库拿到可安装的 APK；
 * 这里改成读一份自己维护的清单，一个文件就能发版：
 *
 * <pre>
 * {
 *   "versionCode": 74,                       // 必填，与已安装 versionCode 比较
 *   "versionName": "3.5.8-preload",          // 展示用
 *   "apkUrl": "MBox_live_3.5.8.apk",         // 可写相对清单的路径，也可写完整 URL
 *   "apkUrls": ["https://.../a.apk"],        // 可选，多镜像按序尝试（apkUrl 优先）
 *   "size": 28515443,                        // 可选，用于完整性校验与断点续传
 *   "notes": "1. 换台预加载\n2. 默认 IJK 软解", // 可选，更新说明（支持轻量 Markdown）
 *   "apkName": "MBox_live.apk"               // 可选，本地落盘文件名
 * }
 * </pre>
 * <p>
 * 清单与 APK 都放在自有静态站点上，改清单即发版；下载/断点续传/安装仍统一交给
 * {@link UpdateManager}，与 GitHub 实现共用同一套 UI。
 */
public class HttpManifestUpdater implements Updater {

    private static final String TAG = "HttpUpdater";

    /** 部分防护层（如京东云 JDTP）会拦非浏览器 UA，这里统一带浏览器 UA */
    private static final String UA = UpdateManager.DOWNLOAD_UA;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    @Override
    public String name() {
        return UpdaterConfig.SOURCE_HTTP;
    }

    // ------------------------------------------------------------------
    // 检查
    // ------------------------------------------------------------------

    @Override
    public void checkUpdate(final Context context, final Callback cb) {
        if (cb == null) return;
        cb.onCheckStart();

        final String manifestUrl = UpdaterConfig.getManifestUrl();
        if (TextUtils.isEmpty(manifestUrl)) {
            post(() -> cb.onError("检查更新失败: 未配置更新地址"));
            return;
        }

        HeavyTaskUtil.getBigTaskExecutorService().execute(() -> {
            UpdateInfo info = null;
            String err = null;
            Response resp = null;
            try {
                OkHttpClient client = AppCompositionRoot.network().general();
                Request req = new Request.Builder()
                        .url(manifestUrl)
                        .header("User-Agent", UA)
                        .header("Accept", "application/json")
                        .header("Cache-Control", "no-cache")
                        .build();
                resp = client.newCall(req).execute();
                if (!resp.isSuccessful() || resp.body() == null) {
                    err = "检查更新失败: HTTP " + resp.code();
                } else {
                    info = parse(resp.body().string(), manifestUrl);
                }
            } catch (Throwable t) {
                err = "检查更新失败: " + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
            } finally {
                if (resp != null) resp.close();
            }
            AppLog.log("更新", "清单检查 " + manifestUrl + " -> "
                    + (err != null ? err : (info == null ? "已是最新" : "新版本 v" + info.versionName)));
            final UpdateInfo fi = info;
            final String fe = err;
            post(() -> {
                if (fe != null) cb.onError(fe);
                else cb.onCheckResult(fi);
            });
        });
    }

    /**
     * 解析清单。返回 null 表示"已是最新"（远端版本号不高于本机 / 没有可用下载地址）。
     */
    private UpdateInfo parse(String json, String manifestUrl) throws Exception {
        // 记事本等工具保存 UTF-8 可能带 BOM，JSON 解析会直接失败，这里先抹掉
        if (json != null && json.length() > 0 && json.charAt(0) == '\uFEFF') {
            json = json.substring(1);
        }
        JSONObject o = new JSONObject(json);

        int remoteCode = o.optInt("versionCode", -1);
        String remoteName = o.optString("versionName", "").trim();
        int installedCode = com.blankj.utilcode.util.AppUtils.getAppVersionCode();
        String installedName = com.blankj.utilcode.util.AppUtils.getAppVersionName();

        boolean newer = remoteCode > 0
                ? remoteCode > installedCode
                : compareVersion(remoteName, installedName) > 0;
        if (!newer) return null;

        List<String> urls = new ArrayList<>();
        String single = resolve(manifestUrl, o.optString("apkUrl", ""));
        if (!single.isEmpty()) urls.add(single);
        JSONArray arr = o.optJSONArray("apkUrls");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                String u = resolve(manifestUrl, arr.optString(i, ""));
                if (!u.isEmpty() && !urls.contains(u)) urls.add(u);
            }
        }
        if (urls.isEmpty()) {
            AppLog.log("更新", "清单里没有可用 APK 地址,忽略本次更新");
            return null;
        }

        String apkName = o.optString("apkName", "").trim();
        if (apkName.isEmpty()) {
            apkName = "MBox-" + (remoteName.isEmpty() ? remoteCode : remoteName) + ".apk";
        }
        long size = o.optLong("size", -1L);
        if (size <= 0) size = -1L;
        String notes = o.optString("notes", o.optString("releaseNote", ""));
        String tag = remoteName.isEmpty() ? ("v" + remoteCode) : ("v" + remoteName);

        return new UpdateInfo(remoteName.isEmpty() ? String.valueOf(remoteCode) : remoteName,
                tag, remoteCode <= 0 ? -1 : remoteCode, urls, apkName, size, notes);
    }

    /** 相对路径按清单地址解析；绝对 URL 原样返回；解析失败返回空串 */
    private static String resolve(String base, String path) {
        if (TextUtils.isEmpty(path)) return "";
        String p = path.trim();
        if (p.startsWith("http://") || p.startsWith("https://")) return p;
        try {
            return URI.create(base).resolve(p).toString();
        } catch (Throwable t) {
            return "";
        }
    }

    // ------------------------------------------------------------------
    // 下载 + 安装（与 GitHub 实现一致，统一下放 UpdateManager）
    // ------------------------------------------------------------------

    @Override
    public void downloadAndInstall(final Context context, final UpdateInfo info, final Callback cb) {
        UpdateManager.get().start(context, info, cb);
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private static void post(Runnable r) {
        if (r != null) MAIN.post(r);
    }

    /** 数字分段比较版本号：a>b 返回正数（清单没给 versionCode 时的兜底） */
    private static int compareVersion(String a, String b) {
        int[] x = parseVersion(a);
        int[] y = parseVersion(b);
        int len = Math.max(x.length, y.length);
        for (int i = 0; i < len; i++) {
            int xv = i < x.length ? x[i] : 0;
            int yv = i < y.length ? y[i] : 0;
            if (xv != yv) return xv > yv ? 1 : -1;
        }
        return 0;
    }

    private static int[] parseVersion(String s) {
        if (s == null) return new int[0];
        List<Integer> list = new ArrayList<>();
        StringBuilder num = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (Character.isDigit(ch)) {
                num.append(ch);
            } else if (num.length() > 0) {
                list.add(Integer.parseInt(num.toString()));
                num.setLength(0);
            }
        }
        if (num.length() > 0) list.add(Integer.parseInt(num.toString()));
        int[] arr = new int[list.size()];
        for (int i = 0; i < arr.length; i++) arr[i] = list.get(i);
        return arr;
    }
}
