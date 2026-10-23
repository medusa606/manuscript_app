# Music Supervisor *(working title)*

**A film-score for whatever you're reading.**

Music Supervisor is an Android app that picks music to match the
*emotional content* of the book you're reading. As the story moves through chapters and the drama
rises or falls, the soundtrack shifts with it. The longer-term plan is to give major characters their
own **leitmotifs**, recurring musical themes that come back whenever they appear.

<!-- TODO: add an emulator screenshot of the Book Details Entry screen, e.g. docs/entry_screen.png -->
<!-- ![Book Details Entry screen](docs/entry_screen.png) -->

The current build has a **Book Details Entry** screen: pick an author and title, and the app pulls
the cover, rating, blurb, genres, themes and motifs from an LLM-built book catalogue.

---

## How it works (the big idea)

Both books and music are placed in the same **emotional space**, Russell's circumplex model of
affect:

```text
                         Arousal  (calm → intense)
                                ▲
              tense / afraid    │    excited / triumphant
                fear, anger     │    joy, excitement
                                │
   Valence  ◄───────────────────┼───────────────────►
   (negative)                   │                   (positive)
                                │
              sad / bleak       │    calm / content
              grief, remorse    │    relief, caring
                                ▼
```

* **Text side:** every sentence of a book is run through emotion classifiers, and each emotion
  label has a fixed Valence/Arousal (VA) coordinate. That turns a novel into a **VA curve over pages**.
* **Music side:** each track's audio is analysed for tempo, energy, key/mode and brightness, and
  those features are mapped to an estimated VA point.
* **The app** follows your reading position along the book's curve and plays the track whose VA
  point is closest. A big jump in arousal, such as a fight or a reveal, triggers a change of track.

> "VAD" = Valence, Arousal, Dominance. Only **V** and **A** are computed at the moment; Dominance is
> reserved for later.

---

## The pieces and how they fit together

The project is split across four repositories:

