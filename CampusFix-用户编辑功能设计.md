# 用户管理「编辑用户」功能设计文档

> 版本：v1.1　|　日期：2026-11-06　|　状态：**已实施并验证**（后端 13 例单测 + 前端 7 例单测 + 接口冒烟）
> 范围：管理端「用户管理」列表新增「编辑」操作，支持查看并修改用户基本信息；数据更新采用动态 SQL。
> 关联文档：`CampusFix-系统设计说明书.md`（§6 接口契约、§8 安全）、`CampusFix-项目注意文档.md`（§17 密钥与安全规范）

---

## 1. 背景与目标

### 1.1 现状

| 能力 | 现状 |
|---|---|
| 用户列表 | `GET /api/v1/users` 分页 + 关键词/角色/部门/状态筛选，已上线 |
| 用户详情 | `GET /api/v1/users/{id}` 已存在，返回 `UserVO`（含角色码、部门名、信誉分、最近登录） |
| 新增用户 | `POST /api/v1/users`、`POST /api/v1/users/students`、Excel 批量导入 |
| 启停 / 重置密码 / 信誉分 | 列表行内已有操作 |
| **编辑基本信息** | **缺失**：`PUT /api/v1/users/{id}` 后端存在，但前端无入口；且该接口为全量覆盖（`updateById`），传入未填字段会把列置空，不适合表单编辑 |

### 1.2 目标

1. 管理端用户列表每行新增「编辑」操作，点击后可**查看完整信息并修改基本信息**。
2. 更新接口采用**动态 SQL**：只更新本次提交的字段，未提交字段保持原值，避免误清空。
3. 明确的字段级校验、权限边界与异常反馈，禁止"把自己锁在门外"类误操作。
4. 变更可审计（操作日志）、必要时使旧令牌失效（权限/部门变更即时生效）。

### 1.3 非目标

- 不改造新增用户与批量导入流程。
- 不提供密码修改（走既有「重置密码」）。
- 不提供信誉分编辑（只能走 `adjustCredit` 并写 `sys_credit_log` 流水）。
- 不开放登录名、学号、工号的在线修改（涉及登录凭据与业务唯一键，见 §4.2 理由）。

---

## 2. 交互流程

### 2.1 主流程

```
[用户管理列表]
   │  行操作列点击「编辑」（仅 user:edit 可见）
   ▼
[打开编辑弹窗] ──① GET /users/{id} 拉取详情（加载态：骨架/禁用表单）
   │
   ├─ 只读区：登录名、学号/工号、信誉分、最近登录、创建时间
   └─ 可编辑区：姓名、手机号、班级 / 所属部门、状态、角色
   │
   ├─ 角色联动：含「学生」→ 显示班级、隐藏部门；否则显示部门、隐藏班级
   │
   ▼  点击「保存」
[前端校验] ──失败──▶ 字段下方红字提示，不发起请求
   │  通过
   ▼
[PUT /users/{id}/basic] ──② 后端校验 + 动态 SQL 更新 + 角色重绑 + 踢下线
   │
   ├─ 成功 → toast「已保存」→ 用响应体 VO 就地替换该行（不整页刷新）→ 关闭弹窗
   └─ 失败 → 按错误码 toast 提示；字段错误定位到具体输入项；弹窗保持打开
```

### 2.2 关键交互细节

| 场景 | 处理 |
|---|---|
| 打开弹窗 | 详情请求未返回前，表单禁用 + 骨架占位，避免空表单被误保存 |
| 未修改直接关闭 | 不做脏检查拦截（低频操作，保持简单）；关闭即丢弃 |
| 改为禁用（1→0） | 保存前 `confirmDialog` 二次确认："禁用后该账号将立即退出登录" |
| 移除「系统管理员」角色 | 保存前二次确认，并提示"该用户将失去系统管理权限" |
| 保存中 | 按钮 loading、禁用重复提交 |
| 保存成功 | 就地更新列表行（`roleNames`/`deptName` 由后端 VO 带回），不触发整页 `load()` |
| 他人已改动该用户 | 若启用乐观校验（§7.3），返回 3013 提示"数据已变更，请刷新后重试" |

