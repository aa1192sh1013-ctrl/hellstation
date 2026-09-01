# -*- coding: utf-8 -*-
"""HellStation 아이콘 생성기.

좌표를 한 군데(100x100 공간)에만 두고 거기서
  - 런처용 적응형 아이콘 벡터(108x108, 안전영역 72x72)
  - Play 스토어 아이콘 512x512 PNG
  - Play 그래픽 이미지 1024x500 PNG
를 모두 뽑습니다. 따로 그리면 언젠가 서로 달라집니다.

도형은 ui/character/HellFace.kt 와 같은 좌표를 씁니다.
"""
import io
import math
import os
import sys

from PIL import Image, ImageDraw

# ── 색 ──────────────────────────────────────────────────────────────────────
BG = (0x12, 0x10, 0x1C)
BODY = (0x2A, 0x27, 0x38)
EDGE = (0x4A, 0x45, 0x60)
GLASS = (0x09, 0x08, 0x10)
SIGN_BG = (0x0B, 0x09, 0x13)
CYAN = (0x00, 0xD9, 0xC0)
MAGENTA = (0xFF, 0x2E, 0x88)
FLAME = (0xFF, 0x3E, 0x63)
CORE = (0xFF, 0xE8, 0xEE)
SKIRT = (0x16, 0x14, 0x1F)


def hexof(c):
    return '#%02X%02X%02X' % c


# ── 100x100 공간의 도형 ──────────────────────────────────────────────────────
HORN_L = [(33, 20), (21, 2), (43, 14)]
HORN_R = [(67, 20), (79, 2), (57, 14)]
BODY_RECT = (20, 14, 80, 84)
BODY_R = 11
SIGN_RECT = (31, 21, 69, 31)
LEDS = [(37 + i * 9, 24, 42 + i * 9, 28) for i in range(3)]
GLASS_POLY = [(31, 38), (69, 38), (73, 63), (27, 63)]
SKIRT_POLY = [(24, 78), (76, 78), (71, 90), (29, 90)]
LAMPS = [(31, 71), (69, 71)]
LAMP_R = 3.4


def cubic(p0, p1, p2, p3, steps=24):
    out = []
    for i in range(steps + 1):
        t = i / steps
        u = 1 - t
        x = u * u * u * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t * t * t * p3[0]
        y = u * u * u * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t * t * t * p3[1]
        out.append((x, y))
    return out


def flame(cx, bottom, half, height, tip_x):
    """HellFace.flamePath 와 같은 모양. 둥근 바닥 + 잘록한 허리 + 눕는 끝."""
    top = bottom - height
    pts = [(cx - half, bottom)]
    pts += cubic((cx - half, bottom),
                 (cx - half * 1.12, bottom - height * 0.42),
                 (tip_x - half * 0.95, bottom - height * 0.66),
                 (tip_x, top))[1:]
    pts += cubic((tip_x, top),
                 (tip_x + half * 0.55, bottom - height * 0.62),
                 (cx + half * 1.12, bottom - height * 0.40),
                 (cx + half, bottom))[1:]
    pts += cubic((cx + half, bottom),
                 (cx + half, bottom + half * 0.85),
                 (cx - half, bottom + half * 0.85),
                 (cx - half, bottom))[1:]
    return pts


# 지옥 등급 기준(heat 0.85)으로 눈을 그립니다. 아이콘은 화난 얼굴이어야 합니다.
HEAT = 0.85
HALF = 3.2 + 1.2 * HEAT
HEIGHT = 8.5 + 6 * HEAT
LEAN = 2.4 + 2.4 * HEAT
EYES = []
for cx, outward in ((41, -1), (59, 1)):
    bottom = 51 + HEIGHT * 0.34
    EYES.append((flame(cx, bottom, HALF, HEIGHT, cx + outward * LEAN),
                 flame(cx, bottom - HEIGHT * 0.06, HALF * 0.46, HEIGHT * 0.55,
                       cx + outward * LEAN * 0.45)))


# ── PNG ─────────────────────────────────────────────────────────────────────
def draw_png(size, pad_ratio, bg):
    """정사각 캔버스에 마스코트를 그립니다. pad_ratio 만큼 여백을 둡니다."""
    scale = 4
    img = Image.new('RGB', (size * scale, size * scale), bg)
    d = ImageDraw.Draw(img)
    span = size * scale * (1 - 2 * pad_ratio)
    off = size * scale * pad_ratio
    u = span / 100.0

    def P(pts):
        return [(off + x * u, off + y * u) for x, y in pts]

    def R(rect, radius=0):
        x0, y0, x1, y1 = rect
        box = [off + x0 * u, off + y0 * u, off + x1 * u, off + y1 * u]
        return box, radius * u

    box, r = R(BODY_RECT, BODY_R)
    d.rounded_rectangle(box, radius=r, fill=BODY, outline=EDGE, width=max(1, int(2 * u)))
    for horn in (HORN_L, HORN_R):
        d.polygon(P(horn), fill=MAGENTA)
    # 꼬리
    tail = cubic((79, 66), (92, 64), (95, 74), (88, 82))
    d.line(P(tail), fill=MAGENTA, width=max(1, int(3.2 * u)), joint='curve')
    d.polygon(P([(88, 80), (96, 86), (85, 90)]), fill=MAGENTA)
    # 몸통을 다시 덮어 꼬리 시작점을 가립니다
    d.rounded_rectangle(box, radius=r, fill=BODY, outline=EDGE, width=max(1, int(2 * u)))
    box, r = R(SIGN_RECT, 2)
    d.rounded_rectangle(box, radius=r, fill=SIGN_BG)
    for led in LEDS:
        box2, r2 = R(led, 1)
        d.rounded_rectangle(box2, radius=r2, fill=CYAN)
    d.polygon(P(GLASS_POLY), fill=GLASS, outline=EDGE)
    for outer, core in EYES:
        d.polygon(P(outer), fill=FLAME)
        d.polygon(P(core), fill=CORE)
    d.polygon(P(SKIRT_POLY), fill=SKIRT, outline=EDGE)
    for lx, ly in LAMPS:
        c = (off + lx * u, off + ly * u)
        rr = LAMP_R * u
        d.ellipse([c[0] - rr * 1.9, c[1] - rr * 1.9, c[0] + rr * 1.9, c[1] + rr * 1.9],
                  fill=(0x0E, 0x3A, 0x38))
        d.ellipse([c[0] - rr, c[1] - rr, c[0] + rr, c[1] + rr], fill=CYAN)
    return img.resize((size, size), Image.LANCZOS)


