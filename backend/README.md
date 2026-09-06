# 词灵学园 · 后端（Ciling-Academy Backend）

Spring Boot 3 单体后端，为前端提供 RESTful API。所有敏感配置走环境变量或本地私密文件，
仓库内零明文密钥。

## 环境要求

| 依赖 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 17+ | |
| Maven | 3.8+ | |
| MySQL | 8.x | 默认 3306 |
| Redis | 6.x+ | 会话 Token、验证码、额度、缓存 |

## 快速启动

### 1. 初始化数据库

```bash
mysql -uroot -p < src/main/resources/db/schema.sql
mysql -uroot -p < src/main/resources/db/data.sql
```

### 2. 私密配置

```bash
cd src/main/resources
copy application-local.yml.example application-local.yml   # Windows
# cp application-local.yml.example application-local.yml   # macOS / Linux
```

编辑 `application-local.yml` 至少填入 MySQL 密码。该文件已被 `.gitignore` 排除，
启动时由 `spring.config.import` 自动加载并覆盖占位符默认值。

生产部署可不用该文件，改为注入同名环境变量。

### 3. 运行

```bash
mvn spring-boot:run        # 默认 8080
```

## 配置说明

所有私密项在 `application.yml` 中均为 `${环境变量:默认值}` 占位符：

| 环境变量 | 说明 | 不配置时的行为 |
| --- | --- | --- |
| `DB_PASSWORD` | MySQL 密码 | 启动失败（必填） |
| `REDIS_HOST` / `REDIS_PASSWORD` | Redis 连接 | 默认 localhost / 空 |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | SMTP 发件邮箱与授权码 | 验证码仅打印在后端日志 |
| `AI_API_KEY` | 阿里云百炼 API Key | 词灵AI 功能降级，学习功能不受影响 |
| `QDRANT_URL` / `QDRANT_API_KEY` | Qdrant 云实例 | RAG 知识增强降级 |
| `OSS_ENDPOINT` / `OSS_BUCKET` / `OSS_ACCESS_KEY_ID` / `OSS_ACCESS_KEY_SECRET` | 阿里云 OSS | 头像等文件回退本地 `uploads/` 存储 |
| `APP_CORS_ORIGINS` | CORS 白名单（逗号分隔） | 默认 `http://localhost:5173,http://127.0.0.1:5173` |
| `MYMEMORY_EMAIL` | MyMemory 翻译授权邮箱 | 使用匿名免费额度 |

## 模块结构

```
com.wordspirit
├─ ai/            # 词灵AI 基础设施：额度管控、限流熔断、全局缓存、RAG、大模型调用
├─ common/        # 统一返回体、全局异常、分页封装、用户上下文
├─ config/        # Web/CORS、密码加密（BCrypt）、OSS、登录拦截器
└─ module/        # 业务模块
   ├─ user        # 注册登录、个人资料、邮箱验证码
   ├─ dict        # 词典查询（MySQL + Redis 本地化）
   ├─ review      # 艾宾浩斯复习调度
   ├─ wordbook    # 生词本与熟悉度
   ├─ wordtest    # 单词测验
   ├─ paper       # AI 试卷、自动批改、错题本
   ├─ game        # 10 款小游戏、金币、排行榜、词池去重
   ├─ shop        # 金币商城、备考资料 PDF 生成
   ├─ task        # 今日任务、活跃度、连续签到
   ├─ learnround  # 学习圈/激励体系
   ├─ assistant   # 词灵AI 答疑
   ├─ longsentence # 长难句分析
   ├─ translate   # 翻译（整句兜底免费引擎）
   ├─ sentence    # 例句服务
   ├─ explain     # 释义解析
   └─ notification # 站内通知
```

## 安全设计

- **密码**：Spring Security `BCryptPasswordEncoder` 加盐哈希（$2a$10$），存量密码格式兼容
- **防爆破**：登录同用户名连败 5 次锁定 15 分钟（Redis 计数），成功登录自动清除
- **CORS**：白名单校验，拒绝任意来源携凭证跨域
- **会话**：服务端随机 UUID Token 存 Redis（7 天滑动续期），可随时吊销
- **注入防护**：MyBatis-Plus 全参数化查询
- **越权防护**：资源类接口服务端校验数据归属
- **验证码**：图形验证码 + 邮箱验证码（60s 间隔、每日上限）

## 性能设计

- API 响应 gzip 压缩（>1KB 才压，JSON 压缩率约 70%~85%）
- Redis Lettuce 连接池（max-active 16）
- AI 全局缓存：相同输入只调用一次大模型（Redis 热点 + MySQL 持久化）
- SQL 日志走 Slf4j 分级控制，生产不刷 stdout

## 常见问题

- **启动报数据源连接失败**：未配置 `DB_PASSWORD`，请先完成「私密配置」步骤
- **AI 接口报错但不影响其他功能**：未配置 `AI_API_KEY`，属正常降级
- **验证码收不到邮件**：未配置 SMTP 时验证码打印在后端日志，本地开发直接看日志即可
- **本地调试想看 SQL**：把 `logging.level.com.wordspirit` 从 `info` 改为 `debug`
