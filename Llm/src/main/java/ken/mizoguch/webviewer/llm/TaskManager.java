package ken.mizoguch.webviewer.llm;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Dual-lane asynchronous task coordinator isolating CPU-bound local {@code llm-core} jobs on a
 * dedicated platform daemon thread while running remote HTTP and RAG tasks concurrently on Java 21
 * virtual threads with per-task ID tracking, partial-cancellation awareness, and bounded history.
 *
 * @author mizoguch-ken
 */
final class TaskManager {

    static final String LANE_LOCAL = "local";
    static final String LANE_REMOTE = "remote";
    static final int MAX_CONCURRENT_TASKS = 16;
    static final int MAX_RESULT_HISTORY = 64;

    record TaskOutcome(String resultJson, boolean cancelled) {
    }

    @FunctionalInterface
    interface TaskBody {
        TaskOutcome run(String taskId, CancellationToken cancel) throws Exception;
    }

    @FunctionalInterface
    interface StopHook {
        void onStop();
    }

    private final EventDispatcher dispatcher_;
    private final Object stateLock_ = new Object();
    private final ExecutorService localExecutor_ = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon().name("llm_local_worker", 0).factory());
    private final ExecutorService remoteExecutor_ = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("llm_remote_worker-", 0).factory());

    private final Map<String, ActiveTask> activeTasks_ = new LinkedHashMap<>();
    private final Map<String, TaskRecord> history_ = new LinkedHashMap<>(MAX_RESULT_HISTORY,
            0.75f, false) {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, TaskRecord> eldest) {
            return size() > MAX_RESULT_HISTORY;
        }
    };

    private ActiveTask localTask_;
    private long nextId_ = 1L;
    private boolean closed_;

    private volatile String latestResult_;
    private volatile String lastLocalResult_;

    TaskManager(EventDispatcher dispatcher) {
        dispatcher_ = dispatcher;
    }

    String submitLocal(String kind, TaskBody body, StopHook stopHook) {
        return submit(LANE_LOCAL, kind, null, localExecutor_, body, stopHook);
    }

    String submitRemote(String kind, String profile, TaskBody body) {
        return submit(LANE_REMOTE, kind, profile, remoteExecutor_, body, null);
    }

    private String submit(String lane, String kind, String profile, ExecutorService executor,
            TaskBody body, StopHook stopHook) {
        synchronized (stateLock_) {
            if (closed_) {
                return null;
            }
            if (LANE_LOCAL.equals(lane) && (localTask_ != null)) {
                return null;
            }
            if (activeTasks_.size() >= MAX_CONCURRENT_TASKS) {
                return null;
            }
            String taskId = "task-" + (nextId_++);
            long startedAt = System.currentTimeMillis();
            CancellationToken cancel = new CancellationToken();
            ActiveTask task = new ActiveTask(taskId, lane, kind, profile, startedAt, cancel,
                    stopHook);
            if (LANE_LOCAL.equals(lane)) {
                localTask_ = task;
            }
            activeTasks_.put(taskId, task);
            history_.put(taskId, TaskRecord.running(task));
            try {
                executor.execute(() -> runTask(task, body));
                return task.taskId_;
            } catch (RejectedExecutionException ex) {
                cleanupActiveLocked(task);
                history_.remove(task.taskId_);
                return null;
            }
        }
    }

    private void runTask(ActiveTask task, TaskBody body) {
        dispatcher_.emitStarted(task.taskId_, task.lane_, task.kind_);
        try {
            if (task.cancel_.isCancelled()) {
                String cancelJson = dispatcher_.buildErrorJson(task.taskId_, task.kind_,
                        LlmRemoteException.cancelled(), true);
                recordAndEmitCancelled(task, cancelJson);
                return;
            }
            TaskOutcome outcome = body.run(task.taskId_, task.cancel_);
            String payload = (outcome == null) ? "{}" : outcome.resultJson();
            boolean cancelled = ((outcome != null) && outcome.cancelled())
                    || task.cancel_.isCancelled();
            if (cancelled) {
                recordAndEmitCancelled(task, payload);
            } else {
                long finishedAt = System.currentTimeMillis();
                synchronized (stateLock_) {
                    recordOutcomeLocked(task, TaskRecord.completed(task, finishedAt, payload),
                            payload);
                }
                dispatcher_.emitDone(task.taskId_, task.lane_, task.kind_, payload);
            }
        } catch (Throwable ex) {
            boolean cancelled = isCancelledFailure(task, ex);
            String errorJson = dispatcher_.buildErrorJson(task.taskId_, task.kind_, ex, cancelled);
            if (cancelled) {
                recordAndEmitCancelled(task, errorJson);
            } else {
                long finishedAt = System.currentTimeMillis();
                synchronized (stateLock_) {
                    recordOutcomeLocked(task, TaskRecord.failed(task, finishedAt, errorJson),
                            errorJson);
                }
                dispatcher_.emitError(task.taskId_, task.lane_, task.kind_, ex, errorJson);
            }
        } finally {
            synchronized (stateLock_) {
                cleanupActiveLocked(task);
            }
        }
    }

    private void recordAndEmitCancelled(ActiveTask task, String payloadJson) {
        long finishedAt = System.currentTimeMillis();
        synchronized (stateLock_) {
            recordOutcomeLocked(task, TaskRecord.cancelled(task, finishedAt, payloadJson),
                    payloadJson);
        }
        dispatcher_.emitCancelled(task.taskId_, task.lane_, task.kind_, payloadJson);
    }

    private static boolean isCancelledFailure(ActiveTask task, Throwable ex) {
        if ((ex instanceof LlmRemoteException rem) && "cancelled".equals(rem.errorCode())) {
            return true;
        }
        return task.cancel_.isCancelled() && (ex instanceof InterruptedException);
    }

    private void recordOutcomeLocked(ActiveTask task, TaskRecord record, String payloadJson) {
        history_.put(task.taskId_, record);
        latestResult_ = payloadJson;
        if (LANE_LOCAL.equals(task.lane_)) {
            lastLocalResult_ = payloadJson;
        }
    }

    private void cleanupActiveLocked(ActiveTask task) {
        activeTasks_.remove(task.taskId_, task);
        if (localTask_ == task) {
            localTask_ = null;
        }
    }

    boolean isIdle() {
        synchronized (stateLock_) {
            return activeTasks_.isEmpty();
        }
    }

    boolean isLocalIdle() {
        synchronized (stateLock_) {
            return localTask_ == null;
        }
    }

    boolean hasRemoteTasks() {
        synchronized (stateLock_) {
            for (ActiveTask task : activeTasks_.values()) {
                if (LANE_REMOTE.equals(task.lane_)) {
                    return true;
                }
            }
            return false;
        }
    }

    int activeCount() {
        synchronized (stateLock_) {
            return activeTasks_.size();
        }
    }

    boolean cancelTask(String taskId) {
        if ((taskId == null) || taskId.trim().isEmpty()) {
            return false;
        }
        ActiveTask target;
        synchronized (stateLock_) {
            target = activeTasks_.get(taskId.trim());
        }
        if (target == null) {
            return false;
        }
        cancelSingle(target);
        return true;
    }

    int cancelLane(String lane) {
        if (!LANE_LOCAL.equals(lane) && !LANE_REMOTE.equals(lane)) {
            throw new IllegalArgumentException(
                    "Invalid lane \"" + lane + "\": use \"local\" or \"remote\"");
        }
        List<ActiveTask> targets = new ArrayList<>();
        synchronized (stateLock_) {
            for (ActiveTask task : activeTasks_.values()) {
                if (lane.equals(task.lane_)) {
                    targets.add(task);
                }
            }
        }
        for (ActiveTask target : targets) {
            cancelSingle(target);
        }
        return targets.size();
    }

    int cancelAll() {
        List<ActiveTask> targets;
        synchronized (stateLock_) {
            if (activeTasks_.isEmpty()) {
                return 0;
            }
            targets = new ArrayList<>(activeTasks_.values());
        }
        for (ActiveTask target : targets) {
            cancelSingle(target);
        }
        return targets.size();
    }

    private static void cancelSingle(ActiveTask target) {
        target.cancel_.cancel();
        if (target.stopHook_ != null) {
            target.stopHook_.onStop();
        }
    }

    void clearLocalResult() {
        lastLocalResult_ = null;
    }

    String latestResult() {
        return latestResult_;
    }

    String lastLocalResult() {
        return lastLocalResult_;
    }

    String taskResult(String taskId) {
        if ((taskId == null) || taskId.trim().isEmpty()) {
            return null;
        }
        synchronized (stateLock_) {
            TaskRecord rec = history_.get(taskId.trim());
            return (rec == null) ? null : rec.resultJson_;
        }
    }

    String taskStatusJson(String taskId) {
        if ((taskId == null) || taskId.trim().isEmpty()) {
            return null;
        }
        synchronized (stateLock_) {
            TaskRecord rec = history_.get(taskId.trim());
            return (rec == null) ? null : JsonUtil.GSON.toJson(rec.toJson());
        }
    }

    JsonArray activeTasksJsonArray() {
        synchronized (stateLock_) {
            JsonArray arr = new JsonArray();
            for (ActiveTask task : activeTasks_.values()) {
                JsonObject item = new JsonObject();
                item.addProperty("taskId", task.taskId_);
                item.addProperty("lane", task.lane_);
                item.addProperty("kind", task.kind_);
                item.addProperty("profile", task.profile_);
                item.addProperty("startedAt", task.startedAt_);
                item.addProperty("cancelled", task.cancel_.isCancelled());
                arr.add(item);
            }
            return arr;
        }
    }

    String allTasksJson() {
        synchronized (stateLock_) {
            JsonArray arr = new JsonArray();
            for (TaskRecord rec : history_.values()) {
                arr.add(rec.toJson());
            }
            return JsonUtil.GSON.toJson(arr);
        }
    }

    void close() {
        List<ActiveTask> targets;
        synchronized (stateLock_) {
            if (closed_) {
                return;
            }
            closed_ = true;
            targets = new ArrayList<>(activeTasks_.values());
        }
        for (ActiveTask target : targets) {
            cancelSingle(target);
        }
        localExecutor_.shutdownNow();
        remoteExecutor_.shutdownNow();
        try {
            localExecutor_.awaitTermination(200L, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        long cancelledAt = System.currentTimeMillis();
        synchronized (stateLock_) {
            for (ActiveTask target : activeTasks_.values()) {
                TaskRecord existing = history_.get(target.taskId_);
                if ((existing != null) && "running".equals(existing.status_)) {
                    String cancelJson = dispatcher_.buildErrorJson(target.taskId_, target.kind_,
                            LlmRemoteException.cancelled(), true);
                    history_.put(target.taskId_,
                            TaskRecord.cancelled(target, cancelledAt, cancelJson));
                }
            }
            activeTasks_.clear();
            localTask_ = null;
        }
        latestResult_ = null;
        lastLocalResult_ = null;
    }

    private static final class ActiveTask {
        final String taskId_;
        final String lane_;
        final String kind_;
        final String profile_;
        final long startedAt_;
        final CancellationToken cancel_;
        final StopHook stopHook_;

        ActiveTask(String taskId, String lane, String kind, String profile, long startedAt,
                CancellationToken cancel, StopHook stopHook) {
            taskId_ = taskId;
            lane_ = lane;
            kind_ = kind;
            profile_ = profile;
            startedAt_ = startedAt;
            cancel_ = cancel;
            stopHook_ = stopHook;
        }
    }

    private static final class TaskRecord {
        final String taskId_;
        final String lane_;
        final String kind_;
        final String profile_;
        final String status_;
        final long startedAt_;
        final Long finishedAt_;
        final String resultJson_;

        private TaskRecord(String taskId, String lane, String kind, String profile, String status,
                long startedAt, Long finishedAt, String resultJson) {
            taskId_ = taskId;
            lane_ = lane;
            kind_ = kind;
            profile_ = profile;
            status_ = status;
            startedAt_ = startedAt;
            finishedAt_ = finishedAt;
            resultJson_ = resultJson;
        }

        static TaskRecord running(ActiveTask task) {
            return new TaskRecord(task.taskId_, task.lane_, task.kind_, task.profile_,
                    "running", task.startedAt_, null, null);
        }

        static TaskRecord completed(ActiveTask task, long finishedAt, String resultJson) {
            return new TaskRecord(task.taskId_, task.lane_, task.kind_, task.profile_,
                    "completed", task.startedAt_, Long.valueOf(finishedAt), resultJson);
        }

        static TaskRecord failed(ActiveTask task, long finishedAt, String errorJson) {
            return new TaskRecord(task.taskId_, task.lane_, task.kind_, task.profile_,
                    "failed", task.startedAt_, Long.valueOf(finishedAt), errorJson);
        }

        static TaskRecord cancelled(ActiveTask task, long finishedAt, String errorJson) {
            return new TaskRecord(task.taskId_, task.lane_, task.kind_, task.profile_,
                    "cancelled", task.startedAt_, Long.valueOf(finishedAt), errorJson);
        }

        JsonObject toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("taskId", taskId_);
            obj.addProperty("lane", lane_);
            obj.addProperty("kind", kind_);
            obj.addProperty("profile", profile_);
            obj.addProperty("status", status_);
            obj.addProperty("startedAt", startedAt_);
            if (finishedAt_ != null) {
                obj.addProperty("finishedAt", finishedAt_.longValue());
            } else {
                obj.add("finishedAt", null);
            }
            return obj;
        }
    }
}
