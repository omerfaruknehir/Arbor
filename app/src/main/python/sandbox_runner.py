import contextlib
import ctypes
import hashlib
import importlib
import importlib.metadata
import importlib.util
import io
import json
import os
import shutil
import sys
import tarfile
import threading
import time
import traceback
import uuid


_loaded_native_libraries = set()
_active_package_target = None
_execution_lock = threading.RLock()
_namespaces = {}


class CappedWriter(io.StringIO):
    def __init__(self, limit):
        super().__init__()
        self.limit = limit
        self.used = 0

    def write(self, text):
        text = str(text)
        remaining = max(0, self.limit - self.used)
        clipped = text[:remaining]
        self.used += len(clipped)
        return super().write(clipped)


def run_code(code, workspace, output_limit=1_000_000, timeout_seconds=90, args_json="[]"):
    started = time.monotonic()
    old_cwd = os.getcwd()
    stdout = CappedWriter(output_limit)
    stderr = CappedWriter(output_limit)
    result = None
    timed_out = False
    cancelled = False
    exit_code = 0
    os.makedirs(workspace, exist_ok=True)
    environment_id = _environment_id(workspace)
    before = _file_state(workspace)
    with _execution_lock:
        _activate_packages(workspace)
        namespace = _namespaces.setdefault(workspace, {
            "__name__": "__xylune_cell__",
            "__builtins__": __builtins__,
            "WORKSPACE": workspace,
        })
        namespace["WORKSPACE"] = workspace
        deadline = time.monotonic() + max(1, min(int(timeout_seconds), 600))
        cancel_path = os.path.join(workspace, ".xylune-cancel")
        try:
            os.unlink(cancel_path)
        except FileNotFoundError:
            pass
        previous_trace = sys.gettrace()
        old_argv = sys.argv
        try:
            requested_args = json.loads(args_json)
            if not isinstance(requested_args, list) or not all(isinstance(value, str) for value in requested_args):
                raise ValueError("Python script arguments must be strings")
        except BaseException:
            requested_args = []

        def deadline_trace(frame, event, arg):
            if os.path.exists(cancel_path):
                raise InterruptedError("Python execution stopped by user")
            if time.monotonic() >= deadline:
                raise TimeoutError("Python execution exceeded %s seconds" % timeout_seconds)
            return deadline_trace

        try:
            os.chdir(workspace)
            sys.argv = ["<xylune-script>"] + requested_args
            sys.settrace(deadline_trace)
            with contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
                compiled = compile(code, "<xylune-cell>", "exec")
                exec(compiled, namespace, namespace)
                if "_" in namespace:
                    result = repr(namespace["_"])
        except TimeoutError:
            timed_out = True
            exit_code = 124
            traceback.print_exc(file=stderr)
        except InterruptedError:
            cancelled = True
            exit_code = 130
            traceback.print_exc(file=stderr)
        except BaseException:
            exit_code = 1
            traceback.print_exc(file=stderr)
        finally:
            sys.settrace(previous_trace)
            sys.argv = old_argv
            os.chdir(old_cwd)
            try:
                os.unlink(cancel_path)
            except FileNotFoundError:
                pass
    after = _file_state(workspace)
    files = [path for path, state in after.items() if before.get(path) != state][:500]
    return json.dumps({
        "stdout": stdout.getvalue(),
        "stderr": stderr.getvalue(),
        "result": result,
        "files": files,
        "elapsedMs": int((time.monotonic() - started) * 1000),
        "timedOut": timed_out,
        "cancelled": cancelled,
        "exitCode": exit_code,
        "environmentId": environment_id,
    })


