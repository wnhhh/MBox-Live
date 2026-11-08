package com.github.tvbox.osc.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.github.tvbox.osc.bean.LiveChannelGroup;
import com.github.tvbox.osc.bean.LiveChannelItem;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** LiveChannelAuth / LiveChannelNav(直播分组门禁、导航与刷新定位纯逻辑)单测 */
public class LiveChannelAuthTest {

    private static LiveChannelGroup group(int idx, String password) {
        LiveChannelGroup g = new LiveChannelGroup();
        g.setGroupIndex(idx);
        g.setGroupPassword(password);
        return g;
    }

    @Test
    public void isPasswordConfirmed_matchesIndex() {
        List<Integer> confirmed = new ArrayList<>();
        confirmed.add(0);
        confirmed.add(3);
        assertTrue(LiveChannelAuth.isPasswordConfirmed(confirmed, 0));
        assertTrue(LiveChannelAuth.isPasswordConfirmed(confirmed, 3));
        assertFalse(LiveChannelAuth.isPasswordConfirmed(confirmed, 1));
        assertFalse(LiveChannelAuth.isPasswordConfirmed(null, 0));
    }

    @Test
    public void needInputPassword_onlyWhenProtectedAndUnconfirmed() {
        List<LiveChannelGroup> groups = new ArrayList<>();
        groups.add(group(0, "1234"));
        groups.add(group(1, ""));   // 无密码
        List<Integer> confirmed = new ArrayList<>();
        confirmed.add(0);
        assertFalse(LiveChannelAuth.needInputPassword(groups, confirmed, 0)); // 已确认
        assertTrue(LiveChannelAuth.needInputPassword(groups, new ArrayList<>(), 0)); // 未确认且设密
        assertFalse(LiveChannelAuth.needInputPassword(groups, new ArrayList<>(), 1)); // 无密码组
    }

    @Test
    public void visibleChannels_authorizedReturnsSameList() {
        List<LiveChannelGroup> groups = new ArrayList<>();
        groups.add(group(0, "1234"));
        ArrayList<LiveChannelItem> channels = new ArrayList<>();
        LiveChannelItem c = new LiveChannelItem();
        c.setChannelName("央视1");
        channels.add(c);
        groups.get(0).setLiveChannels(channels);

        // 未确认:空表
        List<LiveChannelItem> denied = LiveChannelAuth.visibleChannels(groups, new ArrayList<>(), 0, channels);
        assertTrue(denied.isEmpty());
        // 已确认:原引用
        List<Integer> confirmed = new ArrayList<>();
        confirmed.add(0);
        assertSame(channels, LiveChannelAuth.visibleChannels(groups, confirmed, 0, channels));
        assertEquals("央视1", channels.get(0).getChannelName());
    }

    @Test
    public void visibleChannels_unprotectedGroupPassesThrough() {
        List<LiveChannelGroup> groups = new ArrayList<>();
        groups.add(group(0, ""));
        ArrayList<LiveChannelItem> channels = new ArrayList<>();
        groups.get(0).setLiveChannels(channels);
        assertSame(channels, LiveChannelAuth.visibleChannels(groups, new ArrayList<>(), 0, channels));
    }

    @Test
    public void firstOpenGroup_returnsFirstUnprotected() {
        List<LiveChannelGroup> groups = new ArrayList<>();
        groups.add(group(0, "1234"));
        groups.add(group(1, ""));
        groups.add(group(2, "pwd"));
        assertEquals(1, LiveChannelAuth.firstOpenGroup(groups));
        assertEquals(-1, LiveChannelAuth.firstOpenGroup(new ArrayList<>()));
        assertEquals(-1, LiveChannelAuth.firstOpenGroup(null));
    }

    // ── LiveChannelNav: 组/频道上下导航(3 组:0/2 无密码各 3 频道,1 加密) ──
    // channelCount: group0=3, group1(加密) 无频道, group2=2; 加密组 group1 不入可见频道数
    private static java.util.function.IntUnaryOperator CH =
            g -> g == 1 ? 0 : (g == 2 ? 2 : 3);
    private static java.util.function.IntPredicate LOCKED =
            g -> g == 1;

    @Test
    public void next_withinGroupIncrementsAndWraps() {
        // 组内 0→1→2→回卷(非跨组越界仍留本组)
        int[] r = LiveChannelNav.next(1, 3, CH, LOCKED, 0, 1, true);
        assertEquals(0, r[0]);
        assertEquals(2, r[1]);
    }

    @Test
    public void next_crossGroupSkipsLocked() {
        // group0 最后一频道 +1 → 越过加密 group1 到 group2 频道 0
        int[] r = LiveChannelNav.next(1, 3, CH, LOCKED, 0, 2, true);
        assertEquals(2, r[0]);
        assertEquals(0, r[1]);
    }

    @Test
    public void next_previousWrapsToTailOfPreviousOpenGroup() {
        // group2 频道 0 的 -1 → group0 最后一频道(2),跳过加密 group1
        int[] r = LiveChannelNav.next(-1, 3, CH, LOCKED, 2, 0, true);
        assertEquals(0, r[0]);
        assertEquals(2, r[1]);
    }

