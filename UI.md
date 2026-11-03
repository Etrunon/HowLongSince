# How Long Since: Android UI/UX reference

What the Android app looks like and how it behaves, written so the web app can match it.
Platform-only features (NFC, widget, system notifications) are marked **Android only**.

## Visual language

- **Material 3.** Uses the phone's wallpaper colours (Android 12+), otherwise the default Material light/dark palette. Dark theme is a switch in Settings.
- **Logo:** the app drawing on a blue (`#3366CC`) circle, 36dp. Shown before the title in the top bar and in the menu header.
- **Tag colours:** each tag gets a stable colour from its name (hue = name hash mod 360, saturation 55%, lightness 45% on light themes and 70% on dark). The colour appears as a dot on tag chips and as the bar on the left of a counter card.
- **Icons, not words,** for standard actions: ☰ menu, `<` back, ✏ edit, ⚙ group settings, share, ⟳ reset, + add. Every icon has a text label for screen readers.
- **Primary colour** marks the important numbers: the time since, the reminder ring and section headers. Grey ("muted") marks secondary text.
- Text is in English and Italian. Short time units are localised: d/y in English, g/a in Italian.

## Time format

Two forms of the same duration:

| Under | Two-row form (cards, detail) | One-line form (lists, history, widget) |
|---|---|---|
| 1 hour | `37m` / `40s` | `37m` |
| 1 day | `5h` / `12m` | `5h 12m` |
| 1 year | `3d` / `4h` | `3d 4h` |
| more | `1y` / `12d` | `1y 12d` |

- **Two-row form:** both rows are bold, in the primary colour and centred. The top row is larger. Each value stays on one line with its unit (never `37` above `m`).
- **Refresh rate:** every second while any counter is under 1 hour old (so seconds move), otherwise every 30 s.
- The top number animates when it changes: the old value slides down and out, the new one slides in from above.

## App structure

A single screen: the counter list. Everything else is a full screen over it (detail, group settings, settings) or a dialog. Back (the system gesture or `<`) closes the top layer.

### Top bar (list)

A single-line bar, fixed when scrolling: `☰  [logo] Title ……… [share] [⚙]`

- **Title:** the app name on "All", "Ungrouped", or the group's name. Names over 20 characters are cut to 20 followed by `…`.
- **Share:** only on a synced group. Opens the share dialog.
- **⚙:** only on a real group. Opens Group settings.

### Menu (drawer)

1. **Header:** logo and app name. Tapping it goes home (the All view).
2. **Views** (only if any groups exist): All, Ungrouped (only if some counter has no group), then each group by name. The current view is highlighted.
3. A divider.
4. **Join a group:** opens the Join dialog.
5. **New group:** asks for a name, creates the group and opens its page. Sync is set up later from ⚙.
6. **Settings.**

## Counter list

### Filter bar

Shown only when the current view has **more than 5 counters**:

- A search field (with a search icon) that matches the label or the tags.
- A row of chips that scrolls sideways:
  - **Sort chip** `↕ Due`: tapping cycles Due → Recent → Name. The choice is remembered.
    - *Due*: overdue first, otherwise oldest reset first.
    - *Recent*: most recently reset first.
    - *Name*: alphabetical.
  - **Tag chips**, each with a colour dot. Picking one filters to that tag; picking it again clears the filter.
  - **Archived** chip: shows archived counters instead of active ones.
- With 5 counters or fewer the bar disappears and the search and tag filter are cleared. The Archived chip stays visible on its own whenever archived counters exist, so they can always be reached.

### Sections (All view, when groups exist)

- Counters are grouped under sticky headers: Ungrouped first, then the groups alphabetically.
- **Header:** `Name · count` in the primary colour, a **+** (new counter in that group) and a fold arrow ▲/▼.
- Tapping the header folds or unfolds the section. Folded sections are remembered.
- A folded section **hides its +**. The header keeps the same height either way.
- Searching opens every section.

### Counter card

`▌ [time block] | Label / last reset date / group / #tags | (⟳)`

- **Left bar:** the first tag's colour; none if there are no tags.
- **Time block:** the two-row time, 88dp wide.
- **Middle:**
  - the label (title)
  - the date and time of the last reset (muted)
  - the group name, only when 2 or more groups exist (muted)
  - the tags as `#a · #b` (muted)
- **Reset button:** a round tonal ⟳ button.
  - If a reminder is set, a ring around it fills up towards the reminder time. The ring is primary while filling and turns error-red when overdue.
  - Tapping resets immediately with a haptic tick, and a snackbar "Reset X" offers **Undo**.
- Tapping the card opens the Detail screen.

### Other list behaviour

