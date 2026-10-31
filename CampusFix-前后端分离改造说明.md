# CampusFix 前后端分离改造说明

| 项 | 值 |
|---|---|
| 版本 | v1.0 |
| 日期 | 2026-10-30 |
| 范围 | 工程结构、接口契约、跨域与资源地址、部署产物、文档 |
| 结论 | 前端仅通过 HTTP API 访问后端，可独立构建、独立运行、独立部署；**本地开发方式与改造前完全一致** |

---

## 1. 改造目标与结论

改造前项目已经是 `backend/` + `frontend/` 两个目录，但存在**四处真实耦合**，导致「分开部署就坏」：

| # | 耦合点 | 改造前的表现 |
|---|---|---|
| 1 | 前端接口基地址写死在 `request.ts` | 只能是 `/api/v1`，换后端域要改代码重新打包 |
| 2 | 页面里硬编码 `http://localhost:8080/swagger-ui.html` | 部署到服务器后链接失效 |
| 3 | 后端图片返回 `/files/xxx` 相对路径 | 前后端不同域时，图片请求打到前端域 → 404 |
| 4 | CORS 白名单写死 `localhost` | 生产前端域名被拒 → 所有请求 403 |

改造后这四点全部由**配置**控制，代码里不再出现任何后端地址。

**核心原则：改造是向后兼容的。** 所有新增配置都有默认值，不设环境变量时的行为与改造前完全一致 —— 本地`npm run dev` + `mvn spring-boot:run` 的体验没有任何变化。

---

## 2. 新项目结构

```
CampusFix/
├── backend/                          【后端】Spring Boot 3.5 / Java 17，产物：campusfix.jar
│   ├── src/main/java/com/campusfix/
│   │   ├── common/                   统一响应、异常、安全、跨域、Swagger、存储配置
│   │   ├── auth/                     JWT 认证
│   │   ├── system/                   用户、角色、部门、参数
│   │   ├── order/                    工单域（状态机、派单、预约、维修过程）
│   │   ├── asset/                    位置树
│   │   └── file/                     文件上传与存储抽象
│   ├── src/main/resources/application.yml
│   ├── db/                           建表与测试数据
│   ├── Dockerfile                    独立镜像
│   └── pom.xml
│
├── frontend/                         【前端】Vue 3 + Vite，产物：dist/ 静态文件
│   ├── src/config/env.ts             ★ 唯一的对外地址出口
│   ├── src/api/                      request 封装 + 各模块接口
│   ├── src/views/                    页面
│   ├── src/ui/                       自建 UI 组件
│   ├── src/styles/theme.css          设计令牌
│   ├── .env.development              开发配置（提交仓库）
│   ├── .env.production.example       生产配置模板（复制为 .env.production）
│   ├── nginx.conf                    容器内 Nginx（静态 + 反代）
│   ├── Dockerfile                    独立镜像
│   └── vite.config.ts
│
├── deploy/
│   ├── nginx.conf                    单机部署参考（不用 Docker 时）
│   └── docker-compose.yml            mysql + backend + frontend 一键编排
│
├── .env.example                      编排用环境变量模板
├── README.md
├── CampusFix-项目注意文档.md          设计基线
├── CampusFix-系统设计说明书.md        系统设计
└── CampusFix-前后端分离改造说明.md    本文件
```

**两个工程互不依赖对方的源码**：后端不认识 Vue，前端不 import 任何 Java 文件；二者只共享一份 HTTP 接口契约。

---

## 3. 前后端职责划分

