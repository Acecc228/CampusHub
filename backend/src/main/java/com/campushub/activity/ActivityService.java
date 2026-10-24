package com.campushub.activity;

import com.campushub.auth.CampusPrincipal;
import com.campushub.error.BusinessException;
import com.campushub.registration.RegistrationMapper;
import com.campushub.registration.RegistrationView;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ActivityService {
    private final ActivityMapper mapper;
    private final RegistrationMapper registrations;

    public ActivityService(ActivityMapper mapper, RegistrationMapper registrations) {
        this.mapper = mapper;
        this.registrations = registrations;
    }

    @Transactional(readOnly = true)
    public ActivityView published(long id) { return required(mapper.findPublished(positiveId(id))); }

    @Transactional(readOnly = true)
    public ActivityDetail detail(long id, CampusPrincipal actor) {
        var activity = published(id);
        var registration = registrations.find(id, actor.identity().id());
        return new ActivityDetail(activity, registration == null
                ? RegistrationView.none(id, actor.identity().id()) : registration);
    }

    @Transactional(readOnly = true)
    public ActivityView managed(long id, CampusPrincipal actor) {
        return managed(id, actor, false);
    }

    private ActivityView managed(long id, CampusPrincipal actor, boolean lock) {
        requireManager(actor);
        positiveId(id);
        var activity = required(lock ? mapper.findForUpdate(id) : mapper.find(id));
        if (!isAdmin(actor) && activity.creatorId() != actor.identity().id()) {
            throw new BusinessException(403, "FORBIDDEN", "无权管理此活动");
        }
        return activity;
    }

    @Transactional(readOnly = true)
    public ActivityPage list(int page, int size, Long categoryId, String keyword, ActivityStatus status,
            boolean management, CampusPrincipal actor) {
        if (page < 1 || page > 10000 || size < 1 || size > 100) bad("分页范围无效");
        if (categoryId != null) positiveId(categoryId);
        if (keyword != null) {
            keyword = keyword.strip();
            if (keyword.codePointCount(0, keyword.length()) > 100) bad("关键词过长");
            keyword = keyword.isEmpty() ? null : "%" + keyword.replace("!", "!!")
                    .replace("%", "!%").replace("_", "!_") + "%";
        }
        Long owner = null;
        if (management) {
            requireManager(actor);
            if (!isAdmin(actor)) owner = actor.identity().id();
        } else {
            if (status != null && status != ActivityStatus.PUBLISHED) bad("仅可浏览已发布活动");
            status = ActivityStatus.PUBLISHED;
        }
        long total = mapper.count(owner, status, categoryId, keyword);
        return new ActivityPage(mapper.list(owner, status, categoryId, keyword, size, (long) (page - 1) * size),
                page, size, total);
    }

    public ActivityView create(ActivityInput input, CampusPrincipal actor) {
        requireManager(actor);
        var valid = validate(input);
        mapper.insert(valid, actor.identity().id());
        return required(mapper.find(mapper.insertedId()));
    }

    public ActivityView edit(long id, ActivityInput input, CampusPrincipal actor) {
        var current = managed(id, actor, true);
        version(input.version());
        if (current.status() == ActivityStatus.PUBLISHED || current.version() != input.version()) conflict();
        var valid = validate(input);
        if (valid.capacity() < current.registeredCount()) {
            throw new BusinessException(409, "CAPACITY_BELOW_REGISTERED", "容量不能小于已报名人数");
        }
        changed(mapper.edit(id, valid, current.status(), actor.identity().id(), isAdmin(actor)));
        return required(mapper.find(id));
    }

    public ActivityView transition(long id, Long version, boolean publish, CampusPrincipal actor) {
        // Check deadlines only after acquiring the row lock; lock waits can cross registrationEnd.
        var current = managed(id, actor, true);
        version(version);
        if (current.version() != version) conflict();
        if (publish) {
            if (current.status() != ActivityStatus.DRAFT && current.status() != ActivityStatus.UNPUBLISHED) conflict();
            validate(new ActivityInput(current.title(), current.summary(), current.content(), current.categoryId(),
                    current.location(), current.startTime(), current.endTime(), current.registrationStart(),
                    current.registrationEnd(), (long) current.capacity(), version));
            Instant now = Instant.now();
            if (!current.registrationEnd().isAfter(now) || !current.startTime().isAfter(now)) {
                bad("报名已截止或活动已开始，不能发布");
            }
        } else if (current.status() != ActivityStatus.PUBLISHED) conflict();
        changed(mapper.transition(id, version, current.status(),
                publish ? ActivityStatus.PUBLISHED : ActivityStatus.UNPUBLISHED, actor.identity().id(), isAdmin(actor)));
        return required(mapper.find(id));
    }

    private ActivityInput validate(ActivityInput v) {
        String title = text(v.title(), 120), summary = text(v.summary(), 500),
                content = text(v.content(), 20000), location = text(v.location(), 200);
        if (v.categoryId() == null || v.categoryId() <= 0 || mapper.categoryExists(v.categoryId()) != 1) bad("分类不存在");
        if (v.capacity() == null || v.capacity() < 1 || v.capacity() > Integer.MAX_VALUE) bad("容量必须为正整数");
        for (Instant time : new Instant[]{v.startTime(), v.endTime(), v.registrationStart(), v.registrationEnd()}) {
            if (time == null || time.getNano() != 0 || time.isBefore(Instant.parse("1000-01-01T00:00:00Z"))
                    || time.isAfter(Instant.parse("9999-12-31T23:59:59Z"))) bad("时间须为有效的整秒日期");
        }
        if (!v.registrationStart().isBefore(v.registrationEnd()) || v.registrationEnd().isAfter(v.startTime())
                || !v.startTime().isBefore(v.endTime())) bad("活动或报名时间顺序无效");
        return new ActivityInput(title, summary, content, v.categoryId(), location, v.startTime(), v.endTime(),
                v.registrationStart(), v.registrationEnd(), v.capacity(), v.version());
    }

    private static String text(String value, int max) {
        if (value == null || value.isBlank()) bad("必填文本不能为空");
        String normalized = value.strip();
        if (normalized.codePointCount(0, normalized.length()) > max) bad("文本长度超出限制");
        return normalized;
    }

    private static void requireManager(CampusPrincipal actor) {
        if (actor == null || (!isAdmin(actor) && !actor.identity().roles().contains("ORGANIZER"))) {
            throw new BusinessException(403, "FORBIDDEN", "无活动管理权限");
        }
    }
    private static boolean isAdmin(CampusPrincipal actor) { return actor.identity().roles().contains("ADMIN"); }
    private static long positiveId(long id) { if (id <= 0) bad("ID 必须为正整数"); return id; }
    private static void version(Long value) { if (value == null || value < 0 || value == Long.MAX_VALUE) bad("版本号无效"); }
    private static ActivityView required(ActivityView value) {
        if (value == null) throw new BusinessException(404, "NOT_FOUND", "活动不存在");
        return value;
    }
    private static void changed(int rows) { if (rows != 1) conflict(); }
    private static void conflict() { throw new BusinessException(409, "ACTIVITY_CONFLICT", "活动状态或版本已变更，请刷新后重试"); }
    private static void bad(String message) { throw new BusinessException(400, "INVALID_INPUT", message); }
}
