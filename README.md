# 词灵学园 Ciling-Academy

![version](https://img.shields.io/badge/version-1.0.0-10b981) ![JDK](https://img.shields.io/badge/JDK-17-blue) ![Spring%20Boot](https://img.shields.io/badge/Spring%20Boot-3.4.5-6db33f) ![Vue](https://img.shields.io/badge/Vue-3.5-42b883) ![MySQL](https://img.shields.io/badge/MySQL-8-4479a1) ![Redis](https://img.shields.io/badge/Redis-7-ff4438)

一个 AI 增强型英语单词学习平台。以艾宾浩斯记忆曲线安排复习计划，以词灵AI 辅助理解
长难句、阅读与翻译，配合试卷测验、错题本与游戏化激励，让"背了就忘"变成"真正记住"。

**设计理念：AI 增强，而非 AI 驱动。** 大模型只负责内容生成；背单词、复习调度、
试卷批改、游戏计分等核心业务全部由程序实现。关闭词灵AI，基础学习功能完全不受影响。

## 功能特性

**学习**
- 背单词与艾宾浩斯复习：到期词优先、逾期越久越靠前，熟悉度四级流转
- 生词本：答错降熟悉度并清零复习计数，复习答对升级
- 试卷与错题本：AI 生成多题型试卷、自动批改，错题分页管理、批量移除
- 学习报告：今日已学/待复习/连续天数一目了然

**词灵AI**
- 长难句分析助手 / 阅读助手 / 翻译助手 / 自由答疑
- 结构化内容渲染为卡片，可一键收藏为 AI 笔记
- 额度管控 + 全局内容缓存 + RAG 知识增强 + 多层降级容错

**趣味与激励**
- 10 款单词小游戏：金币、星星、排行榜，跨游戏词池去重防重复刷词
- 金币商城：勋章、称号、词库包、备考资料（后端动态生成 PDF）
- 成长体系：20 级等级、23 项成就、连续签到、活跃度任务与节点奖励

**站点**
- 个人中心 / 今日任务 / 工具盒 / 静态文档页（用户协议、隐私政策、帮助中心等）

## 技术栈

| 端 | 技术 |
| --- | --- |
| 后端 | Spring Boot 3.4 · Spring Security Crypto（BCrypt）· MySQL 8 · Redis · Qdrant · MyBatis-Plus |
| 前端 | Vue 3.5 · Element Plus（子路径按需引入）· Vite 5 · Pinia · Vue Router · SCSS |
| AI | 通义千问（阿里云百炼 OpenAI 兼容接口）· Qdrant 向量检索 |

## 快速开始

### 环境要求

- JDK 17+ · Maven 3.8+ · Node.js 18+
- MySQL 8（默认 3306）· Redis（默认 6379）
- 阿里云百炼 API Key（可选：`qwen-flash` 有免费额度；不配置则 AI 功能降级，其余功能正常）

### 1. 初始化数据库

```bash
mysql -uroot -p < backend/src/main/resources/db/schema.sql
mysql -uroot -p < backend/src/main/resources/db/data.sql
```

### 2. 填写私密配置

仓库内**不含任何明文密钥**。首次运行前：

```bash
cd backend/src/main/resources
copy application-local.yml.example application-local.yml   # Windows
# cp application-local.yml.example application-local.yml   # macOS / Linux
```

编辑 `application-local.yml`，至少填入本地 MySQL 密码。该文件已被 `.gitignore`
排除；生产部署也可以不用该文件，改为注入同名环境变量（`DB_PASSWORD`、
`AI_API_KEY`、`OSS_ACCESS_KEY_ID` 等，详见 [application.yml](backend/src/main/resources/application.yml) 注释）。

### 3. 启动后端

```bash
cd backend
mvn spring-boot:run          # 默认 8080，API 响应自动 gzip 压缩
```

### 4. 启动前端

```bash
cd frontend
npm install
npm run dev                  # http://localhost:5173，/api 自动代理到 8080
```

## 目录结构

```
ciling-academy/
├─ backend/                          # Spring Boot 3 后端（详见 backend/README.md）
│  ├─ src/main/java/com/wordspirit/
│  │  ├─ ai/                         # 词灵AI 基础设施（额度/限流/缓存/RAG）
│  │  ├─ common/                     # 统一返回/异常/分页/用户上下文
│  │  ├─ config/                     # Web、CORS、密码加密、OSS、拦截器
│  │  └─ module/                     # 16 个业务模块（user、review、paper、game、shop…）
│  └─ src/main/resources/
│     ├─ application.yml             # 敏感项全部为 ${ENV:} 占位符
│     ├─ application-local.yml.example
│     └─ db/schema.sql               # 建表脚本
├─ frontend/                         # Vue 3 前端（详见 frontend/README.md）
│  ├─ src/                           # views / components / api / store / router
│  ├─ public/frames/                 # 词灵序列帧动画（WebP，4.1 MB）
│  └─ vite.config.js                 # 按需引入 + 构建优化
├─ docs/                             # 后端架构描述、调用流程与时序、设计稿
└─ deploy/                           # 部署辅助（LibreTranslate 容器等）
```

## 安全设计

| 威胁 | 防护措施 |
| --- | --- |
| 拖库撞库 | 密码使用 Spring Security **BCryptPasswordEncoder** 加盐哈希（$2a$10$），永不存储明文 |
| 密码爆破 | 登录同用户名连续失败 5 次锁定 15 分钟（Redis 计数），成功登录自动清除 |
| 用户枚举 | 登录失败统一返回「用户名或密码错误」 |
| 跨站滥用 | CORS 白名单仅放行指定前端来源（默认本机 5173，生产经 `APP_CORS_ORIGINS` 配置） |
| 会话劫持 | Token 为服务端随机 UUID 存 Redis（7 天滑动续期），可随时吊销 |
| 机器人刷量 | 图形验证码 + 邮箱验证码（60s 间隔、每日上限）+ AI 额度与 QPS 限流 |
| SQL 注入 | MyBatis-Plus 全参数化查询，无字符串拼接 SQL |
| 越权访问 | 登录拦截器统一鉴权；资源类接口服务端校验归属（如错题删除仅限本人） |
| 密钥泄露 | 全部密钥走环境变量 / gitignore 的 application-local.yml，仓库零明文密钥 |
| 抓包窃听 | 生产部署请启用 HTTPS；API 层已开启 gzip + 服务端会话校验 |

## 性能与流量优化

**前端**（详见 [frontend/README.md](frontend/README.md)）

- Element Plus 子路径按需引入：产物 -960 KB，最大 chunk 仅 112 KB
- 路由级代码分割 + `vue-vendor` 长效缓存分包，全站 JS 约 1.1 MB
- 词灵序列帧 PNG → WebP（76.4 MB → 4.1 MB）、logo 压缩（398 KB → 6/20 KB）
- Iconify 离线图标包：运行时零图标请求

**后端 / 接口**

- API 响应 gzip 压缩（JSON 压缩率约 70%~85%，浏览器自动解压）
- Redis Lettuce 连接池复用连接；SQL 日志走 Slf4j 分级控制
- AI 全局缓存：相同输入只调用一次大模型（Redis 热点 + MySQL 持久化）
- 词典走本地 MySQL + Redis 查询，不依赖外部 API

## 相关文档

- [后端架构描述](docs/后端架构描述.md)
- [后端调用流程与时序](docs/AI调用流程与时序.md)
- [前端工程说明](frontend/README.md)
- [后端工程说明](backend/README.md)

## 许可

本项目仅供学习交流使用。
