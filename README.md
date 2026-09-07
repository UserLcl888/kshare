# 面试题库知识分享平台

一个前后端分离的面试题知识分享网站，围绕 **技术问题 / 文章（专题分享）/ 学习专题** 三大内容专栏展开：提供文章浏览、分类检索、Markdown 阅读、访问权限申请、个人投稿、站内通知与公告，并内置管理后台，方便管理员维护文章、分类、标签、用户、内容审核、操作日志与对象存储图片。

- 用户端：面向访客与注册用户（`/` 门户页、`/home`、`/topic`、`/learn`、`/articles`、个人中心等）
- 管理端：`/admin` 及其子页面，仅 ADMIN 角色可访问

## 核心功能

### 用户端

- **登录 / 注册 / 找回密码**：邮箱、手机号注册登录，支持密码登录与邮箱验证码登录，可随时双向切换；忘记密码通过邮箱验证码重置；根路径 `/` 为登录门户页，已登录自动跳转首页
- **三大内容专栏**
  - 技术问题专栏（`/home`）：首页轮播、热门文章、每日一句、分类导航、公告滚动条
  - 文章 / 专题分享专栏（`/topic`）：未登录亦可浏览，支持标题搜索、置顶、封面（16:9）、简介、浏览量，详情页左侧 Markdown + 右侧目录，仅在本专栏内上/下一篇
  - 学习专题（`/learn`、`/learn/:categorySlug`）：按学习分类聚合的板块式学习文章，便于系统化学习
- **文章列表 / 详情**：Markdown 渲染、代码高亮、Mermaid 图示、目录 TOC、上一篇 / 下一篇、难度标签、浏览量统计（登录用户 24h 去重）
- **受限内容访问申请**：部分文章需提交申请，管理员审核通过后解锁查看
- **个人中心**：资料修改、修改密码、我的申请、我的投稿
- **图片 / 附件**：正文插图上传、头像预留、Markdown 图片自动处理
- **站内通知与公告**：公告（前台滚动条）与个人站内消息通知

### 管理后台（`/admin`）

- **数据看板**：文章、用户、访问等统计图表（首页概览 30s 轮询）
- **文章管理**：Markdown 编辑、发布 / 下线、难度与标签、浏览量统计；支持按专栏筛选（技术 / 专题 / 学习）、专题模式拖拽排序、置顶开关、封面上传或填写 URL、导入 `.md` 文件
- **学习分类管理**：维护学习专题的分类与板块
- **分类管理、标签管理、用户管理**
- **访问申请审核、用户投稿审核**
- **公告管理、站内通知发布**
- **操作日志**：记录管理员关键操作（目标类型 / 动作 / 结果）
- **图片上传**：Markdown 插图、专题封面，统一走 MinIO

## 技术栈

| 端 | 技术 |
| --- | --- |
| 后端 | Java 17 · Spring Boot 3.3.2 · MyBatis-Plus 3.5.7 · Sa-Token 1.37.0 · Spring Security Crypto · MySQL 8 · Redis（Lettuce + commons-pool2）· QQ 邮箱 SMTP · MinIO 8.5.12 |
| 前端 | Vue 3.4 · Vite 5 · TypeScript · Element Plus 2.7 + @element-plus/icons-vue · Pinia · Vue Router 4 · Axios · marked · highlight.js · DOMPurify · Mermaid · ECharts 6 |

其他：Flexmark 0.64.8（服务端 Markdown 转换）、OWASP Java HTML Sanitizer 20240325.1（HTML 消毒）、BCrypt（密码加密）、Vue 3 组合式 API + `<script setup>`。

## 项目结构