### 2.3 表单布局（沿用现有 `Modal` 组件）

```
┌─ 编辑用户 ─────────────────────────────┐
│ 基本信息（只读，灰底）                    │
│  登录名 1000   学号/工号 D0001            │
│  信誉分 100    最近登录 2026-11-06 09:12  │
├────────────────────────────────────────┤
│ 可编辑                                   │
│  姓名 *    [__________]                  │
│  手机号    [__________]  留空表示清除      │
│  角色 *    [学生][维修人员][后勤][管理员]  │
│  班级 *    [__________]  ← 含学生时显示    │
│  所属部门 * [下拉树]      ← 非学生时显示    │
│  状态      (•) 启用  ( ) 禁用             │
├────────────────────────────────────────┤
│                        [取消] [保存]      │
└────────────────────────────────────────┘
```

---

## 3. 可编辑字段与校验规则

### 3.1 字段矩阵

| 字段 | 库列 | 可编辑 | 前端控件 | 校验规则 | 校验位置 |
|---|---|---|---|---|---|
| 姓名 | `real_name` | ✅ | 文本 | 非空；≤50 字；仅中文/字母/`·`/空格 | 前端 + Bean Validation |
| 手机号 | `phone` | ✅ | 文本 | 可空；`^1[3-9]\d{9}$`；空串表示清除 | 前端 + Bean Validation |
| 角色 | `sys_user_role` | ✅ | 多选标签 | 至少 1 项；必须是启用中的角色码 | 后端业务校验 |
| 班级 | `class_name` | ✅（仅学生） | 文本 | 含 STUDENT 时非空、≤50；不含时后端自动清空 | 后端业务校验 |
| 所属部门 | `dept_id` | ✅（仅员工） | 部门树下拉 | 含 REPAIRER/MANAGER/ADMIN 时非空，且部门存在且启用；纯学生自动清空 | 后端业务校验 |
| 状态 | `status` | ✅ | 单选 | 仅 0/1；禁止禁用自己；禁止禁用最后一个启用的管理员 | 前端 + 后端业务校验 |
| 登录名 | `username` | ❌ | 只读展示 | — | — |
| 学号 / 工号 | `student_no` / `staff_no` | ❌ | 只读展示 | — | — |
| 信誉分 | `credit_score` | ❌ | 只读展示 | 只能经 `adjustCredit` + 流水 | — |
| 密码 | `password` | ❌ | 不展示 | 走「重置密码」 | — |
| 头像 | `avatar` | ❌ | 不在此处 | 走个人中心上传 | — |

### 3.2 不可编辑字段的理由（重要）

1. **登录名**是登录凭据与 JWT `username` 声明来源，修改会导致历史令牌语义漂移、审计日志无法对齐。
2. **学号 / 工号**是业务唯一键，被工单、评价、投诉等表以 `reporter_id` 之外的快照形式引用（如 `staff_no` 展示），在线改动会破坏历史一致性；如需更正，走「信息更改申请 → 管理员审核」既有流程（`profile_change_request`）。
3. **信誉分**必须与 `sys_credit_log` 流水强一致，任何直接 SET 都会破坏可追溯性。

### 3.3 业务规则（后端强制，前端仅做提示）

| 编号 | 规则 | 违反时错误码 |
|---|---|---|
| R1 | 至少保留一个角色 | `PARAM_INVALID(1003)` |
| R2 | 不能禁用当前登录账号自身（`id == CurrentUserHolder.id()` 时 `status` 必须为 1） | `STATUS_NOT_ALLOWED(3001)` |
| R3 | 不能移除自身的管理员角色（避免自锁） | `STATUS_NOT_ALLOWED(3001)` |
| R4 | 不能禁用 / 降级"最后一个启用中的系统管理员" | `STATUS_NOT_ALLOWED(3001)` |
| R5 | 含 STUDENT → `class_name` 必填、`dept_id` 置空 | `PARAM_INVALID(1003)` |
| R6 | 含 REPAIRER/MANAGER/ADMIN → `dept_id` 必填且存在、启用 | `PARAM_INVALID(1003)` |
| R7 | 提交内容为空（所有字段为 null 且未传角色）→ 拒绝 | `PARAM_INVALID(1003)` |

