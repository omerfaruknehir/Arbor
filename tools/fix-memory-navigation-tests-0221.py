from pathlib import Path

path = Path("app/src/test/java/app/arbor/chat/ui/MemoryNavigationBehaviorTest.kt")
text = path.read_text()
replacements = {
    "assertEquals(0f, settingsTopBarHeightOffset(0, -120f))": "assertEquals(0f, settingsTopBarHeightOffset(0, -120f), 0.001f)",
    "assertEquals(-40f, settingsTopBarHeightOffset(40, -120f))": "assertEquals(-40f, settingsTopBarHeightOffset(40, -120f), 0.001f)",
    "assertEquals(-120f, settingsTopBarHeightOffset(500, -120f))": "assertEquals(-120f, settingsTopBarHeightOffset(500, -120f), 0.001f)",
    "assertEquals(0f, chatTopBarHeightOffsetForScroll(0, 20, 56, 176, -100f))": "assertEquals(0f, chatTopBarHeightOffsetForScroll(0, 20, 56, 176, -100f), 0.001f)",
    "assertEquals(-100f, chatTopBarHeightOffsetForScroll(1, 0, 56, 176, -100f))": "assertEquals(-100f, chatTopBarHeightOffsetForScroll(1, 0, 56, 176, -100f), 0.001f)",
    "assertEquals(300f, DrawerPhysics.predictiveBackOffset(300f, 0f))": "assertEquals(300f, DrawerPhysics.predictiveBackOffset(300f, 0f), 0.001f)",
    "assertEquals(150f, DrawerPhysics.predictiveBackOffset(300f, .5f))": "assertEquals(150f, DrawerPhysics.predictiveBackOffset(300f, .5f), 0.001f)",
    "assertEquals(0f, DrawerPhysics.predictiveBackOffset(300f, 1f))": "assertEquals(0f, DrawerPhysics.predictiveBackOffset(300f, 1f), 0.001f)",
}
for old, new in replacements.items():
    if text.count(old) != 1:
        raise SystemExit(f"Expected one assertion: {old}")
    text = text.replace(old, new, 1)
path.write_text(text)
