# CampusFix — 校园设施报修与工单管理平台

> 项目介绍与开发进度看板。**本文件随开发进度持续更新**，与代码状态保持同步。
>
> 最后更新：2026-11-06　|　当前阶段：**第 1 阶段 基础功能开发（进行中）**　|　后端可运行 ✅　|　前端界面改版 ✅　|　站内聊天与通知 ✅　|　统计与预警 ✅　|　操作日志切面 ✅　|　JWT 密钥安全加固 ✅　|　状态机单元测试 ✅　|　未读消息 UI 修复 ✅　|　状态滞留巡检 ✅　|　前端单元测试 ✅　|　正式版界面清理 ✅　|　角色演示账号 ✅　|　用户编辑功能 ✅

## 一、项目简介

CampusFix 面向校园场景，把"发现设施问题 → 报修 → 审核 → 派单 → 维修 → 验收 → 评价"的全过程线上化，并沉淀设施档案与故障数据，辅助后勤运维决策。

- 业务流程闭环：**报修 → 工单 → 分配 → 维修 → 验收 → 评价 → 数据分析**
- 四类角色：学生、维修人员、后勤管理员、系统管理员
- 一期形态：**PC 端 Web**（Vue 3），微信小程序为二期，目录已预留

## 二、当前进度

图例：✅ 已完成并自测　🚧 进行中　⬜ 未开始

### 第 1 阶段：基础功能开发