| 关注点 | 前端（Vue） | 后端（Spring Boot） |
|---|---|---|
| 页面渲染 | 全部 | 不提供任何页面或静态资源 |
| 路由 | `vue-router`，含登录守卫 | 无感知 |
| 状态合法性 | 只按 `actions` 数组决定按钮显隐 | **状态机是唯一裁决方**，越权流转一律 409/3001 |
| 权限 | 按权限码决定菜单与按钮可见 | `@PreAuthorize` + 数据权限，**是唯一真正校验处** |
| 数据过滤 | 传查询条件 | 按角色拼 SQL 条件，不信任前端传参 |
| 表单校验 | 即时提示（长度、必填、手机号格式） | Bean Validation + 业务校验，结果为准 |
| 文件 | 选文件、预览、直传接口 | 校验后缀/魔数/大小、落库、生成访问 URL |
| 统计 | 只做图表渲染 | 聚合口径固定在后端（当前工作台暂由前端基于 100 条样本估算） |
| 时间格式 | 只负责展示 | 统一序列化 `yyyy-MM-dd HH:mm:ss`，时段用 `HH:mm` |
| 部署 | 静态文件，任意 Web 服务器 | jar，任意 JVM |

一句话：**前端决定「看得见什么、怎么排」，后端决定「能不能做、数据是什么」。**

---

## 4. 接口契约

### 4.1 基地址

| 环境 | `VITE_API_BASE_URL` | 说明 |
|---|---|---|
| 开发 | `/api/v1` | 同源，Vite 代理到 `VITE_PROXY_TARGET` |
| 同源部署 | `/api/v1` | 由 Nginx 把 `/api` 反代到后端 |
| 分域部署 | `https://api.example.com/api/v1` | 浏览器直连，后端需开跨域 |

所有业务接口统一前缀 `/api/v1`，版本号在路径里，便于将来并行升级。

### 4.2 统一响应

```json
{ "code": 0, "message": "ok", "data": {}, "traceId": "a8b6ec11" }
```

- `code = 0` 表示成功，其余为业务错误码
- 分页数据固定 `{ list, total, page, size }`
- `traceId` 与后端日志 MDC 对应，排查时用它搜日志

### 4.3 错误码分段

| 段 | 含义 | 典型 |
|---|---|---|
| 1xxx | 认证与参数 | 1001 未登录、1003 参数校验失败 |
| 2xxx | 权限 | 2001 无操作权限、2002 无权访问该数据 |
| 3xxx | 业务状态 | 3001 状态不允许、3002 已被接单、3003 时段冲突 |
| 4xxx | 资源 | 4001 不存在、4003 已存在 |
| 5xxx | 系统 | 5000 系统繁忙 |

### 4.4 鉴权

- 登录返回 `accessToken`（2h）+ `refreshToken`（7d），前端存 `localStorage`
- 所有请求带 `Authorization: Bearer <accessToken>`
- 401 / `code=1001` 时前端自动清 token 并跳登录
- 令牌可撤销：改密、禁用、改角色后后端会按用户批量吊销

### 4.5 跨域

后端白名单由 `CORS_ALLOWED_ORIGINS` 控制，支持 `http://localhost:*` 这类通配模式：

```bash
CORS_ALLOWED_ORIGINS=https://www.example.com,https://admin.example.com
```

不在白名单的源会收到 `403 Invalid CORS request`（已验证）。

### 4.6 资源地址（图片）

这是分离部署最容易踩的坑，单独说明：

| 部署形态 | `STORAGE_PUBLIC_BASE_URL` | 后端返回 | 前端处理 |
|---|---|---|---|
| 同源 | 留空 | `/files/order/xxx.jpg` | `resolveAssetUrl()` 原样使用 |
| 分域 | `https://api.example.com` | `https://api.example.com/files/order/xxx.jpg` | 已是绝对地址，原样使用 |
| CDN | `https://cdn.example.com` | `https://cdn.example.com/files/...` | 同上 |

前端 `resolveAssetUrl()` 同时兼容两种情况：绝对地址直接返回，相对路径补 `VITE_ASSET_BASE_URL`（未配则补后端源站）。

---

## 5. 运行与部署

### 5.1 本地开发（两个终端，与改造前一致）

```bash
# 终端 1：后端
cd backend
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8080

# 终端 2：前端
cd frontend
npm install
npm run dev            # http://localhost:5173
```

前端 `.env.development` 里 `VITE_API_BASE_URL=/api/v1`，Vite 代理到 `127.0.0.1:8080`，浏览器看到的是同源请求，**不需要后端开跨域**。

### 5.2 独立构建（各自产物，可分开发布）

