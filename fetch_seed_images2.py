# -*- coding: utf-8 -*-
"""第二阶段：官方直链替换/补充 + Bing白名单重试(LG) + 清理坏图"""
import html
import os
import re
import urllib.parse
import urllib.request

UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36"
BASE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(BASE, "shop_seed_images")

DIRECT = [
    # (输出文件名, URL, referer)
    ("rtx-5090.jpg", "https://www.techpowerup.com/review/nvidia-geforce-rtx-5090-founders-edition/images/title.jpg", "https://www.techpowerup.com/"),
    ("rtx-4060.jpg", "https://www.techpowerup.com/review/msi-geforce-rtx-4060-gaming-x/images/title.jpg", "https://www.techpowerup.com/"),
    ("rx-7900xtx.jpg", "https://www.techpowerup.com/review/amd-radeon-rx-7900-xtx/images/title.jpg", "https://www.techpowerup.com/"),
    ("mate-70-pro.jpg", "https://consumer.huawei.com/content/dam/huawei-cbg-site/cn/mkt/pdp/phones/mate70-pro/wxshare.jpg", "https://consumer.huawei.com/"),
    ("watch-gt5-pro.jpg", "https://consumer.huawei.com/content/dam/huawei-cbg-site/cn/mkt/pdp/wearables/watch-gt5-pro/wxshare.jpg", "https://consumer.huawei.com/"),
    ("macbook-air-m4.webp", "https://www.apple.com.cn/macbook-air/images/meta/macbook_air_mx__ez5y0k5yy7au_og.webp?202609240552", "https://www.apple.com.cn/"),
    ("ipad-pro-m4.png", "https://www.apple.com/v/ipad-pro/aw/images/meta/ipad-pro_overview__bu4cql27diaa_og.png?202609240552", "https://www.apple.com.cn/"),
    ("ipad-pro-m4-2.jpg", "https://www.apple.com.cn/v/ipad-pro/aw/images/overview/hero/hero_endframe__du5kcy4qnxkm_large_2x.jpg", "https://www.apple.com.cn/"),
    ("airpods-pro-2.png", "https://www.apple.com/v/airpods-pro/t/images/meta/og__c0ceegchesom_overview.png?202609230212", "https://www.apple.com.cn/"),
    ("matepad-pro-13-2.jpg", "https://consumer.huawei.com/content/dam/huawei-cbg-site/cn/mkt/pdp/tablets/matepad-pro-13-2/wxshare.jpg", "https://consumer.huawei.com/"),
]

DELETE = ["rtx-5090.png", "rtx-5090-2.png", "rtx-4060-2.jpg", "rx-7900xtx-2.jpg",
          "mate-70-pro-2.png", "watch-gt5-pro-2.png", "test-apple.jpg"]


def sniff(d):
    if d.startswith(b"\xff\xd8\xff"): return "jpg"
    if d.startswith(b"\x89PNG"): return "png"
    if d[:4] == b"RIFF" and d[8:12] == b"WEBP": return "webp"
    if d.startswith(b"GIF8"): return "gif"
    return None


def fetch(url, referer, timeout=20):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Referer": referer,
                                               "Accept": "image/avif,image/webp,image/*,*/*;q=0.8"})
    return urllib.request.urlopen(req, timeout=timeout).read()


def bing_jd(query):
    q = urllib.parse.quote(query)
    data = fetch("https://cn.bing.com/images/async?q=%s&first=1&count=30" % q,
                 "https://cn.bing.com/", 20).decode("utf-8", "ignore")
    urls = [html.unescape(u) for u in re.findall(r"murl&quot;:&quot;(.*?)&quot;", data)]
    good = [u for u in urls if "360buyimg.com" in u or "zol-img.com.cn" in u]
    return good


def main():
    os.makedirs(OUT, exist_ok=True)
    for name in DELETE:
        p = os.path.join(OUT, name)
        if os.path.exists(p):
            os.remove(p)
            print("[DEL]", name)

    for name, url, ref in DIRECT:
        try:
            d = fetch(url, ref)
            ext = sniff(d)
            want = name.rsplit(".", 1)[1]
            if not ext or 8000 < len(d) > 6000000:
                raise ValueError("bad content %d bytes, sniff=%s" % (len(d), ext))
            if ext != want:
                print("[WARN] ext mismatch %s != %s for %s" % (ext, want, name))
            with open(os.path.join(OUT, name), "wb") as f:
                f.write(d)
            print("[OK] %-24s %7d bytes" % (name, len(d)))
        except Exception as e:
            print("[FAIL] %-24s %s" % (name, str(e)[:80]))

    # LG 27GP95R：京东/中关村图床白名单
    got = False
    try:
        for u in bing_jd("LG 27GP95R 显示器"):
            try:
                d = fetch(u, "https://www.bing.com/", 12)
                if sniff(d) and 20000 < len(d) < 3000000:
                    with open(os.path.join(OUT, "lg-27gp95r.jpg"), "wb") as f:
                        f.write(d)
                    print("[OK] lg-27gp95r.jpg %d bytes <- %s" % (len(d), u[:100]))
                    got = True
                    break
            except Exception:
                continue
    except Exception as e:
        print("[BING] lg search fail", str(e)[:60])
    if not got:
        print("[MISS] lg-27gp95r")


if __name__ == "__main__":
    import sys
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    main()
