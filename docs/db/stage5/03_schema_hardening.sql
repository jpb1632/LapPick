PROMPT =====================================
PROMPT Stage 5 Schema Hardening - sequences
PROMPT =====================================

DECLARE
    v_start_with NUMBER;
BEGIN
    SELECT NVL(MAX(TO_NUMBER(REGEXP_SUBSTR(member_num, '[0-9]+$'))), 100000) + 1
      INTO v_start_with
      FROM members
     WHERE REGEXP_LIKE(member_num, '^mem_[0-9]+$');

    BEGIN
        EXECUTE IMMEDIATE 'DROP SEQUENCE MEMBER_NUM_SEQ';
    EXCEPTION
        WHEN OTHERS THEN
            IF SQLCODE != -2289 THEN
                RAISE;
            END IF;
    END;

    EXECUTE IMMEDIATE 'CREATE SEQUENCE MEMBER_NUM_SEQ START WITH ' || v_start_with
        || ' INCREMENT BY 1 MINVALUE 1 NOCACHE NOCYCLE';
END;
/

DECLARE
    v_start_with NUMBER;
BEGIN
    SELECT NVL(MAX(TO_NUMBER(REGEXP_SUBSTR(emp_num, '[0-9]+$'))), 100000) + 1
      INTO v_start_with
      FROM employees
     WHERE REGEXP_LIKE(emp_num, '^emp_[0-9]+$');

    BEGIN
        EXECUTE IMMEDIATE 'DROP SEQUENCE EMP_NUM_SEQ';
    EXCEPTION
        WHEN OTHERS THEN
            IF SQLCODE != -2289 THEN
                RAISE;
            END IF;
    END;

    EXECUTE IMMEDIATE 'CREATE SEQUENCE EMP_NUM_SEQ START WITH ' || v_start_with
        || ' INCREMENT BY 1 MINVALUE 1 NOCACHE NOCYCLE';
END;
/

DECLARE
    v_start_with NUMBER;
BEGIN
    SELECT NVL(MAX(TO_NUMBER(REGEXP_SUBSTR(goods_num, '[0-9]+$'))), 100000) + 1
      INTO v_start_with
      FROM goods
     WHERE REGEXP_LIKE(goods_num, '^goods_[0-9]+$');

    BEGIN
        EXECUTE IMMEDIATE 'DROP SEQUENCE GOODS_NUM_SEQ';
    EXCEPTION
        WHEN OTHERS THEN
            IF SQLCODE != -2289 THEN
                RAISE;
            END IF;
    END;

    EXECUTE IMMEDIATE 'CREATE SEQUENCE GOODS_NUM_SEQ START WITH ' || v_start_with
        || ' INCREMENT BY 1 MINVALUE 1 NOCACHE NOCYCLE';
END;
/

DECLARE
    v_start_with NUMBER;
BEGIN
    SELECT NVL(MAX(cart_num), 0) + 1
      INTO v_start_with
      FROM cart;

    BEGIN
        EXECUTE IMMEDIATE 'DROP SEQUENCE CART_NUM_SEQ';
    EXCEPTION
        WHEN OTHERS THEN
            IF SQLCODE != -2289 THEN
                RAISE;
            END IF;
    END;

    EXECUTE IMMEDIATE 'CREATE SEQUENCE CART_NUM_SEQ START WITH ' || v_start_with
        || ' INCREMENT BY 1 MINVALUE 1 NOCACHE NOCYCLE';
END;
/

PROMPT ===============================================
PROMPT Stage 5 Schema Hardening - unique constraints
PROMPT ===============================================

ALTER TABLE members
    ADD CONSTRAINT uk_members_member_id UNIQUE (member_id);

ALTER TABLE members
    ADD CONSTRAINT uk_members_member_email UNIQUE (member_email);

ALTER TABLE employees
    ADD CONSTRAINT uk_employees_emp_id UNIQUE (emp_id);

ALTER TABLE employees
    ADD CONSTRAINT uk_employees_emp_email UNIQUE (emp_email);

PROMPT ============================================
PROMPT Stage 5 Schema Hardening - foreign keys
PROMPT ============================================

ALTER TABLE cart
    ADD CONSTRAINT fk_cart_goods
    FOREIGN KEY (goods_num)
    REFERENCES goods (goods_num)
    ON DELETE CASCADE;

ALTER TABLE goods_ipgo
    ADD CONSTRAINT fk_goods_ipgo_goods
    FOREIGN KEY (goods_num)
    REFERENCES goods (goods_num)
    ON DELETE CASCADE;

ALTER TABLE goods
    ADD CONSTRAINT fk_goods_emp
    FOREIGN KEY (emp_num)
    REFERENCES employees (emp_num)
    ON DELETE SET NULL;

ALTER TABLE goods
    ADD CONSTRAINT fk_goods_update_emp
    FOREIGN KEY (update_emp_num)
    REFERENCES employees (emp_num)
    ON DELETE SET NULL;

ALTER TABLE reviews
    ADD CONSTRAINT fk_reviews_purchase
    FOREIGN KEY (purchase_num)
    REFERENCES purchase (purchase_num)
    ON DELETE CASCADE;

PROMPT ============================================
PROMPT Stage 5 Schema Hardening - check constraints
PROMPT ============================================

ALTER TABLE members
    ADD CONSTRAINT ck_members_point_nonnegative
    CHECK (member_point >= 0) ENABLE;

ALTER TABLE goods
    ADD CONSTRAINT ck_goods_price_nonnegative
    CHECK (goods_price >= 0) ENABLE;

ALTER TABLE cart
    ADD CONSTRAINT ck_cart_qty_positive
    CHECK (cart_qty > 0) ENABLE;

ALTER TABLE goods_ipgo
    ADD CONSTRAINT ck_goods_ipgo_qty_nonzero
    CHECK (ipgo_qty <> 0) ENABLE;

ALTER TABLE goods_ipgo
    ADD CONSTRAINT ck_goods_ipgo_price_nonnegative
    CHECK (ipgo_price >= 0) ENABLE;

ALTER TABLE purchase
    ADD CONSTRAINT ck_purchase_total_nonnegative
    CHECK (purchase_total >= 0) ENABLE;

ALTER TABLE purchase_list
    ADD CONSTRAINT ck_purchase_list_qty_positive
    CHECK (purchase_qty > 0) ENABLE;

ALTER TABLE purchase_list
    ADD CONSTRAINT ck_purchase_list_price_nonnegative
    CHECK (purchase_price >= 0) ENABLE;

ALTER TABLE reviews
    ADD CONSTRAINT ck_reviews_rating_range
    CHECK (review_rating BETWEEN 1 AND 5) ENABLE;

ALTER TABLE reviews
    ADD CONSTRAINT ck_reviews_status
    CHECK (review_status IN ('PUBLISHED', 'HIDDEN')) ENABLE;
