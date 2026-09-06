package com.wordspirit.module.shop.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/**
 * 备考资料 PDF 生成服务
 * 词表数据基于 MIT 协议开源词库 ECDICT（github.com/skywind3000/ECDICT）按词频筛选整理；
 * 句型 / 短语 / 作文模板为本站原创内容，均无版权风险。
 * PDF 只经后端鉴权接口流式输出，不暴露任何外部直链（防抓包）。
 */
@Slf4j
@Component
public class StudyResourceService {

    /** 资料 key -> 配置 */
    private static final java.util.Map<String, ResourceDef> DEFS = new java.util.HashMap<>();

    static {
        register("cet4_words", "四级高频词速记手册", "CET-4 真题词频 TOP500", "words", "study-data/cet4_words.json", "cet");
        register("cet6_words", "六级高频词速记手册", "CET-6 真题词频 TOP500", "words", "study-data/cet6_words.json", "cet");
        register("writing", "四六级写作万能句型手册", "60+ 高分句型 · 开头/论证/转折/结尾全覆盖", "sentences", "study-data/writing_sentences.json", "cet");
        register("phrases", "四六级高频短语速查手册", "130+ 必背短语 · 阅读写作双高频", "phrases", "study-data/phrases_cet.json", "cet");
        register("kaoyan_words", "考研英语核心高频词汇", "大纲词频 TOP800 · 按真题词频排序", "words", "study-data/kaoyan_words.json", "kaoyan");
        register("gk_pack", "高考高频词·满分作文模板", "TOP500 高频词 + 读后续写/应用文模板", "mixed", "study-data/gk_templates.json", "gk");
        register("zk_words", "中考核心词汇速记手册", "中考词频 TOP300 · 音标+释义", "words", "study-data/zk_words.json", "zk");
    }

    private static void register(String key, String title, String subtitle, String type, String dataPath, String exam) {
        ResourceDef d = new ResourceDef();
        d.key = key;
        d.title = title;
        d.subtitle = subtitle;
        d.type = type;
        d.dataPath = dataPath;
        d.exam = exam;
        DEFS.put(key, d);
    }

    public static ResourceDef def(String key) {
        return DEFS.get(key);
    }

    public static class ResourceDef {
        public String key;
        public String title;
        public String subtitle;
        public String type;
        public String dataPath;
        public String exam;
    }

    /** 主色（翡翠绿） */
    private static final Color PRIMARY = new Color(0x1E, 0x66, 0x57);
    private static final Color PRIMARY_LIGHT = new Color(0xE8, 0xF3, 0xEE);
    private static final Color TEXT = new Color(0x33, 0x33, 0x33);
    private static final Color GRAY = new Color(0x88, 0x88, 0x88);

    private BaseFont baseFont;

