# M1 正式双域名与 HTTPS 接入准备

2026-10-28。本轮只读检查云端并准备仓库材料，未注册域名、申请证书、改DNS、修改服务器配置、重载服务或开放公网。现有受限站点、内部CA、SSH隧道保留。尚无选定的真实主域名，下文变量和模板均不能作为上线配置直接使用。

## 现状核对（PASS）

- 云端Spring/Nginx/MySQL active，health UP，内部CA验证的HTTPS API代理正常。后端prod、localhost:3306且数据库VERIFY_IDENTITY，微信交换显式关闭。
- Nginx现有server_name=localhost，监听127.0.0.1:8443，TLS1.2/1.3，内部证书；HTTP代理到回环8080，转发头被清空。发行版http层仍列有旧TLS协议，但当前站点已覆盖为TLS1.2/1.3；未来新增默认443站点也必须显式限制协议。
- 防火墙仅允许22；3306/33060/8080/8443均回环，无80/443监听。私密application.env、TLS服务私钥与CA私钥均600/root。未读取或公开这些私钥的内容。
- Session空闲30分钟、cookie-only、Secure/HttpOnly/SameSite=Strict；CSRF token通过csrf接口获取，登录后重取，注销失效。Vue使用同源`/api/v1`和credentials=same-origin，不需切换成跨域请求。未重跑历史全套业务验收。

## 域名与身份边界

| 入口 | 未来目标 | 配置及权限 |
|---|---|---|
| 小程序API | `https://api.<主域名>/api/v1` | 小程序构建的API_ORIGIN；微信request合法域名填写`https://api.<主域名>`，不带业务路径 |
| 管理后台 | `https://admin.<主域名>/` | Vue静态文件；浏览器仍请求`https://admin.<主域名>/api/v1`，由该主机Nginx代理相同Spring服务 |
| 管理端访问范围 | 受信运维网络/VPN/固定出口 | 持续限制admin整个虚拟主机，不因有正式域名就取消访问限制；若没有稳定CIDR，继续通过现有SSH隧道管理 |
| 现有内部入口 | `https://localhost:18443`经SSH转8443 | 保持站点及私有证书，作为部署验证/恢复入口，与新公网虚拟主机并存 |

采用两个独立host-only Session Cookie，不设置Domain=.主域名、不使用proxy_cookie_domain、不共享Cookie。admin同源API和api小程序会话各自认证/获取CSRF；不能把一个主机的Cookie或CSRF带到另一主机复用。两个子域虽然通常同站点，仍是不同origin；保留CSRF防护，不把SameSite当成CSRF替代。

**CORS无需新增**：小程序原生wx.request不使用浏览器CORS，Vue留在admin同源代理。保持后端默认无跨域开放，不添加`Access-Control-Allow-Origin: *`或跨域凭据许可。若将来坚持让管理浏览器直接调用api，须单独设计精确origin/credentials/preflight和CSRF流程，不在本轮更改。

## Nginx和应用的后续改动清单

准备样例：[public-hosts.nginx.example](../infra/production/public-hosts.nginx.example)。包含未替换的`__BASE_DOMAIN__`、`__ADMIN_CIDR__`；未安装、未激活。以现有campushub-proxy.conf为代理片段，保留后端loopback和forward-headers-strategy=none；不需信任任意X-Forwarded头。

- api仅提供/api/v1，关闭管理密码登录、manage/admin/organizer路径、demo、actuator和文档；微信登录有独立限流。后端RBAC仍是最终权限边界。
- admin整个站点保留网络允许列表，提供静态资源及同源API，禁demo和微信登录入口。管理账号由受信任运维流程管理。
- 443默认虚拟主机拒绝未知SNI；API与admin均只用TLS1.2/1.3。可以使用一张覆盖两个真实名称的SAN证书，cert-name固定campushub-public；无需购买通配符或付费证书。
- 替换server_name、证书路径、管理网络CIDR后，才做nginx -t及受限SNI验收。先验证登录、Cookie、CSRF、资源归属与小程序真实请求，再授权最终公网接入。样例没有80签发站点或HSTS预加载：首次签发走下节单独流程；HTTPS稳定后再评估HSTS，不能提前对整个主域名启用includeSubDomains/preload。
- 独立api/admin日志放在/var/log/campushub，现有nginx-*.log轮转规则不能匹配这些新文件名；启用时应新增精确api-access.log/admin-access.log规则或统一命名，避免与发行版nginx通配轮转重复。
- 后端继续使用Secure/HttpOnly/Strict、cookie-only和30分钟Session；数据库仍localhost和独立内部CA，不把MySQL连接改成API域名，也不迁出私密文件。
- 本轮不启用微信交换。待AppSecret通过服务器私密文件提供后再设CH_WECHAT_ENABLED=true，并真实验证官方交换。小程序届时使用正式AppID及真实api地址重新生产构建，urlCheck=true；不使用build:upload空API包充当可运行正式版。

