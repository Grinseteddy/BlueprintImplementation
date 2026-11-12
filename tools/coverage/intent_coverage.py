#!/usr/bin/env python3
"""Intent coverage (blueprint step 12): which passing, active tests trace each item of the inventory.

Reads the JUnit XML reports (surefire, failsafe) and the Cucumber JSON report; traces are the tokens
after '--' in a test's name and a scenario's tags. Exit code 1 on any gap.
Usage: intent_coverage.py <inventory.yaml> <repository root>
"""
import glob
import json
import re
import sys
import xml.etree.ElementTree as ET

import yaml

LEVELS = [
    ("domain", ".domain."), ("application", ".application."), ("adapter", ".adapter."),
    ("contract", "org.larder.contract."), ("quality", "org.larder.quality."),
    ("composition", "org.larder.CompositionRootIT"), ("architecture", "ArchitectureRulesTest"),
]


def level_of(classname):
    for level, marker in LEVELS:
        if marker in classname:
            return level
    return "other"


def passing_tests(root):
    """(level, title, tokens) of every passing test: JUnit XML and Cucumber scenarios."""
    tests = []
    for report in glob.glob(f"{root}/*/target/*-reports/TEST-*.xml"):
        for case in ET.parse(report).getroot().iter("testcase"):
            if any(case.find(tag) is not None for tag in ("failure", "error", "skipped")):
                continue
            name = case.get("name", "")
            if "--" not in name:
                continue
            trace = name.split("--", 1)[1]
            tokens = set(re.split(r"[\s,()]+", trace)) - {""}
            if case.get("classname", "").endswith("AcceptanceIT"):
                continue  # scenarios are read from the Cucumber report, with their tags
            tests.append((level_of(case.get("classname", "")), name, tokens))
    for report in glob.glob(f"{root}/larder-app/target/cucumber/*.json"):
        for feature in json.load(open(report)):
            for scenario in feature.get("elements", []):
                steps = scenario.get("steps", [])
                if steps and all(step["result"]["status"] == "passed" for step in steps):
                    tags = {tag["name"].lstrip("@") for tag in scenario.get("tags", [])}
                    tests.append(("acceptance", scenario["name"], tags))
    return tests


def traces(kind, tokens, traced):
    """Example ids repeat across stories (R1.E1): a test traces an example with its story and its id,
    any other item with one of its tokens."""
    return tokens <= traced if kind == "example" else bool(tokens & traced)


def main(inventory_path, root):
    inventory = yaml.safe_load(open(inventory_path))
    tests = passing_tests(root)
    items = []
    for story, spec in inventory["user_stories"].items():
        items.append(("user story", story, {story}))
        items +=[("example", f"{story} {ex}", {story, ex}) for ex in spec["examples"]]
    items += [("rule", rule, {rule}) for rule in inventory["rules"]]
    items += [("HTTP operation", op, {op}) for op in inventory["http_operations"]]
    items += [("send operation", op, {op}) for op in inventory["send_operations"]]
    items += [("receive operation", op, {op}) for op in inventory["receive_operations"]]
    items += [("domain event", evt, set(tokens)) for evt, tokens in inventory["domain_events"].items()]
    items += [("quality scenario", qs, {qs}) for qs in inventory["quality_scenarios"]]

    gaps, rows = [], []
    for kind, item, tokens in items:
        hits = [(level, title) for level, title, traced in tests if traces(kind, tokens, traced)]
        levels = sorted({level for level, _ in hits})
        rows.append((kind, item, len(hits), ", ".join(levels) or "—"))
        if not hits:
            gaps.append(f"{kind} {item}")

    print("| Kind | Item | Passing tests | Levels |\n|---|---|---|---|")
    for kind, item, count, levels in rows:
        print(f"| {kind} | {item} | {count} | {levels} |")
    for story, spec in inventory["user_stories"].items():
        missing = [ex for ex in spec["examples"]
                   if not any(traces("example", {story, ex}, traced) for _, _, traced in tests)]
        planned = ", ".join(f"{ex} (inc {inc})" for ex, inc in spec.get("planned_examples", {}).items())
        state = "done for the increment's slices" if not missing else f"partial: {', '.join(missing)} missing"
        print(f"\n{story}: {state}; planned: {planned}")
    no_domain = [rule for rule in inventory["rules"]
                 if not any(rule in traced and level == "domain" for level, _, traced in tests)]
    print(f"\nRules without a domain-level test: {', '.join(no_domain) or 'none'}")
    for key in ("planned_http_operations", "planned_send_operations", "planned_quality_scenarios"):
        print(f"{key.replace('_', ' ')}: " + ", ".join(f"{k} (inc {v})" for k, v in inventory.get(key, {}).items()))
    if gaps:
        print("\nGAPS:\n" + "\n".join(f"- {gap}" for gap in gaps))
        return 1
    print("\nNo gaps.")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1], sys.argv[2]))
