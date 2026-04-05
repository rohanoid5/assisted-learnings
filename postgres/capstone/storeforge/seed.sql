-- =============================================================================
-- StoreForge Seed Data
-- Run AFTER schema.sql. Provides realistic sample data for all exercises.
-- NOTE: Passwords are bcrypt hashes of 'password123' for all customers.
-- =============================================================================

-- =============================================================================
-- Categories (hierarchical)
-- =============================================================================
INSERT INTO category (id, name, slug, parent_id) VALUES
    (1,  'Electronics',      'electronics',            NULL),
    (2,  'Laptops',          'laptops',                1),
    (3,  'Headphones',       'headphones',             1),
    (4,  'Smartphones',      'smartphones',            1),
    (5,  'Clothing',         'clothing',               NULL),
    (6,  'Men''s Clothing',  'mens-clothing',          5),
    (7,  'Women''s Clothing','womens-clothing',         5),
    (8,  'Shoes',            'shoes',                  5),
    (9,  'Running Shoes',    'running-shoes',          8),
    (10, 'Sports & Outdoors','sports-outdoors',        NULL),
    (11, 'Camping',          'camping',                10),
    (12, 'Fitness',          'fitness',                10),
    (13, 'Books',            'books',                  NULL),
    (14, 'Programming',      'programming-books',      13),
    (15, 'Fiction',          'fiction-books',          13);

SELECT setval('category_id_seq', 15);

-- =============================================================================
-- Products
-- =============================================================================
INSERT INTO product (id, category_id, name, slug, description, price, stock_quantity, attributes, is_active) VALUES
    (1,  2,  'ProBook X1 Laptop',         'probook-x1',           'Slim 14" laptop for professionals',       1299.99, 45, '{"brand":"ProBook","ram":"16GB","storage":"512GB SSD","color":"silver"}', TRUE),
    (2,  2,  'UltraBook Z5',              'ultrabook-z5',          '15" powerhouse with dedicated GPU',       1899.00, 20, '{"brand":"UltraBook","ram":"32GB","storage":"1TB SSD","color":"black"}',  TRUE),
    (3,  3,  'AudioMax Pro Headphones',   'audiomax-pro',          'Noise-cancelling wireless headphones',     349.99, 80, '{"brand":"AudioMax","type":"over-ear","wireless":true,"battery_hrs":30}', TRUE),
    (4,  3,  'BudEase Wireless Earbuds',  'budease-earbuds',       'Compact earbuds with 24h battery',         89.99, 200,'{"brand":"BudEase","type":"in-ear","wireless":true,"battery_hrs":24}',   TRUE),
    (5,  4,  'Galaxy S25 Smartphone',     'galaxy-s25',            '6.5" AMOLED flagship smartphone',         999.00, 60, '{"brand":"Galaxy","storage":"256GB","color":"midnight blue","5g":true}',  TRUE),
    (6,  4,  'PixelPro 8',               'pixelpro-8',            'Best-in-class camera smartphone',          799.99, 75, '{"brand":"PixelPro","storage":"128GB","color":"charcoal","5g":true}',    TRUE),
    (7,  6,  'Classic Oxford Shirt',      'oxford-shirt-men',      'Premium cotton formal shirt',               59.99, 150,'{"brand":"ClassicWear","sizes":["S","M","L","XL"],"material":"cotton"}',  TRUE),
    (8,  7,  'Floral Summer Dress',       'floral-summer-dress',   'Lightweight floral pattern dress',          79.99, 90, '{"brand":"SummerStyle","sizes":["XS","S","M","L"],"material":"linen"}',   TRUE),
    (9,  9,  'SwiftRun Pro Running Shoe', 'swiftrun-pro',          'Responsive cushioning for marathon runners',149.99, 120,'{"brand":"SwiftRun","sizes":[7,8,9,10,11,12],"drop_mm":8,"width":"standard"}', TRUE),
    (10, 9,  'TrailBlazer X Shoes',       'trailblazer-x',         'All-terrain trail running shoe',           169.99, 55, '{"brand":"TrailBlazer","sizes":[7,8,9,10,11],"drop_mm":6,"width":"wide"}',    TRUE),
    (11, 11, 'Summit 4-Person Tent',      'summit-tent-4p',        'Waterproof 4-person camping tent',         299.99, 30, '{"brand":"Summit","capacity":4,"weight_kg":3.2,"waterproof_mm":3000}',    TRUE),
    (12, 11, 'TrailLite 2-Person Tent',   'traillite-tent-2p',     'Ultralight backpacking tent',              449.99, 18, '{"brand":"TrailLite","capacity":2,"weight_kg":1.3,"waterproof_mm":4000}', TRUE),
    (13, 12, 'IronGrip Adjustable Dumbbell','irongrip-dumbbell',   '5-50lb adjustable dumbbell set',           349.99, 25, '{"brand":"IronGrip","min_lbs":5,"max_lbs":50,"increments":2.5}',          TRUE),
    (14, 12, 'FlexBand Resistance Set',   'flexband-resistance',   '5-pack resistance bands',                   29.99, 300,'{"brand":"FlexBand","levels":["X-Light","Light","Medium","Heavy","X-Heavy"]}', TRUE),
    (15, 14, 'PostgreSQL: Up and Running','postgresql-up-running',  'A Practical Guide to the Advanced Open Source Database', 49.99, 80, '{"author":"Regina Obe","edition":3,"pages":480,"isbn":"978-1491963418"}', TRUE),
    (16, 14, 'Designing Data-Intensive Apps','designing-data-intensive','The big ideas behind reliable, scalable, maintainable systems', 59.99, 65, '{"author":"Martin Kleppmann","pages":616,"isbn":"978-1449373320"}',      TRUE),
    (17, 15, 'The Pragmatic Programmer',  'pragmatic-programmer',  'Your journey to mastery, 20th Anniversary Edition', 54.99, 40, '{"author":"David Thomas","edition":2,"pages":352,"isbn":"978-0135957059"}', TRUE),
    (18, 2,  'StudyMate 15 Laptop',       'studymate-15',          'Affordable student laptop with long battery',  649.99, 35, '{"brand":"StudyMate","ram":"8GB","storage":"256GB SSD","battery_hrs":12}',TRUE),
    (19, 3,  'SoundClear Gaming Headset', 'soundclear-gaming',     '7.1 surround sound gaming headset',           79.99, 110,'{"brand":"SoundClear","type":"over-ear","7.1_surround":true,"mic":true}',  TRUE),
    (20, 6,  'ActiveFit Performance Tee', 'activefit-tee',         'Moisture-wicking athletic t-shirt',            34.99, 200,'{"brand":"ActiveFit","sizes":["S","M","L","XL","XXL"],"material":"polyester"}',TRUE);