    @Test
    public void next_noCrossGroupKeepsGroup() {
        int[] r = LiveChannelNav.next(1, 3, CH, LOCKED, 0, 2, false);
        assertEquals(0, r[0]);
        assertEquals(0, r[1]); // 组内回卷
    }

    @Test
    public void next_allLockedStaysOnCurrentGroup() {
        // 全部加密:跨组无目标 → 兜底停当前组(防死循环)
        int[] r = LiveChannelNav.next(1, 1, CH, LOCKED, 0, 2, true);
        assertEquals(0, r[0]);
    }

    private static void assertNext(int[] expected, int direction, int[] counts, boolean[] locked,
                                   int currentGroup, int currentChannel, boolean crossGroup) {
        int[] calls = new int[2];
        int[] actual = LiveChannelNav.next(direction, counts.length, g -> {
            assertTrue("channelCount group index", g >= 0 && g < counts.length);
            assertTrue("channelCount traversal must be bounded", ++calls[0] <= counts.length);
            return counts[g];
        }, g -> {
            assertTrue("groupLocked group index", g >= 0 && g < locked.length);
            assertTrue("groupLocked traversal must be bounded", ++calls[1] <= counts.length);
            return locked[g];
        }, currentGroup, currentChannel, crossGroup);
        assertArrayEquals(expected, actual);
    }

    @Test
    public void next_noGroupsReturnsMissingWithoutCallingCallbacks() {
        for (int groupCount : new int[]{0, -1}) {
            for (int direction : new int[]{1, -1}) {
                for (boolean crossGroup : new boolean[]{true, false}) {
                    assertArrayEquals(new int[]{-1, -1}, LiveChannelNav.next(direction, groupCount,
                            g -> { throw new AssertionError("channelCount must not be called"); },
                            g -> { throw new AssertionError("groupLocked must not be called"); },
                            7, 2, crossGroup));
                }
            }
        }
    }

