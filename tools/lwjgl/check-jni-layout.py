#!/usr/bin/env python3
"""Check that Android's JNI function table matches a desktop JVM's.

NikLauncher compiles LWJGL against the NDK's jni.h but runs it against the
desktop HotSpot inside the runtime pack, so every `(*env)->Function(...)` call
compiles to an offset taken from one header and is dispatched through a table
built by the other. Those two layouts have to agree.

They agree today because the order of JNINativeInterface is fixed by the JNI
specification, and Android's header simply stops earlier - at JNI 1.6 - than a
modern OpenJDK, which appends GetModule and IsVirtualThread. A shorter table is
harmless: nothing LWJGL calls lives in the tail.

What would not be harmless is a reordering or an inserted slot, which would
silently dispatch to the wrong function. This asserts that cannot happen rather
than assuming it, and runs in CI so an NDK or JDK bump is caught there.
"""

import re
import sys


def read_table(path):
    """Return the JNINativeInterface slots, in declaration order."""
    source = open(path, errors="replace").read()
    start = source.index("struct JNINativeInterface")
    end = source.index("};", start)
    body = source[start:end]

    reserved = re.findall(r"void\s*\*\s*(reserved\d+)\s*;", body)
    functions = re.findall(r"\*\s*(\w+)\s*\)\s*\(", body)
    return reserved + functions


def main(argv):
    if len(argv) != 3:
        print("usage: check-jni-layout.py <android jni.h> <desktop jni.h>")
        return 2

    android = read_table(argv[1])
    desktop = read_table(argv[2])

    print(f"Android table: {len(android)} slots")
    print(f"Desktop table: {len(desktop)} slots")

    if not android or not desktop:
        print("FAIL: could not parse a JNINativeInterface out of one of the headers")
        return 1

    if len(android) > len(desktop):
        print("FAIL: Android declares more slots than the JVM provides; calls past")
        print("      the end of the real table would read whatever follows it.")
        return 1

    shared = len(android)
    mismatched = [
        (index, android[index], desktop[index])
        for index in range(shared)
        if android[index] != desktop[index]
    ]

    if mismatched:
        print(f"FAIL: {len(mismatched)} slot(s) disagree - calls would dispatch wrongly:")
        for index, lhs, rhs in mismatched[:20]:
            print(f"      slot {index}: android={lhs} desktop={rhs}")
        return 1

    tail = desktop[shared:]
    print(f"OK: the first {shared} slots are identical.")
    print(f"    The JVM's extra slots ({', '.join(tail) if tail else 'none'}) sit past")
    print("    the end of Android's table, where nothing LWJGL calls lives.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