- **New button:** an extended "+ New" floating button. It shrinks to an icon once the list is scrolled. It is hidden on "All" when groups exist; there, the section headers' + is used instead.
- **Pull to refresh** syncs. A snackbar shows "sync off" when no group syncs, or the first group's error.
- **Empty state:** a centred title and hint when there are no counters at all.

## New / edit counter (dialog)

- **Fields:**
  - **Label** (required).
  - **Group chips:** Ungrouped plus each group (shown only if groups exist).
  - **Tags:** comma-separated, normalised to lowercase with duplicates removed.
- **Reminder:** "Notify after" is a number plus unit chips (hours, days, weeks; minutes only in debug mode or if already in use).
  - A **"Use average (x)"** button fills it from the counter's usual pace (needs 3 or more resets).
- **Edit-only buttons:** Archive/Unarchive and Delete (red).
- Moving a counter out of a synced group asks for confirmation, since it disappears for the other members.

## Counter detail (full screen)

- **Top bar:** `<`, the counter label, ✏ (opens the edit dialog).
- **Summary row:** the two-row time block, then "N entries" (muted), then the same round ⟳ reset button with its ring as on the card.
  - Here the reset button opens a dialog instead of resetting immediately: an optional note, then **Now** or **Earlier…**. Earlier opens a date picker and then a time picker; future times are refused.
- "Archived" hint if the counter is archived.
- **Android only:** an NFC row ("Paired" or "No tag") with Unpair and Pair/Re-pair.
  - Pairing offers two choices: pair now (hold a tag to the phone), or copy the reset URL to write the tag with another app.
- **Interval chart:** "Intervals (avg x)" and a small bar chart of the last 30 gaps between resets, oldest on the left. Shown only with 2 or more gaps.
- **History timeline,** newest first:
  - A dot on a vertical line; the newest dot is bigger.
  - Each entry shows the date and time, then a muted line "after x · via NFC/created · device name", then the note if any.

## Group settings (full screen, from ⚙)

- **Top bar:** `<`, the group name, and share (if synced).
- **Name:** a text field with Save. Duplicate names get " (2)", " (3)", and so on.
- **Sync section:**
  - **Off:**
    - A short explanation, the server URL and a new recovery password (6+ characters).
    - **Turn on** creates the key on the device; it is never sent to the server.
  - **On:**
    - The server URL and the status (last sync or error).
    - For admins: the device list, showing name ("This device" for the current one), Admin/Member, last seen and **Revoke** (red, with confirmation).
    - A recovery password field whose label changes with the situation:
      - "Recovery password, to join as an admin" when this device has no access.
      - "Change password" for admins.
      - "Become admin" for members.
    - Buttons: Save (only when a password is typed), **Sync now** and **Turn off** (red, with confirmation).
- **Delete group** (red, with confirmation). The group's counters stay on the device, local only.

## Join a group (dialog)

1. Paste a link, then **Next**. The link is checked; an already-joined group shows "Already joined as «name»".
2. "Join a group on server?" appears. A link without an invite also asks for the recovery password. Then **Join**.

Join links opened from outside the app (deep links) skip step 1.

## Share (dialog)

- Shows the group name, a large QR code of the link (always black on white), and an explanation:
  - **Admins** get a single-use invite valid for 24 hours.
  - **Members** get the key backup, which needs the recovery password to use.
- Buttons: **Copy link** (marked sensitive so the clipboard preview hides it), **Share** (the system share sheet) and Close.

## Settings (full screen)

- Dark theme (switch).
- Debug mode (switch; enables minutes as a reminder unit).
- **Device name:** tap to rename. It's shown to the other group members.
- **Language:** System, English or Italiano.
- **Import:** pick a file, then choose **Keep both** or **Replace** (red; groups with the same name are replaced).
- **Export:** saves `howlongsince-YYYY-MM-DD.json`.

## Android only

- **Reminders:** a system notification when a counter passes its "notify after" time. Tapping it opens that counter.
- **Home-screen widget:** up to 6 active counters, overdue first.
  - Each row shows the label, the one-line time (red if overdue, blue otherwise) and a reset button.
  - Tapping a label opens that counter; tapping the title opens the app.
- **NFC:** tapping a paired tag resets its counter.

## Web porting notes

- **Visual details:** use the same tag colour formula, the same two time forms and the same 1 s / 30 s refresh.
- **Screen mapping:**
  - Drawer → side menu, or a slide-in panel on mobile widths.
  - Full screens → routes or views with a `<` back button.
  - Dialogs → modals.
- **Notifications:** snackbar "Undo" becomes a toast with an action. Notification reminders can map to the Notifications API if wanted.
- **Thresholds to keep:** the filter bar at more than 5 counters, the 20-character title cut, and hiding + on folded sections.
