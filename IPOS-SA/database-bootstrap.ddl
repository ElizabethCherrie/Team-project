CREATE TABLE IF NOT EXISTS merchants (
    merchant_id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    address TEXT NOT NULL,
    phone VARCHAR(64),
    credit_limit DECIMAL(12,2) NOT NULL,
    balance DECIMAL(12,2) NOT NULL DEFAULT 0,
    account_status VARCHAR(64) NOT NULL,
    discount_type VARCHAR(64),
    fixed_discount_rate DECIMAL(8,2) NOT NULL DEFAULT 0,
    flexible_rate_tier1 DECIMAL(8,2) NOT NULL DEFAULT 1,
    flexible_rate_tier2 DECIMAL(8,2) NOT NULL DEFAULT 2,
    flexible_rate_tier3 DECIMAL(8,2) NOT NULL DEFAULT 3,
    pending_discount_credit DECIMAL(12,2) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS products (
    product_id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    stock_level INT NOT NULL,
    minimum_stock_level INT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS users (
    username VARCHAR(128) PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(64) NOT NULL,
    merchant_id VARCHAR(64),
    active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS orders (
    order_id INT NOT NULL AUTO_INCREMENT,
    merchant_id VARCHAR(64) NOT NULL,
    order_date DATETIME NOT NULL,
    status VARCHAR(64) NOT NULL,
    subtotal DECIMAL(12,2) NOT NULL,
    discount_amount DECIMAL(12,2) NOT NULL,
    total_amount DECIMAL(12,2) NOT NULL,
    dispatched_by VARCHAR(128),
    dispatch_date DATETIME,
    courier VARCHAR(255),
    tracking_number VARCHAR(255),
    expected_delivery DATETIME,
    delivered_date DATETIME,
    PRIMARY KEY (order_id),
    CONSTRAINT fk_orders_merchant
        FOREIGN KEY (merchant_id) REFERENCES merchants(merchant_id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS order_items (
    order_item_id INT NOT NULL AUTO_INCREMENT,
    order_id INT NOT NULL,
    product_id VARCHAR(64) NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,
    line_total DECIMAL(12,2) NOT NULL,
    PRIMARY KEY (order_item_id),
    CONSTRAINT fk_order_items_order
        FOREIGN KEY (order_id) REFERENCES orders(order_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_order_items_product
        FOREIGN KEY (product_id) REFERENCES products(product_id)
);

CREATE TABLE IF NOT EXISTS invoices (
    invoice_id INT NOT NULL AUTO_INCREMENT,
    order_id INT NOT NULL UNIQUE,
    merchant_id VARCHAR(64) NOT NULL,
    issue_date DATE NOT NULL,
    due_date DATE NOT NULL,
    total_amount DECIMAL(12,2) NOT NULL,
    paid_amount DECIMAL(12,2) NOT NULL DEFAULT 0,
    status VARCHAR(64) NOT NULL,
    PRIMARY KEY (invoice_id),
    CONSTRAINT fk_invoices_order
        FOREIGN KEY (order_id) REFERENCES orders(order_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_invoices_merchant
        FOREIGN KEY (merchant_id) REFERENCES merchants(merchant_id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS payments (
    payment_id INT NOT NULL AUTO_INCREMENT,
    merchant_id VARCHAR(64) NOT NULL,
    amount DECIMAL(12,2) NOT NULL,
    method VARCHAR(64) NOT NULL,
    reference VARCHAR(255),
    payment_date DATETIME NOT NULL,
    notes TEXT,
    PRIMARY KEY (payment_id),
    CONSTRAINT fk_payments_merchant
        FOREIGN KEY (merchant_id) REFERENCES merchants(merchant_id)
        ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS stock_movements (
    movement_id INT NOT NULL AUTO_INCREMENT,
    product_id VARCHAR(64) NOT NULL,
    movement_type VARCHAR(64) NOT NULL,
    quantity INT NOT NULL,
    happened_at DATETIME NOT NULL,
    reference_type VARCHAR(64),
    reference_id VARCHAR(64),
    PRIMARY KEY (movement_id),
    CONSTRAINT fk_stock_movements_product
        FOREIGN KEY (product_id) REFERENCES products(product_id)
);

CREATE TABLE IF NOT EXISTS email_log (
    email_id INT NOT NULL AUTO_INCREMENT,
    recipient VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    sent_at DATETIME NOT NULL,
    delivery_mode VARCHAR(64) NOT NULL,
    PRIMARY KEY (email_id)
);

CREATE TABLE IF NOT EXISTS non_commercial_applications (
    application_id INT NOT NULL AUTO_INCREMENT,
    email VARCHAR(255) NOT NULL,
    status VARCHAR(64) NOT NULL,
    generated_password VARCHAR(255),
    outcome_message TEXT,
    created_at DATETIME NOT NULL,
    processed_at DATETIME,
    PRIMARY KEY (application_id)
);

CREATE TABLE IF NOT EXISTS sessions (
    session_token VARCHAR(64) PRIMARY KEY,
    username VARCHAR(128) NOT NULL,
    role VARCHAR(64) NOT NULL,
    merchant_id VARCHAR(64),
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_sessions_user
        FOREIGN KEY (username) REFERENCES users(username)
        ON DELETE CASCADE
);
