# VelocityNavigator Release Checklist

## Code and Runtime

- Confirm `pom.xml`, plugin annotation, README, and changelog all use the same release number.
- Build with Java 17 and verify tests pass.
- Drop the jar into a clean Velocity test proxy and confirm startup succeeds with no injector errors.
- Test `/lobby`, `/velocitynavigator status`, `/velocitynavigator reload`, and both debug modes.
- Verify config migration creates `navigator.toml.v<old>.bak` when loading a legacy file.

## Marketplace Prep

- Export the SVG listing assets to the PNG sizes required by Modrinth and Spigot.
- Confirm bStats reporting is healthy for plugin id `28341`.
- Paste the prepared copy from `docs/modrinth-listing.md` and `docs/spigot-resource.md`.
- Upload the banner, icon, social preview, and three feature graphics.

## Final QA

- Verify the README images render correctly on GitHub.
- Confirm update-check logs only once after startup delay.
- Confirm the published jar name and uploaded artifact name match the release version.
