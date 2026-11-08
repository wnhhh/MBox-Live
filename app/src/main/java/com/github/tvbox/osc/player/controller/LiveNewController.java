package com.github.tvbox.osc.player.controller;

import android.content.Context;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.github.tvbox.osc.R;

import org.jetbrains.annotations.NotNull;

/**
 * 横竖切换新版直播控制器
 */

public class LiveNewController extends BaseController {
    protected ProgressBar mLoading;
    private int minFlingDistance = 100;             //最小识别距离
    private int minFlingVelocity = 10;              //最小识别速度

    /** 中区竖滑超过该位移才算一次换台,低于此值视为误触(约 60dp) */
    private final int mSwitchThreshold;
    /** 中区当前滑动方向:1 下一个台 / -1 上一个台 / 0 未达阈值 */
    private int mCenterDir = 0;
    /** 上次刷新换台提示的时刻:拖动期间按 400ms 续一次,避免浮层在手指还按着时自己消失 */
    private long mLastTipAt = 0L;

    public LiveNewController(@NotNull Context context) {
        super(context);
        mSwitchThreshold = Math.round(60f * context.getResources().getDisplayMetrics().density);
    }

    /**
     * 直播:任何状态下都允许手势。
     * 换台后到出画面前是 STATE_PREPARING(转圈),默认门槛会把滑动手势整个挡掉,
     * 表现就是"加载中怎么滑都换不了台";而换台本身就是要打断当前加载,没理由禁用。
     */
    @Override
    protected boolean isGestureAvailable() {
        return mControlWrapper != null;
    }

    /**
     * 中区竖滑:拖动超过阈值就提示"即将切到哪个台"(带台名),松手才真正换台。
     * 这样慢滑、短促拖动也能换(不再依赖 onFling 的速度门槛),一次手势只切一个台。
     */
    @Override
    protected void onCenterZoneSlide(MotionEvent e1, MotionEvent e2, float deltaY) {
        int dir = resolveDir(deltaY);
        if (dir != mCenterDir) {   //方向变化:重新取台名
            mCenterDir = dir;
            mLastTipAt = 0L;
        }
        if (dir == 0) {
            hideSlideTip();
            return;
        }
        // 浮层 1 秒自动消失,而用户可能还按着在拖:拖动期间每 400ms 续一次,别让它中途消失
        long now = SystemClock.elapsedRealtime();
        if (now - mLastTipAt < 400L) return;
        mLastTipAt = now;
        String name = listener == null ? null : listener.previewChannelName(dir);
        String prefix = dir > 0 ? "↑ 下一个台" : "↓ 上一个台";
        showSlideTip(name == null || name.isEmpty() ? prefix : prefix + "：" + name);
    }

    @Override
    protected void onCenterZoneSlideEnd(boolean canceled, float totalDeltaY) {
        int dir = resolveDir(totalDeltaY);
        mCenterDir = 0;
        hideSlideTip();
        if (canceled || dir == 0 || listener == null) return;
        listener.changeChannel(dir);
    }

    /** 中区竖滑方向:上滑(deltaY 为正)= 1 下一个台,下滑 = -1 上一个台,未过阈值 = 0 */
    private int resolveDir(float deltaY) {
        if (deltaY > mSwitchThreshold) return 1;
        if (deltaY < -mSwitchThreshold) return -1;
        return 0;
    }

    @Override
    protected int getLayoutId() {
        return R.layout.player_live_control_view;
    }

    @Override
    protected void initView() {
        super.initView();
        mLoading = findViewById(R.id.loading);
    }

    public interface LiveControlListener {
        void setting();

        void playStateChanged(int playState);

        void changeSource(int direction);

        /** 上下滑换台:direction &gt; 0 下一个台,&lt; 0 上一个台 */
        void changeChannel(int direction);

        /**
         * 换台预览:拖动中询问"这个方向上的相邻台叫什么",用于浮层提示。
         *
         * @param direction &gt; 0 下一个台,&lt; 0 上一个台
         * @return 台名;取不到返回 null(浮层退化为只显示"下一个台/上一个台")
         */
        String previewChannelName(int direction);
    }

    private LiveNewController.LiveControlListener listener = null;

    public void setListener(LiveNewController.LiveControlListener listener) {
        this.listener = listener;
    }

    @Override
    protected void onPlayStateChanged(int playState) {
        super.onPlayStateChanged(playState);
        listener.playStateChanged(playState);
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        // 竖向换台已改由 BaseController 的中区分区 + 松手结算处理(见 onCenterZoneSlideEnd),
        // 这里不能再处理竖向,否则一次竖向滑动会既走中区又走 fling,连跳两个台。
        // 只保留横向:左滑上一条线路、右滑下一条线路。
        if (e1.getX() - e2.getX() > minFlingDistance && Math.abs(velocityX) > minFlingVelocity) {
            listener.changeSource(-1);          //左滑:上一条线路
        } else if (e2.getX() - e1.getX() > minFlingDistance && Math.abs(velocityX) > minFlingVelocity) {
            listener.changeSource(1);           //右滑:下一条线路
        }
        return false;
    }

    @Override
    public boolean onBackPressed() {
        if (mControlWrapper.isFullScreen()) {
            return stopFullScreen();
        }
        return super.onBackPressed();
    }
}
