package com.github.tvbox.osc.util.live;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 直播分组规整:把上游那份频道表重新排成固定顺序,并把混在一起的频道按归属拆开。
 * <p>
 * 上游(wnhhh/iptv-list)的格式会变,两种都兼容:
 * <ul>
 *   <li>旧版:所有地方台塞在一个"地方频道"里 —— 逐台按地名分类</li>
 *   <li>新版:上游自己按省分组(广东/香港/北京…),但顺序不对,而且"其他/国内其他"里
 *       混着境外台 —— 逐组归位,混装桶再逐台分类</li>
 * </ul>
 * 最终顺序:央视 -> 卫视 -> 广东 -> 香港 -> 原有其余分组 -> 其余各省(行政区顺序)
 * -> 国内其他 -> 各国(台数降序) -> 境外其他。
 * <p>
 * 已经按省分好的组不重切(只归位);景区/慢直播按需求丢弃。
 */
public final class LiveChannelGrouping {

    /** 旧格式里那个大杂烩分组的特征 */
    private static final String LOCAL_GROUP_KEY = "地方";
    /** 排在所有分组最前的两个上游分组 */
    private static final String HEAD_GROUP_CCTV = "央视";
    private static final String HEAD_GROUP_SAT = "卫视";
    /** 置顶省份:排在"原有其余分组"之前 */
    private static final String[] PINNED_PROVINCES = {"广东", "香港"};
    /** 上游混装桶:里面的台要逐个判断归属 */
    private static final String[] MIXED_GROUPS = {"其他", "国内其他", "地方频道"};
    /** 上游脏分组:内容是"更新时间"那行被误当成频道 */
    private static final String JUNK_GROUP = "更新时间";

    public static final String GROUP_DOMESTIC_OTHER = "国内其他";
    public static final String GROUP_FOREIGN_OTHER = "境外其他";

    /** 省级行政区,数组顺序 = 直播页分组展示顺序 */
    private static final String[] PROVINCES = {
            "北京", "天津", "河北", "山西", "内蒙古", "辽宁", "吉林", "黑龙江",
            "上海", "江苏", "浙江", "安徽", "福建", "江西", "山东", "河南",
            "湖北", "湖南", "广东", "广西", "海南", "重庆", "四川", "贵州",
            "云南", "西藏", "陕西", "甘肃", "青海", "宁夏", "新疆", "香港",
            "澳门", "台湾"
    };

