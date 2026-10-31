#!/usr/bin/env python3
"""Synchronise docs/ROADMAP.md with GitHub: labels, milestones, issues and task statuses.

Uses only the GitHub REST API through `gh api`, so it works wherever `gh auth login` was done.
Idempotent: running it twice creates nothing new.

    python3 scripts/roadmap_sync.py --dry-run        # show what would change
    python3 scripts/roadmap_sync.py                  # apply
    python3 scripts/roadmap_sync.py --only AM-203    # a single task
"""
from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

ROADMAP = Path(__file__).resolve().parent.parent / "docs" / "ROADMAP.md"
MILESTONE_RE = re.compile(r"^## (M\d+) · (.+)$")
ITEM_RE = re.compile(
    r"^- \[(?P<status>[ ~x])\] (?P<id>AM-\d+) · (?P<title>.+?) · moduły: (?P<modules>.+?)"
    r" · zależy: (?P<deps>.+?) · wymagania: (?P<reqs>.+?) · issue: (?P<issue>.+)$"
)
BASE_LABELS = {
    "type:feat": ("1D76DB", "Nowa funkcja lub zadanie z roadmapy"),
    "type:bug": ("D73A4A", "Błąd"),
    "type:chore": ("BFD4F2", "Porządki, build, CI"),
    "compat": ("FBCA04", "Zmiana w Claude Code wpływająca na aplikację"),
    "P1": ("B60205", "Blokuje pracę z agentami"),
    "P2": ("D93F0B", "Utrudnia pracę"),
    "P3": ("C2E0C6", "Kosmetyka"),
}


@dataclass
class Item:
    line_no: int
    status: str
    id: str
    title: str
    modules: str
    deps: str
    reqs: str
    issue: str
    milestone: str

    @property
    def areas(self) -> list[str]:
        areas = []
        for module in re.split(r",\s*", self.modules):
            name = module.strip().split(":")[0].strip(". ")
            if name and name not in ("—", "docs") and f"area:{name}" not in areas:
                areas.append(f"area:{name}")
        return areas

    def render(self) -> str:
        return (f"- [{self.status}] {self.id} · {self.title} · moduły: {self.modules} · zależy: {self.deps}"
                f" · wymagania: {self.reqs} · issue: {self.issue}")


def gh(path: str, method: str = "GET", body: dict | None = None) -> object:
    cmd = ["gh", "api", path, "--method", method, "-H", "Accept: application/vnd.github+json"]
    if body is not None:
        cmd += ["--input", "-"]
    result = subprocess.run(cmd, input=json.dumps(body) if body else None, capture_output=True, text=True)
    if result.returncode != 0:
        sys.exit(f"gh api {method} {path} failed: {result.stderr.strip()}")
    return json.loads(result.stdout) if result.stdout.strip() else None


def gh_all(path: str) -> list[dict]:
    items, page = [], 1
    while True:
        sep = "&" if "?" in path else "?"
        batch = gh(f"{path}{sep}per_page=100&page={page}")
        items.extend(batch)
        if len(batch) < 100:
            return items
        page += 1


def detect_repo() -> str:
    url = subprocess.run(["git", "remote", "get-url", "origin"], capture_output=True, text=True).stdout.strip()
    match = re.search(r"github\.com[:/]([^/]+/[^/.]+)", url)
    if not match:
        sys.exit("Cannot detect GitHub repo from `origin`; pass --repo owner/name.")
    return match.group(1)


def parse(lines: list[str]) -> list[Item]:
    items, milestone = [], ""
    for no, line in enumerate(lines):
        if m := MILESTONE_RE.match(line):
            milestone = f"{m.group(1)} · {m.group(2)}"
        elif m := ITEM_RE.match(line):
            items.append(Item(no, m["status"], m["id"], m["title"], m["modules"], m["deps"], m["reqs"],
                              m["issue"].strip(), milestone))
    return items


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--only", help="sync a single task, e.g. AM-203")
    parser.add_argument("--repo", help="owner/name (default: from git remote origin)")
    args = parser.parse_args()

    repo = args.repo or detect_repo()
    lines = ROADMAP.read_text(encoding="utf-8").splitlines()
    items = [i for i in parse(lines) if not args.only or i.id == args.only]
    if args.only and not items:
        sys.exit(f"{args.only} not found in {ROADMAP}")
    act = "would" if args.dry_run else "did"
    report: list[str] = []

    # Labels
    existing_labels = {label["name"] for label in gh_all(f"repos/{repo}/labels")}
    wanted = dict(BASE_LABELS)
    for item in items:
        for area in item.areas:
            wanted.setdefault(area, ("EDEDED", f"Moduł {area.removeprefix('area:')}"))
    for name, (color, desc) in wanted.items():
        if name not in existing_labels:
            report.append(f"{act} create label {name}")
            if not args.dry_run:
                gh(f"repos/{repo}/labels", "POST", {"name": name, "color": color, "description": desc})

    # Milestones
    milestones = {m["title"]: m["number"] for m in gh_all(f"repos/{repo}/milestones?state=all")}
    for title in dict.fromkeys(i.milestone for i in items):
        if title and title not in milestones:
            report.append(f"{act} create milestone {title}")
            if not args.dry_run:
                milestones[title] = gh(f"repos/{repo}/milestones", "POST", {"title": title})["number"]

    # Issues
    issues = {}
    for issue in gh_all(f"repos/{repo}/issues?state=all"):
        if "pull_request" not in issue and (m := re.match(r"(AM-\d+)\b", issue["title"])):
            issues[m.group(1)] = issue
    changed = False
    for item in items:
        issue = issues.get(item.id)
        if item.issue == "—" and item.status != "x":
            if issue is None:
                kind = "type:chore" if item.milestone.startswith("M0") else "type:feat"
                body = (f"Zadanie z [ROADMAP.md](../blob/main/docs/ROADMAP.md), etap **{item.milestone}**.\n\n"
                        f"- **Moduły:** {item.modules}\n- **Zależy od:** {item.deps}\n"
                        f"- **Wymagania:** {item.reqs}\n- **Plan:** `docs/plans/{item.id}.md` (tworzy `/deliver`)\n")
                report.append(f"{act} create issue for {item.id}")
                if not args.dry_run:
                    issue = gh(f"repos/{repo}/issues", "POST", {
                        "title": f"{item.id} · {item.title}", "body": body,
                        "labels": [kind, *item.areas], "milestone": milestones.get(item.milestone),
                    })
            if issue is not None:
                item.issue = f"#{issue['number']}"
                changed = True
        if issue is not None and issue["state"] == "closed" and item.status != "x":
            report.append(f"{act} mark {item.id} as done (issue #{issue['number']} closed)")
            item.status = "x"
            changed = True

    if changed and not args.dry_run:
        for item in items:
            lines[item.line_no] = item.render()
        ROADMAP.write_text("\n".join(lines) + "\n", encoding="utf-8")
        report.append(f"updated {ROADMAP.relative_to(ROADMAP.parent.parent)}")

    print("\n".join(report) if report else "Roadmap and GitHub are in sync.")


if __name__ == "__main__":
    main()
