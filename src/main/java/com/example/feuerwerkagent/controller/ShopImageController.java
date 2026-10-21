package com.example.feuerwerkagent.controller;

import com.example.feuerwerkagent.entity.ShopProduct;
import com.example.feuerwerkagent.mapper.ShopProductMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 商品图片：
 * 1. /api/shop/image/blob/{key} —— 真实产品图（存于 shop_image_blob 表，BLOB出图，带缓存头）
 * 2. /api/shop/image/p{商品id}-{1|2|3} —— 动态SVG占位图（无真实图片时的回退）
 * 均无需登录。
 */
@Slf4j
@RestController
@RequestMapping("/api/shop/image")
@CrossOrigin(origins = "*")
public class ShopImageController {

    /**
     * 配色（渐变起止色）
     */
    private static final String[][] PALETTES = {
            {"#667eea", "#764ba2"},
            {"#f093fb", "#f5576c"},
            {"#4facfe", "#00f2fe"},
            {"#43e97b", "#38f9d7"},
            {"#fa709a", "#fee140"},
            {"#a18cd1", "#fbc2eb"},
            {"#ffecd2", "#fcb69f"},
            {"#84fab0", "#8fd3f4"},
    };

    /**
     * 分类对应emoji
     */
    private static final String CATEGORY_FALLBACK = "🛍️";