    /** 地级市/县级市 -> 省,格式 "地名|省" */
    private static final String[] CITY = {
            "石家庄|河北", "唐山|河北", "秦皇岛|河北", "邯郸|河北",
            "邢台|河北", "保定|河北", "张家口|河北", "承德|河北",
            "沧州|河北", "廊坊|河北", "衡水|河北", "任丘|河北",
            "滦平|河北", "昌黎|河北", "平泉|河北", "清河|河北",
            "太原|山西", "大同|山西", "阳泉|山西", "长治|山西",
            "晋城|山西", "朔州|山西", "晋中|山西", "运城|山西",
            "忻州|山西", "临汾|山西", "吕梁|山西", "太谷|山西",
            "定襄|山西", "怀仁|山西", "汾西|山西", "长子|山西",
            "古县|山西", "大宁|山西", "呼和浩特|内蒙古", "包头|内蒙古",
            "乌海|内蒙古", "赤峰|内蒙古", "通辽|内蒙古", "鄂尔多斯|内蒙古",
            "呼伦贝尔|内蒙古", "巴彦淖尔|内蒙古", "乌兰察布|内蒙古", "沈阳|辽宁",
            "大连|辽宁", "鞍山|辽宁", "抚顺|辽宁", "本溪|辽宁",
            "丹东|辽宁", "锦州|辽宁", "营口|辽宁", "阜新|辽宁",
            "辽阳|辽宁", "盘锦|辽宁", "铁岭|辽宁", "朝阳|辽宁",
            "葫芦岛|辽宁", "长春|吉林", "吉林|吉林", "四平|吉林",
            "辽源|吉林", "通化|吉林", "白山|吉林", "松原|吉林",
            "白城|吉林", "延边|吉林", "延吉|吉林", "九台|吉林",
            "双辽|吉林", "东丰|吉林", "前郭|吉林", "敦化|吉林",
            "德惠|吉林", "柳河|吉林", "桦甸|吉林", "梅河口|吉林",
            "汪清|吉林", "舒兰|吉林", "辉南|吉林", "靖宇|吉林",
            "龙井|吉林", "磐石|吉林", "集安|吉林", "哈尔滨|黑龙江",
            "齐齐哈尔|黑龙江", "鸡西|黑龙江", "鹤岗|黑龙江", "双鸭山|黑龙江",
            "大庆|黑龙江", "伊春|黑龙江", "佳木斯|黑龙江", "七台河|黑龙江",
            "牡丹江|黑龙江", "黑河|黑龙江", "绥化|黑龙江", "甘南|黑龙江",
            "南京|江苏", "无锡|江苏", "徐州|江苏", "常州|江苏",
            "苏州|江苏", "南通|江苏", "连云港|江苏", "淮安|江苏",
            "盐城|江苏", "扬州|江苏", "镇江|江苏", "泰州|江苏",
            "宿迁|江苏", "常熟|江苏", "新沂|江苏", "沭阳|江苏",
            "泗洪|江苏", "涟水|江苏", "句容|江苏", "靖江|江苏",
            "武进|江苏", "杭州|浙江", "宁波|浙江", "温州|浙江",
            "嘉兴|浙江", "湖州|浙江", "绍兴|浙江", "金华|浙江",
            "衢州|浙江", "舟山|浙江", "台州|浙江", "丽水|浙江",
            "上虞|浙江", "嵊州|浙江", "嵊泗|浙江", "萧山|浙江",
            "诸暨|浙江", "海宁|浙江", "平湖|浙江", "缙云|浙江",
            "象山|浙江", "龙游|浙江", "青田|浙江", "龙泉|浙江",
            "云和|浙江", "松阳|浙江", "衢江|浙江", "合肥|安徽",
            "芜湖|安徽", "蚌埠|安徽", "淮南|安徽", "马鞍山|安徽",
            "淮北|安徽", "铜陵|安徽", "安庆|安徽", "滁州|安徽",
            "阜阳|安徽", "宿州|安徽", "六安|安徽", "亳州|安徽",
            "池州|安徽", "宣城|安徽", "固镇|安徽", "广德|安徽",
            "祁门|安徽", "福州|福建", "厦门|福建", "莆田|福建",
            "三明|福建", "泉州|福建", "漳州|福建", "南平|福建",
            "龙岩|福建", "宁德|福建", "晋江|福建", "云霄|福建",
            "南昌|江西", "景德镇|江西", "萍乡|江西", "九江|江西",
            "新余|江西", "鹰潭|江西", "赣州|江西", "吉安|江西",
            "宜春|江西", "抚州|江西", "上饶|江西", "济南|山东",
            "青岛|山东", "淄博|山东", "枣庄|山东", "东营|山东",
            "烟台|山东", "潍坊|山东", "济宁|山东", "泰安|山东",
            "威海|山东", "日照|山东", "临沂|山东", "德州|山东",
            "聊城|山东", "滨州|山东", "菏泽|山东", "郑州|河南",
            "开封|河南", "洛阳|河南", "平顶山|河南", "安阳|河南",
            "鹤壁|河南", "新乡|河南", "焦作|河南", "濮阳|河南",
            "许昌|河南", "漯河|河南", "三门峡|河南", "南阳|河南",
            "商丘|河南", "信阳|河南", "周口|河南", "驻马店|河南",
            "新野|河南", "永城|河南", "荥阳|河南", "武汉|湖北",
            "黄石|湖北", "十堰|湖北", "宜昌|湖北", "襄阳|湖北",
            "鄂州|湖北", "荆门|湖北", "孝感|湖北", "荆州|湖北",
            "黄冈|湖北", "咸宁|湖北", "随州|湖北", "江夏|湖北",
            "长沙|湖南", "株洲|湖南", "湘潭|湖南", "衡阳|湖南",
            "邵阳|湖南", "岳阳|湖南", "常德|湖南", "益阳|湖南",
            "郴州|湖南", "永州|湖南", "怀化|湖南", "娄底|湖南",
            "湘西|湖南", "广州|广东", "韶关|广东", "深圳|广东",
            "珠海|广东", "汕头|广东", "佛山|广东", "江门|广东",
            "湛江|广东", "茂名|广东", "肇庆|广东", "惠州|广东",
            "梅州|广东", "汕尾|广东", "河源|广东", "阳江|广东",
            "清远|广东", "东莞|广东", "中山|广东", "潮州|广东",
            "揭阳|广东", "云浮|广东", "番禺|广东", "南宁|广西",
            "柳州|广西", "桂林|广西", "梧州|广西", "北海|广西",
            "防城港|广西", "钦州|广西", "贵港|广西", "玉林|广西",
            "百色|广西", "贺州|广西", "河池|广西", "来宾|广西",
            "崇左|广西", "宾阳|广西", "灌阳|广西", "海口|海南",
            "三亚|海南", "三沙|海南", "儋州|海南", "成都|四川",
            "自贡|四川", "攀枝花|四川", "泸州|四川", "德阳|四川",
            "绵阳|四川", "广元|四川", "遂宁|四川", "内江|四川",
            "乐山|四川", "南充|四川", "眉山|四川", "宜宾|四川",
            "广安|四川", "达州|四川", "雅安|四川", "巴中|四川",
            "资阳|四川", "阿坝|四川", "甘孜|四川", "凉山|四川",
            "乐至|四川", "井研|四川", "仁寿|四川", "剑阁|四川",
            "名山|四川", "夹江|四川", "旺苍|四川", "朝天|四川",
            "松潘|四川", "汶川|四川", "荥经|四川", "青川|四川",
            "金川|四川", "贵阳|贵州", "六盘水|贵州", "遵义|贵州",
            "安顺|贵州", "毕节|贵州", "铜仁|贵州", "昆明|云南",
            "曲靖|云南", "玉溪|云南", "保山|云南", "昭通|云南",
            "丽江|云南", "普洱|云南", "临沧|云南", "楚雄|云南",
            "红河|云南", "文山|云南", "大理|云南", "西双版纳|云南",
            "德宏|云南", "怒江|云南", "迪庆|云南", "易门|云南",
            "通海|云南", "拉萨|西藏", "日喀则|西藏", "昌都|西藏",
            "林芝|西藏", "山南|西藏", "那曲|西藏", "西安|陕西",
            "铜川|陕西", "宝鸡|陕西", "咸阳|陕西", "渭南|陕西",
            "延安|陕西", "汉中|陕西", "榆林|陕西", "安康|陕西",
            "商洛|陕西", "兰州|甘肃", "嘉峪关|甘肃", "金昌|甘肃",
            "白银|甘肃", "天水|甘肃", "武威|甘肃", "张掖|甘肃",
            "平凉|甘肃", "酒泉|甘肃", "庆阳|甘肃", "定西|甘肃",
            "陇南|甘肃", "临夏|甘肃", "渭源|甘肃", "秦安|甘肃",
            "西宁|青海", "海东|青海", "海北|青海", "黄南|青海",
            "果洛|青海", "玉树|青海", "海西|青海", "银川|宁夏",
            "石嘴山|宁夏", "吴忠|宁夏", "固原|宁夏", "中卫|宁夏",
            "乌鲁木齐|新疆", "克拉玛依|新疆", "吐鲁番|新疆", "哈密|新疆",
            "伊犁|新疆", "阿克苏|新疆", "喀什|新疆", "和田|新疆",
            "塔城|新疆", "阿勒泰|新疆", "石河子|新疆", "玛纳斯|新疆",
            "奎屯|新疆", "库尔勒|新疆", "昌吉|新疆", "伊宁|新疆",
            "万州|重庆", "铜梁|重庆", "荣昌|重庆", "江津|重庆",
            "涪陵|重庆", "永川|重庆", "合川|重庆", "綦江|重庆",
            "大足|重庆", "璧山|重庆", "开州|重庆", "云阳|重庆",
            "奉节|重庆", "巫山|重庆", "滨海|天津", "津南|天津",
            "武清|天津", "宝坻|天津", "蓟州|天津", "静海|天津",
            "宁河|天津"
    };

