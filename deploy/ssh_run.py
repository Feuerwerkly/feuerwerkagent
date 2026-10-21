# -*- coding: utf-8 -*-
"""在部署服务器上远程执行命令: python ssh_run.py "命令"
凭据从同目录 server.config 读取"""
import sys
import pathlib
import configparser

import paramiko

sys.stdout.reconfigure(encoding="utf-8", errors="replace")
sys.stderr.reconfigure(encoding="utf-8", errors="replace")

cfg = configparser.ConfigParser()
cfg.read(pathlib.Path(__file__).parent / "server.config")
host = cfg["server"]["host"]
user = cfg["server"]["user"]
password = cfg["server"]["password"]
port = cfg["server"].getint("port", 22)

cmd = sys.argv[1] if len(sys.argv) > 1 else sys.stdin.read()

client = paramiko.SSHClient()
client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
client.connect(host, port=port, username=user, password=password, timeout=20)
stdin, stdout, stderr = client.exec_command(cmd, timeout=1800)
out = stdout.read().decode("utf-8", "replace")
err = stderr.read().decode("utf-8", "replace")
rc = stdout.channel.recv_exit_status()
client.close()

if out:
    print(out)
if err:
    print("[stderr]", err, file=sys.stderr)
sys.exit(rc)