> R4 的"最后一个启用管理员"判定：`SELECT COUNT(*) FROM sys_user u JOIN sys_user_role ur ... JOIN sys_role r ... WHERE r.code='ADMIN' AND u.status=1 AND u.deleted=0 AND u.id <> #{id}`，结果为 0 则拒绝。

---

## 4. 接口与数据结构

### 4.1 接口清单

| # | 方法 | 路径 | 权限 | 说明 | 状态 |
|---|---|---|---|---|---|
| 1 | GET | `/api/v1/users/{id}` | `user:list` | 拉取编辑表单数据 | 复用（已存在） |
| 2 | GET | `/api/v1/depts/tree` | 登录即可 | 部门下拉树 | 复用 |
| 3 | GET | `/api/v1/roles` | 登录即可 | 角色多选项 | 复用 |
| 4 | PUT | `/api/v1/users/{id}/basic` | `user:edit` | **新增**：按提交字段动态更新 | 新增 |
| 5 | PUT | `/api/v1/users/{id}/status` | `user:edit` | 列表行内启停 | 保留不变 |

> 既有 `PUT /users/{id}`（全量覆盖）保留不动，避免影响潜在调用方；新流程不调用它。

### 4.2 新增接口契约

**请求**

```http
PUT /api/v1/users/9003/basic
Authorization: Bearer <token>
Content-Type: application/json
```

```json
{
  "realName": "张建国",
  "phone": "13900009013",
  "roleCodes": ["REPAIRER"],
  "className": null,
  "deptId": 2,
  "status": 1,
  "expectedUpdatedAt": "2026-11-06T09:12:33"
}
```

字段语义（**部分更新**）：

| 字段 | 传 null / 不传 | 传空串 `""` | 传值 |
|---|---|---|---|
| `realName` | 不修改 | 视为非法（`@NotBlank` 组合由业务校验兜底） | 修改 |
| `phone` | 不修改 | **清除**（置 NULL） | 修改 |
| `className` | 不修改 | **清除**（置 NULL） | 修改 |
| `deptId` | 不修改 | — | 修改；**传 `0` 表示清除** |
| `status` | 不修改 | — | 修改（0/1） |
| `roleCodes` | 不修改角色 | 非法（至少 1 项） | **整体替换**角色集合 |
| `expectedUpdatedAt` | 跳过乐观校验 | — | 启用乐观校验（见 §7.3） |

**成功响应**

```json
{ "code": 0, "message": "ok", "data": { "id": 9003, "username": "demo_rep", "realName": "张建国",
  "deptId": 2, "deptName": "水电维修组", "phone": "13900009013", "status": 1,
  "roleCodes": ["REPAIRER"], "roleNames": ["维修人员"], "creditScore": 90, "lastLoginAt": "2026-11-06 09:12:33" } }
```

> 返回更新后的 `UserVO`，前端据此就地刷新行，避免整页重载。

### 4.3 数据结构

**DTO（新增于 `UserDtos`）**

```java
@Data
public static class BasicUpdateRequest {
    @Size(max = 50, message = "姓名不能超过 50 字")
    @Pattern(regexp = "^$|^[\\u4e00-\\u9fa5A-Za-z·\\s]{1,50}$", message = "姓名仅支持中文、字母、空格与·")
    private String realName;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @Size(max = 50, message = "班级不能超过 50 字")
    private String className;

    /** null=不修改；0=清除部门 */
    private Long deptId;

    /** null=不修改；仅允许 0/1 */
    private Integer status;

    /** null=不修改；非 null 时整体替换 */
    private List<String> roleCodes;

    /** 可选：启用乐观校验时传上次读取的 updated_at */
    private LocalDateTime expectedUpdatedAt;
}
```

**Controller（新增方法）**

