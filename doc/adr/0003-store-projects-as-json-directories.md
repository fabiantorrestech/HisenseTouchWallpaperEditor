# 3. Store projects as JSON directories

Date: 2026-11-04

## Status

Accepted

## Context

Projects must stay editable after export, with crop and zone edits able to change independently. Each project needs its source image, because the original file may later move or disappear. The data is a small document read and written whole, with no queries across projects beyond listing them. A database library would be a further dependency.

## Decision

We will store each project in its own directory, `filesDir/projects/<uuid>/`, holding `project.json` written with the built-in `org.json`, a private copy of the source image, and a thumbnail. `project.json` carries a schema version, and writes go to a temporary file followed by a rename. We will not use Room or SQLite.

## Consequences

Saving needs no new dependency, a project can be duplicated by copying its directory, and the files can be inspected over adb on a debug build. Listing projects means reading each directory, which is fine at the small counts expected here. Schema changes have to be migrated by hand using the version field. Sorting or searching across many projects would be slower than a database, and moving to one later would need a one-time import. Uninstalling the app deletes every project, because the data lives in app-private storage.
