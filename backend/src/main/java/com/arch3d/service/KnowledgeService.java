package com.arch3d.service;

import com.arch3d.entity.KbEntry;
import com.arch3d.repository.KbEntryRepository;
import com.arch3d.repository.BuildingModelRepository;
import com.arch3d.entity.BuildingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 知识库检索引擎。
 *
 * 匹配分三层，从便宜到贵：
 *   L1 Redis 精确  —— 归一化后完全相等，命中直接返回，O(1)
 *   L2 MySQL 精确  —— 同模型下 normalized_question 完全相等
 *   L3 意图匹配    —— 判断"在问哪一类"，类型命中即返回该类型档案
 *
 * 为什么 L3 用意图分类而不是文本相似度：
 *   开发阶段实测过，「这个建筑的结构是怎样的」和「这座建筑的承重体系是怎样的」
 *   语义完全一致，但字符 2-gram 相似度是 0.0——中文同义异形是常态。
 *   而每个模型每类只有一条档案，所以"判对类型"就等于"找到答案"。
 *
 * 匹配全程限定在同一 modelId 内，绝不跨模型兜底：
 *   宁可答不出来走大模型，也不能拿别的建筑的答案糊弄用户。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final KbEntryRepository kbEntryRepository;
    private final BuildingModelRepository modelRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String CACHE_PREFIX = "kb:entry:";
    private static final int STATUS_APPROVED = 1;

    /** 命中后按这个长度切片回放，模拟大模型逐字输出的观感 */
    private static final int REPLAY_CHUNK_SIZE = 30;
    private static final long REPLAY_INTERVAL_MS = 45L;

    /** 单次问答平均 token 消耗，用来估算命中节省（实测 418，取保守值） */
    private static final int AVG_TOKENS_PER_CHAT = 420;

    // ================================================================ 归一化

    /** 标点、空白、全角符号，归一化时统一剔除 */
    private static final Pattern PUNCT = Pattern.compile("[\\s\\u3000!-/:-@\\[-`{-~，。、；：？！…—－（）《》〈〉“”‘’·「」『』【】]");
    /** 句尾语气词，没有信息量 */
    private static final Pattern TAIL_PARTICLE = Pattern.compile("(呢|吗|呀|吧|啊|嘛|请问|你好|谢谢)+$");

    /**
     * 同义表述归一表。
     *
     * 顺序很重要，先处理指代、再处理问法、最后处理修饰词。
     * 这里每一条都是验证阶段用真实历史问题跑出来的，不是拍脑袋写的。
     */
    private static final List<Map.Entry<Pattern, String>> SYNONYMS = List.of(
            // 指代类：统一成占位符，让"这个建筑的结构怎样"这类纯指代问句能互相命中
            entry("这座桥|这个桥|这桥|该桥|此桥", "〔指代〕"),
            entry("这座建筑|这个建筑|该建筑模型|此建筑", "〔指代〕"),
            entry("这个模型|该模型|这座模型", "〔指代〕"),
            entry("它|其", ""),
            // 问法类
            entry("有什么(特点|特色|特征)", "有哪些特点"),
            entry("有什么(优势|好处|优点)", "有哪些优势"),
            entry("为什么|为何|怎么会", "为何"),
            entry("是什么|是啥|指什么", "是什么"),
            entry("介绍一下|介绍下|讲讲|说说|讲一讲|说一说", "介绍"),
            entry("怎么样|如何", "如何"),
            entry("能不能|能否|可不可以", "能否"),
            // 修饰词统一：这些词对答案内容没影响，统一写法能明显提高命中率
            entry("有哪些|有什么|有啥", "有哪些"),
            entry("主要|一般|通常", ""),
            entry("具体|详细|简单", ""),
            // 常见异体字
            entry("斗拱|斗栱|斗拱结构", "斗拱"),
            entry("榫卯|榫头卯眼|榫铆", "榫卯"),
            entry("拱券|券拱|拱圈", "拱券"),
            entry("敞肩拱|空腹拱|敞肩式", "敞肩拱")
    );

    private static Map.Entry<Pattern, String> entry(String regex, String replacement) {
        return Map.entry(Pattern.compile(regex), replacement);
    }

    /**
     * 把问题压成可比较的形式。
     * 顺序：全角转半角 → 去前端拼接的回答规则 → 同义替换 → 去标点 → 去语气词 → 小写。
     */
    public String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String s = Normalizer.normalize(text, Normalizer.Form.NFKC).trim().toLowerCase(Locale.ROOT);
        // 前端曾把"回答规则"拼在问题后面一起发过来，入库/匹配前必须先切掉
        int ruleIdx = s.indexOf("请严格遵守以下回答规则");
        if (ruleIdx >= 0) {
            s = s.substring(0, ruleIdx);
        }
        for (Map.Entry<Pattern, String> e : SYNONYMS) {
            s = e.getKey().matcher(s).replaceAll(e.getValue());
        }
        s = PUNCT.matcher(s).replaceAll("");
        s = TAIL_PARTICLE.matcher(s).replaceAll("");
        return s.length() > 500 ? s.substring(0, 500) : s;
    }

    /** 剥掉问题里的模型名。匹配已限定同模型，名字是冗余信息，留着反而降低相似度 */
    private String stripModelName(String normalized, String modelName) {
        if (normalized == null || modelName == null || modelName.isBlank()) {
            return normalized == null ? "" : normalized;
        }
        String n = normalize(modelName);
        if (n.isEmpty()) {
            return normalized;
        }
        return normalized.replace(n, "").trim();
    }

    // ================================================================ 意图分类

    /**
     * 六类知识的判别关键词。
     *
     * 踩过的坑：最初把单字「建」放进了 history，结果「建**筑**」里的"建"命中，
     * 导致所有带"建筑"二字的问题都被判成历史类。单字关键词必须禁止。
     */
    private static final Map<String, List<String>> INTENT_RULES = new LinkedHashMap<>();

    /** 万能词，命中只给低权重 */
    private static final Set<String> LOW_WEIGHT = Set.of(
            "瓦", "耐", "特点", "风格", "艺术", "意义", "弧", "榫", "卯");

    /** 强指向词，一个顶三个 */
    private static final Set<String> HIGH_WEIGHT = Set.of(
            "独一无二", "相比", "对比", "比较", "异同", "不同", "区别", "差异", "独特",
            "为什么说", "优势", "礼制", "象征", "寓意", "内涵");

    static {
        INTENT_RULES.put("structure", List.of(
                "结构", "构造", "承重", "受力", "力学", "梁架", "框架", "体系",
                "榫卯", "斗拱", "拱券", "支撑", "传力", "节点",
                "桥墩", "屋檐", "屋面", "起翘", "榫", "卯", "弧"));
        INTENT_RULES.put("material", List.of(
                "材料", "材质", "木材", "木料", "石料", "青砖", "琉璃", "瓦",
                "选材", "耐", "防腐", "用什么做",
                "保存", "耐久", "腐朽"));
        INTENT_RULES.put("history", List.of(
                "历史", "年代", "时期", "朝代", "什么时候", "多久", "修建", "始建",
                "背景", "沿革", "修缮", "主持", "年间", "公元", "谁建", "由谁",
                "风格特点", "时代"));
        INTENT_RULES.put("culture", List.of(
                "文化", "象征", "寓意", "内涵", "礼制", "思想", "观念", "寄托",
                "意义", "精神", "宗教", "信仰", "等级"));
        INTENT_RULES.put("aesthetic", List.of(
                "造型", "装饰", "美学", "艺术", "好看", "漂亮", "比例", "线条",
                "色彩", "雕刻", "纹样", "栏杆", "望柱", "栏板", "风格", "美观"));
        INTENT_RULES.put("compare", List.of(
                "对比", "相比", "比较", "独特", "特别", "不同", "区别", "差异",
                "独一无二", "为什么说", "优势", "特点", "异同", "相比之"));
    }

    /** 返回 [类型, 得分]；类型为 null 表示判不出来，交给兜底 */
    private Object[] classifyIntent(String text) {
        if (text == null || text.isBlank()) {
            return new Object[]{null, 0};
        }
        String bestType = null;
        int bestScore = 0;
        for (Map.Entry<String, List<String>> e : INTENT_RULES.entrySet()) {
            int score = 0;
            for (String kw : e.getValue()) {
                if (text.contains(kw)) {
                    if (HIGH_WEIGHT.contains(kw)) {
                        score += 3;
                    } else if (LOW_WEIGHT.contains(kw)) {
                        score += 1;
                    } else {
                        score += 2;
                    }
                }
            }
            if (score > bestScore) {
                bestScore = score;
                bestType = e.getKey();
            }
        }
        return new Object[]{bestType, bestScore};
    }

    // ================================================================ 拦截闸

    /** 知识档案只有定性描述，答不了这些定量/工程/运营类问题 */
    private static final List<Pattern> OUT_OF_SCOPE = List.of(
            Pattern.compile("多少(吨|米|平|个|攒|种|元|钱|人|次|天|小时|年|块|根|层)"),
            Pattern.compile("(多少|几)(吨|米|平|个|人|次)"),
            Pattern.compile("造价|价格|费用|成本|预算|多少钱|花费"),
            Pattern.compile("抗震|耐火等级|荷载等级|强度等级|等级是多少"),
            Pattern.compile("面数|面片数|顶点数|三角面|polygon"),
            Pattern.compile("尺寸|长宽高|面积|体积|重量|吨位|跨度多少"),
            Pattern.compile("建模|导出|格式|文件大小|贴图|纹理|分辨率|glb|gltf|模型文件"),
            Pattern.compile("步骤|流程|教程|怎么做出来|制作过程"),
            Pattern.compile("施工|机械|设备|脚手架|工期|工人"),
            Pattern.compile("门票|游客|接待|开放时间|怎么走|预约|地址在哪|电话"),
            Pattern.compile("天气|温度|股市|股票|电影|音乐|游戏|代码|python|java|编程|数学"),
            Pattern.compile("釉色|彩绘成分|颜料成分|化学成分|含水率")
    );

    /** 出现这些词且没有指代当前建筑，说明问的是通识，不是这座建筑的档案 */
    private static final List<String> GENERAL_CONCEPTS = List.of(
            "榫卯", "斗拱", "拱券", "营造法式", "古建筑", "中国建筑",
            "木结构", "抬梁", "穿斗", "歇山", "庑殿", "悬山", "攒尖");

    private static final List<String> DEIXIS = List.of(
            "这个", "这座", "该", "它", "此", "本", "〔指代〕", "这");

    public boolean isOutOfScope(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        for (Pattern p : OUT_OF_SCOPE) {
            if (p.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断是否为与当前模型无关的通识问题。
     * 例：用户问「介绍一下榫卯工艺」，不该拿某座桥的结构档案去答。
     */
    public boolean isGeneralKnowledge(String text, String modelName) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String norm = normalize(text);
        if (norm.isEmpty()) {
            return false;
        }
        if (modelName != null && !modelName.isBlank()) {
            norm = norm.replace(normalize(modelName), "");
        }
        // 有明确指代当前建筑，就用档案答
        for (String h : DEIXIS) {
            if (norm.contains(h)) {
                return false;
            }
        }
        // 剥名后仍带"在…里/中"的语境限定，说明问的是这个具体建筑上的表现
        if (Pattern.compile("(里|中|上|内)(怎么|如何|起|是|有|表现)").matcher(norm).find()
                || norm.contains("在这")) {
            return false;
        }
        for (String w : GENERAL_CONCEPTS) {
            if (norm.contains(w)) {
                return true;
            }
        }
        // 「和赵州桥相比」这种跨对象比较，本模型档案答不了
        if (Pattern.compile("和.{2,12}(相比|比较|对比|比)").matcher(norm).find()) {
            return true;
        }
        return false;
    }

    // ================================================================ 检索

    /** 命中结果 */
    public record LookupResult(boolean hit,
                               String layer,
                               KbEntry entry,
                               String intent,
                               String fallbackReason,
                               String modelName) {
        static LookupResult miss(String reason) {
            return new LookupResult(false, "MISS", null, null, reason, null);
        }

        static LookupResult of(String layer, KbEntry entry, String intent, String modelName) {
            return new LookupResult(true, layer, entry, intent, null, modelName);
        }
    }

    /**
     * 主入口：查知识库。
     *
     * @return 命中时 entry 非空；未命中时 fallbackReason 可能带 OUT_OF_SCOPE / GENERAL
     */
    public LookupResult lookup(String question, Integer modelId) {
        if (question == null || question.isBlank() || modelId == null) {
            return LookupResult.miss(null);
        }

        String modelName = modelRepository.findById(modelId)
                .map(BuildingModel::getModelName)
                .orElse("");
        return lookup(question, modelId, modelName);
    }

    public LookupResult lookup(String question, Integer modelId, String modelName) {
        if (question == null || question.isBlank() || modelId == null) {
            return LookupResult.miss(null);
        }

        // ---- 越界拦截：放行会让用户拿到看似专业但答非所问的答案，比拒答更糟
        if (isOutOfScope(question)) {
            return LookupResult.miss("OUT_OF_SCOPE");
        }
        // ---- 通识拦截：这类问题交给大模型的通识能力反而答得更好
        if (isGeneralKnowledge(question, modelName)) {
            return LookupResult.miss("GENERAL");
        }

        String norm = normalize(question);
        if (norm.isEmpty()) {
            return LookupResult.miss(null);
        }

        // ---- L1：Redis 精确缓存
        String cacheKey = CACHE_PREFIX + modelId + ":" + Math.abs(norm.hashCode());
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached instanceof Integer entryId) {
                KbEntry entry = kbEntryRepository.findById(entryId).orElse(null);
                if (entry != null && STATUS_APPROVED == entry.getStatus()) {
                    return LookupResult.of("L1", entry, entry.getQuestionType(), modelName);
                }
            }
        } catch (Exception e) {
            // 缓存只是加速手段，挂了不能影响主流程
            log.debug("知识库缓存读取失败，继续查库: {}", e.getMessage());
        }

        // ---- L2：DB 精确匹配
        List<KbEntry> exact = kbEntryRepository
                .findByModelIdAndNormalizedQuestionAndStatus(modelId, norm, STATUS_APPROVED);
        if (!exact.isEmpty()) {
            KbEntry entry = exact.get(0);
            cacheEntry(cacheKey, entry);
            return LookupResult.of("L2", entry, entry.getQuestionType(), modelName);
        }

        // ---- L3：意图匹配
        // 注意：分类必须用「剥掉模型名之后」的问题。模型名本身常含分类关键词——
        // 「长直栏杆木桥」的"栏杆"会命中审美类，「交叉支撑木拱桥」的"支撑"会命中结构类，
        // 实测这一条修掉了全部的类型错配。
        String stripped = stripModelName(norm, modelName);
        Object[] intentResult = classifyIntent(stripped);
        String intent = (String) intentResult[0];

        if (intent != null) {
            List<KbEntry> typed = kbEntryRepository
                    .findByModelIdAndQuestionTypeAndStatus(modelId, intent, STATUS_APPROVED);
            if (!typed.isEmpty()) {
                KbEntry entry = typed.get(0);
                cacheEntry(cacheKey, entry);
                return LookupResult.of("L3", entry, intent, modelName);
            }
        }

        return LookupResult.miss(null);
    }

    private void cacheEntry(String key, KbEntry entry) {
        try {
            // 缓存里只存 id：答案正文可能很长，存 id 再回查更省内存也便于更新
            redisTemplate.opsForValue().set(key, entry.getId(),
                    java.time.Duration.ofDays(30));
        } catch (Exception e) {
            log.debug("知识库缓存写入失败: {}", e.getMessage());
        }
    }

    /** 条目被编辑/审核后清掉缓存，下次重新从库里取 */
    public void evictCache(Integer modelId, String normalizedQuestion) {
        if (modelId == null || normalizedQuestion == null) {
            return;
        }
        try {
            redisTemplate.delete(CACHE_PREFIX + modelId + ":" + Math.abs(normalizedQuestion.hashCode()));
        } catch (Exception e) {
            log.debug("知识库缓存清理失败: {}", e.getMessage());
        }
    }

    // ================================================================ 回放

    /**
     * 把知识库答案切片，供 SSE 逐段推送。
     *
     * 为什么不一整段发：一次性蹦出整段文字，和模型逐字输出的观感差别很大，
     * 用户反而会怀疑答案是不是真的。切片 + 短间隔能保持体验一致。
     */
    public List<String> splitForReplay(String answer) {
        if (answer == null || answer.isEmpty()) {
            return List.of();
        }
        List<String> chunks = new ArrayList<>();
        // 优先在标点处断开，读起来更自然
        String[] sentences = answer.split("(?<=[。！？；\n])");
        StringBuilder buf = new StringBuilder();
        for (String s : sentences) {
            if (buf.length() + s.length() > REPLAY_CHUNK_SIZE && buf.length() > 0) {
                chunks.add(buf.toString());
                buf.setLength(0);
            }
            buf.append(s);
        }
        if (buf.length() > 0) {
            chunks.add(buf.toString());
        }
        // 兜底：没有标点的长句硬切
        List<String> out = new ArrayList<>();
        for (String c : chunks) {
            for (int i = 0; i < c.length(); i += REPLAY_CHUNK_SIZE) {
                out.add(c.substring(i, Math.min(i + REPLAY_CHUNK_SIZE, c.length())));
            }
        }
        return out;
    }

    public long getReplayIntervalMs() {
        return REPLAY_INTERVAL_MS;
    }

    public int getAvgTokensPerChat() {
        return AVG_TOKENS_PER_CHAT;
    }

    /**
     * 命中计数。故意不加 @Async：项目里没有 @EnableAsync，
     * 加了也不会生效，反而让人误以为异步。由调用方决定是否放独立线程。
     */
    public void recordHit(KbEntry entry) {
        try {
            kbEntryRepository.incrementHit(entry.getId(), AVG_TOKENS_PER_CHAT);
        } catch (Exception e) {
            log.warn("知识库命中计数失败 id={}: {}", entry.getId(), e.getMessage());
        }
    }

    /** 给某模型推荐 3 条高频问题，用于替换现在那个空的 preset_question */
    public List<KbEntry> suggestQuestions(Integer modelId, int limit) {
        List<KbEntry> all = kbEntryRepository.findByModelIdAndStatus(modelId, STATUS_APPROVED);
        return all.stream()
                .sorted(Comparator.comparingInt((KbEntry k) ->
                        k.getHitCount() == null ? 0 : k.getHitCount()).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }
}
