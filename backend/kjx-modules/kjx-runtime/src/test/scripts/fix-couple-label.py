from pathlib import Path
import json
import re

plan = Path(r"d:/koujunxin_web/docs/15-无限世界文游接入开发计划.md").read_text(encoding="utf-8")
m = re.search(r"可玩示例是「([^」]+)」", plan)
if not m:
    raise SystemExit("playable example not found")
label = m.group(1)
print("label", label, [hex(ord(ch)) for ch in label if ord(ch) > 127 or ch == "\u00d7"])

wall_path = Path("backend/kjx-modules/kjx-runtime/src/main/resources/works/breakup-editor/wall.json")
wall = json.loads(wall_path.read_text(encoding="utf-8"))
wall["coupleLabel"] = label
for slide in wall["slides"]:
    if slide.get("id") == "asphyxia":
        slide["couple"] = label
wall_path.write_text(json.dumps(wall, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

works = Path("backend/kjx-modules/kjx-runtime/src/main/java/com/koujunxin/runtime/breakup/BreakupWorks.java")
text = works.read_text(encoding="utf-8")
# keep unicode escapes in Java; verify they match
escaped = "".join("\\u%04X" % ord(ch) if ord(ch) > 127 or ch == "\u00d7" else ch for ch in label)
print("java escape", escaped)