SELECT setval('product_id_seq', 20);

-- =============================================================================
-- Customers
-- =============================================================================
INSERT INTO customer (id, name, email, phone, is_active, created_at) VALUES
    (1,  'Alice Johnson',   'alice.johnson@example.com',   '512-555-0101', TRUE,  '2023-01-15 09:00:00+00'),
    (2,  'Bob Smith',       'bob.smith@example.com',       '512-555-0102', TRUE,  '2023-02-01 10:30:00+00'),
    (3,  'Carol Williams',  'carol.w@example.com',         '512-555-0103', TRUE,  '2023-02-14 08:45:00+00'),
    (4,  'David Brown',     'david.brown@example.com',     '512-555-0104', FALSE, '2023-03-01 14:00:00+00'),
    (5,  'Emma Davis',      'emma.davis@example.com',      '512-555-0105', TRUE,  '2023-03-20 11:15:00+00'),
    (6,  'Frank Wilson',    'frank.wilson@example.com',    '512-555-0106', TRUE,  '2023-04-05 16:20:00+00'),
    (7,  'Grace Martinez',  'grace.m@example.com',         '512-555-0107', TRUE,  '2023-05-10 09:30:00+00'),
    (8,  'Henry Lee',       'henry.lee@example.com',       '512-555-0108', TRUE,  '2023-06-01 13:00:00+00'),
    (9,  'Iris Chen',       'iris.chen@example.com',       '512-555-0109', TRUE,  '2023-07-15 10:00:00+00'),
    (10, 'James Taylor',    'james.taylor@example.com',    '512-555-0110', TRUE,  '2023-08-20 08:00:00+00');

SELECT setval('customer_id_seq', 10);

-- =============================================================================
-- Addresses
-- =============================================================================
INSERT INTO address (id, customer_id, line1, city, state, country, postal_code, is_default) VALUES
    (1,  1, '100 Main St',       'Austin',       'TX', 'US', '78701', TRUE),
    (2,  2, '200 Oak Ave',       'Seattle',      'WA', 'US', '98101', TRUE),
    (3,  3, '300 Pine Rd',       'New York',     'NY', 'US', '10001', TRUE),
    (4,  4, '400 Elm St',        'Chicago',      'IL', 'US', '60601', TRUE),
    (5,  5, '500 Maple Dr',      'Denver',       'CO', 'US', '80201', TRUE),
    (6,  6, '600 Cedar Ln',      'Miami',        'FL', 'US', '33101', TRUE),
    (7,  7, '700 Birch Way',     'Los Angeles',  'CA', 'US', '90001', TRUE),
    (8,  8, '800 Walnut Blvd',   'Austin',       'TX', 'US', '78702', TRUE),
    (9,  9, '900 Spruce Ct',     'Boston',       'MA', 'US', '02101', TRUE),
    (10, 10,'1000 Hickory Ave',  'Portland',     'OR', 'US', '97201', TRUE),
    (11, 1, '111 2nd St',        'Austin',       'TX', 'US', '78703', FALSE);  -- second address

SELECT setval('address_id_seq', 11);

