from pathlib import Path
import re


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected one patch target for {label}, found {count}")
    return text.replace(old, new, 1)


provider_path = Path("app/src/main/java/app/xylune/chat/provider/OpenAiCompatibleProvider.kt")
provider = provider_path.read_text()
provider, count = re.subn(
    r'reasoning = delta\?\.string\("reasoning_content"\)\.orEmpty\(\),',
    'reasoning = delta.openAiCompatibleReasoningText(),',
    provider,
    count=1,
)
if count != 1:
    raise SystemExit(f"Expected one OpenAI-compatible reasoning assignment, found {count}")
provider = replace_once(
    provider,
    "    private fun combinedText(message: InputMessage, nativeAttachmentIds: Set<String>): String {",
    """    private fun JsonObject?.openAiCompatibleReasoningText(): String {
        if (this == null) return ""
        val direct = sequenceOf("reasoning", "reasoning_content", "thinking", "analysis")
            .mapNotNull(::string)
            .firstOrNull(String::isNotBlank)
        if (direct != null) return direct
        return array("reasoning_details").orEmpty().mapNotNull { element ->
            val detail = element as? JsonObject ?: return@mapNotNull null
            when (detail.string("type")) {
                "reasoning.text" -> detail.string("text")
                "reasoning.summary" -> detail.string("summary")
                else -> detail.string("text") ?: detail.string("summary")
            }
        }.filter(String::isNotBlank).joinToString("")
    }

    private fun combinedText(message: InputMessage, nativeAttachmentIds: Set<String>): String {""",
    "OpenAI-compatible reasoning helper",
)
provider_path.write_text(provider)

protocol_test_path = Path("app/src/test/java/app/xylune/chat/provider/NativeProviderProtocolTest.kt")
protocol_test = protocol_test_path.read_text()
protocol_test = replace_once(
    protocol_test,
    """    @Test
    fun anthropicPreservesThinkingSignatureAndToolUseBlocks() {""",
    """    @Test
    fun openAiCompatibleReadsVisibleReasoningAcrossProviderShapes() {
        val provider = OpenAiCompatibleProvider()
        val calls = linkedMapOf<Int, OpenAiCompatibleProvider.ToolCallAccumulator>()

        assertEquals(
            "OpenRouter reasoning",
            provider.parseChunk(
                """ + '"""' + """{"choices":[{"delta":{"reasoning":"OpenRouter reasoning"}}]}""" + '"""' + """,
                calls,
            )!!.reasoning,
        )
        assertEquals(
            "Thinking alias",
            provider.parseChunk(
                """ + '"""' + """{"choices":[{"delta":{"thinking":"Thinking alias"}}]}""" + '"""' + """,
                calls,
            )!!.reasoning,
        )
        assertEquals(
            "Structured detail",
            provider.parseChunk(
                """ + '"""' + """{"choices":[{"delta":{"reasoning_details":[{"type":"reasoning.text","text":"Structured detail"}]}}]}""" + '"""' + """,
                calls,
            )!!.reasoning,
        )
        assertEquals(
            "Summary detail",
            provider.parseChunk(
                """ + '"""' + """{"choices":[{"delta":{"reasoning_details":[{"type":"reasoning.summary","summary":"Summary detail"},{"type":"reasoning.encrypted","data":"opaque"}]}}]}""" + '"""' + """,
                calls,
            )!!.reasoning,
        )
    }

    @Test
    fun anthropicPreservesThinkingSignatureAndToolUseBlocks() {""",
    "reasoning parser regression test",
)
protocol_test_path.write_text(protocol_test)