```bash
# 后端 → backend/target/campusfix.jar
cd backend && mvn clean package -DskipTests

# 前端 → frontend/dist/
cd frontend
cp .env.production.example .env.production   # 按实际域名改
npm run build
```

两个产物可以分别交给不同的人、发到不同的机器，互不依赖。

### 5.3 部署形态一：同源（单机 Nginx，最简单）

```bash
# 前端
rsync -a frontend/dist/ /var/www/campusfix/
sudo cp deploy/nginx.conf /etc/nginx/conf.d/campusfix.conf

# 后端
java -jar backend/target/campusfix.jar --server.port=8080
sudo nginx -s reload
```

Nginx 把 `/api` 与 `/files` 反代到 `127.0.0.1:8080`，`/` 走 SPA 回退。前端构建时两个变量保持 `/api/v1` 和空。

### 5.4 部署形态二：分域（前端静态托管 + 后端独立域名）

```bash
# 前端构建：直连后端
VITE_API_BASE_URL=https://api.example.com/api/v1 \
VITE_ASSET_BASE_URL=https://api.example.com \
npm run build

# 后端启动：放行前端域 + 返回绝对图片地址
CORS_ALLOWED_ORIGINS=https://www.example.com \
STORAGE_PUBLIC_BASE_URL=https://api.example.com \
API_SERVER_URL=https://api.example.com \
java -jar campusfix.jar
```

### 5.5 部署形态三：容器（一键起三件套）

```bash
cp .env.example .env          # 改密码与端口
docker compose -f deploy/docker-compose.yml up -d --build
```

- 前端 → `http://localhost:8081`（容器内 Nginx 反代 `/api`、`/files` 到 `backend:8080`）
- 后端 → `http://localhost:8080`，文档 `/swagger-ui.html`
- MySQL 首次启动自动执行 `01-schema.sql` 与 `02-data.sql`
- 上传文件与数据库分别落在 `upload-data`、`mysql-data` 卷

> 本机未安装 Docker，容器编排**未做实际构建验证**，配置按官方镜像约定编写；语法已用 YAML 解析器校验。

---

## 6. 配置项清单

### 6.1 前端（构建期注入）

| 变量 | 默认 | 说明 |
|---|---|---|
| `VITE_API_BASE_URL` | `/api/v1` | 接口基地址 |
| `VITE_ASSET_BASE_URL` | 空 | 图片等资源基地址，空表示同源 |
| `VITE_PROXY_TARGET` | `http://127.0.0.1:8080` | 仅开发代理用 |
| `VITE_DEV_PORT` | `5173` | 开发端口 |

### 6.2 后端（运行期环境变量）

| 变量 | 默认 | 说明 |
|---|---|---|
| `DB_URL` | `jdbc:mysql://127.0.0.1:3306/campusfix?...` | **改造前是硬编码** |
| `DB_USERNAME` / `DB_PASSWORD` | `root` / 本机密码 | 数据库账号 |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:*,http://127.0.0.1:*` | 跨域白名单 |
| `STORAGE_PUBLIC_BASE_URL` | 空 | 图片对外基地址 |
| `API_SERVER_URL` | 空 | Swagger 的 Try it out 目标 |
| `JWT_SECRET` | 内置默认值 | 生产必须替换 |
| `CACHE_TYPE` | `auto` | `auto` / `redis` / `memory` |
| `STORAGE_TYPE` / `STORAGE_PATH` | `local` / `D:/campusfix-data/upload` | 存储方式与本地目录 |

---

## 7. 从原结构迁移到新结构的变更说明

### 7.1 逐条变更

