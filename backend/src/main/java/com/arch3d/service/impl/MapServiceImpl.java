package com.arch3d.service.impl;

import com.arch3d.client.DeepSeekClient;
import com.arch3d.entity.BuildingModel;
import com.arch3d.repository.BuildingModelRepository;
import com.arch3d.service.MapService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MapServiceImpl implements MapService {
    private final BuildingModelRepository modelRepository;
    private final DeepSeekClient deepSeekClient;

    private static final Map<String, double[]> REGION_CENTER = new LinkedHashMap<>();
    private static final Map<String, String> REGION_NAME = new LinkedHashMap<>();
    private static final String UNKNOWN_REGION = "__unknown__";
    /** 省份正在后台推断的占位值 */
    private static final String PENDING_REGION = "__pending__";
    private final Map<Integer, String> modelRegionCache = new ConcurrentHashMap<>();

    /**
     * 省份 AI 推断专用线程池。
     * 单线程 + 有界队列：AI 接口本身并发能力有限，排队跑比打满更稳；
     * 队列满了就丢任务（丢弃后模型保持未标注），不会堆积拖垮服务。
     */
    private final java.util.concurrent.ExecutorService regionInferenceExecutor =
        new java.util.concurrent.ThreadPoolExecutor(
            1, 1,
            0L, TimeUnit.MILLISECONDS,
            new java.util.concurrent.LinkedBlockingQueue<>(200),
            r -> {
                Thread t = new Thread(r, "region-inference");
                t.setDaemon(true);
                return t;
            },
            new java.util.concurrent.ThreadPoolExecutor.DiscardPolicy()
        );

    static {
        REGION_CENTER.put("beijing", new double[]{116.40, 39.90});
        REGION_CENTER.put("tianjin", new double[]{117.20, 39.12});
        REGION_CENTER.put("hebei", new double[]{114.52, 38.05});
        REGION_CENTER.put("shanxi", new double[]{112.55, 37.87});
        REGION_CENTER.put("neimenggu", new double[]{111.65, 40.82});
        REGION_CENTER.put("liaoning", new double[]{123.43, 41.80});
        REGION_CENTER.put("jilin", new double[]{125.32, 43.90});
        REGION_CENTER.put("heilongjiang", new double[]{126.63, 45.75});
        REGION_CENTER.put("shanghai", new double[]{121.47, 31.23});
        REGION_CENTER.put("jiangsu", new double[]{118.78, 32.07});
        REGION_CENTER.put("zhejiang", new double[]{120.15, 30.28});
        REGION_CENTER.put("anhui", new double[]{117.27, 31.86});
        REGION_CENTER.put("fujian", new double[]{119.30, 26.08});
        REGION_CENTER.put("jiangxi", new double[]{115.85, 28.68});
        REGION_CENTER.put("shandong", new double[]{117.00, 36.67});
        REGION_CENTER.put("henan", new double[]{113.62, 34.75});
        REGION_CENTER.put("hubei", new double[]{114.30, 30.60});
        REGION_CENTER.put("hunan", new double[]{112.93, 28.23});
        REGION_CENTER.put("guangdong", new double[]{113.27, 23.13});
        REGION_CENTER.put("guangxi", new double[]{108.32, 22.82});
        REGION_CENTER.put("hainan", new double[]{110.33, 20.03});
        REGION_CENTER.put("chongqing", new double[]{106.55, 29.56});
        REGION_CENTER.put("sichuan", new double[]{104.07, 30.67});
        REGION_CENTER.put("guizhou", new double[]{106.71, 26.57});
        REGION_CENTER.put("yunnan", new double[]{102.71, 25.04});
        REGION_CENTER.put("xizang", new double[]{91.13, 29.65});
        REGION_CENTER.put("shanxi2", new double[]{108.94, 34.34});
        REGION_CENTER.put("gansu", new double[]{103.73, 36.03});
        REGION_CENTER.put("qinghai", new double[]{101.78, 36.62});
        REGION_CENTER.put("ningxia", new double[]{106.23, 38.48});
        REGION_CENTER.put("xinjiang", new double[]{87.62, 43.82});
        REGION_CENTER.put(UNKNOWN_REGION, new double[]{105.0, 35.0});

        REGION_NAME.put("beijing", "北京市");
        REGION_NAME.put("tianjin", "天津市");
        REGION_NAME.put("hebei", "河北省");
        REGION_NAME.put("shanxi", "山西省");
        REGION_NAME.put("neimenggu", "内蒙古自治区");
        REGION_NAME.put("liaoning", "辽宁省");
        REGION_NAME.put("jilin", "吉林省");
        REGION_NAME.put("heilongjiang", "黑龙江省");
        REGION_NAME.put("shanghai", "上海市");
        REGION_NAME.put("jiangsu", "江苏省");
        REGION_NAME.put("zhejiang", "浙江省");
        REGION_NAME.put("anhui", "安徽省");
        REGION_NAME.put("fujian", "福建省");
        REGION_NAME.put("jiangxi", "江西省");
        REGION_NAME.put("shandong", "山东省");
        REGION_NAME.put("henan", "河南省");
        REGION_NAME.put("hubei", "湖北省");
        REGION_NAME.put("hunan", "湖南省");
        REGION_NAME.put("guangdong", "广东省");
        REGION_NAME.put("guangxi", "广西壮族自治区");
        REGION_NAME.put("hainan", "海南省");
        REGION_NAME.put("chongqing", "重庆市");
        REGION_NAME.put("sichuan", "四川省");
        REGION_NAME.put("guizhou", "贵州省");
        REGION_NAME.put("yunnan", "云南省");
        REGION_NAME.put("xizang", "西藏自治区");
        REGION_NAME.put("shanxi2", "陕西省");
        REGION_NAME.put("gansu", "甘肃省");
        REGION_NAME.put("qinghai", "青海省");
        REGION_NAME.put("ningxia", "宁夏回族自治区");
        REGION_NAME.put("xinjiang", "新疆维吾尔自治区");
        REGION_NAME.put(UNKNOWN_REGION, "未标注地区");
    }

    /**
     * 城市 / 地标 → 省份代码。
     *
     * 数据里的 location 常写成「苏州」「扬州瘦西湖」「江南园林」这种，
     * 光靠省名匹配不上，就会掉进 AI 兜底分支干等 8 秒。
     * 这张表让绝大部分模型在本地一次命中，首页不再卡。
     *
     * 用 LinkedHashMap 保证顺序：更具体的地标放前面，避免被泛化的词抢先匹配。
     */
    private static final Map<String, String> CITY_REGION_HINTS = new LinkedHashMap<>();

    static {
        // 直辖市与华北
        CITY_REGION_HINTS.put("故宫", "beijing");
        CITY_REGION_HINTS.put("颐和园", "beijing");
        CITY_REGION_HINTS.put("北海公园", "beijing");
        CITY_REGION_HINTS.put("天坛", "beijing");
        CITY_REGION_HINTS.put("卢沟桥", "beijing");
        CITY_REGION_HINTS.put("丰台", "beijing");
        CITY_REGION_HINTS.put("海淀", "beijing");
        CITY_REGION_HINTS.put("承德", "hebei");
        CITY_REGION_HINTS.put("避暑山庄", "hebei");
        CITY_REGION_HINTS.put("正定", "hebei");
        CITY_REGION_HINTS.put("赵州", "hebei");
        CITY_REGION_HINTS.put("山海关", "hebei");
        CITY_REGION_HINTS.put("五台山", "shanxi");
        CITY_REGION_HINTS.put("平遥", "shanxi");
        CITY_REGION_HINTS.put("大同", "shanxi");
        CITY_REGION_HINTS.put("应县", "shanxi");
        CITY_REGION_HINTS.put("太原", "shanxi");
        CITY_REGION_HINTS.put("晋祠", "shanxi");
        CITY_REGION_HINTS.put("西安", "shanxi2");
        CITY_REGION_HINTS.put("长安", "shanxi2");
        CITY_REGION_HINTS.put("咸阳", "shanxi2");
        CITY_REGION_HINTS.put("华山", "shanxi2");

        // 华东
        CITY_REGION_HINTS.put("苏州", "jiangsu");
        CITY_REGION_HINTS.put("扬州", "jiangsu");
        CITY_REGION_HINTS.put("瘦西湖", "jiangsu");
        CITY_REGION_HINTS.put("南京", "jiangsu");
        CITY_REGION_HINTS.put("无锡", "jiangsu");
        CITY_REGION_HINTS.put("周庄", "jiangsu");
        CITY_REGION_HINTS.put("同里", "jiangsu");
        CITY_REGION_HINTS.put("杭州", "zhejiang");
        CITY_REGION_HINTS.put("西湖", "zhejiang");
        CITY_REGION_HINTS.put("绍兴", "zhejiang");
        CITY_REGION_HINTS.put("乌镇", "zhejiang");
        CITY_REGION_HINTS.put("西塘", "zhejiang");
        CITY_REGION_HINTS.put("丽水", "zhejiang");
        CITY_REGION_HINTS.put("宁波", "zhejiang");
        CITY_REGION_HINTS.put("温州", "zhejiang");
        CITY_REGION_HINTS.put("黄山", "anhui");
        // 「皖」是安徽简称、「浙」是浙江简称，古驿道这类文本常这么写
        CITY_REGION_HINTS.put("皖南", "anhui");
        CITY_REGION_HINTS.put("皖", "anhui");
        CITY_REGION_HINTS.put("浙西", "zhejiang");
        CITY_REGION_HINTS.put("浙东", "zhejiang");
        CITY_REGION_HINTS.put("浙", "zhejiang");
        CITY_REGION_HINTS.put("歙县", "anhui");
        CITY_REGION_HINTS.put("宏村", "anhui");
        CITY_REGION_HINTS.put("西递", "anhui");
        CITY_REGION_HINTS.put("福州", "fujian");
        CITY_REGION_HINTS.put("泉州", "fujian");
        CITY_REGION_HINTS.put("土楼", "fujian");
        CITY_REGION_HINTS.put("厦门", "fujian");
        CITY_REGION_HINTS.put("景德镇", "jiangxi");
        CITY_REGION_HINTS.put("婺源", "jiangxi");
        CITY_REGION_HINTS.put("庐山", "jiangxi");
        CITY_REGION_HINTS.put("济南", "shandong");
        CITY_REGION_HINTS.put("大明湖", "shandong");
        CITY_REGION_HINTS.put("曲阜", "shandong");
        CITY_REGION_HINTS.put("泰山", "shandong");
        CITY_REGION_HINTS.put("青岛", "shandong");
        // 「江南」是区域概念，古园林/古桥语境下多指苏浙一带，归到江苏最贴近
        CITY_REGION_HINTS.put("江南", "jiangsu");
        CITY_REGION_HINTS.put("苏杭", "jiangsu");

        // 华中与华南
        CITY_REGION_HINTS.put("洛阳", "henan");
        CITY_REGION_HINTS.put("开封", "henan");
        CITY_REGION_HINTS.put("少林", "henan");
        CITY_REGION_HINTS.put("龙门", "henan");
        CITY_REGION_HINTS.put("武汉", "hubei");
        CITY_REGION_HINTS.put("黄鹤楼", "hubei");
        CITY_REGION_HINTS.put("荆州", "hubei");
        CITY_REGION_HINTS.put("长沙", "hunan");
        CITY_REGION_HINTS.put("凤凰", "hunan");
        CITY_REGION_HINTS.put("张家界", "hunan");
        CITY_REGION_HINTS.put("岳阳", "hunan");
        CITY_REGION_HINTS.put("广州", "guangdong");
        CITY_REGION_HINTS.put("潮州", "guangdong");
        CITY_REGION_HINTS.put("开平", "guangdong");
        CITY_REGION_HINTS.put("碉楼", "guangdong");
        CITY_REGION_HINTS.put("桂林", "guangxi");
        CITY_REGION_HINTS.put("阳朔", "guangxi");
        CITY_REGION_HINTS.put("三江", "guangxi");
        CITY_REGION_HINTS.put("侗族", "guangxi");

        // 西南与西北
        CITY_REGION_HINTS.put("成都", "sichuan");
        CITY_REGION_HINTS.put("都江堰", "sichuan");
        CITY_REGION_HINTS.put("乐山", "sichuan");
        CITY_REGION_HINTS.put("峨眉", "sichuan");
        CITY_REGION_HINTS.put("丽江", "yunnan");
        CITY_REGION_HINTS.put("大理", "yunnan");
        CITY_REGION_HINTS.put("西双版纳", "yunnan");
        CITY_REGION_HINTS.put("腾冲", "yunnan");
        CITY_REGION_HINTS.put("拉萨", "xizang");
        CITY_REGION_HINTS.put("布达拉宫", "xizang");
        CITY_REGION_HINTS.put("日喀则", "xizang");
        CITY_REGION_HINTS.put("贵阳", "guizhou");
        CITY_REGION_HINTS.put("黔东南", "guizhou");
        CITY_REGION_HINTS.put("兰州", "gansu");
        CITY_REGION_HINTS.put("敦煌", "gansu");
        CITY_REGION_HINTS.put("嘉峪关", "gansu");
        CITY_REGION_HINTS.put("银川", "ningxia");
        CITY_REGION_HINTS.put("乌鲁木齐", "xinjiang");
        CITY_REGION_HINTS.put("喀什", "xinjiang");
        CITY_REGION_HINTS.put("吐鲁番", "xinjiang");
        CITY_REGION_HINTS.put("西宁", "qinghai");
        CITY_REGION_HINTS.put("青海湖", "qinghai");

        // 东北
        CITY_REGION_HINTS.put("哈尔滨", "heilongjiang");
        CITY_REGION_HINTS.put("沈阳", "liaoning");
        CITY_REGION_HINTS.put("大连", "liaoning");
        CITY_REGION_HINTS.put("长春", "jilin");
        CITY_REGION_HINTS.put("吉林市", "jilin");
        CITY_REGION_HINTS.put("呼和浩特", "neimenggu");
    }

    /**
     * 启动预热：把已发布模型的省份归属先算进缓存。
     *
     * 好处有两个：
     * 1. 首次打开地图不用现算，省掉逐个推断的开销；
     * 2. 本地规则搞不定的，在用户访问前就把 AI 推断排上队，等用户点进来时多半已算完。
     *
     * 用 ApplicationReadyEvent 而不是 @PostConstruct：后者执行时数据库连接池
     * 可能还没完全就绪，容易在启动日志里刷一堆异常。
     * 整个预热跑在后台线程，不阻塞应用启动。
     */
    @org.springframework.context.event.EventListener(
        org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void warmUpRegionCache() {
        regionInferenceExecutor.execute(() -> {
            try {
                List<BuildingModel> models = modelRepository.findAll().stream()
                    .filter(m -> m.getStatus() != null && m.getStatus() == 1)
                    .filter(m -> m.getModelName() != null && !m.getModelName().isBlank())
                    .collect(Collectors.toList());

                int localHit = 0;
                int needAi = 0;
                for (BuildingModel m : models) {
                    String byLocation = inferRegionCode(m.getLocation());
                    if (!UNKNOWN_REGION.equals(byLocation)) {
                        modelRegionCache.put(m.getId(), byLocation);
                        localHit++;
                    } else {
                        // 本地无解的先标占位，让后续请求知道「已在排队」而不是重复提交
                        modelRegionCache.put(m.getId(), PENDING_REGION);
                        needAi++;
                    }
                }
                log.info("地图省份缓存预热完成：本地命中 {} 个，待 AI 推断 {} 个（后台排队）", localHit, needAi);

                // 需要 AI 的那些，紧接着在当前线程里一个个跑（线程池本身就一个线程，天然串行）
                for (BuildingModel m : models) {
                    if (PENDING_REGION.equals(modelRegionCache.get(m.getId()))) {
                        String resolved = inferRegionCodeByAi(
                            m.getModelName(), m.getLocation(), m.getDescription());
                        modelRegionCache.put(m.getId(), resolved);
                    }
                }
                log.info("地图省份 AI 推断全部完成");
            } catch (Exception e) {
                log.warn("地图省份缓存预热失败（不影响正常使用，首次请求时会重新推断）", e);
            }
        });
    }

    @Override
    public Map<String, Object> getMapOverview(String keyword) {
        return getMapOverview(keyword, "all");
    }

    /**
     * 地图点位检索。
     * scope 决定拿哪个字段匹配：model=模型名、dynasty=朝代、location=地点、all=三者任一。
     * 与顶部全局搜索对齐，避免同一个页面出现两套互不相通的搜索规则。
     */
    @Override
    public Map<String, Object> getMapOverview(String keyword, String scope) {
        String kw = keyword == null ? "" : keyword.trim();
        String normalizedScope = scope == null || scope.isBlank() ? "all" : scope.trim().toLowerCase();

        List<BuildingModel> publishedModels = modelRepository.findAll()
            .stream()
            .filter(item -> item.getStatus() != null && item.getStatus() == 1)
            // 无模型名不显示在地图点位上
            .filter(item -> item.getModelName() != null && !item.getModelName().isBlank())
            .filter(item -> matchesKeyword(item, kw, normalizedScope))
            .collect(Collectors.toList());

        List<Map<String, Object>> points = new ArrayList<>();
        Map<String, Integer> regionIndexCounter = new LinkedHashMap<>();
        for (BuildingModel model : publishedModels) {
            String regionCode = resolveRegionCode(model);
            /*
              省份还在后台推断（PENDING）时先不给点位：
              否则会有一批点位堆在「未标注地区」把地图中心糊成一团。
              等后台推断完写进缓存，下次刷新就出现在正确的省份了。
            */
            if (regionCode == null || PENDING_REGION.equals(regionCode)) {
                continue;
            }
            double[] center = REGION_CENTER.getOrDefault(regionCode, new double[]{105.0, 35.0});
            int pointIndex = regionIndexCounter.getOrDefault(regionCode, 0);
            regionIndexCounter.put(regionCode, pointIndex + 1);

            // 同一个地区可能挤了多个模型，按序号稍微错开一点，点位不至于完全叠在一起
            double lngOffset = ((pointIndex % 5) - 2) * 0.25;
            double latOffset = ((pointIndex / 5) % 5 - 2) * 0.18;
            double lng = center[0] + lngOffset;
            double lat = center[1] + latOffset;

            points.add(Map.of(
                "id", model.getId(),
                "regionCode", regionCode,
                "regionName", REGION_NAME.getOrDefault(regionCode, "未知地区"),
                "lng", lng,
                "lat", lat,
                "xPercent", toXPercent(lng),
                "yPercent", toYPercent(lat),
                "hint", model.getModelName() == null ? "古建点位" : model.getModelName(),
                "location", normalize(model.getLocation()),
                "dynasty", normalize(model.getDynasty())
            ));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("points", points);
        result.put("totalModels", publishedModels.size());
        result.put("updatedAt", System.currentTimeMillis());
        return result;
    }

    @Override
    public List<BuildingModel> getRegionModels(String regionCode) {
        return modelRepository.findAll()
            .stream()
            .filter(item -> item.getStatus() != null && item.getStatus() == 1)
            .filter(item -> item.getModelName() != null && !item.getModelName().isBlank())
            .filter(item -> regionCode.equals(resolveRegionCode(item)))
            .limit(12)
            .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> generateModelInsight(Integer modelId, String modelName, String dynasty, String location, String description) {
        String fallbackName = modelName == null || modelName.isBlank() ? "古建筑模型" : modelName;
        /*
          AI 生成失败、超时、甚至上游 SDK 抛运行时异常时，
          这里都必须给出一段可用的兜底文案，而不是把 500 抛给前端。
          地图卡片上的"讲解"属于增强信息，不该因为模型服务抖动就整页报错。
        */
        String answer;
        try {
            answer = askAiInsight(fallbackName, dynasty, location, description);
        } catch (Exception e) {
            log.warn("AI insight failed, use fallback: {}", e.getMessage());
            answer = buildFallbackInsight(fallbackName, dynasty, location);
        }
        if (answer == null || answer.isBlank()) {
            answer = buildFallbackInsight(fallbackName, dynasty, location);
        }
        String query = String.join(" ", fallbackName, dynasty == null ? "" : dynasty, location == null ? "" : location).trim();
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        List<Map<String, String>> links = List.of(
            Map.of(
                "title", "标准地图服务检索",
                "url", "http://bzdt.ch.mnr.gov.cn/"
            ),
            Map.of(
                "title", "国家地理信息公共服务平台（天地图）",
                "url", "https://www.tianditu.gov.cn/"
            ),
            Map.of(
                "title", "了解更多（AI检索建议）",
                "url", "https://www.bing.com/search?q=" + encoded
            )
        );
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("modelId", modelId);
        payload.put("insight", answer);
        payload.put("links", links);
        return payload;
    }

    private String askAiInsight(String modelName, String dynasty, String location, String description) {
        // streamChat 是回调式的，这里用 latch 等它跑完，包装成同步返回字符串的写法
        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder buffer = new StringBuilder();
        final Exception[] holder = new Exception[1];
        String prompt = String.join("\n",
            "你是中国古建筑讲解专家，请生成精炼文案。",
            "输出三段：建筑成就、历史意义、推荐观看方式。",
            "总字数控制在180字以内，语言有画面感但不要虚构。",
            "建筑名称：" + modelName,
            "朝代：" + (dynasty == null || dynasty.isBlank() ? "未知" : dynasty),
            "地点：" + (location == null || location.isBlank() ? "未知" : location),
            "补充信息：" + (description == null || description.isBlank() ? "无" : description)
        );

        // 流式接口的 chunk 全都 append 进 buffer，complete 了才算完
        deepSeekClient.streamChat(prompt, null, modelName, dynasty, location, new DeepSeekClient.DeepSeekCallback() {
            @Override
            public void onChunk(String chunk) {
                buffer.append(chunk);
            }

            @Override
            public void onComplete() {
                latch.countDown();
            }

            @Override
            public void onError(Exception e) {
                holder[0] = e;
                latch.countDown();
            }
        });

        try {
            // 等 20 秒是体验和成本的折中：再长卡片一直转圈，再短 fallback 就太频繁
            boolean completed = latch.await(20, TimeUnit.SECONDS);
            if (!completed || holder[0] != null) {
                return buildFallbackInsight(modelName, dynasty, location);
            }
            String text = buffer.toString().trim();
            if (text.isEmpty()) {
                return buildFallbackInsight(modelName, dynasty, location);
            }
            return text;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("AI insight interrupted: {}", e.getMessage());
            return buildFallbackInsight(modelName, dynasty, location);
        }
    }

    private String buildFallbackInsight(String modelName, String dynasty, String location) {
        return "建筑成就：" + modelName + "体现了中国古建在结构与工艺上的高水平。"
            + "\n历史意义：其所处于" + (dynasty == null || dynasty.isBlank() ? "相关历史时期" : dynasty)
            + "，具有重要文化研究价值。"
            + "\n观看建议：建议先看整体形制，再关注关键节点，并结合"
            + (location == null || location.isBlank() ? "地域文脉" : location)
            + "理解其演变逻辑。";
    }

    /**
     * 判断模型是否命中关键词。空关键词视为全部命中。
     * 地点匹配同时看 location 字段和地区中文名（比如搜"江苏"能命中"南京"的模型），
     * 否则用户按省份搜会一无所获。
     * 注意这里只用本地推断 inferRegionCode，不能调 resolveRegionCode：
     * 后者在猜不出来时会请求 AI，放进逐条过滤里搜索会变成几十次网络调用。
     */
    private boolean matchesKeyword(BuildingModel model, String keyword, String scope) {
        if (keyword.isEmpty()) {
            return true;
        }
        String kw = keyword.toLowerCase();

        // 用清洗后的字段匹配，否则数据里的制表符会让 contains 意外失败
        boolean hitModel = containsIgnoreCase(normalize(model.getModelName()), kw);
        boolean hitDynasty = containsIgnoreCase(normalize(model.getDynasty()), kw);
        boolean hitLocation = containsIgnoreCase(normalize(model.getLocation()), kw);

        if (!hitLocation) {
            String regionCode = inferRegionCode(model.getLocation());
            String regionName = REGION_NAME.getOrDefault(regionCode, "");
            hitLocation = containsIgnoreCase(regionName, kw);
        }

        return switch (scope) {
            case "model" -> hitModel;
            case "dynasty" -> hitDynasty;
            case "location" -> hitLocation;
            default -> hitModel || hitDynasty || hitLocation;
        };
    }

    private boolean containsIgnoreCase(String source, String lowerKeyword) {
        return source != null && source.toLowerCase().contains(lowerKeyword);
    }

    /**
     * 库里部分老数据的朝代/地点前面带了制表符（导入时留下的），
     * 直接返回会让前端标签出现诡异缩进，这里统一清掉首尾空白。
     */
    private String normalize(String raw) {
        return raw == null ? "" : raw.replaceAll("[\\s\\u00A0]+", " ").trim();
    }

    /**
     * 解析模型所属省份。
     *
     * 本地规则命中就直接返回，一次查表搞定。
     * 本地猜不出来时【不再同步等 AI】——原来这里会阻塞最多 8 秒，
     * 几十个模型串起来就能把地图首页拖到 18 秒。
     * 现在改成：先按「未标注」返回（前端能立刻出图），
     * 同时把 AI 推断丢到后台线程，算完写进缓存，下次请求就准了。
     */
    private String resolveRegionCode(BuildingModel model) {
        if (model == null || model.getId() == null) {
            return UNKNOWN_REGION;
        }
        String cached = modelRegionCache.get(model.getId());
        if (cached != null) {
            return cached;
        }
        String byLocation = inferRegionCode(model.getLocation());
        if (!UNKNOWN_REGION.equals(byLocation)) {
            modelRegionCache.put(model.getId(), byLocation);
            return byLocation;
        }

        // 本地规则无解：标记为「推断中」，避免同一模型被重复提交到后台
        if (modelRegionCache.putIfAbsent(model.getId(), PENDING_REGION) == null) {
            scheduleAiRegionInference(model);
        }
        return UNKNOWN_REGION;
    }

    /**
     * 把 AI 推断放到后台单线程池里跑。
     * 用单线程是刻意的：AI 接口本身有并发限制，排队跑比并发打满更稳，
     * 而且这些推断只影响地图点位归属，晚几秒没有用户感知。
     */
    private void scheduleAiRegionInference(BuildingModel model) {
        final Integer modelId = model.getId();
        final String modelName = model.getModelName();
        final String location = model.getLocation();
        final String description = model.getDescription();
        try {
            regionInferenceExecutor.execute(() -> {
                try {
                    String resolved = inferRegionCodeByAi(modelName, location, description);
                    modelRegionCache.put(modelId, resolved);
                    log.debug("后台推断省份完成: modelId={} region={}", modelId, resolved);
                } catch (Exception e) {
                    // 推断失败就落回未标注，别把占位值留着一直卡住
                    modelRegionCache.put(modelId, UNKNOWN_REGION);
                    log.warn("后台推断省份失败: modelId={}", modelId, e);
                }
            });
        } catch (Exception e) {
            // 线程池已满等情况：直接落未标注，不影响本次请求
            modelRegionCache.put(modelId, UNKNOWN_REGION);
            log.warn("提交后台省份推断失败: modelId={}", modelId, e);
        }
    }

    /**
     * 按地点文本推断省份代码。
     *
     * 只认省名的话，像「江南园林」「苏州」「扬州瘦西湖」这类数据全都猜不出来，
     * 只能落到 AI 兜底——每个模型最多等 8 秒，几十个模型串起来就是十几秒的首页卡顿。
     * 所以这里补上主要城市和地标关键词，让绝大多数模型在本地一次命中。
     *
     * 匹配顺序：省名 → 城市名。因为「江苏苏州」这类文本里省名已经够用，
     * 而城市名可能和别省重名（如「山西」与「陕西」），先按省匹配更稳。
     */
    private String inferRegionCode(String location) {
        if (location == null || location.isBlank()) {
            return UNKNOWN_REGION;
        }
        String text = location.replaceAll("[\\s\\u00A0]+", "");

        // ===== 第一轮：直接匹配省级名称 =====
        if (text.contains("北京")) return "beijing";
        if (text.contains("天津")) return "tianjin";
        if (text.contains("河北")) return "hebei";
        if (text.contains("内蒙古")) return "neimenggu";
        if (text.contains("辽宁")) return "liaoning";
        if (text.contains("吉林")) return "jilin";
        if (text.contains("黑龙江")) return "heilongjiang";
        if (text.contains("上海")) return "shanghai";
        if (text.contains("江苏")) return "jiangsu";
        if (text.contains("浙江")) return "zhejiang";
        if (text.contains("安徽")) return "anhui";
        if (text.contains("福建")) return "fujian";
        if (text.contains("江西")) return "jiangxi";
        if (text.contains("山东")) return "shandong";
        if (text.contains("河南")) return "henan";
        if (text.contains("湖北")) return "hubei";
        if (text.contains("湖南")) return "hunan";
        if (text.contains("广东")) return "guangdong";
        if (text.contains("广西")) return "guangxi";
        if (text.contains("海南")) return "hainan";
        if (text.contains("重庆")) return "chongqing";
        if (text.contains("四川")) return "sichuan";
        if (text.contains("贵州")) return "guizhou";
        if (text.contains("云南")) return "yunnan";
        if (text.contains("西藏")) return "xizang";
        if (text.contains("甘肃")) return "gansu";
        if (text.contains("青海")) return "qinghai";
        if (text.contains("宁夏")) return "ningxia";
        if (text.contains("新疆")) return "xinjiang";
        // 山西 / 陕西必须在通用城市匹配之前判断，避免「西安」被 shanxi 抢走
        if (text.contains("陕西")) return "shanxi2";
        if (text.contains("山西")) return "shanxi";

        // ===== 第二轮：城市与地标关键词 =====
        for (Map.Entry<String, String> entry : CITY_REGION_HINTS.entrySet()) {
            if (text.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return UNKNOWN_REGION;
    }

    private String inferRegionCodeByAi(String modelName, String location, String description) {
        // 只要 AI 吐一个地区代码回来，不要解释文字
        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder buffer = new StringBuilder();
        final Exception[] holder = new Exception[1];
        String prompt = String.join("\n",
            "你是地理解析助手，请根据建筑信息判断其中国省级行政区。",
            "只输出一个地区代码，不要解释。",
            "可选代码：" + String.join("、", REGION_NAME.keySet()),
            "若无法判断输出：" + UNKNOWN_REGION,
            "模型名：" + (modelName == null ? "" : modelName),
            "地点字段：" + (location == null ? "" : location),
            "描述：" + (description == null ? "" : description)
        );
        deepSeekClient.streamChat(prompt, null, modelName, null, location, new DeepSeekClient.DeepSeekCallback() {
            @Override
            public void onChunk(String chunk) {
                buffer.append(chunk);
            }

            @Override
            public void onComplete() {
                latch.countDown();
            }

            @Override
            public void onError(Exception e) {
                holder[0] = e;
                latch.countDown();
            }
        });
        try {
            // 只要一个短 code，8 秒等不到就算了
            boolean completed = latch.await(8, TimeUnit.SECONDS);
            if (!completed || holder[0] != null) {
                return UNKNOWN_REGION;
            }
            String text = buffer.toString().trim().toLowerCase();
            if (REGION_NAME.containsKey(text)) {
                return text;
            }
            for (String key : REGION_NAME.keySet()) {
                if (text.contains(key)) {
                    return key;
                }
            }
            return UNKNOWN_REGION;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return UNKNOWN_REGION;
        }
    }

    // 经纬度映射成中国地图底图上的百分比坐标（经度 73~135、纬度 18~54），并留 5% 边距
    private double toXPercent(double lng) {
        double minLng = 73.0;
        double maxLng = 135.0;
        double normalized = (lng - minLng) / (maxLng - minLng);
        return Math.max(5, Math.min(95, normalized * 100));
    }

    private double toYPercent(double lat) {
        double minLat = 18.0;
        double maxLat = 54.0;
        double normalized = 1 - ((lat - minLat) / (maxLat - minLat));
        return Math.max(8, Math.min(92, normalized * 100));
    }
}

