# Next phases

1. **Phase 1A — Foundation.** Finish foundation acceptance. Depends on Phase 0
   closure and the evidence for the application scaffold and PostgreSQL V1
   already recorded in the implementation history.
2. **Phase 1B — IMDb history import.** Import a local ratings export safely and
   idempotently. Depends on an actual export's header and the persistence layer.
3. **Phase 1C — Discovery and scheduled polling.** Start with one source and a
   manual dry run. Depends on reviewed provider terms and access, catalog
   identity rules, and later approval for scheduling.
