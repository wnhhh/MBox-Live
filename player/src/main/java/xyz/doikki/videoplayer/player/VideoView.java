package xyz.doikki.videoplayer.player;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.github.tvbox.osc.player.R;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import xyz.doikki.videoplayer.controller.BaseVideoController;
import xyz.doikki.videoplayer.controller.MediaPlayerControl;
import xyz.doikki.videoplayer.render.IRenderView;
import xyz.doikki.videoplayer.render.RenderViewFactory;
import xyz.doikki.videoplayer.util.L;
import xyz.doikki.videoplayer.util.PlayerUtils;

/**
 * 播放器
 * Created by Doikki on 2017/4/7.
 */

public class VideoView<P extends AbstractPlayer> extends FrameLayout
        implements MediaPlayerControl, AbstractPlayer.PlayerEventListener {

    protected P mMediaPlayer;//播放器
    protected PlayerFactory<P> mPlayerFactory;//工厂类，用于实例化播放核心
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private int mPendingReleases;
    private boolean mStartPending;
    private boolean mResumePending;
    private boolean mPauseRequested;
    private boolean mSourceChanged;
    private boolean mPreparing;
    private long mPlaybackGeneration;
    private long mPreloadGeneration;
    @Nullable
    protected BaseVideoController mVideoController;//控制器

    /**
     * 真正承载播放器视图的容器
     */
    protected FrameLayout mPlayerContainer;

    protected IRenderView mRenderView;
    protected RenderViewFactory mRenderViewFactory;

    public static final int SCREEN_SCALE_DEFAULT = 0;
    public static final int SCREEN_SCALE_16_9 = 1;
    public static final int SCREEN_SCALE_4_3 = 2;
    public static final int SCREEN_SCALE_MATCH_PARENT = 3;
    public static final int SCREEN_SCALE_ORIGINAL = 4;
    public static final int SCREEN_SCALE_CENTER_CROP = 5;
    protected int mCurrentScreenScaleType;

    protected int[] mVideoSize = {0, 0};

    protected boolean mIsMute;//是否静音

    //--------- data sources ---------//
    protected String mUrl;//当前播放视频的地址
    protected String mProgressKey = null;
    protected Map<String, String> mHeaders;//当前视频地址的请求头
    protected AssetFileDescriptor mAssetFileDescriptor;//assets文件

    protected long mCurrentPosition;//当前正在播放视频的位置

    //播放器的各种状态
    public static final int STATE_ERROR = -1;
    public static final int STATE_IDLE = 0;
    public static final int STATE_PREPARING = 1;
    public static final int STATE_PREPARED = 2;
    public static final int STATE_PLAYING = 3;
    public static final int STATE_PAUSED = 4;
    public static final int STATE_PLAYBACK_COMPLETED = 5;
    public static final int STATE_BUFFERING = 6;
    public static final int STATE_BUFFERED = 7;
    public static final int STATE_START_ABORT = 8;//开始播放中止
    protected int mCurrentPlayState = STATE_IDLE;//当前播放器的状态

    public static final int PLAYER_NORMAL = 10;        // 普通播放器
    public static final int PLAYER_FULL_SCREEN = 11;   // 全屏播放器
    public static final int PLAYER_TINY_SCREEN = 12;   // 小屏播放器
    protected int mCurrentPlayerState = PLAYER_NORMAL;

    protected boolean mIsFullScreen;//是否处于全屏状态

    protected boolean mIsTinyScreen;//是否处于小屏状态
    protected int[] mTinyScreenSize = {0, 0};

    /**
     * 监听系统中音频焦点改变，见{@link #setEnableAudioFocus(boolean)}
     */
    protected boolean mEnableAudioFocus;
    @Nullable
    protected AudioFocusHelper mAudioFocusHelper;

    /**
     * OnStateChangeListener集合，保存了所有开发者设置的监听器
     */
    protected List<OnStateChangeListener> mOnStateChangeListeners;

    /**
     * 进度管理器，设置之后播放器会记录播放进度，以便下次播放恢复进度
     */
    @Nullable
    protected ProgressManager mProgressManager;

    /**
     * 循环播放
     */
    protected boolean mIsLooping;

    // ------------------------------------------------------------------
    // 换台预加载（抖音式秒切）：另起一个静音实例，把相邻频道的「建连 + 解封装 + 首包」提前做完，
    // 换台时直接扶正（复用已有 Surface，不重建播放器和渲染视图）。
    // ------------------------------------------------------------------

    /** 预加载实例（无画面、静音），扶正后成为主播放器 */
    protected P mPreloadPlayer;
    /** 预加载地址 */
    protected String mPreloadUrl;
    /** 预加载地址的请求头 */
    protected Map<String, String> mPreloadHeaders;
    /** 预加载是否已 prepare 完成 */
    protected boolean mPreloadPrepared;
    /** 预加载开始时刻（SystemClock.elapsedRealtime） */
    protected long mPreloadStartAt;
    /** 预加载实例解析到的视频宽高（扶正后直接套用，省一次 onVideoSizeChanged） */
    protected final int[] mPreloadVideoSize = {0, 0};
    /** 预加载有效期：超过这个时长认为连接/缓冲已不可靠，换台时不再扶正（直播流尤其需要） */
    public static final long PRELOAD_TTL_MS = 90 * 1000L;

    @Nullable
    protected OnPreloadListener mOnPreloadListener;

    /** 预加载回调：上层用于打点、失败退避 */
    public interface OnPreloadListener {

        void onPreloadReady(String url);

        void onPreloadFailed(String url);
    }

    /**
     * {@link #mPlayerContainer}背景色，默认黑色
     */
    private int mPlayerBackgroundColor;

    public VideoView(@NonNull Context context) {
        this(context, null);
    }

    public VideoView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public VideoView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        //读取全局配置
        VideoViewConfig config = VideoViewManager.getConfig();
        mEnableAudioFocus = config.mEnableAudioFocus;
        mProgressManager = config.mProgressManager;
        mPlayerFactory = config.mPlayerFactory;
        mCurrentScreenScaleType = config.mScreenScaleType;
        mRenderViewFactory = config.mRenderViewFactory;

        //读取xml中的配置，并综合全局配置
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.VideoView);
        mEnableAudioFocus = a.getBoolean(R.styleable.VideoView_enableAudioFocus, mEnableAudioFocus);
        mIsLooping = a.getBoolean(R.styleable.VideoView_looping, false);
        mCurrentScreenScaleType = a.getInt(R.styleable.VideoView_screenScaleType, mCurrentScreenScaleType);
        mPlayerBackgroundColor = a.getColor(R.styleable.VideoView_playerBackgroundColor, Color.BLACK);
        a.recycle();

        initView();
    }

    /**
     * 初始化播放器视图
     */
    protected void initView() {
        mPlayerContainer = new FrameLayout(getContext());
        mPlayerContainer.setBackgroundColor(mPlayerBackgroundColor);
        LayoutParams params = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        this.addView(mPlayerContainer, params);
    }

    /**
     * 设置{@link #mPlayerContainer}的背景色
     */
    public void setPlayerBackgroundColor(int color) {
        mPlayerContainer.setBackgroundColor(color);
    }

    /**
     * 开始播放，注意：调用此方法后必须调用{@link #release()}释放播放器，否则会导致内存泄漏
     */
    @Override
    public void start() {
        mPauseRequested = false;
        mResumePending = false;
        if (mMediaPlayer == null || isInIdleState() || isInStartAbortState()) {
            startPlay();
        } else if (mSourceChanged) {
            startPrepare(true);
        } else if (mPreparing) {
            mMediaPlayer.setPlayWhenPrepared(true);
            setPlayState(STATE_PREPARING);
        } else if (isInPlaybackState()) {
            startInPlaybackState();
        }
    }

    /**
     * 第一次播放
     *
     * @return 是否成功开始播放
     */
    protected boolean startPlay() {
        //如果要显示移动网络提示则不继续播放
        if (showNetWarning()) {
            //中止播放
            setPlayState(STATE_START_ABORT);
            return false;
        }
        //监听音频焦点改变
        if (mEnableAudioFocus && mAudioFocusHelper == null) {
            mAudioFocusHelper = new AudioFocusHelper(this);
        }
        //读取播放进度
        if (mProgressManager != null) {
            mCurrentPosition = mProgressManager.getSavedProgress(mProgressKey == null ? mUrl : mProgressKey);
        }
        mStartPending = true;
        startPendingPlayer();
        return true;
    }

    private void startPendingPlayer() {
        if (!mStartPending || mPauseRequested || mPendingReleases != 0) return;
        mStartPending = false;
        try {
            initPlayer();
            if (mRenderView == null) addDisplay();
            else mRenderView.attachToPlayer(mMediaPlayer);
            startPrepare(false);
        } catch (RuntimeException e) {
            L.e("start player 失败: " + e.getMessage());
            releaseCurrentPlayer();
            onError();
        }
    }

    private void invalidatePlayerEvents() {
        ++mPlaybackGeneration;
        if (mMediaPlayer != null) mMediaPlayer.setPlayerEventListener(null);
    }

    private void releaseCurrentPlayer() {
        invalidatePlayerEvents();
        P player = mMediaPlayer;
        mMediaPlayer = null;
        mPreparing = false;
        if (player != null) releasePlayer(player);
    }

    private void releasePlayer(P player) {
        player.setPlayerEventListener(null);
        ++mPendingReleases;
        player.release(() -> mMainHandler.post(() -> {
            --mPendingReleases;
            startPendingPlayer();
        }));
    }

    /**
     * 是否显示移动网络提示，可在Controller中配置
     */
    protected boolean showNetWarning() {
        //播放本地数据源时不检测网络
        if (isLocalDataSource()) return false;
        return mVideoController != null && mVideoController.showNetWarning();
    }

    /**
     * 判断是否为本地数据源，包括 本地文件、Asset、raw
     */
    protected boolean isLocalDataSource() {
        if (mAssetFileDescriptor != null) {
            return true;
        } else if (!TextUtils.isEmpty(mUrl)) {
            Uri uri = Uri.parse(mUrl);
            return ContentResolver.SCHEME_ANDROID_RESOURCE.equals(uri.getScheme())
                    || ContentResolver.SCHEME_FILE.equals(uri.getScheme())
                    || "rawresource".equals(uri.getScheme());
        }
        return false;
    }

    /**
     * 初始化播放器
     */
    protected void initPlayer() {
        if (mPendingReleases != 0) throw new IllegalStateException("Player release pending");
        mMediaPlayer = mPlayerFactory.createPlayer(getContext());
        mMediaPlayer.setPlayerEventListener(new MainPlayerEvent(mMediaPlayer, ++mPlaybackGeneration));
        setInitOptions();
        mMediaPlayer.initPlayer();
        setOptions();
    }

    /**
     * 初始化之前的配置项
     */
    protected void setInitOptions() {
    }

    /**
     * 初始化之后的配置项
     */
    protected void setOptions() {
        mMediaPlayer.setLooping(mIsLooping);
        float volume = mIsMute ? 0.0f : 1.0f;
        mMediaPlayer.setVolume(volume, volume);
    }

    /**
     * 初始化视频渲染View
     */
    protected void addDisplay() {
        if (mRenderView != null) {
            if (mMediaPlayer != null) {
                mMediaPlayer.setSurface(null);
                mMediaPlayer.setDisplay(null);
            }
            mPlayerContainer.removeView(mRenderView.getView());
            mRenderView.release();
        }
        mRenderView = mRenderViewFactory.createRenderView(getContext());
        mRenderView.attachToPlayer(mMediaPlayer);
        LayoutParams params = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER);
        mPlayerContainer.addView(mRenderView.getView(), 0, params);
    }

    /**
     * 开始准备播放（直接播放）
     */
    protected void startPrepare(boolean reset) {
        if (mMediaPlayer == null) {
            startPlay();
            return;
        }
        invalidatePlayerEvents();
        if (reset && !mMediaPlayer.canReuseForSwitch()) {
            releaseCurrentPlayer();
            mStartPending = true;
            startPendingPlayer();
            return;
        }
        if (reset) {
            mMediaPlayer.reset();
            //重新设置option，media player reset之后，option会失效
            setOptions();
            if (mRenderView != null) mRenderView.attachToPlayer(mMediaPlayer);
        }
        P player = mMediaPlayer;
        long generation = ++mPlaybackGeneration;
        player.setPlayerEventListener(new MainPlayerEvent(player, generation));
        player.setPreloadOnly(false);
        player.setPlayWhenPrepared(!mPauseRequested);
        mSourceChanged = false;
        if (prepareDataSource()) {
            mPreparing = true;
            setPlayState(STATE_PREPARING);
            if (player != mMediaPlayer || generation != mPlaybackGeneration) return;
            setPlayerState(isFullScreen() ? PLAYER_FULL_SCREEN : isTinyScreen() ? PLAYER_TINY_SCREEN : PLAYER_NORMAL);
            if (player != mMediaPlayer || generation != mPlaybackGeneration) return;
            player.prepareAsync();
        }
    }

    /**
     * 设置播放数据
     *
     * @return 播放数据是否设置成功
     */
    protected boolean prepareDataSource() {
        if (mAssetFileDescriptor != null) {
            mMediaPlayer.setDataSource(mAssetFileDescriptor);
            return true;
        } else if (!TextUtils.isEmpty(mUrl)) {
            mMediaPlayer.setDataSource(mUrl, mHeaders);
            return true;
        }
        return false;
    }

    /**
     * 播放状态下开始播放
     */
    protected void startInPlaybackState() {
        mMediaPlayer.start();
        setPlayState(STATE_PLAYING);
        if (mAudioFocusHelper != null && !isMute()) {
            mAudioFocusHelper.requestFocus();
        }
        mPlayerContainer.setKeepScreenOn(true);
    }

    /**
     * 暂停播放
     */
    @Override
    public void pause() {
        mPauseRequested = true;
        mResumePending |= mStartPending;
        mStartPending = false;
        cancelPreload();
        if (mMediaPlayer != null && (mPreparing || isInPlaybackState())) {
            mMediaPlayer.setPlayWhenPrepared(false);
            mMediaPlayer.pause();
            setPlayState(STATE_PAUSED);
        }
        if (mAudioFocusHelper != null) mAudioFocusHelper.abandonFocus();
        mPlayerContainer.setKeepScreenOn(false);
    }

    /**
     * 继续播放
     */
    public void resume() {
        mPauseRequested = false;
        if (mResumePending) {
            mResumePending = false;
            startPlay();
        } else if (mPreparing && mMediaPlayer != null) {
            mMediaPlayer.setPlayWhenPrepared(true);
            setPlayState(STATE_PREPARING);
        } else if (isInPlaybackState() && !mMediaPlayer.isPlaying()) {
            startInPlaybackState();
        }
    }

    /**
     * 释放播放器
     */
    public void release() {
        mStartPending = false;
        mResumePending = false;
        mPauseRequested = true;
        cancelPreload();
        releaseCurrentPlayer();
        if (mRenderView != null) {
            mPlayerContainer.removeView(mRenderView.getView());
            mRenderView.release();
            mRenderView = null;
        }
        if (mAssetFileDescriptor != null) {
            try {
                mAssetFileDescriptor.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            mAssetFileDescriptor = null;
        }
        if (mAudioFocusHelper != null) {
            mAudioFocusHelper.abandonFocus();
            mAudioFocusHelper = null;
        }
        mPlayerContainer.setKeepScreenOn(false);
        saveProgress();
        mCurrentPosition = 0;
        setPlayState(STATE_IDLE);
    }

    // ------------------------------------------------------------------
    // 换台预加载
    // ------------------------------------------------------------------

    public void setOnPreloadListener(@Nullable OnPreloadListener listener) {
        this.mOnPreloadListener = listener;
    }

    /**
     * 预加载一个地址：另起一个播放器实例，静音、不挂画面，只把「建连 + 解封装 + 首包」做完。
     * <p>
     * 内核显式禁止 prepare 后自动起播；扶正后再 start()，不把后台播放当作预加载。
     * <p>
     * 幂等：同一地址已在预加载且未过期时直接返回 true，可放心反复调用。
     *
     * @return true 表示预加载已发起（或已在预加载）；false 表示不适用于该地址
     */
    public synchronized boolean preload(String url, Map<String, String> headers) {
        if (TextUtils.isEmpty(url) || mPauseRequested || mStartPending || mPendingReleases != 0) return false;
        if (mMediaPlayer != null && url.equals(mUrl) && isInPlaybackState()) return false;
        if (mPreloadPlayer != null && url.equals(mPreloadUrl) && getPreloadAgeMs() < PRELOAD_TTL_MS) return true;

        cancelPreload();
        if (mPendingReleases != 0) return false;
        try {
            P player = mPlayerFactory.createPlayer(getContext());
            mPreloadPlayer = player;
            mPreloadUrl = url;
            mPreloadHeaders = headers;
            mPreloadPrepared = false;
            mPreloadStartAt = SystemClock.elapsedRealtime();
            mPreloadVideoSize[0] = 0;
            mPreloadVideoSize[1] = 0;
            player.setPlayerEventListener(new PreloadPlayerEvent(player, ++mPreloadGeneration, url));
            player.setPreloadOnly(true);
            player.setPlayWhenPrepared(false);
            player.initPlayer();
            player.setVolume(0f, 0f);
            player.setDataSource(url, headers);
            player.prepareAsync();
            return true;
        } catch (Throwable th) {
            L.e("preload 失败: " + th.getMessage());
            cancelPreload();
            return false;
        }
    }

    /** 取消预加载并释放实例（页面退到后台/退出时必须调，否则会留一路后台连接） */
    public synchronized void cancelPreload() {
        ++mPreloadGeneration;
        P player = mPreloadPlayer;
        mPreloadPlayer = null;
        mPreloadUrl = null;
        mPreloadHeaders = null;
        mPreloadPrepared = false;
        mPreloadStartAt = 0L;
        mPreloadVideoSize[0] = 0;
        mPreloadVideoSize[1] = 0;
        if (player != null) releasePlayer(player);
    }

    /** 预加载是否可用于扶正（已 prepare 完成且未过期） */
    public synchronized boolean isPreloadUsable() {
        return mPreloadPlayer != null && mPreloadPrepared && getPreloadAgeMs() < PRELOAD_TTL_MS;
    }

    /** 当前预加载的地址（没有则为 null） */
    @Nullable
    public synchronized String getPreloadedUrl() {
        return mPreloadUrl;
    }

    /** 预加载已等待时长（毫秒）；没有预加载时返回 Long.MAX_VALUE */
    public synchronized long getPreloadAgeMs() {
        return mPreloadStartAt <= 0 ? Long.MAX_VALUE : SystemClock.elapsedRealtime() - mPreloadStartAt;
    }

    /**
     * 把预加载实例扶正为当前播放器：不重建播放器、不重建渲染视图，直接换内核实例并复用已有 Surface。
     * 调用成功后仍需调 {@link #start()} 才会出画面。
     *
     * @return true 表示已扶正；false 表示没有可用的预加载（调用方应走普通起播）
     */
    public synchronized boolean promotePreloaded() {
        if (!isPreloadUsable() || mPauseRequested) return false;
        // 必须先解绑旧核，不能在 native release 尚未结束时共享 Surface。
        try {
            if (mMediaPlayer != null) {
                mMediaPlayer.setSurface(null);
                mMediaPlayer.setDisplay(null);
            }
        } catch (RuntimeException e) {
            L.e("promote detach 失败: " + e.getMessage());
            return false;
        }
        mStartPending = false;
        mResumePending = false;
        releaseCurrentPlayer();
        ++mPreloadGeneration;
        mMediaPlayer = mPreloadPlayer;
        mUrl = mPreloadUrl;
        mHeaders = mPreloadHeaders;
        mAssetFileDescriptor = null;
        mPreloadPlayer = null;
        mPreloadUrl = null;
        mPreloadHeaders = null;
        mPreloadPrepared = false;
        mPreloadStartAt = 0L;
        mSourceChanged = false;
        mCurrentPosition = 0;
        mMediaPlayer.setPlayerEventListener(new MainPlayerEvent(mMediaPlayer, ++mPlaybackGeneration));
        try {
            mMediaPlayer.setPreloadOnly(false);
            if (mRenderView == null) addDisplay();
            else mRenderView.attachToPlayer(mMediaPlayer);
            setOptions();
            if (mPreloadVideoSize[0] > 0 && mPreloadVideoSize[1] > 0) {
                onVideoSizeChanged(mPreloadVideoSize[0], mPreloadVideoSize[1]);
            }
            mPreloadVideoSize[0] = 0;
            mPreloadVideoSize[1] = 0;
            if (mEnableAudioFocus && mAudioFocusHelper == null) {
                mAudioFocusHelper = new AudioFocusHelper(this);
            }
            setPlayState(STATE_PREPARED);
            return true;
        } catch (RuntimeException e) {
            L.e("promote 失败: " + e.getMessage());
            releaseCurrentPlayer();
            onError();
            return false;
        }
    }

    /** 地址命中预加载时扶正，不命中返回 false（调用方走普通起播） */
    public synchronized boolean promotePreloadedIfMatches(String url) {
        if (TextUtils.isEmpty(url) || !url.equals(mPreloadUrl)) return false;
        return promotePreloaded();
    }

    /**
     * 换源：可复用内核走 reset，其余等待异步释放完成后重建；等待期间只保留最新地址。
     * @return false 表示没有播放请求或换源失败，调用方可回退到 release/setUrl/start。
     */
    public boolean switchUrl(String url, Map<String, String> headers) {
        if (TextUtils.isEmpty(url) || (mMediaPlayer == null && !mStartPending && !mResumePending)) return false;
        try {
            setUrl(url, headers);
            mCurrentPosition = 0;
            mPauseRequested = false;
            mResumePending = false;
            cancelPreload();
            if (mMediaPlayer != null && mRenderView == null) addDisplay();
            startPrepare(true);
            return true;
        } catch (RuntimeException e) {
            L.e("switchUrl 失败: " + e.getMessage());
            releaseCurrentPlayer();
            onError();
            return false;
        }
    }

    private final class MainPlayerEvent implements AbstractPlayer.PlayerEventListener {
        private final P mEventPlayer;
        private final long mGeneration;

        MainPlayerEvent(P player, long generation) {
            mEventPlayer = player;
            mGeneration = generation;
        }

        private void dispatch(Runnable event) {
            mMainHandler.post(() -> {
                if (mEventPlayer == mMediaPlayer && mGeneration == mPlaybackGeneration) event.run();
            });
        }

        @Override
        public void onPrepared() {
            dispatch(VideoView.this::onPrepared);
        }

        @Override
        public void onError() {
            dispatch(VideoView.this::onError);
        }

        @Override
        public void onCompletion() {
            dispatch(VideoView.this::onCompletion);
        }

        @Override
        public void onInfo(int what, int extra) {
            dispatch(() -> VideoView.this.onInfo(what, extra));
        }

        @Override
        public void onVideoSizeChanged(int width, int height) {
            dispatch(() -> VideoView.this.onVideoSizeChanged(width, height));
        }
    }

    /** 预加载事件不参与主播放器状态机，URL 相同也必须校验实例与代次。 */
    private final class PreloadPlayerEvent implements AbstractPlayer.PlayerEventListener {
        private final P mEventPlayer;
        private final long mGeneration;
        private final String mEventUrl;

        PreloadPlayerEvent(P player, long generation, String url) {
            mEventPlayer = player;
            mGeneration = generation;
            mEventUrl = url;
        }

        private void dispatch(Runnable event) {
            mMainHandler.post(() -> {
                if (mEventPlayer == mPreloadPlayer && mGeneration == mPreloadGeneration) event.run();
            });
        }

        @Override
        public void onPrepared() {
            dispatch(() -> {
                if (mPreloadPrepared) return;
                mPreloadPrepared = true;
                if (mOnPreloadListener != null) mOnPreloadListener.onPreloadReady(mEventUrl);
            });
        }

        @Override
        public void onError() {
            dispatch(() -> {
                cancelPreload();
                if (mOnPreloadListener != null) mOnPreloadListener.onPreloadFailed(mEventUrl);
            });
        }

        @Override
        public void onCompletion() {
            onError();
        }

        @Override
        public void onInfo(int what, int extra) {
        }

        @Override
        public void onVideoSizeChanged(int width, int height) {
            dispatch(() -> {
                mPreloadVideoSize[0] = width;
                mPreloadVideoSize[1] = height;
            });
        }
    }

    /**
     * 保存播放进度
     */
    protected void saveProgress() {
        if (mProgressManager != null && mCurrentPosition > 0) {
            L.d("saveProgress: " + mCurrentPosition);
            mProgressManager.saveProgress(mProgressKey == null ? mUrl : mProgressKey, mCurrentPosition);
        }
    }

    /**
     * 是否处于播放状态
     */
    public boolean isInPlaybackState() {
        return mMediaPlayer != null
                && !mPreparing && !mSourceChanged
                && mCurrentPlayState != STATE_ERROR
                && mCurrentPlayState != STATE_IDLE
                && mCurrentPlayState != STATE_PREPARING
                && mCurrentPlayState != STATE_START_ABORT
                && mCurrentPlayState != STATE_PLAYBACK_COMPLETED;
    }

    /**
     * 是否处于未播放状态
     */
    protected boolean isInIdleState() {
        return mCurrentPlayState == STATE_IDLE;
    }

    /**
     * 播放中止状态
     */
    private boolean isInStartAbortState() {
        return mCurrentPlayState == STATE_START_ABORT;
    }

    /**
     * 重新播放
     *
     * @param resetPosition 是否从头开始播放
     */
    @Override
    public void replay(boolean resetPosition) {
        if (resetPosition) {
            mCurrentPosition = 0;
        }
        mPauseRequested = false;
        mResumePending = false;
        if (mMediaPlayer != null) addDisplay();
        startPrepare(true);
    }

    /**
     * 获取视频总时长
     */
    @Override
    public long getDuration() {
        if (isInPlaybackState()) {
            return mMediaPlayer.getDuration();
        }
        return 0;
    }

    /**
     * 获取当前播放的位置
     */
    @Override
    public long getCurrentPosition() {
        if (isInPlaybackState()) {
            mCurrentPosition = mMediaPlayer.getCurrentPosition();
            return mCurrentPosition;
        }
        return 0;
    }

    /**
     * 调整播放进度
     */
    @Override
    public void seekTo(long pos) {
        if (isInPlaybackState()) {
            mMediaPlayer.seekTo(pos);
        }
    }

    /**
     * 是否处于播放状态
     */
    @Override
    public boolean isPlaying() {
        return isInPlaybackState() && mMediaPlayer.isPlaying();
    }

    /**
     * 获取当前缓冲百分比
     */
    @Override
    public int getBufferedPercentage() {
        return mMediaPlayer != null ? mMediaPlayer.getBufferedPercentage() : 0;
    }

    /**
     * 设置静音
     */
    @Override
    public void setMute(boolean isMute) {
        this.mIsMute = isMute;
        if (mMediaPlayer != null) {
            float volume = isMute ? 0.0f : 1.0f;
            mMediaPlayer.setVolume(volume, volume);
        }
    }

    /**
     * 是否处于静音状态
     */
    @Override
    public boolean isMute() {
        return mIsMute;
    }

    /**
     * 视频缓冲完毕，准备开始播放时回调
     */
    @Override
    public void onPrepared() {
        if (!mPreparing || mMediaPlayer == null) return;
        P player = mMediaPlayer;
        long generation = mPlaybackGeneration;
        mPreparing = false;
        setPlayState(mPauseRequested ? STATE_PAUSED : STATE_PREPARED);
        if (player != mMediaPlayer || generation != mPlaybackGeneration) return;
        if (mCurrentPosition > 0) player.seekTo(mCurrentPosition);
        if (mPauseRequested) {
            player.pause();
            mPlayerContainer.setKeepScreenOn(false);
        } else {
            if (!player.isPlaying()) player.start();
            if (!isMute() && mAudioFocusHelper != null) mAudioFocusHelper.requestFocus();
        }
    }

    /**
     * 播放信息回调，播放中的缓冲开始与结束，开始渲染视频第一帧，视频旋转信息
     */
    @Override
    public void onInfo(int what, int extra) {
        if (mPauseRequested && what != AbstractPlayer.MEDIA_INFO_VIDEO_ROTATION_CHANGED) return;
        switch (what) {
            case AbstractPlayer.MEDIA_INFO_BUFFERING_START:
                setPlayState(STATE_BUFFERING);
                break;
            case AbstractPlayer.MEDIA_INFO_BUFFERING_END:
                setPlayState(STATE_BUFFERED);
                break;
            case AbstractPlayer.MEDIA_INFO_RENDERING_START: // 视频/音频开始渲染
                setPlayState(STATE_PLAYING);
                mPlayerContainer.setKeepScreenOn(true);
                break;
            case AbstractPlayer.MEDIA_INFO_VIDEO_ROTATION_CHANGED:
                if (mRenderView != null) mRenderView.setVideoRotation(extra);
                break;
        }
    }

    /**
     * 视频播放出错回调
     */
    @Override
    public void onError() {
        mStartPending = false;
        mResumePending = false;
        mPreparing = false;
        invalidatePlayerEvents();
        if (mMediaPlayer != null) mMediaPlayer.setPlayWhenPrepared(false);
        mPlayerContainer.setKeepScreenOn(false);
        setPlayState(STATE_ERROR);
    }

    /**
     * 视频播放完成回调
     */
    @Override
    public void onCompletion() {
        invalidatePlayerEvents();
        mPreparing = false;
        mPlayerContainer.setKeepScreenOn(false);
        mCurrentPosition = 0;
        if (mProgressManager != null) {
            //播放完成，清除进度
            mProgressManager.saveProgress(mProgressKey == null ? mUrl : mProgressKey, 0);
        }
        setPlayState(STATE_PLAYBACK_COMPLETED);
    }

    /**
     * 获取当前播放器的状态
     */
    public int getCurrentPlayerState() {
        return mCurrentPlayerState;
    }

    /**
     * 获取当前的播放状态
     */
    public int getCurrentPlayState() {
        return mCurrentPlayState;
    }

    /**
     * 获取缓冲速度
     */
    @Override
    public long getTcpSpeed() {
        return mMediaPlayer != null ? mMediaPlayer.getTcpSpeed() : 0;
    }

    /**
     * 设置播放速度
     */
    @Override
    public void setSpeed(float speed) {
        if (isInPlaybackState()) {
            mMediaPlayer.setSpeed(speed);
        }
    }

    @Override
    public float getSpeed() {
        if (isInPlaybackState()) {
            return mMediaPlayer.getSpeed();
        }
        return 1f;
    }

    /**
     * 设置视频地址
     */
    public void setUrl(String url) {
        setUrl(url, null);
    }

    /**
     * 设置包含请求头信息的视频地址
     *
     * @param url     视频地址
     * @param headers 请求头
     */
    public void setUrl(String url, Map<String, String> headers) {
        invalidateSource();
        mAssetFileDescriptor = null;
        mUrl = url;
        mHeaders = headers;
    }

    private void invalidateSource() {
        invalidatePlayerEvents();
        mSourceChanged = true;
        if (mMediaPlayer != null) {
            mMediaPlayer.setPlayWhenPrepared(false);
            mMediaPlayer.pause();
        }
    }

    /**
     * 用于播放assets里面的视频文件
     */
    public void setAssetFileDescriptor(AssetFileDescriptor fd) {
        invalidateSource();
        mUrl = null;
        this.mAssetFileDescriptor = fd;
    }

    public void setProgressKey(String key) {
        mProgressKey = key;
    }

    /**
     * 一开始播放就seek到预先设置好的位置
     */
    public void skipPositionWhenPlay(int position) {
        this.mCurrentPosition = position;
    }

    /**
     * 设置音量 0.0f-1.0f 之间
     *
     * @param v1 左声道音量
     * @param v2 右声道音量
     */
    public void setVolume(float v1, float v2) {
        if (mMediaPlayer != null) {
            mMediaPlayer.setVolume(v1, v2);
        }
    }

    /**
     * 设置进度管理器，用于保存播放进度
     */
    public void setProgressManager(@Nullable ProgressManager progressManager) {
        this.mProgressManager = progressManager;
    }

    /**
     * 循环播放， 默认不循环播放
     */
    public void setLooping(boolean looping) {
        mIsLooping = looping;
        if (mMediaPlayer != null) {
            mMediaPlayer.setLooping(looping);
        }
    }

    /**
     * 是否开启AudioFocus监听， 默认开启，用于监听其它地方是否获取音频焦点，如果有其它地方获取了
     * 音频焦点，此播放器将做出相应反应，具体实现见{@link AudioFocusHelper}
     */
    public void setEnableAudioFocus(boolean enableAudioFocus) {
        mEnableAudioFocus = enableAudioFocus;
    }

    /**
     * 自定义播放核心，继承{@link PlayerFactory}实现自己的播放核心
     */
    public void setPlayerFactory(PlayerFactory<P> playerFactory) {
        if (playerFactory == null) {
            throw new IllegalArgumentException("PlayerFactory can not be null!");
        }
        mPlayerFactory = playerFactory;
    }

    /**
     * 自定义RenderView，继承{@link RenderViewFactory}实现自己的RenderView
     */
    public void setRenderViewFactory(RenderViewFactory renderViewFactory) {
        if (renderViewFactory == null) {
            throw new IllegalArgumentException("RenderViewFactory can not be null!");
        }
        mRenderViewFactory = renderViewFactory;
    }

    /**
     * 进入全屏
     */
    @Override
    public void startFullScreen() {
        if (mIsFullScreen)
            return;

        ViewGroup decorView = getDecorView();
        if (decorView == null)
            return;

        mIsFullScreen = true;

        //隐藏NavigationBar和StatusBar
        hideSysBar(decorView);

        //从当前FrameLayout中移除播放器视图
        this.removeView(mPlayerContainer);
        //将播放器视图添加到DecorView中即实现了全屏
        decorView.addView(mPlayerContainer);

        setPlayerState(PLAYER_FULL_SCREEN);
    }

    private void hideSysBar(ViewGroup decorView) {
        int uiOptions = decorView.getSystemUiVisibility();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            uiOptions |= View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            uiOptions |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }
        decorView.setSystemUiVisibility(uiOptions);
        getActivity().getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (hasWindowFocus && mIsFullScreen) {
            //重新获得焦点时保持全屏状态
            hideSysBar(getDecorView());
        }
    }

    /**
     * 退出全屏
     */
    @Override
    public void stopFullScreen() {
        if (!mIsFullScreen)
            return;

        ViewGroup decorView = getDecorView();
        if (decorView == null)
            return;

        mIsFullScreen = false;

        //显示NavigationBar和StatusBar
        showSysBar(decorView);

        //把播放器视图从DecorView中移除并添加到当前FrameLayout中即退出了全屏
        decorView.removeView(mPlayerContainer);
        this.addView(mPlayerContainer);

        setPlayerState(PLAYER_NORMAL);
    }

    private void showSysBar(ViewGroup decorView) {
        int uiOptions = decorView.getSystemUiVisibility();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            uiOptions &= ~View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            uiOptions &= ~View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }
        decorView.setSystemUiVisibility(uiOptions);
        getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    /**
     * 获取DecorView
     */
    protected ViewGroup getDecorView() {
        Activity activity = getActivity();
        if (activity == null) return null;
        return (ViewGroup) activity.getWindow().getDecorView();
    }

    /**
     * 获取activity中的content view,其id为android.R.id.content
     */
    protected ViewGroup getContentView() {
        Activity activity = getActivity();
        if (activity == null) return null;
        return activity.findViewById(android.R.id.content);
    }

    /**
     * 获取Activity，优先通过Controller去获取Activity
     */
    protected Activity getActivity() {
        Activity activity;
        if (mVideoController != null) {
            activity = PlayerUtils.scanForActivity(mVideoController.getContext());
            if (activity == null) {
                activity = PlayerUtils.scanForActivity(getContext());
            }
        } else {
            activity = PlayerUtils.scanForActivity(getContext());
        }
        return activity;
    }

    /**
     * 判断是否处于全屏状态
     */
    @Override
    public boolean isFullScreen() {
        return mIsFullScreen;
    }

    /**
     * 开启小屏
     */
    public void startTinyScreen() {
        if (mIsTinyScreen) return;
        ViewGroup contentView = getContentView();
        if (contentView == null) return;
        this.removeView(mPlayerContainer);
        int width = mTinyScreenSize[0];
        if (width <= 0) {
            width = PlayerUtils.getScreenWidth(getContext(), false) / 2;
        }

        int height = mTinyScreenSize[1];
        if (height <= 0) {
            height = width * 9 / 16;
        }

        LayoutParams params = new LayoutParams(width, height);
        params.gravity = Gravity.BOTTOM | Gravity.END;
        contentView.addView(mPlayerContainer, params);
        mIsTinyScreen = true;
        setPlayerState(PLAYER_TINY_SCREEN);
    }

    /**
     * 退出小屏
     */
    public void stopTinyScreen() {
        if (!mIsTinyScreen) return;

        ViewGroup contentView = getContentView();
        if (contentView == null) return;
        contentView.removeView(mPlayerContainer);
        LayoutParams params = new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        this.addView(mPlayerContainer, params);

        mIsTinyScreen = false;
        setPlayerState(PLAYER_NORMAL);
    }

    public boolean isTinyScreen() {
        return mIsTinyScreen;
    }

    @Override
    public void onVideoSizeChanged(int videoWidth, int videoHeight) {
        mVideoSize[0] = videoWidth;
        mVideoSize[1] = videoHeight;

        if (mRenderView != null) {
            mRenderView.setScaleType(mCurrentScreenScaleType);
            mRenderView.setVideoSize(videoWidth, videoHeight);
        }
    }

    /**
     * 设置控制器，传null表示移除控制器
     */
    public void setVideoController(@Nullable BaseVideoController mediaController) {
        mPlayerContainer.removeView(mVideoController);
        mVideoController = mediaController;
        if (mediaController != null) {
            mediaController.setMediaPlayer(this);
            LayoutParams params = new LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            mPlayerContainer.addView(mVideoController, params);
        }
    }

    /**
     * 设置视频比例
     */
    @Override
    public void setScreenScaleType(int screenScaleType) {
        mCurrentScreenScaleType = screenScaleType;
        if (mRenderView != null) {
            mRenderView.setScaleType(screenScaleType);
        }
    }

    /**
     * 设置镜像旋转，暂不支持SurfaceView
     */
    @Override
    public void setMirrorRotation(boolean enable) {
        if (mRenderView != null) {
            mRenderView.getView().setScaleX(enable ? -1 : 1);
        }
    }

    /**
     * 截图，暂不支持SurfaceView
     */
    @Override
    public Bitmap doScreenShot() {
        if (mRenderView != null) {
            return mRenderView.doScreenShot();
        }
        return null;
    }

    /**
     * 获取视频宽高,其中width: mVideoSize[0], height: mVideoSize[1]
     */
    @Override
    public int[] getVideoSize() {
        return mVideoSize;
    }

    /**
     * 旋转视频画面
     *
     * @param rotation 角度
     */
    @Override
    public void setRotation(float rotation) {
        if (mRenderView != null) {
            mRenderView.setVideoRotation((int) rotation);
        }
    }

    /**
     * 设置小屏的宽高
     *
     * @param tinyScreenSize 其中tinyScreenSize[0]是宽，tinyScreenSize[1]是高
     */
    public void setTinyScreenSize(int[] tinyScreenSize) {
        this.mTinyScreenSize = tinyScreenSize;
    }

    /**
     * 向Controller设置播放状态，用于控制Controller的ui展示
     */
    protected void setPlayState(int playState) {
        mCurrentPlayState = playState;
        if (mVideoController != null) {
            mVideoController.setPlayState(playState);
        }
        if (mOnStateChangeListeners != null) {
            for (OnStateChangeListener l : PlayerUtils.getSnapshot(mOnStateChangeListeners)) {
                if (l != null) {
                    l.onPlayStateChanged(playState);
                }
            }
        }
    }

    /**
     * 向Controller设置播放器状态，包含全屏状态和非全屏状态
     */
    protected void setPlayerState(int playerState) {
        mCurrentPlayerState = playerState;
        if (mVideoController != null) {
            mVideoController.setPlayerState(playerState);
        }
        if (mOnStateChangeListeners != null) {
            for (OnStateChangeListener l : PlayerUtils.getSnapshot(mOnStateChangeListeners)) {
                if (l != null) {
                    l.onPlayerStateChanged(playerState);
                }
            }
        }
    }

    /**
     * 播放状态改变监听器
     */
    public interface OnStateChangeListener {
        void onPlayerStateChanged(int playerState);

        void onPlayStateChanged(int playState);
    }

    /**
     * OnStateChangeListener的空实现。用的时候只需要重写需要的方法
     */
    public static class SimpleOnStateChangeListener implements OnStateChangeListener {
        @Override
        public void onPlayerStateChanged(int playerState) {
        }

        @Override
        public void onPlayStateChanged(int playState) {
        }
    }

    /**
     * 添加一个播放状态监听器，播放状态发生变化时将会调用。
     */
    public void addOnStateChangeListener(@NonNull OnStateChangeListener listener) {
        if (mOnStateChangeListeners == null) {
            mOnStateChangeListeners = new ArrayList<>();
        }
        mOnStateChangeListeners.add(listener);
    }

    /**
     * 移除某个播放状态监听
     */
    public void removeOnStateChangeListener(@NonNull OnStateChangeListener listener) {
        if (mOnStateChangeListeners != null) {
            mOnStateChangeListeners.remove(listener);
        }
    }

    /**
     * 设置一个播放状态监听器，播放状态发生变化时将会调用，
     * 如果你想同时设置多个监听器，推荐 {@link #addOnStateChangeListener(OnStateChangeListener)}。
     */
    public void setOnStateChangeListener(@NonNull OnStateChangeListener listener) {
        if (mOnStateChangeListeners == null) {
            mOnStateChangeListeners = new ArrayList<>();
        } else {
            mOnStateChangeListeners.clear();
        }
        mOnStateChangeListeners.add(listener);
    }

    /**
     * 移除所有播放状态监听
     */
    public void clearOnStateChangeListeners() {
        if (mOnStateChangeListeners != null) {
            mOnStateChangeListeners.clear();
        }
    }

    /**
     * 改变返回键逻辑，用于activity
     */
    public boolean onBackPressed() {
        return mVideoController != null && mVideoController.onBackPressed();
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        L.d("onSaveInstanceState: " + mCurrentPosition);
        //activity切到后台后可能被系统回收，故在此处进行进度保存
        saveProgress();
        return super.onSaveInstanceState();
    }
}
