-- =====================================================
-- 网上商店数据库表结构（MySQL 8.x, 数据库 kb_db）
-- 应用启动时会自动执行本建表语句（IF NOT EXISTS），可手动执行
-- =====================================================

-- 用户表
CREATE TABLE IF NOT EXISTS shop_user (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    username    VARCHAR(50)  NOT NULL COMMENT '用户名',
    password    VARCHAR(100) NOT NULL COMMENT 'BCrypt加密密码',
    nickname    VARCHAR(50)  COMMENT '昵称',
    avatar      VARCHAR(255) COMMENT '头像URL',
    phone       VARCHAR(20)  COMMENT '手机号',
    email       VARCHAR(100) COMMENT '邮箱',
    role        VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '角色：USER/ADMIN',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1正常 0禁用',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='商店用户表';

-- 商品表
CREATE TABLE IF NOT EXISTS shop_product (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '商品ID',
    name         VARCHAR(100) NOT NULL COMMENT '商品名称',
    category     VARCHAR(50)  COMMENT '商品分类',
    brief        VARCHAR(500) COMMENT '商品简介',
    detail       TEXT         COMMENT '商品详情（多行文本）',
    price        DECIMAL(10, 2) NOT NULL COMMENT '价格（元）',
    stock        INT          NOT NULL DEFAULT 0 COMMENT '库存',
    main_image   VARCHAR(255) COMMENT '主图URL',
    images       TEXT         COMMENT '图集URL，逗号分隔',
    sales        INT          NOT NULL DEFAULT 0 COMMENT '销量',
    rating_avg   DECIMAL(3, 1) DEFAULT 5.0 COMMENT '平均评分',
    rating_count INT          NOT NULL DEFAULT 0 COMMENT '评价数',
    status       TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1上架 0下架',
    create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY idx_category (category)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='商品表';

-- 购物车表
CREATE TABLE IF NOT EXISTS shop_cart_item (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '购物车项ID',
    user_id    BIGINT      NOT NULL COMMENT '用户ID',
    product_id BIGINT      NOT NULL COMMENT '商品ID',
    quantity   INT         NOT NULL DEFAULT 1 COMMENT '数量',
    checked    TINYINT     NOT NULL DEFAULT 1 COMMENT '是否勾选：1是 0否',
    create_time DATETIME   DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    update_time DATETIME   DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_user_product (user_id, product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='购物车表';

-- 订单表
CREATE TABLE IF NOT EXISTS shop_order (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '订单ID',
    order_no         VARCHAR(64) NOT NULL COMMENT '订单号（支付宝out_trade_no）',
    user_id          BIGINT      NOT NULL COMMENT '用户ID',
    total_amount     DECIMAL(10, 2) NOT NULL COMMENT '订单总金额',
    status           INT         NOT NULL DEFAULT 10 COMMENT '状态：10待支付 20已支付 30已发货 40已完成 50已取消',
    receiver_name    VARCHAR(50)  COMMENT '收货人',
    receiver_phone   VARCHAR(20)  COMMENT '收货电话',
    receiver_address VARCHAR(300) COMMENT '收货地址',
    remark           VARCHAR(200) COMMENT '订单备注',
    trade_no         VARCHAR(64)  COMMENT '支付宝交易号',
    payment_time     DATETIME     COMMENT '支付时间',
    delivery_time    DATETIME     COMMENT '发货时间',
    finish_time      DATETIME     COMMENT '完成时间',
    create_time      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='订单表';

-- 订单明细表
CREATE TABLE IF NOT EXISTS shop_order_item (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '明细ID',
    order_id      BIGINT        NOT NULL COMMENT '订单ID',
    product_id    BIGINT        NOT NULL COMMENT '商品ID',
    product_name  VARCHAR(100)  COMMENT '商品名称快照',
    product_image VARCHAR(255)  COMMENT '商品主图快照',
    price         DECIMAL(10, 2) COMMENT '成交单价快照',
    quantity      INT           COMMENT '数量',
    subtotal      DECIMAL(10, 2) COMMENT '小计',
    KEY idx_order (order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='订单明细表';

-- 商品评价表
CREATE TABLE IF NOT EXISTS shop_review (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '评价ID',
    product_id  BIGINT       NOT NULL COMMENT '商品ID',
    order_id    BIGINT       NOT NULL COMMENT '订单ID',
    user_id     BIGINT       NOT NULL COMMENT '评价用户ID',
    rating      TINYINT      NOT NULL DEFAULT 5 COMMENT '评分1-5星',
    content     VARCHAR(1000) COMMENT '评价内容',
    images      VARCHAR(1000) COMMENT '评价图片，逗号分隔',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '评价时间',
    UNIQUE KEY uk_order_product (order_id, product_id),
    KEY idx_product (product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='商品评价表';
