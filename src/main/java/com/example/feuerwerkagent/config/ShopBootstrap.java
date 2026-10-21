package com.example.feuerwerkagent.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.feuerwerkagent.entity.ShopProduct;
import com.example.feuerwerkagent.entity.ShopReview;
import com.example.feuerwerkagent.entity.ShopUser;
import com.example.feuerwerkagent.mapper.ShopProductMapper;
import com.example.feuerwerkagent.mapper.ShopReviewMapper;
import com.example.feuerwerkagent.mapper.ShopUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.stream.Stream;

/**
 * 网上商店初始化：自动建表（IF NOT EXISTS）+ 首次启动导入种子图片与演示数据
 * 管理员账号 admin / admin123，演示买家 demo / 123456
 * 商品真实图片从项目 shop_seed_images/ 目录导入 shop_image_blob 表（以数据库为准出图）
 */
@Slf4j
@Component
public class ShopBootstrap implements ApplicationRunner {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ShopUserMapper userMapper;

    @Autowired
    private ShopProductMapper productMapper;

    @Autowired
    private ShopReviewMapper reviewMapper;

    @Override
    public void run(ApplicationArguments args) {
        createTables();
        importSeedImages();
        seedData();
    }

    /**
     * 种子图片目录：商城演示商品的官方产品图（jpg/png/webp）
     */
    private static final String SEED_IMAGE_DIR = "shop_seed_images";

