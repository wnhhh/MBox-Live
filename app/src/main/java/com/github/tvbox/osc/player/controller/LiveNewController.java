package com.github.tvbox.osc.player.controller;

import android.content.Context;
import android.view.MotionEvent;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.github.tvbox.osc.R;

import xyz.doikki.videoplayer.util.PlayerUtils;

import org.jetbrains.annotations.NotNull;

/**
 * 横竖切换新版直播控制器
 */

public class LiveNewController extends BaseController {
    protected ProgressBar mLoading;
    private int minFlingDistance = 100;             //最小识别距离
    private int minFlingVelocity = 10;              //最小识别速度

    public LiveNewController(@NotNull Context context) {
        super(context);
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
        // 竖向三等分:左 1/3 亮度、右 1/3 音量(都在 BaseController.onScroll 里连续调节),
        // 只有中间 1/3 的上下滑才换台,避免调音量时误换台
        // 全屏时控制器会被重新挂到 decor 上,极少数时机 getWidth() 还是 0,兜底用屏幕宽度
        int w = getWidth();
        if (w <= 0) w = PlayerUtils.getScreenWidth(getContext(), true);
        if (w <= 0) w = 1;
        float startX = e1.getX();
        boolean middleZone = startX >= w / 3f && startX <= w * 2f / 3f;

        if (e1.getX() - e2.getX() > minFlingDistance && Math.abs(velocityX) > minFlingVelocity) {
            listener.changeSource(-1);          //左滑:上一条线路
        } else if (e2.getX() - e1.getX() > minFlingDistance && Math.abs(velocityX) > minFlingVelocity) {
            listener.changeSource(1);           //右滑:下一条线路
        } else if (middleZone && e1.getY() - e2.getY() > minFlingDistance && Math.abs(velocityY) > minFlingVelocity) {
            listener.changeChannel(1);          //中间上滑:下一个台
        } else if (middleZone && e2.getY() - e1.getY() > minFlingDistance && Math.abs(velocityY) > minFlingVelocity) {
            listener.changeChannel(-1);         //中间下滑:上一个台
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
