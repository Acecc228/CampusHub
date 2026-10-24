# 数据库账号与 M1.3 最小权限

实例固定为 MySQL84CampusHub，TCP `127.0.0.1:3307`。MySQL80 不在变更范围。凭据仍位于受限的仓库外目录；本文不保存密码。

2026-10-20 用户补充明确授权：仅给两个应用账号各自的 `activity_registration` 表增加 SELECT、INSERT、UPDATE。该许可不适用于其他表或未来阶段。

| 账号 / Host | 库 | 既有权限（保持不变） | M1.3 新增 |
|---|---|---|---|
| campushub_app@127.0.0.1 | campushub | category、user、role、user_role SELECT；activity SELECT/INSERT/UPDATE | activity_registration SELECT/INSERT/UPDATE |
| campushub_test_app@127.0.0.1 | campushub_test | category、user、user_role、activity SELECT/INSERT/UPDATE/DELETE；role SELECT | activity_registration SELECT/INSERT/UPDATE |
| campushub_migrator@127.0.0.1 | campushub | 既有库级 SELECT/INSERT/UPDATE/DELETE/CREATE/REFERENCES/INDEX/ALTER | 无 |
| campushub_test_migrator@127.0.0.1 | campushub_test | 同上，仅测试库 | 无 |

表格中的 category/user/role/user_role 分别指 activity_category/app_user/app_role/app_user_role。开发 app 无 DELETE，测试 app 对新报名表同样无 DELETE；应用无 DDL、GRANT OPTION、全库或跨库权限。更新人数使用 activity 已有 UPDATE，无需新增授权。

## 操作与核验

- `Invoke-BackendMigration.ps1 -Database <库> -Operation Migrate -Apply` 使用对应独立迁移账号，仅运行增量 V4，业务进程不接收迁移凭据。
- `Grant-RegistrationAccess.ps1` 默认预览。`-Apply` 前验证实例、账号及 Host、两库完整既有 SHOW GRANTS、V4 成功记录、新表列及 InnoDB；两边前置条件都通过才执行固定两条 GRANT。
- 授权脚本严格比对 SQL 文本及授权后的全部 SHOW GRANTS，拒绝额外权限，不自动撤权、改账号或修复配置。前后快照见 [m1.3-grants.json](verification/m1.3-grants.json)。
- `Test-BackendDatabase.ps1 -Apply` 对两个 app 验证 SELECT 成功，并要求报名表 DELETE、ALTER、跨库 SELECT 返回真实 1142/1044。DELETE 使用 WHERE 1=0；ALTER 使用相同引擎，正常预期均在权限检查被拒绝。未用拒绝 DROP 来替代报名表权限核验。
- 真实测试库 HTTP/事务用例验证 INSERT/UPDATE 可以完成报名与取消；开发库不写业务数据。测试库另直接检验唯一约束、CHECK、外键及事务回滚。

M1.3 保留随机标识测试夹具，避免为了清理而给报名表 DELETE。既有验收 probe、演示账号和历史迁移不变。未来如需清理本阶段夹具，须另行明确范围并授权，不在本阶段自动执行。
