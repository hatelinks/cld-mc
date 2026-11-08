#!/bin/sh
# Установка relay cld_tunnel на VPS (Ubuntu 24.04 или новее, запускать от root).
# Использование: sh install_relay.sh cld_tunnel-relay-0.1.0.jar [домен] [IP или имя сервера]
# Пример с доменом:   sh install_relay.sh cld_tunnel-relay-0.1.0.jar mc.example.ru 81.2.3.4
# Пример без домена:  sh install_relay.sh cld_tunnel-relay-0.1.0.jar "" 81.2.3.4
set -e
JAR="$1"; DOMAIN="$2"; ADDR="$3"
[ -f "$JAR" ] || { echo "нужен путь к cld_tunnel-relay-*.jar"; exit 1; }
[ -n "$DOMAIN" ] || [ -n "$ADDR" ] || { echo "нужен домен или IP"; exit 1; }
command -v java >/dev/null 2>&1 && java -version 2>&1 | grep -q '"2[1-9]' || {
  apt-get update && apt-get install -y openjdk-21-jre-headless
}
id cldrelay >/dev/null 2>&1 || useradd --system --home /opt/cld_tunnel --shell /usr/sbin/nologin cldrelay
mkdir -p /opt/cld_tunnel
cp "$JAR" /opt/cld_tunnel/relay.jar
if [ ! -f /opt/cld_tunnel/relay.properties ]; then
  (cd /opt/cld_tunnel && java -jar relay.jar relay.properties >/dev/null)
  sed -i "s/^domain=.*/domain=$DOMAIN/; s/^address=.*/address=$ADDR/" /opt/cld_tunnel/relay.properties
fi
chown -R cldrelay:cldrelay /opt/cld_tunnel
chmod 600 /opt/cld_tunnel/relay.properties
cat > /etc/systemd/system/cld-relay.service <<UNIT
[Unit]
Description=cld_tunnel relay
After=network-online.target
Wants=network-online.target

[Service]
User=cldrelay
WorkingDirectory=/opt/cld_tunnel
ExecStart=/usr/bin/java -Xmx256m -jar /opt/cld_tunnel/relay.jar /opt/cld_tunnel/relay.properties
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
UNIT
systemctl daemon-reload
systemctl enable --now cld-relay
systemctl restart cld-relay
if command -v ufw >/dev/null 2>&1 && ufw status | grep -q active; then
  ufw allow 25580/tcp; ufw allow 25565/tcp; ufw allow 25601:25650/tcp
fi
# Oracle Cloud и похожие образы закрывают всё правилами iptables (REJECT в INPUT), ufw там нет
if command -v iptables >/dev/null 2>&1 && iptables -S INPUT 2>/dev/null | grep -q REJECT; then
  for p in 25580 25565 25601:25650; do
    iptables -C INPUT -p tcp -m state --state NEW -m tcp --dport $p -j ACCEPT 2>/dev/null ||
      iptables -I INPUT 1 -p tcp -m state --state NEW -m tcp --dport $p -j ACCEPT
  done
  command -v netfilter-persistent >/dev/null 2>&1 && netfilter-persistent save >/dev/null 2>&1 || true
  echo "Порты открыты в iptables. В Oracle Cloud ещё добавьте их в Security List (Ingress Rules) сети VCN."
fi
sleep 2
systemctl --no-pager status cld-relay | head -5
echo
echo "Ключ для настроек мода:"
grep '^key=' /opt/cld_tunnel/relay.properties | cut -d= -f2
echo "Адрес relay для мода: ${ADDR:-$DOMAIN}:25580"
[ -n "$DOMAIN" ] && echo "В DNS домена нужна запись: *.$DOMAIN  A  <IP этого сервера>"
echo "Лог: journalctl -u cld-relay -f"