# ── 벡터(적응형 아이콘) ──────────────────────────────────────────────────────
def path(pts, close=True):
    s = 'M%.2f,%.2f' % pts[0]
    for p in pts[1:]:
        s += ' L%.2f,%.2f' % p
    return s + (' Z' if close else '')


def to_icon(pts):
    """100x100 -> 108x108 의 가운데 72x72 안전영역."""
    return [(18 + x * 0.72, 18 + y * 0.72) for x, y in pts]


def rect_path(rect, radius):
    x0, y0, x1, y1 = rect
    (a, b), (c, dd) = to_icon([(x0, y0)])[0], to_icon([(x1, y1)])[0]
    r = radius * 0.72
    return ('M%.2f,%.2f H%.2f A%.2f,%.2f 0 0 1 %.2f,%.2f V%.2f '
            'A%.2f,%.2f 0 0 1 %.2f,%.2f H%.2f A%.2f,%.2f 0 0 1 %.2f,%.2f '
            'V%.2f A%.2f,%.2f 0 0 1 %.2f,%.2f Z') % (
        a + r, b, c - r, r, r, c, b + r, dd - r, r, r, c - r, dd,
        a + r, r, r, a, dd - r, b + r, r, r, a + r, b)


def build_vector():
    L = []
    add = L.append
    add('<?xml version="1.0" encoding="utf-8"?>')
    add('<!--')
    add('    HellStation 대표 캐릭터 — 전동차 앞면에 악마 뿔.')
    add('')
    add('    이 파일은 손으로 고치지 마세요. scratchpad/icon.py 가 만들어 냅니다.')
    add('    화면 안의 캐릭터(ui/character/HellFace.kt)와 같은 좌표에서 나오므로,')
    add('    한쪽만 고치면 아이콘과 앱 속 얼굴이 서로 달라집니다.')
    add('')
    add('    적응형 아이콘이라 108x108 이고, 기기가 둥글게 잘라도 뿔이 남도록')
    add('    가운데 72x72 안에만 그립니다.')
    add('-->')
    add('<vector xmlns:android="http://schemas.android.com/apk/res/android"')
    add('    android:width="108dp"')
    add('    android:height="108dp"')
    add('    android:viewportWidth="108"')
    add('    android:viewportHeight="108">')

    def p(d, color, alpha=None):
        add('    <path')
        add('        android:pathData="%s"' % d)
        if alpha is not None:
            add('        android:fillAlpha="%s"' % alpha)
        add('        android:fillColor="%s" />' % color)

    # 꼬리
    tail = to_icon(cubic((79, 66), (92, 64), (95, 74), (88, 82)))
    add('    <path')
    add('        android:pathData="%s"' % path(tail, close=False))
    add('        android:strokeWidth="2.3"')
    add('        android:strokeLineCap="round"')
    add('        android:strokeColor="%s" />' % hexof(MAGENTA))
    p(path(to_icon([(88, 80), (96, 86), (85, 90)])), hexof(MAGENTA))
    p(path(to_icon(HORN_L)), hexof(MAGENTA))
    p(path(to_icon(HORN_R)), hexof(MAGENTA))
    p(rect_path(BODY_RECT, BODY_R), hexof(BODY))
    p(rect_path(SIGN_RECT, 2), hexof(SIGN_BG))
    for led in LEDS:
        p(rect_path(led, 1), hexof(CYAN))
    p(path(to_icon(GLASS_POLY)), hexof(GLASS))
    for outer, core in EYES:
        p(path(to_icon(outer)), hexof(FLAME))
        p(path(to_icon(core)), hexof(CORE))
    p(path(to_icon(SKIRT_POLY)), hexof(SKIRT))
    for lx, ly in LAMPS:
        cx, cy = to_icon([(lx, ly)])[0]
        r = LAMP_R * 0.72
        p('M%.2f,%.2f a%.2f,%.2f 0 1,0 %.2f,0 a%.2f,%.2f 0 1,0 -%.2f,0'
          % (cx - r, cy, r, r, r * 2, r, r, r * 2), hexof(CYAN))
    add('</vector>')
    return '\n'.join(L) + '\n'


if __name__ == '__main__':
    root = sys.argv[1]
    store = os.path.join(root, 'docs', 'store')
    os.makedirs(store, exist_ok=True)

    icon = draw_png(512, 0.12, BG)
    icon.save(os.path.join(store, 'play-icon-512.png'))

    feature = Image.new('RGB', (1024, 500), BG)
    mark = draw_png(360, 0.02, BG)
    feature.paste(mark, (96, 70))
    feature.save(os.path.join(store, 'play-feature-1024x500.png'))

    with io.open(os.path.join(root, 'app', 'src', 'main', 'res', 'drawable',
                              'ic_launcher_foreground.xml'), 'w',
                 encoding='utf-8', newline='\n') as f:
        f.write(build_vector())

    print('  play-icon-512.png / play-feature-1024x500.png / ic_launcher_foreground.xml')
