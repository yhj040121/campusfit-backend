SET NAMES utf8mb4;

INSERT INTO app_user (id, phone, nickname, avatar_url, password_hash, status) VALUES
    (1, '18688882026', '乔乔', NULL, NULL, 1),
    (2, '18688882027', '林夏', NULL, NULL, 1),
    (3, '18688882028', '鹿宁', NULL, NULL, 1),
    (4, '18688882029', '安可', NULL, NULL, 1);

INSERT INTO user_profile (id, user_id, school_name, grade_name, signature, avatar_text, avatar_class, like_count, follower_count, following_count) VALUES
    (1, 1, '华东师范大学', '大三', '分享真实校园穿搭和理性购物推荐。', 'Q', 'soft', 3580, 1240, 198),
    (2, 2, '上海大学', '大二', '专门收藏适合运动和社团场景的校园穿搭灵感。', 'L', 'soft', 2290, 980, 156),
    (3, 3, '同济大学', '研一', '偏爱温柔日常和高性价比叠穿单品。', 'L', 'alt', 3110, 760, 132),
    (4, 4, '复旦大学', '大一', '更关注教室通勤和学生预算内的实用穿搭。', 'A', 'soft', 1870, 540, 88);

INSERT INTO tag_option (id, category, option_value, sort_order, status) VALUES
    (1, 'scene', '早八', 1, 1),
    (2, 'scene', '图书馆', 2, 1),
    (3, 'scene', '社团活动', 3, 1),
    (4, 'scene', '约会', 4, 1),
    (5, 'scene', '通勤', 5, 1),
    (6, 'scene', '运动', 6, 1),
    (7, 'style', '学院风', 1, 1),
    (8, 'style', '宽松休闲', 2, 1),
    (9, 'style', '甜酷', 3, 1),
    (10, 'style', '极简', 4, 1),
    (11, 'style', '韩系', 5, 1),
    (12, 'style', '清新', 6, 1),
    (13, 'budget', '50-100', 1, 1),
    (14, 'budget', '100-150', 2, 1),
    (15, 'budget', '150-200', 3, 1),
    (16, 'budget', '200+', 4, 1);

INSERT INTO merchant (id, merchant_name, contact_name, contact_phone, cooperation_status, remark) VALUES
    (1, '青芽校园服饰', '艾米', '13800000001', 1, '主打春夏校园轻穿搭合作'),
    (2, '基础款研究所', 'Jason', '13800000002', 1, '主打学生日常高频基础款'),
    (3, '动感风格店', 'Helen', '13800000003', 1, '主打运动和社团场景穿搭合作');

INSERT INTO post (id, post_code, user_id, title, subtitle, description, scene_tag, style_tag, budget_tag, cover_tag, cover_image_url, status, audit_status, like_count, comment_count, favorite_count, share_count, created_at) VALUES
    (1, 'look1', 1, '适合早八和图书馆的清爽叠穿', '蓝白层次干净利落，适合学生日常路线', '白衬衫叠穿浅蓝针织马甲，下装搭配柔和灰色百褶裙，整体轻盈清爽，很适合早课、图书馆自习和校园走动。', '图书馆', '学院风', '100-150', '图书馆精选', NULL, 1, 1, 128, 36, 92, 14, DATE_SUB(NOW(), INTERVAL 6 DAY)),
    (2, 'look2', 2, '适合社团活动和下午散步的宽松穿搭', '卫衣配工装裤，轻松舒适又有校园活力', '薄荷绿卫衣搭配米白工装裤和运动鞋，适合社团招新、操场拍照和放学后活动，整体轻松不费力。', '社团活动', '宽松休闲', '150-200', '热门穿搭', NULL, 1, 1, 203, 48, 115, 21, DATE_SUB(NOW(), INTERVAL 5 DAY)),
    (3, 'look3', 3, '校园约会也自然的奶油色温柔搭配', '轻薄开衫搭连衣裙，白色运动鞋让整体更轻松', '奶油色开衫叠搭简约连衣裙，再配上一双白色运动鞋和帆布包，温柔不做作，适合周末约会和校园散步。', '约会', '清新', '150-200', '校园优选', NULL, 1, 1, 176, 31, 142, 19, DATE_SUB(NOW(), INTERVAL 4 DAY)),
    (4, 'look4', 4, '百元内也能搞定的教室通勤穿搭', '白T和牛仔裙组合，适合高频重复穿着', '简单白T搭配浅色牛仔裙和帆布鞋，是很适合教室、食堂和宿舍楼之间通勤的实用型搭配。', '通勤', '极简', '50-100', '预算友好', NULL, 1, 1, 98, 19, 87, 10, DATE_SUB(NOW(), INTERVAL 3 DAY)),
    (5, 'look5', 1, '适合校园夜间活动的甜酷叠穿', '衬衫、针织背心和短裙，拍照更出片', '挺括衬衫叠搭针织背心与深色短裙，再配乐福鞋，适合社团夜场、主持活动和校园夜拍。', '社团活动', '甜酷', '150-200', '夜间氛围', NULL, 1, 1, 155, 27, 101, 16, DATE_SUB(NOW(), INTERVAL 2 DAY)),
    (6, 'look6', 2, '复习周也能保持体面的舒适穿搭', '开衫配阔腿裤，长时间学习更轻松', '低饱和开衫搭配柔软阔腿裤，适合长时间自习、复习周和安静的图书馆场景，舒适同时也不显拖沓。', '早八', '韩系', '100-150', '人气榜单', NULL, 1, 1, 132, 29, 108, 13, DATE_SUB(NOW(), INTERVAL 1 DAY));

