#!/usr/bin/env python3
from __future__ import annotations

import re
from pathlib import Path

patcher = Path("ci/apply-title-collapse-hotfix.py")
source = patcher.read_text()

end_marker = '    "latest boundary",\n)'
end = source.index(end_marker) + len(end_marker)
start = source.rfind("\nchat = replace_once(", 0, end)
if start < 0:
    raise RuntimeError("Could not locate the brittle latest-boundary replacement")
start += 1

replacement = r'''latest_pattern = re.compile(
    r"(?m)^(?P<indent>[ \\t]*)listScope\\.launch \\{ snapChatToBottom\\(messageListState, paging\\.itemCount - 1, messageBottomInsetPx\\) \\}$"
)
latest_matches = list(latest_pattern.finditer(chat))
if len(latest_matches) != 1:
    raise RuntimeError(
        f"latest boundary: expected exactly one snapChatToBottom call, found {len(latest_matches)}"
    )
latest_indent = latest_matches[0].group("indent")
latest_replacement = "\\n".join(
    [
        f"{latest_indent}val limit = topAppBarState.heightOffsetLimit",
        f"{latest_indent}if (limit < 0f) {{",
        f"{latest_indent}    topAppBarState.heightOffset = limit",
        f"{latest_indent}    topAppBarState.contentOffset = limit",
        f"{latest_indent}}}",
        f"{latest_indent}listScope.launch {{ snapChatToBottom(messageListState, paging.itemCount - 1, messageBottomInsetPx) }}",
    ]
)
chat = latest_pattern.sub(lambda _: latest_replacement, chat, count=1)'''

source = source[:start] + replacement + source[end:]
exec(
    compile(source, str(patcher), "exec"),
    {"__name__": "__main__", "__file__": str(patcher), "re": re},
)
