#!/usr/bin/env python3
"""Validate Player ARB uniqueness, locale parity, and interpolation contracts."""

import json
import re
import sys
from pathlib import Path


class ObjectPairs(list):
    pass


def convert(value: object) -> object:
    if isinstance(value, ObjectPairs):
        return {key: convert(item) for key, item in value}
    if isinstance(value, list):
        return [convert(item) for item in value]
    return value


def main() -> int:
    repo = Path(__file__).resolve().parents[2]
    l10n = repo / "bingo-player" / "lib" / "l10n"
    files = {locale: l10n / f"app_{locale}.arb" for locale in ("en", "am", "ti")}
    catalogs: dict[str, dict[str, object]] = {}
    failed = False

    for locale, path in files.items():
        duplicates: list[str] = []

        def keep_pairs(pairs: list[tuple[str, object]]) -> ObjectPairs:
            seen: set[str] = set()
            for key, _ in pairs:
                if key in seen:
                    duplicates.append(key)
                seen.add(key)
            return ObjectPairs(pairs)

        try:
            raw = json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=keep_pairs)
        except (OSError, json.JSONDecodeError) as error:
            print(f"{path}: cannot parse ARB: {error}", file=sys.stderr)
            failed = True
            continue
        if not isinstance(raw, ObjectPairs):
            print(f"{path}: ARB root must be a JSON object", file=sys.stderr)
            failed = True
            continue
        if duplicates:
            print(f"{path}: duplicate JSON keys: {', '.join(sorted(set(duplicates)))}", file=sys.stderr)
            failed = True

        catalog = convert(raw)
        assert isinstance(catalog, dict)
        catalogs[locale] = catalog

    if failed or len(catalogs) != len(files):
        return 1

    english = catalogs["en"]
    english_keys = set(english)
    metadata_keys = {key for key in english_keys if key.startswith("@")}
    for locale, catalog in catalogs.items():
        keys = set(catalog)
        if keys != english_keys:
            print(
                f"app_{locale}.arb: key parity mismatch; missing={sorted(english_keys - keys)}, "
                f"extra={sorted(keys - english_keys)}",
                file=sys.stderr,
            )
            failed = True
        locale_metadata = {key for key in keys if key.startswith("@")}
        if locale_metadata != metadata_keys:
            print(f"app_{locale}.arb: metadata key parity mismatch", file=sys.stderr)
            failed = True

    placeholder_pattern = re.compile(r"\{([A-Za-z_][A-Za-z0-9_]*)")
    message_keys = [key for key in english_keys if not key.startswith("@") and key != "@@locale"]
    for key in message_keys:
        expected = set(placeholder_pattern.findall(str(english[key])))
        for locale, catalog in catalogs.items():
            actual = set(placeholder_pattern.findall(str(catalog.get(key, ""))))
            if actual != expected:
                print(
                    f"app_{locale}.arb:{key}: placeholder mismatch; expected={sorted(expected)}, "
                    f"actual={sorted(actual)}",
                    file=sys.stderr,
                )
                failed = True
            metadata_key = f"@{key}"
            metadata = catalog.get(metadata_key)
            if expected and not isinstance(metadata, dict):
                print(f"app_{locale}.arb:{metadata_key}: placeholder metadata is missing", file=sys.stderr)
                failed = True
            elif isinstance(metadata, dict):
                declared = set((metadata.get("placeholders") or {}).keys())
                if declared != expected:
                    print(
                        f"app_{locale}.arb:{metadata_key}: metadata placeholder mismatch; "
                        f"expected={sorted(expected)}, declared={sorted(declared)}",
                        file=sys.stderr,
                    )
                    failed = True

    if failed:
        return 1
    print(f"Player localization validation passed: {len(message_keys)} messages, 3 locales, no duplicate keys.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
