# M1 腾讯云首次受限部署验收

日期：2026-10-28，北京时间。**实际部署 PASS（仅受限试运行）；公网业务、正式域名HTTPS、真实微信登录与发布未完成。** 服务器150.158.179.128，Ubuntu24.04.4，4核/约3.7GiB可用内存、40GB SSD、上海地域。基线远程M1提交28d028e，本轮增加明确关闭微信交换的启动配置和部署/验收脚本；没有引入M2。

## 实际执行与证据

| 项目 | 状态 | 真实结果 |
|---|---|---|
| SSH身份与权限 | PASS | 用户补充确认完整ED25519指纹 `SHA256:V/w7mWgKFEzvC8RHvs0lYtXBXVVnYU/L8fsrFWMed8E`，固定known_hosts、严格校验、现有私钥认证，ubuntu免密sudo可用。原提示私钥路径缺一个分隔符，实际使用用户目录下`.ssh/CampusHub_Lighthouse_SH.pem`。未重建密钥 |
| 系统安全更新 | PASS | apt update/upgrade成功；MaxAuthTries3、LoginGraceTime30、sshd配置检查通过，保留已有认证/恢复方式。原密码认证已为no，没有临时开启。更新后实际重启并再次公钥连接成功；无待重启标记 |
| 防火墙/监听 | PASS | UFW active，仅允许22/tcp（IPv4/IPv6）。MySQL3306/33060、Java8080、Nginx8443均回环绑定；80/443未开放。未修改腾讯云控制台防火墙，未购买额外资源 |
| Java | PASS | OpenJDK21.0.12.1，Ubuntu包build21.0.12.1+1-1-24.04.4-Ubuntu |
| MySQL | PASS | MySQL Community Server8.4.13，官方mysql-apt-config0.8.40-1与Oracle签名APT源安装；独立campushub_prod，require_secure_transport=ON，256MB缓冲池、40连接上限，UTC数据库时间 |
| Nginx | PASS | Ubuntu nginx1.24.0；私有CA签发localhost证书，TLS1.2/1.3，仅127.0.0.1:8443。不是公网受信域名证书 |
| Flyway | PASS | 独立migrator经SSH隧道与MySQL TLS迁移，V1/V2/V3/V4/V8全部success，validate通过；没有baseline/repair/clean，没有M2 V5–V7。应用未持有迁移凭据 |
| 后端/Vue | PASS | 本地Java打包、Vue生产build成功后传输；独立campushub用户，systemd启动生产profile。release路径`/srv/campushub/releases/m1-restricted-20261028`，current指向该目录；后端health=UP、数据库身份核验成功，Vue内部HTTPS HTTP200 |
| 生产保护改动 | PASS | 显式CH_WECHAT_ENABLED=false时必须为空AppSecret，现有交换客户端无secret返回503；启用时仍强制有效secret。24项后端单元PASS；新增用例先RED（6项策略测试1失败）再GREEN。未关闭Session、CSRF、Secure/HttpOnly/Strict Cookie或数据库VERIFY_IDENTITY |
| 真实HTTP/会话/业务 | PASS | 独立Python客户端验证CA和主机名，经真实Nginx→Spring→MySQL：匿名401、管理员登录/me、Cookie属性、管理员访问学生接口403、缺CSRF403、创建/编辑/发布活动1、其他组织者访问403、学生密码管理登录401、注销后401 |
| 报名与取消服务事务 | PASS | 独立受信任Java验收进程调用实际Spring代理RegistrationService，使用真实数据库中限定学生夹具、真实MySQL提交；报名CONFIRMED→取消CANCELLED复用同一记录，学生管理服务访问403。最终1活动、1报名、3账号，取消后人数0 |
| 学生HTTP/微信登录报名 | BLOCKED | 未提供AppSecret、无正式API域名，未创建假微信上游或伪造登录Session。上行服务层事务验证不是学生HTTP/微信/真机闭环 |
| 应用最小权限 | PASS | 应用无DDL、无系统表读取，实际CREATE TABLE和读取mysql.user被拒绝；身份/活动/报名操作使用既有M1最小权限模板。独立app/migrator/operator/backup凭据仅服务器root私密文件 |
| 加密备份与恢复 | PASS | age加密全库快照、SHA256校验；独立新建空恢复schema和仅限该schema的账号恢复，活动/报名/用户/迁移行数一致，人数/容量异常0。定时任务每日03:15UTC（北京时间11:15），开机启用；3份加密快照已下载本地并再次校验 |
| 服务/整机重启恢复 | PASS | systemctl重启后healthUP；系统安全更新后整机重启，SSH重新连接，MySQL/Nginx/Spring与备份timer自启动，内部TLS代理再验通过 |
| 日志 | PASS | 脱敏method/path/status/latency访问日志独立目录；Nginx日志轮转14天，journald200MB/14天限制，未启用请求体/凭据记录 |
| 资源 | PASS | 重启后约1162MiB已用/3723MiB总内存、约2560MiB available；swap约1987MiB、使用0；系统盘已用6.5GB、可用约32GB（18%）。不是负载/容量压测 |
| 本机访问云端 | PASS | SSH隧道127.0.0.1:18443→云端127.0.0.1:8443；curl显式CA校验后HTTP200。WindowsSchannel对私有CA无撤销服务首次拒绝，使用revoke-best-effort保留证书链/主机名验证后通过，没有使用-k |
| 正式微信审核/发布 | NOT_RUN | 备案仍办理中，无审核/发布操作 |

