package com.github.tvbox.osc.ui.activity;

import android.net.Uri;
import android.os.Handler;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Base64;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;

import com.blankj.utilcode.util.ConvertUtils;
import com.blankj.utilcode.util.ScreenUtils;
import com.github.tvbox.osc.util.AppBubble;
import com.github.tvbox.osc.util.PermissionGuide;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.CastVideo;
import com.github.tvbox.osc.bean.LiveChannelGroup;
import com.github.tvbox.osc.bean.LiveChannelItem;
import com.github.tvbox.osc.config.SystemConfig;
import com.github.tvbox.osc.player.controller.LiveNewController;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.LiveChannelAuth;
import com.github.tvbox.osc.util.LiveChannelNav;
import com.github.tvbox.osc.util.LivePlayerManager;
import com.github.tvbox.osc.ui.adapter.LiveChannelGroupNewAdapter;
import com.github.tvbox.osc.ui.adapter.LiveChannelItemNewAdapter;
import com.github.tvbox.osc.ui.dialog.AllChannelsRightDialog;
import com.github.tvbox.osc.ui.dialog.CastListDialog;
import com.github.tvbox.osc.ui.dialog.DialogCoordinator;
import com.github.tvbox.osc.ui.dialog.LiveLineSelectDialog;
import com.github.tvbox.osc.ui.dialog.LiveLineSelectHost;
import com.github.tvbox.osc.ui.dialog.LiveLineSelectRightDialog;
import com.github.tvbox.osc.ui.dialog.LiveApiDialog;
import com.github.tvbox.osc.ui.dialog.LivePasswordDialog;
import com.github.tvbox.osc.ui.dialog.LiveSettingDialog;
import com.github.tvbox.osc.ui.dialog.LiveSettingHost;
import com.github.tvbox.osc.ui.dialog.LiveSettingRightDialog;
import com.github.tvbox.osc.ui.kit.LinearSpacingItemDecoration;
import com.github.tvbox.osc.ui.widget.LiveNormalControlView;
import com.github.tvbox.osc.ui.widget.LiveSideControlView;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HCallBack;
import com.github.tvbox.osc.util.AppLog;
import com.github.tvbox.osc.util.HttpClient;
import com.github.tvbox.osc.util.LiveConfig;
import com.github.tvbox.osc.util.LivePlaybackRetry;
import com.github.tvbox.osc.util.live.LiveSourceParser;
import com.gyf.immersionbar.BarHide;
import com.gyf.immersionbar.ImmersionBar;
import com.lxj.xpopup.core.BasePopupView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;


import xyz.doikki.videocontroller.component.TitleView;
import xyz.doikki.videoplayer.player.VideoView;

/**
 * @author pj567
 * @date :2021/1/12
 * @description:
 */
public class LiveActivity extends BaseActivity implements LiveLineSelectHost, LiveSettingHost {
    private VideoView mVideoView;
    private TextView tvChannelInfo;
    private LinearLayout tvLeftChannelListLayout;
    private RecyclerView mChannelGroupView;
    private RecyclerView mLiveChannelView;
    public LiveChannelGroupNewAdapter liveChannelGroupAdapter;
    public LiveChannelItemNewAdapter liveChannelItemAdapter;

    private int currentChannelGroupIndex = -1;
    private final Handler mHandler = new Handler(android.os.Looper.getMainLooper());
    private boolean pageActive = true;
    private long sourceLoadGeneration;
    private final Object liveSourceTag = new Object();
    private final LivePlaybackRetry playbackRetry = new LivePlaybackRetry();
    private Runnable connectTimeoutTask;

    private List<LiveChannelGroup> liveChannelGroupList = new ArrayList<>();
    private int currentLiveChannelIndex = -1;
    private LiveChannelItem currentLiveChannelItem = null;
    private LivePlayerManager livePlayerManager = new LivePlayerManager();
    private ArrayList<Integer> channelGroupPasswordConfirmed = new ArrayList<>();

//EPG   by 龍
    private LiveChannelItem channel_Name = null;
    TextView tv_channelnum;
    TextView tip_chname;

    TextView tv_srcinfo;
    public String epgStringAddress ="";

    private boolean isBack = false;
    private LiveSideControlView mSideControlView;
    private LiveNormalControlView mNormalControlView;
    private BasePopupView mSettingRightDialog;
    private BasePopupView mSettingBottomDialog;
    private BasePopupView mAllChannelRightDialog;

    // ── 刷新直播源:常驻按钮 + 定时自动刷新 ──
    /** 定时自动刷新的间隔:上游频道表更新不频繁,15 分钟足够,也不至于耗流量 */
    private static final long AUTO_RELOAD_INTERVAL_MS = 15 * 60 * 1000L;
    /** 回到前台时,距上次成功拉取超过这个时长就静默更新一次 */
    private static final long STALE_RELOAD_MS = 5 * 60 * 1000L;
    /** 失败后的重试间隔:上游挂掉时别按 15 分钟硬等,也别死循环 */
    private static final long RETRY_RELOAD_MS = 2 * 60 * 1000L;
    private ImageView mReloadSourceBtn;
    private View mReloadDot;
    private boolean reloadBtnHidden = false;
    /** 是否已有一次频道表请求在飞(防手贱连点、防定时任务和手动刷新打架) */
    private boolean channelLoading = false;
    /** 上次成功拉到频道表的时刻(SystemClock.elapsedRealtime;0=还没成功过) */
    private long lastChannelLoadedAt = 0L;
    /** 连续失败次数(用于退避重试,别拿 2 分钟死磕一个挂掉的源) */
    private int reloadFailCount = 0;
    private final Runnable mAutoReloadRun = new Runnable() {
        @Override
        public void run() {
            reloadChannels(false);
        }
    };

    /** 上次上下滑换台的时刻(防抖,避免一次滑动连跳多个台) */
    private long lastFlingSwitchAt = 0L;

    // ── 换台预加载(抖音式秒切):下一个台提前建连+解封装,换台时直接扶正实例 ──
    /** 起播稳定后延迟多久开始预加载下一个台:太快会和当前台的缓冲抢带宽 */
    private static final long PRELOAD_DELAY_MS = 1200L;
    /** 预加载过期补刷节奏:直播流放久了连接会僵,定期重新准备一次 */
    private static final long PRELOAD_REFRESH_MS = 60 * 1000L;
    /** 预加载失败退避:死链别每次起播都白建连 */
    private static final long PRELOAD_FAIL_BACKOFF_MS = 5 * 60 * 1000L;
    /** 上次换台方向(+1 下一个台 / -1 上一个台):决定预加载哪一侧 */
    private int lastZapDirection = 1;
    /** 连续自动跳过几个"无可用线路"的频道就停下:整张表都没信号时不能一直换下去 */
    private static final int MAX_AUTO_SKIP = 5;
    /** "无可用线路"的判定有效期:过期后允许再试一次(源可能已经恢复) */
    private static final long DEAD_CHANNEL_TTL_MS = 5 * 60 * 1000L;
    /** 已判定无可用线路的频道(组/频道名 → 判定时刻),换台时直接跳过,不必再等一轮超时 */
    private final java.util.Map<String, Long> deadChannels = new java.util.HashMap<>();
    /** 本轮连续自动跳过的次数:一旦播成功就清零 */
    private int autoSkipCount = 0;
    private String preloadFailUrl = null;
    private long preloadFailAt = 0L;
    /** 是否已挂着一个待执行的预加载任务(避免频繁的 buffer 回调把定时器一直往后推) */
    private boolean preloadScheduled = false;
    /** 启动自动检查更新是否已安排(每个页面实例只安排一次) */
    private boolean updateCheckScheduled = false;

    private final Runnable mPreloadRun = new Runnable() {
        @Override
        public void run() {
            preloadScheduled = false;
            preloadNeighborChannel();
        }
    };
    private final Runnable mPreloadRefreshRun = new Runnable() {
        @Override
        public void run() {
            if (!pageActive || !LiveConfig.preloadEnabled()) return;
            preloadNeighborChannel();
            mHandler.postDelayed(this, PRELOAD_REFRESH_MS);
        }
    };