| # | Component | Repo | Language | What it does |
|---|-----------|------|----------|--------------|
| ① | **db_builder** | [medusa606/db_builder](https://github.com/medusa606/db_builder) | Python | Builds the book catalogue: public book lists → LLM → genres, themes, motif, setting, tags, blurb (~52k books). A 15k snapshot ships in this repo as `app/src/main/assets/items.json` |
| ② | **emotion-reader** | [medusa606/emotion-reader](https://github.com/medusa606/emotion-reader) | Python | Book text → per-sentence emotions → VA curve |
| ③ | **music_mood** | [medusa606/music_mood](https://github.com/medusa606/music_mood) | Python | Spotify playlists → audio previews → VA per track (SQLite) |
| ④ | **manuscript_app** | *(this repo)* | Kotlin / Android | The reader-facing app |

```text
 ┌────────────────────────────┐  ┌────────────────────────────┐  ┌────────────────────────────┐
 │ [1] db_builder             │  │ [2] emotion-reader         │  │ [3] music_mood             │
 │  what is this book?        │  │  how does the text feel?   │  │  how does the music feel?  │
 │                            │  │                            │  │                            │
 │  public book datasets      │  │  Project Gutenberg .txt    │  │  Spotify playlists         │
 │  (Goodreads, Kindle,       │  │            │               │  │  horror · space opera ·    │
 │  Amazon, classics, Top-100)│  │            ▼               │  │  spy thriller · …          │
 │            │               │  │  NLTK sentence split       │  │            │ ISRC          │
 │            ▼               │  │            ▼               │  │            ▼               │
 │  one JSON prompt per book  │  │  GoEmotions (28 labels)    │  │  Deezer 30 s previews      │
 │  Gemini 2.5 Flash-Lite +   │  │   + Hartmann (7) ensemble  │  │            ▼               │
 │  local Ollama gemma3:12b   │  │            ▼               │  │  Essentia: BPM, key/mode,  │
 │            ▼               │  │  neutral suppression       │  │  RMS energy, brightness    │
 │  ~52k books: genre, themes,│  │            ▼               │  │            ▼               │
 │  motif, setting, tags,     │  │  label → V,A (circumplex)  │  │  heuristic V, A  (0 … 1)   │
 │  blurb  → items.json       │  │  per sentence → per page   │  │            ▼               │
 │  (15k snapshot in the app) │  │  V, A  (−1 … +1)           │  │  track_features.db         │
 │                            │  │  CSV + JSON + timeline PNG │  │  1,174 tracks analysed     │
 └─────────────┬──────────────┘  └─────────────┬──────────────┘  └─────────────┬──────────────┘
               │ bundled asset                 ┆ not wired in yet              ┆ not wired in yet
               ▼                               ▼                               ▼
 ┌────────────────────────────────────────────────────────────────────────────────────────────┐
 │ [4] manuscript_app  (Android · Kotlin)                                                     │
 │                                                                                            │
 │  WORKING TODAY                                                                             │
 │   Author ─► Title autocomplete (Room + FTS, from items.json)                               │
 │   ─► book card: high-res cover (Google Books → Open Library), rating, blurb, genres,       │
 │      themes, motifs; background tint pulled from the cover palette                         │
 │   Firebase anonymous auth (Firestore save is stubbed out)                                  │
 │                                                                                            │
 │  PLANNED                                                                                   │
 │   reading position ─► page VA from [2] ─► nearest-VA track from [3] ─► Spotify playback    │
 │   chapter / drama-spike detection ─► track changes  ·  character leitmotifs                │
 └────────────────────────────────────────────────────────────────────────────────────────────┘

   ──►  data flows today          ┆  planned / not yet connected
```

A coloured version of this diagram for the terminal is in [`docs/architecture.ansi`](docs/architecture.ansi):

```bash
cat docs/architecture.ansi
```

---

## Project status

| Area | Status | Notes |
|------|--------|-------|
| Book catalogue (db_builder) | ✅ ~52k books | 14,766 from Gemini 2.5 Flash-Lite, ~39k from local Ollama gemma3:12b. |
| Catalogue in the app (`items.json`) | ⚠️ Out of date | A 15,374-entry snapshot. db_builder now has ~52k unique books, so the asset needs refreshing (and it'll grow to ~60 MB, so consider shipping a pre-built Room DB instead of JSON). |
| Book entry screen | ✅ Working | Autocomplete, cover/rating fetch, metadata display. |
| Save book to Firestore | ⏸ Stubbed | `saveBookEntry()` is commented out and the Save button is disabled. |
| Spotify login (PKCE) | ⏸ Stubbed | `LoginActivity.kt` and `PkceUtils.kt` are written but commented out. |
| Book emotion analysis | ✅ Offline, Python | Dracula, Frankenstein and Great Expectations analysed (see emotion-reader). |
| Music VA database | ✅ Offline, Python | 5 playlists, 1,190 tracks, 1,174 with VA features. |
| Book VA → music matching | ❌ Not started | This is the core feature that ties ②, ③ and ④ together. |
| Leitmotifs | ❌ Idea stage | Needs character detection (NER) per sentence. |
| Remote emotion API | ❓ Unknown | `analyzeEmotion()` calls `https://emotion-api-bst1.onrender.com/analyze`, but nothing calls `analyzeEmotion()`. Probably an early experiment; safe to remove if the service is gone. |

### Integration gaps to close

1. **VA scales differ.** emotion-reader produces V and A in **−1…+1**; music_mood produces them in
   **0…1**. One side needs rescaling before nearest-neighbour matching (e.g. `v_music * 2 - 1`).
2. **No shared data format yet.** The app has no way to load a book's page-level VA curve or the
   track database. A small exported JSON/SQLite bundle per book, plus the track table, would do it.
3. **Book text comes from the reader.** The plan is for readers to supply their own ebook.
   emotion-reader currently only fetches public-domain Gutenberg texts, so it needs an ebook
   (EPUB) import path. The models (~500 MB, GPU-friendly) are too heavy to run comfortably on a
   phone, so the options are:
   * **Server-side:** upload the text, analyse it, and return only the VA curve.
   * **On-device:** use a smaller distilled or quantised model.
   * **Shared cache:** store each finished curve against the book's ISBN, so later readers of the
     same book download a few KB instead of re-analysing. The curve contains no text from the book.
4. **Playback.** Spotify's Web API no longer exposes audio features (deprecated Nov 2024), which is
   why music_mood computes its own. Actual playback would go through the Spotify App Remote SDK.

---

## This repo: the Android app

### Tech stack

* Kotlin, `minSdk 24`, `targetSdk 36`
* Views/XML for the current screens, with Jetpack Compose set up (`ui/search`) for future screens
* **Room** with an FTS table for fast author/title search over `items.json`
* **Coil** for image loading, **AndroidX Palette** for cover-matched background colours
* **Firebase** Auth (anonymous) and Firestore
* Google Books API and Open Library for covers, ratings and ISBNs (no API key needed)

### Screens

| Activity | Purpose |
|----------|---------|
| `MainActivity` | Launcher with three buttons (A → `SecondActivity`, B → `EntryActivity`, C unused) |
| `SecondActivity` | Interim menu leading to book entry |
| `EntryActivity` (`book_entry_activity.kt`) | **Book Details Entry**, the main screen in the screenshot |
| `LoginActivity` | Spotify PKCE login (commented out) |

### Code map

```text
app/src/main/
├── assets/items.json                      ① the LLM-generated book catalogue (~20 MB)
├── java/com/blackbriar/musicsupervisor/
│   ├── MainActivity.kt                    launcher + MusicSupervisorApp (Firebase init)
│   ├── book_entry_activity.kt             EntryActivity: autocomplete, cover fetch, metadata
│   ├── LoginActivity.kt / PkceUtils.kt    Spotify auth (WIP, commented out)
│   ├── scratch.kt                         older EntryActivity draft (all commented out)
│   ├── data/local/
│   │   ├── db/        AppDatabase, JsonImporter, RoomImporter (items.json → Room)
│   │   ├── dao/       ItemDao (prefix / author / title queries)
│   │   ├── entity/    ItemEntity, ItemFtsEntity, RawItem, mappers
│   │   └── repository/SearchRepository (FTS + Levenshtein fuzzy sort)
│   └── ui/search/     Compose SearchScreen + SearchViewModel (experimental)
└── res/layout/        activity_main, activity_second, entry_activity, activity_login
```

### Catalogue record format

Each entry in `items.json` looks like this:

```json
{
  "provider": "gemini",
  "model": "gemini-2.5-flash-lite",
  "title": "The Vanishing Half",
  "author": "Brit Bennett",
  "genres": "Historical Fiction, Literary Fiction, Family Saga",
  "themes": "Identity, Race, Family, Secrets",
  "motif": "Passing, Duality, Home, Memory",
  "category": "Social Commentary, Character Study, Generational Story",
  "time_period": "Mid-20th Century to Late 20th Century",
  "location": "Southern United States, California",
  "tags": "African American Experience, Racial Identity, Sisterhood, Social Mobility",
  "blurb": "Brit Bennett's 'The Vanishing Half' tells the story of …",
  "tokens_used": 462,
  "timestamp": "2025-11-14T13:44:14.406920"
}
```

`example_schema.txt` shows a richer target schema (nested `setting`, `review`, `year_published`,
`cover_image_url`) that the catalogue could grow into.

### Building

1. Install Android Studio (a recent stable release that supports AGP for `compileSdk 36`).
2. Create a Firebase project, add an Android app with package `com.blackbriar.musicsupervisor`,
   and put the downloaded `google-services.json` in `app/`. It's git-ignored, and the build will
   fail without it because the `google-services` plugin is applied.
3. In Firebase, enable **Anonymous** sign-in under Authentication.
4. Open the project in Android Studio and run on an emulator or device (API 24+).

On first launch of the entry screen, `items.json` is imported into Room. That takes a few seconds.

### Other files in the repo root

| File | What it is |
|------|------------|
| `TODOS.txt` | UI to-do list for the entry screen |
| `example_schema.txt` | Target JSON schema for a catalogue record |
| `Top100_2020` | "Author, Title" seed list, the first input to db_builder |
| `BISG_book_categories.txt` | BISG subject headings (reference list of book categories) |
| `Shortcuts` | Android Studio keyboard shortcuts cheat-sheet |
| `IconKitchen-Output.zip` | Source for the launcher icons |

---

## Roadmap

- [ ] Re-enable Save → Firestore (per-user library at `artifacts/manuscript/users/{uid}/books`)
- [ ] Finish Spotify PKCE login and token exchange
- [ ] Export a per-book VA curve from emotion-reader in an app-friendly format
- [ ] Export the music_mood track table (id, V, A, BPM, mode) for the app
- [ ] Nearest-VA track selection, with hysteresis so the music doesn't flip every page
- [ ] Chapter and drama-spike detection to choose when to change track
- [ ] Character detection, then leitmotif assignment
- [ ] Refresh `items.json` from db_builder (~52k books), ideally as a pre-built Room database

## Related repositories

* [**db_builder**](https://github.com/medusa606/db_builder): builds the LLM book-metadata catalogue
* [**emotion-reader**](https://github.com/medusa606/emotion-reader): emotion and VA analysis of book text
* [**music_mood**](https://github.com/medusa606/music_mood): VA analysis of soundtrack music
