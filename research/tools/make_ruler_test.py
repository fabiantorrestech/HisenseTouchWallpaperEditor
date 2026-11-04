"""Builds a calibration wallpaper: black top half, white bottom half, numbered ruler along the bottom edge.

A black top and white bottom give the bands opposite votes, so any text's color shows which band controls it.
Usage: make_ruler_test.py <output PNG>. Standard library only.
"""
import struct
import sys
import zlib
from pathlib import Path

WIDTH, HEIGHT = 720, 1440
SPLIT_Y = 720
RULER_TOP = 1320
LINE_GRAY = 200
LABEL_GRAY = 0
SCALE = 3
GLYPHS = {
    "0": ["111", "101", "101", "101", "111"],
    "1": ["010", "110", "010", "010", "111"],
    "2": ["111", "001", "111", "100", "111"],
    "3": ["111", "001", "111", "001", "111"],
    "4": ["101", "101", "111", "001", "001"],
    "5": ["111", "100", "111", "001", "111"],
    "6": ["111", "100", "111", "101", "111"],
    "7": ["111", "001", "010", "010", "010"],
    "8": ["111", "101", "111", "101", "111"],
    "9": ["111", "101", "111", "001", "111"],
}


def build_pixels():
    pixels = [bytearray([0 if y < SPLIT_Y else 255]) * WIDTH for y in range(HEIGHT)]
    for y in range(RULER_TOP, HEIGHT, 20):
        pixels[y][:] = bytes([LINE_GRAY]) * WIDTH
    for x in range(0, WIDTH, 40):
        for y in range(RULER_TOP, HEIGHT):
            pixels[y][x] = LINE_GRAY
    for x in range(40, WIDTH, 80):
        draw_label(pixels, str(x), x + 3, RULER_TOP - 20)
    for y in range(RULER_TOP, HEIGHT, 40):
        draw_label(pixels, str(y), 345, y + 2)
    return pixels


def draw_label(pixels, text, left, top):
    for index, character in enumerate(text):
        for row, bits in enumerate(GLYPHS[character]):
            for column, bit in enumerate(bits):
                if bit == "1":
                    for dy in range(SCALE):
                        x0 = left + (index * 4 + column) * SCALE
                        pixels[top + row * SCALE + dy][x0 : x0 + SCALE] = bytes([LABEL_GRAY]) * SCALE


def write_gray_as_rgb_png(path, pixels):
    def chunk(tag, body):
        return struct.pack(">I", len(body)) + tag + body + struct.pack(">I", zlib.crc32(tag + body))

    raw = bytearray()
    for row in pixels:
        raw.append(0)
        for value in row:
            raw.extend((value, value, value))
    header = struct.pack(">IIBBBBB", WIDTH, HEIGHT, 8, 2, 0, 0, 0)
    Path(path).write_bytes(
        b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(bytes(raw), 6)) + chunk(b"IEND", b"")
    )


if __name__ == "__main__":
    write_gray_as_rgb_png(sys.argv[1], build_pixels())
