package ken.mizoguch.webviewer.llm;

/**
 * Public JavaScript sub-facade ({@code llm.tasks()}) inspecting and cancelling running or
 * historical asynchronous tasks across both local and remote execution lanes.
 *
 * @author mizoguch-ken
 */
public final class TaskApi {

    private final Llm owner_;
    private final LocalModelService localService_;
    private final TaskManager tasks_;

    TaskApi(Llm owner, LocalModelService localService, TaskManager tasks) {
        owner_ = owner;
        localService_ = localService;
        tasks_ = tasks;
    }

    /**
     * Returns true when both local and remote lanes are idle.
     */
    public Boolean isIdle() {
        return tasks_.isIdle() && localService_.isIdle();
    }

    /**
     * Returns true when the local CPU lane has no running job.
     */
    public Boolean isLocalIdle() {
        return tasks_.isLocalIdle() && localService_.isIdle();
    }

    /**
     * Returns true when one or more remote tasks are currently running.
     */
    public Boolean hasRemoteTasks() {
        return tasks_.hasRemoteTasks();
    }

    /**
     * Returns a JSON array describing all currently running tasks.
     */
    public String active() {
        return JsonUtil.GSON.toJson(tasks_.activeTasksJsonArray());
    }

    /**
     * Returns a JSON array listing active and recent (up to 64) task records.
     */
    public String list() {
        return tasks_.allTasksJson();
    }

    /**
     * Returns status metadata JSON for {@code taskId}, or {@code null} if unknown.
     */
    public String status(String taskId) {
        return tasks_.taskStatusJson(taskId);
    }

    /**
     * Returns the JSON result or error payload stored for {@code taskId}, or {@code null}.
     */
    public String result(String taskId) {
        return tasks_.taskResult(taskId);
    }

    /**
     * Returns the JSON payload of the most recently finished task across either lane.
     */
    public String latestResult() {
        return tasks_.latestResult();
    }

    /**
     * Cancels a single active task by {@code taskId}.
     */
    public Boolean cancel(String taskId) {
        return tasks_.cancelTask(taskId);
    }

    /**
     * Cancels all active tasks in {@code lane} ({@code "local"} or {@code "remote"}) and returns
     * the number of cancelled tasks.
     */
    public Integer cancelLane(String lane) {
        try {
            int count = tasks_.cancelLane(lane);
            if (TaskManager.LANE_LOCAL.equals(lane) && !localService_.isIdle()) {
                localService_.stopIfRunning();
                count = Math.max(count, 1);
            }
            return count;
        } catch (IllegalArgumentException ex) {
            owner_.logException(ex);
            return 0;
        }
    }

    /**
     * Cancels all active tasks on both lanes and returns the number cancelled.
     */
    public Integer cancelAll() {
        int count = tasks_.cancelAll();
        if (!localService_.isIdle()) {
            localService_.stopIfRunning();
            count = Math.max(count, 1);
        }
        return count;
    }
}