| 模块 | 状态 | 说明 |
|---|---|---|
| 数据库结构与测试数据 | ✅ | 25 张表 + 测试数据（11 个账号、11 张覆盖全状态的工单、41 条流转日志） |
| Maven 工程与启动骨架 | ✅ | Spring Boot 3.5.16 / Java 21（JDK 25 运行）/ MyBatis-Plus 3.5.18 |
| 统一响应与全局异常 | ✅ | `Result{code,message,data,traceId}`、错误码分段、参数校验统一处理 |
| 认证与令牌 | ✅ | 登录、刷新、注销、当前用户、改密；JWT + 白名单；失败 5 次锁定 15 分钟 |
| 用户管理 | ✅ | 分页、增改、启停、重置密码、信誉分调整（写流水） |
| 学生导入 | ✅ | 单个添加 + Excel 批量；表头强校验、逐行校验、部分成功并返回失败明细 |
| 角色与权限 | ✅ | RBAC 五表、权限树、分配权限、方法级 `@PreAuthorize` |
| 部门树 | ✅ | 树形维护、多部门分片基础（`getDescendantIds` 供数据权限用） |
| 系统参数 | ✅ | 键值参数 + 60s 本地缓存，信誉分/权重/天数可运行期调整 |
| 操作日志 | ✅ | `@OperLog` 注解 + AOP 切面：模块/操作/方法/参数（脱敏）/IP/成败/错误/耗时落 `sys_oper_log`，日志失败不影响业务；已覆盖工单全动作、预约、验收、投诉、用户管理、系统参数（2026-10-31） |
| 操作日志查询页 | ✅ | `GET /oper-logs` 分页筛选（账号/模块/成败/日期区间）+ 管理端「操作日志」页（侧栏 `log:list` 可见，行点击查看脱敏参数与错误详情）（2026-10-31） |
| 维修工日历 | ✅ | `GET /repairers/{id}/calendar` 未来 N 天已确认上门时段 + 在办数；指派/转派面板选中维修工后展示忙闲；本人亦可查看自己的日历（2026-10-31） |
| 维修异常态 | ✅ | 挂起（必填原因）/恢复/无法维修 REST 端点补齐，维修面板按状态显示对应按钮，挂起时长不计入维修用时（2026-10-31） |
| 工单状态机 | ✅ | 17 个动作 / 18 条合法边；角色 + 前置校验 + 副作用；`version` 乐观锁 CAS |
| 提交报修与审核 | ✅ | 图片 1–9 张、隐私确认、质保判定（3 天同位置同类型）、审核通过 / 驳回、管理员取消 |
| 派单与抢单 | ✅ | 指派 / 转派 / 抢单 / 接单 / 退单；抢单冲突返回 409；退单扣 10 信誉分 |
| 24h 自动派单 | ✅ | 每 10 分钟扫描，按「同部门 → 在办最少」派单，系统身份写流转日志 |
| 维修预约 | ✅ | 维修工给 1–3 个时段 → 报修人选择 → 维修工确认 → 转处理中；改约与拒绝改约；仅 CONFIRMED 做重叠校验 |
| 维修过程 | ✅ | 到达/开始打点、维修内容与材料、前后照片、提交结果入待确认（写入 3 天验收截止） |
| 确认验收 | ✅ | 报修人确认完成（解锁评价）；延期申请与管理员审批（通过后顺延截止时间） |
| 返修 | ✅ | 待确认 → 待处理：清空维修人、作废预约、返修计数 +1，可重新派单/抢单 |
| 超时自动验收 | ✅ | 每日 02:00 扫描 `confirm_deadline` 过期工单，自动完成并扣报修人 10 信誉分 |
| 评价 | ✅ | 三维评分（速度/态度/效果 1–5）+ 加权总分；权重读 `sys_config`；一单一次；匿名对外脱敏；评价后维修工 +1 信誉分 |
| 投诉 | ✅ | 报修人提交 → 管理端受理 → 成立/不成立；成立扣维修工 10 分；按部门分片，同单只允许一条未终结投诉 |
| 聊天、通知 | ✅ | 工单维度聊天（相关方校验、拉取即已读、1–500 字）+ 站内通知（状态机触发点、未读数、已读）+ WebSocket 实时推送（token 握手鉴权、断线重连、轮询兜底）（2026-10-31） |
| 统计与预警 | ✅ | `/stats/overview` 后端聚合（角色数据权限、基线 §12 口径）；工作台改接真实统计（去 100 条样本估算）；设施状态联动（PROCESSING→REPAIRING、完成→NORMAL）；报修可选关联设施；重复故障预警（同楼栋+7 天+同类型+≥5 单，每日 08:00）+ 管理端横幅（2026-10-31） |
| 独立统计页 | ✅ | `/stats` 页（侧栏「洞察」，管理端）：维修工绩效排行（完成数/加权均分/好评率/平均处理时长）、近 6 个月新增与完成双柱趋势、投诉率与投诉结构；`GET /stats/insight` 后端聚合，后勤按部门过滤（2026-10-31） |
| 消息中心页 | ✅ | `/notifications` 页：全量通知（工单/聊天/系统筛选 + 只看未读 + 分页），顶栏通知铃加「查看全部消息」入口，侧栏「洞察」组常驻入口（2026-10-31） |
| 前端工程 | ✅ | Vite 5 + Vue 3.5 + TS + Pinia + Vue Router + Axios；自建设计系统（无组件库）；登录、主布局、工作台、工单管理、用户管理 |
| 前端界面改版 | ✅ | 设计系统重构 + 全页面视觉升级；工单管理改为「故障分类优先」导航；学生 / 维修人员工作台按角色轻量化（2026-10-30 / 31，详见 §八「界面改版记录」） |
| 前端页面：统计 | ✅ | `/stats` 独立统计页已上线（侧栏「洞察」，管理端），数据来自 `GET /stats/insight`（2026-10-31） |
| JWT 密钥安全加固 | ✅ | 移除 `application.yml` / `docker-compose.yml` 中硬编码的 JWT 签名密钥默认值，改为 `JWT_SECRET` 环境变量强制注入 + `JwtProperties` 启动强校验（未配置或 <32 字节拒绝启动）；MySQL root 密码同样去默认值（2026-11-03） |
| 状态机单元测试 | ✅ | `OrderStateMachineTest` 12 个用例：转换表完整性、审核/抢单/自动派单/自动验收流转、非法流转、角色不符、归属守卫、乐观锁 409、日志写入（2026-11-03） |
| 未读消息 UI 修复 | ✅ | ① 未读徽标「未读」由竖向堆叠改为横向（补 `white-space: nowrap` + `flex-shrink: 0`）；② 引入全局 `stores/notification.ts`（Pinia）统一未读计数，顶栏铃铛/侧栏/消息中心三处角标实时同步，点击已读即时更新（2026-11-03） |
| 状态滞留巡检 | ✅ | 每日 09:00 扫描「`updated_at` 超 7 天仍停留非终态」的工单，推送站内信（type=SYSTEM，落库+WS）给 ADMIN + MANAGER 全员；`order_stall_alert(order_id, status)` 唯一键去重（同单同状态只推一次，状态变化后再次滞留会重推）；阈值与 cron 入 `campusfix.order.stall-alert-*` 可配（2026-11-04） |
| 前端单元测试 | ✅ | 从零搭建 Vitest 2.1.9 + @vue/test-utils + jsdom + @pinia/testing（锁 2.x 兼容 Vite 5）；39 个用例覆盖 auth store（登录/权限/登出）、notification store（未读计数边界）、LoginView（校验/提交/跳转/异常）、NewOrderModal（逐级位置选择/六重校验链/提交）；`npm test` 全绿（2026-11-04） |
| 维修工个人中心 | ✅ | 侧栏「个人中心」入口（仅维修工）+ `/profile` 页：① 身份与头像同卡展示（可追溯）；② 姓名/联系方式编辑（工号/所属区域只读）；③ 头像上传（JPG/PNG、200–4096px、≤2MB、魔数+尺寸三重校验、强制项未传阻止保存）；④ 学生端工单详情补全维修工头像/工号/所属区域（接单后可见手机号）；新增 `profile:view` 权限 + V4 迁移脚本（2026-11-04） |
| 个人中心字段权限与头像规则 | ✅ | 姓名/工号/所属区域只读（仅手机号可编辑）；默认头像改「品牌色底+姓氏首字」；`avatar_deadline` 一天内强制更换（逾期红色横幅提醒）；新增「信息更改申请」流程——维修工提交 → 后勤管理员审核（通过落地/打回附原因+站内通知）→ `profile_change_request` 表 + `ProfileChangeStatus` 枚举 + `profile:change:review` 权限 + V5 迁移（2026-11-04） |
| 头像模块展示修复 | ✅ | 修复个人中心顶部默认头像塌陷/偏位（`.default-avatar` 由 `height:100%` 改为 `position:absolute; inset:0` 绝对铺满，父容器补 `position:relative`），个人中心顶部、学生端预览、工单详情三处默认头像统一（2026-11-04） |
| 正式版界面清理 | ✅ | 移除界面全部开发类内容：工作台「平台建设进度」面板、工作台与顶栏「接口文档/Swagger」入口、登录页演示账号与预填凭据、欢迎横幅权限数、「后端聚合」标签；「接口/落库/脱敏/分片/注入/口径/字段」等 18 处技术措辞改写为业务语言。被移除的技术内容归档至 [`CampusFix-前端技术信息归档.md`](./CampusFix-前端技术信息归档.md)（2026-11-05） |
| 角色演示账号与数据 | ✅ | 新增 `db/demo/demo_seed.sql`：学生/维修/后勤/系统管理员各 1 个独立演示账号（`demo_stu`/`demo_rep`/`demo_mgr`/`demo_admin`，密码 `Cf@123456` 不强制改密），配套 20 张工单覆盖 10 种状态及预约、维修记录、评价、投诉、延期、聊天、通知、日志、信誉流水；幂等可重跑，演示段 ID 9001–9099 隔离，附 `demo_cleanup.sql` 一键清理。账号与接口已实测通过（2026-11-05） |
| 登录名兼容学号/工号 | ✅ | 修复登录页文案「使用学号或工号登录」与后端实现不一致：`AuthService.findByLoginName()` 依次回退匹配 `username` → `student_no` → `staff_no`（三字段均为唯一索引，逐级查询避免跨字段同名冲突）。已实测 `D0001`（工号）、`D2026099`（学号）、`demo_stu`（登录名）三种写法均可登录（2026-11-05） |
| 密码框双眼睛修复 | ✅ | 登录页密码框出现两个「眼睛」：右侧为自定义显示/隐藏按钮，另一个是 Edge/IE 原生密码显示控件（`::-ms-reveal`）。已在 `theme.css` 全局隐藏原生控件与清除按钮，并为密码框右侧预留 44px 内边距，避免长密码被图标遮挡；同时对调图标与状态的对应关系（密文→划线眼、明文→睁眼），并新增断言锁定该映射（2026-11-06） |
| 用户管理「编辑用户」 | ✅ | 列表行内新增「编辑」（`user:edit` 可见），弹窗分只读区（登录名/学号工号/信誉分/最近登录）与可编辑区（姓名、手机号、角色、班级或部门、状态），角色联动切换班级/部门。新增 `PUT /users/{id}/basic`：**动态 SQL**（`<set>`+`<if>`，null=不改、空串=清除、deptId=0 清除部门），仅写有变化的列。含自我保护（不能禁用自己/移除自身管理员）、最后管理员保护、学生-教职工角色互斥与班级/部门一致性校验；角色/部门/禁用变更提交后注销该用户令牌。后端 13 例 + 前端 7 例单测，接口冒烟验证动态 SQL 不误清空（2026-11-06） |
| 工单分类配色统一 | ✅ | 「故障分类」面板由 8 种色相（蓝/紫/青/琥珀/橙/棕/青蓝/灰）统一为石板灰系 `#64748b`（与「公共设施」「其他」同色系），同步调整面板内标题徽标、选中态、悬浮态与箭头色，移除品牌红高亮；表格行内分类小图标随同一映射一并统一（2026-11-06） |
| 工作台配色统一 + 色系规范 | ✅ | 顶部横幅由「深灰蓝底 + 红/蓝双光晕 + 橙红按钮 + 多色插画」改为纯石板灰渐变 + 亮面反色按钮 + 灰阶插画；四张指标卡由红/橙/蓝/绿统一为 `--slate-500`（图标底 `--slate-100`）；趋势柱与楼栋排行（原五色）统一为石板灰；并按截图复查补齐遗漏项——快捷入口三色图标（品牌红/蓝/紫）、最近工单列表图标（品牌红）、卡片悬浮粉色边框，全部改为石板灰。`theme.css` 新增 `--slate-50~950` 令牌，产出 [`CampusFix-界面配色说明.md`](./CampusFix-界面配色说明.md)（色阶用途、设计意图、搭配规范、应用要点、对比度基线、检查清单）（2026-11-06） |
| 侧边栏选中态改白 | ✅ | 左侧导航当前项由「品牌红渐变药丸 + 白字」改为「白色底 + `--slate-900` 文字」，图标 `--slate-700`；白底与深色侧栏对比约 17:1，文字对比约 17:1。同步处理白底下的徽标反色（普通徽标灰底深字、未读徽标淡红底深红字），未选中项与整体布局不变（2026-11-06） |
| 登录页配色统一 | ✅ | 登录页整体切换为石板灰：Logo 标记渐变、标题强调行渐变、特性条目图标、WELCOME BACK 眉标、登录按钮（`--slate-900`）、输入框聚焦环与页眉兜底渐变全部由品牌红/橙红改为 `--slate-*`；错误提示保留语义红。改动仅作用于登录页（`.login-card .btn-primary` 覆盖全局主按钮），全站其他按钮仍为品牌红（2026-11-06） |
| 导航栏配色统一 | ✅ | 侧边栏底色由「深灰蓝 + 红/紫双光晕」改为深石板渐变 + 同色系灰光晕；Logo 标记与用户头像由品牌红渐变改为石板灰；未选中文字 `--slate-400`、图标 `--slate-500`、悬停 7% 白底转白字；选中态维持白底 + `--slate-900`；未读徽标由红底白字改为 `--slate-100` 底 + `--slate-900` 字；分组标签提亮至 `--slate-500`。顶栏同步：搜索框聚焦由红改灰、通知红点改 `--slate-600`、用户头像改灰。桌面端与 ≤960px 图标栏两套布局均已核对（2026-11-06） |
| 中性状态标签改色系 | ✅ | 「已分配 / 已取消」两个状态标签改用石板灰：新增 `.tag-slate`（底 `--slate-100`、字 `--slate-700`），替换原 `tag-ink` 与默认 `.tag`；圆点颜色保持原值（`#8a90a0` / `#9aa3b2`）不受文字色影响，因组件用独立 `background` 着色。文字对比约 6.8:1（较原 6.6:1 略有提升，且色相与全站统一）；其余状态标签（待处理/处理中/已完成等）保持语义色不变（2026-11-06） |
| 全站状态标签与主按钮统一 | ✅ | `OrderStatusTag` 全部 10 种状态统一为 `tag-slate`（`--slate-100` 底 + `--slate-700` 字），状态语义改由前置 6px 圆点承载（红/橙/蓝/绿/灰原色保留）；`.btn-primary` 由品牌红渐变改为 `--slate-900` 实色（hover `--slate-800`），全站主操作按钮（提交报修、查询、保存等）同步生效；登录页原单独覆盖随之移除。危险类按钮 `.btn-danger` 与错误提示仍保留红色语义（2026-11-06） |
| 分页与页头文字配色统一 | ✅ | 分页控件：选中页由红色渐变改为 `--slate-900` 实底，悬停边框/文字改 `--slate-400/700`；页头 `SERVICE DESK` 类小标题（`.page-hd .eyebrow` 及其前置短横线）由品牌红改为 `--slate-500/400`；KPI 卡片图标由红改 `--slate-500`；用户管理角色标签（系统管理员/后勤管理员/维修人员）与投诉状态标签（待受理/已成立/不成立/处理中）统一为 `tag-slate`。操作日志的「成功/失败」保留红绿（结果判定，颜色是唯一信号）（2026-11-06） |
| 状态筛选选项消失缺陷 | ✅ | 「本页状态」选项原由当前 `rows` 推导，选择某状态后接口只返回该状态数据，导致其余选项消失、无法切换（只能点「全部」恢复）。改为独立快照 `statusSnapshot`：仅在无状态筛选时刷新，筛选期间保持选项稳定；「全部」计数取快照值而非当前筛选结果；带 `?status=` 直达时补一次无状态查询回填快照。新增 `OrderListView.filter.spec.ts` 4 例回归（选项数量、选中态、全部计数、恢复全部），筛选框选中色同步改为 `--slate-500`（2026-11-06） |
| 工单详情配色统一 | ✅ | 详情抽屉（`OrderDetailDrawer`）及内部五个子面板统一为石板灰：状态摘要卡（红晕+粉底+粉边 → 灰晕+`#f1f5f9/#f8fafc`+`--slate-200`）、摘要图标与维修人默认头像（品牌红渐变 → `--slate-600/800`）、小节标题竖线（`--slate-500`）、预约时段 chip（`--accent`+`#eef2ff` → `--slate-700/100`）、顶部三枚标记与流转目标标签（`tag-ok/warn/ink` → `tag-slate`）、流转时间轴节点（绿点 → `--slate-400`）、聊天「我的」气泡（`brand-l`+`#f6cfd3` → `--slate-100/200`）、评价提示条（`brand-l2` → `--slate-100`）。**悬停/聚焦态同步**：全局 `.field:focus` 与聊天输入聚焦改为 `--slate-500` + 灰环；删除时段、添加照片、必填星号等悬停由品牌红改为 `--slate-50/100/500/600/700`。保留语义色：验收截止（临近琥珀/逾期红/已完成绿）、延期审批中、聊天超长计数与驳回原因（错误红）。（2026-11-06） |
| 成员删除（单条） | ✅ | 用户管理行内新增「删除」（`user:delete` 权限，默认仅系统管理员）。采用**逻辑删除**：`deleted=1` + `status=0`，并为 `username/student_no/staff_no` 追加 `#D<id>` 墓碑后缀，释放三个唯一索引，删除后同学号/工号可重新开通。护栏三条：不能删当前登录账号、不能删最后一个启用的系统管理员、作为报修人或维修人仍有未完结工单不可删。删除后立即注销该成员令牌。接口 `DELETE /users/{id}`，带 `@OperLog`（2026-11-06） |
| 成员批量删除 | ✅ | 列表新增复选框列（表头全选含半选态、自动排除当前登录账号、选择仅作用于当前页）与批量工具条；接口 `POST /users/batch-delete`（`user:delete`，`ids` 非空且 ≤200、自动去重）。后端**逐条校验、不做全有或全无**，返回 `{success, failed:[{id,name,reason}]}`；全部成功走轻提示，存在跳过项时弹「删除结果」窗口逐条列出原因。配色沿用石板灰规范（复选框 `--slate-900`、选中行 `--slate-50`、删除按钮复用 `.btn-danger`），未新增色值（2026-11-06） |
| 删除权限与脚本 | ✅ | 新增权限点 `user:delete`（id 63），`data.sql` 内置并授予系统管理员；新增 `db/migration/V6__user_delete_permission.sql` 供旧库增量升级（未执行时前端不显示删除入口、接口返回 2001）。测试：后端 `UserServiceDeleteTest` 9 例、前端 `UserListView.delete.spec.ts` 7 例，全量后端 34 例 / 前端 58 例通过（2026-11-06） |
| 登录页全局封面 | ✅ | 新增项目内静态资源 `src/assets/login-cover.jpg`（1440×826，156KB，`import` 引入，构建为 `login-cover-<hash>.jpg`，无本地路径依赖）；作为**整页全局背景**铺满可视区域（含右侧登录区）：`background-size: cover` + `no-repeat` 等比覆盖、不拉伸不重复；**不做任何模糊处理**（仅 `saturate(.5) brightness(.82)` 去饱和与压暗），并移除登录页内其余 `backdrop-filter`；叠左深右浅双层遮罩（左 0.94→0.78 保文案对比，右 0.92→0.28 保持浅色表单区）；遮罩与封面均 `pointer-events: none`、`z-index` 低于内容，不影响表单可读性与交互；窄屏（≤1100px）隐藏左栏并整屏转浅色遮罩（2026-11-06） |

