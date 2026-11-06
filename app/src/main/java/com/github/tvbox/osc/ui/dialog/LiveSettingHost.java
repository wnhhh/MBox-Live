package com.github.tvbox.osc.ui.dialog;

/**
 * 直播设置面板宿主能力:由 {@code LiveActivity} 实现后以 {@code this} 注入,
 * 弹窗与面板不再依赖具体 Activity 类型。
 */
public interface LiveSettingHost {

    /** 当前播放器画面缩放档位 */
    int getLivePlayerScale();

    /** 当前播放解码类型 */
    int getLivePlayerType();

    /** 切换画面缩放 */
    void changeScale(int position);

    /** 更换播放解码 */
    void changePlayer(int position);

    /** 偏好设置变化后即时刷新两套控制条的显示项 */
    void refreshPreferenceUi();

    /** 换台预加载开关变化:关掉要立刻释放预加载实例,打开要马上补一次预加载 */
    void onPreloadSettingChanged();

    /** 手动"检查更新"(设置面板里的动作项,不参与选中态) */
    void checkUpdate();
}
