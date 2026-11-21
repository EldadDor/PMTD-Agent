CREATE TABLE tmdb_candidate (
    media_type text NOT NULL CHECK (media_type IN ('movie', 'tv')),
    tmdb_id bigint NOT NULL CHECK (tmdb_id > 0),
    title text NOT NULL CHECK (length(trim(title)) > 0),
    primary_date date,
    original_language text CHECK (original_language ~ '^[a-z]{2}$'),
    genre_ids bigint[] NOT NULL DEFAULT '{}',
    origin_countries text[] NOT NULL DEFAULT '{}',
    imdb_id text CHECK (imdb_id ~ '^tt[0-9]+$'),
    catalog_title_id uuid REFERENCES catalog_title(id),
    link_status text NOT NULL CHECK (link_status IN ('linked', 'unmatched', 'conflict', 'not_checked')),
    first_seen_at timestamptz NOT NULL,
    last_seen_at timestamptz NOT NULL,
    PRIMARY KEY (media_type, tmdb_id),
    CHECK ((link_status = 'linked') = (catalog_title_id IS NOT NULL)),
    CHECK (link_status <> 'linked' OR imdb_id IS NOT NULL)
);
CREATE INDEX tmdb_candidate_catalog_idx ON tmdb_candidate(catalog_title_id);

CREATE TABLE tmdb_discovery_run (
    id uuid PRIMARY KEY,
    requested_from date NOT NULL,
    requested_to date NOT NULL,
    started_at timestamptz NOT NULL,
    completed_at timestamptz NOT NULL,
    movie_pages integer NOT NULL CHECK (movie_pages BETWEEN 1 AND 2),
    tv_pages integer NOT NULL CHECK (tv_pages BETWEEN 1 AND 2),
    movie_truncated boolean NOT NULL,
    tv_truncated boolean NOT NULL,
    CHECK (requested_from <= requested_to AND requested_to - requested_from <= 30)
);
