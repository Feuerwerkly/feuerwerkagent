# FeuerwerkAgent - AI聊天、联网搜索、网上商店与即时通讯

这是一个基于Spring Boot和DeepSeek V4 Pro API的AI智能体，提供AI对话、联网搜索功能，内置完整的网上商店（支付宝沙箱支付）与即时通讯IM（好友/群聊/实时消息）。

## 功能特性

- **AI聊天功能**: 基于DeepSeek V4 Pro模型的智能对话
- **国内搜索**: 百度、360、搜狗等国内搜索引擎 🇨🇳 新功能
- **智能摘要**: AI驱动的搜索结果摘要
- **流式响应**: 实时反馈，避免超时问题 ⭐ 新功能
- **RESTful API**: 简单易用的HTTP接口
- **会话历史**: 支持上下文对话
- **超时优化**: 增加超时时间，支持长时间搜索

## 🇨🇳 国内搜索引擎支持

已完全替换为国内搜索引擎，解决访问问题：

- **百度搜索**（主要）- 中国最大搜索引擎
- **360搜索**（备用）- 安全搜索引擎
- **搜狗搜索**（备用）- 微信内容搜索强项

### 优势
- ✅ 无需VPN，国内直接访问
- ✅ 响应速度快，稳定性高
- ✅ 中文搜索结果更准确
- ✅ 自动多引擎备份机制

## 前置要求

- Java 17+
- Maven 3.6+
- DeepSeek API密钥

## 配置

1. 在 `src/main/resources/application.yaml` 中配置你的DeepSeek API密钥：

```yaml
anthropic:
  api-key: your-actual-api-key-here
```

或者通过环境变量设置：
```bash
export DEEPSEEK_API_KEY=your-actual-api-key-here
```

## 🚀 快速启动

### 方式1：一键启动（推荐）⭐
```bash
# 双击运行启动脚本
start-all.bat
```

这会自动启动：
- **后端服务**: `http://localhost:8080` (Spring Boot)
- **前端界面**: `http://localhost:5173` (Vue 3)

### 方式2：分别启动
```bash
# 终端1：启动后端
mvn spring-boot:run

# 终端2：启动前端
cd frontend
npm run dev
```

### 方式3：仅后端（静态HTML）
```bash
mvn spring-boot:run
# 访问 http://localhost:8080 使用HTML界面
```

## 🎯 界面选择

### Vue界面（推荐）⭐
- **地址**: `http://localhost:5173`
- **特点**: 真正的流式响应，现代化UI
- **功能**: 逐字显示，流畅体验

### HTML界面
- **地址**: `http://localhost:8080`
- **特点**: 静态HTML，简单直接
- **功能**: 基础对话和搜索

## 构建和运行

### 构建项目
```bash
mvn clean install
```

### 运行项目
```bash
mvn spring-boot:run
```

或者运行打包后的JAR：
```bash
java -jar target/feuerwerkagent-0.0.1-SNAPSHOT.jar
```

应用启动后将在 `http://localhost:8080` 提供服务。

## API接口

### 1. 健康检查
```
GET /api/ai/health
```

### 2. AI聊天功能

#### 简单聊天
```
GET /api/ai/chat/simple?message=你好
```

#### 完整聊天（支持历史记录）
```
POST /api/ai/chat
Content-Type: application/json

{
  "message": "你好，请介绍一下自己",
  "conversationHistory": [
    {"role": "user", "content": "之前的消息"},
    {"role": "assistant", "content": "之前的回复"}
  ],
  "temperature": 0.7,
  "maxTokens": 2000
}
```

#### 响应格式
```json
{
  "content": "AI的回复内容",
  "model": "deepseek-v4-pro",
  "usage": 1234,
  "success": true
}
```

### 3. 联网搜索功能

#### 简单搜索
```
GET /api/ai/search?query=最新AI技术发展
```

#### 完整搜索
```
POST /api/ai/search
Content-Type: application/json

{
  "query": "最新AI技术发展",
  "maxResults": 10,
  "enableAI": true
}
```

#### 响应格式
```json
{
  "query": "最新AI技术发展",
  "results": [
    {
      "title": "搜索结果标题",
      "url": "https://example.com",
      "snippet": "结果摘要",
      "source": "搜索来源"
    }
  ],
  "aiSummary": "AI生成的搜索结果总结",
  "success": true
}
```

## 使用示例

### 🌟 推荐：使用Web界面测试
启动服务后，直接在浏览器中访问：
```
http://localhost:8080
```
这将打开一个美观的测试界面，可以直接测试AI聊天和搜索功能。

### 使用curl测试AI聊天
```bash
curl -X POST http://localhost:8080/api/ai/chat/simple \
  -H "Content-Type: application/json" \
  -d '{"message": "你好"}'
```

