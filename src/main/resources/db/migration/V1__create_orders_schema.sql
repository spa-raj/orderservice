CREATE TABLE orders (
    id               BINARY(16)     NOT NULL,
    created_at       DATETIME       NULL,
    last_modified_at DATETIME       NULL,
    is_deleted       BIT(1)         NOT NULL DEFAULT 0,
    created_by       BINARY(16)     NULL,
    last_modified_by BINARY(16)     NULL,
    version          INT            NULL,
    user_id          VARCHAR(255)   NOT NULL,
    status           VARCHAR(20)    NOT NULL,
    total_amount     DECIMAL(19,2)  NOT NULL,
    currency         VARCHAR(10)    NOT NULL DEFAULT 'INR',
    cart_event_id    VARCHAR(255)   NULL,
    CONSTRAINT pk_orders PRIMARY KEY (id)
);

CREATE TABLE order_items (
    id               BINARY(16)     NOT NULL,
    created_at       DATETIME       NULL,
    last_modified_at DATETIME       NULL,
    is_deleted       BIT(1)         NOT NULL DEFAULT 0,
    created_by       BINARY(16)     NULL,
    last_modified_by BINARY(16)     NULL,
    version          INT            NULL,
    order_id         BINARY(16)     NOT NULL,
    product_id       VARCHAR(255)   NOT NULL,
    product_name     VARCHAR(255)   NOT NULL,
    quantity         INT            NOT NULL,
    price            DECIMAL(19,2)  NOT NULL,
    currency         VARCHAR(10)    NOT NULL DEFAULT 'INR',
    CONSTRAINT pk_order_items PRIMARY KEY (id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders(id)
);

CREATE INDEX idx_order_user ON orders(user_id, is_deleted, created_at DESC);
CREATE UNIQUE INDEX idx_order_cart_event ON orders(cart_event_id);
CREATE INDEX idx_order_items_order ON order_items(order_id);