### 已实现的接口（可用 curl 验证）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/auth/login` | 登录，返回 access/refresh token 与权限码 |
| POST | `/api/v1/auth/refresh` | 刷新令牌 |
| POST | `/api/v1/auth/logout` | 注销（删除白名单） |
| GET | `/api/v1/auth/me` | 当前用户 + 权限 + 菜单 |
| PUT | `/api/v1/auth/password` | 修改密码 |
| GET | `/api/v1/users` | 用户分页（`user:list`） |
| POST/PUT | `/api/v1/users`、`/{id}` | 新增/修改（`user:add` / `user:edit`） |
| PUT | `/api/v1/users/{id}/status` | 启停用（立即踢下线） |
| PUT | `/api/v1/users/{id}/reset-password` | 重置为默认密码 |
| POST | `/api/v1/users/students` | 手动添加单个学生（姓名 / 学号 / 班级 / 手机号） |
| GET | `/api/v1/users/import/template` | 下载学生导入模板（xlsx，含填写说明） |
| POST | `/api/v1/users/import` | Excel 批量导入，返回 总数 / 成功 / 失败 + 逐行原因 |
| POST | `/api/v1/users/{id}/credit` | 调整信誉分 |
| GET | `/api/v1/depts/tree` | 部门树 |
| GET | `/api/v1/roles` | 角色列表 |
| GET | `/api/v1/permissions/tree` | 权限树 |
| PUT | `/api/v1/roles/{id}/permissions` | 分配权限 |
| GET/PUT | `/api/v1/configs`、`/{key}` | 系统参数 |
| POST | `/api/v1/orders` | 提交报修（全角色，需隐私确认 + 1–9 张图） |
| GET | `/api/v1/orders` | 工单列表（学生看自己的 / 维修工看名下与自报 / 后勤按部门 / 管理员全部） |
| GET | `/api/v1/orders/pool` | 抢单池（全部待处理，按优先级排序） |
| GET | `/api/v1/orders/{id}` | 详情（含 `actions`：当前用户可执行的动作） |
| GET | `/api/v1/orders/{id}/logs` | 流转时间轴 |
| POST | `/api/v1/orders/{id}/review`、`/reject`、`/cancel` | 审核通过 / 驳回 / 取消 |
| POST | `/api/v1/orders/{id}/assign`、`/transfer` | 指派 / 转派 |
| POST | `/api/v1/orders/{id}/grab`、`/accept`、`/return` | 抢单 / 接单 / 退单（冲突返回 409） |
| GET | `/api/v1/orders/{id}/schedules` | 预约时段列表（含每个时段当前能否选/能否接受） |
| POST | `/api/v1/orders/{id}/schedules` | 维修工提供可上门时段（1–3 个） |
| PUT | `/api/v1/schedules/{sid}/select` | 报修人选择时段 |
| PUT | `/api/v1/schedules/{sid}/confirm` | 维修工确认时段（工单转处理中，写上门时间） |
| POST | `/api/v1/orders/{id}/reschedule` | 维修工发起改约（新增提议时段） |
| PUT | `/api/v1/schedules/{sid}/accept`、`/reject` | 报修人接受 / 拒绝改约（拒绝则回抢单池） |
| POST | `/api/v1/orders/{id}/arrive`、`/start` | 到达打点 / 开始维修打点 |
| POST | `/api/v1/orders/{id}/suspend` | 挂起（必填原因，挂起时长不计入维修用时） |
| POST | `/api/v1/orders/{id}/resume` | 恢复（挂起 → 处理中） |
| POST | `/api/v1/orders/{id}/unrepairable` | 判定无法维修（必填原因，工单转已驳回） |
| GET/PUT | `/api/v1/orders/{id}/record` | 查看 / 保存维修记录（内容、材料、结果、维修前照片） |
| POST | `/api/v1/orders/{id}/complete` | 提交维修结果（工单转待确认） |
| GET | `/api/v1/orders/{id}/acceptance` | 验收概况（剩余时长、延期记录） |
| POST | `/api/v1/orders/{id}/confirm` | 报修人确认完成 |
| POST | `/api/v1/orders/{id}/rework` | 报修人申请返修（退回抢单池） |
| POST | `/api/v1/orders/{id}/extension` | 报修人申请延期确认 |
| PUT | `/api/v1/extensions/{id}/handle` | 管理员审批延期（通过则顺延） |
| POST | `/api/v1/orders/auto-confirm` | 手动触发一次超时自动验收（运维用） |
| POST | `/api/v1/orders/{id}/evaluation` | 提交评价（报修人，一单一次） |
| GET | `/api/v1/orders/{id}/evaluation` | 查看工单评价（未评价返回 null） |
| GET | `/api/v1/repairers/{id}/evaluations` | 维修工收到的评价列表（匿名按角色脱敏） |
| GET | `/api/v1/repairers/{id}/evaluation-summary` | 维修工评价汇总（均分、各维度、好评率） |
| POST | `/api/v1/orders/{id}/complaint` | 提交投诉（报修人） |
| GET | `/api/v1/orders/{id}/complaints` | 工单的投诉记录 |
| GET | `/api/v1/complaints` | 投诉列表（按部门分片，附各状态计数与投诉率） |
| PUT | `/api/v1/complaints/{id}/handle` | 处理投诉（成立则扣维修工 10 分） |
| GET | `/api/v1/notifications` | 通知列表（分页，附未读数） |
| GET | `/api/v1/notifications/unread-count` | 未读数 |
| PUT | `/api/v1/notifications/{id}/read` | 标记单条已读 |
| PUT | `/api/v1/notifications/read-all` | 全部已读 |
| GET | `/api/v1/orders/{id}/messages` | 工单会话历史（拉取即已读） |
| POST | `/api/v1/orders/{id}/messages` | 发送消息（相关方，1–500 字） |
| WS | `/ws?token=<accessToken>` | 实时推送（NOTIFY / CHAT / CONNECTED 帧） |
| POST | `/api/v1/orders/auto-assign` | 手动触发一次超时自动派单（运维用） |
| GET | `/api/v1/stats/overview` | 工作台统计总览（KPI/状态计数/完成数/时长/趋势/TOP5，按角色出数） |
| GET | `/api/v1/facilities?locationId=` | 某位置下的设施列表（报修关联用，已报废不返回） |
| GET | `/api/v1/fault-alerts` | 高频故障预警列表（管理端） |
| GET | `/api/v1/fault-alerts/new-count` | 未处理预警数（横幅角标） |
| PUT | `/api/v1/fault-alerts/{id}/status` | 标记预警 READ / HANDLED |
| POST | `/api/v1/fault-alerts/scan` | 手动触发一次预警扫描（运维用） |
| GET | `/api/v1/oper-logs` | 操作日志分页（账号/模块/成败/日期筛选，`log:list`） |
| GET | `/api/v1/oper-logs/modules` | 日志模块下拉（去重，`log:list`） |
| GET | `/api/v1/repairers/{id}/calendar` | 维修工日历（未来 N 天已确认时段 + 在办数；本人或管理端） |
| GET | `/api/v1/stats/insight` | 统计页洞察（维修工绩效排行、月度趋势、投诉汇总；`order:review` / `order:assign`） |
| GET | `/api/v1/repairers` | 可指派维修工（在办少的排前面） |
| POST | `/api/v1/files/upload` | 上传图片（后缀 + 魔数 + 大小三重校验） |
| GET | `/api/v1/locations/tree` | 位置树（校区→区域→楼栋→楼层→房间） |
| GET | `/api/v1/categories` | 故障类型（含责任部门） |
| GET | `/swagger-ui.html` | 在线接口文档 |

