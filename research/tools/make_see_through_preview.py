"""Draws the sample e-reader page that see-through projects use as their editor preview.

The real background is whatever is on screen at lock time, so this only stands in for it while
designing zones. Run from the repo root:

    python research/tools/make_see_through_preview.py
"""
import random

from PIL import Image, ImageDraw, ImageFont

WIDTH, HEIGHT = 720, 1440
MARGIN = 56
LINE_GAP = 44
OUT = "app/src/main/assets/see_through_preview.png"


def main():
    random.seed(7)  # Same page every run.
    page = Image.new("L", (WIDTH, HEIGHT), 255)
    draw = ImageDraw.Draw(page)
    small = ImageFont.load_default(size=22)
    label = ImageFont.load_default(size=26)

    # Status bar.
    draw.text((20, 12), "10:49", fill=0, font=small)
    draw.text((WIDTH - 80, 12), "86%", fill=0, font=small)
    draw.line((0, 48, WIDTH, 48), fill=200, width=1)

    # Chapter title, then paragraphs drawn as gray text-line bars (no font needed).
    draw.rectangle((MARGIN, 120, MARGIN + 300, 150), fill=40)
    y = 200
    while y < HEIGHT - 160:
        lines = random.randint(4, 8)
        for i in range(lines):
            if y >= HEIGHT - 160:
                break
            indent = 40 if i == 0 else 0
            end = WIDTH - MARGIN if i < lines - 1 else random.randint(MARGIN + 160, WIDTH - MARGIN - 80)
            x = MARGIN + indent
            while x < end - 30:
                word = random.randint(28, 110)
                draw.rectangle((x, y, min(x + word, end), y + 14), fill=random.choice((60, 80, 100)))
                x += word + 14
            y += LINE_GAP
        y += LINE_GAP // 2

    # Page footer and the reminder that this is only a preview.
    draw.text((WIDTH // 2 - 12, HEIGHT - 120), "42", fill=90, font=small)
    # Mid-screen, which no lock screen item covers and the text color vote ignores.
    caption = "Preview: your screen appears here"
    box = draw.textbbox((0, 0), caption, font=label)
    cx = (WIDTH - (box[2] - box[0])) // 2
    cy = HEIGHT // 2 - 22
    draw.rectangle((cx - 16, cy - 8, cx + box[2] - box[0] + 16, cy + 36), fill=255, outline=0, width=2)
    draw.text((cx, cy - 2), caption, fill=0, font=label)

    page.save(OUT, optimize=True)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