```java
@Operation(summary = "编辑用户基本信息")
@PutMapping("/{id}/basic")
@PreAuthorize("hasAuthority('user:edit')")
@OperLog(module = "用户管理", action = "编辑用户",
         masked = {"password", "phone", "oldPassword", "newPassword"})
public Result<UserDtos.UserVO> updateBasic(@PathVariable Long id,
                                           @Valid @RequestBody UserDtos.BasicUpdateRequest req) {
    return Result.ok(userService.updateBasic(id, req));
}
```

**Service 签名**

```java
@Transactional
public UserDtos.UserVO updateBasic(Long id, UserDtos.BasicUpdateRequest req)
```

**Mapper（新增，注解式动态 SQL，与项目现有风格一致——仓库无 XML mapper）**

见 §5。

**前端类型（`api/system.ts`）**

```ts
export interface UserBasicUpdatePayload {
  realName?: string; phone?: string; className?: string
  deptId?: number; status?: number; roleCodes?: string[]
  expectedUpdatedAt?: string
}
export const updateUserBasic = (id: number | string, data: UserBasicUpdatePayload) =>
  request.put(`/users/${id}/basic`, data) as Promise<UserVO>
export const getUserDetail = (id: number | string) =>
  request.get(`/users/${id}`) as Promise<UserVO>
```

---

## 5. 动态 SQL 拼接方式

### 5.1 设计原则

1. **只更新提交的字段**：`<if test="...">` 逐字段判断，未提交（null）的列不出现在 `SET` 中，天然避免误清空。
2. **清空用 `NULLIF`**：约定空串表示清除，SQL 侧 `SET col = NULLIF(#{val}, '')`，避免 Java 侧改写空串与 null 语义。
3. **`<set>` 自动去除尾逗号**：保证任意字段组合下 SQL 合法。
4. **始终更新 `updated_at`**：即使本次只改角色（角色在另一张表），也刷新主表活动时间，便于滞留/审计判断。
5. **单条语句 + 影响行数判定**：`WHERE id AND deleted = 0`，行数为 0 视为不存在或已删除。
6. **不拼接字符串**：全部 `#{}` 预编译，杜绝注入（与项目 SQL 安全基线一致）。

### 5.2 Mapper 实现

```java
@Update("""
        <script>
        UPDATE sys_user
        <set>
          <if test="req.realName != null">real_name = #{req.realName},</if>
          <if test="req.phone    != null">phone     = NULLIF(#{req.phone}, ''),</if>
          <if test="req.className != null">class_name = NULLIF(#{req.className}, ''),</if>
          <if test="req.deptId   != null">dept_id   = NULLIF(#{req.deptId}, 0),</if>
          <if test="req.status   != null">status    = #{req.status},</if>
          updated_at = NOW()
        </set>
        WHERE id = #{id} AND deleted = 0
        <if test="req.expectedUpdatedAt != null">
          AND updated_at = #{req.expectedUpdatedAt}
        </if>
        </script>
        """)
int updateBasic(@Param("id") Long id, @Param("req") UserDtos.BasicUpdateRequest req);
```

### 5.3 生成效果（示例）

**场景 A：只改手机号与姓名**

```sql
UPDATE sys_user
   SET real_name = ?, phone = NULLIF(?, ''), updated_at = NOW()
 WHERE id = ? AND deleted = 0
```

**场景 B：只改状态（供行内开关复用）**

```sql
UPDATE sys_user
   SET status = ?, updated_at = NOW()
 WHERE id = ? AND deleted = 0
```

**场景 C：学生转员工（清班级、设部门）**

```sql
UPDATE sys_user
   SET class_name = NULLIF(?, ''), dept_id = NULLIF(?, 0), updated_at = NOW()
 WHERE id = ? AND deleted = 0
```

**场景 D：全字段都不传（业务层已拦截，不会到达 SQL）**

> Service 先做 R7 空提交校验，抛 `PARAM_INVALID`，因此不会产生仅更新 `updated_at` 的空转写。

### 5.4 角色更新（非动态 SQL，但同一事务）

角色是关联表，采用"全删全插"（与既有 `assignRoles` 一致），仅在 `roleCodes != null` 时执行：

```sql
DELETE FROM sys_user_role WHERE user_id = #{userId};
INSERT INTO sys_user_role (user_id, role_id) VALUES (#{userId}, #{roleId});  -- 逐条
```

