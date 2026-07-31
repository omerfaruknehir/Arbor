from pathlib import Path
import ast
import base64
import zlib

loader_path = Path("scripts/apply_setup_status_fix_0_20_22.py")
loader_tree = ast.parse(loader_path.read_text())
exec_call = loader_tree.body[1].value
compile_call = exec_call.args[0]
decompress_call = compile_call.args[0]
decode_call = decompress_call.args[0]
payload = ast.literal_eval(decode_call.args[0])
source = zlib.decompress(base64.b85decode(payload)).decode()

old = 'changelog = replace_once(changelog, "# Changelog\\n\\n", "# Changelog\\n\\n" + entry, "changelog entry")\n'
new = '''header = "# Changelog\\n\\n"
if not changelog.startswith(header):
    raise RuntimeError("changelog entry: expected changelog header at file start")
changelog = header + entry + changelog[len(header):]
'''
if source.count(old) != 1:
    raise RuntimeError(f"Expected one changelog patch statement, found {source.count(old)}")
source = source.replace(old, new, 1)
exec(compile(source, "apply_setup_status_fix_0_20_22.py", "exec"))
