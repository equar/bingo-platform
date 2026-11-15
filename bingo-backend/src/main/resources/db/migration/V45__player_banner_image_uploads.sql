alter table player_announcements
    add column image_data mediumblob null,
    add column image_mime_type varchar(40) null;
