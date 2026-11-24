CREATE VIEW user_title_feedback AS
WITH latest_by_kind AS (
    SELECT DISTINCT ON (i.media_type, i.tmdb_id, (f.action = 'watched'))
        i.media_type, i.tmdb_id, f.action
    FROM feedback_event f
    JOIN notification_item i ON i.id = f.item_id
    ORDER BY i.media_type, i.tmdb_id, (f.action = 'watched'),
        f.occurred_at DESC, f.event_id DESC
)
SELECT media_type, tmdb_id,
    max(action) FILTER (WHERE action <> 'watched') AS interest_action,
    bool_or(action = 'watched') AS watched
FROM latest_by_kind
GROUP BY media_type, tmdb_id;
