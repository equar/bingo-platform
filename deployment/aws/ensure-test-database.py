#!/usr/bin/env python3
"""Create the local, test-only Bingo database and scoped application account."""
import re
import shlex
import subprocess
import sys
from pathlib import Path


def load_settings(path: Path) -> dict[str, str]:
    settings = {}
    for line in path.read_text().splitlines():
        if not line or line.lstrip().startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        parsed = shlex.split(value)
        if len(parsed) == 1:
            settings[key] = parsed[0]
    return settings


def quote_sql_string(value: str) -> str:
    escaped = (
        value.replace("\\", "\\\\")
        .replace("\0", "\\0")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\x1a", "\\Z")
        .replace("'", "\\'")
    )
    return "'" + escaped + "'"


def build_sql(settings: dict[str, str]) -> str:
    if settings.get("MYSQL_HOST") != "127.0.0.1":
        raise ValueError("The test database must use MYSQL_HOST=127.0.0.1.")
    if settings.get("MYSQL_DATABASE") != "bingotest":
        raise ValueError("The local test database must use MYSQL_DATABASE=bingotest.")

    username = settings.get("MYSQL_USER", "")
    if not re.fullmatch(r"[A-Za-z0-9_]{1,32}", username):
        raise ValueError("MYSQL_USER must contain 1-32 letters, digits, or underscores.")
    password = settings.get("MYSQL_PASSWORD", "")
    if not password or any(character in password for character in ("\0", "\n", "\r")):
        raise ValueError("MYSQL_PASSWORD must be a non-empty single-line value.")

    account = quote_sql_string(username) + "@'127.0.0.1'"
    return "\n".join(
        [
            "CREATE DATABASE IF NOT EXISTS `bingotest` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;",
            f"CREATE USER IF NOT EXISTS {account} IDENTIFIED BY {quote_sql_string(password)};",
            f"GRANT ALL PRIVILEGES ON `bingotest`.* TO {account};",
            "",
        ]
    )


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("Usage: ensure-test-database.py BACKEND_ENV_FILE")
    try:
        sql = build_sql(load_settings(Path(sys.argv[1])))
    except (OSError, ValueError) as error:
        raise SystemExit(str(error)) from error
    subprocess.run(["mysql", "--protocol=socket"], input=sql, text=True, check=True)


if __name__ == "__main__":
    main()
