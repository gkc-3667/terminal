import contextlib
import importlib.metadata
import io
import os
import runpy
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
        print("pip is provided by the embedded Python runtime (build-time package environment).")
        return
    if args[0] == "list":
        rows = sorted((d.metadata.get("Name", "unknown"), d.version)
                      for d in importlib.metadata.distributions())
        print("Package                  Version")
        for name, version in rows:
            print(f"{name:24} {version}")
        return
    if args[0] == "show" and len(args) > 1:
        try:
            d = importlib.metadata.distribution(args[1])
            print(f"Name: {d.metadata.get('Name')}")
            print(f"Version: {d.version}")
            print(f"Location: {d.locate_file('')}")
        except importlib.metadata.PackageNotFoundError:
            print("WARNING: Package(s) not found:", args[1])
        return
    if args[0] == "install" and len(args) > 1:
        target = args[-1].lower().replace("_", "-")
        if target in ("discord.py", "discordpy"):
            try:
                d = importlib.metadata.distribution("discord.py")
                print(f"Requirement already satisfied: discord.py=={d.version}")
            except importlib.metadata.PackageNotFoundError:
                print("discord.py chưa được tích hợp trong bản ứng dụng này.")
            return
        print("Cài gói mới trực tiếp bằng pip chưa được hỗ trợ trong Android app.")
        print("Các gói có mã native phải được biên dịch cho Android và đóng gói vào APK.")
        print("Dùng 'pip list' để xem các gói đã có.")
        return
    print("Lệnh pip hỗ trợ: pip list | pip show TEN_GOI | pip install discord.py")
