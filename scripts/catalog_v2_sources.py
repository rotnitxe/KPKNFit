#!/usr/bin/env python3
"""Evidence sources of the fichas: look them up, verify they exist, and keep the proof offline.

A CURATED ficha cites its evidence in ``sources: [{id, url, title, claim}]``. A citation is only
worth something if the work exists and is the one named, so this tool

* ``lookup``  - searches PubMed (NCBI E-utilities) and prints ready-to-paste ``id/title/url`` data;
* ``verify``  - fetches every source of every CURATED ficha and records the proof in
  ``curation/sources_verified.json`` (PubMed and DOI records are matched against the registry title,
  any other page must answer HTTP 200 and contain the title);
* ``check``   - offline: every source of a CURATED ficha must have fresh proof in that file. This is what
  ``catalog_v2_gate.py`` runs, so the gate never needs the network.

The ``title`` of a source must be the title of the work in its original language. Usage::

    python scripts/catalog_v2_sources.py lookup "Youdas JW[Author] AND pull-up chin-up"
    python scripts/catalog_v2_sources.py verify --definitions pull_up,seal_row   # network; merges into the proof file
    python scripts/catalog_v2_sources.py verify          # network; every CURATED source, drops orphan proof
    python scripts/catalog_v2_sources.py check           # offline; exit 1 on missing or stale proof
    python scripts/catalog_v2_sources.py abstract 21068680   # prints the abstract; judge a ``claim`` against it

``verify`` is safe to run from several authors at once: the network work happens outside a lock and
only the read-merge-write of the proof file is serialized.
"""
from __future__ import annotations

import argparse
import datetime
import html
import json
import os
import re
import sys
import time
import unicodedata
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any, Callable

ROOT = Path(__file__).resolve().parents[1]
FICHAS = ROOT / "catalog" / "exercises" / "v2" / "curation" / "fichas"
PROOF = ROOT / "catalog" / "exercises" / "v2" / "curation" / "sources_verified.json"

SCHEMA_VERSION = 1
EUTILS = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/"
CROSSREF = "https://api.crossref.org/works/"
USER_AGENT = "kpkn-catalog-curation/1.0 (mailto:curation@kpkn.invalid)"
PUBMED_URL = re.compile(r"^https://pubmed\.ncbi\.nlm\.nih\.gov/(\d{1,9})/?$")
DOI_URL = re.compile(r"^https://doi\.org/(10\.\d{4,9}/\S+)$")
PUBMED_MIN_SIMILARITY = 0.6  # Jaccard between the registry title and the title found in PubMed/Crossref
PAGE_MIN_SIMILARITY = 0.8  # share of the registry title words that the fetched page contains
_STOPWORDS = frozenset("a an and are as at by for from in into is of on or the to with without versus vs during among between".split())
_WORD = re.compile(r"[a-z0-9]+")

Fetch = Callable[[str], str]


class SourceError(ValueError):
    pass


def fold(text: str) -> str:
    return "".join(c for c in unicodedata.normalize("NFKD", text.lower()) if not unicodedata.combining(c))


def title_words(text: str) -> set[str]:
    return {w for w in _WORD.findall(fold(text)) if len(w) > 2 and w not in _STOPWORDS}


def jaccard(left: str, right: str) -> float:
    a, b = title_words(left), title_words(right)
    return len(a & b) / len(a | b) if a and b else 0.0


def containment(claimed: str, page: str) -> float:
    a, b = title_words(claimed), title_words(page)
    return len(a & b) / len(a) if a else 0.0


def http_get(url: str, *, limit: int = 600_000) -> str:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept": "text/html,application/json"})
    with urllib.request.urlopen(request, timeout=30) as response:
        return response.read(limit).decode("utf-8", errors="replace")


def strip_tags(page: str) -> str:
    page = re.sub(r"(?is)<(script|style)\b.*?</\1>", " ", page)
    return html.unescape(re.sub(r"(?s)<[^>]+>", " ", page))


# ---------------------------------------------------------------------------
# PubMed lookup / registry matching
# ---------------------------------------------------------------------------


def pubmed_summary(pmids: list[str], fetch: Fetch = http_get) -> dict[str, dict[str, Any]]:
    url = EUTILS + "esummary.fcgi?" + urllib.parse.urlencode({"db": "pubmed", "id": ",".join(pmids), "retmode": "json"})
    result = json.loads(fetch(url))["result"]
    return {pmid: result[pmid] for pmid in pmids if pmid in result and "error" not in result[pmid]}


def pubmed_search(term: str, retmax: int, fetch: Fetch = http_get) -> list[str]:
    url = EUTILS + "esearch.fcgi?" + urllib.parse.urlencode({"db": "pubmed", "term": term, "retmode": "json", "retmax": retmax})
    return json.loads(fetch(url))["esearchresult"]["idlist"]


def describe_pubmed(item: dict[str, Any]) -> str:
    authors = ", ".join(a["name"] for a in item.get("authors", [])[:3])
    return f"{item.get('pubdate')} | {item.get('source')} {item.get('volume')}({item.get('issue')}):{item.get('pages')} | {authors} | {item.get('title')}"