### 学生导入约定

| 项 | 规则 |
|---|---|
| 模板表头 | `姓名 | 学号 | 班级 | 手机号`，**顺序与文案必须完全一致**，增删列会直接拒绝 |
| 必填 | 姓名、学号、班级；手机号选填（填则须为 11 位手机号） |
| 学号 | 4-30 位数字或字母，系统内唯一，**同时作为登录账号** |
| 初始密码 | 统一 `Cf@123456`，`pwd_reset_flag=1`，首次登录需修改 |
| 规模 | 单次 ≤ 2000 行，空行自动跳过 |
| 失败策略 | **逐行校验、部分成功**：合法行照常入库，非法行跳过并在 `errors` 中给出「行号 / 学号 / 姓名 / 原因」 |
| 去重 | 同时校验「文件内重复」与「库内已存在」 |

前端入口：用户管理页右上角「添加学生」「批量导入」，弹窗内可直接下载模板并查看失败明细。

## 三、技术栈（与《系统设计说明书》§2.4 一致）

| 分类 | 选型 |
|---|---|
| 后端 | **Java 17**（`--release 17`，字节码 major 61）、Spring Boot 3.5.16、Spring Security、MyBatis-Plus 3.5.18、MySQL 8.0.46 |
| 令牌/缓存 | JWT（jjwt 0.12.6）+ 令牌存储抽象（生产 Redis / 本地可降级进程内） |
| 文档 | springdoc-openapi 2.8.9（Swagger UI） |
| 前端 | Vue 3.5 + Vite 5 + TypeScript + Pinia + Vue Router + Axios；**UI 为自建设计系统**（移植自 DhuStore），不依赖组件库 |
| 存储 | 本地目录 / 阿里云 OSS，通过 `FileStorageService` 切换 |

## 四、目录结构

```
CampusFix/
├── backend/                     【后端】Spring Boot，产物 campusfix.jar
│   ├── db/
│   │   ├── schema.sql           建库建表（25 张）
│   │   └── data.sql             测试数据
│   ├── src/main/java/com/campusfix/
│   │   ├── common/              统一响应、异常、枚举、配置、安全、跨域
│   │   ├── auth/                认证
│   │   ├── system/              用户、角色、部门、参数、日志
│   │   ├── order/               工单域：状态机、派单、预约、维修过程
│   │   ├── message/             通知与聊天：notification / chat_message、WebSocket 推送
│   │   ├── asset/               位置树
│   │   └── file/                存储抽象（local / oss）与上传接口
│   ├── Dockerfile               独立镜像
│   └── pom.xml
├── frontend/                    【前端】Vue 3，产物 dist/ 静态文件
│   ├── src/config/env.ts        ★ 唯一的对外地址出口（API / 资源 / 源站）
│   ├── src/api/                 request 封装（统一响应/401 跳转）、各模块接口
│   ├── src/router/              路由与登录守卫
│   ├── src/stores/auth.ts       Pinia：令牌与用户信息
│   ├── src/views/               登录 / 主布局 / 工作台 / 工单（分类导航+预约+维修+评价投诉+聊天）/ 投诉管理 / 用户管理
│   ├── src/ui/                  自建 UI：线性图标 / 轻提示 / 确认弹窗 / 状态标签
│   ├── src/styles/theme.css     设计令牌与元件（必读）
│   ├── .env.development         开发配置（接口走代理）
│   ├── .env.production.example  生产配置模板（分域部署用）
│   ├── nginx.conf               容器内 Nginx（静态 + 反代）
│   ├── Dockerfile               独立镜像
│   └── vite.config.ts           代理目标读 VITE_PROXY_TARGET
├── deploy/
│   ├── nginx.conf               单机部署参考（静态 + 反代）
│   └── docker-compose.yml       mysql + backend + frontend 一键编排
├── .env.example                 编排环境变量模板
├── assets/                      ER 图（svg/html/png）
├── CampusFix.md                 原始需求
├── CampusFix-项目注意文档.md     设计基线（冲突以此为准）
├── CampusFix-系统设计说明书.md   系统设计（架构/接口/表结构）
└── PROGRESS.md                  本文件
```

## 五、快速启动

### 0. 运行前置条件

| 依赖 | 版本要求 | 是否必需 | 说明 |
|---|---|---|---|
| JDK | **17 及以上** | 必需 | 工程以 `--release 17` 编译，字节码 major 61，可直接在 JDK 17 下运行；高版本 JDK 亦可运行 |
| Maven | 3.6+ | 必需 | 依赖管理 |
| MySQL | 8.0+ | 必需 | 字符集 `utf8mb4`，需 `campusfix` 库建表权限 |
| Redis | 6.0+ | 可选 | 不装则自动降级为进程内令牌存储（见偏差记录） |
| Node.js | 18+ / pnpm | 前端阶段才需要 | 一期后端可独立运行 |

### 1. 初始化数据库

```bash
cd backend
mysql -uroot -p < db/schema.sql
mysql -uroot -p < db/data.sql
```

默认连接参数在 `src/main/resources/application.yml`，可用环境变量覆盖：`DB_USERNAME`、`DB_PASSWORD`。