    /** 境外国家 -> 关键词,格式 "国家:关键词,关键词" */
    private static final String[] COUNTRY = {
            "日本:日本,NHK,富士,朝日,东京,日テレ", "韩国:韩国,KBS,MBC,SBS,tvN,EBS,Arirang,CJ OnStyle,Lotte OneTV,tvchosun,GCN,KPop,BBS,CPBC,BTN,CJB,Momo,NewKID,ET Mall",
            "朝鲜:朝鲜,KCTV", "美国:美国,CNN,ABC News,NBC,CBS,Newsmax,HBO,Fox,PBS,Bloomberg",
            "英国:英国,BBC,ITV,Sky News", "德国:德国,DW,ZDF,ARD",
            "法国:法国,France 24,TV5", "俄罗斯:俄罗斯,RT ",
            "西班牙:西班牙,RTVE,TVE", "意大利:意大利,RAI",
            "葡萄牙:葡萄牙,RTP", "荷兰:荷兰",
            "比利时:比利时", "瑞士:瑞士",
            "奥地利:奥地利,Red Bull TV", "瑞典:瑞典",
            "挪威:挪威", "丹麦:丹麦",
            "芬兰:芬兰", "波兰:波兰",
            "捷克:捷克", "希腊:希腊",
            "土耳其:土耳其", "印度:印度,WionNews",
            "巴基斯坦:巴基斯坦", "越南:越南",
            "泰国:泰国", "新加坡:新加坡,CNA",
            "马来西亚:马来西亚,TV3", "印度尼西亚:印尼,ANTV",
            "菲律宾:菲律宾", "澳大利亚:澳大利亚,澳洲",
            "新西兰:新西兰", "加拿大:加拿大,CBC",
            "巴西:巴西", "阿根廷:阿根廷",
            "墨西哥:墨西哥", "南非:南非",
            "埃及:埃及", "阿联酋:阿联酋,迪拜",
            "沙特:沙特", "以色列:以色列",
            "伊朗:伊朗", "乌克兰:乌克兰",
    };