    private BaseFont font() throws Exception {
        if (baseFont == null) {
            // 文泉驿微米黑（Apache License 2.0，可自由分发商用）
            try (InputStream in = new ClassPathResource("fonts/wqy-microhei.ttc").getInputStream()) {
                File tmp = File.createTempFile("wqy", ".ttc");
                tmp.deleteOnExit();
                Files.copy(in, tmp.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                baseFont = BaseFont.createFont(tmp.getAbsolutePath() + ",0", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            }
        }
        return baseFont;
    }

    /**
     * 获取（或生成并缓存）资料 PDF，返回文件
     */
    public File getPdf(String key) throws Exception {
        ResourceDef def = DEFS.get(key);
        if (def == null) {
            throw new IllegalArgumentException("unknown resource " + key);
        }
        File cache = new File(System.getProperty("java.io.tmpdir"),
                "wordspirit-res-" + key + "-v20260907.pdf");
        if (cache.exists() && cache.length() > 0) {
            return cache;
        }
        synchronized (this) {
            if (cache.exists() && cache.length() > 0) {
                return cache;
            }
            build(def, cache);
            return cache;
        }
    }

    private void build(ResourceDef def, File out) throws Exception {
        JSONObject data = loadData(def.dataPath);
        BaseFont bf = font();
        Document doc = new Document(PageSize.A4, 46, 46, 64, 56);
        try (FileOutputStream fos = new FileOutputStream(out)) {
            PdfWriter writer = PdfWriter.getInstance(doc, fos);
            doc.open();

            cover(doc, writer, def, bf);

            if ("words".equals(def.type)) {
                wordTable(doc, bf, data.getJSONArray("words"));
            } else {
                if ("mixed".equals(def.type)) {
                    // 数据文件自带 words 数组则用自有词表（如六级翻译），否则回退高考词表
                    JSONArray own = data.getJSONArray("words");
                    JSONObject wordsData = own != null ? data : loadData("study-data/gk_words.json");
                    sectionTitle(doc, bf, own != null ? "第一部分 · 翻译高频词速记" : "第一部分 · 高考高频词 TOP500");
                    wordTable(doc, bf, wordsData.getJSONArray("words"));
                    sectionTitle(doc, bf, own != null ? "第二部分 · 翻译句型模板与高分表达" : "第二部分 · 满分作文模板与高分句式");
                }
                JSONArray sections = data.getJSONArray("sections");
                for (int i = 0; i < sections.size(); i++) {
                    JSONObject sec = sections.getJSONObject(i);
                    chapter(doc, bf, sec.getStr("name"));
                    JSONArray items = sec.getJSONArray("items");
                    if ("phrases".equals(def.type)) {
                        phraseTable(doc, bf, items);
                    } else {
                        for (int j = 0; j < items.size(); j++) {
                            JSONObject it = items.getJSONObject(j);
                            Paragraph s = new Paragraph(it.getStr("s"), new Font(bf, 10.5f, Font.NORMAL, TEXT));
                            s.setSpacingBefore(6);
                            s.setSpacingAfter(1);
                            doc.add(s);
                            Paragraph c = new Paragraph(it.getStr("c"), new Font(bf, 9.5f, Font.NORMAL, GRAY));
                            c.setSpacingAfter(6);
                            doc.add(c);
                        }
                    }
                }
            }
            doc.close();
            log.info("生成备考资料 PDF：{}（{} KB）", def.title, out.length() / 1024);
        }
    }

    /** 封面 + 版权/来源声明页 */
    private void cover(Document doc, PdfWriter writer, ResourceDef def, BaseFont bf) throws Exception {
        PdfContentByte cb = writer.getDirectContent();
        // 顶部主色带
        cb.setColorFill(PRIMARY);
        cb.rectangle(0, PageSize.A4.getHeight() - 150, PageSize.A4.getWidth(), 150);
        cb.fill();
        // 底部浅色带
        cb.setColorFill(PRIMARY_LIGHT);
        cb.rectangle(0, 0, PageSize.A4.getWidth(), 46);
        cb.fill();

        Font brand = new Font(bf, 13, Font.NORMAL, new Color(0xD8, 0xEE, 0xE5));
        Paragraph b = new Paragraph("词灵学园 · 备考资料库", brand);
        b.setSpacingBefore(26);
        doc.add(b);

        Paragraph t = new Paragraph(def.title, new Font(bf, 30, Font.BOLD, Color.WHITE));
        t.setSpacingBefore(14);
        doc.add(t);
        Paragraph st = new Paragraph(def.subtitle, new Font(bf, 12.5f, Font.NORMAL, new Color(0xCF, 0xE8, 0xDE)));
        st.setSpacingBefore(6);
        doc.add(st);

        Font h = new Font(bf, 15, Font.BOLD, PRIMARY);
        Paragraph yy = new Paragraph(LocalDate.now().getYear() + " 冲刺版", h);
        yy.setSpacingBefore(180);
        doc.add(yy);

        Font note = new Font(bf, 10, Font.NORMAL, TEXT);
        Paragraph p1 = new Paragraph(
                "本资料由词灵学园整理制作，仅限兑换用户本人学习使用，请勿传播或商用。", note);
        p1.setSpacingBefore(60);
        doc.add(p1);
        Paragraph p2 = new Paragraph(
                "数据说明：词表内容基于 MIT 开源协议词库 ECDICT（github.com/skywind3000/ECDICT）按考试标签与真实使用词频筛选排序；"
                        + "句型、短语与作文模板为本站原创编写，均无版权风险。",
                new Font(bf, 9, Font.NORMAL, GRAY));
        p2.setSpacingBefore(6);
        doc.add(p2);
        Paragraph p3 = new Paragraph(
                "使用建议：按词频顺序背诵，先掌握前 100 个最高频词；配合词灵学堂「今日任务」每日打卡，效果更佳。",
                new Font(bf, 9, Font.NORMAL, GRAY));
        p3.setSpacingBefore(4);
        doc.add(p3);
        doc.newPage();
    }

    private void sectionTitle(Document doc, BaseFont bf, String title) throws Exception {
        Paragraph p = new Paragraph(title, new Font(bf, 18, Font.BOLD, PRIMARY));
        p.setSpacingBefore(10);
        p.setSpacingAfter(10);
        doc.add(p);
    }

    private void chapter(Document doc, BaseFont bf, String title) throws Exception {
        if (doc.getPageNumber() > 2) {
            doc.newPage();
        } else {
            Paragraph gap = new Paragraph(" ");
            gap.setSpacingBefore(8);
            doc.add(gap);
        }
        PdfPCell cell = new PdfPCell(new Phrase(title, new Font(bf, 13.5f, Font.BOLD, PRIMARY)));
        cell.setBackgroundColor(PRIMARY_LIGHT);
        cell.setBorderWidth(0);
        cell.setPadding(8);
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        table.addCell(cell);
        doc.add(table);
        Paragraph gap = new Paragraph(" ");
        gap.setSpacingAfter(2);
        doc.add(gap);
    }

    /** 词表：序号 / 单词 / 音标 / 释义 */
    private void wordTable(Document doc, BaseFont bf, JSONArray words) throws Exception {
        Font head = new Font(bf, 10, Font.BOLD, Color.WHITE);
        Font w = new Font(bf, 10, Font.BOLD, PRIMARY);
        Font p = new Font(bf, 9.5f, Font.NORMAL, TEXT);
        Font t = new Font(bf, 9.5f, Font.NORMAL, TEXT);

        PdfPTable table = new PdfPTable(new float[]{8, 22, 22, 48});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        String[] headers = {"#", "单词", "音标", "释义"};
        for (String hstr : headers) {
            PdfPCell c = new PdfPCell(new Phrase(hstr, head));
            c.setBackgroundColor(PRIMARY);
            c.setBorderWidth(0);
            c.setPadding(6);
            c.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(c);
        }
        for (int i = 0; i < words.size(); i++) {
            JSONObject it = words.getJSONObject(i);
            boolean zebra = i % 2 == 1;
            table.addCell(cell(String.valueOf(i + 1), new Font(bf, 9, Font.NORMAL, GRAY), zebra, Element.ALIGN_CENTER));
            table.addCell(cell(it.getStr("w"), w, zebra, Element.ALIGN_LEFT));
            table.addCell(cell("/" + it.getStr("p", "") + "/", p, zebra, Element.ALIGN_LEFT));
            table.addCell(cell(it.getStr("t"), t, zebra, Element.ALIGN_LEFT));
        }
        doc.add(table);
    }

    /** 短语：短语 / 释义 两列 */
    private void phraseTable(Document doc, BaseFont bf, JSONArray items) throws Exception {
        Font head = new Font(bf, 10, Font.BOLD, Color.WHITE);
        Font ph = new Font(bf, 10, Font.BOLD, PRIMARY);
        Font cn = new Font(bf, 9.5f, Font.NORMAL, TEXT);
        PdfPTable table = new PdfPTable(new float[]{42, 58});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        for (String hstr : new String[]{"短语", "释义"}) {
            PdfPCell c = new PdfPCell(new Phrase(hstr, head));
            c.setBackgroundColor(PRIMARY);
            c.setBorderWidth(0);
            c.setPadding(6);
            c.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(c);
        }
        for (int i = 0; i < items.size(); i++) {
            JSONObject it = items.getJSONObject(i);
            boolean zebra = i % 2 == 1;
            table.addCell(cell(it.getStr("p"), ph, zebra, Element.ALIGN_LEFT));
            table.addCell(cell(it.getStr("c"), cn, zebra, Element.ALIGN_LEFT));
        }
        doc.add(table);
    }

    private PdfPCell cell(String text, Font f, boolean zebra, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "" : text, f));
        c.setBorderWidth(0);
        c.setPadding(5);
        c.setHorizontalAlignment(align);
        if (zebra) {
            c.setBackgroundColor(new Color(0xF6, 0xFA, 0xF8));
        }
        return c;
    }