### 2. 启动后端（:8080，一键启动）

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8080
```

> 若本机环境变量存在 `SERVER__PORT` 会覆盖端口，命令行参数优先级更高，因此显式指定 8080。

后端在 8080 端口**同时托管前端构建产物**，启动后浏览器直接打开 http://localhost:8080 即可使用完整应用，无需再单独启动前端。在 IDEA 中直接运行 `CampusFixApplication` 即为一键启动。

- 应用入口：http://localhost:8080
- 接口文档：http://localhost:8080/swagger-ui.html
- 健康检查：http://localhost:8080/actuator/health

前端静态资源按优先级加载（见 `StaticResourceConfig`）：`frontend/dist` 目录（开发时改动即时生效）→ jar 内 `classpath:/static/`（`mvn package` 时由 `maven-resources-plugin` 自动复制，`java -jar` 可独立运行）。

### 3. 登录验证

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"1000","password":"Cf@123456"}'
```

### 4. Redis（可选）

```bash
campusfix.cache.type = auto    # 默认：探测 Redis，不可用则降级
                     = redis   # 强制 Redis（生产）
                     = memory  # 强制进程内（无 Redis 的演示环境）
```

未安装 Redis 时服务仍可正常启动与使用，仅"重启后登录态保留 / 多实例共享"能力缺失，启动日志会打印 WARN。

### 5. （可选）前端热更新开发模式（:5173）

需要修改前端代码并热更新调试时，才单独启动 Vite：

```bash
cd frontend
npm install       # 首次安装，.npmrc 已指向国内镜像
npm run dev       # 启动后访问 http://localhost:5173
```

| 地址 | 是什么 |
|---|---|
| http://localhost:8080 | **完整应用**（后端 + 前端页面，一键启动入口） |
| http://localhost:5173 | 前端热更新开发模式（仅改前端时用） |
| http://localhost:8080/swagger-ui.html | 接口文档 |

Vite 已配置 `/api` 代理到 8080，前端不需要单独配后端地址。

> **日常使用无需启动前端**：后端 8080 已内置前端构建产物，直接开 http://localhost:8080 即为完整应用。

### 6. 启动日志判读（哪些是真问题）

| 日志 | 含义 | 是否需处理 |
|---|---|---|
| `Started CampusFixApplication in x seconds` | 启动成功 | — |
| `Redis 连接失败，降级为进程内令牌存储` | 本机没装 Redis，走降级 | 预期内，可忽略 |
| `Process finished with exit code 130` | **128 + SIGINT**，即手动停止（IDE 停止按钮 / Ctrl+C） | 不是崩溃 |
| `Process finished with exit code 1` + `Port 8080 was already in use` | 端口被占用 | 换端口或结束旧进程 |
| `Using generated security password: ...` | 缺 `UserDetailsService`，Boot 自动建了内存用户 | **已修复**，见下 |
| `Can not find table primary key in Class` | 关联表无 `@TableId`，MP 无法生成 `xxById` | **已修复**，见下 |

两处早期告警的修复方式（2026-10-30）：

1. `DbUserDetailsService`：提供数据库版 `UserDetailsService`，Boot 不再生成内存后门账号。
2. 关联表 `SysUserRoleMapper` / `SysRolePermissionMapper` **不再继承 `BaseMapper`**，改为显式 `@Insert/@Delete/@Select`；复合主键表本就没有单主键，继承只会带来不可用的 `xxById` 和启动告警。

### 7. 前后端分离配置与部署

前端只通过 HTTP 访问后端，所有对外地址集中在 `frontend/src/config/env.ts`（由环境变量注入）；
后端跨域与资源地址同样由环境变量控制。**不设这些变量时，行为与改造前完全一致。**

| 场景 | 前端构建 | 后端启动 |
|---|---|---|
| 本地开发 | `npm run dev`（Vite 代理） | 默认即可 |
| 同源部署 | `VITE_API_BASE_URL=/api/v1`、资源地址留空 | 默认即可 |
| 分域部署 | `VITE_API_BASE_URL=https://api.x.com/api/v1`、`VITE_ASSET_BASE_URL=https://api.x.com` | `CORS_ALLOWED_ORIGINS=https://www.x.com`、`STORAGE_PUBLIC_BASE_URL=https://api.x.com` |
| 容器编排 | `docker compose -f deploy/docker-compose.yml up -d --build` | 由编排注入 |

## 六、测试账号

初始密码统一为 **`Cf@123456`**（首次登录标记为需改密）。

| 账号 | 角色 | 姓名 | 部门 |
|---|---|---|---|
| `1000` | 系统管理员 | 系统管理员 | 后勤保障处 |
| `20001` / `20002` | 后勤管理员 | 王海涛 / 周文 | 后勤保障处 |
| `30001` / `30002` / `30003` | 维修人员 | 张建国 / 李伟 / 王强 | 水电组 / 空调组 / 网络组 |
| `2021001` ~ `2021005` | 学生 | 陈晓 / 刘洋 / 赵敏 / 孙磊 / 周婷 | — |

## 七、核心实现要点

面向 Java 17 的实现约定，新增代码请保持一致。

| 主题 | 做法 |
|---|---|
| 分层 | `controller → service → mapper`，跨模块只调 Service 接口，不直接注入他人 Mapper |
| 统一响应 | `Result{code,message,data,traceId}`；业务异常返回 HTTP 200 + 业务码，认证 401 / 权限 403 / 并发冲突 409 |
| 异常处理 | `BizException(ResultCode)` + `@RestControllerAdvice` 全局兜底，参数校验错误合并为一条中文提示 |
| 鉴权链路 | `JwtAuthenticationFilter` 解析 Bearer → `TokenStore` 取用户快照 → 写入 `SecurityContext`；接口用 `@PreAuthorize("hasAuthority('order:assign')")` |
| 令牌可撤销 | access token 的 `jti` 与用户快照存 `TokenStore`；改密/禁用/改角色时 `logoutAll(userId)` 按用户批量吊销，解决"纯 JWT 踢不掉人" |
| 参数对象 | DTO 用 `record`（Java 16+）+ `@Valid` + Jakarta 校验注解，Controller 只做绑定与转发 |
| 语言特性边界 | 仅使用 Java 17 已定稿特性：`record`、文本块、`instanceof` 模式匹配、`switch` 表达式、`Stream.toList()`；**未使用** 21 的 SequencedCollection、`getFirst()`、字符串模板等 |
| 持久层 | MyBatis-Plus 分页/乐观锁/防全表更新插件；`AuditMetaObjectHandler` 自动填充创建与更新时间；逻辑删除统一 `deleted` |
| 时间处理 | 统一 `LocalDateTime` + `Asia/Shanghai`，序列化为 `yyyy-MM-dd HH:mm:ss`（`JacksonConfig`） |
| 可观测 | 每个请求生成 `traceId` 写入 MDC 与响应头；`/actuator/health` 健康检查 |
| 存储解耦 | `FileStorageService` 接口 + `local` 实现，二期接 OSS 只加实现类，不改业务代码 |

### 工单状态机（本阶段重点）

转换表集中在 `order/statemachine/OrderStateMachine`，17 个动作 / 18 条合法边，每条边是四要素：

| 要素 | 作用 | 例子 |
|---|---|---|
| 目标状态 | 流转后落到哪 | `GRAB` → `ACCEPTED`（跳过「已分配」，抢单直达接单） |
| 允许角色 | 谁能执行 | `REVIEW_PASS` 只允许 MANAGER / ADMIN；`ASSIGN` 额外放行 SYSTEM（自动派单用） |
| 前置校验 | 业务前提 | `ACCEPT` 要求操作人就是被指派人；`CONFIRM` 要求操作人是报修人 |
| 副作用 | 额外落库字段 | `GRAB` 写 `assignee_id` / `accepted_at`；`REWORK` 清空维修人并 `rework_count+1` |

**三条纪律**：

1. 业务代码**禁止** `order.setStatus()`，只能 `stateMachine.fire(orderId, action, operator, remark, params)`；
2. 每次 `fire` 必须写 `repair_order_log` —— 时间轴、处理时长统计、责任追溯只认这张表；
3. 并发靠 `version` 乐观锁：条件更新影响行数为 0 直接返回 HTTP 409，**不引入分布式锁**。

`GET /orders/{id}` 会返回 `actions` 数组（当前登录者在该单上可做的动作），前端据此显示按钮；但真正的校验仍在 `fire` 里再做一遍 —— 前端只决定「看不看得见」。

### 预约模型（本阶段重点）

基线 A14 定的是「**报修人发起、维修工确认**」，落地顺序是：

```
ACCEPTED
  │ 维修工 POST /orders/{id}/schedules  （给 1–3 个窗口）
  ▼
OFFERED × N
  │ 报修人 PUT /schedules/{sid}/select  （选中一个，同批其余自动 CANCELLED）
  ▼
SELECTED
  │ 维修工 PUT /schedules/{sid}/confirm
  ▼
CONFIRMED  →  工单 PROCESSING，回写 plan_time
```