> 说明：角色集合数量少（≤4），逐条插入可读性更好；若后续角色数量增长，可改为 `<foreach>` 批量插入。

### 5.5 事务与副作用顺序

```
BEGIN
  1. SELECT 用户（不存在 → 4001）
  2. 业务校验 R2/R3/R4/R5/R6/R7（读角色、部门、最后管理员计数）
  3. updateBasic(...)  ← 动态 SQL；0 行 → 4001 或 3013
  4. IF roleCodes != null → assignRoles()
  5. COMMIT
  6. AFTER COMMIT: 需要踢下线时 logoutAll(id)
```

> 踢下线放在事务提交后，避免回滚后用户被误踢；触发条件见 §6.3。

---

## 6. 权限校验与异常处理策略

### 6.1 认证与授权

| 层 | 措施 |
|---|---|
| 接口层 | `@PreAuthorize("hasAuthority('user:edit')")`；当前仅 `ADMIN` 拥有该权限（`MANAGER` 权限集不含 user:*） |
| 前端入口 | 「编辑」按钮 `v-if="auth.has('user:edit')"`；无权限不渲染（不作为唯一防线） |
| 数据范围 | 用户管理为全局能力，不做部门隔离；受 `user:edit` 约束即可 |
| 审计 | `@OperLog(module="用户管理", action="编辑用户")` 记录操作人、IP、参数（手机号脱敏）、耗时、成败 |

### 6.2 自我保护（防自锁）

| 规则 | 实现 |
|---|---|
| 不能禁用自己 | `id.equals(CurrentUserHolder.id()) && status == 0` → 3001 |
| 不能移除自己的管理员角色 | `id.equals(CurrentUserHolder.id())` 且新角色不含 `ADMIN` → 3001 |
| 不能禁用最后一个管理员 | 目标含 ADMIN 且为唯一启用管理员，`status` 改为 0 → 3001 |
| 不能把最后一个管理员降级 | 同上条件下 `roleCodes` 不含 ADMIN → 3001 |

### 6.3 令牌失效（即时生效）

| 变更 | 是否踢下线 | 原因 |
|---|---|---|
| 角色变更 | ✅ `logoutAll(id)` | 权限码缓存在 Redis 登录快照中，不踢则旧权限继续生效至 token 过期 |
| 部门变更 | ✅ `logoutAll(id)` | `deptId` 参与数据权限过滤，同样来自快照 |
| 状态改为禁用 | ✅ `logoutAll(id)` | 禁止已禁用账号继续持有效令牌 |
| 仅改姓名/手机号/班级 | ❌ | 不参与鉴权，无需中断会话 |

### 6.4 异常码映射

| 场景 | HTTP | 业务码 | 提示 |
|---|---|---|---|
| 未登录 / token 失效 | 200 | 1001 / 1002 | 未登录或登录已失效 |
| 无 `user:edit` | 200 | 2001 | 无操作权限 |
| 参数格式错误（手机号等） | 200 | 1003 | 具体字段 message |
| 用户不存在 / 已删除 | 200 | 4001 | 用户不存在 |
| 空提交 | 200 | 1003 | 没有需要更新的内容 |
| 违反 R2/R3/R4 | 200 | 3001 | 不能禁用当前登录账号 / 不能移除自己的管理员角色 / 系统至少保留一名启用的管理员 |
| 违反 R5/R6 | 200 | 1003 | 学生必须填写班级 / 非学生必须选择所属部门 |
| 乐观校验失败 | 200 | 3013 | 数据已变更，请刷新后重试 |
| 未预期异常 | 200 | 5000 | 系统繁忙，请稍后重试（含 traceId） |

> 沿用项目现有约定：业务异常返回 HTTP 200 + 业务码，前端按 `code` 分支处理；`BizException` 由全局异常处理器统一转为 `Result`。

### 6.5 日志与可观测性

