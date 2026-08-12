flashcard update package

Replace these 4 files in your GitHub repository:

1) app/src/main/java/com/vazheyar/app/ai/AiEnrichmentClient.kt
2) app/src/main/java/com/vazheyar/app/ai/EnrichmentWorker.kt
3) app/src/main/java/com/vazheyar/app/MainViewModel.kt
4) app/src/main/java/com/vazheyar/app/ui/VazheYarRoot.kt

Main changes:
- Gemini Interactions API v1
- Model: gemini-3.6-flash
- Structured JSON output with response_format
- Low thinking level for lower latency
- store=false for stateless requests
- Faster handling of non-retryable API errors
- Entire app UI changed to English / LTR
- Flashcards contain: English word, IPA pronunciation, common Persian meanings, one English example
- Persian translation of the example removed
- Existing database schema is preserved, so no Room migration is required

After replacing the files, commit to main. GitHub Actions will build the new APK automatically.
