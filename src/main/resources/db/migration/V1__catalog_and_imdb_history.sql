CREATE TABLE catalog_title (
    id uuid PRIMARY KEY,
    display_title text NOT NULL CHECK (length(trim(display_title)) > 0),
    kind text NOT NULL CHECK (kind IN ('movie', 'series', 'other', 'unknown')),
    release_year integer CHECK (release_year BETWEEN 1800 AND 9999),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE TABLE title_external_id (
    namespace text NOT NULL CHECK (namespace IN (
        'imdb_title', 'tmdb_movie', 'tmdb_tv', 'tvmaze_show'
    )),
    external_id text NOT NULL CHECK (length(trim(external_id)) > 0),
    title_id uuid NOT NULL REFERENCES catalog_title(id),
    observed_from text NOT NULL CHECK (length(trim(observed_from)) > 0),
    first_seen_at timestamptz NOT NULL,
    last_seen_at timestamptz NOT NULL,
    PRIMARY KEY (namespace, external_id),
    UNIQUE (title_id, namespace),
    CHECK (namespace <> 'imdb_title' OR external_id ~ '^tt[0-9]+$')
);
CREATE INDEX title_external_id_title_idx ON title_external_id(title_id);

CREATE TABLE imdb_import_batch (
    id uuid PRIMARY KEY,
    content_sha256 text NOT NULL UNIQUE CHECK (content_sha256 ~ '^[0-9a-f]{64}$'),
    imported_at timestamptz NOT NULL,
    total_rows integer NOT NULL CHECK (total_rows >= 0),
    inserted_rows integer NOT NULL CHECK (inserted_rows >= 0),
    updated_rows integer NOT NULL CHECK (updated_rows >= 0),
    unchanged_rows integer NOT NULL CHECK (unchanged_rows >= 0),
    invalid_rows integer NOT NULL CHECK (invalid_rows >= 0),
    CHECK (total_rows = inserted_rows + updated_rows + unchanged_rows + invalid_rows)
);

CREATE TABLE imported_rating (
    imdb_id text PRIMARY KEY CHECK (imdb_id ~ '^tt[0-9]+$'),
    namespace text NOT NULL DEFAULT 'imdb_title' CHECK (namespace = 'imdb_title'),
    rating smallint NOT NULL CHECK (rating BETWEEN 1 AND 10),
    rated_on date,
    first_import_id uuid NOT NULL REFERENCES imdb_import_batch(id),
    last_import_id uuid NOT NULL REFERENCES imdb_import_batch(id),
    last_source_row integer NOT NULL CHECK (last_source_row > 0),
    updated_at timestamptz NOT NULL,
    FOREIGN KEY (namespace, imdb_id) REFERENCES title_external_id(namespace, external_id)
);