    /** 视频区换台手势:本次按下是否已接管 */
    private boolean zapGestureTracking = false;
    private float zapDownRawX = 0f;
    private float zapDownRawY = 0f;
    private final int[] zapVideoLocation = new int[2];

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_live;
    }

    @Override
    protected void init() {
        ImmersionBar.with(this)
                .statusBarColor(R.color.black)
                .statusBarDarkFont(false)
                .navigationBarColor(R.color.black)
                .fitsSystemWindows(true)
                .hideBar(BarHide.FLAG_HIDE_NAVIGATION_BAR)
                .init();
        epgStringAddress = LiveConfig.epgUrl();
        if(epgStringAddress == null || epgStringAddress.length()<5)
            epgStringAddress = "http://epg.51zmt.top:8000/api/diyp/";

        setLoadSir(findViewById(R.id.live_root));
        mVideoView = findViewById(R.id.mVideoView);
        // 预加载结果只用来记日志和做失败退避,不影响播放流程
        mVideoView.setOnPreloadListener(new VideoView.OnPreloadListener() {
            @Override
            public void onPreloadReady(String url) {
                AppLog.log("直播", "预加载就绪: " + url);
            }

            @Override
            public void onPreloadFailed(String url) {
                preloadFailUrl = url;
                preloadFailAt = SystemClock.elapsedRealtime();
                AppLog.log("直播", "预加载失败(退避 5 分钟): " + url);
            }
        });

        tvLeftChannelListLayout = findViewById(R.id.tvLeftChannnelListLayout);

        mChannelGroupView = findViewById(R.id.mGroupGridView);
        mLiveChannelView = findViewById(R.id.mChannelGridView);
        mChannelGroupView.addItemDecoration(new LinearSpacingItemDecoration(20,true));
        mLiveChannelView.addItemDecoration(new LinearSpacingItemDecoration(20,true));

        tvChannelInfo = findViewById(R.id.tvChannel);

        //EPG  findViewById  by 龍
        tip_chname = findViewById(R.id.tv_channel_bar_name);//底部名称
        tip_chname.setOnClickListener(view -> {
            mChannelGroupView.scrollToPosition(currentChannelGroupIndex);
            mLiveChannelView.scrollToPosition(currentLiveChannelIndex);
        });
        tv_channelnum = findViewById(R.id.tv_channel_bottom_number); //底部数字

        tv_srcinfo = findViewById(R.id.tv_source);//线路状态

        //源切换
        findViewById(R.id.ic_pre_source).setOnClickListener(view -> playPreSource());
        findViewById(R.id.ic_next_source).setOnClickListener(view -> playNextSource());
        tv_srcinfo.setOnClickListener(view -> showLineSelectDialog(false));
        //投屏/设置
        findViewById(R.id.ic_setting).setOnClickListener(view -> showSettingDialog(false));
        findViewById(R.id.ic_cast).setOnClickListener(view -> showCastDialog());
        View refreshBtn = findViewById(R.id.ic_refresh);
        if (refreshBtn != null) {
            refreshBtn.setOnClickListener(view -> {
                if (!isCurrentLiveChannelValid()) return;
                playChannel(currentChannelGroupIndex, currentLiveChannelIndex, true);
            });
        }

        setupReloadSourceButton();

        initVideoView();
        initChannelGroupView();
        initLiveChannelView();
        initLiveChannelList();
    }

    // ------------------------------------------------------------------
    // 刷新直播源:常驻按钮(单击刷新/长按隐藏) + 定时自动刷新
    // ------------------------------------------------------------------

    /**
     * 绑定"刷新直播源"按钮。
     * 显隐状态持久化:长按隐藏后原地留一个小圆点,单击圆点即可叫回来(只需要一次点击)。
     */
    private void setupReloadSourceButton() {
        mReloadSourceBtn = findViewById(R.id.ic_reload_source);
        mReloadDot = findViewById(R.id.iv_reload_dot);
        reloadBtnHidden = LiveConfig.reloadBtnHidden();
        applyReloadBtnState(reloadBtnHidden);

        if (mReloadSourceBtn != null) {
            mReloadSourceBtn.setOnClickListener(view -> {
                // 隐藏态下这一次点击只用来"叫回按钮",不误触发刷新
                if (reloadBtnHidden) {
                    setReloadBtnHidden(false);
                    return;
                }
                reloadChannels(true);
            });
            mReloadSourceBtn.setOnLongClickListener(view -> {
                setReloadBtnHidden(true);
                AppBubble.toast("刷新按钮已隐藏,点小圆点可恢复");
                return true;
            });
        }
        if (mReloadDot != null) {
            mReloadDot.setOnClickListener(view -> setReloadBtnHidden(false));
            // 隐藏态下长按小圆点 = 更换直播源(内置地址失效时的应急入口)
            mReloadDot.setOnLongClickListener(view -> {
                showLiveSourceDialog();
                return true;
            });
        }
    }

    /** 更换直播源:改完地址若真的变了,重新拉一遍频道表 */
    private void showLiveSourceDialog() {
        final String before = SystemConfig.getLiveUrl();
        // XPopup 2.x 没有对外暴露"关闭监听",onDismiss 是给子类覆写的钩子,这里就地继承
        LiveApiDialog dialog = new LiveApiDialog(this) {
            @Override
            protected void onDismiss() {
                super.onDismiss();
                if (!TextUtils.equals(before, SystemConfig.getLiveUrl())) {
                    reloadChannels(true);
                }
            }
        };
        DialogCoordinator.center(this, dialog).show();
    }

    private void setReloadBtnHidden(boolean hidden) {
        reloadBtnHidden = hidden;
        LiveConfig.setReloadBtnHidden(hidden);
        applyReloadBtnState(hidden);
    }

    private void applyReloadBtnState(boolean hidden) {
        if (mReloadSourceBtn != null) {
            mReloadSourceBtn.setVisibility(hidden ? View.GONE : View.VISIBLE);
        }
        if (mReloadDot != null) {
            mReloadDot.setVisibility(hidden ? View.VISIBLE : View.GONE);
        }
    }

    /** 设置里的直播源地址;被清空过就回落到内置默认地址,保证"装完打开就能看" */
    private String resolveLiveSourceUrl() {
        String url = SystemConfig.getLiveUrl();
        if (TextUtils.isEmpty(url)) url = DefaultConfig.DEFAULT_LIVE_URL;
        return url;
    }

    // ── 频道表本地缓存:冷启动先出画面,再后台静默换新 ──

    /** 缓存文件名按直播源地址区分:换源不会读到上一个源的频道表 */
    private File liveCacheFile(String url) {
        return new File(getFilesDir(), "live_cache_" + Integer.toHexString(url.hashCode()) + ".json");
    }

    private void loadLivesFromCache(String url) {
        channelLoading = true;
        final long request = ++sourceLoadGeneration;
        LiveSourceParser.readCacheAsync(liveCacheFile(url), mHandler, result -> {
            if (!isSourceLoadCurrent(request)) return;
            channelLoading = false;
            if (result != null && result.grouped != null && result.grouped.size() > 0) {
                try {
                    com.github.tvbox.osc.spiderapi.LiveChannelConfigProviders.get().loadLives(result.grouped);
                    List<LiveChannelGroup> list = com.github.tvbox.osc.spiderapi.LiveChannelConfigProviders.get().getChannelGroupList();
                    if (!list.isEmpty()) {
                        liveChannelGroupList = new ArrayList<>(list);
                        showSuccess();
                        initLiveState();
                        reloadFailCount = 0;
                        scheduleNextAutoReload(1500L);
                        return;
                    }
                } catch (Throwable th) {
                    // 缓存能通过分组规整却缺 channels 等字段:应用失败别在主线程崩,回退联网重拉
                    AppLog.log("直播", "应用频道缓存失败: " + th.getMessage());
                }
            }
            loadLivesFromUrl(url, null, true);
        });
    }

    /**
     * 重新拉频道表。
     *
     * @param byUser true = 用户点按钮:强制重拉并续播当前频道(会重新起流,从而换到频道表里的新地址);
     *               false = 定时/静默:只更新列表数据,不打断正在看的台
     */
    private void reloadChannels(boolean byUser) {
        if (channelLoading) {
            if (byUser) AppBubble.toast("正在刷新,请稍候");
            return;
        }
        String url = resolveLiveSourceUrl();
        if (TextUtils.isEmpty(url)) {
            if (byUser) AppBubble.toast("未配置直播源");
            return;
        }
        if (byUser) AppBubble.toast("正在刷新频道源…");
        loadLivesFromUrl(url, null, byUser);
    }

    /** 安排下一次自动刷新;delay 过小时(比如刚回前台已过期)按下限兜一下,避免连发 */
    private void scheduleNextAutoReload(long delayMs) {
        mHandler.removeCallbacks(mAutoReloadRun);
        if (pageActive) mHandler.postDelayed(mAutoReloadRun, Math.max(2000L, delayMs));
    }

    //显示底部EPG
    private void showBottomEpg() {
        if (channel_Name.getChannelName() != null) {
            mSideControlView.setTitle(channel_Name.getChannelName());
            if (mNormalControlView != null) mNormalControlView.setTitle(channel_Name.getChannelName());
            tip_chname.setText(channel_Name.getChannelName());
            tv_channelnum.setText("" + channel_Name.getChannelNum());
            //todo 上一/下一/当前节目信息
        }
        updateLineText();
    }

    /** 同步线路文字(页面 tv_srcinfo 与全屏线路标签),只显示当前线路序号 */
    private void updateLineText() {
        String info = "线路1";
        if (channel_Name != null && channel_Name.getSourceNum() > 0) {
            info = "线路" + (channel_Name.getSourceIndex() + 1);
        }
        tv_srcinfo.setText(info);
        mSideControlView.setLineInfo(info);
    }


    @Override
    public void onBackPressed() {
        if(isBack){
            isBack= false;
            playPreSource();
        } else if(mSettingBottomDialog!=null && mSettingBottomDialog.isShow()){//适配底部导航栏(手势条闪屏)变成view模式后在back时手动隐藏
            mSettingBottomDialog.dismiss();
        } else if(mSettingRightDialog!=null && mSettingRightDialog.isShow()){
            mSettingRightDialog.dismiss();
        }  else if(mAllChannelRightDialog!=null && mAllChannelRightDialog.isShow()){
            mAllChannelRightDialog.dismiss();
        } else if (!mVideoView.onBackPressed()) {
            super.onBackPressed();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_MENU) {
                // 菜单键预留:直播设置已走底部/抽屉弹窗(showSettingDialog)
            } else if (!isListOrSettingLayoutVisible()) {
                switch (keyCode) {
                    case KeyEvent.KEYCODE_DPAD_UP:
                        if (LiveConfig.channelReverse())
                            playNext();
                        else
                            playPrevious();
                        break;
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        if (LiveConfig.channelReverse())
                            playPrevious();
                        else
                            playNext();
                        break;
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                        if(isBack){

                        }
                        break;
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        if(isBack){

                        }else{
                            playNextSource();
                        }
                        break;
                    case KeyEvent.KEYCODE_DPAD_CENTER:
                    case KeyEvent.KEYCODE_ENTER:
                    case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                        showChannelList();
                        break;
                }
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        pageActive = true;
        if (mVideoView != null) {
            mVideoView.resume();
            int state = mVideoView.getCurrentPlayState();
            if (state == VideoView.STATE_PREPARING || state == VideoView.STATE_BUFFERING
                    || state == VideoView.STATE_PREPARED) {
                schedulePlaybackRetry(retryDelayMs());
            } else if (state == VideoView.STATE_ERROR || state == VideoView.STATE_PLAYBACK_COMPLETED) {
                schedulePlaybackRetry(2000L);
            }
        }
        if (liveChannelGroupList.isEmpty() && !channelLoading) initLiveChannelList();
        // 回到前台:频道表放旧了就静默更新一次,否则把定时器按剩余时间重新挂上
        if (lastChannelLoadedAt > 0) {
            long age = SystemClock.elapsedRealtime() - lastChannelLoadedAt;
            if (age >= STALE_RELOAD_MS) {
                scheduleNextAutoReload(2000L);
            } else {
                scheduleNextAutoReload(AUTO_RELOAD_INTERVAL_MS - age);
            }
        } else if (!liveChannelGroupList.isEmpty() && !channelLoading) {
            // 缓存命中后还没成功联网就被切到后台:回前台补一次刷新,别让首刷永久丢失
            scheduleNextAutoReload(2000L);
        }

        // 回到前台:恢复换台预加载(后台期间是停掉的)
        mHandler.removeCallbacks(mPreloadRefreshRun);
        if (LiveConfig.preloadEnabled()) {
            schedulePreloadNeighbor(PRELOAD_DELAY_MS);
            mHandler.postDelayed(mPreloadRefreshRun, PRELOAD_REFRESH_MS);
        }

        // 进前台 5 秒后查一次更新(不依赖起播是否成功,源挂了也能收到升级提示)
        scheduleUpdateCheck();

        // 特殊权限(所有文件访问 / 安装未知应用)系统不会弹授权框,只能主动引导;
        // 延迟一点,等直播页画面起来、频道表开始加载之后再弹,别和首屏抢焦点
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || isDestroyed()) return;
                PermissionGuide.checkAndGuide(LiveActivity.this);
            }
        }, 900L);
    }


    @Override
    protected void onPause() {
        pageActive = false;
        sourceLoadGeneration++;
        channelLoading = false;
        HttpClient.cancel(liveSourceTag);
        playbackRetry.invalidate();
        cancelPlaybackRetry();
        mHandler.removeCallbacks(mAutoReloadRun);
        mHandler.removeCallbacks(mPreloadRun);
        mHandler.removeCallbacks(mPreloadRefreshRun);
        preloadScheduled = false;
        if (mVideoView != null) {
            mVideoView.cancelPreload();
            mVideoView.pause();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        pageActive = false;
        sourceLoadGeneration++;
        HttpClient.cancel(liveSourceTag);
        playbackRetry.invalidate();
        cancelPlaybackRetry();
        mHandler.removeCallbacksAndMessages(null);
        if (mVideoView != null) {
            mVideoView.setOnPreloadListener(null);
            mVideoView.release();
            mVideoView = null;
        }
        channel_Name = null;
        currentLiveChannelItem = null;
        super.onDestroy();
    }

    private void showChannelList() {
        //重新载入上一次状态
        // 刷新频道表后分组可能变少,currentChannelGroupIndex 可能已经越界(静态字段会跨页面残留)
        if (currentChannelGroupIndex < 0 || currentChannelGroupIndex >= liveChannelGroupList.size()) return;
        liveChannelItemAdapter.setNewData(getLiveChannels(currentChannelGroupIndex));
        if (currentLiveChannelIndex > -1){
            mLiveChannelView.smoothScrollToPosition(currentLiveChannelIndex);
            if (currentChannelGroupIndex==0){
                mChannelGroupView.scrollToPosition(currentChannelGroupIndex);
            }else {
                mChannelGroupView.smoothScrollToPosition(currentChannelGroupIndex);
            }
        }
    }

    private void showChannelInfo() {
        tvChannelInfo.setText(String.format(Locale.getDefault(), "%d %s %s(%d/%d)", currentLiveChannelItem.getChannelNum(),
                currentLiveChannelItem.getChannelName(), currentLiveChannelItem.getSourceName(),
                currentLiveChannelItem.getSourceIndex() + 1, currentLiveChannelItem.getSourceNum()));

        FrameLayout.LayoutParams lParams = new FrameLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lParams.gravity = Gravity.RIGHT;
        lParams.rightMargin = 60;
        lParams.topMargin = 30;
        tvChannelInfo.setLayoutParams(lParams);

        tvChannelInfo.setVisibility(View.VISIBLE);
        mHandler.removeCallbacks(mHideChannelInfoRun);
        mHandler.postDelayed(mHideChannelInfoRun, 3000);
    }

    private Runnable mHideChannelInfoRun = new Runnable() {
        @Override
        public void run() {
            tvChannelInfo.setVisibility(View.INVISIBLE);
        }
    };

    private boolean playChannel(int channelGroupIndex, int liveChannelIndex, boolean changeSource) {
        return playChannel(channelGroupIndex, liveChannelIndex, changeSource, false);
    }

    private boolean playChannel(int channelGroupIndex, int liveChannelIndex, boolean changeSource, boolean automaticRetry) {
        if (!pageActive || mVideoView == null || isFinishing() || isDestroyed()) return false;
        if (changeSource) {
            if (currentLiveChannelItem == null) return false;
        } else {
            if (channelGroupIndex < 0 || channelGroupIndex >= liveChannelGroupList.size()) return false;
            ArrayList<LiveChannelItem> channels = getLiveChannels(channelGroupIndex);
            if (liveChannelIndex < 0 || liveChannelIndex >= channels.size()) return false;
            int state = mVideoView.getCurrentPlayState();
            if (channelGroupIndex == currentChannelGroupIndex && liveChannelIndex == currentLiveChannelIndex
                    && state != VideoView.STATE_IDLE && state != VideoView.STATE_ERROR
                    && state != VideoView.STATE_PLAYBACK_COMPLETED) return true;
        }
        cancelPlaybackRetry();
        playbackRetry.begin(automaticRetry);
        mHandler.removeCallbacks(mPreloadRun);
        preloadScheduled = false;
        // 换台前先看这个台有没有自己的内核/渲染配置(改了就必须重建,不能复用实例)
        boolean kernelChanged = false;
        if (!changeSource) {
            currentChannelGroupIndex = channelGroupIndex;
            currentLiveChannelIndex = liveChannelIndex;
            currentLiveChannelItem = getLiveChannels(currentChannelGroupIndex).get(currentLiveChannelIndex);
            LiveConfig.setLastChannel(currentLiveChannelItem.getChannelName());
            livePlayerManager.getLiveChannelPlayer(mVideoView, currentLiveChannelItem.getChannelName());
            kernelChanged = livePlayerManager.consumeRebuildRequired();
            if (kernelChanged) mVideoView.cancelPreload();
        } else {
            livePlayerManager.consumeRebuildRequired();   // 线路切换不动内核,清掉残留标记
        }

        channel_Name = currentLiveChannelItem;
        isBack = false;
        if(currentLiveChannelItem.getUrl().indexOf("PLTV/8888") !=-1){
            currentLiveChannelItem.setinclude_back(true);
        }else {
            currentLiveChannelItem.setinclude_back(false);
        }
        showBottomEpg();

        final String url = currentLiveChannelItem.getUrl();
        AppLog.log("直播", "播放频道[" + currentLiveChannelItem.getChannelName() + "] 源[" + (currentLiveChannelItem.getSourceIndex() + 1) + "/" + currentLiveChannelItem.getSourceNum() + "] " + url);
        com.github.tvbox.osc.log.LogStore.log(com.github.tvbox.osc.log.Category.OTHER,
                "直播: 播放频道[" + currentLiveChannelItem.getChannelName() + "] 源["
                        + (currentLiveChannelItem.getSourceIndex() + 1) + "/"
                        + currentLiveChannelItem.getSourceNum() + "]");

        if (!url.equals(mVideoView.getPreloadedUrl()) || !mVideoView.isPreloadUsable()) {
            mVideoView.cancelPreload();
        }
        boolean promoted = !kernelChanged
                && LiveConfig.preloadEnabled()
                && LiveConfig.channelPlayerConfig(currentLiveChannelItem.getChannelName()) == null
                && mVideoView.promotePreloadedIfMatches(url);
        if (promoted) {
            AppLog.log("直播", "换台命中预加载,直接扶正: " + currentLiveChannelItem.getChannelName());
            com.github.tvbox.osc.log.LogStore.log(com.github.tvbox.osc.log.Category.OTHER,
                    "直播: 换台命中预加载[" + currentLiveChannelItem.getChannelName() + "]");
        } else if (kernelChanged || !mVideoView.switchUrl(url, null)) {
            mVideoView.release();
            mVideoView.setUrl(url);
        }
        mVideoView.start();
        // 换台后把"台号 + 台名 + 线路(x/y)"显示 3 秒,让用户确认切到了哪个台
        if (currentLiveChannelItem != null) showChannelInfo();
        return true;
    }

    private void playNext() {
        if (!isCurrentLiveChannelValid()) return;
        lastZapDirection = 1;
        Integer[] groupChannelIndex = getNextChannel(1);
        playChannel(groupChannelIndex[0], groupChannelIndex[1], false);
    }

    private void playPrevious() {
        if (!isCurrentLiveChannelValid()) return;
        lastZapDirection = -1;
        Integer[] groupChannelIndex = getNextChannel(-1);
        playChannel(groupChannelIndex[0], groupChannelIndex[1], false);
    }

    public void playPreSource() {
        if (!isCurrentLiveChannelValid()) return;
        currentLiveChannelItem.preSource();
        playChannel(currentChannelGroupIndex, currentLiveChannelIndex, true);
        updateLineText();
    }

    public void playNextSource() {
        if (!isCurrentLiveChannelValid()) return;
        currentLiveChannelItem.nextSource();
        playChannel(currentChannelGroupIndex, currentLiveChannelIndex, true);
        updateLineText();
    }

//    private void initVideoView() {
//        StandardVideoController controller = new StandardVideoController(this);
//        controller.addControlComponent(new LiveControlView(this)); //直播控制条
//        controller.setEnableInNormal(true);
//        controller.setGestureEnabled(true);
//        mVideoView.setVideoController(controller);
//        mVideoView.setProgressManager(null);
//    }

    private void initVideoView() {
        LiveNewController controller = new LiveNewController(this);
        // 单条右缘垂直居中控制栏(返回/台名/时间/播放暂停/刷新/投屏/换台/设置)
        mSideControlView = new LiveSideControlView(this);
        mSideControlView.setOnLiveSideListener(new LiveSideControlView.OnLiveSideListener() {
            @Override
            public void onExpand() {
                showAllChannelDialog();
            }

            @Override
            public void onSetting() {
                showSettingDialog(true);
            }

            @Override
            public void onCast() {
                showCastDialog();
            }

            @Override
            public void onBack() {
                if (mVideoView != null && mVideoView.isFullScreen()) {
                    mVideoView.stopFullScreen();
                } else {
                    finish();
                }
            }

            @Override
            public void onLineClick() {
                showLineSelectDialog(true);
            }

            @Override
            public void onRefresh() {
                // 重播当前线路
                playChannel(currentChannelGroupIndex, currentLiveChannelIndex, true);
            }
        });
        controller.addControlComponent(mSideControlView);
        // 非全屏小窗横向控制条(频道名/时间/暂停/刷新/全屏)
        LiveNormalControlView normalControlView = new LiveNormalControlView(this);
        normalControlView.setOnNormalListener(new LiveNormalControlView.OnNormalListener() {
            @Override
            public void onRefresh() {
                playChannel(currentChannelGroupIndex, currentLiveChannelIndex, true);
            }
        });
        controller.addControlComponent(normalControlView);
        mNormalControlView = normalControlView;
        controller.setListener(new LiveNewController.LiveControlListener() {

            @Override
            public void setting() {
                // 直播设置已走 showSettingDialog(底部/抽屉弹窗),这里不再内嵌面板
            }

            @Override
            public void playStateChanged(int playState) {
                if (!pageActive || mVideoView == null) return;
                switch (playState) {
                    case VideoView.STATE_IDLE:
                    case VideoView.STATE_PAUSED:
                        cancelPlaybackRetry();
                        break;
                    case VideoView.STATE_PLAYING:
                        playbackRetry.onPlaying();
                        autoSkipCount = 0;   // 播成功:自动跳过的连续计数清零
                        cancelPlaybackRetry();
                        schedulePreloadNeighbor(PRELOAD_DELAY_MS);
                        scheduleUpdateCheck();
                        break;
                    case VideoView.STATE_BUFFERED:
                        cancelPlaybackRetry();
                        schedulePreloadNeighbor(PRELOAD_DELAY_MS);
                        break;
                    case VideoView.STATE_ERROR:
                    case VideoView.STATE_PLAYBACK_COMPLETED:
                        mHandler.removeCallbacks(mPreloadRun);
                        preloadScheduled = false;
                        mVideoView.cancelPreload();
                        schedulePlaybackRetry(2000L);
                        break;
                    case VideoView.STATE_PREPARED:
                    case VideoView.STATE_PREPARING:
                    case VideoView.STATE_BUFFERING:
                        mHandler.removeCallbacks(mPreloadRun);
                        preloadScheduled = false;
                        if (playState == VideoView.STATE_BUFFERING) mVideoView.cancelPreload();
                        schedulePlaybackRetry(retryDelayMs());
                        break;
                }
            }

            @Override
            public void changeSource(int direction) {
                if (direction > 0){
                    playNextSource();
                } else {
                    playPreSource();
                }
            }

            @Override
            public void changeChannel(int direction) {
                zapByGesture(direction);
            }

            @Override
            public String previewChannelName(int direction) {
                return getChannelNameByDirection(direction);
            }
        });
        controller.setCanChangePosition(false);
        controller.setEnableInNormal(true);
        controller.setGestureEnabled(true);
        controller.setDoubleTapTogglePlayEnabled(false);
        mVideoView.setVideoController(controller);
        mVideoView.setProgressManager(null);
    }

    private void cancelPlaybackRetry() {
        if (connectTimeoutTask != null) mHandler.removeCallbacks(connectTimeoutTask);
        connectTimeoutTask = null;
    }

    /** 连续跳过坏台时的单台判死上限:比用户设置的"超时换源"更短,别让人一直等 */
    private static final long AUTO_SKIP_RETRY_MS = 6000L;

    /**
     * 这一轮"等多久算这个台/这条线路挂了"。
     * 正常按用户设置(超时换源:5~30 秒);一旦进入"连续跳过坏台"状态就压到 6 秒——
     * 此时用户要的是赶紧跳到能看的台,不是在坏台上按设置死等。
     */
    private long retryDelayMs() {
        long normal = (LiveConfig.connectTimeout() + 1) * 5000L;
        return autoSkipCount > 0 ? Math.min(normal, AUTO_SKIP_RETRY_MS) : normal;
    }

    private void schedulePlaybackRetry(long delayMs) {
        cancelPlaybackRetry();
        if (!pageActive || currentLiveChannelItem == null || mVideoView == null) return;
        final long request = playbackRetry.generation();
        connectTimeoutTask = () -> {
            connectTimeoutTask = null;
            if (!pageActive || !playbackRetry.isCurrent(request)
                    || currentLiveChannelItem == null || mVideoView == null) return;
            if (!playbackRetry.tryNextSource(currentLiveChannelItem.getSourceNum())) {
                // 这个台所有线路都拉不起来:不要再让用户干等 + 手动切,直接跳到下一个台
                playbackRetry.invalidate();
                onChannelExhausted();
                return;
            }
            currentLiveChannelItem.nextSource();
            playChannel(currentChannelGroupIndex, currentLiveChannelIndex, true, true);
            updateLineText();
        };
        mHandler.postDelayed(connectTimeoutTask, delayMs);
    }

    /**
     * 当前频道所有线路都失败:自动跳到下一个频道。
     * 之前这里是 release + "当前频道无可用线路"提示,用户要干等一轮超时(默认每条线路 10s)
     * 再手动换台,体验很差;而且同一批坏台来回切时会反复等。
     * 现在:记住这个台(5 分钟内直接跳过),按上次换台方向自动跳到下一个台;
     * 连续跳过达到 {@link #MAX_AUTO_SKIP} 个仍不成功就停下并提示,避免整张表都没信号时空转。
     */
    private void onChannelExhausted() {
        if (!pageActive || currentLiveChannelItem == null || mVideoView == null) return;
        String name = currentLiveChannelItem.getChannelName();
        markChannelDead(currentChannelGroupIndex, currentLiveChannelIndex);
        int dir = lastZapDirection == 0 ? 1 : lastZapDirection;
        // 连续跳太多次、或这个方向上剩下的都是刚判死的台:就停下,别无限空转
        if (autoSkipCount >= MAX_AUTO_SKIP || resolveZapTarget(dir) == null) {
            autoSkipCount = 0;
            mVideoView.release();
            AppBubble.toastLong("连续多个频道都没有可用线路,已停止自动跳过");
            return;
        }
        autoSkipCount++;
        AppBubble.toast("「" + name + "」无可用线路,已自动跳过");
        zapChannel(dir);
    }

    /** 频道标识:组下标 + 台名(台名在组内唯一,跨组可能重名) */
    private String channelKey(int groupIndex, int channelIndex) {
        try {
            return groupIndex + "/" + getLiveChannels(groupIndex).get(channelIndex).getChannelName();
        } catch (Throwable th) {
            return null;
        }
    }

    private void markChannelDead(int groupIndex, int channelIndex) {
        String key = channelKey(groupIndex, channelIndex);
        if (key != null) deadChannels.put(key, SystemClock.elapsedRealtime());
    }

    private boolean isChannelDead(int groupIndex, int channelIndex) {
        String key = channelKey(groupIndex, channelIndex);
        if (key == null) return false;
        Long at = deadChannels.get(key);
        if (at == null) return false;
        if (SystemClock.elapsedRealtime() - at > DEAD_CHANNEL_TTL_MS) {
            deadChannels.remove(key);
            return false;
        }
        return true;
    }

    private void initChannelGroupView() {
        mChannelGroupView.setHasFixedSize(true);
        mChannelGroupView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));

        liveChannelGroupAdapter = new LiveChannelGroupNewAdapter();
        mChannelGroupView.setAdapter(liveChannelGroupAdapter);

        //手机/模拟器
        liveChannelGroupAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                selectChannelGroup(position, false, -1);
            }
        });
    }

    private void selectChannelGroup(int groupIndex, boolean focus, int liveChannelIndex) {
        if (groupIndex < 0 || groupIndex >= liveChannelGroupList.size()) return;
        if (focus) {
            liveChannelGroupAdapter.setFocusedGroupIndex(groupIndex);
            liveChannelItemAdapter.setFocusedChannelIndex(-1);
        }
        if ((groupIndex > -1 && groupIndex != liveChannelGroupAdapter.getSelectedGroupIndex()) || isNeedInputPassword(groupIndex)) {
            liveChannelGroupAdapter.setSelectedGroupIndex(groupIndex);
            if (isNeedInputPassword(groupIndex)) {
                showPasswordDialog(groupIndex, liveChannelIndex);
                return;
            }
            loadChannelGroupDataAndPlay(groupIndex, liveChannelIndex);
        }
    }

    private void initLiveChannelView() {
        mLiveChannelView.setHasFixedSize(true);
        mLiveChannelView.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));

        liveChannelItemAdapter = new LiveChannelItemNewAdapter();
        mLiveChannelView.setAdapter(liveChannelItemAdapter);

        liveChannelItemAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                clickLiveChannel(position);
            }
        });
    }

    private void clickLiveChannel(int position) {
        // 分组下标优先取适配器里"当前高亮的组";它偶尔会是 -1(刷新后/首次进页),
        // 这时 -1 传下去会一路 groups[-1] 崩掉,回落到 currentChannelGroupIndex
        int groupIndex = liveChannelGroupAdapter.getSelectedGroupIndex();
        if (groupIndex < 0 || groupIndex >= liveChannelGroupList.size()) {
            groupIndex = currentChannelGroupIndex;
        }
        if (groupIndex < 0 || groupIndex >= liveChannelGroupList.size()) return;
        if (position < 0 || position >= getLiveChannels(groupIndex).size()) return;
        // 列表点选也记一下方向:预加载会按"下一个台"准备
        if (groupIndex == currentChannelGroupIndex && position != currentLiveChannelIndex) {
            lastZapDirection = position > currentLiveChannelIndex ? 1 : -1;
        }
        liveChannelItemAdapter.setSelectedChannelIndex(position);
        playChannel(groupIndex, position, false);
    }


    private void initLiveChannelList() {
        List<LiveChannelGroup> list = com.github.tvbox.osc.spiderapi.LiveChannelConfigProviders.get().getChannelGroupList();
        if (list.isEmpty()) {
            // 本包默认不带任何 JSON 订阅(点播那一套),直播走"设置里的直播源",空则用内置默认地址。
            // 这样装完打开就能看电视,不需要先配订阅。
            String url = resolveLiveSourceUrl();
            if (TextUtils.isEmpty(url)) {
                AppBubble.toast("频道列表为空");
                showEmpty();
                return;
            }
            showLoading();
            loadLivesFromCache(url);
            return;
        }

        if (isProxyOnly(list)) {
            // 主列表是"待拉取的直播源"(设置里配置的直播源):拉它;
            // 没内容/失败再退到订阅源自带的直播(见 useFallbackOrFail)
            loadProxyLives(list.get(0).getGroupName(),
                    com.github.tvbox.osc.spiderapi.LiveChannelConfigProviders.get().getFallbackChannelGroupList());
        }
        else {
            // 没配直播源(用的是订阅源自带的直播):直接用
            liveChannelGroupList.clear();
            liveChannelGroupList.addAll(list);
            showSuccess();
            initLiveState();
        }
    }

    /** 单个"待拉取"的代理分组 = 一个直播源地址(设置里的直播源 / 订阅源的直播地址都是这个形态) */
    private static boolean isProxyOnly(List<LiveChannelGroup> list) {
        return list != null && list.size() == 1
                && list.get(0) != null && list.get(0).getGroupName() != null
                && list.get(0).getGroupName().startsWith("http://127.0.0.1");
    }

    /**
     * 拉取一个"代理分组"形态的直播源地址(设置里的直播源 / 订阅源自带的直播都是这个形态):
     * 地址的 ext 参数是 base64 过的真实 URL,解出来再交给 {@link #loadLivesFromUrl}。
     *
     * @param fallback 该直播源没内容/加载失败时的兜底(订阅源自带的直播);null/空 = 没有兜底,直接进空态。
     *                 <b>主直播源优先</b>:用户配的直播源能用就绝不碰订阅源的直播。
     */
    public void loadProxyLives(String url, List<LiveChannelGroup> fallback) {
        String realUrl;
        try {
            Uri parsedUrl = Uri.parse(url);
            realUrl = new String(Base64.decode(parsedUrl.getQueryParameter("ext"), Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP), "UTF-8");
        } catch (Throwable th) {
            useFallbackOrFail(fallback, "频道列表为空");
            return;
        }
        loadLivesFromUrl(realUrl, fallback, true);
    }

    /**
     * 真正拉取并解析一个直播源地址。
     *
     * @param fallback       该地址没内容/加载失败时的兜底(订阅源自带的直播);null/空 = 没有兜底,直接进空态
     * @param resumePlayback true = 加载完重建列表并按"上次看的频道名"续播(能换到新地址,用户手动刷新走这条);
     *                       false = 只把新频道表灌进列表,不打断正在看的台(定时静默刷新走这条)
     */
    private void loadLivesFromUrl(final String url, final List<LiveChannelGroup> fallback, final boolean resumePlayback) {
        if (!pageActive || channelLoading) return;
        channelLoading = true;
        final long request = ++sourceLoadGeneration;
        if (resumePlayback && currentLiveChannelItem == null) showLoading();
        AppLog.log("直播", "加载直播源: " + url);
        HttpClient.get(url, liveSourceTag, new HCallBack() {

            @Override
            public void onSuccess(String content) {
                if (!isSourceLoadCurrent(request)) return;
                LiveSourceParser.parseAsync(content, mHandler, new LiveSourceParser.Callback() {
                    @Override
                    public void onParsed(LiveSourceParser.Result result) {
                        if (!isSourceLoadCurrent(request)) return;
                        if (result == null) {
                            channelLoading = false;
                            AppLog.log("直播", "解析失败: 内容不是可用的频道表");
                            com.github.tvbox.osc.log.LogStore.log(com.github.tvbox.osc.log.Category.OTHER,
                                    "直播: 直播源解析失败");
                            useFallbackOrFail(fallback, "直播源解析失败,请检查直播源地址");
                            return;
                        }
                        try {
                            String playingGroup = currentChannelGroupIndex >= 0 && currentChannelGroupIndex < liveChannelGroupList.size()
                                    ? liveChannelGroupList.get(currentChannelGroupIndex).getGroupName() : null;
                            com.github.tvbox.osc.spiderapi.LiveChannelConfigProviders.get().loadLives(result.grouped);
                            List<LiveChannelGroup> list = com.github.tvbox.osc.spiderapi.LiveChannelConfigProviders.get().getChannelGroupList();
                            if (list.isEmpty()) {
                                channelLoading = false;
                                useFallbackOrFail(fallback, "频道列表为空");
                                return;
                            }
                            liveChannelGroupList = new ArrayList<>(list);
                            channelGroupPasswordConfirmed.clear();

                            channelLoading = false;
                            lastChannelLoadedAt = SystemClock.elapsedRealtime();
                            reloadFailCount = 0;
                            LiveSourceParser.saveCacheAsync(liveCacheFile(url), result.raw);
                            scheduleNextAutoReload(AUTO_RELOAD_INTERVAL_MS);

                            com.github.tvbox.osc.log.LogStore.log(com.github.tvbox.osc.log.Category.OTHER,
                                    "直播: 直播源加载成功, 分组 " + list.size() + " 个");

                            if (isFinishing() || isDestroyed()) return;
                            if (resumePlayback || currentLiveChannelItem == null) {
                                showSuccess();
                                if (resumePlayback) {
                                    // 手动刷新必须先把"当前在看第几个"抹掉:否则 playChannel 会因为
                                    // 组号/台号都相同直接 return,结果列表换了、流却没重拉 —— 死台还是死台
                                    liveChannelGroupAdapter.setSelectedGroupIndex(-1);
                                    currentLiveChannelIndex = -1;
                                }
                                initLiveState();
                            } else {
                                refreshChannelListOnly(playingGroup);
                            }
                        } catch (Throwable th) {
                            th.printStackTrace();
                            channelLoading = false;
                            AppLog.log("直播", "应用频道表失败: " + th.getMessage());
                            com.github.tvbox.osc.log.LogStore.log(com.github.tvbox.osc.log.Category.OTHER,
                                    "直播: 应用频道表失败 " + th.getMessage());
                            useFallbackOrFail(fallback, "直播源解析失败,请检查直播源地址");
                        }
                    }
                });
            }

            @Override
            public void onError(Throwable e) {
                if (!isSourceLoadCurrent(request)) return;
                channelLoading = false;
                AppLog.log("直播", "加载失败: " + (e == null ? "null" : e.getMessage()));
                com.github.tvbox.osc.log.LogStore.log(com.github.tvbox.osc.log.Category.OTHER,
                        "直播: 直播源加载失败: " + (e == null ? "null" : e.getMessage()));
                useFallbackOrFail(fallback, "直播源加载失败,请检查网络或直播源");
            }
        });
    }

    private boolean isSourceLoadCurrent(long request) {
        return pageActive && request == sourceLoadGeneration && !isFinishing() && !isDestroyed();
    }

    private void refreshChannelListOnly(String playingGroup) {
        String channelName = currentLiveChannelItem == null ? null : currentLiveChannelItem.getChannelName();
        int[] position = LiveChannelNav.findChannel(liveChannelGroupList, playingGroup, channelName);
        currentChannelGroupIndex = position[0];
        currentLiveChannelIndex = position[1];
        if (currentChannelGroupIndex >= 0) {
            LiveChannelItem item = liveChannelGroupList.get(currentChannelGroupIndex).getLiveChannels().get(currentLiveChannelIndex);
            int source = item.getChannelUrls().indexOf(currentLiveChannelItem.getUrl());
            item.setSourceIndex(Math.max(0, source));
            currentLiveChannelItem = item;
            channel_Name = item;
            updateLineText();
            if (source < 0) {
                // 正在播的旧地址已不在新线路集合里:旧流的失败次数不能算到新线路上,重新计数;
                // 旧流若已挂起/出错则补挂一次超时,确保会切到新集合里未播过的线路
                playbackRetry.begin(false);
                int state = mVideoView == null ? VideoView.STATE_IDLE : mVideoView.getCurrentPlayState();
                if (state == VideoView.STATE_PREPARING || state == VideoView.STATE_BUFFERING || state == VideoView.STATE_PREPARED) {
                    schedulePlaybackRetry(retryDelayMs());
                } else if (state == VideoView.STATE_ERROR || state == VideoView.STATE_PLAYBACK_COMPLETED) {
                    schedulePlaybackRetry(2000L);
                }
            }
        }
        // 已移除的频道可继续播旧流，但其旧下标不能再用于新频道表。
        int visibleGroup = currentChannelGroupIndex >= 0 ? currentChannelGroupIndex : getFirstNoPasswordChannelGroup();
        liveChannelGroupAdapter.setNewData(liveChannelGroupList);
        liveChannelGroupAdapter.setSelectedGroupIndex(visibleGroup);
        liveChannelItemAdapter.setNewData(getLiveChannels(visibleGroup));
        liveChannelItemAdapter.setSelectedChannelIndex(currentLiveChannelIndex);
    }

    /**
     * 主直播源(设置里配置的直播源)没内容 / 加载失败时:退到<b>订阅源自带的直播</b>(只兜底一层,不再递归);
     * 连兜底都没有才进空态。会 toast 说明"改用订阅源的直播",避免用户以为配的直播源在生效。
     */
    private void useFallbackOrFail(List<LiveChannelGroup> fallback, String msg) {
        if (fallback != null && !fallback.isEmpty()) {
            AppBubble.toast("直播源不可用,改用订阅源的直播");
            com.github.tvbox.osc.log.LogStore.log(com.github.tvbox.osc.log.Category.OTHER,
                    "直播: 主直播源不可用, 回退订阅源直播");
            if (isProxyOnly(fallback)) {
                loadProxyLives(fallback.get(0).getGroupName(), null);
            } else {
                liveChannelGroupList.clear();
                liveChannelGroupList.addAll(fallback);
                showSuccess();
                initLiveState();
            }
            return;
        }
        onLiveLoadFail(msg);
    }

    /** 直播源加载失败:提示并进入空态,避免一直停留在加载中 */
    private void onLiveLoadFail(String msg) {
        try {
            // 已经在看缓存频道时,失败就别把画面顶成空态、也别弹提示打断观看
            boolean alreadyPlaying = currentLiveChannelItem != null && !liveChannelGroupList.isEmpty();
            if (!alreadyPlaying) {
                AppBubble.toast(msg);
                if (!isFinishing() && !isDestroyed()) {
                    showEmpty();
                }
            }
            scheduleRetryAfterFail();
        } catch (Throwable ignored) {
        }
    }

    /** 失败退避:2 → 4 → 8 → 16 分钟封顶,源恢复后自动回到 15 分钟正常节奏 */
    private void scheduleRetryAfterFail() {
        reloadFailCount = Math.min(reloadFailCount + 1, 4);
        long delay = RETRY_RELOAD_MS * (1L << (reloadFailCount - 1));
        scheduleNextAutoReload(Math.min(delay, AUTO_RELOAD_INTERVAL_MS));
    }

    private void initLiveState() {
        String lastChannelName = LiveConfig.lastChannel();

        int lastChannelGroupIndex = -1;
        int lastLiveChannelIndex = -1;
        for (LiveChannelGroup liveChannelGroup : liveChannelGroupList) {
            for (LiveChannelItem liveChannelItem : liveChannelGroup.getLiveChannels()) {
                if (liveChannelItem.getChannelName().equals(lastChannelName)) {
                    lastChannelGroupIndex = liveChannelGroup.getGroupIndex();
                    lastLiveChannelIndex = liveChannelItem.getChannelIndex();
                    break;
                }
            }
            if (lastChannelGroupIndex != -1) break;
        }
        if (lastChannelGroupIndex == -1) {
            lastChannelGroupIndex = getFirstNoPasswordChannelGroup();
            if (lastChannelGroupIndex == -1)
                lastChannelGroupIndex = 0;
            lastLiveChannelIndex = 0;
        }

        if (mVideoView == null || isFinishing() || isDestroyed()) return; // 防御:页面已销毁/播放器未初始化

        livePlayerManager.init(mVideoView);

        liveChannelGroupAdapter.setNewData(liveChannelGroupList);
        selectChannelGroup(lastChannelGroupIndex, false, lastLiveChannelIndex);
    }

    private boolean isListOrSettingLayoutVisible() {
        return tvLeftChannelListLayout.getVisibility() == View.VISIBLE;
    }

    private void showPasswordDialog(int groupIndex, int liveChannelIndex) {
        final LiveChannelGroup group = liveChannelGroupList.get(groupIndex);
        LivePasswordDialog dialog = new LivePasswordDialog(this);
        dialog.setOnListener(new LivePasswordDialog.OnListener() {
            @Override
            public void onChange(String password) {
                if (!pageActive || groupIndex >= liveChannelGroupList.size() || liveChannelGroupList.get(groupIndex) != group) return;
                if (password.equals(group.getGroupPassword())) {
                    channelGroupPasswordConfirmed.add(groupIndex);
                    loadChannelGroupDataAndPlay(groupIndex, liveChannelIndex);
                } else {
                    AppBubble.toast("密码错误");
                }
            }

            @Override
            public void onCancel() {
                if (tvLeftChannelListLayout.getVisibility() == View.VISIBLE) {
                    int groupIndex = liveChannelGroupAdapter.getSelectedGroupIndex();
                    liveChannelItemAdapter.setNewData(getLiveChannels(groupIndex));
                }
            }
        });
        dialog.show();
    }

    private void loadChannelGroupDataAndPlay(int groupIndex, int liveChannelIndex) {
        if (groupIndex < 0 || groupIndex >= liveChannelGroupList.size()) return;
        liveChannelItemAdapter.setNewData(getLiveChannels(groupIndex));
        if (groupIndex == currentChannelGroupIndex) {
            if (currentLiveChannelIndex > -1)
                mLiveChannelView.smoothScrollToPosition(currentLiveChannelIndex);
            liveChannelItemAdapter.setSelectedChannelIndex(currentLiveChannelIndex);
        }
        else {
            mLiveChannelView.smoothScrollToPosition(0);
            liveChannelItemAdapter.setSelectedChannelIndex(-1);
        }

        if (liveChannelIndex > -1) {
            clickLiveChannel(liveChannelIndex);
            if (groupIndex==0){//部分手机smoothScrollToPosition向上划出屏幕
                mChannelGroupView.scrollToPosition(groupIndex);
            }else {
                mChannelGroupView.smoothScrollToPosition(groupIndex);
            }

            mLiveChannelView.smoothScrollToPosition(liveChannelIndex);
            playChannel(groupIndex, liveChannelIndex, false);
        }
    }

    private boolean isNeedInputPassword(int groupIndex) {
        return LiveChannelAuth.needInputPassword(liveChannelGroupList, channelGroupPasswordConfirmed, groupIndex);
    }

    private boolean isPasswordConfirmed(int groupIndex) {
        return LiveChannelAuth.isPasswordConfirmed(channelGroupPasswordConfirmed, groupIndex);
    }

    private ArrayList<LiveChannelItem> getLiveChannels(int groupIndex) {
        if (groupIndex < 0 || groupIndex >= liveChannelGroupList.size()) return new ArrayList<>();
        if (LiveChannelAuth.needInputPassword(liveChannelGroupList, channelGroupPasswordConfirmed, groupIndex)) {
            return new ArrayList<>();
        }
        return liveChannelGroupList.get(groupIndex).getLiveChannels();
    }

    private Integer[] getNextChannel(int direction) {
        return getNextChannel(direction, LiveConfig.crossGroup());
    }

    /**
     * @param crossGroup 本组到头后是否跳到下一组。上下滑换台固定传 true —— 分组很多、不少组只有一两个台,
     *                   只在组内循环会出现"滑了半天没反应"(单台组更是原地不动)
     */
    private Integer[] getNextChannel(int direction, boolean crossGroup) {
        // 当前频道被刷新移除后双下标为 -1:以列表里高亮的可见组为起点恢复导航,
        // 否则默认"不跨组"时上下键会一直拿到 {-1,-1} 而失效
        int group = currentChannelGroupIndex;
        int channel = currentLiveChannelIndex;
        if (group < 0 || group >= liveChannelGroupList.size()) {
            group = liveChannelGroupAdapter.getSelectedGroupIndex();
            channel = -1;
        }
        // 跨组/加密跳过/回卷决策已抽到 LiveChannelNav(纯逻辑,防死循环兜底)
        int[] next = LiveChannelNav.next(direction,
                liveChannelGroupList.size(),
                g -> getLiveChannels(g).size(),
                g -> !liveChannelGroupList.get(g).getGroupPassword().isEmpty(),
                group,
                channel,
                crossGroup);
        return new Integer[]{next[0], next[1]};
    }

    /** 上下滑换台:跨组连续换,到列表尾回卷 */
    private void zapChannel(int direction) {
        if (!isCurrentLiveChannelValid()) return;
        lastZapDirection = direction;
        Integer[] idx = resolveZapTarget(direction);
        if (idx == null) return;   // 这个方向上全是刚判死的台:不做无谓起播(调用方会提示)
        playChannel(idx[0], idx[1], false);
    }

    /**
     * 找换台目标:顺带跳过"刚判定无可用线路"的台。
     *
     * @return 目标下标;返回 null 表示这个方向跳过若干坏台后仍无可试目标
     */
    private Integer[] resolveZapTarget(int direction) {
        Integer[] idx = getNextChannel(direction, true);
        for (int i = 0; i < MAX_AUTO_SKIP; i++) {
            if (idx == null || idx[0] == null || idx[0] < 0 || idx[1] == null || idx[1] < 0) return idx;
            if (!isChannelDead(idx[0], idx[1])) return idx;
            Integer[] next = getNextChannelFrom(idx[0], idx[1], direction, true);
            if (next[0] == null || next[0] < 0) return null;   // 跳到头都是坏台
            idx = next;
        }
        return null;
    }

    /** 从指定频道位置继续找上/下一个台({@link #getNextChannel(int, boolean)} 只能从"当前频道"起算) */
    private Integer[] getNextChannelFrom(int groupIndex, int channelIndex, int direction, boolean crossGroup) {
        int[] next = LiveChannelNav.next(direction,
                liveChannelGroupList.size(),
                g -> getLiveChannels(g).size(),
                g -> !liveChannelGroupList.get(g).getGroupPassword().isEmpty(),
                groupIndex,
                channelIndex,
                crossGroup);
        return new Integer[]{next[0], next[1]};
    }

    /**
     * 取某个方向上相邻台的台名,供手势拖动时预览。
     * 取下一个台的口径必须与 {@link #zapChannel(int)} 一致(强制跨组),
     * 否则预览显示的台和真正切过去的台会对不上。
     */
    private String getChannelNameByDirection(int direction) {
        try {
            Integer[] idx = getNextChannel(direction, true);
            if (idx == null || idx[0] == null || idx[1] == null) return null;
            ArrayList<LiveChannelItem> channels = getLiveChannels(idx[0]);
            if (idx[1] < 0 || idx[1] >= channels.size()) return null;
            return channels.get(idx[1]).getChannelName();
        } catch (Throwable th) {
            return null;
        }
    }

    /** 手势换台统一入口:页面级与控制器共用,350ms 防抖保证一次滑动只切一个台 */
    private void zapByGesture(int direction) {
        long now = SystemClock.elapsedRealtime();
        if (now - lastFlingSwitchAt < 350L) return;
        lastFlingSwitchAt = now;
        // 用户主动换台:自动跳过的连续计数重新开始
        autoSkipCount = 0;
        zapChannel(direction);
    }

    // ------------------------------------------------------------------
    // 换台预加载调度(抖音式:下一个台提前起流,换台直接扶正)
    // ------------------------------------------------------------------

    /** 安排一次"预加载下一个台";已有待执行任务时不重复排(频繁 buffer 回调不会把定时器一直往后推) */
    private void schedulePreloadNeighbor(long delayMs) {
        if (!canPreloadNeighbor() || !LiveConfig.preloadEnabled()) return;
        if (preloadScheduled) return;
        preloadScheduled = true;
        mHandler.postDelayed(mPreloadRun, Math.max(200L, delayMs));
    }

    private boolean canPreloadNeighbor() {
        if (!pageActive || mVideoView == null || isFinishing() || isDestroyed()) return false;
        int state = mVideoView.getCurrentPlayState();
        return (state == VideoView.STATE_PLAYING || state == VideoView.STATE_BUFFERED) && mVideoView.isPlaying();
    }

    private void preloadNeighborChannel() {
        if (!canPreloadNeighbor()) return;
        if (!LiveConfig.preloadEnabled()) {
            mVideoView.cancelPreload();
            return;
        }
        if (currentLiveChannelItem == null || liveChannelGroupList.isEmpty()) return;

        Integer[] next = getNextChannel(lastZapDirection, true);
        if (next[0] < 0 || next[0] >= liveChannelGroupList.size()) return;
        ArrayList<LiveChannelItem> channels = getLiveChannels(next[0]);
        if (next[1] < 0 || next[1] >= channels.size()) return;

        LiveChannelItem item = channels.get(next[1]);
        if (item == null || TextUtils.isEmpty(item.getUrl())) return;
        if (item.getUrl().equals(currentLiveChannelItem.getUrl())) return;

        // 这个台有自己的内核覆写:预加载实例的内核可能对不上,宁可冷启动也别扶正后黑屏
        if (LiveConfig.channelPlayerConfig(item.getChannelName()) != null) {
            mVideoView.cancelPreload();
            return;
        }
        // 同一个地址刚失败过:退避期内不再重试,免得死链每次起播都白建一次连
        if (preloadFailUrl != null && preloadFailUrl.equals(item.getUrl())
                && SystemClock.elapsedRealtime() - preloadFailAt < PRELOAD_FAIL_BACKOFF_MS) {
            return;
        }
        if (mVideoView.preload(item.getUrl(), null)) {
            AppLog.log("直播", "预加载下一个台[" + item.getChannelName() + "] 方向=" + lastZapDirection);
        }
    }

    @Override
    public void onPreloadSettingChanged() {
        if (mVideoView == null) return;
        if (!LiveConfig.preloadEnabled()) {
            mHandler.removeCallbacks(mPreloadRun);
            mHandler.removeCallbacks(mPreloadRefreshRun);
            preloadScheduled = false;
            mVideoView.cancelPreload();
            AppBubble.toast("已关闭换台预加载");
            return;
        }
        // 打开后立刻补一次,并恢复过期补刷
        schedulePreloadNeighbor(300L);
        mHandler.removeCallbacks(mPreloadRefreshRun);
        mHandler.postDelayed(mPreloadRefreshRun, PRELOAD_REFRESH_MS);
        AppBubble.toast("已开启换台预加载");
    }

    // ------------------------------------------------------------------
    // 在线升级
    // ------------------------------------------------------------------

    /** 起播稳定后做一次"启动自动检查更新"(每进程一次,受设置里"自动检查更新"开关控制) */
    private void scheduleUpdateCheck() {
        if (updateCheckScheduled) return;
        updateCheckScheduled = true;
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isFinishing() || isDestroyed()) return;
                com.github.tvbox.osc.update.UpdateCheck.autoCheckOnce(LiveActivity.this, null);
            }
        }, 5000L);
    }

    /** 设置面板里的"检查更新"动作项:结果走统一的更新说明弹窗 */
    @Override
    public void checkUpdate() {
        if (isFinishing() || isDestroyed()) return;
        AppBubble.toast("正在检查更新…");
        com.github.tvbox.osc.update.UpdateCheck.check(this, null);
    }

    /**
     * 视频区手势换台(页面级),不依赖播放器状态机。
     * <p>
     * 只靠控制器 onFling 不够:控制器的滑动链路要过播放状态判断(见 BaseController.onScroll),
     * 起播/缓冲阶段——也就是画面在转圈的时候——会直接 return,这个阶段恰恰是发现死台后最想滑走的时候。
     * 这里在 Activity 分发阶段就接管"视频区内 + 横向中间 1/3 + 竖向滑动",任何播放状态都能换台;
     * 左 1/3 亮度、右 1/3 音量仍交回控制器,互不干扰。
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // 起点必须落在视频区中段;下面的频道列表区域一律不接管,免得影响滚动
                zapGestureTracking = isInVideoZapZone(ev.getRawX(), ev.getRawY());
                zapDownRawX = ev.getRawX();
                zapDownRawY = ev.getRawY();
                break;
            case MotionEvent.ACTION_UP:
                if (zapGestureTracking) {
                    zapGestureTracking = false;
                    float dx = ev.getRawX() - zapDownRawX;
                    float dy = ev.getRawY() - zapDownRawY;
                    // 48dp 起判:比控制器的 100px 更贴合"短促一划"的手感,也不会把点按当成滑动
                    float minDistance = 48f * getResources().getDisplayMetrics().density;
                    if (Math.abs(dy) >= minDistance && Math.abs(dy) > Math.abs(dx)) {
                        zapByGesture(dy < 0 ? 1 : -1); // 上滑下一个台,下滑上一个台
                        return true;                   // 吃掉 UP:控制器收不到 UP,自然不会重复切一次
                    }
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                zapGestureTracking = false;
                break;
            default:
                break;
        }
        return super.dispatchTouchEvent(ev);
    }

    /** 按下的点是否在「视频区 + 横向中间 1/3」内 */
    private boolean isInVideoZapZone(float rawX, float rawY) {
        if (mVideoView == null || !mVideoView.isShown()) return false;
        int w = mVideoView.getWidth();
        int h = mVideoView.getHeight();
        if (w <= 0 || h <= 0) return false;
        mVideoView.getLocationOnScreen(zapVideoLocation);
        int left = zapVideoLocation[0];
        int top = zapVideoLocation[1];
        if (rawX < left || rawX > left + w || rawY < top || rawY > top + h) return false;
        return rawX >= left + w / 3f && rawX <= left + w * 2f / 3f;
    }

    private int getFirstNoPasswordChannelGroup() {
        return LiveChannelAuth.firstOpenGroup(liveChannelGroupList);
    }

    private boolean isCurrentLiveChannelValid() {
        if (currentLiveChannelItem == null) {
            AppBubble.toast("请先选择频道");
            return false;
        }
        return true;
    }

    public void showAllChannelDialog() {
        mAllChannelRightDialog = DialogCoordinator.right(this,
                new AllChannelsRightDialog(this, liveChannelGroupAdapter, liveChannelItemAdapter),
                0, true, false, null);
        mAllChannelRightDialog.show();
    }

    public void showCastDialog() {
        if (currentLiveChannelItem!=null){
            DialogCoordinator.centerMaxWidth(this, new CastListDialog(this,new CastVideo(currentLiveChannelItem.getChannelName(),currentLiveChannelItem.getUrl())), 360)
                    .show();
        }
    }

    public LivePlayerManager getLivePlayerManager(){
        return livePlayerManager;
    }

    @Override
    public int getLivePlayerScale() {
        return livePlayerManager.getLivePlayerScale();
    }

    @Override
    public int getLivePlayerType() {
        return livePlayerManager.getLivePlayerType();
    }

    @Override
    public LiveChannelItem getCurrentLiveChannelItem(){
        return currentLiveChannelItem;
    }

    /**
     * 切换某个线路播放
     * @param position
     */
    @Override
    public void switchingLine2Replay(int position){
        if (!pageActive || currentLiveChannelItem == null || position < 0
                || position >= currentLiveChannelItem.getSourceNum()) return;
        currentLiveChannelItem.setSourceIndex(position);
        playChannel(currentChannelGroupIndex, currentLiveChannelIndex,true);
        updateLineText();
    }

    /**
     * 切换缩放比例
     * @param position
     */
    @Override
    public void changeScale(int position){
        livePlayerManager.changeLivePlayerScale(mVideoView, position, currentLiveChannelItem.getChannelName());
    }

    /**
     * 更换播放解码
     * @param position
     */
    @Override
    public void changePlayer(int position){
        if (!pageActive || mVideoView == null || currentLiveChannelItem == null) return;
        cancelPlaybackRetry();
        playbackRetry.begin(false);
        mHandler.removeCallbacks(mPreloadRun);
        preloadScheduled = false;
        mVideoView.release();
        livePlayerManager.changeLivePlayerType(mVideoView, position, currentLiveChannelItem.getChannelName());
        mVideoView.setUrl(currentLiveChannelItem.getUrl());
        mVideoView.start();
    }

    /**
     * 设置弹窗
     * @param fullScreenStyle 全屏显示侧边弹窗
     */
    /** 偏好设置即时应用到两套控制条(时间/网速显示开关) */
    @Override
    public void refreshPreferenceUi() {
        if (mSideControlView != null) mSideControlView.refreshPreferenceUi();
        if (mNormalControlView != null) mNormalControlView.refreshPreferenceUi();
    }

    /** 线路入口:全屏用右侧抽屉(与设置抽屉同尺寸),非全屏用底部抽屉 */
    private void showLineSelectDialog(boolean fullScreenStyle) {
        if (!isCurrentLiveChannelValid()) {
            AppBubble.toast("当前频道未加载");
            return;
        }
        if (fullScreenStyle) {
            DialogCoordinator.right(this, new LiveLineSelectRightDialog(this, this), 300, true).show();
        } else {
            DialogCoordinator.bottom(this, new LiveLineSelectDialog(this, this), ScreenUtils.getScreenHeight() / 3).show();
        }
    }

    private void showSettingDialog(boolean fullScreenStyle) {
        if (!isCurrentLiveChannelValid()){
            AppBubble.toast("当前频道未加载");
            return;
        }
        if (fullScreenStyle){
            mSettingRightDialog = DialogCoordinator.right(this,
                    new LiveSettingRightDialog(this, this), 300, true);
            mSettingRightDialog.show();
        }else {
            mSettingBottomDialog = DialogCoordinator.bottom(this,
                    new LiveSettingDialog(this, this), ScreenUtils.getScreenHeight() / 2);
            mSettingBottomDialog.show();
        }

    }

}
