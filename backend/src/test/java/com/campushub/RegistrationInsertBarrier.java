package com.campushub;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;

// Test-only scheduling hook: all SQL still runs against real MySQL, without mocked results.
@Intercepts(@Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}))
class RegistrationInsertBarrier implements Interceptor {
    volatile Gate gate;

    static class Gate {
        final Set<Long> activities;
        final CountDownLatch ready = new CountDownLatch(2);
        final Set<Long> connections = ConcurrentHashMap.newKeySet();
        final Set<String> isolationLevels = ConcurrentHashMap.newKeySet();
        Gate(long first, long second) { activities = Set.of(first, second); }
    }

    @Override public Object intercept(Invocation invocation) throws Throwable {
        Gate current = gate;
        var statement = (MappedStatement) invocation.getArgs()[0];
        if (current != null && statement.getId().equals("com.campushub.registration.RegistrationMapper.insert")
                && current.activities.contains(((Number) ((Map<?, ?>) invocation.getArgs()[1]).get("activityId")).longValue())) {
            var executor = (Executor) invocation.getTarget();
            try (var sql = executor.getTransaction().getConnection().createStatement();
                    var result = sql.executeQuery("SELECT CONNECTION_ID(),@@transaction_isolation")) {
                if (!result.next()) throw new AssertionError("Missing MySQL session");
                current.connections.add(result.getLong(1));
                current.isolationLevels.add(result.getString(2));
            }
            current.ready.countDown();
            if (!current.ready.await(10, TimeUnit.SECONDS)) throw new AssertionError("Insert barrier timed out");
        }
        return invocation.proceed();
    }
}
