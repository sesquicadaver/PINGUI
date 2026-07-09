## Summary

<!-- What changed and why -->

## Living Spec

- [ ] Updated `docs/LIVING_SPEC.md` if behavior/modules changed
- [ ] Updated `docs/en/LIVING_SPEC.md` (English) if applicable
- [ ] If docs changed: `python3 scripts/check_doc_parity.py` passes (UK/EN parity)

## Anti-stub review

- [ ] No unjustified `pass`, `return None`, or `Mock` in production paths
- [ ] Temporary stubs marked with explicit `TODO` and linked issue

## Test plan

- [ ] `./pingui.sh --deploy` passes in venv (or `./scripts/ci_venv.sh`)
- [ ] Manual QA checklist in README (if UI/network touched)

Living Spec matrix: [docs/LIVING_SPEC.md](docs/LIVING_SPEC.md) · [docs/en/LIVING_SPEC.md](docs/en/LIVING_SPEC.md)

Documentation index: [docs/README.md](docs/README.md) · [docs/en/README.md](docs/en/README.md)
