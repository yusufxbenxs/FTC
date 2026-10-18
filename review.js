// Runs inside GitHub Actions (actions/github-script) for the FireTube catalog.
//   check   - on every new/edited submission issue: run the safety checker, comment, label
//   publish - when a maintainer adds the "approved" label: re-check and add to catalog.json
//
// The issue body is untrusted text: it is only ever JSON.parse'd and checked,
// never executed or pasted into a shell.
require('./blocks.js'); // defines globalThis.FT_BLOCKS
const { verifyPackage } = require('./verify.js');

const RESULT_LABELS = ['checks-passed', 'checks-failed', 'needs-code-review'];
const ICON = { pass: '✅', warn: '⚠️', fail: '❌', review: '🔍' };

function extract(body) {
  const fenced = body.match(/```(?:json)?\s*([\s\S]*?)```/);
  const raw = fenced ? fenced[1] : body.slice(body.indexOf('{'), body.lastIndexOf('}') + 1);
  if (!raw || !raw.trim()) return { ok: false, error: 'No package JSON found in the issue. Paste the contents of your .firetube.json file.' };
  if (raw.length > 200 * 1024) return { ok: false, error: 'Package is larger than 200 KB.' };
  try { return { ok: true, value: JSON.parse(raw) }; }
  catch (e) { return { ok: false, error: 'The package is not valid JSON: ' + e.message }; }
}

function verdict(issueBody) {
  const pkg = extract(issueBody || '');
  if (!pkg.ok) return { pkg: null, res: { ok: false, needsHumanReview: false, checks: [{ level: 'fail', msg: pkg.error }] } };
  return { pkg: pkg.value, res: verifyPackage(pkg.value, globalThis.FT_BLOCKS) };
}

function report(res) {
  const head = !res.ok ? '### ❌ Automatic checks failed\nFix the items below and edit this issue — the checks run again automatically.'
    : res.needsHumanReview ? '### 🔍 Passed automatic checks — needs code review\nThis mod contains JavaScript. A maintainer will review it; approved JS mods ship in the next FireTube release.'
    : '### ✅ Passed automatic checks\nA maintainer will take a look. Once it gets the `approved` label it goes live in everyone\'s Community tab.';
  return head + '\n\n' + res.checks.map(c => `- ${ICON[c.level] || '•'} ${c.msg}`).join('\n');
}

async function setResultLabel(github, context, label) {
  const { owner, repo } = context.repo, issue_number = context.payload.issue.number;
  for (const l of context.payload.issue.labels || []) {
    if (RESULT_LABELS.includes(l.name) && l.name !== label) {
      await github.rest.issues.removeLabel({ owner, repo, issue_number, name: l.name }).catch(() => {});
    }
  }
  await github.rest.issues.addLabels({ owner, repo, issue_number, labels: [label] });
}

async function check({ github, context }) {
  const { owner, repo } = context.repo, issue_number = context.payload.issue.number;
  const { res } = verdict(context.payload.issue.body);
  await github.rest.issues.createComment({ owner, repo, issue_number, body: report(res) });
  await setResultLabel(github, context, !res.ok ? 'checks-failed' : res.needsHumanReview ? 'needs-code-review' : 'checks-passed');
}

async function publish({ github, context }) {
  const { owner, repo } = context.repo, issue_number = context.payload.issue.number;
  const say = body => github.rest.issues.createComment({ owner, repo, issue_number, body });

  // Only people with write access may approve (labels already require triage+, this double-checks).
  const { data: perm } = await github.rest.repos.getCollaboratorPermissionLevel({ owner, repo, username: context.actor });
  if (!['admin', 'maintain', 'write'].includes(perm.permission)) {
    await github.rest.issues.removeLabel({ owner, repo, issue_number, name: 'approved' }).catch(() => {});
    return say(`@${context.actor} only maintainers can approve packages.`);
  }

  const { pkg, res } = verdict(context.payload.issue.body);
  if (!res.ok) {
    await github.rest.issues.removeLabel({ owner, repo, issue_number, name: 'approved' }).catch(() => {});
    return say('Can\'t publish — the package fails the automatic checks:\n\n' + report(res));
  }
  if (res.needsHumanReview) {
    return say('This is a JavaScript mod, so it isn\'t added to the catalog (Chrome forbids extensions from running downloaded code).\n' +
      'To ship it: add it to `src/mods.js` in the FireTube extension, then release a new version.');
  }

  const path = 'catalog.json';
  const { data: file } = await github.rest.repos.getContent({ owner, repo, path });
  const catalog = JSON.parse(Buffer.from(file.content, 'base64').toString('utf8'));
  catalog.packages = (catalog.packages || []).filter(p => p.id !== pkg.id);
  catalog.packages.push({ ...pkg, approvedAt: new Date().toISOString(), issue: issue_number });
  catalog.updated = new Date().toISOString();

  await github.rest.repos.createOrUpdateFileContents({
    owner, repo, path, sha: file.sha,
    message: `Publish ${pkg.id} ${pkg.version || ''} (#${issue_number})`.trim(),
    content: Buffer.from(JSON.stringify(catalog, null, 2) + '\n').toString('base64')
  });
  await say(`🎉 **${pkg.name}** is live! FireTube users will see it in their Community tab within a few hours (or right away with *Refresh*).`);
  await github.rest.issues.update({ owner, repo, issue_number, state: 'closed', state_reason: 'completed' });
}

module.exports = { check, publish, extract };
