from pathlib import Path
import subprocess
import sys

subprocess.run([sys.executable, "scripts/apply_cloud_linux_backup_0_20_24.py"], check=True)

# Absolute symbolic links are normal inside Linux root filesystems and are safe
# to recreate as links. Hard links, unlike symlinks, must still resolve inside
# the restored archive root because tar extraction materializes their target.
runner = Path("app/src/main/python/sandbox_runner.py")
text = runner.read_text()
old = '''    if member.issym() or member.islnk():
        link = member.linkname.replace("\\\\", "/")
        if link.startswith("/"):
            raise ValueError("Linux environment archive contains an absolute link")
        resolved = os.path.normpath(os.path.join(os.path.dirname(normalized), link))
        if resolved == ".." or resolved.startswith("../"):
            raise ValueError("Linux environment archive contains a link outside its root")
'''
new = '''    if member.islnk():
        link = member.linkname.replace("\\\\", "/")
        if link.startswith("/"):
            raise ValueError("Linux environment archive contains an absolute hard link")
        resolved = os.path.normpath(os.path.join(os.path.dirname(normalized), link))
        if resolved == ".." or resolved.startswith("../"):
            raise ValueError("Linux environment archive contains a hard link outside its root")
'''
if old in text:
    text = text.replace(old, new, 1)
elif new not in text:
    raise RuntimeError("Could not harden portable Linux link extraction")
runner.write_text(text)

print("Cloud and Linux backup patch driver completed.")
