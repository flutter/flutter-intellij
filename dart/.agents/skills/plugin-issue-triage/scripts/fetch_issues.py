#!/usr/bin/env python3
# Copyright 2026 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import subprocess
import json
import sys
import argparse
import os
import re
import shutil
import base64


def run_cmd(args):
    try:
        res = subprocess.run(args, capture_output=True, text=True, check=True)
        return res.stdout.strip()
    except subprocess.CalledProcessError as e:
        print(f"Error running command {' '.join(args)}: {e.stderr}", file=sys.stderr)
        return None


def fetch_for_repo(repo, limit):
    print(f"\n--- Fetching data for {repo} ---")
    
    print(f"Fetching assignees for {repo}...")
    import base64
    codeowners_b64 = run_cmd(["gh", "api", f"repos/{repo}/contents/.github/CODEOWNERS", "--jq", ".content"])
    
    owners = []
    if codeowners_b64:
        try:
            codeowners_text = base64.b64decode(codeowners_b64).decode('utf-8')
            for line in codeowners_text.splitlines():
                line = line.split('#')[0].strip()
                if line:
                    tokens = line.split()
                    for t in tokens:
                        if t.startswith('@'):
                            handle = t[1:]
                            if '/' not in handle and handle not in owners:
                                owners.append(handle)
        except Exception as e:
            pass
            
    assignees_json = run_cmd(["gh", "api", f"repos/{repo}/assignees", "--paginate", "--jq", ".[].login"])
    raw_assignees = []
    if assignees_json:
        all_assignees = [a.strip() for a in assignees_json.splitlines() if a.strip()]
        raw_assignees = [o for o in owners if o in all_assignees]
        for a in all_assignees:
            if a not in raw_assignees:
                raw_assignees.append(a)

    assignees = []
    if raw_assignees:
        name_map = {}
        git_log = run_cmd(["git", "log", "--format=%an <%ae>"])
        if git_log:
            for line in git_log.splitlines():
                line = line.strip()
                if not line:
                    continue
                match = re.match(r"^(.*?)\s*<(.*?)>$", line)
                if match:
                    name = match.group(1).strip()
                    email = match.group(2).strip().lower()
                    email_prefix = email.split("@")[0]
                    cleaned_name = name.lower().replace(" ", "")

                    if "dependabot" in cleaned_name or "github-actions" in cleaned_name:
                        continue

                    name_map[email_prefix] = name
                    name_map[cleaned_name] = name

        for login in raw_assignees:
            login_lower = login.lower()
            real_name = ""
            if login_lower in name_map:
                real_name = name_map[login_lower]
            else:
                for key, val in name_map.items():
                    if login_lower.startswith(key) or key.startswith(login_lower):
                        real_name = val
                        break

            if not real_name:
                real_name = login

            assignees.append({"login": login, "name": real_name})

    # To isolate owners correctly based on our recent fixes
    repo_owners = [a for a in assignees if a["login"] in owners]
    # If they were not found in assignees list for some reason
    for o in owners:
        if not any(a["login"] == o for a in repo_owners):
            repo_owners.append({"login": o, "name": o})

    print(f"Fetching available labels for {repo}...")
    labels_json = run_cmd(
        ["gh", "label", "list", "--repo", repo, "--limit", "150", "--json", "name"]
    )
    repo_labels = []
    if labels_json:
        try:
            raw_labels = json.loads(labels_json)
            repo_labels = [l["name"] for l in raw_labels if l.get("name")]
        except Exception as e:
            print(f"Warning: Failed to parse repository labels: {e}", file=sys.stderr)

    print(f"Fetching open issues from {repo}...")
    issues_json = run_cmd([
        "gh", "issue", "list", "--repo", repo, "--state", "open",
        "--limit", "100", "--json", "number,title,author,body,createdAt,updatedAt,labels,assignees",
    ])

    if not issues_json:
        print(f"Failed to fetch issues or repository {repo} is empty.", file=sys.stderr)
        issues = []
    else:
        issues = json.loads(issues_json)
        
    priority_labels = {"P0", "P1", "P2", "P3", "P4"}
    untriaged_issues = []

    for issue in issues:
        is_ignored = False
        labels = issue.get("labels", [])
        for l in labels:
            lname = l.get("name")
            if lname in priority_labels or lname in {
                "status: waiting-for-author-response",
                "status: in-discussion",
                "status: first-line-handled",
            }:
                is_ignored = True
                break

        if not is_ignored:
            untriaged_issues.append(issue)

    print(f"Found {len(untriaged_issues)} untriaged issues. Fetching comments for up to {limit}...")

    enriched_issues = []
    for i, issue in enumerate(untriaged_issues[: limit]):
        issue_id = issue["number"]
        author = issue.get("author", {}).get("login", "unknown")

        comments_json = run_cmd([
            "gh", "issue", "view", str(issue_id), "--repo", repo, "--json", "comments",
        ])

        comments = []
        if comments_json:
            try:
                comments_data = json.loads(comments_json)
                raw_comments = comments_data.get("comments", [])
                for c in raw_comments:
                    comments.append({
                        "author": c.get("author", {}).get("login", "unknown"),
                        "body": c.get("body", ""),
                    })
            except Exception as e:
                print(f"Failed to parse comments for issue #{issue_id}: {e}", file=sys.stderr)

        existing_assignees = [
            a.get("login") for a in issue.get("assignees", []) if a.get("login")
        ]

        enriched_issues.append({
            "id": issue_id,
            "number": issue_id,
            "repo": repo,
            "title": issue.get("title", ""),
            "author": author,
            "body": issue.get("body", ""),
            "createdAt": issue.get("createdAt", ""),
            "updatedAt": issue.get("updatedAt", ""),
            "labels": [l.get("name") for l in issue.get("labels", [])],
            "assignees": existing_assignees,
            "comments": comments,
        })
        
    return {
        "assignees": assignees,
        "owners": repo_owners,
        "labels": repo_labels,
        "issues": enriched_issues,
        "total_issues_count": len(untriaged_issues)
    }