INSERT INTO post_tag (id, post_id, tag_type, tag_value) VALUES
    (1, 1, 'highlight', '层次清爽'),
    (2, 1, 'highlight', '早八友好'),
    (3, 1, 'highlight', '高频可重复'),
    (4, 2, 'highlight', '运动活力'),
    (5, 2, 'highlight', '社团可穿'),
    (6, 2, 'highlight', '青春氛围'),
    (7, 3, 'highlight', '温柔约会'),
    (8, 3, 'highlight', '无压力搭配'),
    (9, 3, 'highlight', '轻松通勤'),
    (10, 4, 'highlight', '预算友好'),
    (11, 4, 'highlight', '日常基础'),
    (12, 4, 'highlight', '教室适配'),
    (13, 5, 'highlight', '甜酷叠穿'),
    (14, 5, 'highlight', '夜间活动'),
    (15, 5, 'highlight', '拍照出片'),
    (16, 6, 'highlight', '复习周'),
    (17, 6, 'highlight', '久坐友好'),
    (18, 6, 'highlight', '柔和中性');

INSERT INTO product_link (id, post_id, product_name, platform_name, product_url, link_status, is_partner_product, commission_rate, price_amount, profit_label, guide_tip, last_checked_at) VALUES
    (1, 1, '衬衫 + 针织马甲 + 百褶裙', '淘宝校园精选', 'https://s.taobao.com/search?q=%E8%A1%AC%E8%A1%AB%20%E9%92%88%E7%BB%87%E9%A9%AC%E7%94%B2%20%E7%99%BE%E8%A4%B6%E8%A3%99', 1, 1, 8.00, 139.00, '平台导购佣金 8%', '请结合真实需求比价后再下单。', NOW()),
    (2, 2, '卫衣 + 工装裤 + 运动鞋', '京东校园店', 'https://search.jd.com/Search?keyword=%E5%8D%AB%E8%A1%A3%20%E5%B7%A5%E8%A3%85%E8%A3%A4%20%E8%BF%90%E5%8A%A8%E9%9E%8B', 1, 1, 6.00, 179.00, '平台导购佣金 6%', '运动场景优先考虑舒适度和耐穿性。', NOW()),
    (3, 3, '开衫 + 连衣裙 + 白色运动鞋', '天猫校园店', 'https://list.tmall.com/search_product.htm?q=%E5%BC%80%E8%A1%AB%20%E8%BF%9E%E8%A1%A3%E8%A3%99%20%E7%99%BD%E8%89%B2%E8%BF%90%E5%8A%A8%E9%9E%8B', 1, 0, 6.50, 199.00, '品牌合作分成', '如果通勤需求更强，可以再加一件轻薄外套。', NOW()),
    (4, 4, '白T + 牛仔裙 + 帆布鞋', '拼多多学生优选', 'https://mobile.yangkeduo.com/search_result.html?search_key=%E7%99%BDT%20%E7%89%9B%E4%BB%94%E8%A3%99%20%E5%B8%86%E5%B8%83%E9%9E%8B', 1, 1, 5.00, 89.00, '平台导购佣金 5%', '预算有限时先买高复用基础款。', NOW()),
    (5, 5, '衬衫 + 针织背心 + 短裙', '淘宝校园精选', 'https://s.taobao.com/search?q=%E8%A1%AC%E8%A1%AB%20%E9%92%88%E7%BB%87%E8%83%8C%E5%BF%83%20%E7%9F%AD%E8%A3%99', 1, 0, 7.00, 189.00, '品牌合作分成', '如果场景更正式，可以把短裙替换成中长裙。', NOW()),
    (6, 6, '开衫 + 阔腿裤 + 休闲鞋', '京东校园店', 'https://search.jd.com/Search?keyword=%E5%BC%80%E8%A1%AB%20%E9%98%94%E8%85%BF%E8%A3%A4%20%E4%BC%91%E9%97%B2%E9%9E%8B', 1, 1, 6.00, 149.00, '平台导购佣金 6%', '复习周穿搭建议以舒适和耐久为先。', NOW());

