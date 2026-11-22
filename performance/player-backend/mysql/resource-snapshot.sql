-- READ ONLY MySQL 8 resource snapshot. Run with an approved monitoring account
-- against the disposable instance; repeat at a documented sampling interval.
SELECT UTC_TIMESTAMP(6) AS sampled_at_utc, @@hostname AS mysql_host, @@port AS mysql_port;

SELECT VARIABLE_NAME, VARIABLE_VALUE
FROM performance_schema.global_status
WHERE VARIABLE_NAME IN (
  'Threads_connected', 'Threads_running', 'Connections', 'Aborted_connects',
  'Questions', 'Slow_queries', 'Innodb_row_lock_current_waits',
  'Innodb_row_lock_time', 'Innodb_row_lock_time_avg', 'Innodb_row_lock_time_max',
  'Innodb_deadlocks', 'Innodb_buffer_pool_reads', 'Innodb_buffer_pool_read_requests'
)
ORDER BY VARIABLE_NAME;

SELECT VARIABLE_NAME, VARIABLE_VALUE
FROM performance_schema.global_variables
WHERE VARIABLE_NAME IN (
  'max_connections', 'innodb_lock_wait_timeout', 'innodb_buffer_pool_size'
)
ORDER BY VARIABLE_NAME;

-- Current statements and lock-wait snapshot. Capture before/during/after a
-- contention run; avoid retaining SQL text if it contains sensitive data.
SELECT COUNT(*) AS current_client_threads FROM performance_schema.threads
WHERE TYPE = 'FOREGROUND';

SELECT COUNT(*) AS current_innodb_lock_waits
FROM performance_schema.data_lock_waits;