    @Autowired
    private ShopProductMapper productMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 真实产品图（数据库BLOB）：/api/shop/image/blob/{key}
     * 图片内容不变，允许浏览器/代理缓存7天
     */
    @GetMapping("/blob/{key}")
    public ResponseEntity<byte[]> blob(@PathVariable String key) {
        try {
            List<byte[]> rows = jdbcTemplate.query(
                    "SELECT data FROM shop_image_blob WHERE blob_key = ?",
                    (rs, i) -> rs.getBytes(1), key);
            if (!rows.isEmpty()) {
                String mime = jdbcTemplate.queryForObject(
                        "SELECT mime FROM shop_image_blob WHERE blob_key = ?", String.class, key);
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.parseMediaType(
                        mime == null ? "image/jpeg" : mime));
                headers.setCacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic());
                return new ResponseEntity<>(rows.get(0), headers, org.springframework.http.HttpStatus.OK);
            }
        } catch (Exception e) {
            log.warn("读取图片BLOB失败: {} - {}", key, e.getMessage());
        }
        // 无真实图片时回退到SVG占位图，保证前端始终有图
        String svg = hero(Math.abs(key.hashCode()), CATEGORY_FALLBACK, "Feuerwerk 数码商城", "");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("image/svg+xml;charset=UTF-8"));
        return new ResponseEntity<>(svg.getBytes(StandardCharsets.UTF_8), headers,
                org.springframework.http.HttpStatus.OK);
    }

    @GetMapping(value = "/{key}", produces = "image/svg+xml;charset=UTF-8")
    public String image(@PathVariable String key) {
        try {
            // key: p{id}-{n} 或 auto-{seed}-{n}
            String[] parts = key.split("-");
            int variant;
            long seed;
            ShopProduct product = null;
            if (parts[0].startsWith("p") && parts[0].length() > 1) {
                long productId = Long.parseLong(parts[0].substring(1));
                product = productMapper.selectById(productId);
                seed = productId;
                variant = Integer.parseInt(parts[parts.length - 1]);
            } else {
                seed = Math.abs(key.hashCode());
                variant = Integer.parseInt(parts[parts.length - 1]);
            }
            String name = product == null ? "Feuerwerk 商品" : product.getName();
            String brief = product == null ? "FeuerwerkAgent 网上商店" : product.getBrief();
            String category = product == null ? "" : product.getCategory();
            String emoji = emojiOf(category, seed);

            switch (((variant - 1) % 3 + 3) % 3) {
                case 0:
                    return hero(seed, emoji, name, category);
                case 1:
                    return feature(seed, emoji, name, brief);
                default:
                    return spec(seed, emoji, name, category, product);
            }
        } catch (Exception e) {
            log.warn("生成图片失败: {}", key);
            return hero(1, CATEGORY_FALLBACK, "Feuerwerk 商品", "");
        }
    }

    /**
     * 主图：渐变背景 + 大emoji + 光斑 + 名称胶囊
     */
    private String hero(long seed, String emoji, String name, String category) {
        String[] palette = PALETTES[(int) (seed % PALETTES.length)];
        Random random = new Random(seed * 7 + 1);

        StringBuilder sb = new StringBuilder();
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 800 600\">");
        sb.append("<defs><linearGradient id=\"g\" x1=\"0\" y1=\"0\" x2=\"1\" y2=\"1\">")
                .append("<stop offset=\"0\" stop-color=\"").append(palette[0]).append("\"/>")
                .append("<stop offset=\"1\" stop-color=\"").append(palette[1]).append("\"/>")
                .append("</linearGradient></defs>");
        sb.append("<rect width=\"800\" height=\"600\" fill=\"url(#g)\"/>");

        // 光斑
        for (int i = 0; i < 14; i++) {
            int x = random.nextInt(800), y = random.nextInt(600), r = 2 + random.nextInt(5);
            sb.append("<circle cx=\"").append(x).append("\" cy=\"").append(y)
                    .append("\" r=\"").append(r).append("\" fill=\"#ffffff\" opacity=\"")
                    .append(String.format(java.util.Locale.ROOT, "%.2f", 0.15 + random.nextDouble() * 0.4))
                    .append("\"/>");
        }
        // 星光
        for (int i = 0; i < 6; i++) {
            int x = 40 + random.nextInt(720), y = 40 + random.nextInt(520);
            sb.append("<text x=\"").append(x).append("\" y=\"").append(y)
                    .append("\" font-size=\"").append(14 + random.nextInt(16))
                    .append("\" opacity=\"0.55\">✦</text>");
        }
        sb.append("<text x=\"400\" y=\"330\" font-size=\"210\" text-anchor=\"middle\">").append(emoji).append("</text>");

        // 分类角标
        if (category != null && !category.isBlank()) {
            sb.append("<rect x=\"20\" y=\"20\" rx=\"14\" width=\"")
                    .append(category.length() * 22 + 36).append("\" height=\"34\" fill=\"#00000030\"/>")
                    .append("<text x=\"").append(20 + (category.length() * 22 + 36) / 2)
                    .append("\" y=\"44\" font-size=\"20\" fill=\"#fff\" text-anchor=\"middle\">")
                    .append(escape(category)).append("</text>");
        }
        // 名称胶囊
        String shortName = name.length() > 16 ? name.substring(0, 15) + "…" : name;
        float pillWidth = Math.min(shortName.length() * 30 + 60, 720);
        sb.append("<rect x=\"").append(400 - pillWidth / 2).append("\" y=\"500\" rx=\"27\" width=\"")
                .append(pillWidth).append("\" height=\"54\" fill=\"#ffffff\" opacity=\"0.92\"/>")
                .append("<text x=\"400\" y=\"535\" font-size=\"26\" font-weight=\"bold\" fill=\"#333\" text-anchor=\"middle\">")
                .append(escape(shortName)).append("</text>");
        sb.append("</svg>");
        return sb.toString();
    }

    /**
     * 卖点图：左右分栏，右大emoji，左侧名称+简介要点
     */
    private String feature(long seed, String emoji, String name, String brief) {
        String[] palette = PALETTES[(int) ((seed + 3) % PALETTES.length)];
        Random random = new Random(seed);

        StringBuilder sb = new StringBuilder();
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 800 600\">");
        sb.append("<defs><linearGradient id=\"g\" x1=\"0\" y1=\"0\" x2=\"1\" y2=\"1\">")
                .append("<stop offset=\"0\" stop-color=\"").append(palette[0]).append("\"/>")
                .append("<stop offset=\"1\" stop-color=\"").append(palette[1]).append("\"/>")
                .append("</linearGradient></defs>");
        sb.append("<rect width=\"800\" height=\"600\" fill=\"url(#g)\"/>");
        sb.append("<circle cx=\"560\" cy=\"300\" r=\"190\" fill=\"#ffffff\" opacity=\"0.15\"/>");
        sb.append("<circle cx=\"560\" cy=\"300\" r=\"140\" fill=\"#ffffff\" opacity=\"0.12\"/>");
        sb.append("<text x=\"560\" y=\"375\" font-size=\"180\" text-anchor=\"middle\">").append(emoji).append("</text>");
        for (int i = 0; i < 8; i++) {
            sb.append("<circle cx=\"").append(30 + random.nextInt(320)).append("\" cy=\"")
                    .append(30 + random.nextInt(540)).append("\" r=\"").append(2 + random.nextInt(4))
                    .append("\" fill=\"#fff\" opacity=\"0.4\"/>");
        }

        // 商品名（换行）
        List<String> lines = wrap(name, 9);
        sb.append("<text x=\"48\" y=\"").append(150 - (lines.size() - 1) * 22)
                .append("\" font-size=\"40\" font-weight=\"bold\" fill=\"#fff\">");
        for (int i = 0; i < lines.size() && i < 2; i++) {
            sb.append("<tspan x=\"48\" dy=\"").append(i == 0 ? 0 : 50).append("\">")
                    .append(escape(lines.get(i))).append("</tspan>");
        }
        sb.append("</text>");

        // 简介要点
        List<String> points = wrap(brief == null ? "品质好物 值得信赖" : brief, 13);
        int y = 300;
        for (int i = 0; i < points.size() && i < 4; i++) {
            sb.append("<circle cx=\"56\" cy=\"").append(y - 7).append("\" r=\"4\" fill=\"#fff\" opacity=\"0.9\"/>")
                    .append("<text x=\"74\" y=\"").append(y).append("\" font-size=\"22\" fill=\"#ffffff\" opacity=\"0.95\">")
                    .append(escape(points.get(i))).append("</text>");
            y += 42;
        }
        sb.append("<rect x=\"48\" y=\"530\" rx=\"8\" width=\"150\" height=\"3\" fill=\"#ffffff\" opacity=\"0.7\"/>")
                .append("<text x=\"48\" y=\"570\" font-size=\"18\" fill=\"#ffffff\" opacity=\"0.85\">✦ Feuerwerk 精选好物</text>");
        sb.append("</svg>");
        return sb.toString();
    }

    /**
     * 参数图：卡片式，展示分类/价格/库存/销量
     */
    private String spec(long seed, String emoji, String name, String category, ShopProduct product) {
        String[] palette = PALETTES[(int) ((seed + 5) % PALETTES.length)];

        String price = product == null || product.getPrice() == null ? "--" : "¥" + product.getPrice().toPlainString();
        String stock = product == null ? "--" : String.valueOf(product.getStock());
        String sales = product == null || product.getSales() == null ? "0" : String.valueOf(product.getSales());

        StringBuilder sb = new StringBuilder();
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 800 600\">");
        sb.append("<defs><linearGradient id=\"g\" x1=\"0\" y1=\"0\" x2=\"1\" y2=\"1\">")
                .append("<stop offset=\"0\" stop-color=\"").append(palette[0]).append("\"/>")
                .append("<stop offset=\"1\" stop-color=\"").append(palette[1]).append("\"/>")
                .append("</linearGradient></defs>");
        sb.append("<rect width=\"800\" height=\"600\" fill=\"url(#g)\"/>");
        sb.append("<rect x=\"60\" y=\"80\" rx=\"24\" width=\"680\" height=\"440\" fill=\"#ffffff\" opacity=\"0.95\"/>");

        sb.append("<text x=\"120\" y=\"200\" font-size=\"80\">").append(emoji).append("</text>");
        String shortName = name.length() > 14 ? name.substring(0, 13) + "…" : name;
        sb.append("<text x=\"240\" y=\"170\" font-size=\"34\" font-weight=\"bold\" fill=\"#333\">")
                .append(escape(shortName)).append("</text>");
        sb.append("<text x=\"240\" y=\"210\" font-size=\"20\" fill=\"#888\">分类：")
                .append(escape(category == null || category.isBlank() ? "未分类" : category)).append("</text>");

        // 参数行
        sb.append("<rect x=\"120\" y=\"260\" rx=\"12\" width=\"560\" height=\"2\" fill=\"#eee\"/>");
        sb.append(row("💰 价格", price, 310, "#e74c3c"));
        sb.append(row("📦 库存", stock + " 件", 370, "#333"));
        sb.append(row("🔥 销量", sales + " 件", 430, "#333"));
        sb.append(row("🚚 配送", "48小时内发货 · 全国包邮", 490, "#333"));
        sb.append("<text x=\"400\" y=\"585\" font-size=\"16\" fill=\"#ffffff\" opacity=\"0.9\" text-anchor=\"middle\">— FeuerwerkAgent 网上商店 · 商品参数 —</text>");
        sb.append("</svg>");
        return sb.toString();
    }

    private String row(String label, String value, int y, String color) {
        return "<text x=\"140\" y=\"" + y + "\" font-size=\"22\" fill=\"#666\">" + escape(label)
                + "</text><text x=\"300\" y=\"" + y + "\" font-size=\"24\" font-weight=\"bold\" fill=\""
                + color + "\">" + escape(value) + "</text>";
    }

    /**
     * 分类emoji映射，未匹配时按seed取通用emoji
     */
    private String emojiOf(String category, long seed) {
        if (category != null) {
            if (category.contains("笔记本")) return "💻";
            if (category.contains("手机")) return "📱";
            if (category.contains("平板")) return "📲";
            if (category.contains("显卡")) return "🎮";
            if (category.contains("显示")) return "🖥️";
            if (category.contains("穿戴") || category.contains("手表")) return "⌚";
            if (category.contains("音频") || category.contains("耳机")) return "🎧";
        }
        String[] fallbacks = {"💻", "📱", "📲", "🎮", "🖥️", "⌚", "🎧", "⭐"};
        return fallbacks[(int) (seed % fallbacks.length)];
    }

    /**
     * 中文按字符宽度换行
     */
    private List<String> wrap(String text, int maxChars) {
        List<String> lines = new java.util.ArrayList<>();
        if (text == null || text.isBlank()) {
            lines.add("");
            return lines;
        }
        StringBuilder current = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c == '\n' || c == '；' || c == '。' || c == '，' || c == ',') {
                if (current.length() > 0) {
                    lines.add(current.toString());
                    current = new StringBuilder();
                }
                continue;
            }
            current.append(c);
            if (current.length() >= maxChars) {
                lines.add(current.toString());
                current = new StringBuilder();
            }
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        return lines;
    }

    /**
     * XML转义
     */
    private String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