两条关键口径：

1. **时段是意向窗口，不是排他占用**（A15）。维修时长不定，所以 `OFFERED` 允许重叠，
   只有 `CONFIRMED` 才做「同维修工同日时段重叠」校验，冲突返回 `3003`。
2. **改约可以发生在「已接单」和「处理中」两处**。维修工发起改约时新增一条带 `[改约]` 标记的
   `OFFERED`，原时段先保留；报修人接受则新时段转 `CONFIRMED`，拒绝则 <b>整单退回抢单池</b>
   （清空维修人、上门时间，重新分配）。

### 评价与投诉口径（本阶段重点）

**评价**走「三项打分 → 加权 → 落库」，权重不写死：

```
总分 = (速度×w1 + 态度×w2 + 效果×w3) / (w1 + w2 + w3)
w1/w2/w3 读 sys_config：evaluation.speed-weight / attitude-weight / effect-weight（默认 0.34 / 0.33 / 0.33）
```

除以权重和是刻意为之：管理员把权重配成 `1:1:1` 时结果仍是平均分，不会算出 15 分制的怪数。
**总分在提交时算好落库**，不每次统计重算 —— 权重后续调整不应改变历史评价。

三条硬约束：

1. 只有**本单报修人**能评价，且工单必须「已完成」；一单一次，`evaluation.uk_order` 兜底并发重复提交；
2. 评价成功给维修工 **+1 信誉分**（上限 100），走 `UserService.adjustCredit` 写流水；
3. **匿名只对别人匿名**：本人、管理员、后勤看真名，维修工等看「匿名用户」，脱敏在后端完成，不把真名交给前端遮挡。

**投诉**用状态而非文案决定是否扣分：

| 状态 | 含义 | 是否扣分 |
|---|---|---|
| `PENDING` | 待受理 | — |
| `PROCESSING` | 处理中（已受理，待核实） | — |
| `RESOLVED` | 成立 | **扣维修工 10 分** |
| `REJECTED` | 不成立 | 不扣 |

这样「投诉成立率」与「信誉分变动」永远对得上，不会出现「意见写了成立但状态是驳回」的账实不符。
投诉率沿用基线口径：`投诉数 / (总工单数 + 10) × 100%`。

### 通知与聊天口径（2026-10-31 新增）

**通知**以 `notification` 表为唯一数据源（未读数、列表、已读都查它），WebSocket 只做加速：

- **触发点集中在状态机**：`OrderStateMachine.fire()` 成功流转后调用
  `NotificationService.onTransition(order, action, to, operator, remark, param)`，按基线 §11.4
  分发给报修人 / 维修人，**排除操作人本人**；通知失败只记 WARN，绝不影响主流程；
- **快照陷阱**：传入的是流转前的 order——REWORK / RESCHEDULE_REJECT 要通知「原维修人」
  正好用旧值；但 ASSIGN / TRANSFER 的新维修人必须从 `param.repairerId` 取
  （流转前 assigneeId 还是 null，直接取会静默丢通知，已修复并回归）；
- 打点不经过状态机，「维修人员已到达 / 开始维修」由 `RecordService` 直接发通知；
- 前端角标只按 `NOTIFY` 帧计数；聊天消息本体另发 `CHAT` 帧给聊天面板刷新气泡（一帧两用会重复计数）。

**聊天**以工单为会话单位（基线 §11.1），`chat_message` 表 + 1–500 字纯文本：

- 相关方 = 报修人 / 当前维修人 / 管理员 / （部门含下级匹配的）后勤管理员，其余一律 2002；
- 报修人 ↔ 维修人互发；管理员发送给当前对话另一方；维修人未接单时报修人发送返回 3001；
- **拉取即已读**：`GET /orders/{id}/messages` 把发给我的未读置 1；同时给对方落一条
  `CHAT` 类型通知（离线可补，在线经 WS 秒达）；
- WebSocket：`/ws?token=<accessToken>` 握手鉴权（浏览器 WS 无法带 Authorization 头），
  服务端只下行，上行仅作心跳；断线自动重连（2s→15s），连接失败不影响功能（REST 兜底）。

### 统计与设施口径（2026-10-31 新增）

**统计**（基线 §12 口径，`/stats/overview` 一次返回工作台全部数据）：

- 数据范围与工单列表同规则：ADMIN 全部、MANAGER 本部门及下级（无部门退化为仅自报）、
  REPAIRER 名下+自报、STUDENT 仅自报，在 SQL 层拼 scope；
- 完成类统计（今日/本周/本月）一律按 `finished_at` 且状态 COMPLETED；取消、驳回不计入；
- 平均处理时长 = AVG(finished_at−accepted_at)，平均维修时长 = AVG(finished_at−started_at)；
- 故障类型 TOP5 按全部工单报修量（工作台排行语义）；楼栋分布 TOP5 按已完成工单（基线口径）；
- 年 7,300 单规模下聚合全部毫秒级，不建预聚合表（基线 §12.4），也暂不加缓存。

**设施联动**（基线 §9.2，待确认清单 #9 已确认）：挂状态机成功流转后同步——
预约确认（PROCESSING）→ 设施 `REPAIRING`；验收完成（CONFIRM/AUTO_CONFIRM）→ `NORMAL`。
返修、取消、无法维修**不改动**（设施确实没修好，保持维修中更诚实）。联动失败只记日志。

**重复故障预警**（基线 §12.3）：同一楼栋 + 最近 7 天 + 同一故障类型 + 工单数 ≥5（取消/驳回不计），
每天 08:00 扫描写 `fault_alert`，`uk_day_combo` 唯一索引保证同窗口幂等；阈值/窗口在
`campusfix.stats.*` 可配；管理端工作台顶部横幅提示（NEW 状态），点「知道了」转 READ。

### 操作日志口径（2026-10-31 新增）

- `@OperLog(module, action)` 注解 + AOP 环切：记录 操作人 / 模块 / 操作 / 方法 / URI /
  参数（脱敏 + 截断 2000）/ IP（X-Forwarded-For 优先）/ 成败 / 错误信息 / 耗时；
- **成败都记**：业务异常也会落一条 status=0 的日志（error_msg 为异常消息）；
- 日志任何失败只记 WARN，绝不影响业务事务；
- 覆盖范围：工单提交与全部状态动作、预约六动作、验收/返修/延期、投诉提交与处理、
  用户管理写操作、系统参数修改。查询类接口不打注解。

### 维修异常态（挂起 / 恢复 / 无法维修）

- 挂起：仅当前维修人、仅「处理中」，**必填原因**（基线 #11）；挂起时长自动累计、不计入维修用时；
- 恢复：仅当前维修人、仅「挂起」；
- 无法维修：仅当前维修人、「处理中 / 挂起」，**必填原因**，工单转「已驳回」并通知报修人（可转投诉）；
- 前端在维修过程面板按状态显示按钮（挂起中显示橙色提示与恢复按钮），不走抽屉底部动作区。

### 操作日志页与维修工日历使用说明（2026-10-31 新增）

**操作日志页**（侧栏「业务管理 → 操作日志」，拥有 `log:list` 的账号可见）：

- 筛选：操作人账号（精确）、模块下拉（从现有日志去重生成）、结果（成功/失败）、日期区间；
- 行点击或「详情」按钮查看完整信息：操作人、接口、地址、IP、耗时、失败原因、
  请求参数原文（已脱敏的 JSON，格式化展示）；
- 日志由写接口的 `@OperLog` 注解自动产生，无需手工登记；查询接口只读。

**维修工日历**：工单详情「选择维修人员」面板中，选中某人后自动展示其未来 7 天
已确认上门时段（日期 + 时段 + 关联工单）与在办单数，指派/转派时避开冲突时间。
维修工本人也可通过 `GET /repairers/{id}/calendar` 查看自己的日历（他人查询返回 403）。

### 本次变更记录（2026-10-31 第 9 批）

| 变更 | 影响范围 |
|---|---|
| 新增 `GET /oper-logs`、`GET /oper-logs/modules`（`system/log` 包扩展） | 仅新增只读接口，`log:list` 权限码沿用 data.sql 预置值（ADMIN/MANAGER 已分配），无表结构变更 |
| 新增 `GET /repairers/{id}/calendar`（DispatchService 扩展） | 仅新增只读接口，复用 `repair_schedule` 与 `repair_order` 表，无表结构变更 |
| 新增前端「操作日志」页与路由 `/oper-logs` | 侧栏按 `log:list` 权限显隐，其余页面不受影响 |
| 指派面板增加忙闲日历展示 | 仅详情抽屉指派面板 UI 增强，指派逻辑与接口契约不变 |

