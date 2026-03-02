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

---

## Reducing friction for pantry CRUD

The core mission of the Android app is to keep the pantry index continuously accurate so
pantryman-linux can match recipes to what you actually have. Every additional tap, screen,
or decision the user must make before they can mark something as "in stock" or "used up" is
a reason to not bother. The ideas below are ordered roughly from highest to lowest impact on
that specific goal.

### 1. Quick-decrement / "used it up" from the pantry list ✅ implemented

**Problem:** Removing or reducing an item currently requires: tap item → wait for dialog →
tap Remove (or clear the quantity field and tap Update). That's 2–3 taps plus a modal.
**Idea:** Add a swipe-left gesture on a pantry row that immediately marks the item as "used
up" (removes it from pantry) with a brief undo snackbar (3 s). For ingredients where
quantity matters (e.g. "4 eggs"), swipe-left decrements by one standard unit, and a
long-swipe removes it entirely. This maps to the most common real-world action: you used
something; you swipe it away. Zero dialogs.

### 2. "I just went shopping" / bulk add mode ✅ implemented

**Problem:** Restocking after a shopping trip means opening the add dialog and going through
the ingredient picker one item at a time. With 10 items this is tedious enough that people
won't do it.
**Idea:** A dedicated "Shopping mode" (accessible from the FAB or a toolbar icon) that shows
the full ingredient list as a flat checklist grouped by category. The user taps to toggle
items in-stock, long-presses to set a quantity. When they tap Done, all changes are written
in a single batch. No dialogs, no back-and-forth.

### 3. Quantity as free-text, not number + unit spinner ✅ implemented