    private JSONObject loadData(String path) throws Exception {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return JSONUtil.parseObj(json);
        }
    }

    // ==================== 考试倒计时（用户粘性） ====================

    /**
     * 返回距下一场对应考试的剩余天数；无法计算返回 -1
     * 四六级：每年 6 月（第三个周六）与 12 月（第二个周六）各一次
     */
    public static long examDaysLeft(String exam) {
        LocalDate today = LocalDate.now();
        List<LocalDate> candidates = new ArrayList<>();
        switch (exam) {
            case "cet" -> {
                // 已知的官方日期 + 未来三届估算
                candidates.add(LocalDate.of(2026, 12, 12));
                for (int y = 2027; y <= 2029; y++) {
                    candidates.add(juneNthSaturday(y, 3));
                    candidates.add(decNthSaturday(y, 2));
                }
            }
            case "kaoyan" -> {
                candidates.add(LocalDate.of(2026, 12, 19));
                candidates.add(LocalDate.of(2027, 12, 18));
                candidates.add(LocalDate.of(2028, 12, 23));
            }
            case "gk" -> {
                candidates.add(LocalDate.of(2027, 6, 7));
                candidates.add(LocalDate.of(2028, 6, 7));
            }
            case "zk" -> {
                candidates.add(LocalDate.of(2027, 6, 13));
                candidates.add(LocalDate.of(2028, 6, 13));
            }
            default -> {
                return -1;
            }
        }
        return candidates.stream()
                .filter(d -> !d.isBefore(today))
                .findFirst()
                .map(d -> Math.max(0, ChronoUnit.DAYS.between(today, d)))
                .orElse(-1L);
    }

    public static String examLabel(String exam) {
        return switch (exam) {
            case "cet" -> "四六级";
            case "kaoyan" -> "考研";
            case "gk" -> "高考";
            case "zk" -> "中考";
            default -> "";
        };
    }

    private static LocalDate juneNthSaturday(int year, int n) {
        LocalDate first = LocalDate.of(year, 6, 1).with(TemporalAdjusters.firstInMonth(DayOfWeek.SATURDAY));
        return first.plusWeeks(n - 1L);
    }

    private static LocalDate decNthSaturday(int year, int n) {
        LocalDate first = LocalDate.of(year, 12, 1).with(TemporalAdjusters.firstInMonth(DayOfWeek.SATURDAY));
        return first.plusWeeks(n - 1L);
    }
}
