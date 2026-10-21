# -*- coding: utf-8 -*-
"""
抓取商城种子商品真实图片到 shop_seed_images/ 目录
- 官方源：Apple/Samsung/NVIDIA 官网 CDN（国内可达）
- 其余：cn.bing.com 图片搜索接口取原图（优先京东图床/官网域名）
运行：python fetch_seed_images.py
"""
import html
import os
import re
import sys
import urllib.parse
import urllib.request

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"
OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "shop_seed_images")

# 1) 官方直链（key -> url 列表，第一个为主图，其余为详情图）
OFFICIAL = {
    "iphone-16-pro": [
        "https://www.apple.com.cn/v/iphone-16-pro/f/images/overview/product-viewer/iphone-pro/all_colors__fdpduog7urm2_xlarge.jpg",
        "https://www.apple.com.cn/v/iphone-16-pro/f/images/overview/product-viewer/iphone-pro/black_titanium__ezezv5esulua_xlarge.jpg",
    ],
    "macbook-air-m4": [
        "https://www.apple.com.cn/v/macbook-air/f/images/overview/hero/hero_static__dlx6t0cg2ey2_large.jpg",
        "https://www.apple.com.cn/macbook-air/images/meta/macbook-air-m5--overhead__ca37gho8x6qy_og.png?202510290400",
    ],
    "ipad-pro-m4": [
        "https://www.apple.com.cn/v/ipad-pro/aw/images/overview/hero/hero__bvkgv1xyv5e6_large.jpg",
        "https://www.apple.com.cn/v/ipad-pro/aw/images/overview/accessories/pencil-pro__bdzdkn9g8ciq_large.jpg",
    ],
    "airpods-pro-2": [
        "https://www.apple.com.cn/v/airpods-pro/f/images/meta/airpods-pro--case--both__fnyabhnuewuq_og.png?202510290400",
    ],
    "galaxy-s25-ultra": [
        "https://images.samsung.com.cn/cn/smartphones/galaxy-s25-ultra/images/galaxy-s25-ultra-share-image.jpg",
    ],
    "rtx-5080": [
        "https://www.nvidia.cn/content/dam/en-zz/Solutions/geforce/graphic-cards/50-series/rtx-5080/geforce-rtx-5080-og-1200x630.jpg",
        "https://www.nvidia.cn/content/dam/en-zz/Solutions/geforce/graphic-cards/50-series/geforce-rtx-50series-og-1200x630.jpg",
    ],
}

# 2) Bing 搜索源（key -> 搜索词）
BING_QUERIES = {
    "legion-y9000p": "联想拯救者 Y9000P 笔记本电脑 产品图",
    "matebook-x-pro": "华为 MateBook X Pro 笔记本 产品图",
    "mate-70-pro": "华为 Mate 70 Pro 手机 产品图",
    "xiaomi-15-pro": "小米15 Pro 手机 产品图",
    "matepad-pro-13": "华为 MatePad Pro 13.2 平板 产品图",
    "xiaomi-pad-7-pro": "小米平板7 Pro 平板 产品图",
    "rtx-5090": "RTX 5090 Founders Edition 显卡",
    "rtx-4060": "七彩虹 RTX 4060 显卡 产品图",
    "rx-7900xtx": "AMD RX 7900 XTX 显卡 公版",
    "dell-u2723qe": "戴尔 U2723QE 显示器 产品图",
    "lg-27gp95r": "LG 27GP95R 显示器",
    "aoc-q27g3xm": "AOC Q27G3XM 显示器 产品图",
    "watch-gt5-pro": "华为 WATCH GT 5 Pro 手表 产品图",
}

# 京东图床/官网域名优先（白底产品图概率高）
PRIORITY = ["360buyimg.com", "apple.com", "images.samsung.com", "consumer-res.huawei.com",
            "vmall.com", "nvidia.cn", "nvidia.com", "appmifile.com", "i01.appmifile.com",
            "techpowerup.com", "ign.cn", "lenovo.com.cn", "amd.com"]

MAGIC = [(b"\xff\xd8\xff", "jpg"), (b"\x89PNG", "png"), (b"GIF8", "gif")]


def sniff(data):
    for magic, ext in MAGIC:
        if data.startswith(magic):
            return ext
    if data[:4] == b"RIFF" and data[8:12] == b"WEBP":
        return "webp"
    return None


def fetch(url, timeout=15):
    req = urllib.request.Request(url, headers={
        "User-Agent": UA,
        "Referer": "https://www.bing.com/",
        "Accept": "image/avif,image/webp,image/*,*/*;q=0.8",
    })
    return urllib.request.urlopen(req, timeout=timeout).read()


def bing_murls(query, count=24):
    q = urllib.parse.quote(query)
    data = fetch("https://cn.bing.com/images/async?q=%s&first=1&count=%d" % (q, count), timeout=20).decode("utf-8", "ignore")
    urls = re.findall(r"murl&quot;:&quot;(.*?)&quot;", data)
    return [html.unescape(u) for u in urls]


def sort_by_priority(urls):
    def score(u):
        ul = u.lower()
        for i, p in enumerate(PRIORITY):
            if p in ul:
                return i
        return 99
    return sorted(urls, key=score)


def save(key, idx, data):
    ext = sniff(data)
    if not ext:
        return None
    suffix = "" if idx == 0 else "-%d" % (idx + 1)
    name = "%s%s.%s" % (key, suffix, ext)
    with open(os.path.join(OUT_DIR, name), "wb") as f:
        f.write(data)
    return name


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    report = {}

    for key, urls in OFFICIAL.items():
        got = 0
        for i, u in enumerate(urls):
            try:
                data = fetch(u, timeout=20)
                if 10000 < len(data) < 6000000:
                    name = save(key, got, data)
                    if name:
                        print("[OFFICIAL] %-18s %s (%d bytes)" % (key, name, len(data)))
                        got += 1
                else:
                    print("[OFFICIAL] %-18s bad size %d" % (key, len(data)))
            except Exception as e:
                print("[OFFICIAL] %-18s FAIL %s | %s" % (key, str(e)[:60], u[:80]))
        report[key] = got

    for key, query in BING_QUERIES.items():
        got = 0
        try:
            murls = sort_by_priority(bing_murls(query))
        except Exception as e:
            print("[BING] %-18s SEARCH FAIL %s" % (key, str(e)[:80]))
            murls = []
        if not murls:
            print("[BING] %-18s NO RESULTS" % key)
        tried = 0
        for u in murls:
            if tried >= 6 or got >= 2:
                break
            tried += 1
            try:
                data = fetch(u, timeout=12)
                if 15000 < len(data) < 4000000 and sniff(data):
                    name = save(key, got, data)
                    if name:
                        print("[BING]     %-18s %s (%d bytes) <- %s" % (key, name, len(data), u[:90]))
                        got += 1
            except Exception as e:
                print("[BING]     %-18s skip %s" % (key, str(e)[:50]))
        report[key] = got

    print("\n===== SUMMARY =====")
    for k in sorted(report):
        print("%-20s %d img" % (k, report[k]))
    missing = [k for k, v in report.items() if v == 0]
    if missing:
        print("MISSING:", ", ".join(missing))


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    main()