    /** 拼音/英文台名 -> 省(或"其他") */
    private static final String[] PINYIN = {
            "Anhui:安徽", "Hebei:河北", "Hunan:湖南", "Jiangsu:江苏",
            "Guangxi:广西", "Xinjiang:新疆", "Xizang:西藏", "Nei Monggol:内蒙古",
            "Chaozhou:广东", "Chifeng:内蒙古", "Chuxiong:云南", "Dongguan:广东",
            "Harbin:黑龙江", "Jilin:吉林", "Jingmen:湖北", "Lanzhou:甘肃",
            "Siping:吉林", "Tonghua:吉林", "QTV:山东", "Kangba:四川",
            "Taiwan:台湾", "CETV:其他", "China:其他"
    };

    /** 港台地区特征台名 -> 省 */
    private static final String[] TWHK = {
            "台湾:中视,华视,台视,民视,TVBS,大立,中天,东森,三立,公视,八大,CTi", "香港:翡翠台,明珠台,TVB,凤凰,亚视,香港开电视,Now TV,有线电视",
            "澳门:澳广视,澳门"
    };

    /** 景区/慢直播特征词(通用词 + 地标 + 动物直播):命中即丢弃 */
    private static final String[] SCENIC = {
            "景区", "风景区", "雪山", "冰川", "古城", "古镇",
            "瀑布", "云海", "观景", "鸟瞰", "远眺", "全景",
            "栈道", "日出", "牌坊", "花巷", "大佛", "长城",
            "天池", "梯田", "草原", "沙漠", "湿地", "峡谷",
            "海岛", "沙滩", "航拍", "慢直播", "直播中国", "公园",
            "丹霞", "丽江古城", "泰山", "黄山", "普陀山", "恒山",
            "黄龙", "五彩池", "月牙泉", "鸣沙山", "天涯石", "都江堰",
            "乌镇", "雪乡", "黄果树", "黄花城", "八里沟", "六盘山",
            "九华山", "峨眉山", "四姑娘山", "张家界", "漓江", "大容山",
            "牛首山", "云龙湖", "崂山", "万峰林", "鼓浪屿", "玄武湖",
            "东极岛", "翡翠湾", "玉女峰", "莲花山顶", "西递", "芦村",
            "崇圣寺", "蓝月谷", "玉龙雪山", "东极", "望乡台", "熊猫",
            "白狮", "朱鹮", "野生", "动物"
    };

    private LiveChannelGrouping() {
    }

