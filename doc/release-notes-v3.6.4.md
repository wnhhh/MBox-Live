## 转圈加载时也能换台了

### 问题修复

- 修复**切台后那段转圈加载期间，屏幕怎么滑都换不了台**：之前只要还没出画面，整块滑动手势都是被忽略的；现在加载中、甚至当前线路出错时，上下滑都能继续换台，不用等它转完。
- 中间区域换台不再要求"甩得够快"：以前只有快速滑动才认，慢慢滑（或只拖一小段）经常没反应；现在拖动超过一小段距离、松手就换，一次手势只切一个台。
- 左右两侧仍然是调亮度和音量，横向滑动仍然是切线路，这些没变。

### 兼容性与注意事项

- 版本号 `3.6.4`，覆盖安装即可，数据保留。

<!-- dev -->
本版区间 `v3.6.3..v3.6.4`：BaseController 新增 isGestureAvailable() 钩子(onDown/onScroll 改用它判定,
默认仍等价 isInPlaybackState)、中区竖向滑动钩子 onCenterZoneSlide/onCenterZoneSlideEnd + mChangeCenterZone/mDownY
(ACTION_UP 用按下→抬起总位移结算)、LiveNewController 放开状态门槛并把中区改为松手结算(60dp),
onFling 收敛为只处理横滑切线路;版本号 3.6.4(versionCode 79)。
发布流程：正文取本文件；缺失时 CI 只保留围栏内的提交记录。
<!-- /dev -->
