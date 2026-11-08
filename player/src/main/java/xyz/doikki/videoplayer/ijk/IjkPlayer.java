package xyz.doikki.videoplayer.ijk;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Surface;
import android.view.SurfaceHolder;

import java.util.Map;

import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;
import tv.danmaku.ijk.media.player.misc.ITrackInfo;
import tv.danmaku.ijk.media.player.misc.IjkTrackInfo;
import xyz.doikki.videoplayer.player.AbstractPlayer;
import xyz.doikki.videoplayer.player.VideoViewManager;

public class IjkPlayer extends AbstractPlayer implements IMediaPlayer.OnErrorListener,
        IMediaPlayer.OnCompletionListener, IMediaPlayer.OnInfoListener,
        IMediaPlayer.OnBufferingUpdateListener, IMediaPlayer.OnPreparedListener,
        IMediaPlayer.OnVideoSizeChangedListener, IjkMediaPlayer.OnNativeInvokeListener {

    protected IjkMediaPlayer mMediaPlayer;
    private int mBufferedPercent;
    private final Context mAppContext;
    private boolean mPrepared;

    public IjkPlayer(Context context) {
        mAppContext = context;
    }

    @Override
    public void initPlayer() {
        mMediaPlayer = new IjkMediaPlayer();
        //native日志
        IjkMediaPlayer.native_setLogLevel(VideoViewManager.getConfig().mIsEnableLog ? IjkMediaPlayer.IJK_LOG_INFO : IjkMediaPlayer.IJK_LOG_SILENT);
        setOptions();
        mMediaPlayer.setOnErrorListener(this);
        mMediaPlayer.setOnCompletionListener(this);
        mMediaPlayer.setOnInfoListener(this);
        mMediaPlayer.setOnBufferingUpdateListener(this);
        mMediaPlayer.setOnPreparedListener(this);
        mMediaPlayer.setOnVideoSizeChangedListener(this);
        mMediaPlayer.setOnNativeInvokeListener(this);
    }


    @Override
    public void setOptions() {
    }

    @Override
    public void setDataSource(String path, Map<String, String> headers) {
        try {
            Uri uri = Uri.parse(path);
            if (ContentResolver.SCHEME_ANDROID_RESOURCE.equals(uri.getScheme())) {
                RawDataSourceProvider rawDataSourceProvider = RawDataSourceProvider.create(mAppContext, uri);
                mMediaPlayer.setDataSource(rawDataSourceProvider);
            } else {
                //处理UA问题
                if (headers != null) {
                    String userAgent = headers.get("User-Agent");
                    if (!TextUtils.isEmpty(userAgent)) {
                        mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_FORMAT, "user_agent", userAgent);
                        // 移除header中的User-Agent，防止重复
                        headers.remove("User-Agent");
                    }
                }
                mMediaPlayer.setDataSource(mAppContext, uri, headers);
            }
        } catch (Exception e) {
            if (mPlayerEventListener != null) mPlayerEventListener.onError();
        }
    }

    @Override
    public void setDataSource(AssetFileDescriptor fd) {
        try {
            mMediaPlayer.setDataSource(new RawDataSourceProvider(fd));
        } catch (Exception e) {
            if (mPlayerEventListener != null) mPlayerEventListener.onError();
        }
    }

    @Override
    public void pause() {
        mPlayWhenPrepared = false;
        if (mMediaPlayer == null || !mPrepared) return;
        try {
            mMediaPlayer.pause();
        } catch (IllegalStateException e) {
            if (mPlayerEventListener != null) mPlayerEventListener.onError();
        }
    }

    @Override
    public void start() {
        mPlayWhenPrepared = true;
        if (mMediaPlayer == null || !mPrepared || mPreloadOnly) return;
        try {
            mMediaPlayer.start();
            if (!isVideo() && mPlayerEventListener != null) {
                mPlayerEventListener.onInfo(MEDIA_INFO_RENDERING_START, 0);
            }
        } catch (IllegalStateException e) {
            if (mPlayerEventListener != null) mPlayerEventListener.onError();
        }
    }

    @Override
    public void stop() {
        try {
            mMediaPlayer.stop();
        } catch (IllegalStateException e) {
            if (mPlayerEventListener != null) mPlayerEventListener.onError();
        }
    }

    @Override
    public void prepareAsync() {
        try {
            mPrepared = false;
            // 由 Java prepared 回调决定起播，避免 prepare 期间暂停后 native 仍自动 start。
            mMediaPlayer.setOption(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "start-on-prepared", 0);
            mMediaPlayer.prepareAsync();
        } catch (IllegalStateException e) {
            if (mPlayerEventListener != null) mPlayerEventListener.onError();
        }
    }

    @Override
    public boolean canReuseForSwitch() {
        return false;
    }

    @Override
    public void reset() {
        mPrepared = false;
        mBufferedPercent = 0;
        mMediaPlayer.reset();
        mMediaPlayer.setOnVideoSizeChangedListener(this);
        setOptions();
    }

    @Override
    public boolean isPlaying() {
        return mMediaPlayer.isPlaying();
    }

    @Override
    public void seekTo(long time) {
        try {
            mMediaPlayer.seekTo((int) time);
        } catch (IllegalStateException e) {
            if (mPlayerEventListener != null) mPlayerEventListener.onError();
        }
    }

    @Override
    public void release() {
        release(null);
    }

    @Override
    public void release(Runnable completion) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mMainHandler.post(() -> release(completion));
            return;
        }
        if (!beginRelease(completion)) return;
        final IjkMediaPlayer player = mMediaPlayer;
        mMediaPlayer = null;
        mPlayerEventListener = null;
        mPrepared = false;
        if (player != null) {
            cleanupForRelease(() -> {
                player.resetListeners();
                player.setOnNativeInvokeListener(null);
                player.setOnControlMessageListener(null);
            });
            cleanupForRelease(() -> player.setSurface(null));
            cleanupForRelease(() -> player.setDisplay(null));
        }
        releaseOnExecutor(() -> {
            if (player != null) player.release();
        });
    }

    @Override
    public long getCurrentPosition() {
        return mMediaPlayer.getCurrentPosition();
    }

    @Override
    public long getDuration() {
        return mMediaPlayer.getDuration();
    }

    @Override
    public int getBufferedPercentage() {
        return mBufferedPercent;
    }

    @Override
    public void setSurface(Surface surface) {
        if (mMediaPlayer != null) mMediaPlayer.setSurface(surface);
    }

    @Override
    public void setDisplay(SurfaceHolder holder) {
        if (mMediaPlayer != null) mMediaPlayer.setDisplay(holder);
    }

    @Override
    public void setVolume(float v1, float v2) {
        mMediaPlayer.setVolume(v1, v2);
    }

    @Override
    public void setLooping(boolean isLooping) {
        mMediaPlayer.setLooping(isLooping);
    }

    @Override
    public void setSpeed(float speed) {
        mMediaPlayer.setSpeed(speed);
    }

    @Override
    public float getSpeed() {
        return mMediaPlayer.getSpeed(0);
    }

    @Override
    public long getTcpSpeed() {
        return mMediaPlayer.getTcpSpeed();
    }

    @Override
    public boolean onError(IMediaPlayer mp, int what, int extra) {
        if (mp == mMediaPlayer && mPlayerEventListener != null) mPlayerEventListener.onError();
        return true;
    }

    @Override
    public void onCompletion(IMediaPlayer mp) {
        if (mp == mMediaPlayer && mPlayerEventListener != null) mPlayerEventListener.onCompletion();
    }

    @Override
    public boolean onInfo(IMediaPlayer mp, int what, int extra) {
        if (mp == mMediaPlayer && mPlayerEventListener != null) mPlayerEventListener.onInfo(what, extra);
        return true;
    }

    @Override
    public void onBufferingUpdate(IMediaPlayer mp, int percent) {
        if (mp == mMediaPlayer) mBufferedPercent = percent;
    }

    @Override
    public void onPrepared(IMediaPlayer mp) {
        if (mp != mMediaPlayer || mPlayerEventListener == null) return;
        mPrepared = true;
        PlayerEventListener listener = mPlayerEventListener;
        listener.onPrepared();
        if (mp == mMediaPlayer && listener == mPlayerEventListener && !mPreloadOnly && mPlayWhenPrepared) {
            start();
        }
    }

    private boolean isVideo() {
        IjkTrackInfo[] trackInfo = mMediaPlayer.getTrackInfo();
        if (trackInfo == null) return false;
        for (IjkTrackInfo info : trackInfo) {
            if (info.getTrackType() == ITrackInfo.MEDIA_TRACK_TYPE_VIDEO) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onVideoSizeChanged(IMediaPlayer mp, int width, int height, int sar_num, int sar_den) {
        if (mp != mMediaPlayer || mPlayerEventListener == null) return;
        int videoWidth = mp.getVideoWidth();
        int videoHeight = mp.getVideoHeight();
        if (videoWidth != 0 && videoHeight != 0) {
            mPlayerEventListener.onVideoSizeChanged(videoWidth, videoHeight);
        }
    }

    @Override
    public boolean onNativeInvoke(int what, Bundle args) {
        return true;
    }
}
