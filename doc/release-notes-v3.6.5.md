## 换台前就能看到要切到哪个台

### 功能变更

- **上下滑换台时会提示目标台名**：屏幕中间显示「↑ 下一个台：CCTV-1」这样的提示，手指还没松就知道会切到哪一台；拖动期间提示一直保留，不会滑到一半自己消失。
- **换台完成后右上角会显示「台号 + 台名 + 线路(x/y)」约 3 秒**，确认切到了哪一台、用的是第几条线路。

### 兼容性与注意事项

- 版本号 `3.6.5`，覆盖安装即可，数据保留。
- 提示位置跟着屏幕中间走，长台名会自动换行；如果觉得字号偏大或停留时间不合适，说一声我调整。

<!-- dev -->
本版区间 `v3.6.4..v3.6.5`：BaseController 新增 showSlideTip/hideSlideTip(复用 100/101 消息与
vod_control_slide_info 浮层)、LiveControlListener 新增 previewChannelName(direction)、
LiveNewController 中区拖动按方向提示台名(400ms 续期避免浮层中途消失)、
LiveActivity 新增 getChannelNameByDirection(与 zapChannel 同口径 getNextChannel(dir, true))、
playChannel 恢复 showChannelInfo();版本号 3.6.5(versionCode 80)。
发布流程：正文取本文件；缺失时 CI 只保留围栏内的提交记录。
<!-- /dev -->