def main():
    parser = argparse.ArgumentParser(
        description="Fetch open, untriaged issues and their comments."
    )
    parser.add_argument(
        "--repo", default="both", help="GitHub repository name (owner/repo) or 'both'."
    )
    parser.add_argument(
        "--output-file", required=True, help="Path to save the fetched issues JSON."
    )
    parser.add_argument(
        "--limit", type=int, default=50, help="Maximum number of issues to fetch per repo."
    )
    args = parser.parse_args()

    if not shutil.which("gh"):
        print("Error: GitHub CLI ('gh') is not installed or not in PATH.", file=sys.stderr)
        sys.exit(1)

    if args.repo == "both":
        targets = ["flutter/dart-intellij-third-party", "flutter/flutter-intellij"]
    else:
        targets = [args.repo]
        
    all_assignees = []
    seen_assignees = set()
    all_owners = []
    seen_owners = set()
    labels_by_repo = {}
    all_issues = []
    total_count = 0
    
    for t in targets:
        data = fetch_for_repo(t, args.limit)
        
        for a in data["assignees"]:
            if a["login"] not in seen_assignees:
                seen_assignees.add(a["login"])
                all_assignees.append(a)
                
        for o in data["owners"]:
            if o["login"] not in seen_owners:
                seen_owners.add(o["login"])
                all_owners.append(o)
                
        labels_by_repo[t] = data["labels"]
        all_issues.extend(data["issues"])
        total_count += data["total_issues_count"]
        
    payload = {
        "repo": args.repo,
        "assignees": all_assignees,
        "owners": all_owners,
        "labels_by_repo": labels_by_repo,
        "issues": all_issues,
        "total_issues_count": total_count,
    }

    output_path = os.path.abspath(os.path.expanduser(args.output_file))
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(payload, f, indent=2)

    print(f"Successfully saved {len(all_issues)} total issues to {output_path}")

if __name__ == "__main__":
    main()
