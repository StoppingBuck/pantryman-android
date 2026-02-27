# TODO — Pantryman (Android)

## Near term

- [ ] Conflict resolution UI (currently last-write-wins; silent if clocks skewed)
- [ ] Sync status indicator (show last sync time in settings)
- [ ] Recipe browsing (engine supports it; UI not yet built)
- [ ] Ingredient CRUD UI (currently pantry-only on Android)

## Nice to have

- [ ] Background sync via WorkManager (instead of only on resume/pause)
- [ ] Sync error notification (currently silent on failure)
- [ ] Material You dynamic colour theming
- [ ] Tablet layout

## Known issues

- If the SAF folder is moved or renamed, sync silently stops (no re-prompting)
- `syncToSAF` during `onPause` runs on a background thread but Android may kill the process before it finishes on very fast swipe-to-close
- DocumentFile operations over slow connections can take several seconds with no progress indication
