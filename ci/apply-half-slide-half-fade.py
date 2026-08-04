#!/usr/bin/env python3
from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one match, found {count}: {old[:120]!r}")
    file.write_text(text.replace(old, new, 1))


predictive = "app/src/main/java/app/xylune/chat/ui/PredictiveNavigation.kt"
replace_once(
    predictive,
    """internal fun pageSlideOffset(widthPx: Float, progress: Float): Float =
    widthPx.coerceAtLeast(0f) * progress.coerceIn(0f, 1f)

""",
    """private const val NavigationSlideFraction = 0.5f

internal fun pageSlideOffset(widthPx: Float, progress: Float): Float =
    widthPx.coerceAtLeast(0f) * NavigationSlideFraction * progress.coerceIn(0f, 1f)

internal fun navigationSourceAlpha(progress: Float): Float =
    1f - progress.coerceIn(0f, 1f)

internal fun navigationDestinationAlpha(progress: Float): Float =
    progress.coerceIn(0f, 1f)

""",
)
replace_once(
    predictive,
    """                        .graphicsLayer {
                            clip = true
                            if (isParked) {
                                alpha = 0f
                                compositingStrategy = CompositingStrategy.ModulateAlpha
                            }
                            val p = progress.value.coerceIn(0f, 1f)
                            when (mode) {
                                NavigationTransitionMode.PREDICTIVE -> {
                                    val visualProgress = predictiveBackVisualProgress(p)
                                    val slide = pageSlideOffset(widthPx, visualProgress)
                                    when {
                                        isSource -> {
                                            // Keep the two opaque pages edge-to-edge throughout the
                                            // gesture. At commit the source reaches a complete
                                            // off-screen position instead of disappearing after a
                                            // short preview translation.
                                            translationX = predictiveDirection * slide
                                        }
                                        isDestination -> {
                                            translationX = -predictiveDirection * (widthPx - slide)
                                        }
                                    }
                                }
                                NavigationTransitionMode.ORDINARY -> {
                                    val slide = pageSlideOffset(widthPx, p)
                                    if (transitionForward) {
                                        when {
                                            isSource -> translationX = -slide
                                            isDestination -> translationX = widthPx - slide
                                        }
                                    } else {
                                        when {
                                            isSource -> translationX = slide
                                            isDestination -> translationX = -(widthPx - slide)
                                        }
                                    }
                                }
                                NavigationTransitionMode.IDLE -> Unit
                            }
                        },
""",
    """                        .graphicsLayer {
                            clip = true
                            translationX = 0f
                            alpha = 1f
                            if (isParked) {
                                alpha = 0f
                                compositingStrategy = CompositingStrategy.ModulateAlpha
                            }
                            val p = progress.value.coerceIn(0f, 1f)
                            when (mode) {
                                NavigationTransitionMode.PREDICTIVE -> {
                                    val visualProgress = predictiveBackVisualProgress(p)
                                    val slide = pageSlideOffset(widthPx, visualProgress)
                                    val maxSlide = pageSlideOffset(widthPx, 1f)
                                    when {
                                        isSource -> {
                                            translationX = predictiveDirection * slide
                                            alpha = navigationSourceAlpha(visualProgress)
                                            compositingStrategy = CompositingStrategy.ModulateAlpha
                                        }
                                        isDestination -> {
                                            translationX = -predictiveDirection * (maxSlide - slide)
                                            alpha = navigationDestinationAlpha(visualProgress)
                                            compositingStrategy = CompositingStrategy.ModulateAlpha
                                        }
                                    }
                                }
                                NavigationTransitionMode.ORDINARY -> {
                                    val slide = pageSlideOffset(widthPx, p)
                                    val maxSlide = pageSlideOffset(widthPx, 1f)
                                    if (transitionForward) {
                                        when {
                                            isSource -> {
                                                translationX = -slide
                                                alpha = navigationSourceAlpha(p)
                                                compositingStrategy = CompositingStrategy.ModulateAlpha
                                            }
                                            isDestination -> {
                                                translationX = maxSlide - slide
                                                alpha = navigationDestinationAlpha(p)
                                                compositingStrategy = CompositingStrategy.ModulateAlpha
                                            }
                                        }
                                    } else {
                                        when {
                                            isSource -> {
                                                translationX = slide
                                                alpha = navigationSourceAlpha(p)
                                                compositingStrategy = CompositingStrategy.ModulateAlpha
                                            }
                                            isDestination -> {
                                                translationX = -(maxSlide - slide)
                                                alpha = navigationDestinationAlpha(p)
                                                compositingStrategy = CompositingStrategy.ModulateAlpha
                                            }
                                        }
                                    }
                                }
                                NavigationTransitionMode.IDLE -> Unit
                            }
                        },
""",
)

