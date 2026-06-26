## Summary

<!-- What changed and why -->

## Living Spec

- [ ] Updated `docs/LIVING_SPEC.md` if behavior/modules changed

## Anti-stub review

- [ ] No unjustified `pass`, `return None`, or `Mock` in production paths
- [ ] Temporary stubs marked with explicit `TODO` and linked issue

## Test plan

- [ ] `./pingui.sh --deploy` passes in venv (or `./scripts/ci_venv.sh`)
- [ ] Manual QA checklist in README (if UI/network touched)

Link Living Spec matrix: [docs/LIVING_SPEC.md](docs/LIVING_SPEC.md)
