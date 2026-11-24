package ken.mizoguch.webviewer.llm;

/**
 * Public JavaScript sub-facade ({@code llm.remote()}) managing named remote provider profiles
 * (OpenAI-compatible, Azure OpenAI, native Anthropic) and launching concurrent remote tasks on
 * Java 21 virtual threads.
 *
 * @author mizoguch-ken
 */
public final class RemoteApi {

    private final Llm owner_;
    private final RemoteProfileRegistry profiles_;
    private final RemoteHttpClient client_;
    private final TaskManager tasks_;
    private final EventDispatcher dispatcher_;

    RemoteApi(Llm owner, RemoteProfileRegistry profiles, RemoteHttpClient client,
            TaskManager tasks, EventDispatcher dispatcher) {
        owner_ = owner;
        profiles_ = profiles;
        client_ = client;
        tasks_ = tasks;
        dispatcher_ = dispatcher;
    }

    /**
     * Creates or updates a named endpoint profile (or the active profile when {@code name} is
     * {@code null}/blank). Returns redacted profile metadata JSON or {@code null} on error.
     */
    public String setProfile(String name, String configJson) {
        try {
            RemoteConfig cfg = profiles_.configure(name, configJson);
            String resolved = profiles_.resolveName(name);
            var obj = cfg.toPublicJson(tasks_.hasRemoteTasks() ? "asking" : "idle");
            obj.addProperty("profile", resolved);
            return JsonUtil.GSON.toJson(obj);
        } catch (LlmRemoteException | IllegalArgumentException ex) {
            owner_.logException(ex);
            return null;
        }
    }

    /**
     * Switches the active remote profile cursor to an existing profile {@code name}.
     */
    public String useProfile(String name) {
        try {
            RemoteConfig cfg = profiles_.use(name);
            var obj = cfg.toPublicJson(tasks_.hasRemoteTasks() ? "asking" : "idle");
            obj.addProperty("profile", profiles_.activeName());
            return JsonUtil.GSON.toJson(obj);
        } catch (LlmRemoteException ex) {
            owner_.logException(ex);
            return null;
        }
    }

    /**
     * Removes profile {@code name} (or active profile when {@code null}/blank).
     */
    public Boolean removeProfile(String name) {
        try {
            return profiles_.remove(name);
        } catch (LlmRemoteException ex) {
            owner_.logException(ex);
            return false;
        }
    }

    /**
     * Returns redacted metadata for {@code name} (or active profile when {@code null}/blank).
     */
    public String profileInfo(String name) {
        try {
            return JsonUtil.GSON.toJson(profiles_.profilePublicJson(
                    name, tasks_.hasRemoteTasks() ? "asking" : "idle"));
        } catch (LlmRemoteException ex) {
            owner_.logException(ex);
            return null;
        }
    }

    /**
     * Lists all configured profiles with redacted credentials as JSON.
     */
    public String listProfiles() {
        return JsonUtil.GSON.toJson(profiles_.allProfilesPublicJson(
                tasks_.hasRemoteTasks() ? "asking" : "idle"));
    }

    /**
     * Executes {@code /chat/completions} (or Anthropic {@code /messages}) asynchronously.
     */
    public String chat(String request, String profile) {
        return submitRemote(RemoteHttpClient.CHAT, request, profile);
    }

    /**
     * Executes {@code /completions} asynchronously.
     */
    public String completions(String request, String profile) {
        return submitRemote(RemoteHttpClient.COMPLETIONS, request, profile);
    }

    /**
     * Executes OpenAI {@code /responses} asynchronously.
     */
    public String responses(String request, String profile) {
        return submitRemote(RemoteHttpClient.RESPONSES, request, profile);
    }

    /**
     * Executes {@code /embeddings} asynchronously.
     */
    public String embeddings(String request, String profile) {
        return submitRemote(RemoteHttpClient.EMBEDDINGS, request, profile);
    }

    /**
     * Executes {@code GET /models} asynchronously.
     */
    public String models(String profile) {
        return submitRemote(RemoteHttpClient.MODELS, null, profile);
    }

    private String submitRemote(String endpoint, String request, String profile) {
        if (!RemoteHttpClient.MODELS.equals(endpoint)
                && ((request == null) || request.trim().isEmpty())) {
            owner_.logError(LlmI18n.get(owner_, "llm.remote.requestEmpty"));
            return null;
        }
        final RemoteConfig snapshot;
        final String resolvedProfile;
        try {
            snapshot = profiles_.require(profile);
            resolvedProfile = profiles_.resolveName(profile);
        } catch (LlmRemoteException ex) {
            owner_.logException(ex);
            return null;
        }
        if ((snapshot.provider() == RemoteConfig.Provider.ANTHROPIC)
                && !RemoteHttpClient.CHAT.equals(endpoint)
                && !RemoteHttpClient.MODELS.equals(endpoint)) {
            owner_.logError(LlmI18n.get(owner_, "llm.remote.anthropicEndpointUnsupported", endpoint));
            return null;
        }

        String taskId = tasks_.submitRemote(
                endpoint,
                resolvedProfile,
                (id, cancel) -> {
                    RemoteHttpClient.RemoteExecutionResult res = client_.execute(
                            snapshot, endpoint, request, remoteListener(id, endpoint), cancel);
                    return new TaskManager.TaskOutcome(res.bodyJson(), res.stoppedEarly());
                });
        if (taskId == null) {
            owner_.logError(LlmI18n.get(owner_, "llm.remote.queueFullOrClosed"));
        }
        return taskId;
    }

    RemoteListener remoteListener(String taskId, String kind) {
        return new RemoteListener() {
            @Override
            public void onToken(String piece) {
                dispatcher_.emitToken(taskId, TaskManager.LANE_REMOTE, kind, piece);
            }

            @Override
            public void onReasoning(String piece) {
                dispatcher_.emitReasoning(taskId, TaskManager.LANE_REMOTE, kind, piece);
            }

            @Override
            public void onToolCallDelta(int index, String id, String name, String argumentsDelta) {
                dispatcher_.emitToolCallDelta(taskId, TaskManager.LANE_REMOTE, kind, index, id,
                        name, argumentsDelta);
            }

            @Override
            public void onLog(String message) {
                dispatcher_.emitLog(taskId, TaskManager.LANE_REMOTE, message);
            }
        };
    }
}