### 本次变更记录（2026-10-31 第 10 批）

| 变更 | 影响范围 |
|---|---|
| 新增 `GET /stats/insight`（StatsService 扩展：维修工绩效排行 / 月度趋势 / 投诉汇总） | 仅新增只读接口；维修工判定沿用角色关联表（与指派候选同口径，修复过一次按工号误判）；无表结构变更 |
| 新增数据统计页 `/stats`（侧栏「洞察」，`order:review` / `order:assign` 可见） | 原「Soon」占位移除；学生/维修工侧栏仍显示占位且后端 403 兜底 |
| 新增消息中心页 `/notifications` | 任何登录用户可用；复用既有 `/notifications` 分页接口，无后端变更 |
| 顶栏通知铃下拉新增「查看全部消息」入口；侧栏消息中心带未读角标 | 仅 UI 增强 |

### 本次变更记录（2026-10-31 第 13 批）

| 变更 | 影响范围 |
|---|---|
| 工作台 4 张 KPI 卡与 2 张 Hero 浮动卡增加点击下钻 | 跳转 `/orders?status=...`（等待处理=待审核+待处理+已分配；进行中=已接单+处理中+挂起+待确认；完成类=已完成）；仅前端交互增强 |
| 工单列表页支持 `?status=A,B,C` 多状态下钻 | 读取 `route.query.status` 初始化筛选并 watch 联动；后端 `parseStatuses` 逗号解析早已存在（非法状态自动过滤），**无后端变更** |
| `vite.config.ts` 新增 `preview` 配置（5174 端口，同套代理） | 生产构建预览路径；对严格 CSP 环境（内嵌预览面板）更友好，实测产物零 eval |

### 卡片下钻使用说明（2026-10-31 新增）

工作台的 4 张指标卡、Hero 区 2 张浮动卡均可点击，直达工单管理并自动套用对应状态筛选：

| 卡片 | 跳转 |
|---|---|
| 工单总数 | 全部工单 |
| 等待处理 | 待审核 + 待处理 + 已分配 |
| 进行中的工单 | 已接单 + 处理中 + 挂起 + 待确认 |
| 平均处理时长 | 已完成 |
| 浮动卡「已完成 / 等待处理」 | 同上对应筛选 |

最近工单列表、快捷入口原有跳转保持不变；多状态筛选在后端做了合法性过滤（未知状态自动忽略）。

### 统计页与消息中心使用说明（2026-10-31 新增）

**数据统计页**（侧栏「洞察 → 数据统计」）：

- 谁能看：后勤管理员与系统管理员（后端 `/stats/insight` 按 `order:review` / `order:assign` 鉴权；
  后勤的数据范围为**本部门及下级的维修工与其部门工单**，管理员为全校）；
- 维修工绩效排行：完成单数、三维加权平均分（同评价口径，权重读 `sys_config`）、
  好评率（总分 ≥4 占比）、平均处理时长；无评价的维修工按「未评价」展示并排后；
- 月度趋势：近 6 个月新增（红柱）与完成（绿柱）对比；
- 投诉结构：待受理 / 处理中 / 已成立 / 不成立四态分布与投诉率（口径 `投诉数 / (总单数 + 10)`）。

**消息中心页**（侧栏「洞察 → 消息中心」，或顶栏通知铃下拉「查看全部消息」）：

- 全量通知列表，支持按类型（工单 / 聊天 / 投诉 / 系统）与「只看未读」筛选、分页；
- 点击未读项自动标记已读并跳转对应工单；「全部已读」一键清零角标。






## 八、前端设计规范

UI 基于**自建设计系统**（`frontend/src/styles/theme.css`，最初移植自同作者的 DhuStore，2026-10-30 重写升级），不引入任何组件库或图表库。
新增页面请复用下列类名，不要另起一套样式；换肤只需改变量。

### 设计令牌（改这里就能整体换肤）

| 变量 | 值 | 用途 |
|---|---|---|
| `--brand` / `--brand-d` / `--brand-l` | `#e63946` / `#c42532` / `#fdecee` | 主品牌色、按下态、浅底 |
| `--brand-2` / `--accent` / `--violet` / `--teal` | `#ff7a59` / `#4f6ef7` / `#7c5cfc` / `#0ea5a4` | 辅助色：暖橙、蓝、紫、青 |
| `--ink` / `--ink-2` / `--ink-3` | `#171a21` / `#4f5563` / `#8a90a0` | 三级文字墨色 |
| `--line` / `--line-2` / `--bg` | `#e6e8ef` / `#f0f2f7` / `#f4f5fa` | 发丝边框、次级分隔、页面底色 |
| `--r` / `--r-lg` / `--r-sm` | `14px` / `20px` / `10px` | 卡片 / 浮层 / 控件圆角 |
| `--sh-1` / `--sh-2` / `--sh-3` | 三级投影 | 卡片 / 悬停 / 浮层 |
| `--sidebar-w` / `--hdr` | `246px` / `64px` | 侧栏宽度、顶栏高度 |

> **换品牌色**：只改 `--brand*` 变量即可（例如换回蓝色 `#185FA5`），其余样式自动跟随。

### 常用元件

| 类名 | 说明 |
|---|---|
| `.card` / `.panel` | 白底 + 14px 圆角 + 一级投影；`.panel-hd` / `.panel-bd` 为面板头与内容 |
| `.btn` `.btn-primary` `.btn-ghost` `.btn-danger` `.btn-sm` | 38px 高、10px 圆角；`btn-sm` 32px；主按钮为品牌红渐变 |
| `.field` | 输入框 / 下拉，40px 高；聚焦时品牌红光晕 |
| `.tag` `.tag-brand` `.tag-ok` `.tag-warn` `.tag-crit` `.tag-ink` | 22px 高、11.5px 字号的状态标签 |
| `.tablewrap` `.tbl` `.tbl-hd` | 数据表格（表头 `#f8f9fc`、13.5px 行高、悬停 `#fafbfd`） |
| `.pager` `.pg` | 分页按钮 |
| `.kpis` `.kpi` | KPI 卡片，数值 28px/800、`letter-spacing:-.9px` |
| `.side` `.navitem` `.topbar` `.content` | 应用外壳（246px 深色渐变侧栏、64px 毛玻璃顶栏） |
| `.empty` `.spin` `.toast` `.mask` `.modal` | 空态、加载、轻提示、弹窗 |
| `<Icon name="..." />` | 24×24 线性图标，1.7 描边，共 38 个，颜色继承 `currentColor` |

### 交互约定

- 轻提示：`toastOk(msg)` / `toastErr(msg)`（`src/ui/toast.ts`）
- 二次确认：`await confirmDialog({ title, message, danger })`（`src/ui/dialog.ts`）
- 不再使用 `ElMessage` / `ElMessageBox`；Element Plus 已从依赖中移除

### 界面改版记录

界面改版共三批（2026-10-30 两批、2026-10-31 一批），**后端接口与数据契约均未变化**，`npm run build` 通过。

#### 改版一：整体视觉升级

目标：解决页面只有文字、层级单薄的问题，在不引入组件库 / 图表库的前提下重建视觉体系。

| 范围 | 改动 |
|---|---|
| 设计系统 `theme.css` | 重写令牌与基础元件：品牌红 + 冷调墨色、深色渐变侧栏、毛玻璃顶栏、14–24px 圆角、分层阴影；补齐按钮 / 表单 / 标签 / 表格 / 分页 / 弹窗 / Toast / 空态 / 加载态 / 登录页样式，保留原类名以兼容旧页面 |
| 应用外壳 `LayoutView` | 分组导航（概览 / 业务管理 / 洞察）、顶栏面包屑、全局搜索（回车跳转 `/orders?keyword=…`）、通知铃、用户下拉菜单、侧栏用户卡片与退出确认 |
| 工作台 `DashboardView` | Hero 问候区（日期、待处理数、内联 SVG 插画、悬浮数据卡）、4 张 KPI 卡、近 7 日趋势柱状图、状态分布环形图（`conic-gradient`）、分类 TOP5、最近 8 条工单；统计来自真实接口（最多取 100 条样本，页面已标注「最近 100 条」） |
| 工单管理 `OrderListView` | 页面标题区、状态概览条、工具栏与表格视觉升级；支持 `?keyword=` 搜索联动、`?id=` 直达详情抽屉 |
| 用户管理 `UserListView` | 本页概览条、角色配色头像、信誉分进度条、状态开关、9 列信息重排 |
| 登录页 `LoginView` | 输入框图标、密码显隐切换、按角色配色的测试账号卡片、欢迎语与页脚 |
| 详情 / 弹窗 | 详情抽屉增加状态横幅（优先级 / 紧急度 / 返工统计）与键值卡片；新建工单弹窗紧急度图标化 |
| 图标与启动页 | `Icon.vue` 新增 bell、plus、eye、pin、wrench、sparkles、building、phone 等；`index.html` 增加深色启动闪屏，Vue 挂载后自动替换 |

