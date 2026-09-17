-- Optional catalog image for the ops dashboard's card view. NULL → the dashboard shows a placeholder.
ALTER TABLE products ADD COLUMN image_url VARCHAR(1024);
