# -*- coding: utf-8 -*-
"""把本地构建产物上传到部署服务器 /opt/feuerwerk/build/
用法: python upload.py"""
import pathlib
import sys

import paramiko

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

ROOT = pathlib.Path(__file__).parent
SERVER_ROOT = "/opt/feuerwerk/build"

UPLOADS = [
    (ROOT.parent / "target" / "feuerwerkagent-0.0.1-SNAPSHOT.jar", "app/app.jar"),
    (ROOT / "k8s" / "app" / "Dockerfile", "app/Dockerfile"),
    (ROOT / "k8s" / "frontend" / "Dockerfile", "frontend/Dockerfile"),
    (ROOT / "k8s" / "frontend" / "nginx.conf", "frontend/nginx.conf"),
    (ROOT.parent / "src" / "main" / "resources" / "sql" / "kb_schema.sql", "sql/kb_schema.sql"),
    (ROOT.parent / "src" / "main" / "resources" / "sql" / "shop_schema.sql", "sql/shop_schema.sql"),
    (ROOT / "k8s" / "manifests.yaml", "../manifests.yaml"),
]

# 前端 dist 整目录
FRONT_DIST = ROOT.parent / "frontend" / "dist"

cfg_lines = (ROOT / "server.config").read_text().splitlines()
cfg = dict(l.split("=", 1) for l in cfg_lines if "=" in l and not l.startswith("["))
cfg = {k.strip(): v.strip() for k, v in cfg.items()}

client = paramiko.SSHClient()
client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
client.connect(cfg["host"], port=int(cfg.get("port", 22)), username=cfg["user"], password=cfg["password"], timeout=20)
sftp = client.open_sftp()


def mkdirs(remote_dir):
    parts = remote_dir.strip("/").split("/")
    cur = ""
    for p in parts:
        cur += "/" + p
        try:
            sftp.stat(cur)
        except FileNotFoundError:
            sftp.mkdir(cur)


for local, rel in UPLOADS:
    remote = f"{SERVER_ROOT}/{rel}"
    mkdirs(remote.rsplit("/", 1)[0])
    print(f"put {local.name} -> {remote} ({local.stat().st_size/1024/1024:.1f}MB)")
    sftp.put(str(local), remote)

# dist 目录递归上传
for f in sorted(FRONT_DIST.rglob("*")):
    if f.is_file():
        rel = f.relative_to(FRONT_DIST).as_posix()
        remote = f"{SERVER_ROOT}/frontend/dist/{rel}"
        mkdirs(remote.rsplit("/", 1)[0])
        sftp.put(str(f), remote)
n = sum(1 for f in FRONT_DIST.rglob("*") if f.is_file())
print(f"dist 目录 {n} 个文件上传完成")

sftp.close()
client.close()
print("ALL DONE")