## 证书签发、续期和监控计划（尚未执行）

建议在网站/域名备案与公网授权均满足后采用Certbot webroot HTTP-01：临时准备两个真实server_name的80入口，只让`/.well-known/acme-challenge/`读专用webroot，其他路径在证书签发成功后跳转HTTPS；勿先启用引用不存在证书的443配置。挑战路径不放配置、备份或应用文件。

计划命令（**变量必须先由真实域名、邮箱与webroot赋值；本轮未运行**）：

```sh
certbot certonly --webroot -w "$ACME_WEBROOT" --cert-name campushub-public \
  -d "$API_DOMAIN" -d "$ADMIN_DOMAIN" --email "$ACME_EMAIL" --agree-tos
certbot renew --dry-run
```

Ubuntu24.04届时选择一种受支持安装方式并确认定时器实际运行，避免apt/snap并装。续期deploy-hook只在成功更新证书后执行`nginx -t && systemctl reload nginx`，不停止后端、不重启MySQL；失败保留当前有效证书/配置并报警。HTTP-01续期依赖两个名称持续正确解析且80挑战可达；80不能届时随意关闭。

如果不希望开放80，可在拥有真实域名控制权后选DNS-01自动化，但须先确认DNS服务商及受支持插件，用最小权限DNS令牌和600私密文件。手工TXT挑战自身不满足无人值守续期。证书DNS验证不等于网站备案，也不授权网站开放。不要在本轮创建DNS令牌或调用CA。

监控计划：每日检查磁盘证书`openssl x509 -checkend 2592000 -noout -in <证书路径>`，并另查线上两个主机实际呈现的证书链、主机名和到期日（防止续期后忘reload）。30天预警，14天升级处理，7天紧急；同步监控certbot timer/service失败、DNS挑战失败、磁盘空间和主机时间。通知渠道/收件人未提供，当前尚未建立到期通知，不能把只有本地日志称为已通知。

## 用户准备及备案后的执行门槛

1. 自行选择并注册主域名，完成注册商要求的账号与域名实名认证，确保域名所有者与备案主体资料符合平台要求；无需把身份证资料发送到聊天或仓库。
2. 按腾讯云实际流程确认这台上海Lighthouse的备案资格与域名/网站接入备案。**目前的小程序备案审核中，不据此认定网站域名备案已完成**；用腾讯云和微信后台的实际审核结果确认各项状态。
3. 确定api、admin名称和DNS控制方，规划A记录到服务器；只有在IPv6路由/防火墙/HTTPS都验证后才发布AAAA，避免客户端走到不可达地址。私有ACME验证可另行安排，DNS解析或证书签发本身不代表获准公开业务。
4. 备案完成、证书和运维限制准备好并另获接入授权后，才能调整腾讯云防火墙及UFW的80/443、加载公网站点、开展真实公网/真机业务验证；3306/33060/8080仍不得公开。
5. 配置微信request合法域名及隐私说明，安全提供AppSecret，重建真实API版本，完成官方微信登录/学生报名取消闭环。代码上传、微信审核与正式发布仍分别按实际授权执行。

参考：[腾讯云域名实名认证](https://cloud.tencent.cn/document/product/242/6707)、[腾讯云备案限制](https://cloud.tencent.cn/document/product/243/18911)、[Certbot续期文档](https://eff-certbot.readthedocs.io/en/stable/using.html#renewing-certificates)、[微信官方网络要求说明](https://pay.wechatpay.cn/doc/v3/partner/4012088031)。最终资料及可用插件以实际接入时平台要求为准。

## 本轮验证边界

现有云端健康/内部TLS/防火墙/监听与私密文件权限：PASS；样例内容与现有客户端/认证契约的人工核对：PASS。真实域名、DNS、ACME签发/续期、公众证书、双域名Cookie及微信真机验证：NOT_RUN/BLOCKED。模板保留不可用占位符且没有真实证书，未把nginx语法或公网验收伪报PASS。没有改变现有部署。
