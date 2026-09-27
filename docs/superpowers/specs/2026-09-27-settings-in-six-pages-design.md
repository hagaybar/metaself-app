# Settings in six pages — D79

> Written 2026-09-27. The single Settings scroll was hard to follow, and the Drive copy controls hard
> to find; a reorganisation was asked for, with the choice left among three mock-ups (a list of six
> pages; one page of folding sections; everyday-first with setup folded away). Chosen: **a list of six
> pages**. Nothing is added or removed — every existing control moves, unchanged in behaviour and
> wording except headings.

## D79 — Settings is a short list; each entry is its own page

The Settings screen becomes a list of six rows, in this order. Each row shows its title and **one
status line** saying the current state, and opens its own page (top bar with back and the title).

| Row | Status line (examples invented) | Its page holds, in this order |
|---|---|---|
| **Eating** | "09:00–19:00 · reminder at 20:00" / "16/8 · reminder off" | When you eat (hours, keep tab, stop keeping tab, the tally), Set a ratio, then Daily reminder (time, on/off, Send one now) |
| **Movement and health** | "On · health record 40 days" / "Off" / "Health Connect not available" | The Movement status line, band-energy line, health-record line, not-allowed lines, history line, Connect |
| **Backups** | "Daily to a folder and to Drive · last copy 27 Sep" / "Not set up" | Keeping a copy (folder, Change the folder, Copy now, its message); Google Drive (toggle, Copy to Drive now, its message, the detailed-readings line, Bring back detailed readings from Drive and its question/answer); By hand (Save to a file, Restore from a file, the restore question, the archive offer, messages) |
| **AI estimates** | "Key saved · up to 30 a day" / "No key yet" | Your OpenAI API key, Model, Estimates per day, Test it |
| **Food database** | "Open Food Facts · signed in" / "Not signed in" | The Open Food Facts account |
| **Recent problems** | "3 recent" / "None" | The problems list, Copy them, Clear them |

Rules:
- **Behaviour does not change.** Every action, message, question and refusal keeps working exactly as
  now, on the page that holds it; one `SettingsViewModel` still backs every page.
- **Messages appear where their action is.** A refusal or result shows on the page of the control that
  caused it, as now within its section.
- **Movement's "Bring back detailed readings" moves to Backups** with the other Drive controls (it is a
  Drive action); the Movement page keeps a one-line pointer "Detailed readings: see Backups".
- **Entry points into Settings stay** (top-right menu). Any code that navigates into Settings to a
  particular section (e.g. after granting Health Connect permission, or Drive consent) lands on the
  right page.
- Status lines are counts and states, never reassurance (the BackupWording rule).
