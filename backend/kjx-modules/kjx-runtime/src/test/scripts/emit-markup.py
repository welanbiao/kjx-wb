from pathlib import Path
import json

html = Path(r"d:\koujunxin_web\apps\web\app\play\breakup-editor\vendor\original-screens.html").read_text(
    encoding="utf-8"
)
start = html.index("<div id=")
start = html.index("app", start)
# find the actual app div
marker = '<div id="app">'
start = html.index(marker)
end = html.index("</body>")
body = html[start:end].strip()
out = Path(r"d:\koujunxin_web\apps\web\app\play\breakup-editor\originalMarkup.ts")
out.write_text("export const ORIGINAL_MARKUP = " + json.dumps(body, ensure_ascii=False) + " as const;\n", encoding="utf-8")
print("wrote", out, "chars", out.stat().st_size)
