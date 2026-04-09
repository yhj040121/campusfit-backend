SET NAMES utf8mb4;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS message_notification;
DROP TABLE IF EXISTS creator_withdraw_request;
DROP TABLE IF EXISTS commission_record;
DROP TABLE IF EXISTS creator_cooperation;
DROP TABLE IF EXISTS merchant;
DROP TABLE IF EXISTS post_activity_binding;
DROP TABLE IF EXISTS user_activity_join;
DROP TABLE IF EXISTS official_announcement;
DROP TABLE IF EXISTS activity_topic;
DROP TABLE IF EXISTS post_comment;
DROP TABLE IF EXISTS post_like;
DROP TABLE IF EXISTS post_favorite;
DROP TABLE IF EXISTS user_follow;
DROP TABLE IF EXISTS product_link_click;
DROP TABLE IF EXISTS product_link;
DROP TABLE IF EXISTS post_tag;
DROP TABLE IF EXISTS post_image;
DROP TABLE IF EXISTS post;
DROP TABLE IF EXISTS tag_option;
DROP TABLE IF EXISTS user_profile;
DROP TABLE IF EXISTS sys_admin_user;
DROP TABLE IF EXISTS app_user;
SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE app_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    phone VARCHAR(20) NOT NULL UNIQUE,
    nickname VARCHAR(50) NOT NULL,
    avatar_url VARCHAR(255),
    password_hash VARCHAR(255),
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_profile (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    school_name VARCHAR(100),
    grade_name VARCHAR(50),
    signature VARCHAR(255),
    avatar_text VARCHAR(10),
    avatar_class VARCHAR(20),
    cover_image_url VARCHAR(255),
    gender VARCHAR(20),
    email VARCHAR(120),
    location_name VARCHAR(100),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_profile_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tag_option (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    category VARCHAR(30) NOT NULL,
    option_value VARCHAR(50) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_tag_option (category, option_value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE post (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    post_code VARCHAR(32) UNIQUE,
    user_id BIGINT NOT NULL,
    cooperation_id BIGINT,
    title VARCHAR(120) NOT NULL,
    subtitle VARCHAR(160),
    description TEXT,
    scene_tag VARCHAR(50),
    style_tag VARCHAR(50),
    budget_tag VARCHAR(50),
    cover_tag VARCHAR(50),
    cover_image_url VARCHAR(255),
    status TINYINT NOT NULL DEFAULT 1,
    audit_status TINYINT NOT NULL DEFAULT 1,
    like_count INT NOT NULL DEFAULT 0,
    comment_count INT NOT NULL DEFAULT 0,
    favorite_count INT NOT NULL DEFAULT 0,
    share_count INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_post_cooperation (cooperation_id),
    KEY idx_post_status_audit_created (status, audit_status, created_at, id),
    KEY idx_post_user_status_audit_updated (user_id, status, audit_status, updated_at, id),
    CONSTRAINT fk_post_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE post_image (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    image_url VARCHAR(255) NOT NULL,
    sort_order INT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_post_image_post FOREIGN KEY (post_id) REFERENCES post(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE post_tag (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    tag_type VARCHAR(30) NOT NULL,
    tag_value VARCHAR(50) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_post_tag_post FOREIGN KEY (post_id) REFERENCES post(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE product_link (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    product_name VARCHAR(120) NOT NULL,
    platform_name VARCHAR(50) NOT NULL,
    product_url VARCHAR(500) NOT NULL,
    link_status TINYINT NOT NULL DEFAULT 1,
    price_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    profit_label VARCHAR(120),
    guide_tip VARCHAR(255),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_product_link_post FOREIGN KEY (post_id) REFERENCES post(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE product_link_click (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    product_link_id BIGINT NOT NULL,
    click_user_id BIGINT,
    source_page VARCHAR(50) NOT NULL DEFAULT 'product_jump',
    source_action VARCHAR(50) NOT NULL DEFAULT 'jump_out',
    target_url VARCHAR(500) NOT NULL,
    platform_name VARCHAR(50) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_product_link_click_post_created (post_id, created_at),
    KEY idx_product_link_click_user_created (click_user_id, created_at),
    CONSTRAINT fk_product_link_click_post FOREIGN KEY (post_id) REFERENCES post(id),
    CONSTRAINT fk_product_link_click_product_link FOREIGN KEY (product_link_id) REFERENCES product_link(id),
    CONSTRAINT fk_product_link_click_user FOREIGN KEY (click_user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE activity_topic (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    activity_code VARCHAR(50) NOT NULL UNIQUE,
    title VARCHAR(120) NOT NULL,
    badge_label VARCHAR(50),
    theme_desc VARCHAR(255),
    summary_desc VARCHAR(255),
    period_text VARCHAR(50),
    reward_desc VARCHAR(255),
    participation_desc VARCHAR(255),
    scene_label VARCHAR(50),
    status_code VARCHAR(20) NOT NULL DEFAULT 'ONGOING',
    featured_flag TINYINT NOT NULL DEFAULT 0,
    publish_selectable_flag TINYINT NOT NULL DEFAULT 1,
    heat_value INT NOT NULL DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    start_time DATETIME,
    end_time DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE official_announcement (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(120) NOT NULL,
    badge_label VARCHAR(30) NOT NULL DEFAULT '官方公告',
    summary VARCHAR(255) NOT NULL,
    content TEXT,
    status TINYINT NOT NULL DEFAULT 1,
    pinned_flag TINYINT NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    publish_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expire_time DATETIME,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_activity_join (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    activity_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_activity_join (activity_id, user_id),
    CONSTRAINT fk_user_activity_join_activity FOREIGN KEY (activity_id) REFERENCES activity_topic(id),
    CONSTRAINT fk_user_activity_join_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE post_activity_binding (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    activity_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_post_activity_binding_post (post_id),
    CONSTRAINT fk_post_activity_binding_post FOREIGN KEY (post_id) REFERENCES post(id),
    CONSTRAINT fk_post_activity_binding_activity FOREIGN KEY (activity_id) REFERENCES activity_topic(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE post_draft (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    draft_code VARCHAR(50) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    title VARCHAR(120),
    description VARCHAR(500),
    image_urls_json TEXT,
    tags_json TEXT,
    product_link VARCHAR(500),
    product_price DECIMAL(10,2),
    activity_code VARCHAR(50),
    cooperation_code VARCHAR(50),
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_post_draft_user_status (user_id, status, updated_at),
    CONSTRAINT fk_post_draft_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_follow (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    follower_user_id BIGINT NOT NULL,
    followee_user_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_follow (follower_user_id, followee_user_id),
    KEY idx_user_follow_followee_created (followee_user_id, created_at, id, follower_user_id),
    KEY idx_user_follow_follower_created (follower_user_id, created_at, id, followee_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE post_like (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_post_like (post_id, user_id),
    KEY idx_post_like_user_created (user_id, created_at, id, post_id),
    CONSTRAINT fk_post_like_post FOREIGN KEY (post_id) REFERENCES post(id),
    CONSTRAINT fk_post_like_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE post_favorite (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_post_favorite (post_id, user_id),
    KEY idx_post_favorite_user_created (user_id, created_at, id, post_id),
    CONSTRAINT fk_post_favorite_post FOREIGN KEY (post_id) REFERENCES post(id),
    CONSTRAINT fk_post_favorite_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE post_comment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    content VARCHAR(500) NOT NULL,
    like_count INT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    parent_comment_id BIGINT,
    reply_user_id BIGINT,
    KEY idx_post_comment_post_status_parent_created (post_id, status, parent_comment_id, created_at, id),
    CONSTRAINT fk_post_comment_post FOREIGN KEY (post_id) REFERENCES post(id),
    CONSTRAINT fk_post_comment_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE post_comment_like (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    comment_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_post_comment_like (comment_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE merchant (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    merchant_name VARCHAR(120) NOT NULL,
    contact_name VARCHAR(50),
    contact_phone VARCHAR(30),
    cooperation_status TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at DATETIME NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE creator_cooperation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    cooperation_code VARCHAR(50) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    merchant_id BIGINT NOT NULL,
    cooperation_title VARCHAR(120) NOT NULL,
    cooperation_desc VARCHAR(500),
    cooperation_mode VARCHAR(30) NOT NULL DEFAULT 'PLATFORM_INVITE',
    cooperation_status TINYINT NOT NULL DEFAULT 0,
    reward_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    target_post_count INT NOT NULL DEFAULT 1,
    target_like_count INT NOT NULL DEFAULT 0,
    deadline_at DATETIME,
    accepted_at DATETIME,
    reward_issued_at DATETIME,
    settled_at DATETIME,
    abandoned_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_creator_cooperation_user FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT fk_creator_cooperation_merchant FOREIGN KEY (merchant_id) REFERENCES merchant(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE commission_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    cooperation_id BIGINT,
    income_type VARCHAR(50) NOT NULL,
    commission_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    settlement_status TINYINT NOT NULL DEFAULT 0,
    remark VARCHAR(255),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_commission_record_cooperation (cooperation_id),
    CONSTRAINT fk_commission_record_post FOREIGN KEY (post_id) REFERENCES post(id),
    CONSTRAINT fk_commission_record_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE creator_withdraw_request (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    request_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    request_status TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at DATETIME NULL,
    remark VARCHAR(255),
    CONSTRAINT fk_creator_withdraw_request_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE message_notification (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    message_type VARCHAR(50) NOT NULL,
    title VARCHAR(120) NOT NULL,
    content VARCHAR(500) NOT NULL,
    read_status TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_message_user_created (user_id, created_at, id),
    KEY idx_message_user_read (user_id, read_status),
    CONSTRAINT fk_message_notification_user FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sys_admin_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role_code VARCHAR(50) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