    /**
     * 导入种子图片到 shop_image_blob 表（仅当表为空时执行，可重复启动）
     */
    private void importSeedImages() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM shop_image_blob", Integer.class);
        if (count != null && count > 0) {
            return;
        }
        Path dir = resolveSeedDir();
        if (dir == null) {
            log.warn("种子图片目录 {} 不存在（已尝试 jar运行目录/父目录/项目根），跳过导入", SEED_IMAGE_DIR);
            return;
        }
        int imported = 0;
        try (Stream<Path> files = Files.list(dir)) {
            for (Path p : files.filter(f -> Files.isRegularFile(f)).toList()) {
                String name = p.getFileName().toString();
                int dot = name.lastIndexOf('.');
                if (dot <= 0) {
                    continue;
                }
                String ext = name.substring(dot + 1).toLowerCase();
                String mime = switch (ext) {
                    case "jpg", "jpeg" -> "image/jpeg";
                    case "png" -> "image/png";
                    case "webp" -> "image/webp";
                    case "gif" -> "image/gif";
                    default -> null;
                };
                if (mime == null) {
                    continue;
                }
                byte[] data = Files.readAllBytes(p);
                String key = name.substring(0, dot);
                jdbcTemplate.update(
                        "INSERT IGNORE INTO shop_image_blob (blob_key, mime, size, data, create_time) VALUES (?,?,?,?,NOW())",
                        key, mime, data.length, data);
                imported++;
            }
        } catch (IOException e) {
            log.error("导入种子图片失败", e);
            return;
        }
        log.info("已导入 {} 张种子商品图片（{}）", imported, dir);
    }

    /**
     * 定位种子图片目录：兼容「项目根运行」与「target目录运行jar」两种启动方式
     */
    private Path resolveSeedDir() {
        Path workDir = Paths.get("").toAbsolutePath();
        // 当前工作目录（项目根直接运行 / IDE运行）
        Path candidate = workDir.resolve(SEED_IMAGE_DIR);
        if (Files.isDirectory(candidate)) {
            return candidate;
        }
        // 父目录（jar 在 target/ 下运行时，工作目录的上一级即项目根）
        candidate = workDir.getParent().resolve(SEED_IMAGE_DIR);
        if (Files.isDirectory(candidate)) {
            return candidate;
        }
        // classpath 定位（jar内资源根的上级，兜底）
        try {
            Path jar = Paths.get(ShopBootstrap.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).getParent();
            candidate = jar.getParent().resolve(SEED_IMAGE_DIR);
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void createTables() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS shop_user (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "username VARCHAR(50) NOT NULL," +
                "password VARCHAR(100) NOT NULL," +
                "nickname VARCHAR(50)," +
                "avatar VARCHAR(255)," +
                "phone VARCHAR(20)," +
                "email VARCHAR(100)," +
                "role VARCHAR(20) NOT NULL DEFAULT 'USER'," +
                "status TINYINT NOT NULL DEFAULT 1," +
                "create_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "UNIQUE KEY uk_username (username)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS shop_product (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "name VARCHAR(100) NOT NULL," +
                "category VARCHAR(50)," +
                "brief VARCHAR(500)," +
                "detail TEXT," +
                "price DECIMAL(10,2) NOT NULL," +
                "original_price DECIMAL(10,2) DEFAULT NULL," +
                "stock INT NOT NULL DEFAULT 0," +
                "main_image VARCHAR(255)," +
                "images TEXT," +
                "sales INT NOT NULL DEFAULT 0," +
                "rating_avg DECIMAL(3,1) DEFAULT 5.0," +
                "rating_count INT NOT NULL DEFAULT 0," +
                "status TINYINT NOT NULL DEFAULT 1," +
                "create_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "KEY idx_category (category)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS shop_cart_item (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "user_id BIGINT NOT NULL," +
                "product_id BIGINT NOT NULL," +
                "quantity INT NOT NULL DEFAULT 1," +
                "checked TINYINT NOT NULL DEFAULT 1," +
                "create_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "UNIQUE KEY uk_user_product (user_id, product_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS shop_order (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "order_no VARCHAR(64) NOT NULL," +
                "user_id BIGINT NOT NULL," +
                "total_amount DECIMAL(10,2) NOT NULL," +
                "status INT NOT NULL DEFAULT 10," +
                "receiver_name VARCHAR(50)," +
                "receiver_phone VARCHAR(20)," +
                "receiver_address VARCHAR(300)," +
                "remark VARCHAR(200)," +
                "trade_no VARCHAR(64)," +
                "payment_time DATETIME," +
                "delivery_time DATETIME," +
                "finish_time DATETIME," +
                "create_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
                "UNIQUE KEY uk_order_no (order_no)," +
                "KEY idx_user (user_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS shop_order_item (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "order_id BIGINT NOT NULL," +
                "product_id BIGINT NOT NULL," +
                "product_name VARCHAR(100)," +
                "product_image VARCHAR(255)," +
                "price DECIMAL(10,2)," +
                "quantity INT," +
                "subtotal DECIMAL(10,2)," +
                "KEY idx_order (order_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS shop_review (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "product_id BIGINT NOT NULL," +
                "order_id BIGINT NOT NULL," +
                "user_id BIGINT NOT NULL," +
                "rating TINYINT NOT NULL DEFAULT 5," +
                "content VARCHAR(1000)," +
                "images VARCHAR(1000)," +
                "create_time DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "UNIQUE KEY uk_order_product (order_id, product_id)," +
                "KEY idx_product (product_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        // 商品真实图片（BLOB存储，接口 /api/shop/image/blob/{key} 出图）
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS shop_image_blob (" +
                "blob_key VARCHAR(64) PRIMARY KEY," +
                "mime VARCHAR(30) NOT NULL," +
                "size INT NOT NULL," +
                "data LONGBLOB NOT NULL," +
                "create_time DATETIME DEFAULT CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

        // 老库迁移：v2数码商城新增划线原价列
        ensureColumn("shop_product", "original_price",
                "original_price DECIMAL(10,2) DEFAULT NULL AFTER price");

        log.info("网上商店数据表已就绪");
    }

    /**
     * 老库平滑迁移：列不存在时补建
     */
    private void ensureColumn(String table, String column, String definition) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                Integer.class, table, column);
        if (n != null && n == 0) {
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + definition);
            log.info("已为 {} 补充列 {}", table, column);
        }
    }

    private void seedData() {
        // 管理员
        if (userMapper.selectCount(new QueryWrapper<ShopUser>().eq("username", "admin")) == 0) {
            ShopUser admin = new ShopUser();
            admin.setUsername("admin");
            admin.setPassword(ENCODER.encode("admin123"));
            admin.setNickname("店主");
            admin.setRole(ShopUser.ROLE_ADMIN);
            admin.setStatus(1);
            admin.setCreateTime(LocalDateTime.now());
            userMapper.insert(admin);
            log.info("已创建管理员账号 admin / admin123");
        }
        // 演示买家
        if (userMapper.selectCount(new QueryWrapper<ShopUser>().eq("username", "demo")) == 0) {
            ShopUser demo = new ShopUser();
            demo.setUsername("demo");
            demo.setPassword(ENCODER.encode("123456"));
            demo.setNickname("演示买家");
            demo.setRole(ShopUser.ROLE_USER);
            demo.setStatus(1);
            demo.setCreateTime(LocalDateTime.now());
            userMapper.insert(demo);
            log.info("已创建演示买家账号 demo / 123456");
        }

        // 演示商品（仅首次启动）
        if (productMapper.selectCount(null) > 0) {
            return;
        }

        // ==================== 3C数码商城演示商品（真实产品图存于 shop_image_blob） ====================
        insertProduct("Apple MacBook Air 13.6英寸 M5（16G+256G）", "笔记本电脑", "8999.00", "9499.00", 200, 1560, 3,
                "M5芯片 · 13.6英寸Liquid视网膜屏 · 最薄1.13kg · 18小时续航",
                "【核心配置】\n芯片：Apple M5（10核中央处理器 · 8核图形处理器 · 16核神经网络引擎）\n内存：16GB统一内存\n存储：256GB SSD\n屏幕：13.6英寸Liquid视网膜显示屏（2560×1664 · 500尼特 · 支持10亿色彩）\n\n【产品特点】\n1. M5芯片对比M1提速最高2倍，无风扇静音设计\n2. 最薄处1.13cm、重量1.24kg，便携无负担\n3. 最长18小时电池续航，一充用一天\n4. 支持两台外接显示器，磁吸MagSafe 3充电\n5. 1080p FaceTime高清摄像头+三麦克风阵列\n\n【接口】\nMagSafe 3磁吸充电口×1、雷雳/USB 4×2、3.5mm耳机孔\n\n【包装清单】\nMacBook Air×1、USB-C转MagSafe 3连接线（2米）×1、35W双USB-C口便携式电源适配器×1",
                "macbook-air");
        insertProduct("联想拯救者 Y9000P 2025（i9-14900HX 32G 1T RTX5070Ti）", "笔记本电脑", "11299.00", "11999.00", 120, 892, 45,
                "14代i9旗舰HX · RTX5070Ti 8G独显 · 2.5K 240Hz电竞屏",
                "【核心配置】\n处理器：英特尔酷睿 i9-14900HX（24核32线程 · 最高睿频5.8GHz）\n显卡：NVIDIA GeForce RTX 5070Ti 笔记本GPU 8GB GDDR7\n内存：32GB DDR5 5600\n硬盘：1TB PCIe 4.0 NVMe SSD\n屏幕：16英寸2.5K（2560×1600）IPS · 240Hz · 500尼特 · 100% sRGB\n\n【产品特点】\n1. 霜刃Pro双液冷散热系统，满血性能释放140W\n2. 240Hz高刷电竞屏，3ms响应，游戏画面丝滑\n3. 支持独显直连/混合模式切换，游戏办公两不误\n4. 80Wh大电池+300W适配器，充电30分钟回血50%\n\n【包装清单】\nY9000P×1、300W电源适配器×1、说明书×1",
                "legion-y9000p", "legion-y9000p-2");
        insertProduct("华为 MateBook X Pro（Ultra9 32G 1T）", "笔记本电脑", "11199.00", "11999.00", 80, 654, 60,
                "980克超轻薄 · Ultra9旗舰处理器 · 3.1K原色全面屏",
                "【核心配置】\n处理器：英特尔酷睿 Ultra 9 185H（16核22线程）\n内存：32GB LPDDR5X\n硬盘：1TB NVMe SSD\n屏幕：14.2英寸3.1K（3120×2080）原色全面屏 · 120Hz · 100% P3色域\n\n【产品特点】\n1. 镁合金机身轻至980克，轻薄与性能兼得\n2. 超级中转站+超级终端，手机平板无缝协同\n3. 70Wh大电池，本地1080P视频播放最长14小时\n4. 电容式压感触控板，顺手好用\n\n【包装清单】\nMateBook X Pro×1、90W USB-C便携适配器×1、USB-C数据线×1",
                "matebook-x-pro", "matebook-x-pro-2");
        insertProduct("Apple iPhone 16 Pro 256GB 沙漠色钛金属", "智能手机", "8499.00", "8999.00", 300, 3210, 30,
                "A18 Pro芯片 · 4800万融合式四合一镜头 · 钛金属边框",
                "【核心配置】\n芯片：A18 Pro（6核CPU · 6核GPU · 16核神经网络引擎）\n存储：256GB\n屏幕：6.3英寸超视网膜XDR显示屏（2622×1206 · ProMotion 120Hz · 峰值亮度2000尼特）\n摄像头：4800万融合式主摄 + 4800万超广角 + 1200万5倍长焦\n\n【产品特点】\n1. 第五代钛金属设计，迄今最轻Pro\n2. 相机控制按键，指尖快速取景调参\n3. 支持4K 120fps杜比视界视频拍摄\n4. USB 3高速传输 + 全天候电池续航\n\n【包装清单】\niPhone 16 Pro×1、USB-C充电线×1、资料×1",
                "iphone-16-pro", "iphone-16-pro-2");
        insertProduct("华为 Mate 70 Pro 12GB+512GB", "智能手机", "6499.00", "6999.00", 250, 2140, 75,
                "红枫原色摄像头 · 第二代玄武架构 · 卫星通信",
                "【核心配置】\n处理器：麒麟9020\n内存：12GB+512GB\n屏幕：6.9英寸OLED（1-120Hz LTPO · 2500尼特峰值亮度）\n摄像头：5000万超光变主摄（F1.4-F4.0可变光圈）+ 4000万超广角 + 4800万长焦微距\n电池：5500mAh · 100W有线快充 · 80W无线快充\n\n【产品特点】\n1. 红枫原色摄像头，150多万种颜色还原\n2. 第二代玄武架构+玄武钢化昆仑玻璃，抗摔耐用\n3. 支持北斗卫星消息+天通卫星通话\n4. 鸿蒙HarmonyOS原生系统，流畅安全\n\n【包装清单】\nMate 70 Pro×1、100W充电器×1、USB-C线×1、保护壳×1",
                "mate-70-pro");
        insertProduct("小米15 Pro 16GB+512GB 黑色", "智能手机", "5299.00", "5799.00", 350, 1870, 50,
                "骁龙8至尊版 · 徕卡三摄 · 6100mAh金沙江电池 · 90W秒充",
                "【核心配置】\n处理器：第三代骁龙8至尊版（3nm制程）\n内存：16GB LPDDR5X + 512GB UFS 4.1\n屏幕：6.73英寸2K LTPO AMOLED（1-120Hz自适应 · 3200尼特峰值亮度）\n摄像头：徕卡5000万主摄（光影猎人900）+ 5000万超广角 + 5000万浮动长焦\n电池：6100mAh金沙江电池 · 90W有线秒充 · 50W无线秒充\n\n【产品特点】\n1. 徕卡光学Summilux镜头，大师人像直出\n2. 金沙江电池技术，-20℃低温照样耐用\n3. 超声波指纹+小米星辰通信\n4. 澎湃OS 3，跨端互联体验流畅\n\n【包装清单】\n小米15 Pro×1、90W充电器×1、USB-C线×1、保护壳×1",
                "xiaomi-15-pro");
        insertProduct("三星 Galaxy S25 Ultra 12GB+256GB", "智能手机", "9699.00", "10199.00", 150, 720, 6,
                "骁龙8至尊版for Galaxy · 2亿像素 · S Pen内置 · 钛金属边框",
                "【核心配置】\n处理器：骁龙8至尊版for Galaxy（超频版）\n内存：12GB+256GB\n屏幕：6.9英寸QHD+ Dynamic AMOLED 2X（1-120Hz · 2600尼特峰值）\n摄像头：2亿像素主摄 + 5000万超广角 + 1000万3倍长焦 + 5000万5倍长焦\n电池：5000mAh · 45W有线快充\n\n【产品特点】\n1. 2亿像素自适应像素传感器，夜景进光量大幅提升\n2. 内置S Pen，办公创作随手记\n3. 钛金属边框+康宁大猩猩Armor玻璃\n4. Galaxy AI全套AI功能：即圈即搜、通话实时翻译\n\n【包装清单】\nGalaxy S25 Ultra×1、S Pen（内置）×1、USB-C线×1、取卡针×1",
                "galaxy-s25-ultra");
        insertProduct("Apple iPad Pro 11英寸 M4（256G WLAN）", "平板电脑", "8999.00", "9499.00", 150, 1080, 90,
                "M4芯片 · Ultra Retina XDR屏 · 历来最薄iPad · 支持Apple Pencil Pro",
                "【核心配置】\n芯片：Apple M4（9核CPU · 10核GPU）\n存储：256GB\n屏幕：11英寸Ultra Retina XDR（2420×1668 · 120Hz ProMotion · 1000尼特SDR/1600尼特峰值HDR）\n摄像头：1200万广角+激光雷达扫描仪\n\n【产品特点】\n1. 历来最强iPad芯片，性能秒杀多数笔记本\n2. 厚度仅5.3mm，史上最薄Apple设备\n3. 支持Apple Pencil Pro：挤压感应+触觉反馈\n4. 横向前置1200万超广角镜头，视频通话更自然\n\n【包装清单】\niPad Pro×1、USB-C充电线×1",
                "ipad-pro-m4", "ipad-pro-m4-2");
        insertProduct("Apple iPad Air 11英寸 M3（128G WLAN）", "平板电脑", "4799.00", "4999.00", 250, 2340, 40,
                "M3芯片 · 液态视网膜屏 · 支持Apple Pencil Pro · 五色可选",
                "【核心配置】\n芯片：Apple M3（8核CPU · 9核GPU · 16核神经网络引擎）\n存储：128GB\n屏幕：11英寸液态视网膜显示屏（2360×1640 · 500尼特 · P3广色域）\n摄像头：1200万广角后置 + 横向前置Center Stage超广角\n\n【产品特点】\n1. M3芯片性能强劲，办公剪片轻松胜任\n2. 支持Apple Pencil Pro与妙控键盘，生产力拉满\n3. Wi-Fi 6E高速无线\n4. 星光/深空灰/蓝色/紫色/粉色五色可选\n\n【包装清单】\niPad Air×1、USB-C充电线×1",
                "ipad-air");
        insertProduct("华为 MatePad Pro 13.2英寸（12+256G）", "平板电脑", "5699.00", "5999.00", 100, 560, 25,
                "2.8K柔性OLED · 3.4mm超窄边框 · 星闪笔 · 10100mAh",
                "【核心配置】\n处理器：麒麟9010\n内存：12GB+256GB\n屏幕：13.2英寸2.8K（2800×1840）柔性OLED · 144Hz · 1000尼特峰值亮度\n电池：10100mAh · 100W超级快充\n\n【产品特点】\n1. 3.4mm超窄边框，94%超高屏占比\n2. 柔性OLED原色显示，Delta E<1专业色准\n3. 支持第三代M-Pencil（星闪连接），万级压感\n4. 多屏协同+PC应用引擎，平板秒变电脑\n\n【包装清单】\nMatePad Pro×1、100W充电器×1、USB-C线×1",
                "matepad-pro-13", "matepad-pro-13-2");
        insertProduct("小米平板7 Pro 11.2英寸（8+256G）", "平板电脑", "2299.00", "2599.00", 400, 3100, 12,
                "骁龙8s Gen3 · 3.2K 144Hz · 澎湃OS · 支持焦点触控笔",
                "【核心配置】\n处理器：第三代骁龙8s（旗舰平台）\n内存：8GB+256GB\n屏幕：11.2英寸3.2K（3200×2136） · 144Hz高刷 · 800尼特\n电池：8850mAh · 67W快充\n\n【产品特点】\n1. 3:2生产力比例大屏，办公网课两相宜\n2. 支持焦点触控笔与焦点键盘，秒变生产力工具\n3. 四扬声器杜比全景声\n4. 澎湃OS跨端互联，与小米手机无缝协作\n\n【包装清单】\n小米平板7 Pro×1、67W充电器×1、USB-C线×1",
                "xiaomi-pad-7-pro", "xiaomi-pad-7-pro-2");
        insertProduct("NVIDIA GeForce RTX 5090 Founders Edition 32G", "显卡", "16999.00", "18999.00", 30, 156, 18,
                "Blackwell架构 · 32GB GDDR7 · 21760核心 · 4K光追旗舰之王",
                "【核心规格】\n架构：NVIDIA Blackwell\nCUDA核心：21760个\n显存：32GB GDDR7 · 512bit · 1792GB/s带宽\n加速频率：2.41GHz · TGP功耗575W\n接口：PCIe 5.0 · HDMI 2.1b×1 · DisplayPort 2.1b×3\n\n【产品特点】\n1. 第五代Tensor Core，AI算力高达3352 TOPS，本地跑大模型利器\n2. 第四代RT Core， fullPath光追性能翻倍\n3. DLSS 4多帧生成，4K 240fps游戏触手可及\n4. 双槽流线型公版散热，做工质感一流\n\n【推荐电源】850W及以上（含16pin 12V-2×6供电线）",
                "rtx-5090");
        insertProduct("NVIDIA GeForce RTX 5080 Founders Edition 16G", "显卡", "8299.00", "8999.00", 60, 428, 22,
                "Blackwell架构 · 16GB GDDR7 · 10752核心 · 4K高刷甜品旗舰",
                "【核心规格】\n架构：NVIDIA Blackwell\nCUDA核心：10752个\n显存：16GB GDDR7 · 256bit · 960GB/s带宽\n加速频率：2.62GHz · TGP功耗360W\n接口：PCIe 5.0 · HDMI 2.1b×1 · DisplayPort 2.1b×3\n\n【产品特点】\n1. DLSS 4多帧生成，4K 144Hz游戏轻松达成\n2. 第三代光追引擎，赛博朋克2077全特效流畅运行\n3. AV1/AV2双编码器，直播推片效率大增\n4. 公版双槽散热设计，小机箱也能装\n\n【推荐电源】750W及以上（含16pin 12V-2×6供电线）",
                "rtx-5080", "rtx-5080-2");
        insertProduct("微星 RTX 4060 GAMING X OC 8G", "显卡", "2399.00", "2599.00", 200, 2680, 120,
                "三风扇刀锋散热 · 一键超频 · 1080P游戏甜点卡",
                "【核心规格】\n架构：NVIDIA Ada Lovelace\nCUDA核心：3072个\n显存：8GB GDDR6 · 128bit\n加速频率：2595MHz（OC模式） · TGP功耗115W\n接口：PCIe 4.0 · HDMI 2.1×1 · DisplayPort 1.4a×3\n\n【产品特点】\n1. TORX FAN 5.0三风扇刀锋散热，低温静音\n2. 微星中心软件一键超频，灯效同步自定义\n3. DLSS 3帧生成，1080P高刷游戏稳稳拿捏\n4. 双滚珠轴承风扇，耐用度更长\n\n【推荐电源】550W及以上",
                "rtx-4060");
        insertProduct("AMD Radeon RX 7900 XTX 公版 24G", "显卡", "7399.00", "7999.00", 0, 312, 35,
                "RDNA3旗舰 · 24GB GDDR6 · 4K游戏大显存利器",
                "【核心规格】\n架构：AMD RDNA 3\n流处理器：6144个\n显存：24GB GDDR6 · 384bit · 960GB/s带宽\n加速频率：2.5GHz · TBP功耗355W\n接口：PCIe 4.0 · HDMI 2.1a×2 · DisplayPort 2.1×2\n\n【产品特点】\n1. 24GB超大显存，4K材质全开毫无压力\n2. 芯片小芯片（Chiplet）设计，能效比大幅提升\n3. AMD智能访问显存+FSR 3超分辨率，帧率飙升\n4. 公版三风扇真空腔均热板散热\n\n【推荐电源】800W及以上",
                "rx-7900xtx");
        insertProduct("戴尔 U2723QE 27英寸 4K IPS Black显示器", "显示器", "3999.00", "4599.00", 90, 470, 55,
                "IPS Black技术 · 4K HDR400 · 90W Type-C一线通 · 硬件防蓝光",
                "【核心参数】\n尺寸：27英寸 16:9\n分辨率：3840×2160 4K\n面板：IPS Black（对比度2000:1）\n色域：98% DCI-P3 · 10.7亿色 · 出厂校色Delta E<2\n刷新率：60Hz · HDR400认证\n接口：HDMI 2.0×1 · DP 1.4×1 · USB-C 90W×1 · USB Hub×4 · RJ45网口\n\n【产品特点】\n1. IPS Black革命性面板，暗部细节与对比度大幅提升\n2. 90W Type-C一线连笔记本：充电+传输+扩展全搞定\n3. ComfortView Plus硬件级防蓝光，不闪屏更护眼\n4. 旋转升降支架+VESA壁挂，人体工学满分\n\n【适用人群】\n程序员、设计师、办公生产力用户",
                "dell-u2723qe");
        insertProduct("AOC Q27G3XM 27英寸 2K 180Hz MiniLED显示器", "显示器", "1499.00", "1799.00", 300, 1560, 9,
                "1152分区MiniLED · HDR1000 · 2K 180Hz Fast IPS · 电竞卷王",
                "【核心参数】\n尺寸：27英寸 16:9\n分辨率：2560×1440 2K\n面板：Fast IPS · 1ms GTG响应\n刷新率：180Hz（DP线）\n背光：1152分区MiniLED · HDR1000认证 · 10bit\n接口：DP 1.4×2 · HDMI 2.0×2 · 音频输出\n\n【产品特点】\n1. 千元档1152分区MiniLED，暗场细节吊打普通IPS\n2. 180Hz+1ms极速响应，FPS游戏先人一步\n3. 支持Adaptive-Sync防撕裂\n4. 人体工学支架（升降/旋转/俯仰）\n\n【适用人群】\n电竞玩家、主机游戏、HDR内容爱好者",
                "aoc-q27g3xm");
        insertProduct("华为 WATCH GT 5 Pro 46mm 云杉绿", "智能穿戴", "2288.00", "2488.00", 180, 980, 15,
                "钛金属表体 · 14天续航 · 专业运动健康 · 向日葵定位系统",
                "【核心配置】\n表体：航天级钛金属 · 46mm\n屏幕：1.43英寸AMOLED（466×466）\n续航：典型使用14天 · 磁吸无线充电\n防水：5ATM+IP68 · 自由潜泳100米\n\n【产品特点】\n1. 向日葵定位系统，运动轨迹精准不漂移\n2. 专业跑姿分析，跑步动力7项数据\n3. 情绪健康助手+微体检，健康数据一目了然\n4. 支持100+运动模式，高尔夫/自由潜水专业模式\n5. 蓝牙通话+音乐播放+NFC刷卡\n\n【包装清单】\nWATCH GT 5 Pro×1、无线充电底座×1、说明书×1",
                "watch-gt5-pro");
        insertProduct("Apple AirPods Pro 2 主动降噪无线耳机（USB-C）", "智能音频", "1899.00", "1999.00", 400, 4200, 70,
                "H2芯片 · 2倍主动降噪 · 自适应通透 · 精确查找",
                "【核心配置】\n芯片：Apple H2\n降噪：主动降噪提升至2倍 · 自适应通透模式\n音质：个性化空间音频 · 动态头部追踪\n续航：单次6小时（开启降噪） · 配合充电盒最长30小时\n\n【产品特点】\n1. H2芯片驱动，降噪与音质双升级\n2. 自适应通透模式，外界声音自然清晰\n3. 四副硅胶耳塞（XS-XL），佩戴贴合稳固\n4. 充电盒支持精确查找+扬声器发声\n5. IP54防尘抗汗，运动可用\n\n【包装清单】\nAirPods Pro×1、MagSafe充电盒（USB-C）×1、硅胶耳塞×4、USB-C线×1",
                "airpods-pro-2");

        // 演示评价
        ShopUser demo = userMapper.selectOne(new QueryWrapper<ShopUser>().eq("username", "demo"));
        ShopUser admin = userMapper.selectOne(new QueryWrapper<ShopUser>().eq("username", "admin"));
        insertReview(1L, demo.getId(), 5, "M5性能溢出，日常办公加轻度剪辑毫无压力，续航真的能撑一整天，风扇一次都没转过。");
        insertReview(2L, demo.getId(), 5, "5070Ti跑2K全高画质稳得一批，240Hz屏打游戏太爽了，就是适配器砖头有点大。");
        insertReview(3L, admin.getId(), 5, "980克带32G内存全网找不出第二台，3.1K屏看代码是真舒服。");
        insertReview(4L, admin.getId(), 5, "沙漠色钛金属质感绝了，相机控制键用过就回不去，256G日常够用。");
        insertReview(6L, demo.getId(), 5, "徕卡人像直出真的能打，6100mAh重度使用一天半，90W回血速度飞快。");
        insertReview(8L, demo.getId(), 5, "目前最强平板没有之一，配Pencil Pro写画手感接近纸笔，值回票价。");
        insertReview(9L, admin.getId(), 4, "给孩子上网课买的，M3性能过剩但求个安心，就是妙控键盘要另购有点抠。");
        insertReview(12L, admin.getId(), 5, "4K光追全特效拉满，顺便本地跑大模型也快，就是价格确实顶。");
        insertReview(14L, demo.getId(), 5, "1080P游戏的甜点选择，微星散热压得很安静，装完驱动直接爽。");
        insertReview(16L, demo.getId(), 4, "IPS Black对比度提升肉眼可见，Type-C一线连Mac太方便，就是60Hz打游戏不行。");
        insertReview(17L, demo.getId(), 5, "千元档MiniLED卷王，HDR效果炸裂，玩FPS拉满180Hz丝般顺滑。");
        insertReview(18L, demo.getId(), 5, "钛表壳质感在线，14天续航名不虚传，跑姿分析对提速真有帮助。");
        insertReview(19L, demo.getId(), 5, "降噪依旧是第一梯队，通透模式像没戴耳机，通勤党无脑入。");

        // 重算评分
        for (long pid = 1; pid <= 19; pid++) {
            productMapper.refreshRating(pid);
        }
        log.info("数码商城演示数据已初始化（19个商品，图片来自 shop_image_blob）");
    }

    /**
     * @param imageKeys shop_image_blob 中的图片key，第一个为主图，其余为详情图
     */
    private void insertProduct(String name, String category, String price, String originalPrice,
                               int stock, int sales, int daysAgo,
                               String brief, String detail, String... imageKeys) {
        ShopProduct product = new ShopProduct();
        product.setName(name);
        product.setCategory(category);
        product.setBrief(brief);
        product.setDetail(detail);
        product.setPrice(new BigDecimal(price));
        if (originalPrice != null && !originalPrice.isBlank()) {
            product.setOriginalPrice(new BigDecimal(originalPrice));
        }
        product.setStock(stock);
        product.setSales(sales);
        product.setRatingAvg(new BigDecimal("5.0"));
        product.setRatingCount(0);
        product.setStatus(1);
        product.setCreateTime(LocalDateTime.now().minusDays(daysAgo));
        product.setUpdateTime(LocalDateTime.now());
        productMapper.insert(product);

        // 真实图片（数据库BLOB）；无key时回退动态SVG占位图
        if (imageKeys.length > 0 && !imageKeys[0].isBlank()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < imageKeys.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append("/api/shop/image/blob/").append(imageKeys[i]);
            }
            product.setMainImage("/api/shop/image/blob/" + imageKeys[0]);
            product.setImages(sb.toString());
        } else {
            String base = "/api/shop/image/p" + product.getId() + "-";
            product.setMainImage(base + "1");
            product.setImages(base + "1," + base + "2," + base + "3");
        }
        productMapper.updateById(product);
    }

    private void insertReview(Long productId, Long userId, int rating, String content) {
        ShopReview review = new ShopReview();
        review.setProductId(productId);
        review.setUserId(userId);
        // 演示评价使用占位orderId，不影响真实评价唯一键校验
        review.setOrderId(900000L + productId * 10 + userId);
        review.setRating(rating);
        review.setContent(content);
        review.setCreateTime(LocalDateTime.now().minusDays(rating));
        reviewMapper.insert(review);
    }
}
