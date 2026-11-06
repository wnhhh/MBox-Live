package com.github.tvbox.osc.update;

import android.content.Context;

import com.github.tvbox.osc.update.github.GithubReleaseUpdater;
import com.github.tvbox.osc.update.http.HttpManifestUpdater;
import com.github.tvbox.osc.util.AppLog;

/**
 * 双源更新：GitHub Releases 为主，自建清单兜底。
 * <p>
 * APK 分发在 {@code wnhhh/MBox-Live} 的 Releases 上（发版即打 tag，CI 自动构建挂包）；
 * 但 {@code api.github.com} 在国内网络下经常不可达，只走 GitHub 会让"检查更新"直接报错。
 * 所以这里先问 GitHub，<b>只有报错时</b>才回落到自建清单：
 * <ul>
 *   <li>GitHub 正常 → 完全以 Releases 为准（清单不用管）</li>
 *   <li>GitHub 不通 → 清单仍能给出新版本与下载地址，不至于整条更新链路瘫痪</li>
 *   <li>GitHub 正常但没有新版本 → 直接视为最新，不再打第二遍网络</li>
 * </ul>
 * 下载/安装两端都走 {@link UpdateManager}，行为一致。
 */
public final class FallbackUpdater implements Updater {

    private final Updater primary;
    private final Updater secondary;

    public FallbackUpdater(Updater primary, Updater secondary) {
        this.primary = primary;
        this.secondary = secondary;
    }

    /** 默认组合：GitHub Releases → 自建清单 */
    public static FallbackUpdater defaults() {
        return new FallbackUpdater(new GithubReleaseUpdater(), new HttpManifestUpdater());
    }

    @Override
    public String name() {
        return "auto";
    }

    @Override
    public void checkUpdate(final Context context, final Callback cb) {
        if (cb == null) return;
        primary.checkUpdate(context, new Callback() {
            @Override
            public void onCheckStart() {
                cb.onCheckStart();
            }

            @Override
            public void onCheckResult(UpdateInfo newVersion) {
                // null = GitHub 明确回答"已是最新"，这是成功结果，不需要再问清单
                cb.onCheckResult(newVersion);
            }

            @Override
            public void onDownloadProgress(long current, long total) {
            }

            @Override
            public void onDownloadReady(UpdateInfo info) {
            }

            @Override
            public void onError(String message) {
                AppLog.log("更新", "GitHub 检查失败，改用自建清单兜底: " + message);
                secondary.checkUpdate(context, cb);
            }
        });
    }

    @Override
    public void downloadAndInstall(Context context, UpdateInfo info, Callback cb) {
        // 无论版本信息来自哪个源，下载/断点续传/安装都统一交给 UpdateManager
        UpdateManager.get().start(context, info, cb);
    }
}