def preflight_packages(requirements_json, workspace, platform_tag):
    requirements = json.loads(requirements_json)
    target = os.path.join(workspace, ".packages")
    installed = {}
    if os.path.isdir(target):
        for distribution in importlib.metadata.distributions(path=[target]):
            name = distribution.metadata.get("Name") or ""
            installed[_normalize_distribution(name)] = (name, distribution.version or "")
    parsed_requirements = {}
    errors = []
    try:
        from pip._vendor.packaging.requirements import Requirement
        from pip._vendor.packaging.version import Version
    except BaseException as error:
        return json.dumps({
            "ecosystem": "PIP", "items": [],
            "error": "Package parser unavailable: %s" % error,
        })
    for raw in requirements:
        try:
            parsed = Requirement(raw)
            parsed_requirements[_normalize_distribution(parsed.name)] = (raw, parsed)
        except BaseException as error:
            errors.append("%s: %s" % (raw, error))
    if errors:
        return json.dumps({
            "ecosystem": "PIP",
            "items": [{"request": raw, "name": raw, "action": "INVALID", "detail": "; ".join(errors)} for raw in requirements],
            "error": "; ".join(errors),
        })

    os.makedirs(workspace, exist_ok=True)
    report_path = os.path.join(workspace, ".pip-preflight-%s.json" % uuid.uuid4().hex)
    stdout = CappedWriter(250_000)
    stderr = CappedWriter(250_000)
    success = False
    try:
        os.environ["_PIP_USE_IMPORTLIB_METADATA"] = "0"
        from pip._internal.cli.main import main as pip_main
        args = [
            "install", "--dry-run", "--ignore-installed", "--report", report_path,
            "--no-input", "--disable-pip-version-check",
            "--extra-index-url", "https://chaquo.com/pypi-13.1/",
            "--platform", platform_tag,
            "--python-version", "3.12",
            "--implementation", "cp",
            "--abi", "cp312",
            "--only-binary", ":all:",
        ] + requirements
        with contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
            success = pip_main(args) == 0
        if not success or not os.path.isfile(report_path):
            detail = stderr.getvalue().strip() or stdout.getvalue().strip() or "pip could not resolve an Android-compatible wheel set"
            return json.dumps({
                "ecosystem": "PIP",
                "items": [{"request": raw, "name": raw, "action": "INVALID", "detail": detail[-2000:]} for raw in requirements],
                "rawPreview": (stdout.getvalue() + "\n" + stderr.getvalue())[-6000:],
                "error": detail[-2000:],
            })
        with open(report_path, "r", encoding="utf-8") as source:
            report = json.load(source)
    except BaseException as error:
        return json.dumps({
            "ecosystem": "PIP",
            "items": [{"request": raw, "name": raw, "action": "INVALID", "detail": "%s: %s" % (type(error).__name__, error)} for raw in requirements],
            "error": "Resolver failed: %s: %s" % (type(error).__name__, error),
        })
    finally:
        try:
            os.unlink(report_path)
        except OSError:
            pass

    items = []
    resolved_names = set()
    for resolved in report.get("install", []):
        metadata = resolved.get("metadata") or {}
        name = metadata.get("name") or ""
        version = str(metadata.get("version") or "")
        normalized = _normalize_distribution(name)
        if not normalized:
            continue
        resolved_names.add(normalized)
        requested = parsed_requirements.get(normalized)
        raw = requested[0] if requested else name
        parsed = requested[1] if requested else None
        current = installed.get(normalized)
        same_version = current is not None and current[1] == version
        direct_source = bool(parsed is not None and parsed.url)
        action = "ALREADY_INSTALLED" if same_version and not direct_source else ("UPDATE" if current else "INSTALL")
        detail = "Direct source" if direct_source else ("Requested" if requested else "Dependency")
        items.append({
            "request": raw,
            "name": name,
            "installedVersion": current[1] if current else None,
            "candidateVersion": version or None,
            "action": action,
            "detail": detail,
        })

    # A resolver may omit requirements which are provided by the embedded
    # interpreter. Keep them visible rather than making them disappear.
    for normalized, (raw, parsed) in parsed_requirements.items():
        if normalized in resolved_names:
            continue
        current = installed.get(normalized)
        satisfied = current is not None and not parsed.url and (not parsed.specifier or parsed.specifier.contains(Version(current[1]), prereleases=True))
        items.append({
            "request": raw,
            "name": parsed.name,
            "installedVersion": current[1] if current else None,
            "candidateVersion": current[1] if satisfied else None,
            "action": "ALREADY_INSTALLED" if satisfied else "INVALID",
            "detail": "Already satisfies the requirement" if satisfied else "Resolver did not return a compatible candidate",
        })
        if not satisfied:
            errors.append("No compatible candidate was returned for %s" % raw)

    # "Installed" is not the same as usable. A previous interrupted install,
    # missing Android native dependency, or stale import cache may leave valid
    # distribution metadata behind while importing the requested module still
    # fails. Surface that as an explicit repair transaction instead of
    # disabling the install button as a no-op.
    if os.path.isdir(target):
        _, current_import_errors = _verify_installed_imports(target, requirements)
        for item in items:
            if item["action"] != "ALREADY_INSTALLED" or _normalize_distribution(item["name"]) not in parsed_requirements:
                continue
            visible_name = _requirement_name(item["request"])
            failures = [
                "%s: %s" % (key, value)
                for key, value in current_import_errors.items()
                if key == visible_name or key.startswith(visible_name + " →")
            ]
            if failures:
                item["action"] = "UPDATE"
                item["detail"] = ("Repair installed package; import verification failed: " + "; ".join(failures))[:1400]
    return json.dumps({
        "ecosystem": "PIP",
        "items": items,
        "rawPreview": ("pip install " + " ".join(requirements) + "\n" + stdout.getvalue())[-6000:],
        "error": "; ".join(errors) if errors else None,
    })