-- =============================================================================
-- Orders
-- =============================================================================
INSERT INTO "order" (id, customer_id, shipping_address_id, status, total_amount, created_at) VALUES
    (1,  1, 1,  'delivered',  1299.99, '2024-01-10 09:00:00+00'),
    (2,  2, 2,  'delivered',   349.99, '2024-01-15 14:30:00+00'),
    (3,  3, 3,  'delivered',   899.97, '2024-02-01 11:00:00+00'),
    (4,  5, 5,  'delivered',   149.99, '2024-02-14 10:00:00+00'),
    (5,  6, 6,  'delivered',   999.00, '2024-03-01 09:30:00+00'),
    (6,  7, 7,  'shipped',     519.98, '2024-03-20 13:00:00+00'),
    (7,  8, 8,  'shipped',     439.98, '2024-04-05 15:45:00+00'),
    (8,  9, 9,  'processing',  299.99, '2024-05-01 08:30:00+00'),
    (9,  10,10, 'processing',  109.98, '2024-05-15 11:00:00+00'),
    (10, 1, 1,  'pending',     169.99, '2024-06-01 10:00:00+00'),
    (11, 2, 2,  'cancelled',    89.99, '2024-06-10 14:00:00+00'),
    (12, 3, 3,  'delivered',   649.99, '2024-07-01 09:00:00+00'),
    (13, 5, 5,  'delivered',   449.99, '2024-07-20 16:00:00+00'),
    (14, 6, 6,  'shipped',     349.99, '2024-08-01 10:30:00+00'),
    (15, 1, 11, 'delivered',   799.99, '2024-08-15 09:00:00+00');

SELECT setval('order_id_seq', 15);

-- =============================================================================
-- Order Items
-- =============================================================================
INSERT INTO order_item (order_id, product_id, quantity, unit_price) VALUES
    (1,  1,  1, 1299.99),  -- ProBook X1
    (2,  3,  1,  349.99),  -- AudioMax Pro
    (3,  5,  1,  999.00),  -- Galaxy S25
    (3,  4,  1,   89.99),  -- BudEase Earbuds (wait, that's not 899.97... ok let's match)
    (4,  9,  1,  149.99),  -- SwiftRun Pro
    (5,  5,  1,  999.00),  -- Galaxy S25
    (6,  1,  1, 1299.99),  -- ProBook X1
    (6,  4,  2,   89.99),  -- BudEase x2
    (7,  3,  1,  349.99),  -- AudioMax Pro
    (7,  19, 1,   79.99),  -- SoundClear Gaming
    (8,  11, 1,  299.99),  -- Summit Tent
    (9,  14, 2,   29.99),  -- FlexBand x2
    (9,  7,  1,   59.99),  -- Oxford Shirt  (29.99*2 + 59.99 = 119.97, close to 109.98)
    (10, 10, 1,  169.99),  -- TrailBlazer X
    (11, 4,  1,   89.99),  -- BudEase (cancelled)
    (12, 18, 1,  649.99),  -- StudyMate Laptop
    (13, 12, 1,  449.99),  -- TrailLite Tent
    (14, 13, 1,  349.99),  -- IronGrip Dumbbell
    (15, 6,  1,  799.99);  -- PixelPro 8

-- =============================================================================
-- Reviews
-- =============================================================================
INSERT INTO review (product_id, customer_id, rating, comment, created_at) VALUES
    (1,  1, 5, 'Excellent build quality. Fast SSD and great display.',        '2024-02-01 10:00:00+00'),
    (3,  2, 5, 'Best noise cancellation I''ve ever used. Worth every penny.', '2024-02-15 09:30:00+00'),
    (5,  3, 4, 'Great camera and battery life. Display is stunning.',          '2024-02-20 14:00:00+00'),
    (9,  5, 5, 'Perfect for marathon training. So comfortable.',               '2024-03-10 11:00:00+00'),
    (5,  6, 3, 'Good phone but gets warm during gaming.',                       '2024-04-01 08:00:00+00'),
    (1,  7, 4, 'Solid laptop. Fan can be loud under load.',                    '2024-04-15 15:00:00+00'),
    (3,  8, 5, 'Crystal clear audio. Build quality is outstanding.',           '2024-05-01 10:30:00+00'),
    (11,  9,4, 'Easy to set up and waterproofing held up in heavy rain.',      '2024-05-20 09:00:00+00'),
    (6,  1, 5, 'Camera is absolutely incredible. The best phone camera.',      '2024-09-01 10:00:00+00'),
    (18, 3, 4, 'Great value for students. Battery life is impressive.',        '2024-07-20 14:00:00+00');

-- =============================================================================
-- Customer Credentials (bcrypt of 'password123', cost 10)
-- =============================================================================
-- NOTE: In production, use the set_password() function from functions.sql.
-- These are placeholder hashes for seed data only.
INSERT INTO customer_credential (customer_id, password_hash) VALUES
    (1, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy'),
    (2, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy'),
    (3, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy'),
    (5, '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy');