- 操作日志记录**变更前后值**（在 Service 中拼装 `变更：phone 139****9003 → 139****9013`），便于追责；手机号按中间四位脱敏。
- 关键拒绝路径（自我保护、最后管理员）记录 `WARN` 日志，包含操作人 ID 与目标用户 ID。
- 失败不影响主流程：操作日志写入异常仅告警，不回滚业务（与既有切面行为一致）。

---

## 7. 前端改动设计

### 7.1 文件清单

| 文件 | 改动 |
|---|---|
| `src/api/system.ts` | 新增 `getUserDetail`、`updateUserBasic` 及 `UserBasicUpdatePayload` 类型 |
| `src/views/UserEditModal.vue` | **新增**组件：编辑弹窗（复用 `Modal`、`Icon`、`toast`、`confirmDialog`），避免 `UserListView.vue`（现 700+ 行）继续膨胀 |
| `src/views/UserListView.vue` | 操作列新增「编辑」按钮；引入组件；保存成功后以返回 VO 替换 `rows` 中对应项 |
| `src/views/__tests__/UserEditModal.spec.ts` | **新增**单测 |

### 7.2 组件契约

```ts
props: { userId: number | null; open: boolean }
emits: { close: []; saved: [user: UserVO] }
```

内部职责：加载详情 → 表单初始化 → 前端校验 → 提交 → 错误映射 → 触发 `saved`。

### 7.3 前端校验与后端的关系

前端校验只做**即时体验**（手机号格式、姓名非空、角色至少一项、班级/部门联动必填），后端仍是唯一权威。前端不重复实现 R3/R4（需要后端状态），仅在后端返回 3001 时展示提示并保持弹窗打开。

---

## 8. 测试计划

| 层级 | 用例 |
|---|---|
| 后端单测（Mockito，新增 `UserServiceBasicUpdateTest`） | ① 正常更新（仅姓名）② 手机号空串→清除 ③ 空提交被拒 ④ 禁用自己被拒 ⑤ 移除自己管理员角色被拒 ⑥ 最后一个管理员禁用被拒 ⑦ STUDENT 缺班级被拒 ⑧ 非学生缺部门被拒 ⑨ 用户不存在 ⑩ 影响行数 0 → NOT_FOUND |
| 动态 SQL 验证 | 通过接口冒烟断言：仅改姓名后 `phone`/`dept_id` 原值不变（证明未误清空） |
| 接口冒烟（curl/Python） | 登录 ADMIN → 取详情 → 改姓名+手机号 → 再取详情比对；改角色后旧 token 失效（401/1001） |
| 前端单测（Vitest） | 打开弹窗加载详情、角色联动显示班级/部门、提交 payload 正确、错误提示渲染 |
| 手工验收 | 管理员编辑学生/维修工/管理员各一次；尝试禁用自己；尝试移除最后一个管理员 |

---

## 9. 风险与已知限制

| 项 | 说明 | 缓解 |
|---|---|---|
| 并发覆盖 | `sys_user` 无 `version` 列，两人同时编辑以最后提交为准 | 提供可选 `expectedUpdatedAt` 乐观校验（§5.2），默认关闭；管理端低频，风险可接受 |
| 角色提权 | 拥有 `user:edit` 者可给别人加 ADMIN | 当前仅 ADMIN 拥有该权限；后续如需下放给 MANAGER，应拆分 `user:role:assign` 独立权限 |
| 审计粒度 | 操作日志记录的是请求参数，非"变更前/后"结构化差异 | 在 Service 内拼装差异描述写入 remark（§6.5） |
| 踢下线副作用 | 改部门/角色会中断用户当前会话 | 前端二次确认提示"保存后该用户需重新登录" |
| 手机号脱敏 | 列表已脱敏展示（`maskPhone`），编辑框需展示完整号码 | 详情接口返回完整号码（既有行为），仅对有 `user:list` 的 ADMIN 可见 |

---

## 10. 待确认事项（需你拍板）

