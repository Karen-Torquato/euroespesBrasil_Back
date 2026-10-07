UPDATE produtos
SET estoque_minimo = 5
WHERE estoque_minimo IS NULL;

ALTER TABLE produtos
    ALTER COLUMN estoque_minimo SET DEFAULT 5;

ALTER TABLE produtos
    ALTER COLUMN estoque_minimo SET NOT NULL;
