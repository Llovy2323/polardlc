package snill.client.api.utils.media;

import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import snill.client.api.QClient;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MediaTracker implements QClient {

    private static final MediaTracker INSTANCE = new MediaTracker();
    public static final Identifier MUSIC_COVER_TEXTURE = Identifier.of("snill", "music_cover");

    private final AtomicReference<MediaTrack> currentTrack = new AtomicReference<>(null);
    private final ConcurrentHashMap<String, String> coverUrlCache = new ConcurrentHashMap<>();

    private volatile Process gsmtcProcess;
    private volatile boolean running = false;
    private String lastCoverKey = "";
    private long lastCoverCheckTime = 0;

    // 16 полос эквалайзера + пиковые точки
    public static final int BARS_COUNT = 16;
    private final float[] visualizerBars = new float[BARS_COUNT];
    private final float[] targetBars = new float[BARS_COUNT];
    private final float[] peakBars = new float[BARS_COUNT];
    private final float[] peakVelocities = new float[BARS_COUNT];

    private MediaTracker() {
        for (int i = 0; i < BARS_COUNT; i++) {
            visualizerBars[i] = 0.12f;
            targetBars[i] = 0.12f;
            peakBars[i] = 0.12f;
            peakVelocities[i] = 0.0f;
        }
    }

    public static MediaTracker getInstance() {
        return INSTANCE;
    }

    public synchronized void start() {
        if (running) return;
        running = true;

        Thread trackerThread = new Thread(this::runTrackerLoop, "Polar-MediaTracker");
        trackerThread.setDaemon(true);
        trackerThread.setPriority(Thread.MIN_PRIORITY);
        trackerThread.start();

        Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "Polar-MediaTracker-Shutdown"));
    }

    public synchronized void stop() {
        running = false;
        if (gsmtcProcess != null) {
            try {
                gsmtcProcess.destroyForcibly();
            } catch (Exception ignored) {
            }
            gsmtcProcess = null;
        }
    }

    public MediaTrack getCurrentTrack() {
        return currentTrack.get();
    }

    public float[] getVisualizerBars() {
        return visualizerBars;
    }

    public float[] getPeakBars() {
        return peakBars;
    }

    public void updateVisualizer() {
        MediaTrack track = currentTrack.get();
        boolean active = track != null && track.isPlaying();
        long time = System.currentTimeMillis();

        for (int i = 0; i < BARS_COUNT; i++) {
            if (active) {
                // 1. Ритмичный бас-бит (силен на левых 0-5 полосах)
                double beatFreq = (time * 0.007);
                double beatPulse = Math.pow(Math.max(0.0, Math.sin(beatFreq)), 2.2);
                double bassFactor = Math.max(0.0, 1.0 - (i / 6.0)) * beatPulse * 0.50;

                // 2. Мелодическая волна в середине
                double midWave = (Math.sin((time * 0.009) + (i * 0.52)) * 0.25) + 0.25;

                // 3. Быстрый мерцающий хай-хэт/верха справа
                double trebleShake = (Math.cos((time * 0.015) + (i * 0.78)) * 0.20) + 0.20;
                double trebleFactor = (i / 15.0) * trebleShake;

                // 4. Базовый спектральный профиль (натуральный колокол, гарантирует отсутствие пустых полос)
                double baseCurve = 0.32 + (0.32 * Math.sin((i / (double) BARS_COUNT) * Math.PI));

                double rawHeight = baseCurve + bassFactor + (midWave * 0.25) + (trebleFactor * 0.35);
                float height = (float) Math.max(0.24, Math.min(1.0, rawHeight));
                targetBars[i] = height;
            } else {
                targetBars[i] = 0.08f;
            }

            // Плавное движение столбиков
            visualizerBars[i] += (targetBars[i] - visualizerBars[i]) * 0.28f;

            // Пиковые точки (плавают наверху со свободным падением)
            if (visualizerBars[i] >= peakBars[i]) {
                peakBars[i] = visualizerBars[i];
                peakVelocities[i] = 0.0f;
            } else {
                peakVelocities[i] += 0.010f;
                peakBars[i] = Math.max(visualizerBars[i], peakBars[i] - peakVelocities[i]);
            }
        }
    }

    private volatile long lastGsmtcTime = 0;

    private void runTrackerLoop() {
        startGsmtcProcess();

        while (running) {
            try {
                // Если процесс упал, перезапускаем его
                if (gsmtcProcess == null || !gsmtcProcess.isAlive()) {
                    startGsmtcProcess();
                }

                // Сканируем окна ТОЛЬКО если GSMTC не передавал данных более 5 секунд
                if (System.currentTimeMillis() - lastGsmtcTime > 5000) {
                    scanWindowsForMedia();
                }

                MediaTrack active = currentTrack.get();
                if (active != null && !active.getRawTitle().isEmpty() && !active.hasCover()) {
                    String key = active.getArtist() + " - " + active.getRawTitle();
                    long now = System.currentTimeMillis();
                    if (!key.equals(lastCoverKey) || now - lastCoverCheckTime > 4000) {
                        lastCoverKey = key;
                        lastCoverCheckTime = now;
                        fetchCoverOnlineAsync(active);
                    }
                }

                Thread.sleep(500);
            } catch (InterruptedException ignored) {
                break;
            } catch (Exception e) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignored) {
                    break;
                }
            }
        }
    }

    private void startGsmtcProcess() {
        try {
            if (gsmtcProcess != null && gsmtcProcess.isAlive()) {
                gsmtcProcess.destroyForcibly();
            }

            File scriptFile = new File(System.getProperty("java.io.tmpdir"), "polar_gsmtc_daemon.ps1");
            String script =
                    "[Console]::OutputEncoding = [System.Text.Encoding]::UTF8\n" +
                    "Add-Type -AssemblyName System.Runtime.WindowsRuntime\n" +
                    "[Windows.Storage.Streams.IRandomAccessStream, Windows.Storage.Streams, ContentType = WindowsRuntime] | Out-Null\n" +
                    "[Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage.Streams, ContentType = WindowsRuntime] | Out-Null\n" +
                    "[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media, ContentType = WindowsRuntime] | Out-Null\n" +
                    "$asTaskGeneric = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' }[0]\n" +
                    "$asStream = [System.IO.WindowsRuntimeStreamExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsStream' -and $_.GetParameters().Count -eq 1 } | Select-Object -First 1\n" +
                    "Function Await($t, $type) {\n" +
                    "    if ($t -eq $null) { return $null }\n" +
                    "    try {\n" +
                    "        $netTask = $asTaskGeneric.MakeGenericMethod($type).Invoke($null, @($t))\n" +
                    "        if ($netTask.Wait(1200)) { return $netTask.Result }\n" +
                    "    } catch {}\n" +
                    "    return $null\n" +
                    "}\n" +
                    "$manager = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])\n" +
                    "$tempBase = \"$env:TEMP\\polar_cover\"\n" +
                    "$coverIdx = 0\n" +
                    "$lastCoverTrack = ''\n" +
                    "$lastSavedFile = ''\n" +
                    "while ($true) {\n" +
                    "    try {\n" +
                    "        if ($manager -eq $null) {\n" +
                    "            $manager = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])\n" +
                    "        }\n" +
                    "        $sessions = if ($manager -ne $null) { $manager.GetSessions() } else { @() }\n" +
                    "        $session = $null\n" +
                    "        if ($sessions -ne $null) {\n" +
                    "            foreach ($s in $sessions) {\n" +
                    "                try {\n" +
                    "                    $nfo = $s.GetPlaybackInfo()\n" +
                    "                    if ($nfo -ne $null -and $nfo.PlaybackStatus.ToString() -eq 'Playing') {\n" +
                    "                        $session = $s\n" +
                    "                        break\n" +
                    "                    }\n" +
                    "                } catch {}\n" +
                    "            }\n" +
                    "        }\n" +
                    "        if ($session -eq $null -and $manager -ne $null) {\n" +
                    "            try { $session = $manager.GetCurrentSession() } catch {}\n" +
                    "        }\n" +
                    "        if ($session -eq $null -and $sessions -ne $null -and $sessions.Count -gt 0) {\n" +
                    "            $session = $sessions[0]\n" +
                    "        }\n" +
                    "        if ($session -ne $null) {\n" +
                    "            $props = Await ($session.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])\n" +
                    "            $info = $session.GetPlaybackInfo()\n" +
                    "            $tl = $session.GetTimelineProperties()\n" +
                    "            $title = if ($props -ne $null -and $props.Title) { $props.Title } else { '' }\n" +
                    "            $artist = if ($props -ne $null -and $props.Artist) { $props.Artist } else { '' }\n" +
                    "            $album = if ($props -ne $null -and $props.AlbumTitle) { $props.AlbumTitle } else { '' }\n" +
                    "            $status = if ($info -ne $null) { $info.PlaybackStatus.ToString() } else { 'Unknown' }\n" +
                    "            $pos = if ($tl -ne $null) { [int]$tl.Position.TotalMilliseconds } else { 0 }\n" +
                    "            $dur = if ($tl -ne $null) { [int]$tl.EndTime.TotalMilliseconds } else { 0 }\n" +
                    "            $hasThumb = 0\n" +
                    "            $coverFile = ''\n" +
                    "            if ($props -ne $null -and $props.Thumbnail -ne $null -and $asStream -ne $null) {\n" +
                    "                $curKey = \"$title - $artist\"\n" +
                    "                if ($curKey -ne $lastCoverTrack -or -not (Test-Path $lastSavedFile)) {\n" +
                    "                    try {\n" +
                    "                        $st = Await ($props.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])\n" +
                    "                        if ($st -ne $null) {\n" +
                    "                            $nst = $asStream.Invoke($null, @($st))\n" +
                    "                            if ($nst -ne $null) {\n" +
                    "                                $coverFile = \"$tempBase\" + \"_$coverIdx.png\"\n" +
                    "                                $coverIdx = ($coverIdx + 1) % 4\n" +
                    "                                $fs = [System.IO.File]::Create($coverFile)\n" +
                    "                                $nst.CopyTo($fs)\n" +
                    "                                $fs.Dispose()\n" +
                    "                                $nst.Dispose()\n" +
                    "                                $hasThumb = 1\n" +
                    "                                $lastCoverTrack = $curKey\n" +
                    "                                $lastSavedFile = $coverFile\n" +
                    "                            }\n" +
                    "                        }\n" +
                    "                    } catch {}\n" +
                    "                } else {\n" +
                    "                    $hasThumb = 1\n" +
                    "                    $coverFile = $lastSavedFile\n" +
                    "                }\n" +
                    "            }\n" +
                    "            $bTitle = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($title))\n" +
                    "            $bArtist = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($artist))\n" +
                    "            $bAlbum = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($album))\n" +
                    "            $bCover = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($coverFile))\n" +
                    "            [Console]::WriteLine(\"GSMTC64|{0}|{1}|{2}|{3}|{4}|{5}|{6}|{7}\", $bTitle, $bArtist, $bAlbum, $status, $pos, $dur, $hasThumb, $bCover)\n" +
                    "            [Console]::Out.Flush()\n" +
                    "        } else {\n" +
                    "            [Console]::WriteLine(\"GSMTC_IDLE\")\n" +
                    "            [Console]::Out.Flush()\n" +
                    "        }\n" +
                    "    } catch {\n" +
                    "        [Console]::WriteLine(\"GSMTC_IDLE\")\n" +
                    "        [Console]::Out.Flush()\n" +
                    "    }\n" +
                    "    Start-Sleep -Milliseconds 300\n" +
                    "}\n";

            try (FileOutputStream fos = new FileOutputStream(scriptFile);
                 OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                osw.write(script);
            }

            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-ExecutionPolicy", "Bypass",
                    "-File", scriptFile.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            gsmtcProcess = pb.start();

            Thread reader = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(gsmtcProcess.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while (running && (line = br.readLine()) != null) {
                        handleGsmtcLine(line.trim());
                    }
                } catch (Exception ignored) {
                }
            }, "Polar-GSMTC-Reader");
            reader.setDaemon(true);
            reader.start();
        } catch (Exception ignored) {
        }
    }

    private void handleGsmtcLine(String line) {
        if (line.startsWith("GSMTC_IDLE")) {
            lastGsmtcTime = System.currentTimeMillis();
            MediaTrack old = currentTrack.get();
            if (old != null && old.isPlaying()) {
                currentTrack.set(new MediaTrack(old.getRawTitle(), old.getArtist(), old.getAlbum(), false, old.getPositionMs(), old.getDurationMs()));
            }
            return;
        }

        if (line.startsWith("GSMTC64|")) {
            lastGsmtcTime = System.currentTimeMillis();
            String[] parts = line.split("\\|", -1);
            if (parts.length >= 9) {
                String title = decodeB64(parts[1]);
                String artist = decodeB64(parts[2]);
                String album = decodeB64(parts[3]);
                String status = parts[4].trim();
                long pos = 0;
                long dur = 0;
                try {
                    pos = Long.parseLong(parts[5].trim());
                    dur = Long.parseLong(parts[6].trim());
                } catch (NumberFormatException ignored) {
                }
                boolean hasThumb = "1".equals(parts[7].trim());
                String thumbPath = decodeB64(parts[8]);

                boolean playing = "Playing".equalsIgnoreCase(status);

                if (!title.isEmpty()) {
                    MediaTrack old = currentTrack.get();
                    boolean trackChanged = old == null || !old.getRawTitle().equalsIgnoreCase(title) || !old.getArtist().equalsIgnoreCase(artist);

                    MediaTrack track = new MediaTrack(title, artist, album, playing, pos, dur);

                    if (!trackChanged && old.hasCover()) {
                        track.setCoverTexture(old.getCoverTexture());
                    }

                    if (hasThumb && !thumbPath.isEmpty() && (!track.hasCover() || trackChanged)) {
                        File file = new File(thumbPath);
                        if (file.exists() && file.length() > 0) {
                            loadCoverFromFile(file, track);
                        }
                    } else if (trackChanged && !hasThumb) {
                        fetchCoverOnlineAsync(track);
                    }

                    currentTrack.set(track);
                }
            }
        }
    }

    private static String decodeB64(String str) {
        if (str == null || str.isEmpty()) return "";
        try {
            return new String(Base64.getDecoder().decode(str), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return "";
        }
    }

    private void scanWindowsForMedia() {
        try {
            char[] titleBuffer = new char[512];
            int[] currentPid = new int[1];

            WindowsMediaUser32.INSTANCE.EnumWindows((hWnd, arg) -> {
                if (!WindowsMediaUser32.INSTANCE.IsWindowVisible(hWnd)) return true;

                int len = WindowsMediaUser32.INSTANCE.GetWindowTextW(hWnd, titleBuffer, titleBuffer.length);
                if (len <= 3) return true;

                String windowTitle = new String(titleBuffer, 0, len).trim();
                WindowsMediaUser32.INSTANCE.GetWindowThreadProcessId(hWnd, currentPid);

                ParsedMedia parsed = parseTitle(windowTitle);
                if (parsed != null && !parsed.title.isEmpty()) {
                    MediaTrack track = new MediaTrack(parsed.title, parsed.artist, "", true, 0, 0);
                    currentTrack.set(track);
                    return false;
                }

                return true;
            }, null);
        } catch (Throwable ignored) {
        }
    }

    private static class ParsedMedia {
        final String title;
        final String artist;

        ParsedMedia(String title, String artist) {
            this.title = title;
            this.artist = artist;
        }
    }

    private ParsedMedia parseTitle(String raw) {
        if (raw == null || raw.isEmpty()) return null;

        String lower = raw.toLowerCase(Locale.ROOT);

        // Игнорируем служебные окна и IDE
        if (raw.startsWith("Minecraft") || raw.contains("Antigravity") || raw.contains("Visual Studio") || raw.contains("IntelliJ")) {
            return null;
        }

        // Фильтруем стримы, мессенджеры, поисковые системы, пустые вкладки и стартовые страницы
        if (lower.contains("twitch") || lower.contains("kick.com")
                || lower.contains("discord") || lower.contains("telegram")
                || lower.contains("obs ") || lower.contains("steam")
                || lower.contains("search") || lower.contains("поиск")
                || lower.contains("new tab") || lower.contains("новая вкладка")
                || lower.contains("discover") || lower.contains("feed")
                || lower.contains("explore") || lower.contains("trending")
                || lower.contains("playlists and artists") || lower.contains("popular tracks")
                || lower.contains("settings") || lower.contains("настройки")
                || lower.contains("downloads") || lower.contains("загрузки")
                || lower.equals("soundcloud") || lower.equals("youtube")
                || lower.equals("яндекс музыка") || lower.equals("spotify")) {
            return null;
        }

        String cleaned = raw;

        // Удаление суффиксов браузеров
        cleaned = cleaned.replaceAll("(?i)\\s*[-—]\\s*(?:Google Chrome|Chromium|Microsoft​ Edge|Microsoft Edge|Mozilla Firefox|Yandex|Яндекс|Opera(?:\\s*GX)?|Brave|Vivaldi)$", "");

        // Очистка уведомлений типа (1) в начале
        cleaned = cleaned.replaceAll("^\\([0-9]+\\)\\s*", "").trim();

        // 1. SoundCloud
        if (lower.contains("soundcloud")) {
            cleaned = cleaned.replaceAll("(?i)\\s*\\|\\s*Listen online for free on SoundCloud", "");
            cleaned = cleaned.replaceAll("(?i)\\s*\\|\\s*SoundCloud", "");
            cleaned = cleaned.replaceAll("(?i)\\s*on\\s*SoundCloud", "");
            cleaned = cleaned.replaceAll("(?i)\\s*[-—]\\s*SoundCloud", "");
            if (cleaned.startsWith("Stream ") || cleaned.startsWith("stream ")) {
                cleaned = cleaned.substring(7).trim();
            }
            if (cleaned.contains(" by ")) {
                String[] parts = cleaned.split(" by ", 2);
                return new ParsedMedia(cleanTrack(parts[0]), cleanArtist(parts[1]));
            } else if (cleaned.contains(" - ")) {
                String[] parts = cleaned.split(" - ", 2);
                return new ParsedMedia(cleanTrack(parts[1]), cleanArtist(parts[0]));
            } else if (cleaned.contains(" — ")) {
                String[] parts = cleaned.split(" — ", 2);
                return new ParsedMedia(cleanTrack(parts[1]), cleanArtist(parts[0]));
            }
            return null;
        }

        // 2. YouTube & YouTube Music
        if (lower.contains("youtube")) {
            cleaned = cleaned.replaceAll("(?i)\\s*[-—]\\s*YouTube(?:\\s*Music)?", "").trim();
            if (cleaned.contains(" - ")) {
                String[] parts = cleaned.split(" - ", 2);
                return new ParsedMedia(cleanTrack(parts[1]), cleanArtist(parts[0]));
            } else if (cleaned.contains(" — ")) {
                String[] parts = cleaned.split(" — ", 2);
                return new ParsedMedia(cleanTrack(parts[1]), cleanArtist(parts[0]));
            }
            return null;
        }

        // 3. Яндекс Музыка
        if (lower.contains("яндекс музыка") || lower.contains("яндекс.музыка")) {
            cleaned = cleaned.replaceAll("(?i)\\s*[-—]\\s*Яндекс\\.?Музыка", "").trim();
            if (cleaned.contains(" — ")) {
                String[] parts = cleaned.split(" — ", 2);
                return new ParsedMedia(cleanTrack(parts[0]), cleanArtist(parts[1]));
            } else if (cleaned.contains(" - ")) {
                String[] parts = cleaned.split(" - ", 2);
                return new ParsedMedia(cleanTrack(parts[0]), cleanArtist(parts[1]));
            }
        }

        // 4. ВКонтакте (VK)
        if (lower.contains("вконтакте") || lower.contains("vk.com") || lower.contains("| vk")) {
            cleaned = cleaned.replaceAll("(?i)\\s*\\|\\s*(?:ВКонтакте|VK)", "").trim();
            if (cleaned.contains(" - ")) {
                String[] parts = cleaned.split(" - ", 2);
                return new ParsedMedia(cleanTrack(parts[1]), cleanArtist(parts[0]));
            }
        }

        // 5. Spotify
        if (lower.contains("spotify")) {
            cleaned = cleaned.replaceAll("(?i)\\s*[-—]\\s*Spotify", "").trim();
            if (cleaned.equals("Spotify Free") || cleaned.equals("Spotify Premium")) {
                return null;
            }
            if (cleaned.contains(" • ")) {
                String[] parts = cleaned.split(" • ", 2);
                return new ParsedMedia(cleanTrack(parts[0]), cleanArtist(parts[1]));
            } else if (cleaned.contains(" - ")) {
                String[] parts = cleaned.split(" - ", 2);
                return new ParsedMedia(cleanTrack(parts[1]), cleanArtist(parts[0]));
            }
        }

        return null;
    }

    private String cleanTrack(String track) {
        if (track == null) return "";
        String s = track.trim();
        s = s.replaceAll("(?i)#(?:fyp|shorts|viral|trending|pov|music|tiktok|remix|edit)\\b", "");
        s = s.replaceAll("(?i)\\s*\\((?:official\\s+(?:video|music\\s+video|audio|lyric\\s+video)|lyrics?|clip|audio|hd|4k)\\)", "");
        s = s.replaceAll("(?i)\\s*\\[(?:official\\s+(?:video|music\\s+video|audio|lyric\\s+video)|lyrics?|clip|audio|hd|4k)\\]", "");
        s = s.replaceAll("^[\"']+|[\"']+$", "").trim();
        return s;
    }

    private String cleanArtist(String artist) {
        if (artist == null) return "Unknown";
        String s = artist.trim();
        s = s.replaceAll("(?i)\\s*[-—]\\s*SoundCloud.*", "");
        s = s.replaceAll("(?i)\\s*[-—]\\s*YouTube.*", "");
        return s.isEmpty() ? "Unknown" : s;
    }

    private void loadCoverFromFile(File file, MediaTrack track) {
        try (FileInputStream fis = new FileInputStream(file)) {
            NativeImage image = NativeImage.read(fis);
            if (image == null) return;

            if (mc == null) {
                image.close();
                return;
            }

            mc.execute(() -> {
                try {
                    Identifier id = Identifier.of("polar", "cover_" + (System.currentTimeMillis() % 1000000));
                    mc.getTextureManager().registerTexture(id, new NativeImageBackedTexture(image));
                    track.setCoverTexture(id);
                    MediaTrack curr = currentTrack.get();
                    if (curr != null && curr.getRawTitle().equalsIgnoreCase(track.getRawTitle())) {
                        curr.setCoverTexture(id);
                    }
                } catch (Exception e) {
                    image.close();
                }
            });
        } catch (Exception ignored) {
        }
    }

    private void fetchCoverOnlineAsync(MediaTrack track) {
        new Thread(() -> {
            try {
                String query = track.getArtist() + " " + track.getRawTitle();
                String cachedUrl = coverUrlCache.get(query);
                String artworkUrl = cachedUrl;

                // 1. Проверяем iTunes Search API
                if (artworkUrl == null) {
                    try {
                        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
                        URL url = URI.create("https://itunes.apple.com/search?term=" + encoded + "&entity=song&limit=1").toURL();
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setConnectTimeout(3000);
                        conn.setReadTimeout(3000);
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0");

                        StringBuilder sb = new StringBuilder();
                        try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = in.readLine()) != null) sb.append(line);
                        }

                        Pattern p = Pattern.compile("\"artworkUrl100\"\\s*:\\s*\"(https:[^\"]+)\"");
                        Matcher m = p.matcher(sb.toString());
                        if (m.find()) {
                            artworkUrl = m.group(1).replace("100x100bb", "256x256bb");
                            coverUrlCache.put(query, artworkUrl);
                        }
                    } catch (Exception ignored) {
                    }
                }

                // 2. Если iTunes не нашел (SoundCloud, русские треки, фонк) — проверяем Deezer Search API
                if (artworkUrl == null) {
                    try {
                        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
                        URL url = URI.create("https://api.deezer.com/search?q=" + encoded + "&limit=1").toURL();
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setConnectTimeout(3000);
                        conn.setReadTimeout(3000);
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0");

                        StringBuilder sb = new StringBuilder();
                        try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = in.readLine()) != null) sb.append(line);
                        }

                        Pattern p = Pattern.compile("\"cover_medium\"\\s*:\\s*\"(https:[^\"]+)\"");
                        Matcher m = p.matcher(sb.toString());
                        if (m.find()) {
                            artworkUrl = m.group(1).replace("\\/", "/");
                            coverUrlCache.put(query, artworkUrl);
                        }
                    } catch (Exception ignored) {
                    }
                }

                // 3. Если по связке артист+название не нашлось, ищем просто по названию песни в Deezer
                if (artworkUrl == null && !track.getRawTitle().isEmpty()) {
                    try {
                        String encoded = URLEncoder.encode(track.getRawTitle(), StandardCharsets.UTF_8);
                        URL url = URI.create("https://api.deezer.com/search?q=" + encoded + "&limit=1").toURL();
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setConnectTimeout(3000);
                        conn.setReadTimeout(3000);
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0");

                        StringBuilder sb = new StringBuilder();
                        try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = in.readLine()) != null) sb.append(line);
                        }

                        Pattern p = Pattern.compile("\"cover_medium\"\\s*:\\s*\"(https:[^\"]+)\"");
                        Matcher m = p.matcher(sb.toString());
                        if (m.find()) {
                            artworkUrl = m.group(1).replace("\\/", "/");
                            coverUrlCache.put(query, artworkUrl);
                        }
                    } catch (Exception ignored) {
                    }
                }

                // Загружаем и регистрируем текстуру
                if (artworkUrl != null) {
                    URL imgUrl = URI.create(artworkUrl).toURL();
                    HttpURLConnection imgConn = (HttpURLConnection) imgUrl.openConnection();
                    imgConn.setConnectTimeout(4000);
                    imgConn.setReadTimeout(4000);
                    imgConn.setRequestProperty("User-Agent", "Mozilla/5.0");

                    try (InputStream is = imgConn.getInputStream()) {
                        NativeImage image = NativeImage.read(is);
                        if (image != null && mc != null) {
                            mc.execute(() -> {
                                try {
                                    Identifier id = Identifier.of("polar", "cover_" + (System.currentTimeMillis() % 1000000));
                                    mc.getTextureManager().registerTexture(id, new NativeImageBackedTexture(image));
                                    track.setCoverTexture(id);
                                    MediaTrack curr = currentTrack.get();
                                    if (curr != null && curr.getRawTitle().equalsIgnoreCase(track.getRawTitle())) {
                                        curr.setCoverTexture(id);
                                    }
                                } catch (Exception e) {
                                    image.close();
                                }
                            });
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }, "Polar-Cover-Fetcher").start();
    }

    public void togglePlayPause() {
        sendMediaKey((byte) 0xB3); // VK_MEDIA_PLAY_PAUSE
        MediaTrack track = currentTrack.get();
        if (track != null) {
            currentTrack.set(new MediaTrack(track.getRawTitle(), track.getArtist(), track.getAlbum(), !track.isPlaying(), track.getPositionMs(), track.getDurationMs()));
        }
    }

    public void nextTrack() {
        sendMediaKey((byte) 0xB0); // VK_MEDIA_NEXT_TRACK
    }

    public void prevTrack() {
        sendMediaKey((byte) 0xB1); // VK_MEDIA_PREV_TRACK
    }

    private void sendMediaKey(byte vkCode) {
        try {
            WindowsMediaUser32.INSTANCE.keybd_event(vkCode, (byte) 0, 0, 0);
            WindowsMediaUser32.INSTANCE.keybd_event(vkCode, (byte) 0, 2, 0); // KEYEVENTF_KEYUP = 2
        } catch (Throwable ignored) {
        }
    }
}
