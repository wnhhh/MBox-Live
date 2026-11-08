package com.github.tvbox.osc.player.controller;

import android.content.Context;
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
     * 中区竖滑:拖动过程不动,松手才换台。
     * 这样慢滑、短促拖动也能换(不再依赖 onFling 的速度门槛),一次手势只切一个台。
     */
    @Override
    protected void onCenterZoneSlideEnd(boolean canceled, float totalDeltaY) {
        if (canceled || listener == null) return;
        if (totalDeltaY > mSwitchThreshold) {
            listener.changeChannel(1);          //上滑:下一个台
        } else if (totalDeltaY < -mSwitchThreshold) {
            listener.changeChannel(-1);         //下滑:上一个台
        }
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
