import contextlib
import importlib.metadata
import io
import os
import runpy
import re
import shlex
import sys
import traceback


def run_command(command, cwd):
    args = shlex.split(command)
    if not args:
        return ""
    os.makedirs(cwd, exist_ok=True)
    os.chdir(cwd)
    out = io.StringIO()
    with contextlib.redirect_stdout(out), contextlib.redirect_stderr(out):
        try:
            name = os.path.basename(args[0])
            if name in ("pip", "pip3"):
                _run_pip(args[1:])
            elif name in ("python", "python3"):
                _run_python(args[1:])
            else:
                print("Lệnh Python không hợp lệ.")
        except SystemExit as exc:
            if exc.code not in (None, 0):
                print("Python exited with code:", exc.code)
        except Exception:
            traceback.print_exc()
    return out.getvalue()


def _run_python(args):
    if not args or args[0] in ("--version", "-V"):
        print("Python", sys.version.split()[0], "(embedded Android)")
    elif args[0] == "-c":
        if len(args) < 2:
            print("Thiếu mã sau -c")
        else:
            exec(args[1], {"__name__": "__main__"})
    elif args[0] in ("-m",):
        if len(args) < 2:
            print("Thiếu tên module")
        else:
            runpy.run_module(args[1], run_name="__main__")
    elif args[0].startswith("-"):
        print("Tùy chọn Python chưa được hỗ trợ:", args[0])
    else:
        script = os.path.abspath(args[0])
        if not os.path.isfile(script):
            print("python: không tìm thấy tệp:", args[0])
            return
        sys.argv = args
        runpy.run_path(script, run_name="__main__")


def _run_pip(args):
    if not args or args[0] in ("--version", "-V"):
        print("pip is bundled with the embedded Python runtime.")
        return

    action = args[0].lower()
    if action in ("help", "--help", "-h"):
        print("GKC embedded pip commands:")
        print("  pip list                 show packages bundled in this APK")
        print("  pip freeze               show pinned package versions")
        print("  pip show PACKAGE         show package metadata")
        print("  pip install PACKAGE      verify whether a package is bundled")
        print("")
        print("New packages must be added to app/build.gradle.kts and included in a new APK.")
        return

    distributions = list(importlib.metadata.distributions())

    if action == "list":
        rows = sorted(
            (d.metadata.get("Name", "unknown"), d.version)
            for d in distributions
        )
        print("Package                  Version")
        for name, version in rows:
            print(f"{name:24} {version}")
        return

    if action == "freeze":
        for d in sorted(distributions, key=lambda item: item.metadata.get("Name", "").lower()):
            print(f"{d.metadata.get('Name', 'unknown')}=={d.version}")
        return

    if action == "show" and len(args) > 1:
        try:
            d = importlib.metadata.distribution(args[1])
            print(f"Name: {d.metadata.get('Name')}")
            print(f"Version: {d.version}")
            print(f"Location: {d.locate_file('')}")
            if d.metadata.get("Summary"):
                print(f"Summary: {d.metadata.get('Summary')}")
        except importlib.metadata.PackageNotFoundError:
            print("WARNING: Package(s) not found:", args[1])
        return

    if action == "install":
        specs = [item for item in args[1:] if not item.startswith("-")]
        if not specs:
            print("pip install: thiếu tên package.")
            return
        for spec in specs:
            package_name = re.split(r"[<=>!~\[]", spec, maxsplit=1)[0].strip()
            if not package_name:
                print("Yêu cầu package không hợp lệ:", spec)
                continue
            try:
                d = importlib.metadata.distribution(package_name)
                requested = re.search(r"==\s*([A-Za-z0-9_.+-]+)", spec)
                if requested and requested.group(1) != d.version:
                    print(f"ERROR: {package_name}=={requested.group(1)} không có trong APK.")
                    print(f"       Bản đã tích hợp: {d.version}")
                else:
                    print(f"Requirement already satisfied: {d.metadata.get('Name', package_name)}=={d.version}")
            except importlib.metadata.PackageNotFoundError:
                print(f"ERROR: Không tìm thấy {package_name} trong gói Python của ứng dụng.")
                print("Chaquopy đóng gói package tương thích Android lúc build APK;")
                print(f'  thêm install("{spec}") vào khối pip trong app/build.gradle.kts, rồi build lại.')
                print("Khi đã kết nối SSH, lệnh pip install sẽ chạy trực tiếp trên VPS.")
        return

    print("Lệnh pip hỗ trợ: pip list | pip freeze | pip show PACKAGE | pip install PACKAGE | pip help")