def extract_rootfs(archive, destination, strip_components=0):
    """Safely unpack a Linux rootfs archive without requiring Android exec()."""
    started = time.monotonic()
    os.makedirs(destination, exist_ok=True)
    root = os.path.realpath(destination)
    extracted = 0
    skipped = []
    with tarfile.open(archive, "r:*") as source:
        for member in source:
            parts = [part for part in member.name.split("/") if part not in ("", ".")]
            if len(parts) <= strip_components:
                continue
            member.name = "/".join(parts[strip_components:])
            if member.islnk() and not os.path.isabs(member.linkname):
                link_parts = [part for part in member.linkname.split("/") if part not in ("", ".")]
                if len(link_parts) > strip_components:
                    member.linkname = "/".join(link_parts[strip_components:])
            target = os.path.realpath(os.path.join(root, member.name))
            if target != root and not target.startswith(root + os.sep):
                raise ValueError("Archive entry escapes rootfs: %s" % member.name)
            if member.ischr() or member.isblk() or member.isfifo():
                skipped.append(member.name)
                continue
            source.extract(member, root, set_attrs=True, numeric_owner=False)
            extracted += 1
    return json.dumps({
        "extracted": extracted,
        "skipped": skipped[:100],
        "elapsedMs": int((time.monotonic() - started) * 1000),
    })


def install_packages(requirements_json, workspace, platform_tag):
    started = time.monotonic()
    stdout = CappedWriter(1_000_000)
    stderr = CappedWriter(1_000_000)
    requirements = json.loads(requirements_json)
    target = os.path.join(workspace, ".packages")
    staging = os.path.join(workspace, ".packages-installing-%s" % uuid.uuid4().hex)
    if os.path.isdir(target):
        shutil.copytree(target, staging, symlinks=True)
    else:
        os.makedirs(staging, exist_ok=True)
    success = False
    import_names = {}
    import_errors = {}
    try:
        # Chaquopy exposes bundled Python files through AssetPath. pip's
        # importlib.metadata backend assumes every path has pathlib's `parent`
        # attribute, which AssetPath intentionally doesn't implement. pip keeps
        # a pkg_resources backend for compatibility; select it before pip is
        # imported so Android package discovery doesn't crash.
        os.environ["_PIP_USE_IMPORTLIB_METADATA"] = "0"
        from pip._internal.cli.main import main as pip_main
        args = [
            "install", "--no-input", "--disable-pip-version-check", "--upgrade",
            "--root-user-action", "ignore",
            "--target", staging,
            "--extra-index-url", "https://chaquo.com/pypi-13.1/",
            "--platform", platform_tag,
            "--python-version", "3.12",
            "--implementation", "cp",
            "--abi", "cp312",
            "--only-binary", ":all:",
        ] + requirements
        with contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
            success = pip_main(args) == 0
    except BaseException:
        traceback.print_exc(file=stderr)
    if success:
        native_errors = _activate_target(staging)
        import_names, import_errors = _verify_installed_imports(staging, requirements)
        import_errors.update({"native library → %s" % name: error for name, error in native_errors.items()})
        if not import_errors:
            backup = os.path.join(workspace, ".packages-previous-%s" % uuid.uuid4().hex)
            try:
                if os.path.isdir(target):
                    os.replace(target, backup)
                os.replace(staging, target)
                shutil.rmtree(backup, ignore_errors=True)
                _write_environment_metadata(workspace)
                _namespaces.pop(workspace, None)
                _activate_packages(workspace)
            except BaseException:
                traceback.print_exc(file=stderr)
                success = False
                if not os.path.isdir(target) and os.path.isdir(backup):
                    os.replace(backup, target)
        else:
            # Verification failure must not corrupt the last working environment.
            success = False
    if os.path.isdir(staging):
        shutil.rmtree(staging, ignore_errors=True)
    return json.dumps({
        "success": success,
        "stdout": stdout.getvalue(),
        "stderr": stderr.getvalue(),
        "packages": requirements,
        "importNames": import_names,
        "importErrors": import_errors,
        "elapsedMs": int((time.monotonic() - started) * 1000),
    })