boot_path = Path("docs/assets/js/theme-boot.js")
boot = boot_path.read_text()
palette_surfaces = """  const paletteSurfaces = {
    xylune: { dark: {}, light: {} },
    graphite: {
      dark: {
        '--background': '#111318', '--surface': '#111318', '--surface-low': '#191b20',
        '--surface-container': '#1d2025', '--on-surface': '#e2e2e9',
        '--on-surface-variant': '#c4c6d0', '--outline': '#8e9099',
        '--outline-variant': '#44474e', '--rail': '#0c0e13',
      },
      light: {
        '--background': '#f9f9ff', '--surface': '#f9f9ff', '--surface-low': '#f1f3fa',
        '--surface-container': '#ebedf4', '--on-surface': '#1a1b20',
        '--on-surface-variant': '#44474e', '--outline': '#74777f',
        '--outline-variant': '#c4c6d0', '--rail': '#ffffff',
      },
    },
    ocean: {
      dark: {
        '--background': '#0e1416', '--surface': '#0e1416', '--surface-low': '#161c1e',
        '--surface-container': '#1a2022', '--on-surface': '#dce4e6',
        '--on-surface-variant': '#bec8cb', '--outline': '#899295',
        '--outline-variant': '#3f484b', '--rail': '#091012',
      },
      light: {
        '--background': '#f4fafc', '--surface': '#f4fafc', '--surface-low': '#edf4f6',
        '--surface-container': '#e7eef0', '--on-surface': '#161d1f',
        '--on-surface-variant': '#3f484b', '--outline': '#6f797c',
        '--outline-variant': '#bec8cb', '--rail': '#ffffff',
      },
    },
    violet: {
      dark: {
        '--background': '#151218', '--surface': '#151218', '--surface-low': '#1d1a20',
        '--surface-container': '#211e24', '--on-surface': '#e7e0e8',
        '--on-surface-variant': '#cbc3cc', '--outline': '#958e96',
        '--outline-variant': '#49454d', '--rail': '#100d13',
      },
      light: {
        '--background': '#fcf8ff', '--surface': '#fcf8ff', '--surface-low': '#f5f0f7',
        '--surface-container': '#efeaf1', '--on-surface': '#1d1a20',
        '--on-surface-variant': '#49454d', '--outline': '#7a757d',
        '--outline-variant': '#cbc3cc', '--rail': '#ffffff',
      },
    },
    sunset: {
      dark: {
        '--background': '#181210', '--surface': '#181210', '--surface-low': '#211a18',
        '--surface-container': '#251e1c', '--on-surface': '#f1dfda',
        '--on-surface-variant': '#d5c2bc', '--outline': '#9e8c87',
        '--outline-variant': '#51443f', '--rail': '#120c0a',
      },
      light: {
        '--background': '#fff8f6', '--surface': '#fff8f6', '--surface-low': '#f9f1ee',
        '--surface-container': '#f3ebe8', '--on-surface': '#211a18',
        '--on-surface-variant': '#51443f', '--outline': '#83746f',
        '--outline-variant': '#d5c2bc', '--rail': '#ffffff',
      },
    },
  };

"""
boot = replace_once(
    boot,
    "  const fixedColors = (scheme, dark) => {",
    palette_surfaces + "  const fixedColors = (scheme, dark) => {",
    "palette surface table",
)
boot = replace_once(
    boot,
    """      ...basePalettes[mode],
      ...(paletteAccents[scheme]?.[mode] || paletteAccents.xylune[mode]),""",
    """      ...basePalettes[mode],
      ...(paletteSurfaces[scheme]?.[mode] || paletteSurfaces.xylune[mode]),
      ...(paletteAccents[scheme]?.[mode] || paletteAccents.xylune[mode]),""",
    "palette surface merge",
)
boot_path.write_text(boot)

css_path = Path("docs/assets/css/app-bar.css")
css = css_path.read_text()
css = replace_once(
    css,
    """  --xylune-app-bar-collapse-distance: 88px;
}""",
    """  --xylune-app-bar-collapse-distance: 88px;
  --xylune-app-bar-row-shift: 0px;
  --xylune-title-shift: 58px;
  --xylune-title-scale: 1.18;
  --xylune-bar-opacity: 0;
  --xylune-bar-shadow-alpha: 0;
}""",
    "title motion variables",
)
css = replace_once(
    css,
    """  overscroll-behavior-y: contain;
  scroll-behavior: smooth;
  scroll-padding-top: var(--xylune-app-bar-compact-height);
  /* Only this page scroller has snap points, and only the two title states
     participate. The rest of the document remains ordinary free scrolling. */
  scroll-snap-type: y proximity;
  scroll-timeline-name: --xylune-page-scroll;
  scroll-timeline-axis: block;""",
    """  overscroll-behavior-y: contain;
  scroll-behavior: auto;
  scroll-padding-top: var(--xylune-app-bar-compact-height);""",
    "free page scrolling",
)
css = replace_once(
    css,
    """  isolation: isolate;
  scroll-snap-align: start;
  scroll-snap-stop: always;
}""",
    """  isolation: isolate;
}""",
    "app bar snap removal",
)
css = replace_once(
    css,
    """  box-shadow: 0 7px 20px rgb(0 0 0 / 0%);
  content: "";
  opacity: 0;""",
    """  box-shadow: 0 7px 20px rgb(0 0 0 / var(--xylune-bar-shadow-alpha));
  content: "";
  opacity: var(--xylune-bar-opacity);""",
    "app bar surface progress",
)
css = replace_once(
    css,
    """  grid-template-columns: 56px minmax(0, 1fr) 80px;
  align-items: center;
  transform: translateY(0);""",
    """  grid-template-columns: 80px minmax(0, 1fr) 80px;
  align-items: center;
  transform: translateY(var(--xylune-app-bar-row-shift));""",
    "centered app bar row",
)
css = replace_once(
    css,
    """  text-align: start;
  text-overflow: ellipsis;
  white-space: nowrap;
  transform: translate(-40px, 58px) scale(1.18);
  transform-origin: left center;""",
    """  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
  transform: translateY(var(--xylune-title-shift)) scale(var(--xylune-title-scale));
  transform-origin: center center;""",
    "centered title mapping",
)
css = replace_once(
    css,
    """.document-title-collapse-snap {
  width: 1px;
  height: 1px;
  margin: 0;
  scroll-margin-top: var(--xylune-app-bar-compact-height);
  scroll-snap-align: start;
  scroll-snap-stop: always;
  pointer-events: none;
}""",
    """.document-title-collapse-snap {
  display: none;
  pointer-events: none;
}""",
    "title snap target removal",
)
animation_start = css.index("@supports (animation-timeline: scroll())")
mobile_start = css.index("@media (max-width: 820px)")
css = css[:animation_start] + css[mobile_start:]
reduced_start = css.find("@media (prefers-reduced-motion: reduce)")
if reduced_start >= 0:
    css = css[:reduced_start].rstrip() + "\n"
