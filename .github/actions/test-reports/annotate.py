# Copyright [2021-present] [ahoo wang <ahoowang@qq.com> (https://github.com/Ahoo-Wang)].
# Licensed under the Apache License, Version 2.0 (the "License"); see the LICENSE file.
"""Prints a GitHub ::error annotation for every failed JUnit test case under the given directory, and appends a Markdown
table of them to the step summary ($GITHUB_STEP_SUMMARY)."""
import glob
import os
import sys
import xml.etree.ElementTree as ET


def escape(text):
    return text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def escape_property(text):
    return escape(text).replace(":", "%3A").replace(",", "%2C")


def source_of(classname):
    path = classname.split("$")[0].replace(".", "/") + ".java"
    found = glob.glob(f"**/src/test/java/{path}", recursive=True)
    return found[0] if found else None


rows = []
for report in sorted(glob.glob(os.path.join(sys.argv[1], "**/build/test-results/test/TEST-*.xml"), recursive=True)):
    for case in ET.parse(report).getroot().iter("testcase"):
        failure = case.find("failure")
        if failure is None:
            failure = case.find("error")
        if failure is None:
            continue
        classname, name = case.get("classname", ""), case.get("name", "")
        text = (failure.get("message") or failure.text or "").strip()
        message = text.splitlines()[0][:300] if text else ""
        source = source_of(classname)
        where = f" file={escape_property(source)}," if source else " "
        simple = classname.rsplit(".", 1)[-1]
        print(f"::error{where}title={escape_property(simple + '.' + name)}::{escape(message)}")
        cell = message.replace("|", "\\|")
        rows.append(f"| `{simple}` | `{name}` | {cell} |")
summary = os.environ.get("GITHUB_STEP_SUMMARY")
if rows and summary:
    with open(summary, "a", encoding="utf-8") as out:
        out.write("### Failed tests\n\n| Class | Test | Message |\n|---|---|---|\n" + "\n".join(rows) + "\n")