```text
.
├── interview-backend/          # Spring Boot 后端
│   ├── application-local.yml   # jar 外部覆盖配置（本地运行打包 jar 用）
│   ├── uploads/                # 本地图片目录（MinIO 未配置时降级使用）
│   ├── log/                    # 运行日志
│   └── src/main/
│       ├── resources/          # application.yml / application-example.yml / logback-spring.xml / static/
│       └── java/com/interview/
│           ├── controller/     # 接口层（用户端 + admin/ 管理端）
│           ├── service/        # 业务逻辑（含 MarkdownImageService 图片处理）
│           ├── mapper/         # MyBatis-Plus 数据访问
│           ├── entity/         # 数据库实体
│           ├── dto/            # 请求 / 响应结构
│           ├── enums/          # 角色、状态、难度、操作日志等枚举
│           ├── config/         # Sa-Token、MyBatis-Plus、MinIO、Web 等配置 + MinioProperties
│           └── common/         # 统一返回 Result、PageResult、异常处理、常量
├── user-web/                   # Vue 3 前端（用户端 + 管理后台同一 SPA）
│   └── src/
│       ├── views/              # 页面（含 admin/ 管理后台）
│       ├── components/         # 公共组件 + layout/（AppHeader、AuthLayout、AdminLayout 等）
│       ├── api/                # 接口封装
│       ├── stores/             # Pinia 状态
│       ├── router/             # 路由与登录 / 权限守卫
│       ├── composables/        # usePolling 等组合式函数
│       ├── config/             # site.ts（站点级 / MinIO 常量、轮播配置）
│       └── styles/             # 全局主题（暗色 + 橙金强调色）
├── interview.sql               # 数据库初始化脚本（建库、建表、种子数据，git 忽略）
├── 数据库最终脚本.sql           # 最终库结构脚本（入库）
├── learn-seed.sql              # 学习专题示例数据
├── topic-seed.sql              # 文章 / 专题分享示例数据
├── 学习专题分类.sql             # 学习分类初始化数据
└── 部署上手指南.md              # 从 0 到 1 的完整部署文档
```

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.6+
- MySQL 8.x（SQL 使用 `utf8mb4_0900_ai_ci` 排序规则，不支持 MySQL 5.7）
- Redis 6+（生产环境建议设置 `requirepass`）
- Node.js 18+（仅前端构建需要）
- MinIO（可选；未配置时图片自动降级到本地 `uploads/` 目录）

### 1. 初始化数据库

```bash
mysql -uroot -p < interview.sql
```

脚本会自动创建 `interview` 库、建表并写入种子数据（含管理员 / 普通用户测试账号）。若只需最终表结构，可导入 `数据库最终脚本.sql`，再按需执行 `topic-seed.sql` / `learn-seed.sql` / `学习专题分类.sql`。

### 2. 启动后端

```bash
cd interview-backend

# 本地开发直接以 example（或 local）profile 启动，读取 application-example.yml
mvn spring-boot:run -Dspring-boot.run.profiles=example
```

或使用已打包 jar，并通过外部配置文件覆盖：

```bash
mvn clean package -DskipTests
java -jar target/interview-backend-0.1.0.jar \
  --spring.config.additional-location=file:./application-local.yml
```

服务默认运行在 `http://localhost:8080`。

### 3. 启动前端

```bash
cd user-web
npm install
npm run dev
```

访问 `http://localhost:5173`。开发环境已配置代理：`/api`、`/images` 转发到 `http://localhost:8080`。

## 构建产物

```bash
# 后端：可执行 jar（内含 Tomcat，启动时自动上传轮播图到 MinIO）
cd interview-backend && mvn clean package -DskipTests
# 产物：interview-backend/target/interview-backend-0.1.0.jar

# 前端：静态文件（dist/ 同时包含用户端与管理后台）
cd user-web && npm run build
# 产物：user-web/dist/
```

## 内容专栏说明

`article.column_type` 决定文章归属的专栏（`tech` / `topic` / `learn`）：

| 专栏 | 取值 | 前台入口 | 说明 |
| --- | --- | --- | --- |
| 技术问题 | `tech` | `/home`、`/articles` | 常规面试题，按技术分类浏览 |
| 文章 / 专题分享 | `topic` | `/topic`（未登录可浏览） | 支持置顶、封面、拖拽排序；详情仅在本专栏内上/下篇 |
| 学习专题 | `learn` | `/learn`、`/learn/:categorySlug` | 按学习分类（learn_category）聚合的板块式内容 |

> 首页热门 / 统计 / 分类列表会过滤 `topic` 与 `learn` 文章，避免与常规技术问题混排。

## 对象存储（MinIO）

站点图片（轮播图、文章正文图、用户上传图、专题封面）已接入 MinIO：