for forbidden in ("scroll-snap-type:", "animation-timeline:", "scroll-timeline-name:"):
    if forbidden in css:
        raise SystemExit(f"App-bar CSS still contains {forbidden}")
css_path.write_text(css)

site_path = Path("docs/assets/js/site.js")
site = site_path.read_text()
old_title = """  function setupTitleSettle() {
    const scroller = document.querySelector('.page-with-app-bar');
    if (!scroller) return;
    const collapseDistance = Number.parseFloat(
      getComputedStyle(root).getPropertyValue('--xylune-app-bar-collapse-distance'),
    ) || 88;
    let fallbackTimer = 0;
    let settling = false;

    const settle = () => {
      if (settling) return;
      const position = scroller.scrollTop;
      if (position <= 1 || position >= collapseDistance - 1) return;
      const target = position < collapseDistance / 2 ? 0 : collapseDistance;
      settling = true;
      scroller.scrollTo({
        top: target,
        behavior: reducedMotion.matches ? 'auto' : 'smooth',
      });
      setTimeout(() => {
        settling = false;
      }, reducedMotion.matches ? 0 : 260);
    };

    if ('onscrollend' in scroller) {
      scroller.addEventListener('scrollend', settle);
    } else {
      scroller.addEventListener('scroll', () => {
        clearTimeout(fallbackTimer);
        fallbackTimer = setTimeout(settle, 120);
      }, { passive: true });
    }
  }
"""
new_title = """  function setupTitleCollapse() {
    const scroller = document.querySelector('.page-with-app-bar');
    if (!scroller) return;
    const collapseDistance = Number.parseFloat(
      getComputedStyle(root).getPropertyValue('--xylune-app-bar-collapse-distance'),
    ) || 88;
    const expandedTitleShift = 58;
    const expandedTitleScale = 1.18;
    const supportsScrollEnd = 'onscrollend' in scroller;
    let animationFrame = 0;
    let fallbackTimer = 0;
    let releaseTimer = 0;
    let settling = false;

    const applyProgress = () => {
      animationFrame = 0;
      const progress = Math.min(1, Math.max(0, scroller.scrollTop / collapseDistance));
      scroller.style.setProperty('--xylune-app-bar-row-shift', `${collapseDistance * progress}px`);
      scroller.style.setProperty('--xylune-title-shift', `${expandedTitleShift * (1 - progress)}px`);
      scroller.style.setProperty(
        '--xylune-title-scale',
        String(expandedTitleScale - ((expandedTitleScale - 1) * progress)),
      );
      scroller.style.setProperty('--xylune-bar-opacity', String(progress));
      scroller.style.setProperty('--xylune-bar-shadow-alpha', String(0.13 * progress));
    };

    const queueProgress = () => {
      if (!animationFrame) animationFrame = requestAnimationFrame(applyProgress);
    };

    const settlePartialTitle = () => {
      applyProgress();
      if (settling) return;
      const position = scroller.scrollTop;
      if (position <= 1 || position >= collapseDistance - 1) return;
      const target = position < collapseDistance / 2 ? 0 : collapseDistance;
      settling = true;
      scroller.scrollTo({
        top: target,
        behavior: reducedMotion.matches ? 'auto' : 'smooth',
      });
      clearTimeout(releaseTimer);
      releaseTimer = setTimeout(() => {
        settling = false;
        applyProgress();
      }, reducedMotion.matches ? 0 : 320);
    };

    scroller.addEventListener('scroll', () => {
      queueProgress();
      if (!supportsScrollEnd) {
        clearTimeout(fallbackTimer);
        fallbackTimer = setTimeout(settlePartialTitle, 140);
      }
    }, { passive: true });
    if (supportsScrollEnd) scroller.addEventListener('scrollend', settlePartialTitle);
    addEventListener('resize', queueProgress, { passive: true });
    applyProgress();
  }
"""
site = replace_once(site, old_title, new_title, "cross-browser title collapse")
site = replace_once(site, "  setupTitleSettle();", "  setupTitleCollapse();", "title setup call")
site_path.write_text(site)