### 使用curl测试搜索功能
```bash
curl -X POST http://localhost:8080/api/ai/search/simple \
  -H "Content-Type: application/json" \
  -d '{"query": "人工智能"}'
```

### 使用Postman测试

1. 导入以下API端点：
   - POST `http://localhost:8080/api/ai/chat`
   - GET `http://localhost:8080/api/ai/search`

2. 设置请求头：`Content-Type: application/json`

3. 发送请求体（对于POST请求）

## 技术栈

- Spring Boot 4.0.5
- DeepSeek API (Anthropic SDK兼容)
- OkHttp (HTTP客户端)
- Jackson (JSON处理)
- Lombok (减少样板代码)

## 项目结构

```
src/main/java/com/example/feuerwerkagent/
├── config/          # 配置类
│   ├── AnthropicConfig.java
│   ├── SearchConfig.java
│   └── WebConfig.java
├── controller/      # REST控制器
│   └── AIController.java
├── dto/            # 数据传输对象
│   ├── ChatRequest.java
│   ├── ChatResponse.java
│   ├── SearchRequest.java
│   └── SearchResponse.java
├── service/        # 业务逻辑
│   ├── ChatService.java
│   └── SearchService.java
└── FeuerwerkagentApplication.java
```

## 注意事项

1. 确保正确设置DeepSeek API密钥
2. 注意API调用频率限制
3. 搜索功能依赖外部搜索服务，可能会受到网络影响
4. 建议在生产环境中配置适当的日志和监控

---

# 🛒 网上商店（含支付宝沙箱支付）

前端导航栏点击 **🛒 网上商店** 进入。后端接口统一前缀 `/api/shop`，数据表 `shop_*`（启动时自动建表并写入演示数据）。

## 功能清单

| 模块 | 说明 |
|---|---|
| 用户子系统 | 注册/登录/注销（BCrypt加密 + Token鉴权）、个人资料、修改密码；管理员角色 |
| 商品 | 名称、分类、**价格**、**库存**、**简介**、**图文详情页**、销量、评分 |
| 商品图片 | 动态SVG生成图集（主图/卖点图/参数图）+ 图片上传（`/shop-images/`） |
| 商品搜索 | 关键词/分类/排序（最新/销量/价格）分页查询 |
| 购物车 | 加购、改数量、勾选、结算 |
| 订单（买卖核心） | 事务下单原子扣库存 → 支付宝支付 → 幂等支付回调加销量 → 发货 → 确认收货；取消订单回滚库存 |
| 商品评价 | 购买后才能评价、一单一商品一条、星级+文字、自动重算商品评分 |
| 管理后台 | 商品增删改/上下架/价格库存维护、订单发货、经营统计 |
| 支付宝沙箱 | 电脑网站支付 `alipay.trade.page.pay`、异步通知验签、主动查单 |

## 演示账号（首次启动自动创建）

- 管理员：`admin / admin123`（可进入"商品管理"）
- 演示买家：`demo / 123456`

## 支付宝沙箱配置（重要）

`src/main/resources/application.yaml` 中已按沙箱信息预填 APPID 与网关：

```yaml
alipay:
  app-id: 9021000169626849
  gateway-url: https://openapi-sandbox.dl.alipaydev.com/gateway.do
  seller-id: 2088721115700271
```

还需填入两个密钥（截图里点"查看"获得）：

1. 打开 https://open.alipay.com → 控制台 → **沙箱环境**
2. 「开发信息」→ 接口加签方式（**系统默认密钥 / 公钥模式**）→ 点击 **查看**
3. 复制 **应用私钥（PKCS8格式）** 填入 `merchant-private-key`，复制 **支付宝公钥** 填入 `alipay-public-key`（也可用环境变量 `ALIPAY_MERCHANT_PRIVATE_KEY` / `ALIPAY_ALIPAY_PUBLIC_KEY`）
4. 重启后端，商城订单点"💳 支付宝支付"会在新窗口打开沙箱收银台
5. 用沙箱 **买家账号**（沙箱控制台「沙箱账号」页可查）登录收银台完成付款

支付结果确认（三重保障）：

- **异步通知** `/api/shop/alipay/notify`：验签+校验appid/卖家/金额后幂等更新订单。本地开发支付宝服务器无法回调 localhost，如需真实回调可用 natapp/ngrok 穿透并把地址填入 `alipay.notify-url`
- **同步回跳**：支付完浏览器跳回 `http://localhost:5173/?payReturn=1`，前端自动查单
- **主动查单**：订单页"🔍 查询支付状态"按钮（支付后也会自动轮询）

## 商店API速览

