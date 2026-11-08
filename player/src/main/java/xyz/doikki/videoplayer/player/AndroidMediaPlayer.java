package xyz.doikki.videoplayer.player;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Looper;
import android.view.Surface;
import android.view.SurfaceHolder;

import java.util.Map;

/**
 * 封装系统的MediaPlayer，不推荐，系统的MediaPlayer兼容性较差，建议使用IjkPlayer或者ExoPlayer
 */
public class AndroidMediaPlayer extends AbstractPlayer implements MediaPlayer.OnErrorListener,
        MediaPlayer.OnCompletionListener, MediaPlayer.OnInfoListener,
        MediaPlayer.OnBufferingUpdateListener, MediaPlayer.OnPreparedListener,
        MediaPlayer.OnVideoSizeChangedListener {

    protected MediaPlayer mMediaPlayer;
    private int mBufferedPercent;
    private Context mAppContext;
    private boolean mIsPreparing;
    private boolean mPrepared;

    public AndroidMediaPlayer(Context context) {
        mAppContext = context.getApplicationContext();
    }

    @Override
    public void initPlayer() {
        mMediaPlayer = new MediaPlayer();
        setOptions();
        mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mMediaPlayer.setOnErrorListener(this);
        mMediaPlayer.setOnCompletionListener(this);
        mMediaPlayer.setOnInfoListener(this);
        mMediaPlayer.setOnBufferingUpdateListener(this);
        mMediaPlayer.setOnPreparedListener(this);
        mMediaPlayer.setOnVideoSizeChangedListener(this);
    }

    @Override
    public void setDataSource(String path, Map<String, String> headers) {
        try {
            mMediaPlayer.setDataSource(mAppContext, Uri.parse(path), headers);
        } catch (Exception e) {
            if (mPlayerEventListener != null) mPlayerEventListener.onError();
        }
    }

    @Override
    public void setDataSource(AssetFileDescriptor fd) {
        try {
            mMediaPlayer.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
        } catch (Exception e) {
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
            mIsPreparing = true;
            mPrepared = false;
            mMediaPlayer.prepareAsync();
        } catch (IllegalStateException e) {
            if (mPlayerEventListener != null) mPlayerEventListener.onError();
        }
    }

    @Override
    public void reset() {
        mIsPreparing = false;
        mPrepared = false;
        mBufferedPercent = 0;
        mMediaPlayer.reset();
        mMediaPlayer.setSurface(null);
        mMediaPlayer.setDisplay(null);
        mMediaPlayer.setVolume(1, 1);
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
        final MediaPlayer player = mMediaPlayer;
        mMediaPlayer = null;
        mPlayerEventListener = null;
        mPrepared = false;
        mIsPreparing = false;
        if (player != null) {
            cleanupForRelease(() -> {
                player.setOnErrorListener(null);
                player.setOnCompletionListener(null);
                player.setOnInfoListener(null);
                player.setOnBufferingUpdateListener(null);
                player.setOnPreparedListener(null);
                player.setOnVideoSizeChangedListener(null);
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
        if (mMediaPlayer == null) return;
        mMediaPlayer.setSurface(surface);
    }

    @Override
    public void setDisplay(SurfaceHolder holder) {
        if (mMediaPlayer == null) return;
        mMediaPlayer.setDisplay(holder);
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
    public void setOptions() {
    }

    @Override
    public void setSpeed(float speed) {
        // only support above Android M
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                mMediaPlayer.setPlaybackParams(mMediaPlayer.getPlaybackParams().setSpeed(speed));
            } catch (Exception e) {
                if (mPlayerEventListener != null) mPlayerEventListener.onError();
            }
        }
    }

    @Override
    public float getSpeed() {
        // only support above Android M
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                return mMediaPlayer.getPlaybackParams().getSpeed();
            } catch (Exception e) {
                if (mPlayerEventListener != null) mPlayerEventListener.onError();
            }
        }
        return 1f;
    }

    @Override
    public long getTcpSpeed() {
        // no support
        return 0;
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        if (mp == mMediaPlayer && mPlayerEventListener != null) mPlayerEventListener.onError();
        return true;
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        if (mp == mMediaPlayer && mPlayerEventListener != null) mPlayerEventListener.onCompletion();
    }

    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        if (mp != mMediaPlayer || mPlayerEventListener == null) return true;
        //解决MEDIA_INFO_VIDEO_RENDERING_START多次回调问题
        if (what == AbstractPlayer.MEDIA_INFO_RENDERING_START) {
            if (mIsPreparing) {
                mPlayerEventListener.onInfo(what, extra);
                mIsPreparing = false;
            }
        } else {
            mPlayerEventListener.onInfo(what, extra);
        }
        return true;
    }

    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {
        if (mp == mMediaPlayer) mBufferedPercent = percent;
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        if (mp != mMediaPlayer || mPlayerEventListener == null) return;
        mPrepared = true;
        PlayerEventListener listener = mPlayerEventListener;
        listener.onPrepared();
        if (mp == mMediaPlayer && listener == mPlayerEventListener && !mPreloadOnly && mPlayWhenPrepared) {
            start();
        }
    }

    private boolean isVideo() {
        try {
            MediaPlayer.TrackInfo[] trackInfo = mMediaPlayer.getTrackInfo();
            for (MediaPlayer.TrackInfo info :
                    trackInfo) {
                if (info.getTrackType() == MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_VIDEO) {
                    return true;
                }
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    @Override
    public void onVideoSizeChanged(MediaPlayer mp, int width, int height) {
        if (mp != mMediaPlayer || mPlayerEventListener == null) return;
        int videoWidth = mp.getVideoWidth();
        int videoHeight = mp.getVideoHeight();
        if (videoWidth != 0 && videoHeight != 0) {
            mPlayerEventListener.onVideoSizeChanged(videoWidth, videoHeight);
        }
    }
}
