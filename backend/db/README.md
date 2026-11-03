# 数据库初始化指南

本目录存放 CampusFix 的全部数据库脚本。**项目未接入 Flyway / Liquibase，也没有开启 Spring 的自动建表**，建库完全依赖手工执行下列 SQL（或由 Docker 首次启动时自动导入）。

---

## 一、脚本清单

| 文件                                      | 作用                                | 内容规模                                       | 可重复执行                            | 破坏性                                     |
| --------------------------------------- | --------------------------------- | ------------------------------------------ | -------------------------------- | --------------------------------------- |
| `schema.sql`                            | 建库 + 建表                           | `CREATE DATABASE campusfix` + 25 张表        | ✅ 可（先 `DROP TABLE IF EXISTS` 再建） | ⚠️ **高**：会删掉 `campusfix` 库中这 25 张表的现有数据 |
| `data.sql`                              | 灌入测试数据                            | 20 张表有数据，含 11 个账号、11 条工单（覆盖 10 种状态）        | ✅ 可（先 `DELETE FROM` 再 `INSERT`）  | ⚠️ **高**：会清空上述 20 张表的业务数据               |
| `migration/V2__student_class.sql`       | 历史增量：`sys_user` 增加 `class_name` 列 | 1 条 `ALTER TABLE` + 1 条回填 `UPDATE`         | ❌ 否，重复执行报 `ERROR 1060`           | 中：仅改表结构                                 |
| `../db/` 之外：`deploy/docker-compose.yml` | 容器化首次启动自动导入                       | 挂载 `schema.sql` → `01-`、`data.sql` → `02-` | 仅首次                              | 同 schema + data                         |

> **结论**：日常开发只需 `schema.sql` + `data.sql` 两个文件、两条命令即可从零建库。

---

## 二、脚本之间的依赖关系

```
schema.sql（建库 campusfix + 25 张表）
    └── data.sql（必须先有表，脚本内直接 USE campusfix）
            └── 后端启动（spring.profiles.active=dev，读 application.yml / application-dev.yml）
```

**关于 `V2__student_class.sql`**：当前 `schema.sql` 的 `sys_user` 已内置 `class_name` 列（第 19–20 行），因此**全新库不要再执行 V2**，否则会因重复列直接中断。该脚本仅用于修复「在 V2 之前建库」的旧环境。

---

## 三、方式 A：本机 MySQL 8（开发环境首选）

### 步骤 1｜前置确认

```bash
mysql --version          # 需 8.0 及以上
mysql -uroot -p -e "SELECT VERSION();"   # 能连上说明账号密码可用
```

要求：MySQL 8.0+、字符集 `utf8mb4`、执行账号具备 `CREATE DATABASE` 与建表权限。

### 步骤 2｜建库建表

```bash
cd backend
mysql --default-character-set=utf8mb4 -uroot -p < db/schema.sql
```

- `schema.sql` 自带 `CREATE DATABASE IF NOT EXISTS campusfix ... utf8mb4` 与 `USE campusfix`，**无需手工先建库**。
- **Windows PowerShell 不支持 `<` 重定向**，请改用：

```powershell
cmd /c "mysql --default-character-set=utf8mb4 -uroot -p < db\schema.sql"
```

- Windows 命令行建议加 `--default-character-set=utf8mb4`，否则表注释中的中文可能显示乱码（不影响数据本身）。

### 步骤 3｜导入测试数据（可选，但在开发/演示环境强烈建议）

```bash
mysql --default-character-set=utf8mb4 -uroot -p < db/data.sql
```

`data.sql` 内置 `SET @PWD`（BCrypt 哈希），**所有账号初始密码统一为 `Cf@123456`**。

### 步骤 4｜校验导入结果

```sql
USE campusfix;
SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='campusfix';  -- 期望 25
SELECT COUNT(*) FROM sys_user;      -- 期望 11
SELECT COUNT(*) FROM repair_order;  -- 期望 11
SELECT status, COUNT(*) FROM repair_order GROUP BY status;  -- 期望覆盖 10 种状态
SELECT username, real_name FROM sys_user WHERE id <= 4;     -- 1000/20001/30001/2021001 四个角色
```

预期为空的 5 张表：`sys_oper_log`、`sys_login_log`、`fault_alert`、`order_extension_request`、`repair_order_image`（工单图片需自行上传，初始无图属正常）。

### 步骤 5｜配置后端连接密码（**这一步现有文档缺失，最易踩坑**）

`application.yml` 的密码默认值是占位符 `change-me`，真实密码放在 `application-dev.yml`：

```yaml
# backend/src/main/resources/application-dev.yml
# 该文件被 .gitignore 排除，新克隆仓库中不存在，需手动创建
spring:
  datasource:
    password: '你的 MySQL root 密码'

# 同时需配置 JWT 密钥（否则启动失败，见下方说明）
campusfix:
  jwt:
    secret: 'openssl rand -base64 48 生成的结果'
```

若不想创建文件，也可用环境变量覆盖（优先级高于配置文件）：

```bash
# macOS / Linux / Git Bash
export DB_PASSWORD='你的密码'
export JWT_SECRET='你的 JWT 密钥（≥32 字节）'
# Windows PowerShell
$env:DB_PASSWORD='你的密码'
$env:JWT_SECRET='你的 JWT 密钥（≥32 字节）'
```

> **JWT 密钥必填**：项目不再提供默认密钥，未配置或长度不足 32 字节时应用会拒绝启动。
> 生成命令：`openssl rand -base64 48`（或 `openssl rand -hex 32`）。
> 生产环境务必通过 `JWT_SECRET` 环境变量注入，不要把真实密钥写进任何提交到仓库的文件。