| # | 决策点 | 建议方案 | 备选 |
|---|---|---|---|
| 1 | 是否允许在编辑弹窗中修改**角色** | 允许（带 R1/R3/R4 保护） | 只读展示，角色变更另设入口 |
| 2 | **登录名 / 学号 / 工号**是否只读 | 只读（推荐，理由见 §3.2） | 允许修改登录名（需同步迁移审计数据） |
| 3 | 清空语义 | 空串=清除；`deptId=0`=清除 | 增加显式 `clearFields: []` 字段 |
| 4 | 是否启用乐观校验 `expectedUpdatedAt` | 暂不启用，仅保留字段 | 默认启用 |
| 5 | 编辑入口位置 | 行内「编辑」按钮（与重置并列） | 行点击打开抽屉（与工单详情一致） |

---

## 11. 实施顺序（确认后执行）

1. 后端：`UserDtos.BasicUpdateRequest` → `SysUserMapper.updateBasic`（动态 SQL）→ `UserService.updateBasic`（校验 + 角色 + 踢下线）→ `UserController` 新增端点。
2. 后端单测 `UserServiceBasicUpdateTest`（10 例）+ 编译 + 冒烟。
3. 前端：`api/system.ts` → `UserEditModal.vue` → `UserListView.vue` 接线。
4. 前端单测 + `vite build`。
5. 文档同步：`系统设计说明书` §6 接口表、`PROGRESS.md`。
6. 演示数据回归：用 `demo_mgr/demo_admin` 账号验证编辑学生与维修工。

---

## 12. 实施记录（v1.1，2026-11-06）

### 12.1 落地清单

| 层 | 文件 | 说明 |
|---|---|---|
| DTO | `system/dto/UserDtos.java` | 新增 `BasicUpdateRequest`（含 `expectedUpdatedAt`） |
| Mapper | `system/mapper/SysUserMapper.java` | 新增 `updateBasic`，注解式 `<script>` 动态 SQL |
| Service | `system/service/UserService.java` | 新增 `updateBasic`、`normalizeRoles`、`activeAdminCountExcluding`、`logoutAfterCommit` |
| Controller | `system/controller/UserController.java` | 新增 `PUT /users/{id}/basic`，`@OperLog` + 手机号脱敏 |
| 单测 | `test/.../UserServiceBasicUpdateTest.java` | 13 例 |
| 前端 API | `api/system.ts` | `getUserDetail`、`updateUserBasic`、`UserVO`、`UserBasicUpdatePayload` |
| 前端组件 | `views/UserEditModal.vue` | 新增编辑弹窗（只读区 + 可编辑区 + 角色联动） |
| 前端接线 | `views/UserListView.vue` | 行内「编辑」按钮（`user:edit` 可见）+ 保存后就地替换行 |
| 前端单测 | `views/__tests__/UserEditModal.spec.ts` | 7 例 |
| 图标 | `ui/Icon.vue` | 新增 `edit`（铅笔）图标 |

### 12.2 与设计的差异

| 项 | 设计 | 实现 | 原因 |
|---|---|---|---|
| 主表更新时机 | 只要提交就执行 UPDATE | 仅当存在字段实际变化时才执行 | 避免「只改角色」时产生仅更新 `updated_at` 的空转写；单测 `roleChangeAssignsRolesAndLogsOut` 已锁定该行为 |
| 部门有效性校验 | 每次提交都校验 | 仅在**角色变更**或**显式提交 deptId** 时校验 | 若只改手机号也校验历史部门，脏数据会导致无关编辑被拒 |
| 姓名校验 | 非空 + 字符白名单 | 同设计，另在 Service 侧 `trim()` 后落库 | 防止首尾空格进入数据 |

### 12.3 验证结果

| 项 | 结果 |
|---|---|
| 后端单测 | 25 例全部通过（原状态机 12 + 新增 13） |
| 前端单测 | 47 例全部通过（原 40 + 新增 7） |
| `vite build` | 通过 |
| 接口冒烟：仅改姓名+手机号 | `code=0`；`className`、`deptId` 保持原值 → 动态 SQL 未误清空 |
| 接口冒烟：禁用自己 | `3001` 不能禁用当前登录账号 |
| 接口冒烟：空提交 | `1003` 没有需要更新的内容 |
| 接口冒烟：手机号 `123` | `1003` 手机号格式不正确 |
| 接口冒烟：变更部门后旧令牌 | `1001`（已注销，符合 §6.3） |
