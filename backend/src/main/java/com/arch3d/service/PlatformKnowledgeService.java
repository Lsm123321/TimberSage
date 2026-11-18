package com.arch3d.service;

import com.arch3d.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 「平台自身」的知识供给。
 *
 * 为什么需要它：问答页的 AI 原本只被设定成「古建筑专家」，
 * 用户问「这个网站怎么点赞」「一共收录了多少模型」「我是普通用户能上传吗」时，
 * 它只会泛泛而谈，甚至回答"暂无可靠依据"。
 * 这里把项目的实际数据、功能入口和权限规则整理成一段系统提示，
 * 让模型先当一个"懂这个系统的人"，再当古建筑专家。
 *
 * 数据是每次提问时现查的（模型数会变），所以不加缓存。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlatformKnowledgeService {

    private final BuildingModelRepository modelRepository;
    private final BuildingCategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final DownloadLogRepository downloadLogRepository;
    private final KbEntryRepository kbEntryRepository;

    /** 平台概况：数字部分实时查库，避免模型拿过期数字回答 */
    public Map<String, Object> snapshot() {
        Map<String, Object> stats = new LinkedHashMap<>();
        try {
            stats.put("modelCount", modelRepository.count());
            stats.put("categoryCount", categoryRepository.count());
            stats.put("userCount", userRepository.count());
            stats.put("downloadLogCount", downloadLogRepository.count());
            stats.put("knowledgeCount", kbEntryRepository.count());
        } catch (Exception e) {
            log.warn("平台统计读取失败: {}", e.getMessage());
        }
        return stats;
    }

    /**
     * 拼给大模型看的系统提示片段。
     *
     * @param role 当前用户角色（user / contributor / admin），匿名传 null
     */
    public String buildPromptSection(String role) {
        Map<String, Object> s = snapshot();
        StringBuilder sb = new StringBuilder();

        sb.append("\n\n【关于当前平台：智筑云览】\n");
        sb.append("你同时是本平台的导览助手。平台是基于 3D 与 AI 技术的中国古建筑展示与教学系统，")
          .append("核心能力：3D 模型浏览、地图导览、AI 知识问答、收藏/点赞/评论、贡献者上传与管理员审核。\n");

        sb.append("\n平台实时数据（回答数量类问题时以此为准）：\n");
        appendStat(sb, "已收录 3D 古建筑模型", s.get("modelCount"), "座");
        appendStat(sb, "建筑分类", s.get("categoryCount"), "个");
        appendStat(sb, "注册用户", s.get("userCount"), "人");
        appendStat(sb, "累计下载记录", s.get("downloadLogCount"), "条");
        appendStat(sb, "知识库问答条目", s.get("knowledgeCount"), "条");

        sb.append("\n【功能入口与操作路径】\n");
        sb.append("· 点赞模型：进入「建筑列表」或「首页-热门建筑」，鼠标移到模型卡片上，")
          .append("点击卡片下方的心形/点赞图标；详情页顶部也有点赞按钮。\n");
        sb.append("· 收藏模型：同样在模型卡片或详情页顶部，点击星形收藏按钮；收藏内容在「个人中心-我的收藏」。\n");
        sb.append("· 发表评论：进入模型详情页，向下滚动到「评论」区域，输入框输入后点击「发表评论」。\n");
        sb.append("· 下载模型：模型详情页顶部「下载模型」按钮，仅在上传者允许下载时可用。\n");
        sb.append("· 地图导览：左侧菜单「地图导览」，点击地图上的金色点位查看该地区模型；筛选可按朝代过滤。\n");
        sb.append("· 上传模型：贡献者及以上角色可在「建筑列表」页点「添加建筑」，上传 .glb 文件与缩略图。\n");
        sb.append("· 权限申请：普通用户在「个人中心」或「权限申请」页提交申请，管理员审核通过后升级。\n");
        sb.append("· 纠错反馈：模型详情页顶部铅笔图标 → 填写错误类型与描述 → 提交；")
          .append("审核通过后才会公开展示，未通过时仅本人可见。\n");
        sb.append("· 个人中心：左侧菜单「个人中心」，可改昵称/邮箱/密码，查看收藏、点赞、评论、问答历史、下载记录。\n");

        sb.append("\n【角色与权限（务必按此回答，不要给出越权建议）】\n");
        sb.append("· 游客（未登录）：只能浏览模型列表、详情页、地图、AI 问答与评论；")
          .append("点赞、收藏、评论、下载、上传、改资料都需要先登录。\n");
        sb.append("· 普通用户 user：可点赞/收藏/评论/下载、提交纠错与站点建议、申请成为贡献者；不能上传模型。\n");
        sb.append("· 贡献者 contributor：在普通用户基础上，可上传模型（需管理员审核通过后公开）、编辑自己的模型。\n");
        sb.append("· 管理员 admin：可审核模型、管理用户与角色、处理纠错/建议/评论、维护预设问题与知识库。\n");
        sb.append("如果用户当前角色做不到某件事，要明确说明需要什么角色、怎么申请，不要含糊。\n");

        sb.append("\n【当前用户】\n");
        if (role == null || role.isBlank()) {
            sb.append("未登录的游客。涉及点赞/收藏/评论/下载/上传/改资料时，先提示登录，并说明登录后才能操作。\n");
        } else {
            sb.append("已登录，角色：").append(roleText(role)).append("（").append(role).append("）。")
              .append("请按该角色的权限边界回答，不要承诺它没有的能力。\n");
        }

        sb.append("\n【回答要求】\n");
        sb.append("1. 涉及平台功能、数据、权限的问题，用上面的真实信息回答，不要推说\"暂无可靠依据\"。\n");
        sb.append("2. 涉及古建筑本身的问题，按专业讲解；如果问题与当前平台数据有关（如某模型数量），以实时数据为准。\n");
        sb.append("3. 不确定的内容如实说明，不要编造不存在的菜单、按钮或数字。\n");
        sb.append("4. 回答末尾可以给 1-2 条平台内的下一步建议（如\"可在左侧「建筑列表」查看\"）。\n");
        return sb.toString();
    }

    /** 角色中文名，解释权限时读起来更自然 */
    public static String roleText(String role) {
        if (role == null) {
            return "游客";
        }
        return switch (role.toLowerCase()) {
            case "admin" -> "管理员";
            case "contributor" -> "贡献者";
            default -> "普通用户";
        };
    }

    private void appendStat(StringBuilder sb, String label, Object value, String unit) {
        if (value == null) {
            return;
        }
        sb.append("· ").append(label).append("：").append(value).append(" ").append(unit).append("\n");
    }

    // ================================================================ 确定性回答

    /**
     * 平台类问题的确定性回答。
     *
     * 好处有三：1) 不依赖大模型在线，问"怎么点赞"永远有正确答案；
     * 2) 数字实时从库里取，不会像模型那样凭记忆瞎编；
     * 3) 权限说明能精确到当前角色，避免越权建议。
     * 只有明显是本平台相关的问题才命中，古建筑专业问题仍然交给大模型。
     */
    public Optional<String> answerPlatformQuestion(String question, String role) {
        if (question == null || question.isBlank()) {
            return Optional.empty();
        }
        String q = question.toLowerCase(Locale.ROOT);
        boolean asksHowTo = q.contains("怎么") || q.contains("如何") || q.contains("怎样")
            || q.contains("在哪") || q.contains("哪里") || q.contains("入口");

        // —— 本平台统计数据
        if (hasAny(q, "多少", "几个", "几座", "几种", "收录", "统计")
            && hasAny(q, "模型", "建筑", "收藏", "用户", "下载", "知识", "问答", "分类", "网站", "平台")) {
            return Optional.of(statsAnswer());
        }

        // —— 点赞
        if (hasAny(q, "点赞", "点个赞")) {
            return Optional.of(likeAnswer(role));
        }

        // —— 收藏
        if (hasAny(q, "收藏", "喜欢这个")) {
            return Optional.of(favoriteAnswer(role));
        }

        // —— 评论
        if (hasAny(q, "评论", "留言", "评价")) {
            return Optional.of(commentAnswer(role));
        }

        // —— 下载
        if (hasAny(q, "下载", "保存模型")) {
            return Optional.of(downloadAnswer(role));
        }

        // —— 上传 / 成为贡献者
        if (hasAny(q, "上传", "贡献者", "发布模型", "提交模型")) {
            return Optional.of(uploadAnswer(role));
        }

        // —— 权限 / 角色
        if (hasAny(q, "权限", "角色", "能做什么", "谁可以", "管理员", "申请")) {
            return Optional.of(permissionAnswer(role));
        }

        // —— 登录注册
        if (hasAny(q, "登录", "注册", "账号", "密码")) {
            return Optional.of(accountAnswer());
        }

        // —— 平台功能导览（必须是"怎么用"类问法，避免误伤古建筑问题）
        if (asksHowTo) {
            if (hasAny(q, "地图", "导览")) {
                return Optional.of(mapAnswer());
            }
            if (hasAny(q, "问答", "ai", "聊天", "提问")) {
                return Optional.of(aiAnswer());
            }
            if (hasAny(q, "纠错", "反馈", "报错", "建议")) {
                return Optional.of(feedbackAnswer(role));
            }
            if (hasAny(q, "个人中心", "资料", "昵称", "头像", "改密码")) {
                return Optional.of(profileAnswer());
            }
            if (hasAny(q, "3d", "模型", "旋转", "查看")) {
                return Optional.of(viewerAnswer());
            }
        }

        return Optional.empty();
    }

    private boolean hasAny(String text, String... words) {
        for (String w : words) {
            if (text.contains(w)) {
                return true;
            }
        }
        return false;
    }

    private String statsAnswer() {
        Map<String, Object> s = snapshot();
        StringBuilder sb = new StringBuilder("截至目前的平台数据：\n");
        sb.append("· 已收录 3D 古建筑模型：").append(s.getOrDefault("modelCount", "—")).append(" 座\n");
        sb.append("· 建筑分类：").append(s.getOrDefault("categoryCount", "—")).append(" 个\n");
        sb.append("· 注册用户：").append(s.getOrDefault("userCount", "—")).append(" 人\n");
        sb.append("· 累计下载记录：").append(s.getOrDefault("downloadLogCount", "—")).append(" 条\n");
        sb.append("· 知识库问答条目：").append(s.getOrDefault("knowledgeCount", "—")).append(" 条\n\n");
        sb.append("这些数字直接来自数据库实时统计。想按朝代或地区缩小范围，可以到「建筑列表」用顶部分类与搜索筛选，")
          .append("或打开「地图导览」按地域查看分布。");
        return sb.toString();
    }

    private String likeAnswer(String role) {
        return "给模型点赞有两条路径：\n"
            + "1. 在「建筑列表」或「首页 → 热门建筑」中，鼠标移到模型卡片上，点击卡片信息区里的点赞图标（心形）。\n"
            + "2. 打开模型详情页，在右上角的信息卡片顶部点击点赞按钮。\n\n"
            + "点赞记录会出现在「个人中心 → 我的点赞」，同名按钮再点一次即可取消。\n"
            + (role == null ? "\n注意：你现在是游客，需要先登录才能点赞。" : "");
    }

    private String favoriteAnswer(String role) {
        return "收藏模型的入口和点赞类似：\n"
            + "· 模型卡片上点击星形收藏按钮；\n"
            + "· 或进入模型详情页，点击右上角信息卡片顶部的星形按钮。\n\n"
            + "收藏的模型统一在「个人中心 → 我的收藏」里，支持按名称搜索和取消收藏。\n"
            + (role == null ? "\n注意：收藏需要登录后才能操作。" : "");
    }

    private String commentAnswer(String role) {
        return "发表评论：进入任意模型详情页，向下滚动到「评论」区域，"
            + "在输入框写下看法后点击「发表评论」。评论支持点赞、回复和删除（作者本人、模型贡献者或管理员可删）。\n"
            + (role == null ? "\n注意：评论需要先登录。登录后你的评论会显示昵称与头像。" : "");
    }

    private String downloadAnswer(String role) {
        return "下载模型：打开模型详情页，点击顶部「下载模型」按钮。\n"
            + "· 只有上传者设置了「允许下载」的模型才可下载；\n"
            + "· 下载会记录到「个人中心 → 我的下载」，管理员可在后台查看下载统计。\n"
            + (role == null ? "\n注意：下载需要登录。" : "");
    }

    private String uploadAnswer(String role) {
        StringBuilder sb = new StringBuilder();
        sb.append("上传模型需要「贡献者」及以上角色：\n")
          .append("1. 若你还是普通用户，先在「权限申请」页提交成为贡献者的申请，填写申请说明；\n")
          .append("2. 管理员审核通过后，进入「建筑列表」，点击右上角「添加建筑」或「上传模型」；\n")
          .append("3. 填写名称、朝代、地点、简介、标签，上传 .glb 模型文件与缩略图；\n")
          .append("4. 提交后进入「待审核」，管理员通过后才会出现在公开列表。\n\n");
        if ("admin".equalsIgnoreCase(role)) {
            sb.append("你是管理员，可直接上传，也可在「管理后台 → 模型管理」审核他人的上传。");
        } else if ("contributor".equalsIgnoreCase(role)) {
            sb.append("你已是贡献者，可以直接从「建筑列表 → 添加建筑」上传。");
        } else if (role == null) {
            sb.append("你当前是游客，需要先登录，再按上面的流程申请贡献者。");
        } else {
            sb.append("你当前是普通用户，需要先提交贡献者申请。");
        }
        return sb.toString();
    }

    private String permissionAnswer(String role) {
        return "平台有四类角色，权限逐级递增：\n"
            + "· 游客（未登录）：浏览模型列表/详情、地图导览、AI 问答；不能点赞、收藏、评论、下载。\n"
            + "· 普通用户 user：可点赞、收藏、评论、下载、提交纠错与建议、申请成为贡献者。\n"
            + "· 贡献者 contributor：在普通用户基础上，可上传与管理自己的模型（需管理员审核）。\n"
            + "· 管理员 admin：审核模型、管理用户与角色、处理纠错/建议/评论、维护预设问题与知识库。\n\n"
            + "当前身份：" + roleText(role) + "。"
            + (role == null ? "登录后即可使用互动功能。" : "")
            + (("user".equalsIgnoreCase(role)) ? "如需上传模型，请到「权限申请」提交贡献者申请。" : "");
    }

    private String accountAnswer() {
        return "登录与注册在页面右上角：\n"
            + "· 已有账号：点击右上角「登录」，输入用户名、密码和图形验证码；\n"
            + "· 新用户：在登录页切换到「注册」，填写用户名、邮箱与密码即可；\n"
            + "· 忘记密码：目前请联系管理员在后台重置为默认密码，登录后到「个人中心 → 修改密码」自行更换。";
    }

    private String mapAnswer() {
        return "地图导览在左侧菜单「地图导览」：\n"
            + "· 金色光点代表一个区域的模型，点击即可弹出该地区的模型卡片；\n"
            + "· 顶部「筛选」可按朝代过滤，也可以直接搜索建筑名或地点；\n"
            + "· 弹出的卡片可以拖动，避免挡住地图。";
    }

    private String aiAnswer() {
        return "AI 问答有两种用法：\n"
            + "· 左侧菜单「AI 知识问答」是独立的问答页，适合系统性地了解建筑知识、平台使用与权限问题；\n"
            + "· 模型详情页右下角的悬浮问答入口，会把当前模型信息带进上下文，问「这个模型的结构特点」更准确。";
    }

    private String feedbackAnswer(String role) {
        return "反馈分三类：\n"
            + "· 模型纠错：模型详情页顶部铅笔图标，填写错误类型与描述；提交后由管理员审核，"
            + "审核通过才会公开展示，未通过时仅你本人可见。\n"
            + "· 站点建议：在「关于我们」页填写标题与内容提交。\n"
            + "· 回答评价：每条 AI 回答下方有「有用 / 无用」，用于改进知识库。\n"
            + (role == null ? "\n提交纠错需要登录。" : "");
    }

    private String profileAnswer() {
        return "个人中心在左侧菜单「个人中心」：\n"
            + "· 基本信息：修改昵称、邮箱；点击头像可更换头像（支持 JPG/PNG/WebP，≤5MB）；\n"
            + "· 修改密码：输入原密码与新密码；\n"
            + "· 还可查看收藏、点赞、评论、问答历史、下载记录、待审/驳回模型与材质方案。";
    }

    private String viewerAnswer() {
        return "3D 模型查看：\n"
            + "· 进入模型详情页后，画布支持左键旋转、滚轮缩放、右键平移；\n"
            + "· 底部工具栏可重置视角、全屏、线框模式、切换材质与环境；\n"
            + "· 「爆炸展开」会把构件拆开，点击单个构件可查看 AI 生成的构件说明；\n"
            + "· 右下角「AI 导览」可生成语音讲解。";
    }
}