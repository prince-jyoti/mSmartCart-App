#!/usr/bin/env python3
"""Keeps the infrastructure classes that every service carries its own copy of in sync.

Each service stays self-contained (no shared library), so these files are deliberately
duplicated. This check fails when the copies drift apart; package names are the only allowed
difference.

    scripts/check-shared-code.py          # check; exit 1 and show diffs on drift
    scripts/check-shared-code.py --fix    # copy each group's reference version to the others

To change one of these classes: edit the reference copy (first service listed for its group),
run --fix, then build and test every service in the group.

Not listed on purpose: request/response objects (UserDTO, OrderRes, ...) — each consumer keeps
only the fields it needs; SecurityConfig, OutboxService, RabbitConfig and the remaining
GlobalExceptionHandlers — they legitimately differ per service.
"""
import difflib
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# (services, files): the first service holds the reference copy.
GROUPS = [
    (["order", "user", "product", "cart", "payment"],
     ["BaseResponse.java", "CookieBearerTokenResolver.java", "NotFoundException.java", "ServiceTimingAspect.java"]),
    (["order", "cart", "payment"],  # services that call others through Feign
     ["GlobalExceptionHandler.java", "ServiceUnavailableException.java", "CircuitBreakerConfiguration.java",
      "CustomErrorDecoder.java", "FallbackErrors.java", "FeignCallLoggingAspect.java", "FeignClientConfig.java",
      "FeignConfig.java"]),
    (["order", "payment"],  # transactional outbox publishers
     ["OutboxEvent.java", "OutboxRepo.java", "OutboxPublisher.java", "OutboxCleanup.java"]),
    (["cart", "product"],  # event consumers that deduplicate
     ["ProcessedEvent.java", "ProcessedEventRepo.java", "ProcessedEventCleanup.java", "SchedulingConfig.java"]),
]


def find(service, name):
    matches = list((ROOT / f"{service}-service/src/main/java").rglob(name))
    if len(matches) != 1:
        sys.exit(f"{service}-service: expected exactly one {name}, found {len(matches)}")
    return matches[0]


def normalize(text):
    """The file with its service's identity removed, for comparison."""
    text = re.sub(r"^package .*;\n", "", text, count=1, flags=re.M)
    text = re.sub(r"com\.example\.\w+_service", "SVC", text)
    text = text.replace("SVC.services.", "SVC.service.")  # product-service names its package "services"
    return "\n".join(line.rstrip() for line in text.strip().splitlines()) + "\n"


def adapt(reference_text, reference_service, target_path):
    """The reference file rewritten for the target service (its package line kept)."""
    target_package = re.search(r"^package .*;$", target_path.read_text(), re.M).group(0)
    text = reference_text.replace(f"com.example.{reference_service}_service", f"com.example.{target_path.relative_to(ROOT).parts[0].removesuffix('-service')}_service")
    return re.sub(r"^package .*;$", target_package, text, count=1, flags=re.M)


def main():
    fix = "--fix" in sys.argv
    drift = 0
    for services, files in GROUPS:
        reference_service, others = services[0], services[1:]
        for name in files:
            reference = find(reference_service, name)
            for service in others:
                target = find(service, name)
                if normalize(reference.read_text()) == normalize(target.read_text()):
                    continue
                if fix:
                    target.write_text(adapt(reference.read_text(), reference_service, target))
                    print(f"fixed   {target.relative_to(ROOT)}")
                    continue
                drift += 1
                print(f"DRIFT   {target.relative_to(ROOT)} differs from {reference.relative_to(ROOT)}")
                diff = difflib.unified_diff(normalize(reference.read_text()).splitlines(),
                                            normalize(target.read_text()).splitlines(),
                                            f"{reference_service} (reference)", service, lineterm="", n=1)
                for line in list(diff)[:30]:
                    print("        " + line)
    checked = sum(len(s) * len(f) for s, f in GROUPS)
    if drift:
        print(f"\n{drift} file(s) drifted. Bring them in line with the reference copy "
              f"(scripts/check-shared-code.py --fix copies it over).")
        return 1
    print(f"{'Synced' if fix else 'OK'}: {checked} copies of {sum(len(f) for _, f in GROUPS)} shared classes "
          f"across {len({s for g, _ in GROUPS for s in g})} services")
    return 0


if __name__ == "__main__":
    sys.exit(main())
