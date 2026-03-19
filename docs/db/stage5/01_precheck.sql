PROMPT ==============================
PROMPT Stage 5 Precheck - duplicates
PROMPT ==============================

SELECT member_id, COUNT(*) AS duplicate_count
FROM members
GROUP BY member_id
HAVING COUNT(*) > 1;

SELECT member_email, COUNT(*) AS duplicate_count
FROM members
WHERE member_email IS NOT NULL
GROUP BY member_email
HAVING COUNT(*) > 1;

SELECT emp_id, COUNT(*) AS duplicate_count
FROM employees
GROUP BY emp_id
HAVING COUNT(*) > 1;

SELECT emp_email, COUNT(*) AS duplicate_count
FROM employees
WHERE emp_email IS NOT NULL
GROUP BY emp_email
HAVING COUNT(*) > 1;

PROMPT =============================
PROMPT Stage 5 Precheck - orphan row
PROMPT =============================

SELECT c.member_num, c.goods_num
FROM cart c
LEFT JOIN goods g ON g.goods_num = c.goods_num
WHERE g.goods_num IS NULL;

SELECT gi.goods_num, gi.ipgo_num
FROM goods_ipgo gi
LEFT JOIN goods g ON g.goods_num = gi.goods_num
WHERE g.goods_num IS NULL;

SELECT g.goods_num, g.emp_num
FROM goods g
LEFT JOIN employees e ON e.emp_num = g.emp_num
WHERE g.emp_num IS NOT NULL
  AND e.emp_num IS NULL;

SELECT g.goods_num, g.update_emp_num
FROM goods g
LEFT JOIN employees e ON e.emp_num = g.update_emp_num
WHERE g.update_emp_num IS NOT NULL
  AND e.emp_num IS NULL;

SELECT r.review_num, r.purchase_num
FROM reviews r
LEFT JOIN purchase p ON p.purchase_num = r.purchase_num
WHERE p.purchase_num IS NULL;

PROMPT ===================================
PROMPT Stage 5 Precheck - invalid values
PROMPT ===================================

SELECT *
FROM cart
WHERE cart_qty IS NOT NULL
  AND cart_qty <= 0;

SELECT *
FROM goods
WHERE goods_price IS NOT NULL
  AND goods_price < 0;

SELECT *
FROM goods_ipgo
WHERE (ipgo_qty IS NOT NULL AND ipgo_qty = 0)
   OR (ipgo_price IS NOT NULL AND ipgo_price < 0);

SELECT *
FROM purchase
WHERE purchase_total < 0;

SELECT *
FROM purchase_list
WHERE purchase_qty <= 0
   OR purchase_price < 0;

SELECT *
FROM reviews
WHERE review_rating NOT BETWEEN 1 AND 5
   OR review_status NOT IN ('PUBLISHED', 'HIDDEN');
