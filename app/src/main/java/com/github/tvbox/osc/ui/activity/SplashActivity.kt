package com.github.tvbox.osc.ui.activity

import android.content.Intent
import android.os.Handler
import com.github.tvbox.osc.R
import com.github.tvbox.osc.base.App
import com.github.tvbox.osc.base.BaseVbActivity
import com.github.tvbox.osc.databinding.ActivitySplashBinding

/**
 * 启动页:直接进直播页(直播优先的包)。
 *
 * 本包内置了默认直播源、也不需要点播订阅,所以跳过 MainActivity 首页,
 * 打开即出画面;按返回键退出应用。首页(点播/订阅)仍保留在代码里,
 * 需要时可从直播页长按刷新按钮旁的小圆点 → 更换直播源,或改回 MainActivity。
 */
class SplashActivity : BaseVbActivity<ActivitySplashBinding>() {
    override fun init() {
        App.getInstance().isNormalStart = true

        mBinding.root.postDelayed({
            startActivity(Intent(this@SplashActivity, LiveActivity::class.java))
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
            finish()
        }, 0)

    }
}