其他可覆盖项：`DB_USERNAME`（默认 `root`）、`DB_URL`（默认 `jdbc:mysql://127.0.0.1:3306/campusfix?...`）。

### 步骤 6｜启动与验收

```bash
cd backend
mvn spring-boot:run
# 或 IDEA 直接运行 CampusFixApplication
```

- 应用：<http://localhost:8080>（后端同时托管前端构建产物）
- 接口文档：<http://localhost:8080/swagger-ui.html>
- 健康检查：<http://localhost:8080/actuator/health>
- 用 `1000 / Cf@123456`（系统管理员）登录验证

---

## 四、方式 B：Docker Compose（演示 / 交付环境）

```bash
cd /仓库根目录
cp .env.example .env          # 修改 MYSQL_ROOT_PASSWORD 等
docker compose -f deploy/docker-compose.yml up -d --build
```

**自动建库机制**：compose 将 `backend/db/schema.sql`、`data.sql` 以 `01-`、`02-` 前缀挂载到 MySQL 容器的 `/docker-entrypoint-initdb.d/`，官方镜像**仅在数据卷 `mysql-data` 为空时执行一次**。

因此：

- 首次启动 → 自动建表 + 灌数据，无需手工导入；
- 之后修改 SQL 再启动 → **不会重新执行**（数据卷已存在）；
- 需要重新初始化 → 先删卷再启：

```bash
docker compose -f deploy/docker-compose.yml down -v
docker compose -f deploy/docker-compose.yml up -d --build
```

> 注意：本编排只建 `mysql` / `backend` / `frontend` 三个服务，**不含 Redis**（`CACHE_TYPE` 默认 `memory`）。仓库根目录没有 `docker-compose.yml`，必须带 `-f deploy/docker-compose.yml`。

---

## 五、常见问题与处置

| 现象                                                       | 原因                                                         | 处置                                                                                          |
| -------------------------------------------------------- | ---------------------------------------------------------- | ------------------------------------------------------------------------------------------- |
| `ERROR 1060 (42S21): Duplicate column name 'class_name'` | 在新库上重复执行了 `V2__student_class.sql`                          | 全新库跳过 V2；仅旧库执行                                                                              |
| `Access denied for user 'root'@'localhost'`              | 未配置 `application-dev.yml` 或 `DB_PASSWORD`，仍用默认 `change-me` | 按「步骤 5」配置真实密码                                                                               |
| `Unknown database 'campusfix'`                           | 只跑了 `data.sql`，未跑 `schema.sql`                             | 先执行 `schema.sql`                                                                            |
| `Public Key Retrieval is not allowed`                    | MySQL 8 认证插件要求                                             | 连接串已内置 `allowPublicKeyRetrieval=true`，不要自行删掉                                                |
| `syntax error near '<'`（PowerShell）                      | PowerShell 不支持输入重定向                                        | 用 `cmd /c "mysql ... < db\schema.sql"`                                                      |
| 表注释 / 中文数据乱码                                             | 客户端默认字符集非 utf8mb4                                          | 命令加 `--default-character-set=utf8mb4`                                                       |
| 启动日志 `Redis 连接失败，降级为进程内令牌存储`                             | 本机无 Redis                                                  | 预期行为，可忽略（`CACHE_TYPE=memory` 亦可不装）                                                          |
| 学生账号「班级」为空                                               | `data.sql` 未写入 `class_name`（V2 之后新增字段）                     | `UPDATE sys_user SET class_name='未分班' WHERE student_no IS NOT NULL AND class_name IS NULL;` |
| 重跑 `schema.sql` 后数据全没                                    | 脚本内含 25 条 `DROP TABLE IF EXISTS`                           | 生产库禁止执行；改动前先 `mysqldump` 备份                                                                 |

备份建议（改动结构前）：

```bash
mysqldump --default-character-set=utf8mb4 -uroot -p campusfix > campusfix_backup.sql
```

---

## 六、现有文档的覆盖情况

| 文档                          | 已覆盖                                        | 缺失环节                                                                                                                                  |
| --------------------------- | ------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------- |
| `README.md`「1. 初始化数据库」      | 两条 `mysql` 命令 + 环境要求含 MySQL 8.0+           | 未说明密码如何配置（`application-dev.yml` 未入库）、未讲校验方法、未提 Windows 重定向问题、未提 V2 迁移                                                                 |
| `PROGRESS.md`「1. 初始化数据库」    | 同上，并提到 `DB_USERNAME`/`DB_PASSWORD` 可覆盖     | 未给出文件创建方式与默认密码 `change-me` 的坑；无 Docker 路径                                                                                             |
| `CampusFix-项目注意文档.md` §4.4  | 仅声明「测试数据放 `db/data.sql`，与 `schema.sql` 分离」 | 无任何操作步骤                                                                                                                               |
| `CampusFix-系统设计说明书.md` §7.2 | 有启动顺序代码块                                   | 原文写作 `mvn flyway:migrate`，但 `pom.xml` **并未引入 Flyway**，该命令不可用；`docker compose up -d` 未指明 `-f deploy/...`，且描述「MySQL 8 + Redis 7」与实际编排不符 |

本文件即为对上述缺口的补齐。

---

## 七、验收清单

- [ ] `information_schema.tables` 中 `campusfix` 有 25 张表
- [ ] `sys_user` 11 条、`repair_order` 11 条
- [ ] 四个角色账号可登录：`1000`（系统管理员）、`20001`（后勤管理员）、`30001`（维修人员）、`2021001`（学生）
- [ ] 密码统一 `Cf@123456`，首次登录提示改密（`pwd_reset_flag`）
- [ ] `/actuator/health` 返回 `UP`
- [ ] `/swagger-ui.html` 可打开，接口分组正常