math_test = "app/src/test/java/app/xylune/chat/ui/PredictiveNavigationMathTest.kt"
replace_once(
    math_test,
    """    fun pageSlideTravelsTheEntireViewportBeforeTheSourceIsRetired() {
        assertEquals(0f, pageSlideOffset(1080f, 0f), .0001f)
        assertEquals(540f, pageSlideOffset(1080f, .5f), .0001f)
        assertEquals(1080f, pageSlideOffset(1080f, 1f), .0001f)
        assertEquals(1080f, pageSlideOffset(1080f, 2f), .0001f)
    }

""",
    """    fun pageSlideTravelsHalfTheViewportWhileFadeFinishesTheTransition() {
        assertEquals(0f, pageSlideOffset(1080f, 0f), .0001f)
        assertEquals(270f, pageSlideOffset(1080f, .5f), .0001f)
        assertEquals(540f, pageSlideOffset(1080f, 1f), .0001f)
        assertEquals(540f, pageSlideOffset(1080f, 2f), .0001f)
    }

    @Test
    fun pageOpacityCrossfadesCompletelyWithoutAnEndCut() {
        assertEquals(1f, navigationSourceAlpha(0f), .0001f)
        assertEquals(.5f, navigationSourceAlpha(.5f), .0001f)
        assertEquals(0f, navigationSourceAlpha(1f), .0001f)
        assertEquals(0f, navigationDestinationAlpha(0f), .0001f)
        assertEquals(.5f, navigationDestinationAlpha(.5f), .0001f)
        assertEquals(1f, navigationDestinationAlpha(1f), .0001f)
    }

""",
)

build = "app/build.gradle.kts"
replace_once(build, 'versionCode = 182', 'versionCode = 183')
replace_once(build, 'versionName = "0.23.13"', 'versionName = "0.23.14"')

changelog = Path("CHANGELOG.md")
text = changelog.read_text()
entry = """## 0.23.14 — 2026-08-04

- Replace the full-width page travel with a balanced half-slide, half-fade transition for ordinary and predictive Back.
- Keep the outgoing page alive until it is fully transparent and the destination is fully opaque, so the animation still finishes cleanly without the previous cut-and-vanish behavior.
- Apply the same motion model to forward navigation, toolbar Back, predictive Back commit, and predictive Back cancellation.

"""
if not text.startswith("## 0.23.13"):
    raise SystemExit("CHANGELOG.md did not start at 0.23.13")
changelog.write_text(entry + text)

Path("docs/releases/RELEASE_NOTES_0.23.14.md").write_text(
    """# Xylune 0.23.14

## Back motion: half slide, half fade

Page transitions now travel only half the viewport while crossfading between the source and destination. This keeps the motion visible and complete without making the entire screen feel as though it is being dragged away.

The outgoing page remains composed until it reaches zero opacity, and the destination reaches full opacity before the source is retired. Predictive Back, button Back, toolbar Back, forward navigation, and cancelled gestures use the same transition model.
"""
)

source = Path(predictive).read_text()
assert "NavigationSlideFraction = 0.5f" in source
assert "navigationSourceAlpha" in source
assert "navigationDestinationAlpha" in source
assert "widthPx - slide" not in source
assert "maxSlide - slide" in source
assert 'versionName = "0.23.14"' in Path(build).read_text()
print("Applied half-slide half-fade navigation for Xylune 0.23.14")