## 本地访问方法

在自己的PowerShell保持下列SSH隧道窗口运行（如果18443已有本轮隧道，直接使用即可）：

```powershell
ssh -i "$env:USERPROFILE\.ssh\CampusHub_Lighthouse_SH.pem" -o StrictHostKeyChecking=yes -N -L 127.0.0.1:18443:127.0.0.1:8443 ubuntu@150.158.179.128
```

后台地址：`https://localhost:18443`。它使用内部CA；普通浏览器默认不会信任。可在独立测试浏览器环境中手动信任公开CA证书后访问，不要关闭证书验证或把这个地址称为正式生产域名。本轮没有自动修改Windows信任设置。

公开CA文件已保存在用户`.ssh/CampusHub_restricted_CA.crt`；管理员/组织者验收账号和随机密码保存在用户`.ssh/CampusHub_restricted_access.json`，仅当前用户和SYSTEM可读，**不要提交或转发该文件**。账号均为限定的受限验收夹具；学生账号不允许通过管理密码接口登录。可用以下命令验证页面，避免忽略TLS：

```powershell
curl.exe --noproxy "*" --ssl-revoke-best-effort --cacert "$env:USERPROFILE\.ssh\CampusHub_restricted_CA.crt" https://localhost:18443/
```

## 配置、备份与恢复入口

- root私密配置：`/etc/campushub/application.env`、独立角色`.cnf`及`acceptance-accounts.json`。应用systemd只读取应用环境；迁移/备份账号不传入业务进程。
- TLS：`/etc/campushub/tls/`与`/etc/mysql/campushub-tls/`；CA及服务私钥root/mysql限定。MySQL应用连接主机localhost、3306、campushub_prod并使用VERIFY_IDENTITY。
- 备份：`/var/backups/campushub/*.sql.gz.age`与纯摘要sidecar；`campushub-backup.timer`与service。恢复脚本拒绝非空目标，独立scratch schema不接入业务；失败演练schema保留。
- 恢复私钥另已安全导出到用户`.ssh/CampusHub_backup_recovery.key`，同样仅当前用户和SYSTEM可读。服务器root仍保留一份用于本次恢复演练；这份本地副本应另做离线保护，正式运行前制定异机保留/密钥轮换策略。未购买异地备份服务。
- 本地加密快照：本worktree忽略目录`.local/cloud-deploy/backups/`。仅加密数据和摘要，没有明文dump。完整加密恢复是在同一云主机的独立空库验证，尚非异机/灾难恢复或量化RPO/RTO保证。
- 服务器执行日志：`/var/log/campushub-deploy/`；HTTP结果`http-acceptance.json`、服务层结果`registration-service.log`、恢复结果`restore-output.txt`。本地私有部署输出`.local/cloud-deploy/`，不公开原始日志。

## 失败、修复与限制

1. 首次输入指纹省略开头字符：在连接前停止，由用户再次独立确认完整值后才继续。没有接受未知密钥。
2. SSH socket运行目录缺失导致首次初始化止于sshd检查；创建标准/run/sshd后通过。初始化stdin的CRLF尾空行曾导致脚本最终退出127，但实际安装版本与源逐项复核通过；不将该退出码写为完整脚本首次成功。
3. root安全umask导致新建/srv/campushub父目录700，systemd报CHDIR、Nginx500；仅修正本轮新目录为755后恢复，脚本同步修复。
4. 首轮HTTP活动夹具含微秒，被业务整秒日期规则正常拒绝；修正验收输入后创建/编辑/发布成功，未放宽业务规则。
5. 首轮最小恢复账号不能执行dump内LOCK TABLES，在创建第一张表后失败；加入skip-add-locks，创建独立新空恢复库重新演练成功。没有扩大生产应用权限，没有自动删除或复用非空恢复库。
6. 独立日志路径避免与发行版nginx通配轮转重复；nginx -t与logrotate debug无重复报错。

## 后续正式上线门槛

提供备案完成的正式API域名及DNS/证书控制权；配置公众信任HTTPS与微信合法域名；将AppSecret直接置入服务器私密环境并显式启用微信交换；完成真实微信/真机学生HTTP闭环和正式隐私资料审核。届时再审核公网80/443开放、管理网络限制、备份保留与监控。当前仅受限试运行，禁止据此提交审核或宣布上线。

软件源参考：[Oracle官方APT仓库](https://dev.mysql.com/downloads/repo/apt/)、[官方安装指南](https://dev.mysql.com/doc/mysql-apt-repo-quick-guide/en/)。本次没有推送部署改动、合并main或改动v1.0.0/M2，也没有操作本机MySQL80/84。
