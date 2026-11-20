CREATE TABLE tvmaze_schedule_episode (
    country char(2) NOT NULL CHECK (country ~ '^[A-Z]{2}$'),
    episode_id bigint NOT NULL CHECK (episode_id > 0),
    show_id bigint NOT NULL CHECK (show_id > 0),
    show_name text,
    episode_name text,
    air_date date,
    source_url text,
    first_seen_at timestamptz NOT NULL,
    last_seen_at timestamptz NOT NULL,
    PRIMARY KEY (country, episode_id)
);
CREATE TABLE tvmaze_schedule_checkpoint (
    country char(2) NOT NULL CHECK (country ~ '^[A-Z]{2}$'),
    schedule_date date NOT NULL,
    last_success_at timestamptz NOT NULL,
    PRIMARY KEY (country, schedule_date)
);