def verify_source(source: dict[str, Any], fetch: Fetch = http_get) -> dict[str, Any]:
    """Proof entry for one source or ``SourceError`` explaining why it can not be verified."""
    url, title = source["url"], source["title"]
    pubmed = PUBMED_URL.match(url)
    doi = DOI_URL.match(url)
    if pubmed:
        pmid = pubmed.group(1)
        record = pubmed_summary([pmid], fetch).get(pmid)
        if record is None:
            raise SourceError(f"PubMed has no record {pmid}")
        found, method = str(record.get("title", "")), "pubmed-esummary"
        score = jaccard(title, found)
        floor = PUBMED_MIN_SIMILARITY
    elif doi:
        try:
            message = json.loads(fetch(CROSSREF + urllib.parse.quote(doi.group(1), safe="/")))["message"]
        except (urllib.error.URLError, ValueError, KeyError) as error:
            raise SourceError(f"Crossref could not resolve {doi.group(1)}: {error}") from error
        found = " ".join(message.get("title") or [])
        method, score, floor = "crossref", jaccard(title, found), PUBMED_MIN_SIMILARITY
    else:
        try:
            page = strip_tags(fetch(url))
        except (urllib.error.URLError, OSError) as error:
            raise SourceError(f"{url} did not answer: {error}") from error
        found, method = "", "http-page"
        score, floor = containment(title, page), PAGE_MIN_SIMILARITY
    if score < floor:
        raise SourceError(f"title mismatch ({score:.2f} < {floor}): registry says {title!r}, source says {found!r}")
    return {
        "foundTitle": found,
        "method": method,
        "similarity": round(score, 2),
        "title": title,
        "verifiedAt": datetime.date.today().isoformat(),
    }


# ---------------------------------------------------------------------------
# Fichas <-> proof
# ---------------------------------------------------------------------------


def curated_sources(fichas_dir: Path) -> list[tuple[str, dict[str, Any]]]:
    """(definition id, source) for every source of every CURATED ficha."""
    found: list[tuple[str, dict[str, Any]]] = []
    for path in sorted(fichas_dir.glob("*.json")):
        try:
            ficha = json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            continue  # the ficha gate reports malformed files; they carry no usable citation
        if not isinstance(ficha, dict) or not isinstance(ficha.get("definitions"), dict):
            continue
        for definition_id, body in sorted(ficha["definitions"].items()):
            if isinstance(body, dict) and body.get("status") == "CURATED":
                for source in body.get("sources") or []:
                    if isinstance(source, dict) and isinstance(source.get("url"), str) and isinstance(source.get("title"), str):
                        found.append((definition_id, source))
    return found


def load_proof(path: Path) -> dict[str, Any]:
    if not path.is_file():
        return {"schemaVersion": SCHEMA_VERSION, "sources": {}}
    return json.loads(path.read_text(encoding="utf-8"))


