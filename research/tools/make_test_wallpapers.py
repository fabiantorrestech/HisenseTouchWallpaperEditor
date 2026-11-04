"""Generates 720x1440 RGB test PNGs for the lock screen font rule. Standard library only."""
import struct
import sys
import zlib
from pathlib import Path

WIDTH, HEIGHT = 720, 1440
WHITE, BLACK = (255, 255, 255), (0, 0, 0)


def is_sampled(x, y):
    return x % 4 == 0 and y % 4 == 0


def write_png(path, pixel_at):
    rows = bytearray()
    for y in range(HEIGHT):
        rows.append(0)
        for x in range(WIDTH):
            rows.extend(pixel_at(x, y))

    def chunk(tag, data):
        body = tag + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body))

    header = struct.pack(">IIBBBBB", WIDTH, HEIGHT, 8, 2, 0, 0, 0)
    path.write_bytes(
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", header)
        + chunk(b"IDAT", zlib.compress(bytes(rows), 6))
        + chunk(b"IEND", b"")
    )


CASES = {
    "white": lambda x, y: WHITE,
    "black": lambda x, y: BLACK,
    "gray192": lambda x, y: (192, 192, 192),
    "gray193": lambda x, y: (193, 193, 193),
    "red": lambda x, y: (255, 0, 0),
    "cyan": lambda x, y: (0, 255, 255),
    "black_grid_red193": lambda x, y: (193, 0, 0) if is_sampled(x, y) else BLACK,
    "white_grid_r192": lambda x, y: (192, 255, 255) if is_sampled(x, y) else WHITE,
    "middle_white_only": lambda x, y: WHITE if 360 <= y < 1080 else BLACK,
    "top_white_bottom_black": lambda x, y: WHITE if y < 720 else BLACK,
    "top_black_bottom_white": lambda x, y: BLACK if y < 720 else WHITE,
}

if __name__ == "__main__":
    out = Path(sys.argv[1])
    out.mkdir(parents=True, exist_ok=True)
    for name, pixel_at in CASES.items():
        write_png(out / f"{name}.png", pixel_at)
        print(name)
