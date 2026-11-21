CREATE TABLE rated_title_tmdb_metadata (
    catalog_title_id uuid PRIMARY KEY REFERENCES catalog_title(id),
    media_type text NOT NULL CHECK (media_type IN ('movie', 'tv')),
    tmdb_id bigint NOT NULL CHECK (tmdb_id > 0),
    genre_ids bigint[] NOT NULL DEFAULT '{}',
    original_language text CHECK (original_language ~ '^[a-z]{2}$'),
    origin_countries text[] NOT NULL DEFAULT '{}',
    observed_at timestamptz NOT NULL,
    UNIQUE (media_type, tmdb_id)
);
CREATE TABLE rated_title_enrichment_attempt (
    catalog_title_id uuid PRIMARY KEY REFERENCES catalog_title(id),
    outcome text NOT NULL CHECK (outcome IN ('matched', 'unmatched', 'conflict')),
    attempted_at timestamptz NOT NULL
);
