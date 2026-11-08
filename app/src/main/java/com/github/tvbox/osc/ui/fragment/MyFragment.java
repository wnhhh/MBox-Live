package com.github.tvbox.osc.ui.fragment;

import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;

import com.blankj.utilcode.util.AppUtils;
import com.blankj.utilcode.util.ClipboardUtils;
import com.github.tvbox.osc.util.AppBubble;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.base.BaseVbFragment;
import com.github.tvbox.osc.databinding.FragmentMyBinding;
import com.github.tvbox.osc.ui.activity.CollectActivity;
import com.github.tvbox.osc.ui.activity.DetailActivity;
import com.github.tvbox.osc.ui.activity.DownloadActivity;
import com.github.tvbox.osc.ui.activity.HistoryActivity;
import com.github.tvbox.osc.ui.activity.LiveActivity;
import com.github.tvbox.osc.ui.activity.LocalPlayActivity;
import com.github.tvbox.osc.ui.activity.MovieFoldersActivity;
import com.github.tvbox.osc.ui.activity.SettingActivity;
import com.github.tvbox.osc.ui.activity.SubscriptionActivity;
import com.github.tvbox.osc.ui.dialog.AboutDialog;
import com.github.tvbox.osc.ui.dialog.DialogCoordinator;
import com.github.tvbox.osc.update.UpdateCheck;
import com.github.tvbox.osc.update.UpdateInfo;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.Utils;
import com.hjq.permissions.OnPermissionCallback;
import com.hjq.permissions.Permission;
import com.hjq.permissions.XXPermissions;
import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.interfaces.OnInputConfirmListener;

import java.util.Arrays;
import java.util.List;

/**
 * @author pj567
 * @date :2021/3/9
 * @description:
 */
public class MyFragment extends BaseVbFragment<FragmentMyBinding> {


    @Override
    protected void init() {
        mBinding.tvVersion.setText("v"+ AppUtils.getAppVersionName());

        // 「我的」页直接检查更新:不必先点「关于」再点弹窗里的按钮(TV 遥控少两步)
        mBinding.btnCheckUpdate.setOnClickListener(v -> checkUpdate());

        mBinding.addrPlay.setOnClickListener(v ->{
            new XPopup.Builder(getContext())
                    .asInputConfirm("播放", "", isPush(ClipboardUtils.getText().toString())?ClipboardUtils.getText():"", "地址", text -> {
                        if (!TextUtils.isEmpty(text)){
                            Intent newIntent = new Intent(mContext, DetailActivity.class);
                            newIntent.putExtra("id", text);
                            newIntent.putExtra("sourceKey", "push_agent");
                            newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            startActivity(newIntent);
                        }
                    }, null, R.layout.dialog_input).show();
        });
        //mBinding.tvLive.setOnClickListener(v -> jumpActivity(LivePlayActivity.class));
        mBinding.tvLive.setOnClickListener(v -> jumpActivity(LiveActivity.class));

        mBinding.tvSetting.setOnClickListener(v -> jumpActivity(SettingActivity.class));

        mBinding.tvHistory.setOnClickListener(v -> jumpActivity(HistoryActivity.class));

        mBinding.tvDownload.setOnClickListener(v -> jumpActivity(DownloadActivity.class));

        mBinding.tvFavorite.setOnClickListener(v -> jumpActivity(CollectActivity.class));

        mBinding.tvLocal.setOnClickListener(v -> {
            if (!XXPermissions.isGranted(mContext, Permission.MANAGE_EXTERNAL_STORAGE)) {
                showPermissionTipPopup();
            } else {
                jumpActivity(MovieFoldersActivity.class);
            }
        });

        mBinding.llSubscription.setOnClickListener(v -> jumpActivity(SubscriptionActivity.class));

        mBinding.llAbout.setOnClickListener(v -> {
            DialogCoordinator.center(mActivity, new AboutDialog(mActivity)).show();
        });
    }

    /**
     * 检查更新(「我的」页底部按钮):发现新版本直接弹更新说明,无更新/失败用气泡提示。
     * 与「关于」弹窗、启动自动检查共用 {@link UpdateCheck},三处行为完全一致。
     */
    private void checkUpdate() {
        FastClickCheckUtil.check(mBinding.btnCheckUpdate);
        AppBubble.toast("正在检查更新...");
        UpdateCheck.check(mActivity, new UpdateCheck.Listener() {
            @Override
            public void onChecking() {
                // 点击时已经提示过,这里不再重复
            }

            @Override
            public boolean onResult(UpdateInfo newVersion) {
                if (newVersion == null) {
                    AppBubble.toast("当前已是最新版本");
                }
                // 返回 false:有新版本时由 UpdateCheck 立刻弹更新说明(本页没有宿主弹窗要等退场)
                return false;
            }

            @Override
            public void onFailed(String message) {
                AppBubble.toast(message);
            }
        });
    }

    private void showPermissionTipPopup(){
        // 统一主题化确认弹窗(替代 XPopup 默认 asConfirm)
        com.github.tvbox.osc.ui.dialog.ConfirmDialog.show(mActivity, "提示",
                "为了播放视频、音频等,我们需要访问您设备文件的读写权限", "去授权", this::getPermission);
    }

    private void getPermission(){
        XXPermissions.with(this)
                .permission(Permission.MANAGE_EXTERNAL_STORAGE)
                .request(new OnPermissionCallback() {
                    @Override
                    public void onGranted(List<String> permissions, boolean all) {
                        if (all) {
                            jumpActivity(MovieFoldersActivity.class);
                        }else {
                            AppBubble.toastLong("部分权限未正常授予,请授权");
                        }
                    }

                    @Override
                    public void onDenied(List<String> permissions, boolean never) {
                        if (never) {
                            AppBubble.toastLong("读写文件权限被永久拒绝，请手动授权");
                            // 如果是被永久拒绝就跳转到应用权限系统设置页面
                            XXPermissions.startPermissionActivity(mActivity, permissions);
                        } else {
                            AppBubble.toast("获取权限失败");
                            showPermissionTipPopup();
                        }
                    }
                });
    }

    private boolean isPush(String text) {
        return !TextUtils.isEmpty(text) && Arrays.asList("smb", "http", "https", "thunder", "magnet", "ed2k", "mitv", "jianpian").contains(Uri.parse(text).getScheme());
    }

}