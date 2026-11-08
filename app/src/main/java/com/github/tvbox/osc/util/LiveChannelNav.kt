package com.github.tvbox.osc.util

import com.github.tvbox.osc.bean.LiveChannelGroup
import java.util.function.IntPredicate
import java.util.function.IntUnaryOperator

/**
 * 直播频道组/频道导航纯逻辑(自 LiveActivity.getNextChannel 抽取):
 * 上/下一条频道;跨组时跳过空组和锁定组,无其他目标则在可见的当前组内回卷。
 * 输入为组数、各组可见频道数、各组是否锁定,输出 {组索引, 频道索引}。
 * 无 View/Context 依赖,可 JVM 单测。
 */
object LiveChannelNav {

    /**
     * 计算上一条(direction&lt;0)或下一条(direction&gt;0)频道。
     *
     * @param direction      -1 上一频道 / 1 下一频道
     * @param groupCount     分组总数(回卷上界)
     * @param channelCount   第 i 组的可见频道数(授权后取实际数,未授权通常 0)
     * @param groupLocked    第 i 组是否锁定(加密);跨组跳转时跳过
     * @param currentGroup   当前组索引
     * @param currentChannel 当前组内频道索引
     * @param crossGroup     是否允许跨组;关闭时当前组失效或为空则无目标
     * @return {组索引, 频道索引},无可用目标时为 {-1, -1}
     */
    @JvmStatic
    fun next(
        direction: Int,
        groupCount: Int,
        channelCount: IntUnaryOperator,
        groupLocked: IntPredicate,
        currentGroup: Int,
        currentChannel: Int,
        crossGroup: Boolean
    ): IntArray {
        if (groupCount <= 0) return intArrayOf(-1, -1)

        val forward = direction > 0
        val validCurrentGroup = currentGroup in 0 until groupCount
        // 静默刷新可能删组:必须先校验下标,再调用基于列表取值的回调。
        val currentCount = if (validCurrentGroup) channelCount.applyAsInt(currentGroup) else 0
        if (currentCount > 0) {
            // 频道列表也可能缩短;失效频道从当前组首/尾恢复,避免索引运算溢出。
            if (currentChannel !in 0 until currentCount) {
                return intArrayOf(currentGroup, if (forward) 0 else currentCount - 1)
            }
            val channel = currentChannel + if (forward) 1 else -1
            if (channel in 0 until currentCount) return intArrayOf(currentGroup, channel)
        }

        if (crossGroup) {
            // 失效组按方向从列表首/尾寻找;有效组最多检查其余 groupCount - 1 组。
            var group = if (validCurrentGroup) currentGroup else if (forward) groupCount - 1 else 0
            repeat(if (validCurrentGroup) groupCount - 1 else groupCount) {
                group = if (forward) {
                    if (group == groupCount - 1) 0 else group + 1
                } else {
                    if (group == 0) groupCount - 1 else group - 1
                }
                if (!groupLocked.test(group)) {
                    val count = channelCount.applyAsInt(group)
                    if (count > 0) return intArrayOf(group, if (forward) 0 else count - 1)
                }
            }
        }

        // 当前加密组若已有可见频道,仍保留原有组内导航/回卷语义。
        return if (currentCount > 0) {
            intArrayOf(currentGroup, if (forward) 0 else currentCount - 1)
        } else {
            intArrayOf(-1, -1)
        }
    }

    /** 刷新后按名称定位:优先同组同频道,否则取全组首个同名频道;只返回实际列表下标。 */
    @JvmStatic
    fun findChannel(
        groups: List<LiveChannelGroup>,
        groupName: String?,
        channelName: String?
    ): IntArray {
        if (channelName == null) return intArrayOf(-1, -1)

        var fallbackGroup = -1
        var fallbackChannel = -1
        for ((groupIndex, group) in groups.withIndex()) {
            val channelIndex = group.liveChannels?.indexOfFirst { it.channelName == channelName } ?: -1
            if (channelIndex < 0) continue
            if (groupName != null && group.groupName == groupName) {
                return intArrayOf(groupIndex, channelIndex)
            }
            if (fallbackGroup < 0) {
                fallbackGroup = groupIndex
                fallbackChannel = channelIndex
            }
        }
        return intArrayOf(fallbackGroup, fallbackChannel)
    }
}
