from pathlib import Path

candidates = [
    Path(r"D:\分手结局修改器-v2-给产品"),
    Path("D:/分手结局修改器-v2-给产品"),
]
for p in Path("D:/").iterdir():
    name = p.name
    if "v2" in name or "修改" in name or "结局" in name:
        print("FOUND", repr(name), p.exists(), (p / "index.html").exists())
        candidates.append(p)

for p in candidates:
    html = p / "index.html"
    print("try", p, html.exists())
    if html.exists():
        text = html.read_text(encoding="utf-8")
        print("chars", len(text))
        out = Path("backend/kjx-modules/kjx-runtime/src/test/scripts/original-index-head.txt")
        out.write_text("\n".join(text.splitlines()[:120]), encoding="utf-8")
        ids = [line.strip() for line in text.splitlines() if 'id="screen-' in line or "id='screen-" in line]
        print("\n".join(ids[:40]))
        break
