"""Trusted operator CLI. Preview by default; SQL and credentials never printed."""
import argparse
import getpass
import hashlib
import os
import pathlib
import re
import secrets
import stat
import subprocess
import sys


def validate_target(database, account):
    if not re.fullmatch(r'[a-z][a-z0-9_]{0,63}', database) or database in ('campushub', 'campushub_test'):
        raise ValueError('Use an independent production database')
    if not re.fullmatch(r'[a-z][a-z0-9_]{0,31}@[A-Za-z0-9.%:_-]+', account):
        raise ValueError('An exact CURRENT_USER identity is required')
    if account.split('@')[0] in ('root', 'campushub_app', 'campushub_test_app',
                                 'campushub_migrator', 'campushub_test_migrator'):
        raise ValueError('Use a dedicated production operator account')


def password_hash(password, salt=None):
    if len(password) < 16 or len(password) > 128:
        raise ValueError('Password must be 16 to 128 characters; use a password manager')
    salt = secrets.token_bytes(16) if salt is None else salt
    return (salt + hashlib.pbkdf2_hmac('sha256', password.encode('utf-8'), salt, 600000, 32)).hex()


def literal(value):
    return "CONVERT(X'" + value.encode('utf-8').hex() + "' USING utf8mb4)"


def validate_login(login):
    if not re.fullmatch(r'[A-Za-z][A-Za-z0-9_.-]{2,63}', login) or login.startswith(('demo_', 'wx_')):
        raise ValueError('Invalid operator login name')


def create_sql(login, display_name, digest, role):
    validate_login(login)
    if role not in ('ADMIN', 'ORGANIZER') or not 1 <= len(display_name) <= 100:
        raise ValueError('Invalid operator role or display name')
    return ('START TRANSACTION;\nINSERT INTO app_user(login_name,display_name,password_hash,enabled,demo_account) '
            f'VALUES ({literal(login)},{literal(display_name)},{literal(digest)},TRUE,FALSE);\n'
            'INSERT INTO app_user_role(user_id,role_id) SELECT LAST_INSERT_ID(),id FROM app_role '
            f'WHERE code={literal(role)};\nCOMMIT;')


def disable_sql(login):
    validate_login(login)
    return f'UPDATE app_user SET enabled=FALSE WHERE login_name={literal(login)} AND demo_account=FALSE; SELECT ROW_COUNT();'


def reset_sql(login, digest):
    validate_login(login)
    return (f'UPDATE app_user SET password_hash={literal(digest)} WHERE login_name={literal(login)} '
            'AND demo_account=FALSE; SELECT ROW_COUNT();')


def mysql(defaults, database, sql):
    result = subprocess.run(['mysql', '--defaults-file=' + str(defaults), '--protocol=TCP',
                             '--batch', '--skip-column-names', '--default-character-set=utf8mb4',
                             '--database=' + database], input=sql, text=True, encoding='utf-8',
                            capture_output=True, timeout=30)
    if result.returncode:
        raise RuntimeError('MySQL operation failed; inspect server status without recording SQL/passwords')
    return result.stdout.strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['create', 'disable', 'reset-password'])
    parser.add_argument('--defaults-file', required=True, type=pathlib.Path)
    parser.add_argument('--database', required=True)
    parser.add_argument('--account', required=True, help='Exact CURRENT_USER(), e.g. campushub_prod_operator@localhost')
    parser.add_argument('--login', required=True)
    parser.add_argument('--display-name')
    parser.add_argument('--role', choices=['ORGANIZER', 'ADMIN'])
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    validate_target(args.database, args.account)
    validate_login(args.login)
    if args.action == 'create' and (not args.display_name or not args.role):
        raise ValueError('Create requires display-name and role')
    if not args.apply:
        print('PREVIEW: trusted account operation; no database connection or password requested')
        return
    if not args.defaults_file.is_absolute() or args.defaults_file.is_symlink() or not args.defaults_file.is_file():
        raise ValueError('Use an absolute private MySQL option file')
    if os.name != 'nt' and stat.S_IMODE(args.defaults_file.stat().st_mode) & 0o077:
        raise ValueError('MySQL option file must be private (0600)')
    identity = mysql(args.defaults_file, args.database, 'SELECT DATABASE(),CURRENT_USER();')
    if identity != args.database + '\t' + args.account:
        raise ValueError('Database or account identity differs; nothing changed')
    if mysql(args.defaults_file, args.database, 'SELECT GROUP_CONCAT(code ORDER BY code) FROM app_role;') != 'ADMIN,ORGANIZER,STUDENT':
        raise ValueError('Required role inventory differs')
    if args.action == 'disable':
        sql = disable_sql(args.login)
    else:
        password = getpass.getpass('New unique operator password: ')
        if password != getpass.getpass('Confirm password: '):
            raise ValueError('Passwords differ')
        digest = password_hash(password)
        sql = create_sql(args.login, args.display_name, digest, args.role) if args.action == 'create' else reset_sql(args.login, digest)
    output = mysql(args.defaults_file, args.database, sql)
    if args.action != 'create' and output != '1':
        raise ValueError('No matching non-demo account changed')
    print('PASS: account operation completed; existing sessions are checked on their next request')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, RuntimeError, OSError, subprocess.TimeoutExpired) as error:
        # Never print raw subprocess errors, SQL or credential contents.
        print(str(error) if isinstance(error, (ValueError, RuntimeError)) else 'Operation failed or timed out; read back state before retry', file=sys.stderr)
        sys.exit(1)
