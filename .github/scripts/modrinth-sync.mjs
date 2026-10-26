// Syncs this repository's Modrinth page through the Modrinth API: long description from
// .modrinth/description.md plus the sidebar links (source, issues, wiki, Discord).
//
// Repository links belong in Modrinth's link fields, not in the description body, so the body
// file must not contain them. If the project does not exist yet it is created as a draft and,
// when MODRINTH_ORG is set, moved into that organization. Uploading jars (versions) is not done
// here: that is the shared org "Publicar" workflow.
//
// Env: MODRINTH_TOKEN (required), MODRINTH_PROJECT_ID (id or slug, default "slimefun4-drake"),
// MODRINTH_ORG (optional), DRY_RUN=true to print the payload without calling write endpoints.

import { readFileSync } from 'fs';

const V2 = 'https://api.modrinth.com/v2';
const V3 = 'https://api.modrinth.com/v3';
const TOKEN = process.env.MODRINTH_TOKEN;
const PROJECT = process.env.MODRINTH_PROJECT_ID || 'slimefun4-drake';
const ORG = process.env.MODRINTH_ORG || '';
const DRY_RUN = process.env.DRY_RUN === 'true';
const REPO = `${process.env.GITHUB_SERVER_URL || 'https://github.com'}/${process.env.GITHUB_REPOSITORY || 'DrakesCraft-Labs/Slimefun4-Drake'}`;

const metadata = {
  title: 'Slimefun4-Drake Core',
  description: 'Modernized Slimefun 4 core for Java 21 and Paper/Purpur 1.21.11: guide bookmarks, native acceleration and exploit-hardened networks for the DrakesCraft ecosystem.',
  body: readFileSync('.modrinth/description.md', 'utf8'),
  source_url: REPO,
  issues_url: `${REPO}/issues`,
  wiki_url: `${REPO}/tree/main/docs`,
  discord_url: 'https://discord.gg/rsjbf3KgKh',
};

if (DRY_RUN) {
  console.log(JSON.stringify({ project: PROJECT, ...metadata, body: `${metadata.body.length} chars` }, null, 2));
  process.exit(0);
}
if (!TOKEN) {
  console.error('Missing MODRINTH_TOKEN.');
  process.exit(1);
}

const headers = { Authorization: TOKEN, 'User-Agent': 'DrakesCraft-Labs/Slimefun4-Drake modrinth-sync' };

async function call(url, options = {}) {
  const r = await fetch(url, { ...options, headers: { ...headers, ...(options.headers || {}) } });
  if (!r.ok && r.status !== 404) {
    throw new Error(`${options.method || 'GET'} ${url} -> HTTP ${r.status}: ${(await r.text()).slice(0, 400)}`);
  }
  return r;
}

let r = await call(`${V2}/project/${encodeURIComponent(PROJECT)}`);
let project = r.status === 200 ? await r.json() : null;

if (!project) {
  console.log(`Project "${PROJECT}" not found; creating it as a draft.`);
  const form = new FormData();
  form.append('data', JSON.stringify({
    slug: PROJECT,
    title: metadata.title,
    description: metadata.description,
    body: metadata.body,
    categories: ['library', 'utility'],
    client_side: 'unsupported',
    server_side: 'required',
    project_type: 'mod',
    // Modrinth refuses review for a project with no versions, so it stays a draft until one is uploaded.
    is_draft: true,
    license_id: 'GPL-3.0-only',
    initial_versions: [],
  }));
  project = await (await call(`${V2}/project`, { method: 'POST', body: form })).json();
  console.log(`Created draft ${project.slug} (${project.id}).`);
}

if (ORG && project.organization == null) {
  r = await call(`${V3}/organization/${encodeURIComponent(ORG)}`);
  if (r.status !== 200) throw new Error(`Modrinth organization "${ORG}" not found or not visible to the token.`);
  const org = await r.json();
  await call(`${V3}/organization/${org.id}/projects`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ project_id: project.id }),
  });
  console.log(`Moved project into organization ${org.slug}.`);
}

await call(`${V2}/project/${project.id}`, {
  method: 'PATCH',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(metadata),
});
console.log(`Synced description and links for ${project.slug} (${project.id}).`);