INSERT INTO post_comment (id, post_id, user_id, content, like_count, status, created_at) VALUES
    (1, 1, 2, '这个配色真的很适合图书馆场景。', 16, 1, DATE_SUB(NOW(), INTERVAL 42 MINUTE)),
    (2, 1, 3, '预算区间很真实，我先收藏了。', 12, 1, DATE_SUB(NOW(), INTERVAL 30 MINUTE)),
    (3, 1, 4, '如果换成运动鞋，会不会更适合早八？', 8, 1, DATE_SUB(NOW(), INTERVAL 18 MINUTE)),
    (4, 2, 1, '这套确实很适合社团活动拍照。', 10, 1, DATE_SUB(NOW(), INTERVAL 1 HOUR));

INSERT INTO creator_cooperation (id, user_id, merchant_id, cooperation_title, cooperation_status, reward_amount, created_at) VALUES
    (1, 1, 1, '春季清爽校园穿搭专题', 0, 120.00, DATE_SUB(NOW(), INTERVAL 7 DAY)),
    (2, 1, 2, '平价通勤基础款合作', 1, 88.00, DATE_SUB(NOW(), INTERVAL 5 DAY)),
    (3, 1, 3, '社团与运动场景穿搭专题', 1, 60.00, DATE_SUB(NOW(), INTERVAL 3 DAY));

INSERT INTO commission_record (id, post_id, user_id, income_type, commission_amount, settlement_status, created_at) VALUES
    (1, 1, 1, '导购佣金', 86.50, 1, DATE_SUB(NOW(), INTERVAL 6 DAY)),
    (2, 5, 1, '品牌分成', 92.00, 1, DATE_SUB(NOW(), INTERVAL 3 DAY)),
    (3, 1, 1, '导购佣金', 89.50, 0, DATE_SUB(NOW(), INTERVAL 1 DAY));

INSERT INTO message_notification (id, user_id, message_type, title, content, read_status, created_at) VALUES
    (1, 1, '互动', '你的穿搭新增了 18 个收藏', '图书馆那套搭配还在持续获得曝光，建议补充更多亮点标签。', 0, DATE_SUB(NOW(), INTERVAL 1 MINUTE)),
    (2, 1, '合作', '有一个校园品牌合作待确认', '青芽校园服饰希望在 24 小时内确认春季专题合作。', 0, DATE_SUB(NOW(), INTERVAL 5 MINUTE)),
    (3, 1, '系统', '商品链接审核已通过', '你提交的导购链接已通过审核，现在可以参与佣金统计。', 0, DATE_SUB(NOW(), INTERVAL 12 MINUTE)),
    (4, 1, '评论', '鹿宁评论了你的穿搭', '她想看 100 元预算以内的更多搭配建议。', 0, DATE_SUB(NOW(), INTERVAL 28 MINUTE)),
    (5, 1, '收益', '本周导购收益正在结算', '你本周的导购内容预计结算 86.50 元，可前往创作者收益中心查看。', 0, DATE_SUB(NOW(), INTERVAL 1 HOUR));

INSERT INTO user_follow (id, follower_user_id, followee_user_id, created_at) VALUES
    (1, 1, 2, DATE_SUB(NOW(), INTERVAL 12 DAY)),
    (2, 3, 1, DATE_SUB(NOW(), INTERVAL 10 DAY)),
    (3, 4, 1, DATE_SUB(NOW(), INTERVAL 8 DAY));

