from pathlib import Path


core_path = Path(".github/scripts/patch-search-results-stream-errors-core-0.24.10.py")
source = core_path.read_text()

helper_anchor = '''    return text.replace(old, new, 1)


chat_path = Path("app/src/main/java/app/xylune/chat/ui/ChatScreen.kt")
'''
helper_replacement = '''    return text.replace(old, new, 1)


def replace_first(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count < 1:
        raise SystemExit(f"{label}: expected at least one match, found {count}")
    return text.replace(old, new, 1)


chat_path = Path("app/src/main/java/app/xylune/chat/ui/ChatScreen.kt")
'''
if source.count(helper_anchor) != 1:
    raise SystemExit("replace helper anchor changed")
source = source.replace(helper_anchor, helper_replacement, 1)

label = '"timeline working source links signature",'
if source.count(label) != 1:
    raise SystemExit("timeline working source-links label changed")
label_offset = source.index(label)
call_offset = source.rfind("chat = replace_once(", 0, label_offset)
if call_offset < 0:
    raise SystemExit("timeline working source-links call missing")
source = (
    source[:call_offset]
    + source[call_offset:].replace("chat = replace_once(", "chat = replace_first(", 1)
)

namespace = {
    "__name__": "__main__",
    "__file__": str(core_path),
}
exec(compile(source, str(core_path), "exec"), namespace)
