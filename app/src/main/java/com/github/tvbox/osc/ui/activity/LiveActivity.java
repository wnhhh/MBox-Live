package com.github.tvbox.osc.ui.activity;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Base64;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.blankj.utilcode.util.ConvertUtils;
import com.blankj.utilcode.util.ScreenUtils;
import com.github.tvbox.osc.util.AppBubble;
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
import com.github.tvbox.osc.util.live.LiveChannelGrouping;
import com.github.tvbox.osc.util.live.LiveSourceParser;
import com.github.tvbox.osc.util.live.TxtSubscribe;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.gyf.immersionbar.BarHide;
import com.gyf.immersionbar.ImmersionBar;
import com.lxj.xpopup.core.BasePopupView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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
    public static Context context;
    private VideoView mVideoView;
    private TextView tvChannelInfo;
    private LinearLayout tvLeftChannelListLayout;
    private RecyclerView mChannelGroupView;
    private RecyclerView mLiveChannelView;
    public LiveChannelGroupNewAdapter liveChannelGroupAdapter;
    public LiveChannelItemNewAdapter liveChannelItemAdapter;

    public static  int currentChannelGroupIndex = 0;
    private Handler mHandler = new Handler();

    private List<LiveChannelGroup> liveChannelGroupList = new ArrayList<>();
    private int currentLiveChannelIndex = -1;
    private int currentLiveChangeSourceTimes = 0;
    private LiveChannelItem currentLiveChannelItem = null;
    private LivePlayerManager livePlayerManager = new LivePlayerManager();
    private ArrayList<Integer> channelGroupPasswordConfirmed = new ArrayList<>();