设计约束：

- 不新增运行时依赖（无 Element Plus / ECharts），图表为手写 SVG / CSS；
- 设计令牌集中在 `src/styles/theme.css`，换品牌色只需改 `--brand*`；
- 功能与后端契约保持原样，仅做展示层增强。

#### 改版二：工单管理「故障分类优先」

背景：原页面直接进入密集表格，分类仅作为表格中的一列，不便于按类别浏览。

改动：

- 页面顶部新增「故障分类」面板，展示「全部工单 + 9 个启用分类」共 10 张可点击卡片；
- 每张卡片显示分类专属图标、名称、该分类工单数（按当前页签「我的工单 / 抢单池」从后端逐类读取 `total`，不是本页样本）；
- 点击卡片按 `categoryId` 筛选列表；选中态显示品牌红描边与「当前：xx / 查看全部」；再次点击同一卡片或「查看全部」取消筛选；
- 列表空态增加分类场景文案；新增工单、处理工单、刷新后分类计数同步更新；
- 响应式：桌面 5 列 / 1024px 4 列 / 平板 3 列 / 手机 2 列；手机端选择分类后自动滚动到列表；
- 表格「类型」列图标改为按分类配色，与顶部卡片一致。

接口说明：

- 复用现有 `GET /api/v1/orders?categoryId={id}&page=1&size=1` 获取各类 `total`，**无需新增后端接口**；
- `GET /api/v1/categories` 获取分类列表（`id / name / code / deptName`）；
- 每次进入页面或切换页签并发 10 个轻量查询（`size=1`），不随关键词 / 状态筛选重复请求。

涉及文件：`frontend/src/views/OrderListView.vue`、`frontend/src/ui/Icon.vue`（新增 droplet / wifi / snowflake / door / lightbulb / sofa）。

#### 改版三：客户端工作台轻量化（2026-10-31）

背景：学生与维修人员原先和管理员共用同一套工作台，Hero 插画、KPI、趋势图、分类排行、平台建设进度等管理端内容与大量图标让客户端显得严肃、信息过载。

改动：

- 工作台按角色分流：`STUDENT` 渲染 `StudentHome`，`REPAIRER` 渲染 `RepairerHome`，`MANAGER / ADMIN` 保留原工作台；
- 学生工作台：浅暖色 Hero + 一句进度提示 + 单一「提交报修」主按钮；焦点工单卡直达当前进度；待处理 / 处理中 / 待确认 / 已完成四张数字卡；最近报修与三步报修流程；无装饰插画与图表；
- 维修人员工作台：浅蓝 Hero + 「我的任务 / 抢单池」两个带数量的主按钮；今日已完成、本周完成、待学生确认；待接单 / 进行中 / 待验收 / 可抢单四张数字卡；按优先级排好的任务列表（含「去接单 / 去处理 / 查看」动作）与抢单池预览；
- 客户端应用外壳简化：侧栏仅保留首页 / 我的报修（我的任务）/ 消息中心，隐藏面包屑、管理分组、数据统计与「Soon」占位、接口文档入口；搜索占位与页头标题按角色改写；
- 工单列表支持 `?tab=pool` 深链接，维修人员可直接从工作台进入抢单池；页签切换同步 URL；
- 全程复用 `getStatsOverview`、`pageOrders`、`pagePool`，**后端接口与数据契约未变化**。

涉及文件：`frontend/src/views/dashboards/StudentHome.vue`、`frontend/src/views/dashboards/RepairerHome.vue`（新增），`frontend/src/views/DashboardView.vue`、`frontend/src/views/LayoutView.vue`、`frontend/src/views/OrderListView.vue`。

#### 验证记录

- `npm run build` 通过（165 个模块，约 1.7s）；
- 已检查 1440×1000、1366×768、1024×768、390×844 四档宽度截图；
- 顶栏搜索「门锁」→ `/orders?keyword=门锁` 并正确筛选出结果；
- 点击「水电故障」→ 列表由 14 条变为 3 条；点击 0 条的「公共设施」→ 显示「该分类下暂无工单」空态；
- 详情抽屉、`?id=` 直达、新建工单弹窗、用户管理页面回归正常。
- 客户端改版另检查 1440×1000、1024×768、390×844 三档：学生「提交报修」弹窗、维修人员「我的任务 / 抢单池」跳转与 `?tab=pool` 页签均正常；管理员工作台无回归。

## 九、文档索引

| 文档 | 用途 |
|---|---|
| `CampusFix.md` | 原始需求 |
| `CampusFix-项目注意文档.md` | 设计基线：24 条决策、状态机、信誉值、统计口径 |
| `CampusFix-系统设计说明书.md` | 架构、模块、接口、25 张表 DDL、部署、非功能、风险 |
| `assets/er-diagram.svg` | 数据库 ER 图（矢量） |

## 十、与文档的偏差记录

| 日期 | 偏差 | 原因 | 影响 |
|---|---|---|---|
| 2026-10-30 | 新增 `TokenStore` 抽象：Redis 不可用时自动降级为进程内存储 | 本机无 Redis 且无法联网下载二进制，需保证"本地跑通" | 仅影响重启保留登录态与多实例；生产设 `cache.type=redis` 即与设计一致 |
| 2026-10-30 | 前端未采用 Element Plus / ECharts：UI 使用自建设计系统，工作台图表为手写 SVG / CSS | 统一品牌视觉、减少依赖；当前图表只需基础可视化 | 功能一致；组件与图表能力以 `theme.css`、`src/ui/` 为准，后端接口不变 |
| 2026-10-30 | 状态文案与配色未抽到 `constants/orderStatus.ts`，实际集中在 `src/ui/OrderStatusTag.vue` | 目前只有工作台、工单列表、详情抽屉三处用到，抽文件的收益低于多维护一份映射 | 设计说明书 §8「前端规范」该条与代码不符；后续统计页或小程序若要复用，需先把映射迁到 `constants/` |
| 2026-10-31 | 后端 8080 托管前端构建产物（`StaticResourceConfig`），实现一键启动 | 解决「IDEA 点击一次运行即完整可用、无需单独起 Vite」的诉求，彻底绕开 IDEA 拉起 Vite 的不稳定环节 | 本地开发默认访问 8080；前端 5173（`npm run dev`）降级为可选的热更新模式，二者并存不冲突 |

## 十二、一键启动改造记录（2026-10-31）

为解决「在 IDEA 中点击一次运行即可在浏览器直接访问完整应用」的诉求，后端在 8080 端口直接托管前端构建产物：

**改动清单**

| 文件 | 改动 |
|---|---|
| `backend/.../config/StaticResourceConfig.java` | 新增：前端 `dist` 绝对路径查找 + `/` 静态托管 + history 路由 SPA 回退 |
| `backend/.../web/HomeController.java` | 根路径 `/` 由 `redirect:/swagger-ui.html` 改为 `forward:/index.html` |
| `backend/.../config/SecurityConfig.java` | 鉴权边界改为「`/api/v1/auth/login\|refresh` 放行 → `/api/v1/**` 鉴权 → 其余放行」 |
| `backend/pom.xml` | `maven-resources-plugin` 在 process-resources 阶段复制 `frontend/dist` 进 jar 的 `static/`（属性 `frontend.dist.skip` 可控） |

**验证结果**：`mvn spring-boot:run` 与 `java -jar` 两条路径下，`/`、`/assets/*.js`、`/student`、`/admin` 均返回 200；登录接口返回真实 JWT；未授权 `/api/v1/**` 返回 401。

**关键坑**：根路径曾一直 302 到 swagger，先后误判为 springdoc welcome 机制，最终靠「Ambiguous mapping」报错定位到项目里早已存在的 `HomeController#home()` 重定向；此外 `/assets/**` 静态映射存在路径少一层 `assets/` 的拼接错误，导致 JS/CSS 返回 5000、页面卡在启动加载页——已一并修复。

## 十一、下一步

1. **数据增强**：统计页导出（CSV/Excel）、楼栋分布下钻（点击楼栋看工单列表）
2. **微信小程序端**（二期，文档中已预留位置）

> 第 1 阶段功能已全部落地：报修 → 审核 → 派单/抢单 → 预约 → 维修（含挂起/无法维修）→ 确认/返修 → 完成 → 评价/投诉 → 消息触达 → 数据洞察（工作台 + 独立统计页）→ 操作可审计 → 派单有日历辅助 → 消息有中心页。
