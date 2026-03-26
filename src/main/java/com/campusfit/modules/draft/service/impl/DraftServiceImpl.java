package com.campusfit.modules.draft.service.impl;

import com.campusfit.common.exception.BusinessException;
import com.campusfit.modules.activity.service.ActivityService;
import com.campusfit.modules.activity.vo.ActivityItemVO;
import com.campusfit.modules.auth.support.UserAuthContext;
import com.campusfit.modules.draft.dto.DraftSaveRequest;
import com.campusfit.modules.draft.service.DraftService;
import com.campusfit.modules.draft.vo.DraftItemVO;
import com.campusfit.modules.post.dto.PostCreateRequest;
import com.campusfit.modules.post.service.PostService;
import com.campusfit.modules.post.vo.PostCreateResultVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class DraftServiceImpl implements DraftService {

    private static final String DEFAULT_DRAFT_TITLE = "未命名草稿";
    private static final String DEFAULT_DRAFT_NOTE = "这条草稿还没有补充描述。";
    private static final String PUBLISH_INCOMPLETE_MESSAGE = "草稿还不能发布，请先补齐标题、描述、标签和图片";
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};
    private static final String DRAFT_SELECT = """
        select draft_code, title, description, image_urls_json, tags_json, product_link, product_price, activity_code, updated_at
        from post_draft
        where status = 1
        """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final ActivityService activityService;
    private final PostService postService;

    public DraftServiceImpl(
        JdbcTemplate jdbcTemplate,
        ObjectMapper objectMapper,
        ActivityService activityService,
        PostService postService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.activityService = activityService;
        this.postService = postService;
    }

    @Override
    public List<DraftItemVO> listMine() {
        long currentUserId = UserAuthContext.requireUserId();
        Map<String, ActivityItemVO> activityCache = new LinkedHashMap<>();
        return jdbcTemplate.query(
            DRAFT_SELECT + " and user_id = ? order by updated_at desc, draft_code desc",
            (rs, rowNum) -> toSummaryVO(mapDraftRecord(rs), activityCache),
            currentUserId
        );
    }

    @Override
    public DraftItemVO getDetail(String draftId) {
        long currentUserId = UserAuthContext.requireUserId();
        return toDetailVO(requireOwnedDraft(draftId, currentUserId), new LinkedHashMap<>());
    }

    @Override
    @Transactional
    public DraftItemVO save(DraftSaveRequest request) {
        long currentUserId = UserAuthContext.requireUserId();
        DraftPayload payload = normalizePayload(request);
        validateDraftNotEmpty(payload);

        String requestedDraftId = normalize(request.draftId());
        if (requestedDraftId != null) {
            return update(requestedDraftId, request);
        }

        String draftCode = generateDraftCode();
        jdbcTemplate.update(
            """
            insert into post_draft (
                draft_code, user_id, title, description, image_urls_json, tags_json, product_link, product_price,
                activity_code, status, created_at, updated_at
            ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, 1, now(), now())
            """,
            draftCode,
            currentUserId,
            payload.title(),
            payload.desc(),
            writeJson(payload.imageUrls()),
            writeJson(payload.tags()),
            payload.productLink(),
            payload.productPrice(),
            payload.activityCode()
        );

        return toDetailVO(requireOwnedDraft(draftCode, currentUserId), new LinkedHashMap<>());
    }

    @Override
    @Transactional
    public DraftItemVO update(String draftId, DraftSaveRequest request) {
        long currentUserId = UserAuthContext.requireUserId();
        validateDraftIdMatch(draftId, request.draftId());

        DraftPayload payload = normalizePayload(request);
        validateDraftNotEmpty(payload);
        requireOwnedDraft(draftId, currentUserId);

        updateDraftRow(draftId, currentUserId, payload);
        return toDetailVO(requireOwnedDraft(draftId, currentUserId), new LinkedHashMap<>());
    }

    @Override
    @Transactional
    public boolean delete(String draftId) {
        long currentUserId = UserAuthContext.requireUserId();
        requireOwnedDraft(draftId, currentUserId);
        return softDeleteDraft(draftId, currentUserId);
    }

    @Override
    @Transactional
    public PostCreateResultVO publish(String draftId, DraftSaveRequest request) {
        long currentUserId = UserAuthContext.requireUserId();
        DraftRecord draft = requireOwnedDraft(draftId, currentUserId);
        DraftPayload payload = request == null ? normalizePayload(draft) : mergePayload(draft, request);
        validateDraftPublishable(payload);
        updateDraftRow(draftId, currentUserId, payload);

        PostCreateResultVO result = postService.create(new PostCreateRequest(
            payload.title(),
            payload.desc(),
            payload.imageUrls(),
            payload.tags(),
            payload.productLink(),
            payload.productPrice(),
            payload.activityCode()
        ));
        softDeleteDraft(draftId, currentUserId);
        return result;
    }

    private DraftRecord requireOwnedDraft(String draftId, long currentUserId) {
        List<DraftRecord> list = jdbcTemplate.query(
            DRAFT_SELECT + " and draft_code = ? and user_id = ? limit 1",
            (rs, rowNum) -> mapDraftRecord(rs),
            draftId,
            currentUserId
        );
        if (list.isEmpty()) {
            throw new BusinessException("未找到对应草稿");
        }
        return list.get(0);
    }

    private DraftRecord mapDraftRecord(ResultSet rs) throws SQLException {
        return new DraftRecord(
            rs.getString("draft_code"),
            normalize(rs.getString("title")),
            normalize(rs.getString("description")),
            sanitizeStringList(readStringList(rs.getString("image_urls_json"))),
            sanitizeStringList(readStringList(rs.getString("tags_json"))),
            normalize(rs.getString("product_link")),
            rs.getBigDecimal("product_price"),
            normalize(rs.getString("activity_code")),
            rs.getTimestamp("updated_at")
        );
    }

    private DraftItemVO toSummaryVO(DraftRecord draft, Map<String, ActivityItemVO> activityCache) {
        return buildDraftVO(draft, activityCache, true);
    }

    private DraftItemVO toDetailVO(DraftRecord draft, Map<String, ActivityItemVO> activityCache) {
        return buildDraftVO(draft, activityCache, false);
    }

    private DraftItemVO buildDraftVO(DraftRecord draft, Map<String, ActivityItemVO> activityCache, boolean summaryMode) {
        String title = summaryMode ? displayTitle(draft.title()) : blankToEmpty(draft.title());
        String desc = blankToEmpty(draft.desc());
        return new DraftItemVO(
            draft.draftCode(),
            title,
            desc.isEmpty() ? DEFAULT_DRAFT_NOTE : desc,
            desc,
            draft.tags(),
            draft.imageUrls(),
            blankToEmpty(draft.productLink()),
            draft.productPrice(),
            formatTime(draft.updatedAt()),
            resolveActivity(draft.activityCode(), activityCache)
        );
    }

    private ActivityItemVO resolveActivity(String activityCode, Map<String, ActivityItemVO> activityCache) {
        String normalizedCode = normalize(activityCode);
        if (normalizedCode == null) {
            return null;
        }
        if (activityCache.containsKey(normalizedCode)) {
            return activityCache.get(normalizedCode);
        }
        ActivityItemVO activity = activityService.findByCode(normalizedCode);
        activityCache.put(normalizedCode, activity);
        return activity;
    }

    private DraftPayload normalizePayload(DraftSaveRequest request) {
        String activityCode = normalizeActivityCode(request.activityId());
        return new DraftPayload(
            normalize(request.title()),
            normalize(request.desc()),
            sanitizeStringList(request.imageUrls()),
            sanitizeStringList(request.tags()),
            normalize(request.productLink()),
            request.productPrice(),
            activityCode
        );
    }

    private DraftPayload mergePayload(DraftRecord draft, DraftSaveRequest request) {
        String activityCode = request.activityId() != null
            ? normalizeActivityCode(request.activityId())
            : normalizeExistingActivityCode(draft.activityCode());
        return new DraftPayload(
            request.title() != null ? normalize(request.title()) : normalize(draft.title()),
            request.desc() != null ? normalize(request.desc()) : normalize(draft.desc()),
            request.imageUrls() != null ? sanitizeStringList(request.imageUrls()) : sanitizeStringList(draft.imageUrls()),
            request.tags() != null ? sanitizeStringList(request.tags()) : sanitizeStringList(draft.tags()),
            request.productLink() != null ? normalize(request.productLink()) : normalize(draft.productLink()),
            request.productPrice() != null ? request.productPrice() : draft.productPrice(),
            activityCode
        );
    }

    private DraftPayload normalizePayload(DraftRecord draft) {
        String activityCode = normalizeExistingActivityCode(draft.activityCode());
        return new DraftPayload(
            normalize(draft.title()),
            normalize(draft.desc()),
            sanitizeStringList(draft.imageUrls()),
            sanitizeStringList(draft.tags()),
            normalize(draft.productLink()),
            draft.productPrice(),
            activityCode
        );
    }

    private void updateDraftRow(String draftId, long currentUserId, DraftPayload payload) {
        jdbcTemplate.update(
            """
            update post_draft
            set title = ?, description = ?, image_urls_json = ?, tags_json = ?, product_link = ?, product_price = ?, activity_code = ?, updated_at = now()
            where draft_code = ? and user_id = ? and status = 1
            """,
            payload.title(),
            payload.desc(),
            writeJson(payload.imageUrls()),
            writeJson(payload.tags()),
            payload.productLink(),
            payload.productPrice(),
            payload.activityCode(),
            draftId,
            currentUserId
        );
    }

    private void validateDraftIdMatch(String pathDraftId, String requestDraftId) {
        String normalizedRequestId = normalize(requestDraftId);
        if (normalizedRequestId != null && !normalizedRequestId.equals(pathDraftId)) {
            throw new BusinessException("草稿编号不一致");
        }
    }

    private void validateDraftNotEmpty(DraftPayload payload) {
        boolean hasTitle = payload.title() != null;
        boolean hasDesc = payload.desc() != null;
        boolean hasLink = payload.productLink() != null;
        boolean hasPrice = payload.productPrice() != null;
        boolean hasTags = !payload.tags().isEmpty();
        boolean hasImages = !payload.imageUrls().isEmpty();
        boolean hasActivity = payload.activityCode() != null;
        if (!hasTitle && !hasDesc && !hasLink && !hasPrice && !hasTags && !hasImages && !hasActivity) {
            throw new BusinessException("草稿内容不能为空");
        }
    }

    private void validateDraftPublishable(DraftPayload payload) {
        if (isBlank(payload.title()) || DEFAULT_DRAFT_TITLE.equals(payload.title())
            || isBlank(payload.desc())
            || payload.imageUrls().isEmpty()
            || payload.tags().isEmpty()) {
            throw new BusinessException(PUBLISH_INCOMPLETE_MESSAGE);
        }
    }

    private boolean softDeleteDraft(String draftId, long currentUserId) {
        int updated = jdbcTemplate.update(
            "update post_draft set status = 0, updated_at = now() where draft_code = ? and user_id = ? and status = 1",
            draftId,
            currentUserId
        );
        if (updated <= 0) {
            throw new BusinessException("未找到可删除的草稿");
        }
        return true;
    }

    private String normalizeActivityCode(String activityId) {
        String code = normalize(activityId);
        if (code == null) {
            return null;
        }
        ActivityItemVO activity = activityService.findByCode(code);
        if (activity == null) {
            throw new BusinessException("未找到对应活动");
        }
        if (!activity.selectable()) {
            throw new BusinessException("褰撳墠娲诲姩涓嶆敮鎸佸湪鍙戝竷鏃堕€夋嫨");
        }
        return activity.id();
    }

    private String normalizeExistingActivityCode(String activityCode) {
        String code = normalize(activityCode);
        if (code == null) {
            return null;
        }
        if (activityService.findByCode(code) == null) {
            throw new BusinessException("草稿绑定的活动已失效，请重新选择活动后再发布");
        }
        return code;
    }

    private List<String> sanitizeStringList(List<String> rawList) {
        if (rawList == null || rawList.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> unique = new LinkedHashSet<>();
        for (String item : rawList) {
            String normalized = normalize(item);
            if (normalized != null) {
                unique.add(normalized);
            }
        }
        return unique.isEmpty() ? Collections.emptyList() : new ArrayList<>(unique);
    }

    private String displayTitle(String title) {
        return isBlank(title) ? DEFAULT_DRAFT_TITLE : title;
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String writeJson(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list == null ? Collections.emptyList() : list);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("草稿内容保存失败");
        }
    }

    private List<String> readStringList(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(raw, STRING_LIST_TYPE);
        } catch (JsonProcessingException exception) {
            return Collections.emptyList();
        }
    }

    private String formatTime(Timestamp timestamp) {
        LocalDateTime safeTime = timestamp == null ? LocalDateTime.now() : timestamp.toLocalDateTime();
        return safeTime.format(TIME_FORMATTER);
    }

    private String generateDraftCode() {
        return "draft-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private record DraftRecord(
        String draftCode,
        String title,
        String desc,
        List<String> imageUrls,
        List<String> tags,
        String productLink,
        BigDecimal productPrice,
        String activityCode,
        Timestamp updatedAt
    ) {
    }

    private record DraftPayload(
        String title,
        String desc,
        List<String> imageUrls,
        List<String> tags,
        String productLink,
        BigDecimal productPrice,
        String activityCode
    ) {
    }
}
