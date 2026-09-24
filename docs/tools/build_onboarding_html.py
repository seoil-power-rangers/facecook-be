#!/usr/bin/env python3
"""docs/백엔드_코드_온보딩.md를 한 장짜리 HTML(docs/백엔드_코드_온보딩.html)로 만든다.

md가 원본이다. HTML은 md 원문을 그대로 품고, 브라우저에서 marked(마크다운)와
mermaid(흐름 그림)로 그린다. 그래서 md를 고친 뒤 이 스크립트만 다시 돌리면 두 문서가
항상 같은 내용이다. 표준 라이브러리만 쓴다.

    python3 docs/tools/build_onboarding_html.py
"""
import html
from pathlib import Path

DOCS = Path(__file__).resolve().parent.parent
SOURCE = DOCS / "백엔드_코드_온보딩.md"
TARGET = DOCS / "백엔드_코드_온보딩.html"

TEMPLATE = """<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>백엔드 코드 온보딩</title>
<!-- 이 파일은 docs/tools/build_onboarding_html.py가 만든다. 직접 고치지 말고 md를 고친 뒤 다시 만든다. -->
<style>
  :root {
    --bg: #ffffff; --fg: #1f2328; --muted: #59636e; --border: #d1d9e0;
    --code-bg: #f6f8fa; --accent: #d9485f; --side-bg: #fafafa; --row: #f6f8fa;
  }
  @media (prefers-color-scheme: dark) {
    :root {
      --bg: #0d1117; --fg: #e6edf3; --muted: #9198a1; --border: #3d444d;
      --code-bg: #161b22; --accent: #ff7b8f; --side-bg: #11151b; --row: #161b22;
    }
  }
  * { box-sizing: border-box; }
  body { margin: 0; background: var(--bg); color: var(--fg);
         font: 16px/1.7 -apple-system, BlinkMacSystemFont, "Apple SD Gothic Neo", "Noto Sans KR", sans-serif; }
  .layout { display: flex; min-height: 100vh; }
  nav { position: sticky; top: 0; height: 100vh; overflow-y: auto; width: 280px; flex-shrink: 0;
        padding: 20px 16px; border-right: 1px solid var(--border); background: var(--side-bg); font-size: 14px; }
  nav .title { font-weight: 700; margin-bottom: 12px; }
  nav a { display: block; color: var(--muted); text-decoration: none; padding: 3px 0; }
  nav a:hover, nav a.active { color: var(--accent); }
  nav a.h3 { padding-left: 14px; font-size: 13px; }
  main { flex: 1; min-width: 0; max-width: 980px; padding: 24px 40px 80px; }
  h1, h2, h3, h4 { line-height: 1.35; scroll-margin-top: 16px; }
  h1 { font-size: 30px; }
  h2 { font-size: 24px; margin-top: 48px; padding-bottom: 6px; border-bottom: 1px solid var(--border); }
  h3 { font-size: 19px; margin-top: 32px; }
  h4 { font-size: 16px; margin-top: 24px; }
  a { color: var(--accent); }
  code { background: var(--code-bg); padding: 1px 5px; border-radius: 4px; font-size: 0.9em;
         font-family: ui-monospace, SFMono-Regular, Menlo, monospace; overflow-wrap: anywhere; }
  pre { background: var(--code-bg); padding: 14px 16px; border-radius: 8px; overflow-x: auto; }
  pre code { background: none; padding: 0; overflow-wrap: normal; }
  .table-wrap { overflow-x: auto; }
  table { border-collapse: collapse; margin: 12px 0; font-size: 14px; }
  th, td { border: 1px solid var(--border); padding: 6px 10px; vertical-align: top; text-align: left; }
  tr:nth-child(even) td { background: var(--row); }
  hr { border: 0; border-top: 1px solid var(--border); margin: 32px 0; }
  .mermaid { margin: 16px 0; overflow-x: auto; }
  #fallback { white-space: pre-wrap; }
  #menu-toggle { display: none; }
  @media (max-width: 860px) {
    .layout { display: block; }
    nav { position: fixed; z-index: 10; left: 0; top: 0; transform: translateX(-100%); transition: transform .2s; }
    nav.open { transform: none; }
    #menu-toggle { display: block; position: fixed; right: 16px; bottom: 16px; z-index: 11;
                   border: 1px solid var(--border); background: var(--bg); color: var(--fg);
                   border-radius: 20px; padding: 8px 14px; }
    main { padding: 16px 16px 80px; }
  }
</style>
</head>
<body>
<div class="layout">
  <nav id="toc"><div class="title">목차</div></nav>
  <main id="content"><pre id="fallback">__ESCAPED__</pre></main>
</div>
<button id="menu-toggle" type="button">목차</button>
<script type="text/markdown" id="source">__RAW__</script>
<script src="https://cdn.jsdelivr.net/npm/marked@12.0.2/marked.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/mermaid@11.4.1/dist/mermaid.min.js"></script>
<script>
(function () {
  if (!window.marked) return; // 인터넷이 없으면 위의 원문(fallback)이 그대로 보인다.
  var content = document.getElementById("content");
  var source = document.getElementById("source").textContent.replace(/<\\\\\\/script/g, "</script");
  content.innerHTML = marked.parse(source);

  // 표가 화면보다 넓으면 표만 가로로 스크롤되게 감싼다.
  content.querySelectorAll("table").forEach(function (table) {
    var wrap = document.createElement("div");
    wrap.className = "table-wrap";
    table.parentNode.insertBefore(wrap, table);
    wrap.appendChild(table);
  });

  // 제목에 id를 붙이고 왼쪽 목차를 만든다.
  var toc = document.getElementById("toc");
  var used = {};
  content.querySelectorAll("h2, h3").forEach(function (h) {
    var base = h.textContent.trim().replace(/[^\\w가-힣]+/g, "-").replace(/^-|-$/g, "") || "section";
    var id = base, n = 1;
    while (used[id]) id = base + "-" + (++n);
    used[id] = true;
    h.id = id;
    var a = document.createElement("a");
    a.href = "#" + id;
    a.textContent = h.textContent;
    a.className = h.tagName.toLowerCase();
    toc.appendChild(a);
  });

  // md 안의 상대 링크(./API명세.md 등)는 같은 docs 폴더의 파일을 가리킨다.
  content.querySelectorAll("a[href$='.md']").forEach(function (a) { a.target = "_blank"; });

  // ```mermaid 코드 블록을 그림으로 바꾼다.
  if (window.mermaid) {
    content.querySelectorAll("pre > code.language-mermaid").forEach(function (code) {
      var div = document.createElement("div");
      div.className = "mermaid";
      div.textContent = code.textContent;
      code.parentNode.replaceWith(div);
    });
    var dark = window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches;
    // 그림을 본문 폭에 억지로 줄이면 글자가 읽기 어려워진다. 원래 크기로 그리고 넓으면 그림만 옆으로 스크롤한다.
    mermaid.initialize({
      startOnLoad: false, theme: dark ? "dark" : "default", securityLevel: "strict",
      flowchart: { useMaxWidth: false }, sequence: { useMaxWidth: false }, er: { useMaxWidth: false }
    });
    mermaid.run({ querySelector: ".mermaid" });
  }

  // 지금 보고 있는 절을 목차에서 강조한다.
  var links = Array.prototype.slice.call(toc.querySelectorAll("a"));
  var headings = links.map(function (a) { return document.getElementById(a.getAttribute("href").slice(1)); });
  window.addEventListener("scroll", function () {
    var current = 0;
    headings.forEach(function (h, i) { if (h.getBoundingClientRect().top < 80) current = i; });
    links.forEach(function (a, i) { a.classList.toggle("active", i === current); });
  }, { passive: true });

  var nav = document.getElementById("toc");
  document.getElementById("menu-toggle").addEventListener("click", function () { nav.classList.toggle("open"); });
  links.forEach(function (a) { a.addEventListener("click", function () { nav.classList.remove("open"); }); });
})();
</script>
</body>
</html>
"""


def main() -> None:
    markdown = SOURCE.read_text(encoding="utf-8")
    # <script> 안에 넣으므로 원문의 "</script"가 태그를 닫지 않게 바꿔 둔다(화면에서는 되돌린다).
    raw = markdown.replace("</script", "<\\/script")
    page = TEMPLATE.replace("__RAW__", raw).replace("__ESCAPED__", html.escape(markdown))
    TARGET.write_text(page, encoding="utf-8")
    print(f"만들었다: {TARGET.relative_to(DOCS.parent)}")


if __name__ == "__main__":
    main()
