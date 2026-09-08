package snill.client.api.utils.browser;

import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import snill.client.api.QClient;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class BrowserEngine implements QClient {

    private static BrowserEngine INSTANCE;

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (INSTANCE != null && INSTANCE.browserProcess != null && INSTANCE.browserProcess.isAlive()) {
                    long pid = INSTANCE.browserProcess.pid();
                    new ProcessBuilder("taskkill", "/F", "/T", "/PID", String.valueOf(pid)).start().waitFor();
                    INSTANCE.browserProcess.destroyForcibly();
                }
            } catch (Exception ignored) {
            }
        }, "Polar-Browser-ShutdownHook"));
    }

    public static BrowserEngine getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new BrowserEngine();
        }
        return INSTANCE;
    }

    public static final Identifier TEXTURE_ID = Identifier.of("polar", "browser_screen");

    private Process browserProcess;
    private WebSocket webSocket;
    private int port = 9222;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean hasTexture = new AtomicBoolean(false);
    private final AtomicInteger msgId = new AtomicInteger(1);

    private final AtomicReference<NativeImage> pendingImage = new AtomicReference<>();
    private NativeImageBackedTexture currentTexture;
    private String currentUrl = "https://www.google.com";
    private String pageTitle = "Загрузка...";
    private volatile int viewportWidth = 1280;
    private volatile int viewportHeight = 720;
    private final AtomicBoolean isFullscreen = new AtomicBoolean(false);
    private final AtomicBoolean isDecoding = new AtomicBoolean(false);
    private java.util.concurrent.ExecutorService decodeExecutor;

    private final java.util.concurrent.ConcurrentLinkedQueue<String> sendQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private final AtomicBoolean isSending = new AtomicBoolean(false);

    public boolean isConnected() {
        return connected.get();
    }

    public boolean hasTexture() {
        return hasTexture.get();
    }

    public String getCurrentUrl() {
        return currentUrl;
    }

    public String getPageTitle() {
        return pageTitle;
    }

    public int getViewportWidth() {
        return viewportWidth;
    }

    public int getViewportHeight() {
        return viewportHeight;
    }

    public boolean isFullscreen() {
        return isFullscreen.get();
    }

    public void exitFullscreen() {
        sendCdp("Runtime.evaluate", "{\"expression\":\"document.exitFullscreen ? document.exitFullscreen() : (document.querySelector('#movie_player') && document.querySelector('#movie_player').classList.remove('ytp-fullscreen'))\"}");
        isFullscreen.set(false);
    }

    public synchronized void start(String initialUrl) {
        start(initialUrl, "Edge");
    }

    public synchronized void start(String initialUrl, String preferredBrowser) {
        if (running.get()) {
            if (initialUrl != null && !initialUrl.isEmpty() && !initialUrl.equalsIgnoreCase(currentUrl)) {
                navigate(initialUrl);
            }
            return;
        }

        if (decodeExecutor == null || decodeExecutor.isShutdown()) {
            decodeExecutor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "Polar-FrameDecoder");
                t.setDaemon(true);
                return t;
            });
        }

        if (initialUrl != null && !initialUrl.isEmpty()) {
            this.currentUrl = initialUrl;
        }

        running.set(true);
        connected.set(false);
        hasTexture.set(false);

        new Thread(() -> {
            try {
                String browserPath = findBrowserExecutable(preferredBrowser);
                if (browserPath == null) {
                    pageTitle = "Браузер не найден";
                    log("No browser found for " + preferredBrowser);
                    return;
                }
                log("Starting browser: " + browserPath);

                port = findFreePort();
                File userDataDir = new File(System.getProperty("java.io.tmpdir"), "polar_browser_profile_" + port);
                userDataDir.mkdirs();

                ProcessBuilder pb = new ProcessBuilder(
                        browserPath,
                        "--headless=new",
                        "--remote-debugging-port=" + port,
                        "--user-data-dir=" + userDataDir.getAbsolutePath(),
                        "--disable-gpu=false",
                        "--window-size=1280,720",
                        "--force-device-scale-factor=1",
                        "--hide-scrollbars",
                        "--no-first-run",
                        "--no-default-browser-check",
                        "--disable-sync",
                        "--disable-background-networking",
                        "--disable-component-update",
                        "--disable-extensions",
                        "--disable-default-apps",
                        "--disable-background-timer-throttling",
                        "--disable-backgrounding-occluded-windows",
                        "--disable-renderer-backgrounding",
                        "--disable-features=AudioServiceSandbox,msEdgeSyncDialog,msEdgeFirstRunExperience",
                        "--autoplay-policy=no-user-gesture-required",
                        currentUrl
                );
                pb.redirectErrorStream(true);
                browserProcess = pb.start();

                HttpClient httpClient = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(2))
                        .build();

                String wsUrl = null;
                for (int i = 0; i < 35; i++) {
                    if (!running.get()) return;
                    try {
                        Thread.sleep(300);
                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create("http://127.0.0.1:" + port + "/json/list"))
                                .timeout(Duration.ofSeconds(1))
                                .GET()
                                .build();
                        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                        if (response.statusCode() == 200 && response.body().contains("webSocketDebuggerUrl")) {
                            wsUrl = extractTargetWebSocketUrl(response.body());
                            if (wsUrl != null) break;
                        }
                    } catch (Exception ignored) {
                    }
                }

                if (wsUrl == null) {
                    log("Failed to get WebSocket debugger URL");
                    return;
                }

                httpClient.newWebSocketBuilder()
                        .buildAsync(URI.create(wsUrl), new WebSocket.Listener() {
                            private final StringBuilder messageBuffer = new StringBuilder();

                            @Override
                            public void onOpen(WebSocket ws) {
                                webSocket = ws;
                                connected.set(true);
                                pageTitle = "Подключено";
                                log("WebSocket connected. Starting smooth screencast (720p)...");

                                sendCdp("Page.enable", "{}");
                                sendCdp("Runtime.enable", "{}");
                                sendCdp("Emulation.setDeviceMetricsOverride", "{\"width\":1280,\"height\":720,\"deviceScaleFactor\":1,\"mobile\":false}");
                                sendCdp("Emulation.setVisibleSize", "{\"width\":1280,\"height\":720}");
                                String autoScript = "(function() {" +
                                        "var fsElement = null;" +
                                        "try {" +
                                        "Object.defineProperty(document, 'fullscreenElement', {" +
                                        "get: function() { return fsElement; }," +
                                        "configurable: true" +
                                        "});" +
                                        "Object.defineProperty(document, 'fullscreen', {" +
                                        "get: function() { return !!fsElement; }," +
                                        "configurable: true" +
                                        "});" +
                                        "Element.prototype.requestFullscreen = function() {" +
                                        "fsElement = this;" +
                                        "this.classList.add('polar-fullscreen');" +
                                        "var style = document.getElementById('polar-fs-style');" +
                                        "if (!style) {" +
                                        "style = document.createElement('style');" +
                                        "style.id = 'polar-fs-style';" +
                                        "style.textContent = '.polar-fullscreen, #movie_player.polar-fullscreen, ytd-watch-flexy[fullscreen] #movie_player { position: fixed !important; top: 0 !important; left: 0 !important; width: 100vw !important; height: 100vh !important; z-index: 2147483647 !important; max-width: 100% !important; max-height: 100% !important; }';" +
                                        "(document.head || document.documentElement).appendChild(style);" +
                                        "}" +
                                        "console.log('[POLAR_FS:true]');" +
                                        "document.dispatchEvent(new Event('fullscreenchange'));" +
                                        "return Promise.resolve();" +
                                        "};" +
                                        "document.exitFullscreen = function() {" +
                                        "if (fsElement) {" +
                                        "fsElement.classList.remove('polar-fullscreen');" +
                                        "fsElement = null;" +
                                        "}" +
                                        "console.log('[POLAR_FS:false]');" +
                                        "document.dispatchEvent(new Event('fullscreenchange'));" +
                                        "return Promise.resolve();" +
                                        "};" +
                                        "} catch(e) {}" +
                                        "document.addEventListener('focusin', function(e) {" +
                                        "var t = e.target;" +
                                        "if (t && (t.tagName === 'INPUT' || t.tagName === 'TEXTAREA' || t.isContentEditable || t.getAttribute('role') === 'textbox' || t.getAttribute('role') === 'combobox')) {" +
                                        "console.log('[POLAR_INPUT_FOCUS:true]');" +
                                        "}" +
                                        "}, true);" +
                                        "document.addEventListener('focusout', function(e) {" +
                                        "setTimeout(function() {" +
                                        "var el = document.activeElement;" +
                                        "var isInput = el && (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.isContentEditable || el.getAttribute('role') === 'textbox' || el.getAttribute('role') === 'combobox');" +
                                        "if (!isInput) {" +
                                        "console.log('[POLAR_INPUT_FOCUS:false]');" +
                                        "}" +
                                        "}, 100);" +
                                        "}, true);" +
                                        "setInterval(function(){" +
                                        "document.querySelectorAll('ytd-video-preview video, #preview video, .inline-preview-player video').forEach(function(p){p.muted=true;p.pause();});" +
                                        "var m = document.querySelector('video.html5-main-video') || document.querySelector('#movie_player video');" +
                                        "if(m && m.muted){m.muted=false;m.volume=1.0;}" +
                                        "var mp = document.querySelector('#movie_player');" +
                                        "if (mp) {" +
                                        "var isYtFs = mp.classList.contains('ytp-fullscreen') || !!fsElement;" +
                                        "if (isYtFs && !fsElement) {" +
                                        "fsElement = mp;" +
                                        "console.log('[POLAR_FS:true]');" +
                                        "} else if (!isYtFs && fsElement) {" +
                                        "fsElement = null;" +
                                        "console.log('[POLAR_FS:false]');" +
                                        "}" +
                                        "}" +
                                        "}, 500);" +
                                        "})();";
                                sendCdp("Page.addScriptToEvaluateOnNewDocument", "{\"source\":\"" + escapeJson(autoScript) + "\"}");
                                sendCdp("Runtime.evaluate", "{\"expression\":\"" + escapeJson(autoScript) + "\"}");
                                if (currentUrl != null && !currentUrl.isEmpty() && !currentUrl.equalsIgnoreCase("about:blank")) {
                                    sendCdp("Page.navigate", "{\"url\":\"" + escapeJson(currentUrl) + "\"}");
                                }
                                sendCdp("Page.startScreencast", "{\"format\":\"png\",\"maxWidth\":1280,\"maxHeight\":720,\"everyNthFrame\":1}");
                                unmuteAudio();
                                WebSocket.Listener.super.onOpen(ws);
                            }

                            @Override
                            public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                                messageBuffer.append(data);
                                if (last) {
                                    String message = messageBuffer.toString();
                                    messageBuffer.setLength(0);
                                    try {
                                        handleCdpMessage(message);
                                    } catch (Throwable t) {
                                        log("Error handling CDP message: " + t.getMessage());
                                    }
                                }
                                return WebSocket.Listener.super.onText(ws, data, last);
                            }

                            @Override
                            public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
                                connected.set(false);
                                log("WebSocket closed: " + reason);
                                return WebSocket.Listener.super.onClose(ws, statusCode, reason);
                            }

                            @Override
                            public void onError(WebSocket ws, Throwable error) {
                                connected.set(false);
                                log("WebSocket error: " + (error != null ? error.getMessage() : "unknown"));
                                WebSocket.Listener.super.onError(ws, error);
                            }
                        });
            } catch (Exception e) {
                log("Error in BrowserEngine runner: " + e.getMessage());
            }
        }, "Polar-BrowserEngine").start();
    }

    private void handleCdpMessage(String json) {
        if (json.contains("[POLAR_FS:true]")) {
            isFullscreen.set(true);
            sendCdp("Emulation.setDeviceMetricsOverride", "{\"width\":1280,\"height\":720,\"deviceScaleFactor\":1,\"mobile\":false}");
            log("[Fullscreen] Entered fullscreen mode");
        } else if (json.contains("[POLAR_FS:false]")) {
            isFullscreen.set(false);
            sendCdp("Emulation.setDeviceMetricsOverride", "{\"width\":1280,\"height\":720,\"deviceScaleFactor\":1,\"mobile\":false}");
            log("[Fullscreen] Exited fullscreen mode");
        }

        if (json.contains("[POLAR_INPUT_FOCUS:true]")) {
            snill.client.client.modules.impl.render.Browser.INSTANCE.setWebTypingMode(true);
            log("[WebInput] Focus gained in web page");
        } else if (json.contains("[POLAR_INPUT_FOCUS:false]")) {
            snill.client.client.modules.impl.render.Browser.INSTANCE.setWebTypingMode(false);
            log("[WebInput] Focus lost in web page");
        }

        if (json.contains("\"method\":\"Page.screencastFrame\"")) {
            // 1. Извлекаем sessionId и ВСЕГДА немедленно отправляем подтверждение (ACK)
            // Без этого Chromium через несколько секунд навсегда прекращает слать кадры!
            int sessIdx = json.lastIndexOf("\"sessionId\"");
            if (sessIdx != -1) {
                int colon = json.indexOf(':', sessIdx);
                if (colon != -1) {
                    int start = colon + 1;
                    while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
                        start++;
                    }
                    int end = start;
                    while (end < json.length() && Character.isDigit(json.charAt(end))) {
                        end++;
                    }
                    if (end > start) {
                        String sId = json.substring(start, end);
                        sendCdp("Page.screencastFrameAck", "{\"sessionId\":" + sId + "}");
                    }
                }
            }

            // 2. Если фоновый декодер ещё обрабатывает предыдущий кадр — пропускаем (ACK уже ушёл, нет лага)
            if (!isDecoding.compareAndSet(false, true)) {
                return;
            }

            int dataIdx = json.indexOf("\"data\":");
            if (dataIdx != -1) {
                int firstQuote = json.indexOf('"', dataIdx + 7);
                if (firstQuote != -1) {
                    int quoteStart = firstQuote + 1;
                    int quoteEnd = json.indexOf('"', quoteStart);
                    if (quoteEnd != -1) {
                        String base64 = json.substring(quoteStart, quoteEnd);
                        java.util.concurrent.ExecutorService exec = decodeExecutor;
                        if (exec != null && !exec.isShutdown()) {
                            exec.submit(() -> {
                                try {
                                    byte[] bytes = Base64.getDecoder().decode(base64);
                                    NativeImage img = NativeImage.read(bytes);
                                    if (img != null) {
                                        int w = img.getWidth();
                                        int h = img.getHeight();
                                        if (w > 0 && h > 0) {
                                            viewportWidth = w;
                                            viewportHeight = h;
                                        }
                                        NativeImage old = pendingImage.getAndSet(img);
                                        if (old != null) {
                                            old.close();
                                        }
                                    }
                                } catch (Exception e) {
                                    log("Error decoding NativeImage frame: " + e.getMessage());
                                } finally {
                                    isDecoding.set(false);
                                }
                            });
                            return;
                        }
                    }
                }
            }
            isDecoding.set(false);
        } else if (json.contains("\"method\":\"Page.navigatedWithinDocument\"") || json.contains("\"method\":\"Page.frameNavigated\"")) {
            int urlIdx = json.indexOf("\"url\":\"");
            if (urlIdx != -1) {
                int start = urlIdx + 7;
                int end = json.indexOf('"', start);
                if (end > start) {
                    currentUrl = json.substring(start, end);
                    unmuteAudio();
                }
            }
        }
    }

    public void updateTexture() {
        NativeImage newImg = pendingImage.getAndSet(null);
        if (newImg != null && mc != null) {
            try {
                if (currentTexture == null) {
                    currentTexture = new NativeImageBackedTexture(newImg);
                    mc.getTextureManager().registerTexture(TEXTURE_ID, currentTexture);
                } else {
                    NativeImage existing = currentTexture.getImage();
                    if (existing != null && existing.getWidth() == newImg.getWidth() && existing.getHeight() == newImg.getHeight()) {
                        existing.copyFrom(newImg);
                        currentTexture.upload();
                        newImg.close();
                    } else {
                        mc.getTextureManager().destroyTexture(TEXTURE_ID);
                        currentTexture = new NativeImageBackedTexture(newImg);
                        mc.getTextureManager().registerTexture(TEXTURE_ID, currentTexture);
                    }
                }
                hasTexture.set(true);
            } catch (Exception e) {
                log("Error uploading texture: " + e.getMessage());
                newImg.close();
            }
        }
    }

    public static void log(String msg) {
        try {
            java.nio.file.Files.writeString(
                    java.nio.file.Path.of("browser_engine.log"),
                    "[" + new java.util.Date() + "] " + msg + System.lineSeparator(),
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND
            );
        } catch (Exception ignored) {
        }
    }

    public void navigate(String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }
        this.currentUrl = url;
        sendCdp("Page.navigate", "{\"url\":\"" + escapeJson(url) + "\"}");
    }

    public void goBack() {
        sendCdp("Runtime.evaluate", "{\"expression\":\"window.history.back()\"}");
    }

    public void goForward() {
        sendCdp("Runtime.evaluate", "{\"expression\":\"window.history.forward()\"}");
    }

    public void reload() {
        sendCdp("Page.reload", "{}");
        unmuteAudio();
    }

    public void unmuteAudio() {
        log("Unmuting main video player and muting previews");
        String js = "(function(){" +
                "document.querySelectorAll('ytd-video-preview video, #preview video, .inline-preview-player video').forEach(function(p){p.muted=true;p.pause();});" +
                "var m = document.querySelector('video.html5-main-video') || document.querySelector('#movie_player video') || document.querySelector('video');" +
                "if(m){m.muted=false;m.volume=1.0;}" +
                "})()";
        sendCdp("Runtime.evaluate", "{\"expression\":\"" + escapeJson(js) + "\"}");
    }

    public void checkInputFocus() {
        String js = "setTimeout(function(){" +
                "var el = document.activeElement;" +
                "var isInput = el && (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.isContentEditable || el.getAttribute('role') === 'textbox' || el.getAttribute('role') === 'combobox');" +
                "console.log('[POLAR_INPUT_FOCUS:' + (!!isInput) + ']');" +
                "}, 60);";
        sendCdp("Runtime.evaluate", "{\"expression\":\"" + escapeJson(js) + "\"}");
    }

    public void blurActiveElement() {
        String js = "if(document.activeElement && document.activeElement.blur){document.activeElement.blur();}";
        sendCdp("Runtime.evaluate", "{\"expression\":\"" + escapeJson(js) + "\"}");
    }

    private int lastSentX = -1;
    private int lastSentY = -1;
    private long lastMouseMoveTime = 0;

    public void mouseMove(int x, int y) {
        long now = System.currentTimeMillis();
        if (now - lastMouseMoveTime < 35) {
            return;
        }
        if (Math.abs(x - lastSentX) < 4 && Math.abs(y - lastSentY) < 4) {
            return;
        }
        lastSentX = x;
        lastSentY = y;
        lastMouseMoveTime = now;
        sendCdp("Input.dispatchMouseEvent", "{\"type\":\"mouseMoved\",\"x\":" + x + ",\"y\":" + y + "}");
    }

    public void click(int x, int y) {
        lastSentX = x;
        lastSentY = y;
        log("Dispatched click at (" + x + ", " + y + ")");
        sendCdp("Input.dispatchMouseEvent", "{\"type\":\"mouseMoved\",\"x\":" + x + ",\"y\":" + y + "}");
        sendCdp("Input.dispatchMouseEvent", "{\"type\":\"mousePressed\",\"x\":" + x + ",\"y\":" + y + ",\"button\":\"left\",\"buttons\":1,\"clickCount\":1}");
        sendCdp("Input.dispatchMouseEvent", "{\"type\":\"mouseReleased\",\"x\":" + x + ",\"y\":" + y + ",\"button\":\"left\",\"buttons\":0,\"clickCount\":1}");
    }

    public void mouseDown(int x, int y, String button) {
        lastSentX = x;
        lastSentY = y;
        int buttons = "right".equalsIgnoreCase(button) ? 2 : 1;
        sendCdp("Input.dispatchMouseEvent", "{\"type\":\"mousePressed\",\"x\":" + x + ",\"y\":" + y + ",\"button\":\"" + button + "\",\"buttons\":" + buttons + ",\"clickCount\":1}");
    }

    public void mouseUp(int x, int y, String button) {
        lastSentX = x;
        lastSentY = y;
        sendCdp("Input.dispatchMouseEvent", "{\"type\":\"mouseReleased\",\"x\":" + x + ",\"y\":" + y + ",\"button\":\"" + button + "\",\"buttons\":0,\"clickCount\":1}");
    }

    public void mouseWheel(int x, int y, int deltaY) {
        sendCdp("Input.dispatchMouseEvent", "{\"type\":\"mouseWheel\",\"x\":" + x + ",\"y\":" + y + ",\"deltaX\":0,\"deltaY\":" + deltaY + "}");
    }

    public void insertText(String text) {
        if (text == null || text.isEmpty()) return;
        log("Inserting text into web page: " + text);
        sendCdp("Input.insertText", "{\"text\":\"" + escapeJson(text) + "\"}");
    }

    public void pressBackspace() {
        sendCdp("Input.dispatchKeyEvent", "{\"type\":\"rawKeyDown\",\"key\":\"Backspace\",\"code\":\"Backspace\",\"windowsVirtualKeyCode\":8}");
        sendCdp("Input.dispatchKeyEvent", "{\"type\":\"keyUp\",\"key\":\"Backspace\",\"code\":\"Backspace\",\"windowsVirtualKeyCode\":8}");
    }

    public void pressEnter() {
        sendCdp("Input.dispatchKeyEvent", "{\"type\":\"rawKeyDown\",\"key\":\"Enter\",\"code\":\"Enter\",\"windowsVirtualKeyCode\":13}");
        sendCdp("Input.dispatchKeyEvent", "{\"type\":\"keyUp\",\"key\":\"Enter\",\"code\":\"Enter\",\"windowsVirtualKeyCode\":13}");
    }

    public void pressDelete() {
        sendCdp("Input.dispatchKeyEvent", "{\"type\":\"rawKeyDown\",\"key\":\"Delete\",\"code\":\"Delete\",\"windowsVirtualKeyCode\":46}");
        sendCdp("Input.dispatchKeyEvent", "{\"type\":\"keyUp\",\"key\":\"Delete\",\"code\":\"Delete\",\"windowsVirtualKeyCode\":46}");
    }

    public void pressTab() {
        sendCdp("Input.dispatchKeyEvent", "{\"type\":\"rawKeyDown\",\"key\":\"Tab\",\"code\":\"Tab\",\"windowsVirtualKeyCode\":9}");
        sendCdp("Input.dispatchKeyEvent", "{\"type\":\"keyUp\",\"key\":\"Tab\",\"code\":\"Tab\",\"windowsVirtualKeyCode\":9}");
    }

    public void pressArrow(String key, String code, int vk) {
        sendCdp("Input.dispatchKeyEvent", "{\"type\":\"rawKeyDown\",\"key\":\"" + key + "\",\"code\":\"" + code + "\",\"windowsVirtualKeyCode\":" + vk + "}");
        sendCdp("Input.dispatchKeyEvent", "{\"type\":\"keyUp\",\"key\":\"" + key + "\",\"code\":\"" + code + "\",\"windowsVirtualKeyCode\":" + vk + "}");
    }

    public void keyPress(String key, String text) {
        if (text != null && !text.isEmpty()) {
            sendCdp("Input.dispatchKeyEvent", "{\"type\":\"keyDown\",\"text\":\"" + escapeJson(text) + "\",\"unmodifiedText\":\"" + escapeJson(text) + "\",\"key\":\"" + escapeJson(key) + "\"}");
            sendCdp("Input.dispatchKeyEvent", "{\"type\":\"keyUp\",\"key\":\"" + escapeJson(key) + "\"}");
        } else {
            sendCdp("Input.dispatchKeyEvent", "{\"type\":\"keyDown\",\"key\":\"" + escapeJson(key) + "\"}");
            sendCdp("Input.dispatchKeyEvent", "{\"type\":\"keyUp\",\"key\":\"" + escapeJson(key) + "\"}");
        }
    }

    public void sendCdp(String method, String paramsJson) {
        if (webSocket == null || !connected.get()) return;
        int id = msgId.incrementAndGet();
        String payload = "{\"id\":" + id + ",\"method\":\"" + method + "\",\"params\":" + paramsJson + "}";
        sendQueue.offer(payload);
        drainSendQueue();
    }

    private void drainSendQueue() {
        if (webSocket == null || !connected.get()) return;
        if (isSending.compareAndSet(false, true)) {
            String msg = sendQueue.poll();
            if (msg == null) {
                isSending.set(false);
                return;
            }
            try {
                webSocket.sendText(msg, true).whenComplete((ws, error) -> {
                    if (error != null) {
                        log("sendText error: " + error.getMessage());
                    }
                    isSending.set(false);
                    drainSendQueue();
                });
            } catch (Exception e) {
                log("sendText exception: " + e.getMessage());
                isSending.set(false);
            }
        }
    }

    public synchronized void stop() {
        running.set(false);
        connected.set(false);
        hasTexture.set(false);

        if (webSocket != null) {
            try {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Module disabled");
            } catch (Exception ignored) {
            }
            webSocket = null;
        }

        if (browserProcess != null && browserProcess.isAlive()) {
            try {
                long pid = browserProcess.pid();
                new ProcessBuilder("taskkill", "/F", "/T", "/PID", String.valueOf(pid)).start().waitFor();
            } catch (Exception ignored) {
            }
            try {
                browserProcess.destroyForcibly();
            } catch (Exception ignored) {
            }
            browserProcess = null;
        }

        sendQueue.clear();
        isSending.set(false);

        if (decodeExecutor != null) {
            try {
                decodeExecutor.shutdownNow();
            } catch (Exception ignored) {
            }
            decodeExecutor = null;
        }
        isDecoding.set(false);

        NativeImage img = pendingImage.getAndSet(null);
        if (img != null) {
            img.close();
        }
    }

    private static String findBrowserExecutable(String preferred) {
        String localApp = System.getenv("LOCALAPPDATA");
        String[] edgePaths = {
                "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
                "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe",
                "C:\\Program Files (x86)\\Microsoft\\EdgeWebView\\Application\\152.0.4191.66\\msedgewebview2.exe",
                "C:\\Program Files (x86)\\Microsoft\\EdgeWebView\\Application\\152.0.4191.62\\msedgewebview2.exe"
        };
        String[] bravePaths = {
                "C:\\Program Files\\BraveSoftware\\Brave-Browser\\Application\\brave.exe",
                "C:\\Program Files (x86)\\BraveSoftware\\Brave-Browser\\Application\\brave.exe",
                (localApp != null ? localApp + "\\BraveSoftware\\Brave-Browser\\Application\\brave.exe" : "")
        };
        String[] chromePaths = {
                "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
                "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe"
        };

        java.util.List<String> list = new java.util.ArrayList<>();
        if ("Brave".equalsIgnoreCase(preferred)) {
            java.util.Collections.addAll(list, bravePaths);
            java.util.Collections.addAll(list, edgePaths);
            java.util.Collections.addAll(list, chromePaths);
        } else {
            java.util.Collections.addAll(list, edgePaths);
            java.util.Collections.addAll(list, bravePaths);
            java.util.Collections.addAll(list, chromePaths);
        }

        for (String p : list) {
            if (p.isEmpty()) continue;
            File f = new File(p);
            if (f.exists() && f.canExecute()) {
                return p;
            }
        }

        return null;
    }

    private static int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            return 9222;
        }
    }

    private static String extractTargetWebSocketUrl(String json) {
        int searchFrom = 0;
        String fallbackWs = null;

        while (true) {
            int objStart = json.indexOf('{', searchFrom);
            if (objStart == -1) break;
            int objEnd = json.indexOf('}', objStart);
            if (objEnd == -1) break;
            searchFrom = objEnd + 1;

            String obj = json.substring(objStart, objEnd + 1);
            if (obj.contains("\"type\": \"page\"") || obj.contains("\"type\":\"page\"")) {
                int wsIdx = obj.indexOf("\"webSocketDebuggerUrl\":");
                if (wsIdx != -1) {
                    int start = obj.indexOf('"', wsIdx + 23) + 1;
                    int end = obj.indexOf('"', start);
                    if (start > 0 && end > start) {
                        String wsUrl = obj.substring(start, end);
                        // Проверяем, что это не системный диалог Edge или расширение
                        if (!obj.contains("edge://") && !obj.contains("chrome-extension://")) {
                            return wsUrl; // Отличная вкладка
                        }
                        if (fallbackWs == null) {
                            fallbackWs = wsUrl;
                        }
                    }
                }
            }
        }

        return fallbackWs;
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
