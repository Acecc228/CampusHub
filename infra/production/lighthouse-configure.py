"""First-use restricted Lighthouse configuration; run as root with --apply after install.
Secrets are generated on the server, never printed. Refuses an existing application schema.
"""
import json, os, pathlib, secrets, subprocess, sys

if '--apply' not in sys.argv:
    print('PREVIEW: private TLS, independent production schema/accounts, root-only secret files')
    sys.exit(0)
if os.geteuid() != 0:
    raise SystemExit('Root required')

def run(command, data=None):
    result = subprocess.run(command, input=data, text=True, capture_output=True)
    if result.returncode:
        raise SystemExit('Configuration failed: '+command[0]+'; raw diagnostics suppressed')
    return result.stdout.strip()

def write(path, content, mode=0o600):
    p=pathlib.Path(path)
    p.write_text(content,encoding='utf-8');p.chmod(mode)

existing=run(['mysql','--protocol=SOCKET','--batch','--skip-column-names'],
             "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='campushub_prod';")
if existing != '0' or pathlib.Path('/etc/campushub/application.env').exists():
    raise SystemExit('Existing deployment detected; refuse initialization')
os.umask(0o077)
for directory in ['/etc/campushub','/etc/campushub/tls','/var/backups/campushub','/etc/mysql/campushub-tls']:
    pathlib.Path(directory).mkdir(exist_ok=True,parents=True)
run(['openssl','req','-x509','-newkey','rsa:3072','-nodes','-days','3650','-subj','/CN=CampusHub Restricted CA',
     '-keyout','/etc/campushub/tls/ca.key','-out','/etc/campushub/tls/ca.crt'])
write('/etc/campushub/tls/extensions','subjectAltName=DNS:localhost,IP:127.0.0.1\nextendedKeyUsage=serverAuth\n')
for folder in ['/etc/campushub/tls','/etc/mysql/campushub-tls']:
    run(['openssl','req','-newkey','rsa:3072','-nodes','-subj','/CN=localhost',
         '-keyout',folder+'/server.key','-out',folder+'/server.csr'])
    run(['openssl','x509','-req','-in',folder+'/server.csr','-CA','/etc/campushub/tls/ca.crt',
         '-CAkey','/etc/campushub/tls/ca.key','-CAcreateserial','-days','365','-extfile','/etc/campushub/tls/extensions',
         '-out',folder+'/server.crt'])
run(['cp','/etc/campushub/tls/ca.crt','/etc/mysql/campushub-tls/ca.crt'])
run(['chown','-R','mysql:mysql','/etc/mysql/campushub-tls'])
pathlib.Path('/etc/mysql/campushub-tls').chmod(0o750)
run(['cp','/etc/campushub/tls/ca.crt','/usr/local/share/ca-certificates/campushub-restricted.crt'])
run(['update-ca-certificates'])
write('/etc/mysql/mysql.conf.d/90-campushub.cnf','''[mysqld]
bind-address=127.0.0.1
mysqlx-bind-address=127.0.0.1
require_secure_transport=ON
innodb_buffer_pool_size=256M
max_connections=40
default-time-zone=+00:00
ssl-ca=/etc/mysql/campushub-tls/ca.crt
ssl-cert=/etc/mysql/campushub-tls/server.crt
ssl-key=/etc/mysql/campushub-tls/server.key
''',0o644)
run(['systemctl','restart','mysql'])
passwords={role:secrets.token_hex(32) for role in ['app','migrator','operator','backup']}
sql="CREATE DATABASE campushub_prod CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;\n"
for role,password in passwords.items():
    sql+=f"CREATE USER 'campushub_prod_{role}'@'localhost' IDENTIFIED BY '{password}' REQUIRE SSL;\n"
    write('/etc/campushub/'+role+'.cnf',f'''[client]
host=localhost
port=3306
protocol=TCP
user=campushub_prod_{role}
password={password}
ssl-mode=VERIFY_IDENTITY
ssl-ca=/etc/campushub/tls/ca.crt
''')
sql+="GRANT SELECT,INSERT,UPDATE,DELETE,CREATE,ALTER,INDEX,REFERENCES ON campushub_prod.* TO 'campushub_prod_migrator'@'localhost';\n"
run(['mysql','--protocol=SOCKET'],sql)
write('/etc/campushub/migration.json',json.dumps({'username':'campushub_prod_migrator','password':passwords['migrator']}))
write('/etc/campushub/application.env',f'''CH_PROD_DB_URL=jdbc:mysql://localhost:3306/campushub_prod?sslMode=VERIFY_IDENTITY&characterEncoding=UTF-8&connectionTimeZone=UTC
CH_PROD_DB_NAME=campushub_prod
CH_PROD_DB_USERNAME=campushub_prod_app
CH_PROD_DB_ACCOUNT=campushub_prod_app@localhost
CH_PROD_DB_PASSWORD={passwords['app']}
CH_WECHAT_APP_ID=wx8ed96bab7f0fbe47
CH_WECHAT_ENABLED=false
CH_WECHAT_APP_SECRET=
''')
if subprocess.run(['id','campushub'],capture_output=True).returncode:
    run(['useradd','--system','--home-dir','/srv/campushub','--shell','/usr/sbin/nologin','campushub'])
pathlib.Path('/srv/campushub/releases').mkdir(parents=True,exist_ok=True)
pathlib.Path('/srv/campushub').chmod(0o755)
pathlib.Path('/srv/campushub/releases').chmod(0o755)
print('PASS: private TLS and independent production database/accounts configured; migration pending')
