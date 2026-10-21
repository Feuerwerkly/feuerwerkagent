# -*- coding: utf-8 -*-
"""一键发布后端新版本: python -I release.py [版本号]
流程: 校验jar -> (md5相同则跳过)上传 -> docker build -> 导入k3s -> 改manifests -> 滚动更新
版本号不传则自动在集群当前版本上+1 (如 1.0.2 -> 1.0.3)"""
import re
import sys
import pathlib
import hashlib
import paramiko

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

DEPLOY = pathlib.Path(__file__).parent
ROOT = DEPLOY.parent
JAR = ROOT / "target" / "feuerwerkagent-0.0.1-SNAPSHOT.jar"
MANIFEST = DEPLOY / "k8s" / "manifests.yaml"
REMOTE_DIR = "/opt/feuerwerk/build/app"
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
if not JAR.exists():
    print(f"[错误] 找不到 {JAR}\n先构建: cd {ROOT} && JAVA_HOME=D:\\development_all\\java_all\\jdk_all\\jdk17_all ./mvnw package -DskipTests")
    sys.exit(1)
local_md5 = hashlib.md5(JAR.read_bytes()).hexdigest()
print(f"[1/6] 本地jar: {JAR.name} ({JAR.stat().st_size/1024/1024:.0f}MB, md5 {local_md5[:8]})")

# ---------- 2. 上传(相同则跳过) ----------
remote_md5, _ = run(f"md5sum {REMOTE_DIR}/app.jar 2>/dev/null | cut -d' ' -f1")
if remote_md5 == local_md5:
    print("[2/6] 远端jar一致, 跳过上传")
else:
    print("[2/6] 上传jar ...")
    sftp = client.open_sftp()
    sftp.put(str(JAR), f"{REMOTE_DIR}/app.jar")
    sftp.close()
    print(f"      上传完成 ({JAR.stat().st_size/1024/1024:.0f}MB)")

# ---------- 3. 版本号 ----------
if not VERSION:
    cur, _ = run(K + "kubectl -n feuerwerk get deploy feuerwerk-app -o jsonpath='{.spec.template.spec.containers[0].image}'")
    m = re.search(r"feuerwerk/app:(\d+)\.(\d+)\.(\d+)", cur or "")
    if not m:
        print(f"[错误] 无法从集群读出当前版本: {cur!r}")
        sys.exit(1)
    VERSION = f"{m.group(1)}.{m.group(2)}.{int(m.group(3)) + 1}"
print(f"[3/6] 目标版本: feuerwerk/app:{VERSION}")

# ---------- 4. 构建镜像并导入 ----------
_, rc = run(f"cd {REMOTE_DIR} && docker build -q -t feuerwerk/app:{VERSION} . && "
            f"docker save feuerwerk/app:{VERSION} | k3s ctr -n k8s.io images import - >/dev/null && echo 镜像OK")
if rc != 0:
    print("[错误] 构建/导入失败")
    sys.exit(1)

# ---------- 5. 改manifests并上传 ----------
text = MANIFEST.read_text(encoding="utf-8")
new_text, n = re.subn(r"feuerwerk/app:\d+\.\d+\.\d+", f"feuerwerk/app:{VERSION}", text)
if n == 0:
    print("[错误] manifests.yaml 里没找到 feuerwerk/app 镜像tag")
    sys.exit(1)
MANIFEST.write_text(new_text, encoding="utf-8")
sftp = client.open_sftp()
sftp.put(str(MANIFEST), "/opt/feuerwerk/manifests.yaml")
sftp.close()
print(f"[5/6] manifests.yaml 已更新(替换{n}处)并上传")

# ---------- 6. 滚动更新 ----------
_, rc = run(K + f"kubectl apply -f /opt/feuerwerk/manifests.yaml >/dev/null && "
             f"kubectl -n feuerwerk rollout status deploy/feuerwerk-app --timeout=300s")
print(f"\n{'='*46}\n✅ 发布完成: feuerwerk/app:{VERSION}\n{'='*46}")
client.close()
