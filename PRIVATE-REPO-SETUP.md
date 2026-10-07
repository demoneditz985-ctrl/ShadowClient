# Moving Vortex to a private repository

TL;DR — a **fork cannot be made private on GitHub** (platform rule, not a setting
we can flip), so the source must live in a normal private repo. Everything in
this checkout is already rebranded, so moving it is a 3-step, lossless copy.

---

## Why the current repo can't just be switched to private

* `demoneditz985-ctrl/ShadowClient` is a **fork** of `TheProjectLumina/LuminaClient`.
* GitHub only allows forks of private repositories to be private. A fork of a
  public repository is permanently public — the visibility dropdown is not even
  offered for forks.
* Trying the API anyway returns `403 Resource not accessible by integration`
  from the Arena GitHub connection (it can push code, open PRs and publish
  releases, but it cannot change repository settings or create repositories).

## Option A — new private repo (recommended, 5 minutes)

1. On GitHub: **New repository** → name it e.g. `VortexClient` → select
   **Private** → *do not* add a README, then **Create repository**.
2. Copy the two commands GitHub shows you, or run these (replace the URL):

   ```bash
   cd /home/user/ShadowClient
   git remote add private https://github.com/demoneditz985-ctrl/VortexClient.git
   git push private arena/b8e60820-shadowclient
   ```

   Every commit of the Vortex rebrand, the neon-purple theme, the new logo, the
   launcher fixes and the auth fixes is on that branch.
3. In the new repo: **Settings → General → Default branch** → switch it to
   `arena/b8e60820-shadowclient`, or open a PR inside the new repo and merge it
   into `main` if you prefer a tidy branch name.
4. The `.github/workflows/build-apk.yml` workflow is already in that branch, so
   the private repo builds and publishes the APK by itself from now on
   (private-repo Actions minutes are limited on free accounts — roughly
   2 000 minutes/month, and one build here takes ~5-7 minutes).

## Option B — keep using the public fork

Nothing to do. The code stays readable by anyone, including every future change
we push from this session.

## Option C — delete the public fork

Deleting removes the public source completely, **but**:

* it also deletes the Actions history, releases and the PR;
* this Agent session is attached to that repository, so pushes and builds from
  here stop working — future fixes would have to be delivered as patches instead.

If you want this, do it from **Settings → Danger Zone → Delete repository**
after Option A is done, and tell me so I can point the workflow at the new repo.

---

## A note on the licence

Vortex Client inherits the upstream GPL-3.0 licence. GPL does not require you to
publish the source, but it does require that anyone who receives the APK from you
can also get the source. So:

* private source + **no public APK distribution** → completely fine,
* private source + public APK downloads → recipients are entitled to the source
  on request (the private repo link is enough, but it must be *given*).