    /** 重新排序并归类 */
    public static JsonArray regroup(JsonArray lives) {
        if (lives == null || lives.size() == 0) return lives;

        List<JsonObject> head = new ArrayList<>();          // 央视 / 卫视
        List<JsonObject> rest = new ArrayList<>();          // 体育/电影/少儿…原样透传
        Map<String, List<JsonObject>> provinces = new LinkedHashMap<>();
        Map<String, List<JsonObject>> countries = new LinkedHashMap<>();
        List<JsonObject> domesticOther = new ArrayList<>();
        List<JsonObject> foreignOther = new ArrayList<>();

        for (int i = 0; i < lives.size(); i++) {
            JsonObject group = lives.get(i).getAsJsonObject();
            String name = group.has("group") ? group.get("group").getAsString() : "";
            JsonArray channels = group.has("channels") ? group.getAsJsonArray("channels") : null;

            if (name.contains(JUNK_GROUP)) continue;         // "更新时间"这种脏分组整组丢掉

            if (name.contains(HEAD_GROUP_CCTV) || name.contains(HEAD_GROUP_SAT)) {
                head.add(group);
                continue;
            }
            String province = nameIn(PROVINCES, name);
            if (province != null) {
                addChannels(provinces, province, channels);
                continue;
            }
            String country = nameIn(COUNTRY, name);
            if (country != null) {
                addChannels(countries, country, channels);
                continue;
            }
            if (isMixedGroup(name)) {
                classifyInto(channels, provinces, countries, domesticOther, foreignOther);
                continue;
            }
            rest.add(group);
        }

        JsonArray out = new JsonArray();
        for (JsonObject g : head) out.add(g);                                  // 央视 / 卫视
        for (String p : PINNED_PROVINCES) addGroup(out, provinces, p);          // 广东 / 香港
        for (JsonObject g : rest) out.add(g);                                  // 原有其余分组
        for (String p : PROVINCES) {                                           // 其余各省
            if (isPinned(p)) continue;
            addGroup(out, provinces, p);
        }
        if (!domesticOther.isEmpty()) out.add(newGroup(GROUP_DOMESTIC_OTHER, domesticOther));
        List<Map.Entry<String, List<JsonObject>>> entries = new ArrayList<>(countries.entrySet());
        entries.sort((a, b) -> b.getValue().size() - a.getValue().size());
        for (Map.Entry<String, List<JsonObject>> e : entries) {
            out.add(newGroup(e.getKey(), e.getValue()));
        }
        if (!foreignOther.isEmpty()) out.add(newGroup(GROUP_FOREIGN_OTHER, foreignOther));
        return out;
    }

    private static boolean isPinned(String province) {
        for (String p : PINNED_PROVINCES) if (p.equals(province)) return true;
        return false;
    }

    // ------------------------------------------------------------------
    // 装配
    // ------------------------------------------------------------------

    private static void addGroup(JsonArray out, Map<String, List<JsonObject>> map, String name) {
        List<JsonObject> list = map.get(name);
        if (list != null && !list.isEmpty()) out.add(newGroup(name, list));
    }

    private static void addChannels(Map<String, List<JsonObject>> map, String key, JsonArray channels) {
        if (channels == null) return;
        List<JsonObject> list = map.get(key);
        if (list == null) {
            list = new ArrayList<>();
            map.put(key, list);
        }
        for (JsonElement c : channels) {
            if (c == null || !c.isJsonObject()) continue;
            JsonObject channel = c.getAsJsonObject();
            if (isTimestamp(channelName(channel))) continue;  // 上游把"更新时间"也当成频道塞了进来
            list.add(channel);
        }
    }

    /** 混装桶:逐个台判断归属 */
    private static void classifyInto(JsonArray channels,
                                     Map<String, List<JsonObject>> provinces,
                                     Map<String, List<JsonObject>> countries,
                                     List<JsonObject> domesticOther,
                                     List<JsonObject> foreignOther) {
        if (channels == null) return;
        for (JsonElement c : channels) {
            if (c == null || !c.isJsonObject()) continue;
            JsonObject channel = c.getAsJsonObject();
            String name = channelName(channel);
            if (isTimestamp(name)) continue;
            String tag = classify(name);
            if (tag == null) continue;                        // 景区/慢直播:丢弃
            if (GROUP_DOMESTIC_OTHER.equals(tag)) {
                domesticOther.add(channel);
            } else if (GROUP_FOREIGN_OTHER.equals(tag)) {
                foreignOther.add(channel);
            } else if (isProvince(tag)) {
                addOne(provinces, tag, channel);
            } else {
                addOne(countries, tag, channel);
            }
        }
    }

