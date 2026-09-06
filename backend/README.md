# 后端说明

Spring Boot 3 单体后端。所有密钥、密码这类敏感信息不在仓库里，走环境变量或者本地的
`application-local.yml`（这个文件被 gitignore 了），所以仓库克隆下来第一件事是补配置。

## 环境

JDK 17+、Maven 3.8+，本地跑着 MySQL 8 和 Redis 就行。

## 启动步骤

1. 建库：

```bash
mysql -uroot -p < src/main/resources/db/schema.sql
mysql -uroot -p < src/main/resources/db/data.sql
```

2. 补私密配置。复制示例文件：

```bash
cd src/main/resources
copy application-local.yml.example application-local.yml
```

打开至少把 MySQL 密码填了。文件里每一项都有注释说明是干嘛的。

3. 运行：

```bash
mvn spring-boot:run
```

默认 8080，API 响应开了 gzip 压缩。IDEA 里直接跑 `CilingAcademyApplication` 也一样。

## 配置项一览

application.yml 里的敏感项全是 `${环境变量:默认值}` 形式，对应关系如下。
不配置时的行为都写了，大部分功能缺配置只是降级，不会崩：

| 环境变量 | 干什么用 | 不配置会怎样 |
| --- | --- | --- |
| `DB_PASSWORD` | MySQL 密码 | 起不来，必填 |
| `REDIS_HOST` / `REDIS_PASSWORD` | Redis 连接 | 默认 localhost，密码空 |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | SMTP 发件邮箱 | 验证码不真发，直接打在后端日志里 |
| `AI_API_KEY` | 阿里云百炼 Key | AI 功能降级，其他正常 |
| `QDRANT_URL` / `QDRANT_API_KEY` | Qdrant 向量库 | RAG 检索降级 |
| `OSS_*` 四个 | 阿里云 OSS | 头像这类文件存本地 uploads/ 目录 |
| `APP_CORS_ORIGINS` | CORS 白名单，逗号分隔 | 默认只放行 localhost:5173 |
| `MYMEMORY_EMAIL` | MyMemory 翻译授权邮箱 | 用匿名额度，量小一些 |

## 代码结构

```
com.wordspirit
├─ ai/            # AI 基础设施：额度管控、限流、全局缓存、RAG、大模型调用
├─ common/        # 统一返回体、全局异常、分页封装
├─ config/        # Web/CORS、BCrypt 密码加密、OSS、登录拦截器
└─ module/        # 业务模块
   ├─ user         # 注册登录、个人资料、邮箱验证码
   ├─ dict         # 词典查询，走本地 MySQL + Redis
   ├─ review       # 艾宾浩斯复习调度
   ├─ wordbook     # 生词本、熟悉度
   ├─ wordtest     # 单词测验
   ├─ paper        # AI 试卷、批改、错题本
   ├─ game         # 小游戏、金币、排行榜
   ├─ shop         # 商城、备考资料 PDF 生成
   ├─ task         # 今日任务、活跃度、签到
   ├─ learnround   # 学习圈
   ├─ assistant    # 词灵AI 答疑
   ├─ longsentence # 长难句分析
   ├─ translate    # 翻译，整句走免费引擎兜底
   ├─ sentence     # 例句
   ├─ explain      # 释义解析
   └─ notification # 通知
```

## 安全

- 密码是 spring-security-crypto 的 BCryptPasswordEncoder 加盐哈希，之前用 hutool BCrypt
  存的存量密码格式一样（$2a$ 开头），直接兼容，不用迁移数据
- 登录连错 5 次锁 15 分钟，Redis 计数，登录成功清零
- CORS 白名单，不是 *
- Token 是服务端随机 UUID 存 Redis，7 天滑动续期，可吊销
- MyBatis-Plus 参数化查询，没有拼 SQL 的地方
- 删错题这类操作在服务端校验归属，改 ID 越权无效

## 常见问题

**起不来，报数据源错误**：`DB_PASSWORD` 没配，去 application-local.yml 里填 MySQL 密码。

**AI 接口报错但其他功能正常**：没配 `AI_API_KEY`，正常降级，不是 bug。

**验证码邮件收不到**：SMTP 没配，验证码在后端日志里，搜"验证码"就能找到。

**本地想看 SQL**：application.yml 里 `logging.level.com.wordspirit` 改成 debug。