//EPG   by 龍
    private static LiveChannelItem  channel_Name = null;
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
        context = this;
        epgStringAddress = LiveConfig.epgUrl();
        if(epgStringAddress == null || epgStringAddress.length()<5)
            epgStringAddress = "http://epg.51zmt.top:8000/api/diyp/";

        setLoadSir(findViewById(R.id.live_root));
        mVideoView = findViewById(R.id.mVideoView);

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

    private void saveLivesCache(String url, String json) {
        if (TextUtils.isEmpty(json)) return;
        try {
            FileOutputStream fos = new FileOutputStream(liveCacheFile(url));
            try {
                fos.write(json.getBytes("UTF-8"));
            } finally {
                fos.close();
            }
        } catch (Throwable th) {
            AppLog.log("直播", "写频道缓存失败: " + th.getMessage());
        }
    }

    /** 读缓存并灌进 provider;缓存不存在/损坏都返回 false(当作没命中,不影响正常拉取) */
    private boolean loadLivesFromCache(String url) {
        try {
            File f = liveCacheFile(url);
            long len = f.exists() ? f.length() : 0;
            if (len <= 0 || len > 8L * 1024 * 1024) return false;   // 空文件/异常大文件都不认
            byte[] buf = new byte[(int) len];
            FileInputStream fis = new FileInputStream(f);
            try {
                int off = 0;
                while (off < buf.length) {
                    int n = fis.read(buf, off, buf.length - off);
                    if (n <= 0) break;
                    off += n;
                }
            } finally {
                fis.close();
            }
            JsonArray arr = JsonParser.parseString(new String(buf, "UTF-8")).getAsJsonArray();
            if (arr == null || arr.size() == 0) return false;
            // 缓存里存的是原始分组,这里同样过一遍分组规则(规则改了也能立刻生效)
            com.github.tvbox.osc.spiderapi.LiveChannelConfigProviders.get().loadLives(
                    LiveChannelGrouping.regroup(arr));
            List<LiveChannelGroup> list = com.github.tvbox.osc.spiderapi.LiveChannelConfigProviders.get().getChannelGroupList();
            if (list.isEmpty()) return false;
            liveChannelGroupList.clear();
            liveChannelGroupList.addAll(list);
            return true;
        } catch (Throwable th) {
            AppLog.log("直播", "读频道缓存失败: " + th.getMessage());
            return false;
        }
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
        mHandler.postDelayed(mAutoReloadRun, Math.max(2000L, delayMs));
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
        if (mVideoView != null) {
            mVideoView.resume();
        }
        // 回到前台:频道表放旧了就静默更新一次,否则把定时器按剩余时间重新挂上
        if (lastChannelLoadedAt > 0) {
            long age = SystemClock.elapsedRealtime() - lastChannelLoadedAt;
            if (age >= STALE_RELOAD_MS) {
                scheduleNextAutoReload(2000L);
            } else {
                scheduleNextAutoReload(AUTO_RELOAD_INTERVAL_MS - age);
            }
        }
    }


    @Override
    protected void onPause() {
        super.onPause();
        // 后台不刷新:既省电省流量,也避免回来时列表和"正在播的台"对不上
        mHandler.removeCallbacks(mAutoReloadRun);
        if (mVideoView != null) {
            mVideoView.pause();
        }
    }

    @Override
    protected void onDestroy() {
        mHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
        if (mVideoView != null) {
            mVideoView.release();
            mVideoView = null;
        }
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
        if (changeSource) {
            // 只切线路不换台:当前频道可能还没选好(切组瞬间/刷新后),别直接 getSourceNum() 炸空指针
            if (currentLiveChannelItem == null) return false;
            if (currentLiveChannelItem.getSourceNum() == 1) {
                return true;
            }
        } else {
            // 组/台下标必须都合法:刷新频道表后总量可能变少,旧下标直接 get() 会越界
            if (channelGroupIndex < 0 || channelGroupIndex >= liveChannelGroupList.size()) return false;
            ArrayList<LiveChannelItem> channels = getLiveChannels(channelGroupIndex);
            if (liveChannelIndex < 0 || liveChannelIndex >= channels.size()) return false;
            if (channelGroupIndex == currentChannelGroupIndex && liveChannelIndex == currentLiveChannelIndex) {
                return true;
            }
        }
        mVideoView.release();
        if (!changeSource) {
            currentChannelGroupIndex = channelGroupIndex;
            currentLiveChannelIndex = liveChannelIndex;
            currentLiveChannelItem = getLiveChannels(currentChannelGroupIndex).get(currentLiveChannelIndex);
            LiveConfig.setLastChannel(currentLiveChannelItem.getChannelName());
            livePlayerManager.getLiveChannelPlayer(mVideoView, currentLiveChannelItem.getChannelName());
        }

        channel_Name = currentLiveChannelItem;
        isBack = false;
        if(currentLiveChannelItem.getUrl().indexOf("PLTV/8888") !=-1){
            currentLiveChannelItem.setinclude_back(true);
        }else {
            currentLiveChannelItem.setinclude_back(false);
        }
        showBottomEpg();

        AppLog.log("直播", "播放频道[" + currentLiveChannelItem.getChannelName() + "] 源[" + (currentLiveChannelItem.getSourceIndex() + 1) + "/" + currentLiveChannelItem.getSourceNum() + "] " + currentLiveChannelItem.getUrl());
        com.github.tvbox.osc.log.LogStore.log(com.github.tvbox.osc.log.Category.OTHER,
                "直播: 播放频道[" + currentLiveChannelItem.getChannelName() + "] 源["
                        + (currentLiveChannelItem.getSourceIndex() + 1) + "/"
                        + currentLiveChannelItem.getSourceNum() + "]");

        mVideoView.setUrl(currentLiveChannelItem.getUrl());
       // showChannelInfo();
        mVideoView.start();
        return true;
    }

    private void playNext() {
        if (!isCurrentLiveChannelValid()) return;
        Integer[] groupChannelIndex = getNextChannel(1);
        playChannel(groupChannelIndex[0], groupChannelIndex[1], false);
    }

    private void playPrevious() {
        if (!isCurrentLiveChannelValid()) return;
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
                switch (playState) {
                    case VideoView.STATE_IDLE:
                    case VideoView.STATE_PAUSED:
                        break;
                    case VideoView.STATE_PREPARED:
                    case VideoView.STATE_BUFFERED:
                    case VideoView.STATE_PLAYING:
                        currentLiveChangeSourceTimes = 0;
                        mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun);
                        break;
                    case VideoView.STATE_ERROR:
                    case VideoView.STATE_PLAYBACK_COMPLETED:
                        mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun);
                        mHandler.postDelayed(mConnectTimeoutChangeSourceRun, 2000);
                        break;
                    case VideoView.STATE_PREPARING:
                    case VideoView.STATE_BUFFERING:
                        mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun);
                        mHandler.postDelayed(mConnectTimeoutChangeSourceRun, (LiveConfig.connectTimeout() + 1) * 5000);
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
                // 上下滑换台:350ms 防抖,免得一次滑动被识别成两次、连着跳过好几个台
                long now = SystemClock.elapsedRealtime();
                if (now - lastFlingSwitchAt < 350L) return;
                lastFlingSwitchAt = now;
                zapChannel(direction);
            }
        });
        controller.setCanChangePosition(false);
        controller.setEnableInNormal(true);
        controller.setGestureEnabled(true);
        controller.setDoubleTapTogglePlayEnabled(false);
        mVideoView.setVideoController(controller);
        mVideoView.setProgressManager(null);
    }

    @NonNull
    private Runnable mConnectTimeoutChangeSourceRun = new Runnable() {
        @Override
        public void run() {
            // 这是个延时任务:2 秒后再跑时当前频道可能已经被清掉(刷新/退出),
            // 不判空就会在 currentLiveChannelItem.getSourceNum() 上炸 NPE
            if (currentLiveChannelItem == null || mVideoView == null) return;
            currentLiveChangeSourceTimes++;
            if (currentLiveChannelItem.getSourceNum() == currentLiveChangeSourceTimes) {
                // 当前频道所有线路都失败:停止播放,不自动切下一个频道
                currentLiveChangeSourceTimes = 0;
                try {
                    mVideoView.release();
                } catch (Throwable ignored) {
                }
                AppBubble.toastLong("当前频道无可用线路,请手动切换频道");
            } else {
                playNextSource();
            }
        }
    };

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
            // 先用上次成功拉到的频道表上屏(冷启动不用等网络),随后静默换成最新的
            if (loadLivesFromCache(url)) {
                AppLog.log("直播", "冷启动命中频道缓存,先上屏再后台刷新");
                showSuccess();
                initLiveState();
                reloadFailCount = 0;
                scheduleNextAutoReload(1500L);
                return;
            }
            loadLivesFromUrl(url, null, true);
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
        if (channelLoading) return;
        channelLoading = true;
        // 只有"还没有任何频道在播"(首屏/上次失败)才铺满屏加载态;已经在看电视时刷新不顶掉画面
        if (resumePlayback && currentLiveChannelItem == null) showLoading();
        AppLog.log("直播", "加载直播源: " + url);
        HttpClient.get(url, null, new HCallBack() {

            @Override
            public void onSuccess(String content) {
                // 解析 900+ 个台 + 重新分组放后台线程:HttpClient 的回调是投递到主线程的,
                // 直接在这里解析会把主线程占住几百毫秒 —— 换台正好撞上就是"卡死"
                LiveSourceParser.parseAsync(content, mHandler, new LiveSourceParser.Callback() {
                    @Override
                    public void onParsed(LiveSourceParser.Result result) {
                        if (result == null) {
                            channelLoading = false;
                            AppLog.log("直播", "解析失败: 内容不是可用的频道表");
                            com.github.tvbox.osc.log.LogStore.log(com.github.tvbox.osc.log.Category.OTHER,
                                    "直播: 直播源解析失败");
                            useFallbackOrFail(fallback, "直播源解析失败,请检查直播源地址");
                            return;
                        }
                        try {
                            com.github.tvbox.osc.spiderapi.LiveChannelConfigProviders.get().loadLives(result.grouped);
                            List<LiveChannelGroup> list = com.github.tvbox.osc.spiderapi.LiveChannelConfigProviders.get().getChannelGroupList();
                            if (list.isEmpty()) {
                                channelLoading = false;
                                useFallbackOrFail(fallback, "频道列表为空");
                                return;
                            }
                            liveChannelGroupList.clear();
                            liveChannelGroupList.addAll(list);

                            channelLoading = false;
                            lastChannelLoadedAt = SystemClock.elapsedRealtime();
                            reloadFailCount = 0;
                            // 落盘:下次冷启动不用等网络,直接用这份先上屏
                            saveLivesCache(url, result.raw.toString());
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
                                refreshChannelListOnly();
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
                channelLoading = false;
                AppLog.log("直播", "加载失败: " + (e == null ? "null" : e.getMessage()));
                com.github.tvbox.osc.log.LogStore.log(com.github.tvbox.osc.log.Category.OTHER,
                        "直播: 直播源加载失败: " + (e == null ? "null" : e.getMessage()));
                useFallbackOrFail(fallback, "直播源加载失败,请检查网络或直播源");
            }
        });
    }

    /** 静默刷新:只把新频道表换进适配器,不碰播放器(避免看着看着被强制重开一次流) */
    private void refreshChannelListOnly() {
        // 列表换了以后,按频道名把"当前在看的是第几个"重新对上,否则高亮和上下换台会错位
        if (currentLiveChannelItem != null && currentChannelGroupIndex >= 0
                && currentChannelGroupIndex < liveChannelGroupList.size()) {
            for (LiveChannelItem item : liveChannelGroupList.get(currentChannelGroupIndex).getLiveChannels()) {
                if (currentLiveChannelItem.getChannelName().equals(item.getChannelName())) {
                    currentLiveChannelItem = item;
                    currentLiveChannelIndex = item.getChannelIndex();
                    channel_Name = item;
                    break;
                }
            }
        }
        liveChannelGroupAdapter.setNewData(liveChannelGroupList);
        if (currentChannelGroupIndex >= 0 && currentChannelGroupIndex < liveChannelGroupList.size()) {
            liveChannelItemAdapter.setNewData(getLiveChannels(currentChannelGroupIndex));
            liveChannelItemAdapter.setSelectedChannelIndex(currentLiveChannelIndex);
        }
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

        LivePasswordDialog dialog = new LivePasswordDialog(this);
        dialog.setOnListener(new LivePasswordDialog.OnListener() {
            @Override
            public void onChange(String password) {
                if (password.equals(liveChannelGroupList.get(groupIndex).getGroupPassword())) {
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
        // 跨组/加密跳过/回卷决策已抽到 LiveChannelNav(纯逻辑,防死循环兜底)
        int[] next = LiveChannelNav.next(direction,
                liveChannelGroupList.size(),
                group -> getLiveChannels(group).size(),
                group -> !liveChannelGroupList.get(group).getGroupPassword().isEmpty(),
                currentChannelGroupIndex,
                currentLiveChannelIndex,
                crossGroup);
        return new Integer[]{next[0], next[1]};
    }

    /** 上下滑换台:跨组连续换,到列表尾回卷 */
    private void zapChannel(int direction) {
        if (!isCurrentLiveChannelValid()) return;
        Integer[] groupChannelIndex = getNextChannel(direction, true);
        playChannel(groupChannelIndex[0], groupChannelIndex[1], false);
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
