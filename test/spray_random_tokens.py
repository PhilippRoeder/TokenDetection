#!/usr/bin/env python3
"""
spray_random_tokens.py

Fire a bunch of background HTTP requests using tokens from a JSON file.

Features:
 - Sends JWT, LTPA2, PASETO tokens (headers and optional JSON body)
 - Sends SAML tokens in three variants per token:
     * Query parameter (HTTP GET ?SAMLRequest=... or ?SAMLResponse=...)
     * Form-encoded POST body (application/x-www-form-urlencoded)
     * Header injection (SAMLRequest: <val> or SAMLResponse: <val>)
 - Uses a ThreadPoolExecutor to simulate background / concurrent sends
 - Proxy and TLS verify options supported

Usage examples:
  python3 spray_random_tokens.py --json-file ./tokens.json --proxy http://127.0.0.1:8080 --target http://example.local/protected
  python3 spray_random_tokens.py --json-file tokens.json --max-workers 100 --timeout 5 --target http://localhost:9999/protected
"""
from __future__ import annotations
import os
import sys
import json
import argparse
import requests
import concurrent.futures
from typing import Optional, List
from urllib.parse import urlencode


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


def fire(session: requests.Session, method: str, url: str, proxies=None, timeout: float = 10.0,
         verify: bool = True, **kwargs) -> None:
    """
    Fire-and-forget HTTP request. Exceptions are swallowed.
    """
    try:
        session.request(method, url, proxies=proxies, timeout=timeout, verify=verify, **kwargs)
    except Exception:
        # Intentionally swallow exceptions for fire-and-forget behaviour
        pass


def send_tokens(executor: concurrent.futures.Executor, futures_list: list,
                session: requests.Session, proxies: dict, target: str, timeout: float, verify: bool,
                tokens: Optional[List[str]],
                header_name: Optional[str] = None, header_prefix: str = "",
                body_field: Optional[str] = None, body_prefix: str = "") -> int:
    """
    Schedule requests for each token. Supports placing tokens into headers and/or JSON body.
    Returns number of scheduled requests.
    """
    count = 0
    for tok in tokens or []:
        if tok is None:
            continue
        tok = str(tok).strip()
        if not tok:
            continue

        # Header-style
        if header_name:
            headers = {}
            hn_lower = header_name.lower()
            if hn_lower == "authorization":
                headers["Authorization"] = f"{header_prefix}{tok}"
            elif hn_lower == "cookie":
                # header_prefix expected like "LtpaToken2=" to form "Cookie: LtpaToken2=<tok>"
                headers["Cookie"] = f"{header_prefix}{tok}"
            else:
                headers[header_name] = f"{header_prefix}{tok}"

            fut = executor.submit(fire, session, "GET", target,
                                  proxies=proxies, timeout=timeout, verify=verify, headers=headers)
            futures_list.append(fut)
            count += 1

        # JSON body
        if body_field:
            payload = {body_field: f"{body_prefix}{tok}"}
            headers = {"Content-Type": "application/json"}
            fut = executor.submit(fire, session, "POST", target,
                                  proxies=proxies, timeout=timeout, verify=verify,
                                  headers=headers, json=payload)
            futures_list.append(fut)
            count += 1

    return count


def send_saml_variants(executor: concurrent.futures.Executor, futures_list: list,
                       session: requests.Session, proxies: dict, target: str, timeout: float, verify: bool,
                       tokens: Optional[List[str]], allow_header_injection: bool = False) -> int:
    """
    For each SAML token entry, submit two variants by default:
      - Query param (GET)    -> ?SAMLRequest=<val>  or ?SAMLResponse=<val>
      - Form POST (body)     -> application/x-www-form-urlencoded

    Header injection is optional via allow_header_injection.
    """
    count = 0
    for entry in tokens or []:
        if entry is None:
            continue
        raw = str(entry).strip()
        if not raw:
            continue

        if raw.startswith("SAMLRequest:"):
            pname = "SAMLRequest"
            value = raw.split("SAMLRequest:", 1)[1].strip()
        elif raw.startswith("SAMLResponse:"):
            pname = "SAMLResponse"
            value = raw.split("SAMLResponse:", 1)[1].strip()
        else:
            pname = "SAMLRequest"
            value = raw

        # 1) Query param GET
        try:
            query = urlencode({pname: value})
            url_with_q = f"{target}?{query}"
            fut = executor.submit(fire, session, "GET", url_with_q,
                                  proxies=proxies, timeout=timeout, verify=verify)
            futures_list.append(fut)
            count += 1
        except Exception:
            pass

        # 2) Form-encoded POST body
        try:
            headers = {"Content-Type": "application/x-www-form-urlencoded"}
            fut = executor.submit(fire, session, "POST", target,
                                  proxies=proxies, timeout=timeout, verify=verify,
                                  headers=headers, data={pname: value})
            futures_list.append(fut)
            count += 1
        except Exception:
            pass

        # 3) Optional header injection
        if allow_header_injection:
            try:
                headers = {pname: value}
                fut = executor.submit(fire, session, "GET", target,
                                      proxies=proxies, timeout=timeout, verify=verify,
                                      headers=headers)
                futures_list.append(fut)
                count += 1
            except Exception:
                pass

    return count



def load_json_file(path: str) -> dict:
    with open(path, "r", encoding="utf-8") as fh:
        return json.load(fh)


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
    data = load_json_file(args.json_file)

    # ThreadPoolExecutor simulates background sends
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.max_workers) as executor:
        # JWT -> Authorization: Bearer <token> and also as JSON body "token": "<token>"
        total_count += send_tokens(
            executor, futures, session, proxies, args.target, args.timeout, verify_flag,
            data.get("jwt"), header_name="Authorization", header_prefix="Bearer ",
            body_field="token", body_prefix=""
        )

        # LTPA2 -> Cookie: LtpaToken2=<token> and also as JSON body "cookie": "LtpaToken2=<token>"
        total_count += send_tokens(
            executor, futures, session, proxies, args.target, args.timeout, verify_flag,
            data.get("ltpa2"), header_name="Cookie", header_prefix="LtpaToken2=",
            body_field="cookie", body_prefix="LtpaToken2="
        )

        # PASETO -> Authorization: Bearer <token> and JSON body "paseto": "<token>"
        total_count += send_tokens(
            executor, futures, session, proxies, args.target, args.timeout, verify_flag,
            data.get("paseto"), header_name="Authorization", header_prefix="Bearer ",
            body_field="paseto", body_prefix=""
        )

        # SAML -> query param, form body, header
        total_count += send_saml_variants(
            executor, futures, session, proxies, args.target, args.timeout, verify_flag,
            data.get("saml")
        )

        if not args.quiet:
            print(f"Launched {total_count} background requests. Check your proxy (Burp) history for highlights.")

    # Executor context exits here; threads will finish outstanding requests before program exits