| # | 位置 | 改前 | 改后 | 不改会怎样 |
|---|---|---|---|---|
| 1 | `frontend/src/api/request.ts` | `baseURL: '/api/v1'` 硬编码 | 读 `API_BASE_URL`（来自 `config/env.ts`） | 换后端域必须改源码重新打包 |
| 2 | `frontend/src/config/env.ts` | 不存在 | 新增，统一导出 API/资源/源站地址 | 地址散落各处，容易漏改 |
| 3 | `LayoutView.vue`、`DashboardView.vue` | `http://localhost:8080/swagger-ui.html` | `swaggerUrl()` | 线上点「接口文档」404 |
| 4 | 4 处图片渲染 | 直接用 `img.url` | `asset(url)` → `resolveAssetUrl()` | 跨域部署图片全部裂开 |
| 5 | `vite.config.ts` | proxy 目标写死 | 读 `VITE_PROXY_TARGET` | 后端换端口要改配置 |
| 6 | `SecurityConfig` | 白名单写死 localhost | 读 `CorsProperties` | 生产前端域被 403 |
| 7 | `StorageProperties` / `LocalFileStorageService` | 固定返回 `/files/xxx` | 配 `publicBaseUrl` 后返回绝对地址 | 分域部署图片 URL 指向前端域 |
| 8 | `application.yml` 数据源 | `url` 硬编码 `127.0.0.1` | `${DB_URL:...}` | 容器内连不上数据库 |
| 9 | `OpenApiConfig` | 无 servers | 读 `API_SERVER_URL` | Swagger「试一下」打到错误源站 |
| 10 | 仓库根 | 无部署产物 | 2 个 Dockerfile、2 份 nginx 配置、compose、`.env.example` | 只能手工拼环境 |

### 7.2 未改动的部分（保持行为一致）

- 所有 REST 路径、请求体、响应体、错误码 —— **接口契约零变更**
- 数据库结构、状态机转换表、业务规则
- 前端页面结构、组件、样式
- 本地开发命令与端口

因此**前端与后端可以各自升级，只要接口契约不变，就无需同步发布**。

### 7.3 回滚方式

新增配置全部带默认值，回滚只需**不设置环境变量**：

```bash
# 不设 DB_URL → 仍连 127.0.0.1:3306
# 不设 CORS_ALLOWED_ORIGINS → 仍只放行 localhost
# 不设 STORAGE_PUBLIC_BASE_URL → 仍返回 /files/xxx 相对路径
```

前端 `config/env.ts` 在变量缺失时回落到 `/api/v1`，与改造前一致。

---

## 8. 验证记录（2026-10-30）

| 项 | 方法 | 结果 |
|---|---|---|
| 后端独立启动 | `mvn spring-boot:run` | `/actuator/health` = UP |
| 前端独立构建 | `npm run build` | 135 模块，通过 |
| 前端独立启动 | `npm run dev` | 5173 返回 200 |
| 同源代理链路 | 经 5173 调 `/api/v1/auth/login` | 登录成功（张建国） |
| 跨域放行 | `OPTIONS` 带 `Origin: http://localhost:5173` | 200 + `Access-Control-Allow-Origin` |
| 跨域拒绝 | `OPTIONS` 带 `Origin: http://evil.example.com` | 403 `Invalid CORS request` |
| 跨域实际请求 | `POST /auth/login` 带合法 Origin | 200 + 令牌正常返回 |
| 图片相对地址 | 未配 `STORAGE_PUBLIC_BASE_URL` | 返回 `/files/order/...` |
| 图片绝对地址 | 启动参数配置 `public-base-url` | 返回 `http://127.0.0.1:8080/files/order/...` |
| 前端产物注入 | `VITE_API_BASE_URL=... npm run build` 后检索 | 产物内含 `http://127.0.0.1:8080/api/v1` |
| 容器编排 | `docker compose config` | 本机无 Docker，仅 YAML 解析校验通过 |

---

## 9. 遗留与建议

1. **前端尚无单元测试**，接口契约变更时只能靠手工回归；建议先给 `config/env.ts`、`resolveAssetUrl` 补两个纯函数测试。
2. **工作台统计仍由前端基于 100 条样本估算**，`/stats/*` 接口落地后应改为后端聚合（与本次改造无关，但同属「职责边界」问题）。
3. **容器镜像未实际构建验证**，首次在目标环境执行时建议先 `docker compose build` 单独验证后端镜像（Maven 依赖下载耗时较长）。
4. 若将来前端要出小程序，`src/api/` 与 `src/config/` 可直接复用，只需替换视图层 —— 这也是本次把地址收敛到单一配置文件的收益之一。
