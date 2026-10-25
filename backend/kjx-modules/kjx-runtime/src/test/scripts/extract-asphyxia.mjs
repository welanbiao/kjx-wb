import fs from "node:fs";
import path from "node:path";
import vm from "node:vm";

const srcDir = "D:\\分手结局修改器-v2-给产品";
const outDir = path.resolve("backend/kjx-modules/kjx-runtime/src/main/resources/works/breakup-editor");
fs.mkdirSync(path.join(outDir, "server"), { recursive: true });
fs.mkdirSync(path.join(outDir, "cdn", "css"), { recursive: true });

const src = fs.readFileSync(path.join(srcDir, "js", "data", "asphyxia.js"), "utf8");
const context = { globalThis: {} };
context.globalThis = context;
vm.createContext(context);
vm.runInContext(src, context);
const couple = context.COUPLE_ASPHYXIA;
if (!couple) {
  throw new Error("COUPLE_ASPHYXIA missing");
}

const demo = couple.demoBranches || {};
delete couple.demoBranches;

function branchToJson(id, branch) {
  if (!branch) return null;
  const out = { messageId: id };
  if (branch.when instanceof RegExp) {
    out.when = branch.when.source;
  }
  out.tone = branch.tone || "";
  out.useOriginal = !!branch.useOriginal;
  out.from = branch.from;
  out.next = branch.next;
  out.nar = branch.nar || "";
  out.chat = branch.chat || [];
  out.narrs = branch.narrs || [];
  return out;
}

const trial = {
  uncovered: "演示模式暂未覆盖这句改写",
  lines: [
    {
      messageId: "n7g",
      nodeId: "n7",
      soft: branchToJson("n7g", demo.n7g?.soft),
      hard: branchToJson("n7g", demo.n7g?.hard),
    },
    {
      messageId: "n15g",
      nodeId: "n15",
      soft: branchToJson("n15g", demo.n15g?.soft),
      hard: branchToJson("n15g", demo.n15g?.hard),
    },
  ],
};

fs.writeFileSync(path.join(outDir, "asphyxia.json"), JSON.stringify(couple, null, 2), "utf8");
fs.writeFileSync(path.join(outDir, "server", "trial-script.json"), JSON.stringify(trial, null, 2), "utf8");
console.log("wrote", couple.a?.name, couple.b?.name, "trial lines", trial.lines.length);
console.log(
  "male name cps",
  [...couple.b.name].map((ch) => "U+" + ch.codePointAt(0).toString(16).toUpperCase()),
);
