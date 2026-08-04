#!/usr/bin/env python3
"""Convert emotion PNGs to LVGL 9 RGB565 C arrays for wrist-gem."""

from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image

EMOTIONS = [
    "neutral",
    "happy",
    "laughing",
    "funny",
    "sad",
    "angry",
    "crying",
    "loving",
    "embarrassed",
    "surprised",
    "shocked",
    "thinking",
    "winking",
    "cool",
    "relaxed",
    "delicious",
    "kissy",
    "confident",
    "sleepy",
    "silly",
    "confused",
]

SIZE = 128


def png_to_rgb565_bytes(path: Path, swap_bytes: bool) -> bytes:
    """Encode as RGB565. Default: little-endian for LVGL (matches SPI swap_bytes flush)."""
    img = Image.open(path).convert("RGB").resize((SIZE, SIZE), Image.Resampling.LANCZOS)
    out = bytearray()
    for y in range(SIZE):
        for x in range(SIZE):
            r, g, b = img.getpixel((x, y))
            value = ((r & 0xF8) << 8) | ((g & 0xFC) << 3) | (b >> 3)
            lo = value & 0xFF
            hi = (value >> 8) & 0xFF
            if swap_bytes:
                out.append(hi)
                out.append(lo)
            else:
                out.append(lo)
                out.append(hi)
    return bytes(out)


def write_image_c(out_dir: Path, name: str, data: bytes) -> None:
    var = f"emotion_{name}"
    map_name = f"{var}_map"
    lines = [
        '#include "lvgl.h"',
        "",
        f"const LV_ATTRIBUTE_MEM_ALIGN LV_ATTRIBUTE_LARGE_CONST uint8_t {map_name}[] = {{",
    ]
    for i in range(0, len(data), 16):
        chunk = data[i : i + 16]
        hexes = ", ".join(f"0x{b:02X}" for b in chunk)
        lines.append(f"  {hexes},")
    lines += [
        "};",
        "",
        f"const lv_image_dsc_t {var} = {{",
        "  .header = {",
        "    .magic = LV_IMAGE_HEADER_MAGIC,",
        "    .cf = LV_COLOR_FORMAT_RGB565,",
        "    .flags = 0,",
        f"    .w = {SIZE},",
        f"    .h = {SIZE},",
        f"    .stride = {SIZE * 2},",
        "    .reserved_2 = 0,",
        "  },",
        f"  .data_size = {SIZE * SIZE * 2},",
        f"  .data = {map_name},",
        "  .reserved = NULL,",
        "};",
        "",
    ]
    (out_dir / f"{var}.c").write_text("\n".join(lines), encoding="utf-8")


def write_api(out_dir: Path) -> None:
    header = [
        "#pragma once",
        "",
        "#include <lvgl.h>",
        "",
        "#ifdef __cplusplus",
        'extern "C" {',
        "#endif",
        "",
        "const lv_image_dsc_t* FindEmotionImage(const char* emotion);",
        "",
    ]
    for name in EMOTIONS:
        header.append(f"extern const lv_image_dsc_t emotion_{name};")
    header += [
        "",
        "#ifdef __cplusplus",
        "}",
        "#endif",
        "",
    ]
    (out_dir / "emotion_images.h").write_text("\n".join(header), encoding="utf-8")

    source = [
        '#include "emotion_images.h"',
        "",
        "#include <string.h>",
        "",
        "typedef struct {",
        "  const char* name;",
        "  const lv_image_dsc_t* img;",
        "} emotion_image_entry_t;",
        "",
        "static const emotion_image_entry_t kEmotionImages[] = {",
    ]
    for name in EMOTIONS:
        source.append(f'  {{"{name}", &emotion_{name}}},')
    source += [
        "};",
        "",
        "const lv_image_dsc_t* FindEmotionImage(const char* emotion) {",
        "  if (emotion == NULL) {",
        "    return NULL;",
        "  }",
        "  for (unsigned i = 0; i < sizeof(kEmotionImages) / sizeof(kEmotionImages[0]); ++i) {",
        "    if (strcmp(emotion, kEmotionImages[i].name) == 0) {",
        "      return kEmotionImages[i].img;",
        "    }",
        "  }",
        "  return NULL;",
        "}",
        "",
    ]
    (out_dir / "emotion_images.c").write_text("\n".join(source), encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--input",
        type=Path,
        default=Path("main/boards/esp32-s3-wrist-gem/emotions/png"),
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("main/boards/esp32-s3-wrist-gem/emotions/generated"),
    )
    parser.add_argument(
        "--swap-bytes",
        action="store_true",
        help="Store RGB565 with swapped bytes (try if colors look wrong on device)",
    )
    args = parser.parse_args()

    args.output.mkdir(parents=True, exist_ok=True)
    missing = [n for n in EMOTIONS if not (args.input / f"{n}.png").exists()]
    if missing:
        raise SystemExit(f"Missing PNGs: {', '.join(missing)}")

    for name in EMOTIONS:
        data = png_to_rgb565_bytes(args.input / f"{name}.png", args.swap_bytes)
        write_image_c(args.output, name, data)
        print(f"wrote emotion_{name}.c ({len(data)} bytes)")

    write_api(args.output)
    print(f"wrote emotion_images.h / emotion_images.c -> {args.output}")


if __name__ == "__main__":
    main()