def write_proof(path: Path, proof: dict[str, Any]) -> None:
    """Atomic: a reader (the gate, another author) never sees a half-written file."""
    temporary = path.with_name(path.name + ".tmp")
    temporary.write_text(json.dumps(proof, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    os.replace(temporary, path)


class ProofLock:
    """Cross-process lock around the read-merge-write of the proof file (several authors verify at once)."""

    STALE_AFTER_SECONDS = 300.0

    def __init__(self, proof_path: Path, timeout: float = 120.0) -> None:
        self.lock = proof_path.with_name(proof_path.name + ".lock")
        self.timeout = timeout

    def __enter__(self) -> "ProofLock":
        deadline = time.monotonic() + self.timeout
        while True:
            try:
                os.close(os.open(self.lock, os.O_CREAT | os.O_EXCL | os.O_WRONLY))
                return self
            except FileExistsError:
                try:
                    if time.time() - self.lock.stat().st_mtime > self.STALE_AFTER_SECONDS:
                        self.lock.unlink(missing_ok=True)  # a killed process left it behind
                        continue
                except OSError:
                    continue
                if time.monotonic() > deadline:
                    raise SourceError(f"proof file is locked by another run ({self.lock.name}); delete the lock if no run is active")
                time.sleep(0.2)

    def __exit__(self, *_exc: object) -> None:
        self.lock.unlink(missing_ok=True)


def proof_problems(fichas_dir: Path, proof: dict[str, Any], definitions: set[str] | None = None) -> list[str]:
    """Offline: reasons why a CURATED source has no valid proof (empty = every citation is verified)."""
    entries = proof.get("sources") or {}
    problems: list[str] = []
    for definition_id, source in curated_sources(fichas_dir):
        if definitions is not None and definition_id not in definitions:
            continue
        entry = entries.get(source["url"])
        label = f"{definition_id}:{source.get('id')}"
        if entry is None:
            problems.append(
                f"source_unverified:{label}: run `python scripts/catalog_v2_sources.py verify --definitions {definition_id}` ({source['url']})"
            )
        elif entry.get("title") != source["title"]:
            problems.append(f"source_title_changed:{label}: the proof was made for {entry.get('title')!r}")
        elif float(entry.get("similarity", 0)) < min(PUBMED_MIN_SIMILARITY, PAGE_MIN_SIMILARITY):
            problems.append(f"source_weak_match:{label}: similarity {entry.get('similarity')}")
    return problems


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="catalog_v2_sources.py", description=__doc__.split("\n\n")[0])
    commands = parser.add_subparsers(dest="command", required=True)
    lookup = commands.add_parser("lookup", help="search PubMed")
    lookup.add_argument("queries", nargs="+")
    lookup.add_argument("--max", type=int, default=5)
    verify = commands.add_parser("verify", help="fetch every CURATED source and update the proof file")
    verify.add_argument("--refresh", action="store_true", help="re-verify sources that already have proof")
    verify.add_argument(
        "--definitions",
        help="comma-separated definition ids: verify only their sources and never drop proof of other definitions",
    )
    check = commands.add_parser("check", help="offline: every CURATED source has fresh proof")
    check.add_argument("--definitions", help="comma-separated definition ids: check only their sources")
    abstract = commands.add_parser("abstract", help="print the PubMed abstract of a source (to judge whether a claim is faithful)")
    abstract.add_argument("pmids", nargs="+", help="PubMed ids or pubmed.ncbi.nlm.nih.gov URLs")
    return parser


def pubmed_abstract(pmid: str, fetch: Fetch = http_get) -> str:
    url = EUTILS + "efetch.fcgi?" + urllib.parse.urlencode({"db": "pubmed", "id": pmid, "rettype": "abstract", "retmode": "text"})
    return fetch(url).strip()


def main(
    argv: list[str] | None = None,
    *,
    fichas_dir: Path | None = None,
    proof_path: Path | None = None,
    fetch: Fetch = http_get,
    pause: float = 0.4,
) -> int:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    arguments = build_parser().parse_args(argv)
    fichas_dir = Path(fichas_dir) if fichas_dir is not None else FICHAS
    proof_path = Path(proof_path) if proof_path is not None else PROOF
    if arguments.command == "lookup":
        for query in arguments.queries:
            print(f"> {query}")
            pmids = pubmed_search(query, arguments.max, fetch)
            records = pubmed_summary(pmids, fetch) if pmids else {}
            if not records:
                print("  (sin resultados)")
            for pmid in pmids:
                if pmid in records:
                    print(f"  https://pubmed.ncbi.nlm.nih.gov/{pmid}/ | {describe_pubmed(records[pmid])}")
            time.sleep(pause)
        return 0
    if arguments.command == "abstract":
        status = 0
        for raw in arguments.pmids:
            match = re.search(r"(\d{1,9})/?$", raw.strip())
            if match is None:
                print(f"not a PubMed id or URL: {raw}", file=sys.stderr)
                status = 1
                continue
            print(f"=== PMID {match.group(1)} ===")
            print(pubmed_abstract(match.group(1), fetch) or "(PubMed has no abstract for this record)")
            print()
            time.sleep(pause)
        return status
    scope = {item.strip() for item in (arguments.definitions or "").split(",") if item.strip()}
    in_scope = [(definition_id, source) for definition_id, source in curated_sources(fichas_dir) if not scope or definition_id in scope]
    if arguments.command == "check":
        problems = proof_problems(fichas_dir, load_proof(proof_path), scope or None)
        for problem in problems:
            print(problem)
        print(f"sources={len(in_scope)} problems={len(problems)}")
        return 1 if problems else 0
    failures = 0
    known = load_proof(proof_path).get("sources") or {}
    verified: dict[str, dict[str, Any]] = {}
    seen: set[str] = set()
    for definition_id, source in in_scope:
        url = source["url"]
        if url in seen:
            continue
        seen.add(url)
        current = known.get(url)
        if current is not None and current.get("title") == source["title"] and not arguments.refresh:
            continue
        try:
            verified[url] = verify_source(source, fetch)
            print(f"verified {url} ({verified[url]['method']} {verified[url]['similarity']}) <- {definition_id}")
        except (SourceError, urllib.error.URLError, OSError, ValueError, KeyError) as error:
            failures += 1
            print(f"FAILED   {url} <- {definition_id}: {error}", file=sys.stderr)
        time.sleep(pause)
    # The network work above is outside the lock; only the read-merge-write is serialized, so two authors
    # verifying at once never overwrite each other's proof.
    with ProofLock(proof_path):
        proof = load_proof(proof_path)
        entries = proof.setdefault("sources", {})
        entries.update(verified)
        if not scope:
            # Proof for URLs that no CURATED ficha cites any more is dropped so the file never outlives its fichas.
            cited = {source["url"] for _, source in curated_sources(fichas_dir)}
            for url in sorted(set(entries) - cited):
                del entries[url]
        proof["schemaVersion"] = SCHEMA_VERSION
        write_proof(proof_path, proof)
    print(f"proof={proof_path.name} sources={len(entries)} failed={failures}")
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
