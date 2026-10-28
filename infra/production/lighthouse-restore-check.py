"""Restore latest trusted encrypted snapshot into a newly created scratch schema only."""
import datetime,json,os,pathlib,secrets,subprocess,sys
if '--apply' not in sys.argv: print('PREVIEW: restore latest encrypted backup to independent scratch schema');sys.exit(0)
if os.geteuid()!=0:raise SystemExit('Root required')
def run(cmd,data=None,env=None):
    p=subprocess.run(cmd,input=data,text=True,capture_output=True,env=env)
    if p.returncode:raise SystemExit('Restore drill failed: '+cmd[0]+'; raw output suppressed')
    return p.stdout.strip()
stamp=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%d%H%M%S')
schema='campushub_prod_restore_'+stamp
username='restore_'+stamp
password=secrets.token_hex(32)
sql=f"CREATE DATABASE {schema}; CREATE USER '{username}'@'localhost' IDENTIFIED BY '{password}' REQUIRE SSL; GRANT SELECT,INSERT,CREATE,ALTER,INDEX,REFERENCES ON {schema}.* TO '{username}'@'localhost';"
run(['mysql','--protocol=SOCKET'],sql)
options=pathlib.Path('/etc/campushub/restore.cnf')
options.write_text(f'[client]\nhost=localhost\nport=3306\nprotocol=TCP\nuser={username}\npassword={password}\nssl-mode=VERIFY_IDENTITY\nssl-ca=/etc/campushub/tls/ca.crt\n');options.chmod(0o600)
backup=sorted(pathlib.Path('/var/backups/campushub').glob('*.sql.gz.age'))[-1]
env={**os.environ,'CH_RESTORE_DEFAULTS':str(options),'CH_RESTORE_DATABASE':schema,'CH_RESTORE_BACKUP':str(backup),'CH_RESTORE_IDENTITY':'/etc/campushub/backup-recovery.key'}
output=run(['bash','/etc/campushub/restore-drill.sh','--apply'],env=env)
pathlib.Path('/var/log/campushub-deploy/restore-output.txt').write_text(output)
checks='SELECT COUNT(*) FROM activity; SELECT COUNT(*) FROM activity_registration; SELECT COUNT(*) FROM app_user; SELECT COUNT(*) FROM flyway_schema_history WHERE success=1;'
source=run(['mysql','--protocol=SOCKET','-N','campushub_prod'],checks)
restored=run(['mysql','--protocol=SOCKET','-N',schema],checks)
if source!=restored:raise SystemExit('Restored row counts differ')
invariants=run(['mysql','--protocol=SOCKET','-N',schema],"SELECT COUNT(*) FROM activity a LEFT JOIN (SELECT activity_id,COUNT(*) n FROM activity_registration WHERE status='CONFIRMED' GROUP BY activity_id) r ON r.activity_id=a.id WHERE a.registered_count<>COALESCE(r.n,0); SELECT COUNT(*) FROM activity WHERE registered_count>capacity;")
if invariants.split()!=['0','0']:raise SystemExit('Restored invariants failed')
print('PASS: encrypted full snapshot restored to independent scratch schema; row counts and invariants match')
