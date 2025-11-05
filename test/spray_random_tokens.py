#!/usr/bin/env python3
"""
blast_tokens.py

Fire a bunch of background HTTP requests using tokens from a JSON file.

Usage examples:
  python3 %(prog)s --json-file ./tokens.json --proxy http://127.0.0.1:8080 --target http://example.local/protected
  python3 %(prog)s --json-file tokens.json --max-workers 100 --timeout 5 --target http://localhost:9999/protected

Note: --target is required (no default). Example values shown above.
"""

import os
import sys
import json
import argparse
import requests
import concurrent.futures

def parse_args():
    env_json = os.getenv("JSON_FILE", "./tokens.json")
    env_proxy = os.getenv("PROXY", "http://127.0.0.1:8080")

    epilog = """
Examples:
  python3 %(prog)s --json-file ./tokens.json --target http://example.local/protected
  python3 %(prog)s --json-file ./tokens.json --proxy http://127.0.0.1:8080 --target http://localhost:9999/protected
Environment variables used as fallback for non-required options:
  JSON_FILE, PROXY
"""

    parser = argparse.ArgumentParser(
        description="Fire-and-forget HTTP requests using tokens from a JSON file.",
        epilog=epilog,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )

    parser.add_argument("--json-file", "-j",
                        default=env_json,
                        help=f"Path to JSON file with tokens (default: env JSON_FILE or {env_json})")
    parser.add_argument("--proxy", "-p",
                        default=env_proxy,
                        help=f"Proxy to use for requests (default: env PROXY or {env_proxy})")
    parser.add_argument("--target", "-t",
                        required=True,
                        help="Target URL to send requests to (required). Examples: http://example.local/protected or http://localhost:9999/protected")
    parser.add_argument("--max-workers", "-w",
                        type=int, default=50,
                        help="Number of threads in ThreadPoolExecutor (default: 50)")
    parser.add_argument("--timeout",
                        type=float, default=10.0,
                        help="Per-request timeout in seconds (default: 10)")
    parser.add_argument("--no-verify-proxy", action="store_true",
                        help="Do not verify TLS (useful when intercepting with an intercepting proxy; use with caution).")
    parser.add_argument("--quiet", "-q", action="store_true",
                        help="Suppress informational prints.")
    return parser.parse_args()


def fire(session, method, url, proxies=None, timeout=10, verify=True, **kwargs):
    """Fire-and-forget HTTP request. Exceptions are swallowed."""
    try:
        session.request(method, url, proxies=proxies, timeout=timeout, verify=verify, **kwargs)
    except Exception:
        # Intentionally ignore errors: fire-and-forget
        pass


def send_tokens(executor, futures_list, session, proxies, target, timeout, verify,
                tokens, header_name=None, header_prefix="", body_field=None, body_prefix=""):
    """
    Schedule requests for each token. Uses executor to submit fire() calls and appends futures to futures_list.
    Returns number of scheduled requests.
    """
    count = 0
    for tok in tokens or []:
        tok = tok.strip()
        if not tok:
            continue

        # Header
        if header_name:
            headers = {}
            if header_name.lower() == "authorization":
                headers["Authorization"] = f"{header_prefix}{tok}"
            elif header_name.lower() == "cookie":
                headers["Cookie"] = f"{header_prefix}{tok}"
            else:
                headers[header_name] = f"{header_prefix}{tok}"

            fut = executor.submit(fire, session, "GET", target, proxies=proxies, timeout=timeout, verify=verify, headers=headers)
            futures_list.append(fut)
            count += 1

        # Body (JSON)
        if body_field:
            payload = {body_field: f"{body_prefix}{tok}"}
            headers = {"Content-Type": "application/json"}
            fut = executor.submit(fire, session, "POST", target, proxies=proxies, timeout=timeout, verify=verify, headers=headers, json=payload)
            futures_list.append(fut)
            count += 1

    return count


if __name__ == "__main__":
    args = parse_args()

    # Check JSON file existence
    if not os.path.isfile(args.json_file):
        print(f"ERROR: JSON file not found at {args.json_file}", file=sys.stderr)
        sys.exit(1)

    if not args.quiet:
        print(f"Proxy : {args.proxy}")
        print(f"Target: {args.target}")
        print(f"JSON  : {args.json_file}")
        print("Launching background requests...")

    # Proxy config for requests
    proxies = {
        "http": args.proxy,
        "https": args.proxy,
    }

    # verify TLS unless user requested no-verify
    verify_flag = not args.no_verify_proxy

    session = requests.Session()
    futures = []
    total_count = 0

    # Load JSON
    with open(args.json_file, "r", encoding="utf-8") as f:
        data = json.load(f)

    # ThreadPoolExecutor simulates background sends
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.max_workers) as executor:
        # JWT
        total_count += send_tokens(
            executor, futures, session, proxies, args.target, args.timeout, verify_flag,
            data.get("jwt"), "Authorization", "Bearer ", "token", ""
        )

        # LTPA2
        total_count += send_tokens(
            executor, futures, session, proxies, args.target, args.timeout, verify_flag,
            data.get("ltpa2"), "Cookie", "LtpaToken2=", "cookie", "LtpaToken2="
        )

        # PASETO
        total_count += send_tokens(
            executor, futures, session, proxies, args.target, args.timeout, verify_flag,
            data.get("paseto"), "Authorization", "Bearer ", "paseto", ""
        )

        if not args.quiet:
            print(f"Launched {total_count} background requests. Check your proxy (Burp) history for highlights.")

    # Executor context exits here; threads will finish outstanding requests before program exits
