package snill.client.api.utils.rpc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

final class DiscordIpcClient implements Closeable {

    private static final int OP_HANDSHAKE = 0;
    private static final int OP_FRAME = 1;
    private static final int OP_CLOSE = 2;
    private static final int OP_PING = 3;
    private static final int OP_PONG = 4;
    private static final int IPC_VERSION = 1;
    private static final int MAX_PIPES = 10;
    private static final String[] PIPE_PATH_FORMATS = {
            "\\\\?\\pipe\\discord-ipc-%d",
            "\\\\.\\pipe\\discord-ipc-%d"
    };

    private final String clientId;
    private final long processId;

    private RandomAccessFile pipe;

    DiscordIpcClient(String clientId) {
        this.clientId = clientId;
        this.processId = ProcessHandle.current().pid();
    }

    boolean isConnected() {
        return pipe != null;
    }

    void connect() throws IOException {
        IOException lastException = null;

        for (String pathFormat : PIPE_PATH_FORMATS) {
            for (int i = 0; i < MAX_PIPES; i++) {
                RandomAccessFile candidate = null;

                try {
                    candidate = new RandomAccessFile(String.format(pathFormat, i), "rw");
                    pipe = candidate;
                    handshake();
                    return;
                } catch (IOException exception) {
                    lastException = exception;
                    pipe = null;

                    if (candidate != null) {
                        candidate.close();
                    }
                }
            }
        }

        throw new IOException("Discord desktop client is not available.", lastException);
    }

    void setActivity(Activity activity) throws IOException {
        ensureConnected();

        JsonObject payload = new JsonObject();
        JsonObject args = new JsonObject();

        payload.addProperty("cmd", "SET_ACTIVITY");
        payload.addProperty("nonce", UUID.randomUUID().toString());

        args.addProperty("pid", processId);
        args.add("activity", activity.toJson());
        payload.add("args", args);

        writeJson(OP_FRAME, payload);
        JsonObject response = readJsonFrame();
        ensureNoError(response);
    }

    private void handshake() throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("v", IPC_VERSION);
        payload.addProperty("client_id", clientId);

        writeJson(OP_HANDSHAKE, payload);
        JsonObject response = readJsonFrame();
        ensureNoError(response);
    }

    private void ensureConnected() throws IOException {
        if (!isConnected()) {
            throw new IOException("Discord IPC connection is closed.");
        }
    }

    private void writeJson(int opcode, JsonObject payload) throws IOException {
        writeFrame(opcode, payload.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void writeFrame(int opcode, byte[] payload) throws IOException {
        ensureConnected();

        ByteBuffer header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(opcode);
        header.putInt(payload.length);

        pipe.write(header.array());
        pipe.write(payload);
    }

    private JsonObject readJsonFrame() throws IOException {
        while (true) {
            ByteBuffer header = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
            pipe.readFully(header.array());

            int opcode = header.getInt(0);
            int length = header.getInt(4);
            byte[] payload = new byte[length];
            pipe.readFully(payload);

            if (opcode == OP_PING) {
                writeFrame(OP_PONG, payload);
                continue;
            }

            if (opcode == OP_CLOSE) {
                throw new EOFException("Discord IPC closed the connection.");
            }

            if (opcode != OP_FRAME) {
                throw new IOException("Unexpected Discord IPC opcode: " + opcode);
            }

            return JsonParser.parseString(new String(payload, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private void ensureNoError(JsonObject payload) throws IOException {
        if (!payload.has("evt") || payload.get("evt").isJsonNull()) {
            return;
        }

        String event = payload.get("evt").getAsString();
        if (!"ERROR".equals(event)) {
            return;
        }

        JsonObject data = payload.has("data") && payload.get("data").isJsonObject()
                ? payload.getAsJsonObject("data")
                : new JsonObject();

        int code = data.has("code") ? data.get("code").getAsInt() : -1;
        String message = data.has("message") ? data.get("message").getAsString() : "Unknown Discord RPC error";
        throw new IOException("Discord RPC error " + code + ": " + message);
    }

    @Override
    public void close() throws IOException {
        if (pipe != null) {
            pipe.close();
            pipe = null;
        }
    }

    static final class Activity {
        private final String details;
        private final String state;
        private final long startTimestamp;
        private final String detailsUrl;
        private final String stateUrl;

        Activity(String details, String state, long startTimestamp, String detailsUrl, String stateUrl) {
            this.details = details;
            this.state = state;
            this.startTimestamp = startTimestamp;
            this.detailsUrl = detailsUrl;
            this.stateUrl = stateUrl;
        }

        JsonObject toJson() {
            JsonObject activity = new JsonObject();
            JsonObject timestamps = new JsonObject();

            activity.addProperty("type", 0);
            activity.addProperty("details", details);
            activity.addProperty("state", state);
            activity.addProperty("instance", true);

            if (detailsUrl != null && !detailsUrl.isEmpty()) {
                activity.addProperty("details_url", detailsUrl);
            }

            if (stateUrl != null && !stateUrl.isEmpty()) {
                activity.addProperty("state_url", stateUrl);
            }

            if (startTimestamp > 0L) {
                timestamps.addProperty("start", startTimestamp);
                activity.add("timestamps", timestamps);
            }

            return activity;
        }
    }
}
