#!/usr/bin/env python3
"""Run the standalone Gradle audit using JAVA_HOME or Java on PATH.

Pass --offline only when Gradle and compiler dependencies are already cached.
No Android SDK or separately installed Kotlin compiler is required.
"""
from pathlib import Path
import os
import subprocess
import sys

root = Path(__file__).resolve().parent
wrapper = root.parent / ("gradlew.bat" if os.name == "nt" else "gradlew")
arguments = sys.argv[1:]
task = "compileHarness" if "--compile-only" in arguments else "runHarness"
arguments = [argument for argument in arguments if argument != "--compile-only"]
command = [str(wrapper), "-p", str(root), task, "--console=plain", *arguments]
if os.name == "nt":
    command = [os.environ.get("COMSPEC", "cmd.exe"), "/c", *command]
subprocess.run(command, cwd=root, check=True)