def environment_info(workspace):
    with _execution_lock:
        native_errors = _activate_packages(workspace)
        target = os.path.join(workspace, ".packages")
        packages = []
        if os.path.isdir(target):
            try:
                for distribution in importlib.metadata.distributions(path=[target]):
                    name = distribution.metadata.get("Name") or "unknown"
                    packages.append({"name": name, "version": distribution.version or ""})
            except BaseException:
                pass
        packages.sort(key=lambda item: item["name"].lower())
        return json.dumps({
            "pythonVersion": "%s.%s.%s" % sys.version_info[:3],
            "environmentId": _environment_id(workspace),
            "packages": packages,
            "sizeBytes": _directory_size(target),
            "nativeErrors": native_errors,
        })


def repair_environment(workspace):
    with _execution_lock:
        target = os.path.join(workspace, ".packages")
        _purge_bytecode(target)
        _switch_package_target(target)
        importlib.invalidate_caches()
        _namespaces.pop(workspace, None)
        _write_environment_metadata(workspace)
        return environment_info(workspace)


def remove_packages(names_json, workspace):
    names = {_normalize_distribution(value) for value in json.loads(names_json)}
    target = os.path.join(workspace, ".packages")
    with _execution_lock:
        if os.path.isdir(target):
            distributions = list(importlib.metadata.distributions(path=[target]))
            for distribution in distributions:
                if _normalize_distribution(distribution.metadata.get("Name", "")) not in names:
                    continue
                for entry in distribution.files or []:
                    path = os.path.realpath(os.path.join(target, str(entry)))
                    if path == os.path.realpath(target) or not path.startswith(os.path.realpath(target) + os.sep):
                        continue
                    try:
                        if os.path.isfile(path) or os.path.islink(path):
                            os.unlink(path)
                    except OSError:
                        pass
                info_path = getattr(distribution, "_path", None)
                if info_path is not None:
                    shutil.rmtree(str(info_path), ignore_errors=True)
            _remove_empty_directories(target)
            _switch_package_target(target, force=True)
            _namespaces.pop(workspace, None)
            _write_environment_metadata(workspace)
        return environment_info(workspace)


def reset_session(workspace):
    with _execution_lock:
        _namespaces.pop(workspace, None)
        return True


def _activate_packages(workspace):
    target = os.path.join(workspace, ".packages")
    return _activate_target(target)


def _activate_target(target):
    _switch_package_target(target)
    # A previous failed import may have cached this formerly-empty target.
    # Refresh both Python's finder caches and pip's legacy working set after
    # installing into a live embedded interpreter.
    sys.path_importer_cache.pop(target, None)
    importlib.invalidate_caches()
    try:
        from pip._vendor import pkg_resources
        pkg_resources.working_set.add_entry(target)
    except BaseException:
        pass
    return _load_native_libraries(target)


def _switch_package_target(target, force=False):
    global _active_package_target
    target = os.path.realpath(target)
    if force or (_active_package_target is not None and _active_package_target != target):
        old = _active_package_target
        for name, module in list(sys.modules.items()):
            path = getattr(module, "__file__", None)
            if old and path and _is_under(path, old):
                sys.modules.pop(name, None)
    for path in list(sys.path):
        if path != target and (path.endswith(os.sep + ".packages") or ".packages-installing-" in path):
            sys.path.remove(path)
    if os.path.isdir(target) and target not in sys.path:
        sys.path.insert(0, target)
    _active_package_target = target


