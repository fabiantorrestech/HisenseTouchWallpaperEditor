"""Builds a panel test: wallpaper + white panels + minimum sampled-pixel changes to force black text.

Usage: make_force_color_test.py <input 720x1440 RGBA PNG> <output PNG>. Standard library only.
"""
import struct
import sys
import zlib
from pathlib import Path

WIDTH, HEIGHT = 720, 1440
STEP = 4
RED_THRESHOLD = 192
FORCED_PIXEL = (193, 0, 0, 255)
SAFETY_MARGIN = 200
BANDS = {"top": (0, 360), "bottom": (1080, 1440)}
WHITE_PANELS = [(44, 148, 400, 348), (0, 1065, 720, 1289)]


def read_rgba_png(path):
    data = Path(path).read_bytes()
    position, idat = 8, b""
    while position < len(data):
        length, tag = struct.unpack(">I4s", data[position : position + 8])
        body = data[position + 8 : position + 8 + length]
        if tag == b"IHDR":
            width, height, depth, color = struct.unpack(">IIBB", body[:10])
            if (width, height, depth, color) != (WIDTH, HEIGHT, 8, 6):
                raise ValueError(f"expected {WIDTH}x{HEIGHT} 8-bit RGBA, got {width}x{height} {depth} {color}")
        elif tag == b"IDAT":
            idat += body
        position += length + 12
    raw = zlib.decompress(idat)
    stride = WIDTH * 4
    rows, previous, offset = [], bytearray(stride), 0
    for _ in range(HEIGHT):
        kind = raw[offset]
        row = bytearray(raw[offset + 1 : offset + 1 + stride])
        offset += stride + 1
        for i in range(stride):
            left = row[i - 4] if i >= 4 else 0
            up = previous[i]
            corner = previous[i - 4] if i >= 4 else 0
            if kind == 1:
                row[i] = (row[i] + left) & 255
            elif kind == 2:
                row[i] = (row[i] + up) & 255
            elif kind == 3:
                row[i] = (row[i] + (left + up) // 2) & 255
            elif kind == 4:
                estimate = left + up - corner
                pa, pb, pc = abs(estimate - left), abs(estimate - up), abs(estimate - corner)
                best = left if pa <= pb and pa <= pc else up if pb <= pc else corner
                row[i] = (row[i] + best) & 255
        rows.append(row)
        previous = row
    return rows


def write_rgba_png(path, rows):
    def chunk(tag, body):
        return struct.pack(">I", len(body)) + tag + body + struct.pack(">I", zlib.crc32(tag + body))

    raw = b"".join(b"\x00" + bytes(row) for row in rows)
    header = struct.pack(">IIBBBBB", WIDTH, HEIGHT, 8, 6, 0, 0, 0)
    Path(path).write_bytes(
        b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(raw, 6)) + chunk(b"IEND", b"")
    )


def sample_points(top, bottom):
    for y in range(0, HEIGHT, STEP):
        if top < y < bottom:
            for x in range(STEP, WIDTH, STEP):
                yield x, y


def red_at(rows, x, y):
    return rows[y][x * 4]


def vote(rows, top, bottom):
    return sum(1 if red_at(rows, x, y) > RED_THRESHOLD else -1 for x, y in sample_points(top, bottom))


def paint_white_panels(rows):
    for left, top, right, bottom in WHITE_PANELS:
        for y in range(top, bottom):
            rows[y][left * 4 : right * 4] = b"\xff" * ((right - left) * 4)


def force_black_text(rows, top, bottom):
    """Flips evenly spaced dark samples until the band votes white. Returns the number of pixels changed."""
    count = vote(rows, top, bottom)
    needed = max(0, (SAFETY_MARGIN - count + 1) // 2)
    dark = [(x, y) for x, y in sample_points(top, bottom) if red_at(rows, x, y) <= RED_THRESHOLD]
    if needed > len(dark):
        raise ValueError("band cannot reach the target")
    for index in range(needed):
        x, y = dark[index * len(dark) // needed]
        rows[y][x * 4 : x * 4 + 4] = bytes(FORCED_PIXEL)
    return needed


def main():
    rows = read_rgba_png(sys.argv[1])
    paint_white_panels(rows)
    for name, (top, bottom) in BANDS.items():
        before = vote(rows, top, bottom)
        changed = force_black_text(rows, top, bottom)
        print(f"{name}: vote {before} -> {vote(rows, top, bottom)}, pixels changed {changed}")
    write_rgba_png(sys.argv[2], rows)


if __name__ == "__main__":
    main()
