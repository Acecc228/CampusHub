"""Activate a new restricted release after migration; root, --apply, no public listeners."""
import hashlib,json,os,pathlib,secrets,subprocess,sys
if '--apply' not in sys.argv:
    print('PREVIEW: activate restricted M1 release, grant scoped permissions, operator accounts, TLS proxy and backup timer');sys.exit(0)
if os.geteuid()!=0:raise SystemExit('Root required')
os.umask(0o077)
stage=pathlib.Path('/home/ubuntu/campushub-deploy')
def run(cmd,data=None):
    p=subprocess.run(cmd,input=data,text=True,capture_output=True)
    if p.returncode:raise SystemExit('Activation failed: '+cmd[0]+'; raw output suppressed')
    return p.stdout.strip()
def write(p,s,mode=0o600):
    p=pathlib.Path(p);p.write_text(s,encoding='utf-8');p.chmod(mode)
release=pathlib.Path('/srv/campushub/releases/m1-restricted-20261028')
if release.exists():raise SystemExit('Release directory already exists; do not overwrite')
release.mkdir(mode=0o755)
run(['tar','-xzf',str(stage/'release.tgz'),'-C',str(release)])
run(['chmod','-R','u=rwX,go=rX',str(release)])
run(['mysql','--protocol=SOCKET'],(stage/'grants.sql.example').read_text())
accounts={}
sql='START TRANSACTION;\n'
for name,role in [('restricted_admin','ADMIN'),('restricted_organizer','ORGANIZER'),('restricted_student','STUDENT')]:
    password=secrets.token_hex(24);salt=secrets.token_bytes(16)
    digest=(salt+hashlib.pbkdf2_hmac('sha256',password.encode(),salt,600000,32)).hex()
    sql+=f"INSERT INTO campushub_prod.app_user(login_name,display_name,password_hash,enabled,demo_account) VALUES('{name}','Restricted acceptance {role}','{digest}',1,0);\n"
    sql+=f"INSERT INTO campushub_prod.app_user_role(user_id,role_id) SELECT LAST_INSERT_ID(),id FROM campushub_prod.app_role WHERE code='{role}';\n"
    accounts[role]={'login':name,'password':password}
sql+='COMMIT;'
run(['mysql','--protocol=SOCKET'],sql)
write('/etc/campushub/acceptance-accounts.json',json.dumps(accounts))
pathlib.Path('/var/log/campushub').mkdir(mode=0o750,exist_ok=True)
os.symlink(release,'/srv/campushub/current')
run(['cp',str(stage/'campushub.service'),'/etc/systemd/system/campushub.service'])
write('/etc/nginx/sites-available/campushub-restricted','''log_format campushub '$time_iso8601 $request_method $uri $status $request_time';
limit_req_zone $binary_remote_addr zone=restricted:10m rate=10r/s;
server {
listen 127.0.0.1:8443 ssl;
server_name localhost;
ssl_certificate /etc/campushub/tls/server.crt;
ssl_certificate_key /etc/campushub/tls/server.key;
ssl_protocols TLSv1.2 TLSv1.3;
server_tokens off;
root /srv/campushub/current/admin-web;
client_max_body_size 32k;
access_log /var/log/campushub/nginx-access.log campushub;
add_header X-Content-Type-Options nosniff always;
add_header Referrer-Policy no-referrer always;
add_header X-Frame-Options DENY always;
allow 127.0.0.1; deny all;
location = /api/v1/auth/demo-login { return 404; }
location /api/ {
 limit_req zone=restricted burst=30 nodelay;
 proxy_pass http://127.0.0.1:8080;
 proxy_set_header Host $host;
 proxy_set_header Forwarded "";
 proxy_set_header X-Forwarded-For "";
 proxy_set_header X-Forwarded-Proto "";
 proxy_next_upstream off;
}
location /actuator/ { return 404; }
location /v3/ { return 404; }
location /swagger-ui { return 404; }
location / { try_files $uri $uri/ /index.html; }
}
''',0o644)
default=pathlib.Path('/etc/nginx/sites-enabled/default')
if default.is_symlink():default.unlink()
os.symlink('/etc/nginx/sites-available/campushub-restricted','/etc/nginx/sites-enabled/campushub-restricted')
run(['nginx','-t'])
run(['systemd-analyze','verify','/etc/systemd/system/campushub.service'])
run(['systemctl','daemon-reload'])
run(['systemctl','enable','--now','campushub','nginx','mysql'])
# Backups are encrypted before being written. Restore key is root-only; export off-host after drill.
run(['age-keygen','-o','/etc/campushub/backup-recovery.key'])
recipient=run(['age-keygen','-y','/etc/campushub/backup-recovery.key'])
for script in ['backup.sh','restore-drill.sh','verify-backup.sh']:
    run(['install','-m','0700',str(stage/script),'/etc/campushub/'+script])
write('/etc/campushub/backup.env',f'CH_BACKUP_DEFAULTS=/etc/campushub/backup.cnf\nCH_BACKUP_DATABASE=campushub_prod\nCH_BACKUP_DIRECTORY=/var/backups/campushub\nCH_BACKUP_RECIPIENT={recipient}\n')
write('/etc/systemd/system/campushub-backup.service','''[Unit]
Description=CampusHub encrypted MySQL snapshot
[Service]
Type=oneshot
EnvironmentFile=/etc/campushub/backup.env
ExecStart=/bin/bash /etc/campushub/backup.sh --apply
UMask=0077
''',0o644)
write('/etc/systemd/system/campushub-backup.timer','''[Unit]
Description=Daily CampusHub backup
[Timer]
OnCalendar=*-*-* 03:15:00 UTC
Persistent=true
[Install]
WantedBy=timers.target
''',0o644)
pathlib.Path('/etc/systemd/journald.conf.d').mkdir(exist_ok=True)
write('/etc/systemd/journald.conf.d/campushub.conf','[Journal]\nSystemMaxUse=200M\nMaxRetentionSec=14day\n',0o644)
write('/etc/logrotate.d/campushub','''/var/log/campushub/nginx-*.log {
 daily
 rotate 14
 compress
 missingok
 notifempty
 sharedscripts
 postrotate
  /usr/sbin/nginx -s reopen
 endscript
}
''',0o644)
run(['systemctl','daemon-reload'])
run(['systemctl','enable','--now','campushub-backup.timer'])
print('PASS: restricted release and backup timer installed; readiness/acceptance pending')
