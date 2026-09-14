package com.interview.service;

import com.interview.dto.VOs;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.owasp.html.AttributePolicy;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class MarkdownService {

    private static final Pattern HEADING_PATTERN = Pattern.compile("<h([1-6])([^>]*)>(.*?)</h\\1>", Pattern.DOTALL);
    private static final Pattern ID_PATTERN = Pattern.compile("id=\"([^\"]+)\"");
    private static final Pattern TAG_PATTERN = Pattern.compile("<[^>]+>");
    /** 带 scheme 的 URL 前缀（http:、javascript:、data: …），用于判断 img src 是否为外链协议。 */
    private static final Pattern SCHEME_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:");
    /**
     * img src：允许 base64(data:image/*)、http(s)、相对路径（包含 / 开头的站内路径）；
     * 其余带 scheme 的写法（javascript: / data:text/html / vbscript: 等）一律丢弃。
     */
    private static final AttributePolicy IMG_SRC_POLICY = (el, attr, val) -> {
        String v = val == null ? "" : val.trim();
        String lower = v.toLowerCase();
        if (lower.startsWith("data:image/")) return v;
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("mailto:")) return v;
        // 无 scheme（相对路径 / 以 / 或 ./ 开头）直接放行
        return SCHEME_PATTERN.matcher(lower).find() ? null : v;
    };
    /** a href：拒绝 data: URL，防止 data:text/html 等 XSS。 */
    private static final AttributePolicy HREF_POLICY = (el, attr, val) ->
            val != null && val.trim().toLowerCase().startsWith("data:") ? null : val;
    private final PolicyFactory policy;

    public MarkdownService() {
        this.policy = new HtmlPolicyBuilder()
                .allowElements("h1", "h2", "h3", "h4", "h5", "h6", "p", "pre", "code",
                        "table", "thead", "tbody", "tr", "th", "td", "img", "a",
                        "ul", "ol", "li", "blockquote", "strong", "em", "del", "br", "hr")
                .allowAttributes("id").onElements("h1", "h2", "h3", "h4", "h5", "h6")
                // c++ / c# / objective-c 这类语言名里带 +、#、. ，原来的 [\w-]+ 会把类名整段丢掉
                .allowAttributes("class").matching(Pattern.compile("(language-[A-Za-z0-9_+#.-]+|hljs)")).onElements("code")
                .allowUrlProtocols("http", "https", "mailto", "data")
                .allowAttributes("href").matching(HREF_POLICY).onElements("a")
                .allowAttributes("title").onElements("a", "img")
                .allowAttributes("src").matching(IMG_SRC_POLICY).onElements("img")
                .allowAttributes("alt").onElements("img")
                .allowElements("input")
                .allowAttributes("type", "checked", "disabled").onElements("input")
                .toFactory();
    }

    /**
     * 渲染预览用 HTML（与文章正文同一套解析 + 白名单）。
     *
     * <p>与 {@link ContentRenderService} 的区别只在于<b>不做图片搬运</b>：
     * 正文入库前会把图片下载压缩后换成 MinIO 地址，而预览只用原文渲染，
     * 图片仍是原地址（base64 / 外链 / 相对路径），排版与正文完全一致。
     */
    public VOs.MarkdownPreviewVO preview(String markdown) {
        String html = render(markdown);
        return VOs.MarkdownPreviewVO.builder()
                .contentHtml(html)
                .toc(extractToc(html))
                .build();
    }

    public String render(String markdown) {
        MutableDataSet options = new MutableDataSet();
        options.set(Parser.EXTENSIONS, List.of(
                TablesExtension.create(),
                StrikethroughExtension.create(),
                TaskListExtension.create()
        ));
        Parser parser = Parser.builder(options).build();
        HtmlRenderer renderer = HtmlRenderer.builder(options).build();
        String html = renderer.render(parser.parse(markdown == null ? "" : markdown));
        return policy.sanitize(addHeadingIds(html));
    }

    private String addHeadingIds(String html) {
        Matcher m = HEADING_PATTERN.matcher(html);
        StringBuilder sb = new StringBuilder();
        int seq = 0;
        Map<String, Integer> usedIds = new HashMap<>();
        while (m.find()) {
            String level = m.group(1);
            String attrs = m.group(2);
            String content = m.group(3);
            String id = null;
            Matcher idm = ID_PATTERN.matcher(attrs);
            if (idm.find()) {
                id = idm.group(1);
            }
            if (id == null) {
                // 先去掉行内标签、再把 HTML 实体还原成纯文本再取 slug，
                // 这样标题里的 & 之类字符在前端预览（按 DOM 文本取 slug）也能得到同一个 id
                id = slugify(HtmlUtils.htmlUnescape(content.replaceAll("<[^>]+>", "")));
                if (id.isEmpty()) {
                    id = "sec-" + (++seq);
                }
            }
            int count = usedIds.merge(id, 1, Integer::sum);
            String finalId = count > 1 ? id + "-" + count : id;
            String finalAttrs;
            if (ID_PATTERN.matcher(attrs).find()) {
                finalAttrs = attrs.replaceAll("id=\"[^\"]*\"", "id=\"" + finalId + "\"");
            } else {
                finalAttrs = attrs + " id=\"" + finalId + "\"";
            }
            String replacement = "<h" + level + finalAttrs + ">" + content + "</h" + level + ">";
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String slugify(String text) {
        String slug = text.trim().replaceAll("[^\\p{L}\\p{N}]+", "-").replaceAll("^-+|-+$", "");
        return slug.length() > 80 ? slug.substring(0, 80) : slug;
    }

    public List<VOs.TocItemVO> extractToc(String html) {
        List<VOs.TocItemVO> toc = new ArrayList<>();
        if (html == null) {
            return toc;
        }
        // 标题里可能带行内标签（**加粗**、`行内代码`、链接、图片），
        // 早期用 ([^<]+) 匹配文本会让这类标题整条从目录里消失，这里改成提取后再去标签。
        Matcher m = HEADING_PATTERN.matcher(html);
        while (m.find()) {
            Matcher idm = ID_PATTERN.matcher(m.group(2));
            if (!idm.find()) {
                // 没有锚点的标题（历史数据里手写的裸 <h2>）不进目录，避免点了没反应
                continue;
            }
            String text = HtmlUtils.htmlUnescape(TAG_PATTERN.matcher(m.group(3)).replaceAll("")).trim();
            toc.add(new VOs.TocItemVO(idm.group(1), text, Integer.valueOf(m.group(1))));
        }
        return toc;
    }
}
