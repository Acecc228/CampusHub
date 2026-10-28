#!/usr/bin/env bash
set -euo pipefail
if [[ ${1:-} != --apply ]]; then echo 'PREVIEW: Ubuntu 24.04 updates, SSH-only firewall, Java21/Nginx/tools and official MySQL8.4 repository'; exit 0; fi
[[ $EUID = 0 ]] || exit 2
. /etc/os-release
[[ $ID = ubuntu && $VERSION_ID = 24.04 ]] || exit 2
mkdir -p /var/log/campushub-deploy
exec > >(tee -a /var/log/campushub-deploy/init.log) 2>&1
export DEBIAN_FRONTEND=noninteractive
ufw allow 22/tcp
ufw default deny incoming
ufw default allow outgoing
ufw --force enable
apt-get update
apt-get -y -o Dpkg::Options::=--force-confold upgrade
apt-get install -y openjdk-21-jdk-headless nginx curl ca-certificates gnupg unzip python3 age rsync git
systemctl stop nginx
# Preserve all existing authentication and recovery methods.
printf 'MaxAuthTries 3\nLoginGraceTime 30\n' > /etc/ssh/sshd_config.d/60-campushub.conf
install -d -m 0755 /run/sshd
sshd -t
systemctl reload-or-restart ssh
install -d -m 0750 /var/cache/campushub-deploy
cd /var/cache/campushub-deploy
curl --fail --location --retry 2 --max-time 90 -o mysql-apt-config.deb https://dev.mysql.com/get/mysql-apt-config_0.8.40-1_all.deb
echo 'mysql-apt-config mysql-apt-config/select-server select mysql-8.4-lts' | debconf-set-selections
dpkg -i mysql-apt-config.deb
apt-get update
apt-cache policy mysql-community-server
apt-get install -y mysql-server
mysql --version
java -version
nginx -v
ufw status
test ! -f /var/run/reboot-required || echo REBOOT_REQUIRED
