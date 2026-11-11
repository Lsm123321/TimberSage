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
    private final Map<Integer, String> modelRegionCache = new ConcurrentHashMap<>();

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
            if (regionCode == null) {
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
        // 地图卡片上的 AI 导览文案：按建筑元信息生成三段短文案
        String answer = askAiInsight(fallbackName, dynasty, location, description);
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
        // 地点字段猜不出来时才让 AI 兜底推断，省着点调用量
        String aiResolved = inferRegionCodeByAi(model.getModelName(), model.getLocation(), model.getDescription());
        modelRegionCache.put(model.getId(), aiResolved);
        return aiResolved;
    }

    private String inferRegionCode(String location) {
        if (location == null || location.isBlank()) {
            return UNKNOWN_REGION;
        }
        if (location.contains("北京")) return "beijing";
        if (location.contains("天津")) return "tianjin";
        if (location.contains("河北")) return "hebei";
        if (location.contains("山西")) return "shanxi";
        if (location.contains("内蒙古")) return "neimenggu";
        if (location.contains("辽宁")) return "liaoning";
        if (location.contains("吉林")) return "jilin";
        if (location.contains("黑龙江")) return "heilongjiang";
        if (location.contains("上海")) return "shanghai";
        if (location.contains("江苏")) return "jiangsu";
        if (location.contains("浙江")) return "zhejiang";
        if (location.contains("安徽")) return "anhui";
        if (location.contains("福建")) return "fujian";
        if (location.contains("江西")) return "jiangxi";
        if (location.contains("山东")) return "shandong";
        if (location.contains("河南")) return "henan";
        if (location.contains("湖北")) return "hubei";
        if (location.contains("湖南")) return "hunan";
        if (location.contains("广东")) return "guangdong";
        if (location.contains("广西")) return "guangxi";
        if (location.contains("海南")) return "hainan";
        if (location.contains("重庆")) return "chongqing";
        if (location.contains("四川")) return "sichuan";
        if (location.contains("贵州")) return "guizhou";
        if (location.contains("云南")) return "yunnan";
        if (location.contains("西藏")) return "xizang";
        if (location.contains("陕西") || location.contains("西安")) return "shanxi2";
        if (location.contains("甘肃")) return "gansu";
        if (location.contains("青海")) return "qinghai";
        if (location.contains("宁夏")) return "ningxia";
        if (location.contains("新疆")) return "xinjiang";
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