def _load_native_libraries(target):
    """Load dependency libraries which Chaquopy normally packages as JNI libs.

    Runtime pip installs them under ``chaquopy/lib`` instead, which isn't in
    Android's linker search path. Loading by absolute path with RTLD_GLOBAL
    makes each library's SONAME available to extension modules such as NumPy.
    Multiple passes resolve dependency order without hard-coding package names.
    """
    library_dirs = []
    primary = os.path.join(target, "chaquopy", "lib")
    if os.path.isdir(primary):
        library_dirs.append(primary)
    if os.path.isdir(target):
        for root, directories, names in os.walk(target):
            if root == primary:
                continue
            base = os.path.basename(root)
            if base.endswith(".libs") or base == "lib":
                library_dirs.append(root)
            directories[:] = [name for name in directories if name not in {"tests", "test", "__pycache__"}]
    if not library_dirs:
        return {}
    remaining = [
        os.path.join(directory, name)
        for directory in library_dirs
        for name in sorted(os.listdir(directory))
        if name.startswith("lib") and ".so" in name and os.path.isfile(os.path.join(directory, name))
        and os.path.join(directory, name) not in _loaded_native_libraries
    ]
    errors = {}
    mode = getattr(ctypes, "RTLD_GLOBAL", getattr(os, "RTLD_GLOBAL", 0))
    while remaining:
        next_remaining = []
        progress = False
        for path in remaining:
            try:
                ctypes.CDLL(path, mode=mode)
                _loaded_native_libraries.add(path)
                errors.pop(os.path.basename(path), None)
                progress = True
            except OSError as error:
                errors[os.path.basename(path)] = str(error)
                next_remaining.append(path)
        if not progress:
            break
        remaining = next_remaining
    return errors


def _verify_installed_imports(target, requirements):
    requested = {
        _normalize_distribution(_requirement_name(requirement)): requirement
        for requirement in requirements
    }
    names = {}
    errors = {}
    try:
        distributions = list(importlib.metadata.distributions(path=[target]))
    except BaseException as error:
        return names, {"metadata": "%s: %s" % (type(error).__name__, error)}
    for distribution in distributions:
        distribution_name = distribution.metadata.get("Name", "")
        normalized = _normalize_distribution(distribution_name)
        if normalized not in requested:
            continue
        raw_top_level = distribution.read_text("top_level.txt") or ""
        top_levels = [
            value.strip() for value in raw_top_level.splitlines()
            if value.strip() and value.strip().isidentifier()
            and value.strip() not in {"tests", "test"}
            and not value.strip().startswith("_")
        ]
        if not top_levels:
            roots = []
            for entry in distribution.files or []:
                root = str(entry).replace("\\", "/").split("/", 1)[0]
                if root.endswith(".py"):
                    root = root[:-3]
                if root.isidentifier() and root not in {"tests", "test", "__pycache__"} and not root.startswith("_"):
                    roots.append(root)
            top_levels = list(dict.fromkeys(roots))
        if not top_levels:
            candidate = distribution_name.replace("-", "_")
            if candidate.isidentifier():
                top_levels = [candidate]
        visible_name = _requirement_name(requested[normalized])
        names[visible_name] = top_levels[:8]
        for module_name in top_levels[:8]:
            try:
                sys.path_importer_cache.pop(target, None)
                importlib.invalidate_caches()
                importlib.import_module(module_name)
            except BaseException as error:
                errors["%s → %s" % (visible_name, module_name)] = _concise_error(error)
    return names, errors


def _concise_error(error):
    message = str(error).strip()
    marker = "Original error was:"
    if marker in message:
        message = message.split(marker, 1)[1].strip()
    lines = [line.strip() for line in message.splitlines() if line.strip()]
    detail = " ".join(lines[-4:]) if lines else repr(error)
    return ("%s: %s" % (type(error).__name__, detail))[:1200]


def _requirement_name(requirement):
    return requirement.split("[", 1)[0].split("=", 1)[0].split("<", 1)[0].split(">", 1)[0].split("!", 1)[0].split("~", 1)[0]


def _normalize_distribution(name):
    return name.lower().replace("-", "_").replace(".", "_")


def _file_state(workspace):
    result = {}
    ignored = {".packages", ".cache", "__pycache__", "incoming"}
    for root, directories, names in os.walk(workspace):
        directories[:] = [name for name in directories if name not in ignored]
        for name in names:
            path = os.path.join(root, name)
            try:
                stat = os.stat(path)
                digest = None
                if stat.st_size <= 8 * 1024 * 1024:
                    with open(path, "rb") as stream:
                        digest = hashlib.blake2b(stream.read(), digest_size=12).hexdigest()
                result[os.path.relpath(path, workspace)] = (stat.st_mtime_ns, stat.st_ctime_ns, stat.st_size, digest)
            except OSError:
                pass
    return result


