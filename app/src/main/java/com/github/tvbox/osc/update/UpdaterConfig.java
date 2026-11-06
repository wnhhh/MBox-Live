package com.github.tvbox.osc.update;

import com.github.tvbox.osc.config.PrefsDataStore;

/**
 * 更新源配置门面:记录当前更新实现类型与 GitHub 仓库参数。
 * <p>
 * 数据自持于 {@link PrefsDataStore}(现代化偏好存储),供 {@link UpdaterProvider}
 * 按配置切换到不同更新实现;未写入时使用仓库默认值。
 */
public final class UpdaterConfig {

    /** 默认实现:GitHub Releases 为主 + 自建清单兜底（国内访问 api.github.com 不通时自动切换） */
    public static final String SOURCE_AUTO = "auto";

    /** 仅用 GitHub Releases */
    public static final String SOURCE_GITHUB = "github";

    /** 仅用自建 HTTP 清单 */
    public static final String SOURCE_HTTP = "http";

    /**
     * 默认更新清单地址(自建静态站点)。
     * <p>
     * 清单里声明最新 versionCode / versionName / APK 地址 / 更新说明,App 只认 versionCode 比较;
     * 发版流程:替换 APK → 改这份 update.json → 客户端下次检查即可一键升级。
     */
    public static final String DEFAULT_MANIFEST_URL = "http://111.228.23.40/mbox/update.json";

    /**
     * 更新仓库默认坐标:{@code wnhhh/MBox-Live} 的 Releases 就是 APK 的分发点。
     * <p>
     * 发版只需在该仓库打一个 {@code v*} tag:仓库自带的 GitHub Actions(build-apk.yml)会构建 APK
     * 并自动创建 Release、挂上安装包;客户端读 {@code /releases} 列表比对版本,下载走 gh-proxy 加速。
     */
    public static final String DEFAULT_GITHUB_OWNER = "wnhhh";
    public static final String DEFAULT_GITHUB_REPO = "MBox-Live";

    /** 默认 GitHub 下载加速前缀(形如 https://gh-proxy.org/);空串=不使用代理,仅直连 */
    public static final String DEFAULT_GITHUB_DOWNLOAD_PROXY = "https://gh-proxy.org/";

    private static final String KEY_SOURCE = "update_source";
    private static final String KEY_MANIFEST_URL = "update_manifest_url";
    private static final String KEY_GITHUB_OWNER = "update_github_owner";
    private static final String KEY_GITHUB_REPO = "update_github_repo";
    private static final String KEY_GITHUB_DOWNLOAD_PROXY = "update_github_download_proxy";

    private UpdaterConfig() {
    }

    /** 更新实现标识,默认 {@link #SOURCE_AUTO}（GitHub 优先、自建清单兜底） */
    public static String getSource() {
        String s = PrefsDataStore.getString(KEY_SOURCE, SOURCE_AUTO);
        return s == null || s.trim().isEmpty() ? SOURCE_AUTO : s.trim();
    }

    public static void setSource(String source) {
        PrefsDataStore.put(KEY_SOURCE, source == null || source.trim().isEmpty() ? SOURCE_AUTO : source.trim());
    }

    /** 自建更新清单地址,默认 {@value #DEFAULT_MANIFEST_URL} */
    public static String getManifestUrl() {
        String v = PrefsDataStore.getString(KEY_MANIFEST_URL, DEFAULT_MANIFEST_URL);
        return v == null ? DEFAULT_MANIFEST_URL : v.trim();
    }

    public static void setManifestUrl(String url) {
        PrefsDataStore.put(KEY_MANIFEST_URL, url == null ? DEFAULT_MANIFEST_URL : url.trim());
    }

    /** GitHub 仓库 owner,默认 {@value #DEFAULT_GITHUB_OWNER} */
    public static String getGithubOwner() {
        String v = PrefsDataStore.getString(KEY_GITHUB_OWNER, DEFAULT_GITHUB_OWNER);
        return v == null || v.trim().isEmpty() ? DEFAULT_GITHUB_OWNER : v.trim();
    }

    public static void setGithubOwner(String owner) {
        PrefsDataStore.put(KEY_GITHUB_OWNER, owner == null || owner.trim().isEmpty() ? DEFAULT_GITHUB_OWNER : owner.trim());
    }

    /** GitHub 仓库名,默认 {@value #DEFAULT_GITHUB_REPO} */
    public static String getGithubRepo() {
        String v = PrefsDataStore.getString(KEY_GITHUB_REPO, DEFAULT_GITHUB_REPO);
        return v == null || v.trim().isEmpty() ? DEFAULT_GITHUB_REPO : v.trim();
    }

    public static void setGithubRepo(String repo) {
        PrefsDataStore.put(KEY_GITHUB_REPO, repo == null || repo.trim().isEmpty() ? DEFAULT_GITHUB_REPO : repo.trim());
    }

    /** GitHub 下载加速前缀(默认 {@value #DEFAULT_GITHUB_DOWNLOAD_PROXY});空串/未配置=只用直连 */
    public static String getGithubDownloadProxy() {
        String v = PrefsDataStore.getString(KEY_GITHUB_DOWNLOAD_PROXY, DEFAULT_GITHUB_DOWNLOAD_PROXY);
        return v == null ? "" : v.trim();
    }

    public static void setGithubDownloadProxy(String proxyPrefix) {
        PrefsDataStore.put(KEY_GITHUB_DOWNLOAD_PROXY,
                proxyPrefix == null ? "" : proxyPrefix.trim());
    }
}
