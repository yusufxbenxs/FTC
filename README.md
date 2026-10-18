# FireTube Community Catalog

This repository is the free "server" for FireTube, the mod kit that makes YouTube fun. It holds the community mods and automations everyone sees in FireTube's **Community** tab.

- `catalog.json` — the approved packages. FireTube downloads this file.
- `.github/ISSUE_TEMPLATE/submit-package.yml` — the submission form FireTube opens when you press **Publish online**.
- `.github/workflows/` — robots: one checks every submission, one publishes it when a maintainer adds the `approved` label.
- `scripts/` — the checker (`verify.js`, `blocks.js`) and the robot logic (`review.js`).

## Submitting
Use **Create & Publish → Publish online** inside FireTube. The form is filled in for you.

## Rules
Mods may change how YouTube looks and add helpful buttons. They may not hide ads or sponsorship labels, load outside files, read what people type, or fake likes/views/subscribers.
