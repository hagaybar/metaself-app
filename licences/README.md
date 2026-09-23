# Licences of bundled third-party assets

The app ships two typefaces as font resources under `app/src/main/res/font/`. Both are licensed
under the SIL Open Font License, Version 1.1, which requires this licence text to be distributed
with the font software. That is why these files sit at the top level rather than with the design
notes: the fonts are published, so their licences must be too.

- **Fraunces** — `fraunces_light.ttf`, `fraunces_regular.ttf` — [OFL-Fraunces.txt](OFL-Fraunces.txt)
- **Work Sans** — `work_sans_regular.ttf`, `work_sans_medium.ttf`, `work_sans_semibold.ttf` —
  [OFL-WorkSans.txt](OFL-WorkSans.txt)

Both are subset builds: they carry the characters the app draws and no font metadata beyond what
the OFL requires.