    private static void addOne(Map<String, List<JsonObject>> map, String key, JsonObject channel) {
        List<JsonObject> list = map.get(key);
        if (list == null) {
            list = new ArrayList<>();
            map.put(key, list);
        }
        list.add(channel);
    }

    private static JsonObject newGroup(String name, List<JsonObject> channels) {
        JsonObject group = new JsonObject();
        group.addProperty("group", name);
        JsonArray arr = new JsonArray();
        for (JsonObject c : channels) arr.add(c);
        group.add("channels", arr);
        return group;
    }

    // ------------------------------------------------------------------
    // 判定
    // ------------------------------------------------------------------

    private static String channelName(JsonObject channel) {
        return channel.has("name") ? channel.get("name").getAsString() : "";
    }

    /** 上游把 "#更新时间 ..." 这行当成了频道,名字长这样:2026-11-03 14:12:17 */
    private static boolean isTimestamp(String name) {
        if (name == null || name.length() < 10) return false;
        for (int i = 0; i < 10; i++) {
            char c = name.charAt(i);
            if (i == 4 || i == 7) {
                if (c != '-') return false;
            } else if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    private static boolean isMixedGroup(String name) {
        for (String m : MIXED_GROUPS) if (m.equals(name)) return true;
        return name.contains(LOCAL_GROUP_KEY);
    }

    /** 分组名正好是省名/国家名时返回它,否则 null */
    private static String nameIn(String[] table, String name) {
        for (String row : table) {
            String key = row;
            int bar = key.indexOf('|');
            if (bar > 0) key = key.substring(0, bar);
            int colon = key.indexOf(':');
            if (colon > 0) key = key.substring(0, colon);
            if (key.equals(name)) return key;
        }
        return null;
    }

    private static boolean isProvince(String tag) {
        for (String p : PROVINCES) if (p.equals(tag)) return true;
        return false;
    }

    /**
     * 单个台名 -> 分组名。
     *
     * @return 省名 / 国家名 / 国内其他 / 境外其他;null 表示"景区慢直播,要丢弃"
     */
    static String classify(String name) {
        if (name == null || name.isEmpty()) return GROUP_DOMESTIC_OTHER;
        // 剧集标题(带"之"的多是《鬼吹灯之xx》)先归其他,别被"云南"抢进省份,也别当景区删掉
        if (name.contains("之")) return hasCjk(name) ? GROUP_DOMESTIC_OTHER : GROUP_FOREIGN_OTHER;
        for (String w : SCENIC) if (name.contains(w)) return null;
        for (String row : COUNTRY) {
            int idx = row.indexOf(':');
            for (String k : row.substring(idx + 1).split(",")) {
                if (!k.isEmpty() && name.contains(k)) return row.substring(0, idx);
            }
        }
        String p = match(name, PROVINCES, 1);
        if (p != null) return p;
        p = match(name, CITY, 3);
        if (p != null) return p;
        String low = name.toLowerCase();
        for (String row : PINYIN) {
            int idx = row.indexOf(':');
            if (low.contains(row.substring(0, idx).toLowerCase())) {
                String v = row.substring(idx + 1);
                return "其他".equals(v) ? GROUP_DOMESTIC_OTHER : v;
            }
        }
        for (String row : TWHK) {
            int idx = row.indexOf(':');
            for (String k : row.substring(idx + 1).split(",")) {
                if (!k.isEmpty() && name.contains(k)) return row.substring(0, idx);
            }
        }
        return hasCjk(name) ? GROUP_DOMESTIC_OTHER : GROUP_FOREIGN_OTHER;
    }

    /**
     * @param minSubstringLen 键长 &gt;= 这个值才允许出现在名字中间;更短的键必须是开头,
     *                        否则"泰山南天门"会被"山南"这类两字地名劫走。
     */
    private static String match(String name, String[] table, int minSubstringLen) {
        for (String row : table) {
            String key;
            String value;
            int bar = row.lastIndexOf('|');
            if (bar > 0) {
                key = row.substring(0, bar);
                value = row.substring(bar + 1);
            } else {
                key = row;
                value = row;
            }
            if (key.length() >= minSubstringLen) {
                if (name.contains(key)) return value;
            } else if (name.startsWith(key)) {
                return value;
            }
        }
        return null;
    }

    private static boolean hasCjk(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) >= 0x4E00 && s.charAt(i) <= 0x9FFF) return true;
        }
        return false;
    }
}
