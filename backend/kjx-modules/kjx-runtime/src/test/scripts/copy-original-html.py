from pathlib import Path
import re

src = None
for p in Path("D:/").iterdir():
    if (p / "index.html").exists() and (p / "css").exists() and (p / "js").exists():
        src = p / "index.html"
        break
if src is None:
    raise SystemExit("original index.html not found")

text = src.read_text(encoding="utf-8")
# drop scripts
text = re.sub(r"<script[\s\S]*?</script>", "", text, flags=re.I)
out = Path(r"d:\koujunxin_web\apps\web\app\play\breakup-editor\vendor\original-screens.html")
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(text, encoding="utf-8")
print("wrote", out, "chars", len(text))
