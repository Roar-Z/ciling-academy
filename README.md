# 词灵学园 Ciling Academy

![version](https://img.shields.io/badge/version-1.0.0-emeraldgreen) ![jdk](https://img.shields.io/badge/JDK-17-blue) ![springboot](https://img.shields.io/badge/Spring%20Boot-3.4.5-brightgreen) ![vue](https://img.shields.io/badge/Vue-3.5-42b883)

AI 增强型英语单词学习平台。艾宾浩斯记忆曲线 × 词灵AI 智能解析，每天 10 分钟，
让每个单词都真正被记住。

> **设计理念：AI 增强，而非 AI 驱动** —— 词灵AI 仅负责内容生成；背单词、艾宾浩斯复习、
> 试卷批改、游戏计分等核心业务全部由程序实现。关闭词灵AI，基础学习功能完全不受影响。

## 技术栈

| 端 | 技术 |
| --- | --- |
| 后端 | Spring Boot 3.4 · Spring Security Crypto（BCrypt）· MySQL 8 · Redis · Qdrant 向量库 · MyBatis-Plus · Hutool |
| 前端 | Vue 3.5 · Element Plus（子路径按需引入）· Vite 5 · Pinia · Vue Router · SCSS |
| AI | 通义千问 qwen-flash（阿里云百炼 OpenAI 兼容接口）· Qdrant RAG 知识增强 |
| 部署 | 服务器位于中国香港（无需 ICP 备案） |

## 功能总览

- **背单词**：艾宾浩斯复习、新词学习、生词本、复习批次/每日目标自定义
- **词灵AI**：长难句分析助手 / 阅读助手 / 翻译助手 / 自由答疑 / AI 笔记收藏
- **测验**：AI 试卷（5 种题型）+ 自动批改 + 错题本（分页管理、批量移除）
- **趣味乐园**：10 款单词小游戏 + 金币 + 排行榜 + 跨游戏词池去重
- **激励体系**：金币商城（勋章/称号/词库包/备考资料）、等级 20 级、成就 23 项、连续签到、活跃度任务
- **其他**：今日任务 / 学习报告 / 个人中心 / 工具盒 / 静态文档页（协议·帮助·学习指南）

## 目录结构

```
ciling-academy/
├─ backend/                         # Spring Boot 3 后端
│  ├─ src/main/java/com/wordspirit/
│  │  ├─ ai/                        # 词灵AI 基础组件（额度/限流/RAG/调用）
│  │  ├─ common/                    # 统一返回/异常/分页/用户上下文
│  │  ├─ config/                    # Web/CORS/密码加密/OSS/拦截器
│  │  └─ module/                    # 业务模块（user/dict/wordbook/paper/game/shop/...）
│  └─ src/main/resources/
│     ├─ application.yml            # 全部敏感项均为 ${ENV:} 占位符（无明文密钥）
│     ├─ application-local.yml.example  # 私密配置示例（复制为 application-local.yml 使用）
│     └─ db/schema.sql              # 建表脚本
├─ frontend/                        # Vue 3 前端（详见 frontend/README.md）
│  ├─ src/（views/components/api/store/router）
│  ├─ public/frames/                # 词灵序列帧动画（WebP，4.1 MB）
│  └─ vite.config.js                # 按需引入 + 构建优化
├─ deploy/                          # 部署辅助（LibreTranslate 容器等）
└─ docs/                            # AI 架构/调用流程文档（可粘贴进课程设计报告）
```

## 快速启动

### 环境要求

- JDK 17+ · Maven 3.8+ · Node.js 18+
- MySQL 8（默认 3306）· Redis（默认 6379）
- 阿里云百炼 API Key（`qwen-flash` 有免费额度；不配置则 AI 功能降级，其余功能正常）

### 1. 数据库

```bash
mysql -uroot -p < backend/src/main/resources/db/schema.sql
mysql -uroot -p < backend/src/main/resources/db/data.sql
```

### 2. 私密配置（重要）

仓库内**不含任何明文密钥**。首次运行前：

```bash
cd backend/src/main/resources
copy application-local.yml.example application-local.yml   # Windows
# cp application-local.yml.example application-local.yml   # macOS/Linux
```

编辑 `application-local.yml` 填入本地 MySQL 密码等真实值。
该文件已被 `.gitignore` 排除；生产部署也可以不用该文件，改为注入同名环境变量
（`DB_PASSWORD` / `AI_API_KEY` / `OSS_ACCESS_KEY_ID` ...，见 application.yml 注释）。

### 3. 后端

```bash
cd backend
mvn spring-boot:run          # 默认 8080，API 响应自动 gzip 压缩
```

### 4. 前端

```bash
cd frontend
npm install
npm run dev                  # http://localhost:5173，/api 自动代理到 8080
```

## 安全设计

| 威胁 | 防护措施 |
| --- | --- |
| 拖库撞库 | 密码使用 Spring Security **BCryptPasswordEncoder** 加盐哈希（$2a$10$），永不存储明文 |
| 密码爆破 | 登录同用户名连续失败 5 次锁定 15 分钟（Redis 计数），成功登录自动清除 |
| 用户枚举 | 登录失败统一返回「用户名或密码错误」 |
| CSRF/跨站 | CORS 白名单仅放行指定前端来源（默认本机 5173，生产经 `APP_CORS_ORIGINS` 配置），拒绝任意站点携凭证跨域 |
| 会话劫持 | Token 为服务端随机 UUID 存 Redis（7 天滑动续期），可随时吊销，无 JWT 泄露面 |
| 机器人/刷量 | 图形验证码 + 邮箱验证码（60s 间隔 + 每日 10 次上限）+ AI 额度/QPS 限流 |
| SQL 注入 | MyBatis-Plus 全参数化查询，无字符串拼接 SQL |
| 越权访问 | 登录拦截器统一鉴权；资源类接口服务端校验归属（如错题删除仅限本人） |
| 密钥泄露 | 全部密钥走环境变量 / gitignore 的 application-local.yml，仓库零明文密钥 |
| 抓包窃听 | 生产部署请务必启用 HTTPS（防中间人抓包）；API 层已开启 gzip + 服务端会话校验 |
| 信息泄露 | 全局异常处理统一返回友好文案，不向前端暴露堆栈与原始报错 |

## 性能与流量优化

**前端**（详见 frontend/README.md）

- Element Plus 子路径按需引入：产物 -960 KB，最大 chunk 仅 112 KB
- 路由级代码分割 + `vue-vendor` 长效缓存分包
- 词灵序列帧 PNG → WebP（76.4 MB → 4.1 MB）+ logo 压缩（398 KB → 6/20 KB）
- Iconify 离线图标包：运行时零图标请求
- 生产构建移除 console/debugger

**后端 / 接口**

- API 响应 gzip 压缩（JSON 压缩率约 70%~85%，浏览器自动解压）
- Redis Lettuce 连接池复用连接（额度/限流/会话高频读写）
- SQL 日志走 Slf4j 分级控制，生产不刷 stdout
- AI 全局缓存：相同输入只调用一次大模型（Redis 热点 + MySQL 持久化）
- 分页接口（错题 10 条/页等）+ 词典本地 MySQL+Redis 查询，不依赖外部 API

## 版本

- **v1.0.0**（2026-09）：首个开源版本。前后端整仓发布、敏感数据脱敏、
  Spring Security BCrypt 密码加密、登录防爆破、CORS 白名单收紧、
  前端构建链优化与序列帧 WebP 化。

## 文档

- [AI 调用流程与时序说明](docs/AI调用流程与时序.md)
- [AI 架构描述](docs/AI架构描述.md)
- [前端工程说明](frontend/README.md)

## License

仅供学习交流使用。