def _environment_id(workspace):
    metadata_path = os.path.join(workspace, ".xylune-python.json")
    try:
        with open(metadata_path, "r", encoding="utf-8") as stream:
            value = json.load(stream).get("environmentId")
            if value:
                return value
    except (OSError, ValueError):
        pass
    value = uuid.uuid4().hex[:12]
    try:
        with open(metadata_path, "w", encoding="utf-8") as stream:
            json.dump({"environmentId": value, "updatedAt": int(time.time() * 1000)}, stream)
    except OSError:
        pass
    return value


def _write_environment_metadata(workspace):
    metadata_path = os.path.join(workspace, ".xylune-python.json")
    value = _environment_id(workspace)
    try:
        with open(metadata_path, "w", encoding="utf-8") as stream:
            json.dump({"environmentId": value, "updatedAt": int(time.time() * 1000)}, stream)
    except OSError:
        pass


def _directory_size(path):
    total = 0
    if not os.path.isdir(path):
        return total
    for root, directories, names in os.walk(path):
        for name in names:
            try:
                total += os.path.getsize(os.path.join(root, name))
            except OSError:
                pass
    return total


def _purge_bytecode(path):
    if not os.path.isdir(path):
        return
    for root, directories, names in os.walk(path):
        for name in names:
            if name.endswith((".pyc", ".pyo")):
                try:
                    os.unlink(os.path.join(root, name))
                except OSError:
                    pass


def _remove_empty_directories(path):
    if not os.path.isdir(path):
        return
    for root, directories, names in os.walk(path, topdown=False):
        try:
            if root != path and not os.listdir(root):
                os.rmdir(root)
        except OSError:
            pass


def _is_under(path, parent):
    try:
        path = os.path.realpath(path)
        parent = os.path.realpath(parent)
        return path == parent or path.startswith(parent + os.sep)
    except (OSError, TypeError):
        return False



def _portable_archive_stats(root):
    file_count = 0
    size_bytes = 0
    for directory, _, files in os.walk(root, followlinks=False):
        for name in files:
            path = os.path.join(directory, name)
            file_count += 1
            if not os.path.islink(path):
                try:
                    size_bytes += os.path.getsize(path)
                except OSError:
                    pass
    return {"fileCount": file_count, "sizeBytes": size_bytes}


def create_portable_tar(source, destination):
    source = os.path.realpath(source)
    destination = os.path.realpath(destination)
    if not os.path.isdir(source):
        raise ValueError("Linux environment directory is missing")
    os.makedirs(os.path.dirname(destination), exist_ok=True)
    temporary = destination + ".part"
    try:
        os.unlink(temporary)
    except FileNotFoundError:
        pass
    try:
        with tarfile.open(
            temporary,
            "w:gz",
            format=tarfile.PAX_FORMAT,
            dereference=False,
            compresslevel=6,
        ) as archive:
            archive.add(source, arcname=".", recursive=True)
        os.replace(temporary, destination)
    finally:
        try:
            os.unlink(temporary)
        except FileNotFoundError:
            pass
    result = _portable_archive_stats(source)
    result["sizeBytes"] = os.path.getsize(destination)
    return json.dumps(result)


def _safe_portable_tar_member(member, destination):
    name = member.name.replace("\\", "/")
    normalized = os.path.normpath(name)
    if name.startswith("/") or normalized in ("..", ".") or normalized.startswith("../"):
        if normalized == "." and member.isdir():
            return member
        raise ValueError("Linux environment archive contains an unsafe path")
    if member.isdev() or member.isfifo():
        raise ValueError("Linux environment archive contains an unsupported device node")
    if member.islnk():
        link = member.linkname.replace("\\", "/")
        if link.startswith("/"):
            raise ValueError("Linux environment archive contains an absolute hard link")
        resolved = os.path.normpath(os.path.join(os.path.dirname(normalized), link))
        if resolved == ".." or resolved.startswith("../"):
            raise ValueError("Linux environment archive contains a hard link outside its root")
    target = os.path.realpath(os.path.join(destination, normalized))
    root = os.path.realpath(destination)
    if target != root and not target.startswith(root + os.sep):
        raise ValueError("Linux environment archive escapes its destination")
    return member


def extract_portable_tar(archive_path, destination):
    archive_path = os.path.realpath(archive_path)
    destination = os.path.realpath(destination)
    if not os.path.isfile(archive_path):
        raise ValueError("Linux environment archive is missing")
    os.makedirs(destination, exist_ok=True)
    with tarfile.open(archive_path, "r:*") as archive:
        archive.extractall(
            destination,
            filter=lambda member, path: _safe_portable_tar_member(member, destination),
        )
    return json.dumps(_portable_archive_stats(destination))