- **桶**：`interview-images`，公开读
- **访问**：图片公开访问走 `9000`（S3 API）端口，`9001` 仅为管理控制台
- **目录规划**：`banner/`（轮播图）、`article/yyyy/MM/dd/uuid`（文章正文图）、`upload/yyyy/MM/dd/uuid`（用户上传图）、`cover/`（专题封面）
- **自动处理**：后端 `MarkdownImageService` 在保存文章 / 用户投稿时自动解析 `![](...)`：识别 base64（解码上传）、外链（下载上传，失败保留原链）、`/images/`（读取上传），并整篇重写为 MinIO URL；数据库只存 URL 文本
- **限制**：仅 `png/jpg/jpeg/gif/webp`，单张 ≤ 5MB，文件名 UUID
- **降级**：未配置 MinIO 时服务正常启动，封面等回退到本地 `uploads/` 目录

配置项在 `application*.yml` 的 `minio.*` 与前端 `user-web/src/config/site.ts`（`MINIO_PUBLIC_BASE` / `MINIO_BUCKET`）。

## 配置说明

> 三个 `application*.yml` 均被 git 忽略（`interview-backend/.gitignore` 中 `src/main/resources/application*.yml`），不会随仓库分发。

| 文件 | 用途 | 是否打入 jar |
| --- | --- | --- |
| `application.yml` | 部署 / 打包配置，预填真实 MySQL、Redis、MinIO、QQ 邮箱；随 jar 分发 | ✅ |
| `application-example.yml` | 本地开发模板，通过 `--spring.profiles.active=example` 启动；Maven 打包时排除 | ❌ |
| `application-local.yml`（后端根目录） | 运行打包 jar 时的外部覆盖配置，用 `--spring.config.additional-location` 引用 | ❌（在 jar 外） |
| `application-prod.yml`（自建） | 生产环境可单独创建，用 `--spring.profiles.active=prod` 启动，配置不入库 | 依需 |

**关键配置项**：MySQL 连接、Redis 连接与密码、QQ 邮箱 SMTP（用于发送验证码，需用邮箱授权码而非登录密码）、文件上传大小限制、验证码策略（有效期 / 最大尝试 / 重发间隔 / 每日上限）、浏览量写库刷新间隔、MinIO 连接与桶信息。

## 测试账号

| 角色 | 账号 | 密码 |
| --- | --- | --- |
| 管理员 | 2090323327@qq.com | 123456 |
| 普通用户 | 2090323328@qq.com | 123456 |

> 仅用于前期本地调试，正式上线前务必修改密码并清理多余种子数据。

## 部署

完整部署流程（服务器选购、FinalShell 连接、宝塔面板、MySQL / Redis / JDK 安装、后端 systemd、前端 Nginx、MinIO、ICP 备案、备份与验收）见：

- [部署上手指南.md](部署上手指南.md)

当前部署环境：雨云深圳电信 2C4G / Ubuntu 22.04 / 宝塔面板 / Nginx + systemd，图片对象存储 MinIO（`103.236.54.34:9000`）。部署时需保证：

- Nginx 将 `/api`、`/images` 反向代理到后端
- 后端运行目录可写（本地降级存储 `uploads/` 需要）
- MinIO 地址、Access / Secret Key 与前端 `site.ts` 中的公开访问前缀一致

## 相关文档

- [后端设计文档](后端设计文档.md)
- [用户端设计文档](用户端设计文档.md)
- [管理后台设计文档](管理后台设计文档.md)
- [后端交接文档](后端交接文档.md)
- [专题专栏功能说明](专题专栏功能说明.md)
- [MinIO 接入操作方案](MinIO接入操作方案.md)
- [MinIO 接入联调报告](MinIO接入联调报告.md)

## 后续规划

- 每日推荐（类似 Geo SEO 的运营化推荐）
- 独立面试模块（模拟面试、真题练习）
- 专题子分组 / 多专栏支持
- 专题拖拽排序动效（如 sortablejs）
- 游客浏览专题文章的浏览量去重策略细化
- 按业务拆分微服务

## License

本项目暂未指定开源协议；如需商用或二次分发，请联系作者。
