## Inspect packages in a Space Maven repo

Remote package deletion is intentionally unavailable on this fork branch. The read-only tasks can
still list repositories and prepare a candidate report for inspection.

1. Requirements:
* JDK 9+ is required to run the script.

2. Generate a personal token:
* Add `ReadRepository` and `ViewProject` permissions

3. Create `local.properties` from template:
```
cp template.local.properties local.properties
```

4. Set parameters in `local.properties`:
* `space.server.url` - URL of space installation;
* `space.auth.token` - your token; 

5. Find out project ID and package repo ID by running:
```
./gradlew listProjectsAndPackageRepositories
```

6. Set parameters in `local.properties`:
* `space.project.id` - Project ID;
* `space.repo.id` - Repo ID;

7. Generate a candidate package report:
```
./gradlew generateListOfPackagesToDelete -Pspace.package.version=0.4.0-preview-*
```