INSERT INTO post_like (id, post_id, user_id, created_at) VALUES
    (1, 1, 2, DATE_SUB(NOW(), INTERVAL 1 DAY)),
    (2, 1, 3, DATE_SUB(NOW(), INTERVAL 20 HOUR)),
    (3, 2, 1, DATE_SUB(NOW(), INTERVAL 18 HOUR));

INSERT INTO post_favorite (id, post_id, user_id, created_at) VALUES
    (1, 1, 2, DATE_SUB(NOW(), INTERVAL 10 HOUR)),
    (2, 1, 3, DATE_SUB(NOW(), INTERVAL 9 HOUR)),
    (3, 3, 1, DATE_SUB(NOW(), INTERVAL 7 HOUR));

INSERT INTO promotion_slot (id, slot_name, slot_position, merchant_id, start_time, end_time, status) VALUES
    (1, '首页主视觉推广位', 'home_top_banner', 1, NOW(), DATE_ADD(NOW(), INTERVAL 15 DAY), 1),
    (2, '搜索热词推广位', 'search_hot', 2, NOW(), DATE_ADD(NOW(), INTERVAL 10 DAY), 1);

INSERT INTO campaign (id, campaign_name, campaign_type, merchant_id, start_time, end_time, status) VALUES
    (1, '春季新生穿搭指南', '内容专题', 1, NOW(), DATE_ADD(NOW(), INTERVAL 20 DAY), 1),
    (2, '社团活力穿搭周', '品牌活动', 3, NOW(), DATE_ADD(NOW(), INTERVAL 12 DAY), 1);

INSERT INTO report_record (id, reporter_user_id, target_type, target_id, reason, process_status, created_at) VALUES
    (1, 2, 'post', 4, '演示用内容治理样例', 1, DATE_SUB(NOW(), INTERVAL 2 DAY));

UPDATE activity_topic
SET badge_label = CASE activity_code
    WHEN 'spring-library-week' THEN '热门活动'
    WHEN 'club-style-challenge' THEN '校园挑战'
    WHEN 'budget-outfit-plan' THEN '预算计划'
    ELSE badge_label
END;

INSERT INTO official_announcement (
    id, title, badge_label, summary, content, status, pinned_flag, sort_order,
    publish_time, expire_time, created_by, updated_by
) VALUES
    (
        1,
        'CampusFit 内容发布与审核说明',
        '官方公告',
        '新发布或修改的穿搭内容会先进入审核，审核通过后才会展示到首页信息流。',
        '为了保证首页内容质量，用户新发和修改后的穿搭内容都会先进入后台审核。你可以在管理端处理审核，在移动端“我的发布”查看审核进度。',
        1,
        1,
        1,
        DATE_SUB(NOW(), INTERVAL 2 HOUR),
        NULL,
        '系统初始化',
        '系统初始化'
    );

INSERT INTO sys_admin_user (id, username, password_hash, role_code, status) VALUES
    (1, 'admin', 'admin123', 'SUPER_ADMIN', 1),
    (2, 'operator', 'operator123', 'CONTENT_OPERATOR', 1),
    (3, 'finance', 'finance123', 'FINANCE', 1);

ALTER TABLE app_user AUTO_INCREMENT = 5;
ALTER TABLE user_profile AUTO_INCREMENT = 5;
ALTER TABLE tag_option AUTO_INCREMENT = 17;
ALTER TABLE merchant AUTO_INCREMENT = 4;
ALTER TABLE post AUTO_INCREMENT = 7;
ALTER TABLE post_image AUTO_INCREMENT = 1;
ALTER TABLE post_tag AUTO_INCREMENT = 19;
ALTER TABLE product_link AUTO_INCREMENT = 7;
ALTER TABLE post_comment AUTO_INCREMENT = 5;
ALTER TABLE creator_cooperation AUTO_INCREMENT = 4;
ALTER TABLE commission_record AUTO_INCREMENT = 4;
ALTER TABLE message_notification AUTO_INCREMENT = 6;
ALTER TABLE user_follow AUTO_INCREMENT = 4;
ALTER TABLE post_like AUTO_INCREMENT = 4;
ALTER TABLE post_favorite AUTO_INCREMENT = 4;
ALTER TABLE promotion_slot AUTO_INCREMENT = 3;
ALTER TABLE campaign AUTO_INCREMENT = 3;
ALTER TABLE report_record AUTO_INCREMENT = 2;
ALTER TABLE official_announcement AUTO_INCREMENT = 2;
ALTER TABLE sys_admin_user AUTO_INCREMENT = 4;
