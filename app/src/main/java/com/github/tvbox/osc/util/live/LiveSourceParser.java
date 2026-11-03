package com.github.tvbox.osc.util.live;

import android.os.Handler;

import com.github.tvbox.osc.util.AppLog;
import com.google.gson.JsonArray;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 直播源解析(后台线程)。
 * <p>
 * {@code HttpClient} 的成功回调是投递到主线程的,直接在里面解析 900+ 个频道 + 重新分组,
 * 会把主线程占住几百毫秒 —— 换台/自动刷新正好撞上就是明显"卡一下",网络慢时还可能 ANR。
 * 这里把手上的活挪到单线程池,干完再回主线程交给调用方。
 */
public final class LiveSourceParser {

    /** 解析结果:原始分组留一份用于落缓存,分组后的用于 loadLives */
    public static final class Result {
        public final JsonArray raw;
        public final JsonArray grouped;

        Result(JsonArray raw, JsonArray grouped) {
            this.raw = raw;
            this.grouped = grouped;
        }
    }

    public interface Callback {
        /** 失败时 result 为 null */
        void onParsed(Result result);
    }

    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "live-source-parse");
        t.setDaemon(true);
        return t;
    });

    private LiveSourceParser() {
    }

    public static void parseAsync(final String content, final Handler main, final Callback callback) {
        POOL.execute(() -> {
            Result result = null;
            try {
                LinkedHashMap<String, LinkedHashMap<String, ArrayList<String>>> map = new LinkedHashMap<>();
                TxtSubscribe.parse(map, content);
                JsonArray raw = TxtSubscribe.live2JsonArray(map);
                result = new Result(raw, LiveChannelGrouping.regroup(raw));
            } catch (Throwable th) {
                AppLog.log("直播", "解析直播源失败: " + th.getMessage());
            }
            final Result r = result;
            main.post(() -> callback.onParsed(r));
        });
    }
}
