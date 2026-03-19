PROMPT ==========================================
PROMPT Stage 5 Cleanup - orphan review delete
PROMPT ==========================================

SELECT COUNT(*) AS orphan_review_count
FROM reviews r
WHERE NOT EXISTS (
    SELECT 1
    FROM purchase p
    WHERE p.purchase_num = r.purchase_num
);

DELETE FROM reviews r
WHERE NOT EXISTS (
    SELECT 1
    FROM purchase p
    WHERE p.purchase_num = r.purchase_num
);

COMMIT;
