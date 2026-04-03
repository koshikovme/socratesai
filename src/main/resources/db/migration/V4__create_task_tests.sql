CREATE TABLE IF NOT EXISTS task_tests (
  id BIGSERIAL PRIMARY KEY,
  task_id BIGINT NOT NULL,
  input_data TEXT NOT NULL,
  expected_output TEXT NOT NULL,
  hidden BOOLEAN NOT NULL,

  CONSTRAINT fk_task_tests_task
      FOREIGN KEY (task_id) REFERENCES tasks(id)
          ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_task_tests_task_id
    ON task_tests(task_id);