**Problem:** The current dialog has a numeric input for quantity and a dropdown for unit.
This requires two separate interactions and forces the user to pick from a predefined unit
list. In practice people think "a block of butter", "half a bag of rice", "loads of garlic".
**Idea:** Accept a single free-text quantity string (e.g. "2 cans", "half a bag", "a
handful"). The engine's `quantity_type` field is already a plain string; nothing stops us
from storing "cans" or "half a bag" there. Keep the unit autocomplete as a soft suggestion
but never block the user from typing something freeform. Reduces friction from two
interactions to one.

### 4. Fuzzy / typo-tolerant search in ingredient picker

**Problem:** The ingredient picker uses a simple `contains()` filter. Typing "tomatoe" finds
nothing; typing "chick" might miss "chicken breast" if they categorised it differently.
**Idea:** Use a lightweight fuzzy match (e.g. Levenshtein distance ≤ 2 for short queries,
prefix match scored higher than substring match). Since the ingredient list lives in memory
this can be done purely in Kotlin with no backend changes. The picker should also search
tags, not just name and category, so a user who types "dairy" surfaces milk, butter, and
cheese even if their category is "Refrigerator".

### 5. "Not in list" shortcut: create + add to pantry in one step ✅ implemented

**Problem:** If the ingredient doesn't exist yet, the user must: dismiss the picker → tap
"Create new" → fill in the create form (name, category, plural, tags) → wait → get
redirected to the pantry-item dialog. The create form is the highest-friction part of the
entire app.
**Idea:** When the picker search returns no results, show a prominent inline banner:
`Add "tomato paste" to your pantry?` (using the exact search string as the pre-filled name).
Tapping it creates the ingredient with no category / tags (defaults are fine; the user can
edit later on the desktop), immediately marks it as in-stock, and closes the picker. Single
tap for a net-new item. The create-ingredient dialog should still exist for when the user
wants to set category/tags, but it should not be the mandatory path.

### 6. Home-screen widget

**Problem:** Opening the app, waiting for sync, navigating to the picker, and searching is a
lot of steps if you just want to mark one thing as used up while cooking.
**Idea:** A 2×2 Android home-screen widget showing the last 4–6 pantry items sorted by
`last_updated` descending (the ones you touched most recently). Each item has a ✓/✗ button
directly on the widget. Tapping ✗ immediately tombstones the item; tapping ✓ marks it
in-stock. No app open required for the most common "I just used this" action. Requires the
engine to be callable from an `AppWidgetProvider`, which means it must run in the app's
process — doable since we already have the JNI bridge.

### 7. Notification-based reminders for staleness

**Problem:** Pantry data goes stale silently. An item added 6 months ago is probably no
longer accurate but nothing prompts the user to review it.
**Idea:** A daily/weekly WorkManager job checks `last_updated` timestamps. Items not touched
in N days (configurable; default 30) generate a notification: "You haven't checked your
pantry in 30 days. 12 items may be outdated. Tap to review." The review screen is the bulk
add mode (item 2 above) pre-filtered to stale items. This turns pantry maintenance from a
push action (the user has to remember) to a pull one (the app asks).

### 8. Swipe-right: "I still have this / just bought more"

**Problem:** When the user restocks something already in the pantry, they have to open the
item dialog to update quantity. In many cases they don't care about exact quantity — they
just want to mark it as "yes, still there / just bought more".
**Idea:** Swipe-right on a pantry row refreshes the `last_updated` timestamp without
changing quantity, dismissing any staleness badge. This is the "nothing changed, I just
confirmed it's there" gesture — extremely low friction.

### 9. Sync progress and reliability

**Problem:** The sync runs silently. If it fails (slow connection, SAF provider error) the
user has no idea their pantry is out of date. `onPause` sync can be killed by Android before
it finishes.
**Idea (short term):** Show a persistent "Syncing…" indicator (spinner in the toolbar) while
sync is in progress. Show a "Last synced: 3 min ago" line in the Settings dialog. On error,
show a persistent non-dismissable snackbar with a Retry button rather than silent failure.
**Idea (medium term):** Migrate `onPause` push to a `WorkManager` job with
`setExpedited(true)` and a `CONNECTED` constraint, which Android is far less likely to kill.
Use a `ForegroundService` fallback if the expedited quota is exhausted.

### 10. Scan-to-add (barcode / receipt OCR)

**Problem:** After a big shop, manually finding and adding each ingredient is the single
biggest source of friction.
**Idea (phase 1 — barcode):** Integrate ML Kit Barcode Scanning (no internet required).
Scanning an EAN/UPC barcode that matches a known ingredient (stored as a `barcode` tag on
the ingredient YAML) immediately opens the pantry-item dialog for that ingredient. A new
ingredient can be created with the barcode pre-filled.
**Idea (phase 2 — receipt OCR):** Integrate ML Kit Text Recognition to photograph a
supermarket receipt. Parse line items against the known ingredient list using fuzzy name
matching. Show a "Did you buy these?" checklist for the user to confirm before bulk-adding
to pantry. This is high-effort but would be a step-change improvement for the "just got home
from shopping" use case.

### 11. Voice input for hands-free updates

**Problem:** The app requires both hands and full visual attention. When cooking, hands are
often dirty or occupied.
**Idea:** A floating action button that activates Android's built-in speech recognition. The
user says "remove eggs" or "add milk, two litres" — the app parses the utterance against the
known ingredient list and applies the change with a brief confirmation snackbar (with undo).
Can be implemented entirely with Android's `SpeechRecognizer` API; no third-party AI
service required.

### 12. Show "cookable now" count on the pantry screen

**Problem:** The connection between what's in the pantry and what's cookable (the core value
proposition) is only visible in the GTK app. The Android app feels disconnected from
recipes.
**Idea:** Show a persistent chip/badge at the top of the pantry list: "You can make 4
recipes right now." Tapping it opens the recipe browser filtered to cookable-only. This
requires exposing `get_recipe_coverage` from the engine via JNI (the engine already computes
it), and building a minimal recipe browser screen (already in the near-term TODO). Even
read-only recipe browsing with pantry coverage indicators would reinforce the app's purpose
and motivate the user to keep the pantry up to date.

### 13. "Empty pantry" quick-reset for a fresh review

**Problem:** If the user goes away for a month (holiday, hospital stay, etc.) and comes back
to a fridge they've cleared out, the current pantry is entirely wrong. Resetting it means
removing items one by one.
**Idea:** A Settings option "Mark everything as used" that tombstones all current pantry
items in one tap (with a confirmation dialog). The user can then use Shopping mode (item 2)
to quickly re-check what they have. Pairs well with the staleness notification (item 7).

### 14. Improve `PantryAdapter` list diffing

**Problem:** `PantryAdapter.submitList()` calls `notifyDataSetChanged()`, which redraws the
entire list on every update. On a pantry of 50+ items this causes visible flicker and loses
scroll position.
**Idea:** Migrate to `DiffUtil.DiffResult` (or `ListAdapter` + `DiffUtil.ItemCallback`) so
only the changed rows are rebound. Also use the `DiffUtil` mechanism to animate item
removal (item 1 swipe gesture) so the action feels responsive rather than abrupt.

### 15. Keyboard/accessibility shortcuts for power users

**Idea:** When a physical keyboard is connected (many Android users use their phone with a
keyboard dock), support:
- `n` to open the "add to pantry" picker
- `Ctrl+F` to focus the search bar
- Arrow keys to navigate the ingredient list
- `Enter` to confirm / `Escape` to dismiss dialogs

Also audit all interactive elements for content descriptions so TalkBack users can operate
the app eyes-free.
