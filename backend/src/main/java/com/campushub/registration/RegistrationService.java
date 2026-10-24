package com.campushub.registration;

import com.campushub.activity.ActivityMapper;
import com.campushub.activity.ActivityService;
import com.campushub.activity.ActivityStatus;
import com.campushub.activity.ActivityView;
import com.campushub.auth.CampusPrincipal;
import com.campushub.error.BusinessException;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service
@Transactional
public class RegistrationService {
    private final RegistrationMapper registrations;
    private final ActivityMapper activities;
    private final ActivityService management;

    public RegistrationService(RegistrationMapper registrations, ActivityMapper activities, ActivityService management) {
        this.registrations = registrations;
        this.activities = activities;
        this.management = management;
    }

    // Avoid RR missing-key gap locks between first registrations on different activities.
    // The activity row lock still serializes all seat/state mutations for the same activity.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RegistrationView change(long activityId, Long version, boolean cancel, CampusPrincipal actor) {
        long userId = student(actor);
        positiveId(activityId);
        if (version == null || version < 0 || version == Long.MAX_VALUE) bad("报名版本号无效");
        // Every mutation locks the activity first. Locking reads see the latest committed state.
        ActivityView activity = activities.findForUpdate(activityId);
        if (activity == null) missing();
        RegistrationView current = registrations.lock(activityId, userId);
        if (current == null) current = RegistrationView.none(activityId, userId);
        if (current.id() == null && activity.status() != ActivityStatus.PUBLISHED) missing();
        String target = cancel ? "CANCELLED" : "CONFIRMED";
        if (version > current.version()) conflict("REGISTRATION_CONFLICT", "报名版本已变更，请刷新");
        if (current.status().equals(target) || (cancel && current.id() == null)) return current;
        if (version != current.version() || current.version() == Long.MAX_VALUE) {
            conflict("REGISTRATION_CONFLICT", "报名版本已变更，请刷新");
        }
        Instant now = Instant.now();
        if (cancel) {
            if (!now.isBefore(activity.startTime())) conflict("CANCELLATION_CLOSED", "活动已开始，不能取消");
            if (registrations.release(activityId) != 1) throw new IllegalStateException("Registration count mismatch");
        } else {
            if (activity.status() != ActivityStatus.PUBLISHED) conflict("REGISTRATION_UNAVAILABLE", "活动未发布");
            if (now.isBefore(activity.registrationStart())) conflict("REGISTRATION_NOT_OPEN", "报名尚未开始");
            if (!now.isBefore(activity.registrationEnd()) || !now.isBefore(activity.startTime())) {
                conflict("REGISTRATION_CLOSED", "报名已截止");
            }
            if (registrations.occupy(activityId) != 1) conflict("ACTIVITY_FULL", "活动名额已满");
        }
        if (current.id() == null) registrations.insert(activityId, userId);
        else if (registrations.change(current.id(), target) != 1) throw new IllegalStateException("Registration disappeared");
        return registrations.lock(activityId, userId);
    }

    @Transactional(readOnly = true)
    public RegistrationView mine(long activityId, CampusPrincipal actor) {
        long userId = student(actor);
        positiveId(activityId);
        var current = registrations.find(activityId, userId);
        if (current != null) return current;
        if (activities.findPublished(activityId) == null) missing();
        return RegistrationView.none(activityId, userId);
    }

    @Transactional(readOnly = true)
    public RegistrationPage mine(int page, int size, CampusPrincipal actor) {
        long userId = student(actor);
        return page(page, size, userId, null);
    }

    @Transactional(readOnly = true)
    public RegistrationPage managed(long activityId, int page, int size, CampusPrincipal actor) {
        management.managed(activityId, actor);
        return page(page, size, null, activityId);
    }

    private RegistrationPage page(int page, int size, Long userId, Long activityId) {
        if (page < 1 || page > 10000 || size < 1 || size > 100) bad("分页范围无效");
        long total = registrations.count(userId, activityId);
        return new RegistrationPage(registrations.list(userId, activityId, size, (long) (page - 1) * size), page, size, total);
    }
    private static long student(CampusPrincipal actor) {
        if (actor == null || !actor.identity().roles().contains("STUDENT")) {
            throw new BusinessException(403, "FORBIDDEN", "仅学生可操作本人的报名");
        }
        return actor.identity().id();
    }
    private static void positiveId(long id) { if (id <= 0) bad("ID 必须为正整数"); }
    private static void missing() { throw new BusinessException(404, "NOT_FOUND", "活动不存在"); }
    private static void bad(String message) { throw new BusinessException(400, "INVALID_INPUT", message); }
    private static void conflict(String code, String message) { throw new BusinessException(409, code, message); }
}