```
POST /api/shop/auth/register|login|logout   GET /api/shop/auth/me
GET  /api/shop/products?keyword&category&sort&page&size
GET  /api/shop/products/{id}               GET /api/shop/categories
GET|POST /api/shop/cart   PUT|DELETE /api/shop/cart/{id}
POST /api/shop/orders    GET /api/shop/orders
POST /api/shop/orders/{id}/cancel|confirm
GET  /api/shop/reviews/product/{id}   POST /api/shop/reviews   GET /api/shop/reviews/mine
POST /api/shop/alipay/pay/{orderId}   POST /api/shop/alipay/notify   GET /api/shop/alipay/query
POST /api/shop/upload/image           GET /api/shop/image/p{id}-{1|2|3}（SVG图）
管理员：/api/shop/admin/products|orders|stats
```

需要登录的接口在请求头携带 `Authorization: Bearer <token>`。

## 商店数据表

`shop_user`（用户）、`shop_product`（商品：价格/库存/图集/简介/详情/评分）、`shop_cart_item`（购物车）、`shop_order`（订单）、`shop_order_item`（订单明细快照）、`shop_review`（评价）。建表SQL见 `src/main/resources/sql/shop_schema.sql`（启动时自动执行，幂等）。

## 许可证

MIT License

---

# 💬 即时通讯（IM）

前端导航栏点击 **💬 即时通讯** 进入。复用商城账号体系（`shop_user`，登录弹窗全局共用），WebSocket 长连接实时收发，MySQL 持久化（`im_*` 表启动时自动建表）。

## 功能清单

| 模块 | 说明 |
|---|---|
| 好友系统 | 搜索用户（用户名/昵称）→ 发送好友申请（附验证消息）→ 对方实时收到申请通知 → 同意/拒绝；同意后双方互为好友并可开聊；支持删除好友 |
| 单聊 | 实时消息（WebSocket）、文字/图片、乐观发送（发送中/失败状态）、历史消息分页加载、未读数、在线状态实时广播 |
| 群聊 | 发起群聊（多选好友）→ 群实时消息；群主可改群名/公告、邀请、踢人、解散；成员可退群；群成员在线状态 |
| 会话列表 | 单聊+群聊合并列表，最后消息预览（群消息带发言人）、时间、未读角标，按最新排序 |
| 消息通知 | 非当前会话/非IM视图收到消息弹桌面通知，点击跳转会话；导航栏 💬 按钮显示总未读 |
| 连接管理 | 25s心跳、断线指数退避重连、重连后自动刷新数据、token失效自动弹出登录 |

## 技术要点

- **通道**：`ws(s)://host/ws/im?token=<登录token>`，握手时校验token（401拒绝），支持同一账号多标签页多连接
- **上行消息**：`{type:"SEND", peerType:"SINGLE"|"GROUP", peerId, contentType:"TEXT"|"IMAGE", content, clientId}`、`{type:"PING"}`
- **下行事件**：`MESSAGE`（新消息）、`ACK`（发送确认，含clientId）、`PRESENCE`（好友上下线）、`FRIEND_REQUEST` / `FRIEND_ACCEPTED`、`GROUP_EVENT`（建群/邀请/移出/解散/更新）、`PONG`、`ERROR`
- **可靠性**：消息先落库再推送；离线消息以未读数+历史消息兜底；发送校验好友关系/群成员身份
- **图片消息**：复用 `/api/shop/upload/image` 上传，`contentType: "IMAGE"` 传URL

## IM接口速览（REST，均需登录）

```
GET  /api/im/contacts/search?keyword=        POST /api/im/contacts/requests（发申请）
GET  /api/im/contacts/requests               POST /api/im/contacts/requests/{id}/accept|reject
GET  /api/im/contacts/friends                DELETE /api/im/contacts/friends/{friendId}
POST /api/im/groups（建群）                   GET  /api/im/groups（我的群）
GET  /api/im/groups/{id}[/members]           POST /api/im/groups/{id}/members（邀请）
DELETE /api/im/groups/{id}/members/{uid}（踢） POST /api/im/groups/{id}/quit|disband
PUT  /api/im/groups/{id}（改名/公告）
GET  /api/im/chat/conversations              GET  /api/im/chat/messages?peerType&peerId&beforeId
POST /api/im/chat/read                       GET  /api/im/chat/unread-total
```

## IM数据表

`im_friendship`（好友，双向两行）、`im_friend_request`（好友申请）、`im_group`（群）、`im_group_member`（群成员）、`im_message`（消息：单聊存to_user_id、群聊存group_id）、`im_conversation`（会话读位，算未读数）。

## IM体验步骤

1. 开两个浏览器（或一个正常窗口+一个无痕窗口），分别登录两个账号（如 `demo/123456` 与 `admin/admin123`，或注册新号）
2. 在「好友」页搜索对方用户名 → 加好友 → 另一边点「申请」同意
3. 「会话」或「好友」里点开对方开始单聊，另一边实时收到消息与通知
4. 「群聊」页发起群聊勾选好友 → 群内实时群聊；群主可管理成员
5. 试试发图片、切换会话看未读角标、断网重连自动恢复