legal_test_path = Path("app/src/test/java/app/xylune/chat/ui/LegalWebsiteIntegrationTest.kt")
legal_test = legal_test_path.read_text()
legal_test = replace_once(
    legal_test,
    "        assertTrue(boot.contains(\"queryKeys: ['theme', 'scheme'\"))",
    """        assertTrue(boot.contains("queryKeys: ['theme', 'scheme'"))
        assertTrue(boot.contains("const paletteSurfaces ="))
        assertTrue(boot.contains("'--background': '#0e1416'"))
        assertTrue(boot.contains("'--on-surface': '#e7e0e8'"))
        assertTrue(boot.contains("'--on-surface-variant': '#51443f'"))
        assertTrue(boot.contains("...(paletteSurfaces[scheme]?.[mode]"))""",
    "full palette regression assertions",
)
old_scroll = """        assertTrue(css.contains("position: sticky"))
        assertTrue(css.contains("scroll-timeline-name: --xylune-page-scroll"))
        assertTrue(css.contains("animation-timeline: --xylune-page-scroll"))
        assertTrue(css.contains("transform: translate(-40px, 58px) scale(1.18)"))
        assertTrue(css.contains("transform: translateY(88px)"))
        assertTrue(appearance.contains("scroll-snap-type: none !important"))
        assertTrue(appearance.contains("scroll-behavior: auto !important"))
        assertTrue(appearance.contains("scroll-snap-align: none !important"))
        assertTrue(appearance.contains("display: none !important"))
        assertTrue(site.contains("function setupTitleSettle()"))
        assertTrue(site.contains("position <= 1 || position >= collapseDistance - 1"))
        assertTrue(site.contains("position < collapseDistance / 2 ? 0 : collapseDistance"))
        assertTrue(site.contains("behavior: reducedMotion.matches ? 'auto' : 'smooth'"))"""
new_scroll = """        assertTrue(css.contains("position: sticky"))
        assertTrue(css.contains("grid-template-columns: 80px minmax(0, 1fr) 80px"))
        assertTrue(css.contains("text-align: center"))
        assertTrue(css.contains("translateY(var(--xylune-title-shift)) scale(var(--xylune-title-scale))"))
        assertTrue(!css.contains("scroll-snap-type:"))
        assertTrue(!css.contains("scroll-timeline-name:"))
        assertTrue(!css.contains("animation-timeline:"))
        assertTrue(appearance.contains("scroll-snap-type: none !important"))
        assertTrue(appearance.contains("scroll-behavior: auto !important"))
        assertTrue(site.contains("function setupTitleCollapse()"))
        assertTrue(site.contains("requestAnimationFrame(applyProgress)"))
        assertTrue(site.contains("--xylune-title-shift"))
        assertTrue(site.contains("--xylune-title-scale"))
        assertTrue(site.contains("position <= 1 || position >= collapseDistance - 1"))
        assertTrue(site.contains("position < collapseDistance / 2 ? 0 : collapseDistance"))
        assertTrue(site.contains("behavior: reducedMotion.matches ? 'auto' : 'smooth'"))"""
legal_test = replace_once(legal_test, old_scroll, new_scroll, "title collapse regression assertions")
legal_test_path.write_text(legal_test)

for path in (
    Path(".github/workflows/patch-pages-reasoning-once.yml"),
    Path(".github/workflows/patch-pages-reasoning-pr.yml"),
    Path(".github/patch-pages-reasoning.trigger"),
    Path(".github/scripts/patch_pages_reasoning.py"),
):
    path.unlink(missing_ok=True)
