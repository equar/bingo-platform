alter table agent_float_top_up_requests
    add column submission_note varchar(500) null after review_note;

-- Before this column existed, the submitter's note occupied review_note until
-- an admin review replaced it. Preserve the note only while the request is
-- still pending; reviewed rows contain the reviewer's decision note there.
update agent_float_top_up_requests
set submission_note = review_note
where status = 'PENDING' and review_note is not null;