    @Test
    public void next_staleGroupSearchesOnlyValidNonEmptyOpenGroups() {
        int[] counts = {0, 3, 2};
        boolean[] locked = {false, false, true};
        for (int currentGroup : new int[]{-1, 3, 99, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertNext(new int[]{1, 0}, 1, counts, locked, currentGroup, 8, true);
            assertNext(new int[]{1, 2}, -1, counts, locked, currentGroup, 8, true);
        }
    }

    @Test
    public void next_staleGroupWithoutCrossGroupDoesNotCallCallbacks() {
        for (int direction : new int[]{1, -1}) {
            assertArrayEquals(new int[]{-1, -1}, LiveChannelNav.next(direction, 2,
                    g -> { throw new AssertionError("channelCount must not be called"); },
                    g -> { throw new AssertionError("groupLocked must not be called"); },
                    2, 0, false));
        }
    }

    @Test
    public void next_crossGroupSkipsEmptyAndLockedGroupsInBothDirections() {
        int[] counts = {2, 0, 4, 3, 0};
        boolean[] locked = {false, false, true, false, false};
        assertNext(new int[]{3, 0}, 1, counts, locked, 0, 1, true);
        assertNext(new int[]{0, 1}, -1, counts, locked, 3, 0, true);
        // 穿过列表边界也跳过空组。
        assertNext(new int[]{0, 0}, 1, counts, locked, 3, 2, true);
        assertNext(new int[]{3, 2}, -1, counts, locked, 0, 0, true);
    }

    @Test
    public void next_emptyCurrentGroupRecoversOnlyWhenCrossGroupEnabled() {
        int[] counts = {0, 3, 0};
        boolean[] locked = {false, false, false};
        assertNext(new int[]{1, 0}, 1, counts, locked, 0, 0, true);
        assertNext(new int[]{1, 2}, -1, counts, locked, 0, 0, true);
        assertNext(new int[]{-1, -1}, 1, counts, locked, 0, 0, false);
        assertNext(new int[]{-1, -1}, -1, counts, locked, 0, 0, false);
    }

    @Test
    public void next_noUsableGroupReturnsMissingWithBoundedTraversal() {
        for (int direction : new int[]{1, -1}) {
            for (int currentGroup : new int[]{0, 9}) {
                assertNext(new int[]{-1, -1}, direction, new int[]{0, 0},
                        new boolean[]{false, false}, currentGroup, 0, true);
                assertNext(new int[]{-1, -1}, direction, new int[]{0, 2, 0},
                        new boolean[]{false, true, false}, currentGroup, 0, true);
            }
            assertNext(new int[]{-1, -1}, direction, new int[]{2, 3},
                    new boolean[]{true, true}, 9, 0, true);
        }
    }

    @Test
    public void next_noOtherUsableGroupWrapsWithinCurrentGroup() {
        int[] counts = {3, 0, 2};
        boolean[] locked = {false, false, true};
        assertNext(new int[]{0, 0}, 1, counts, locked, 0, 2, true);
        assertNext(new int[]{0, 2}, -1, counts, locked, 0, 0, true);
    }

    @Test
    public void next_singleGroupWrapsInBothDirections() {
        for (int count : new int[]{1, 3}) {
            for (boolean crossGroup : new boolean[]{true, false}) {
                assertNext(new int[]{0, 0}, 1, new int[]{count}, new boolean[]{false},
                        0, count - 1, crossGroup);
                assertNext(new int[]{0, count - 1}, -1, new int[]{count}, new boolean[]{false},
                        0, 0, crossGroup);
            }
        }
    }

    @Test
    public void next_groupOnlyModeNeverEntersAvailableNeighbor() {
        int[] counts = {3, 2};
        boolean[] locked = {false, false};
        assertNext(new int[]{0, 0}, 1, counts, locked, 0, 2, false);
        assertNext(new int[]{0, 2}, -1, counts, locked, 0, 0, false);
    }

    @Test
    public void next_protectedCurrentGroupWithVisibleChannelsStillNavigatesAndWraps() {
        int[] counts = {3, 2};
        boolean[] locked = {true, true};
        assertNext(new int[]{0, 2}, 1, counts, locked, 0, 1, true);
        assertNext(new int[]{0, 0}, 1, counts, locked, 0, 2, true);
        assertNext(new int[]{0, 2}, -1, counts, locked, 0, 0, true);
    }

    @Test
    public void next_staleChannelRecoversWithinCurrentGroup() {
        for (int currentChannel : new int[]{-1, 3, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertNext(new int[]{0, 0}, 1, new int[]{3, 2}, new boolean[]{false, false},
                    0, currentChannel, true);
            assertNext(new int[]{0, 2}, -1, new int[]{3, 2}, new boolean[]{false, false},
                    0, currentChannel, true);
        }
    }

    private static LiveChannelGroup namedGroup(int oldIndex, String name, String... channelNames) {
        LiveChannelGroup result = group(oldIndex, "");
        result.setGroupName(name);
        ArrayList<LiveChannelItem> channels = new ArrayList<>();
        for (String channelName : channelNames) {
            LiveChannelItem channel = new LiveChannelItem();
            channel.setChannelName(channelName);
            channel.setChannelIndex(100 + channels.size()); // 故意保留不同于列表位置的旧索引。
            channels.add(channel);
        }
        result.setLiveChannels(channels);
        return result;
    }

    @Test
    public void findChannel_reorderedGroupsPreferSameGroupAndReturnListPositions() {
        LiveChannelGroup preferred = namedGroup(12, "央视", "央视1", "新闻");
        LiveChannelGroup duplicate = namedGroup(8, "其他", "新闻");
        List<LiveChannelGroup> groups = Arrays.asList(duplicate, preferred);
        assertArrayEquals(new int[]{1, 1}, LiveChannelNav.findChannel(groups, "央视", "新闻"));

        Collections.swap(groups, 0, 1);
        Collections.swap(preferred.getLiveChannels(), 0, 1);
        assertArrayEquals(new int[]{0, 0}, LiveChannelNav.findChannel(groups, "央视", "新闻"));
        assertEquals(12, preferred.getGroupIndex());
        assertEquals(101, preferred.getLiveChannels().get(0).getChannelIndex());
    }

    @Test
    public void findChannel_removedGroupFallsBackToFirstMatchingChannel() {
        List<LiveChannelGroup> groups = new ArrayList<>(Arrays.asList(
                namedGroup(7, "其他", "电影", "新闻"),
                namedGroup(9, "央视", "新闻"),
                namedGroup(11, "地方", "新闻")));
        assertArrayEquals(new int[]{1, 0}, LiveChannelNav.findChannel(groups, "央视", "新闻"));
        groups.remove(1);
        assertArrayEquals(new int[]{0, 1}, LiveChannelNav.findChannel(groups, "央视", "新闻"));
        assertArrayEquals(new int[]{0, 1}, LiveChannelNav.findChannel(groups, null, "新闻"));
    }

    @Test
    public void findChannel_sameGroupWithoutChannelFallsBackAcrossGroups() {
        List<LiveChannelGroup> groups = Arrays.asList(
                namedGroup(3, "央视", "电影"), namedGroup(5, "其他", "新闻"));
        assertArrayEquals(new int[]{1, 0}, LiveChannelNav.findChannel(groups, "央视", "新闻"));
    }

    @Test
    public void findChannel_missingChannelAndEmptyListsReturnMissing() {
        assertArrayEquals(new int[]{-1, -1},
                LiveChannelNav.findChannel(new ArrayList<>(), "央视", "新闻"));
        List<LiveChannelGroup> groups = Arrays.asList(
                group(4, ""), namedGroup(6, "空组"), namedGroup(8, "央视", "电影"));
        assertArrayEquals(new int[]{-1, -1}, LiveChannelNav.findChannel(groups, "央视", "新闻"));
        assertArrayEquals(new int[]{-1, -1}, LiveChannelNav.findChannel(groups, "央视", null));
        assertArrayEquals(new int[]{2, 0}, LiveChannelNav.findChannel(groups, "央视", "电影"));
    }
}
