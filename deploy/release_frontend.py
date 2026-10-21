# -*- coding: utf-8 -*-
"""一键发布前端新版本: python -I release_frontend.py [版本号]
流程: 校验dist -> 递归上传(/opt/feuerwerk/build/frontend/dist) -> docker build
      -> 导入k3s -> 改manifests -> 滚动更新
版本号不传则自动在集群当前版本上+1 (如 1.0.0 -> 1.0.1)"""
import re
import sys
import pathlib
import paramiko

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

DEPLOY = pathlib.Path(__file__).parent
ROOT = DEPLOY.parent
DIST = ROOT / "frontend" / "dist"
MANIFEST = DEPLOY / "k8s" / "manifests.yaml"
REMOTE_DIR = "/opt/feuerwerk/build/frontend"
VERSION = sys.argv[1] if len(sys.argv) > 1 else None

# ---------- 连接 ----------
lines = (DEPLOY / "server.config").read_text().splitlines()
kv = {}
for l in lines:
    if "=" in l and not l.startswith("["):
        k, v = l.split("=", 1)
        kv[k.strip()] = v.strip()
client = paramiko.SSHClient()
client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
client.connect(kv["host"], port=int(kv.get("port", 22)), username=kv["user"], password=kv["password"], timeout=20)


def run(cmd, timeout=1800):
    """远程执行并回显, 返回(stdout, rc)"""
    _, out, err = client.exec_command(cmd, timeout=timeout)
    o = out.read().decode("utf-8", "replace").strip()
    e = err.read().decode("utf-8", "replace").strip()
    rc = out.channel.recv_exit_status()
    text = o or e
    if text:
        print(text[-1500:])
    return o, rc


K = "export KUBECONFIG=/etc/rancher/k3s/k3s.yaml; "

# ---------- 1. 本地检查 ----------
index = DIST / "index.html"
if not index.exists():
    print(f"[错误] 找不到 {index}\n先构建: cd frontend && npm run build")
    sys.exit(1)
assets = sorted(p.name for p in (DIST / "assets").glob("*")) if (DIST / "assets").exists() else []
print(f"[1/6] 本地dist: index.html + assets({len(assets)}个): {', '.join(assets[:3])}...")

# ---------- 2. 递归上传dist ----------
print("[2/6] 上传dist ...")
sftp = client.open_sftp()
run(f"rm -rf {REMOTE_DIR}/dist && mkdir -p {REMOTE_DIR}/dist/assets")


def upload_dir(local: pathlib.Path, remote: str):
    for f in sorted(local.iterdir()):
        if f.is_dir():
            run(f"mkdir -p {remote}/{f.name}")
            upload_dir(f, f"{remote}/{f.name}")
        else:
            sftp.put(str(f), f"{remote}/{f.name}")


upload_dir(DIST, REMOTE_DIR + "/dist")
sftp.close()
print("      上传完成")

# ---------- 3. 版本号 ----------
if not VERSION:
    cur, _ = run(K + "kubectl -n feuerwerk get deploy feuerwerk-frontend -o jsonpath='{.spec.template.spec.containers[0].image}'")
    m = re.search(r"feuerwerk/frontend:(\d+)\.(\d+)\.(\d+)", cur or "")
    if not m:
        print(f"[错误] 无法从集群读出当前版本: {cur!r}")
        sys.exit(1)
    VERSION = f"{m.group(1)}.{m.group(2)}.{int(m.group(3)) + 1}"
print(f"[3/6] 目标版本: feuerwerk/frontend:{VERSION}")

# ---------- 4. 构建镜像并导入 ----------
_, rc = run(f"cd {REMOTE_DIR} && docker build -q -t feuerwerk/frontend:{VERSION} . && "
            f"docker save feuerwerk/frontend:{VERSION} | k3s ctr -n k8s.io images import - >/dev/null && echo 镜像OK")
if rc != 0:
    print("[错误] 构建/导入失败")
    sys.exit(1)

# ---------- 5. 改manifests并上传 ----------
text = MANIFEST.read_text(encoding="utf-8")
new_text, n = re.subn(r"feuerwerk/frontend:\d+\.\d+\.\d+", f"feuerwerk/frontend:{VERSION}", text)
if n == 0:
    print("[错误] manifests.yaml 里没找到 feuerwerk/frontend 镜像tag")
    sys.exit(1)
MANIFEST.write_text(new_text, encoding="utf-8")
sftp = client.open_sftp()
sftp.put(str(MANIFEST), "/opt/feuerwerk/manifests.yaml")
sftp.close()
print(f"[5/6] manifests.yaml 已更新(替换{n}处)并上传")

# ---------- 6. 滚动更新 ----------
_, rc = run(K + f"kubectl apply -f /opt/feuerwerk/manifests.yaml >/dev/null && "
             f"kubectl -n feuerwerk rollout status deploy/feuerwerk-frontend --timeout=300s")
print(f"\n{'=' * 46}\n✅ 前端发布完成: feuerwerk/frontend:{VERSION}\n{'=' * 46}")
client.close()
