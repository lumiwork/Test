package de.lumiwork.silentnetguard.verify;

import de.lumiwork.silentnetguard.scan.Category;
import de.lumiwork.silentnetguard.scan.Origin;
import de.lumiwork.silentnetguard.scan.ScanResult;
import de.lumiwork.silentnetguard.util.MiniJson;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * InjectedMod Assist - Herkunftsprüfung.
 * <ul>
 *   <li>Mods: SHA-512 wird bei Modrinth nachgeschlagen. Treffer = unveränderte Original-Datei.</li>
 *   <li>Gibt es die Mod-ID auf Modrinth, die Datei aber nicht, ist die Datei <b>nicht original</b>
 *       (verändert/infiziert, oder ein inoffizieller Build).</li>
 *   <li>Eingebettete Libraries: zusaetzlich SHA-1 bei Maven Central.</li>
 * </ul>
 * Positive Ergebnisse werden zwischengespeichert, damit nicht bei jedem Start alles neu abgefragt wird.
 */
public final class OriginVerifier {

    public static final int MODIFIED_POINTS = 25;

    private static final String MODRINTH = System.getProperty("silentnetguard.modrinth", "https://api.modrinth.com");
    private static final String MAVEN = System.getProperty("silentnetguard.maven", "https://search.maven.org");
    private static final String USER_AGENT = "lumiwork/silentnet-guard (https://github.com/lumiwork/Test)";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final Path cacheFile;
    private final Properties cache = new Properties();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    /** @param cacheFile Cache-Datei oder {@code null} für keinen Cache */
    public OriginVerifier(Path cacheFile) {
        this.cacheFile = cacheFile;
        if (cacheFile != null && Files.exists(cacheFile)) {
            try (Reader r = Files.newBufferedReader(cacheFile)) {
                cache.load(r);
            } catch (IOException ignored) {
                // kaputter Cache - neu aufbauen
            }
        }
    }

    /** Prüft alle Ergebnisse (inkl. eingebetteter). Liefert false, wenn die Online-Prüfung nicht möglich war. */
    public boolean verify(List<ScanResult> topLevel) {
        List<ScanResult> all = new ArrayList<>();
        topLevel.forEach(r -> flatten(r, all));

        List<ScanResult> open = new ArrayList<>();
        for (ScanResult r : all) {
            if (r.allowlisted) {
                r.origin = new Origin(Origin.Status.TRUSTED, "Von dir als vertrauenswürdig markiert");
                continue;
            }
            String cached = cache.getProperty(r.sha512);
            if (cached != null) {
                String[] parts = cached.split("\t", 2);
                r.origin = new Origin(Origin.Status.valueOf(parts[0]), parts.length > 1 ? parts[1] : "");
            } else {
                open.add(r);
            }
        }
        if (open.isEmpty()) {
            return true;
        }

        boolean online = true;
        try {
            modrinthByHash(open);
            modrinthBySlug(open.stream().filter(r -> !r.nested && r.origin.status() == Origin.Status.NOT_CHECKED).toList());
        } catch (IOException | RuntimeException e) {
            online = false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            online = false;
        }
        if (online) {
            mavenCentral(open.stream().filter(r -> r.nested && r.origin.status() == Origin.Status.NOT_CHECKED).toList());
        }

        for (ScanResult r : open) {
            if (r.origin.status() == Origin.Status.NOT_CHECKED) {
                r.origin = online ? new Origin(Origin.Status.UNKNOWN, r.nested
                        ? "Weder auf Modrinth noch auf Maven Central bekannt"
                        : "Nicht auf Modrinth (evtl. CurseForge, GitHub oder selbst gebaut)")
                        : new Origin(Origin.Status.OFFLINE, "Modrinth nicht erreichbar");
            }
            if (r.origin.status() == Origin.Status.ORIGINAL || r.origin.status() == Origin.Status.KNOWN_LIBRARY) {
                cache.setProperty(r.sha512, r.origin.status().name() + "\t" + r.origin.detail());
            }
        }
        saveCache();
        return online;
    }

    private void modrinthByHash(List<ScanResult> open) throws IOException, InterruptedException {
        String body = "{\"hashes\":[" + open.stream().map(r -> MiniJson.quote(r.sha512)).collect(Collectors.joining(","))
                + "],\"algorithm\":\"sha512\"}";
        Map<String, Object> versions = MiniJson.obj(MiniJson.parse(send(HttpRequest.newBuilder(URI.create(MODRINTH + "/v2/version_files"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)))));
        if (versions.isEmpty()) {
            return;
        }
        Set<String> projectIds = new LinkedHashSet<>();
        versions.values().forEach(v -> projectIds.add(MiniJson.str(MiniJson.obj(v).get("project_id"))));
        Map<String, String> titles = projectTitles(projectIds);
        for (ScanResult r : open) {
            Map<String, Object> v = MiniJson.obj(versions.get(r.sha512));
            if (!v.isEmpty()) {
                String pid = MiniJson.str(v.get("project_id"));
                r.origin = new Origin(Origin.Status.ORIGINAL, "Modrinth: " + titles.getOrDefault(pid, pid) + " "
                        + MiniJson.str(v.get("version_number")));
            }
        }
    }

    private void modrinthBySlug(List<ScanResult> unknown) throws IOException, InterruptedException {
        Set<String> ids = new LinkedHashSet<>();
        for (ScanResult r : unknown) {
            if (r.modId != null && r.modId.matches("[A-Za-z0-9_.-]{2,64}")) {
                ids.add(r.modId);
            }
        }
        if (ids.isEmpty()) {
            return;
        }
        Map<String, String> bySlug = new HashMap<>();
        String q = "[" + ids.stream().map(MiniJson::quote).collect(Collectors.joining(",")) + "]";
        for (Object p : MiniJson.arr(MiniJson.parse(send(HttpRequest.newBuilder(
                URI.create(MODRINTH + "/v2/projects?ids=" + URLEncoder.encode(q, StandardCharsets.UTF_8))).GET())))) {
            Map<String, Object> project = MiniJson.obj(p);
            String slug = MiniJson.str(project.get("slug"));
            if (slug != null) {
                bySlug.put(slug.toLowerCase(), MiniJson.str(project.get("title")));
            }
        }
        for (ScanResult r : unknown) {
            String title = r.modId == null ? null : bySlug.get(r.modId.toLowerCase());
            if (title != null) {
                String text = "\"" + title + "\" gibt es auf Modrinth, aber diese Datei gehört zu keiner offiziellen "
                        + "Version - sie wurde verändert (z. B. infiziert) oder stammt aus einer inoffiziellen Quelle";
                r.origin = new Origin(Origin.Status.MODIFIED, text);
                r.add(Category.ORIGIN, MODIFIED_POINTS, text);
            }
        }
    }

    private Map<String, String> projectTitles(Set<String> ids) throws IOException, InterruptedException {
        Map<String, String> out = new HashMap<>();
        String q = "[" + ids.stream().map(MiniJson::quote).collect(Collectors.joining(",")) + "]";
        for (Object p : MiniJson.arr(MiniJson.parse(send(HttpRequest.newBuilder(
                URI.create(MODRINTH + "/v2/projects?ids=" + URLEncoder.encode(q, StandardCharsets.UTF_8))).GET())))) {
            Map<String, Object> project = MiniJson.obj(p);
            out.put(MiniJson.str(project.get("id")), MiniJson.str(project.get("title")));
        }
        return out;
    }

    private void mavenCentral(List<ScanResult> libs) {
        if (libs.isEmpty()) {
            return;
        }
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(8, libs.size()), r -> {
            Thread t = new Thread(r, "SilentNetGuard-Maven");
            t.setDaemon(true);
            return t;
        });
        try {
            List<Future<?>> jobs = new ArrayList<>();
            for (ScanResult lib : libs) {
                jobs.add(pool.submit(() -> {
                    try {
                        String json = send(HttpRequest.newBuilder(URI.create(MAVEN + "/solrsearch/select?rows=1&wt=json&q="
                                + URLEncoder.encode("1:\"" + lib.sha1 + "\"", StandardCharsets.UTF_8))).GET());
                        List<Object> docs = MiniJson.arr(MiniJson.obj(MiniJson.obj(MiniJson.parse(json)).get("response")).get("docs"));
                        if (!docs.isEmpty()) {
                            Map<String, Object> d = MiniJson.obj(docs.get(0));
                            lib.origin = new Origin(Origin.Status.KNOWN_LIBRARY, "Maven Central: " + MiniJson.str(d.get("g"))
                                    + ":" + MiniJson.str(d.get("a")) + ":" + MiniJson.str(d.get("v")));
                        }
                    } catch (IOException | RuntimeException ignored) {
                        // bleibt unbekannt
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }));
            }
            for (Future<?> j : jobs) {
                try {
                    j.get(30, TimeUnit.SECONDS);
                } catch (Exception ignored) {
                    j.cancel(true);
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private String send(HttpRequest.Builder request) throws IOException, InterruptedException {
        HttpResponse<String> res = http.send(request.timeout(TIMEOUT).header("User-Agent", USER_AGENT).build(),
                HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 404) {
            return "{}";
        }
        if (res.statusCode() / 100 != 2) {
            throw new IOException("HTTP " + res.statusCode() + " von " + res.uri());
        }
        return res.body();
    }

    private void saveCache() {
        if (cacheFile == null) {
            return;
        }
        try {
            Files.createDirectories(cacheFile.getParent());
            try (Writer w = Files.newBufferedWriter(cacheFile)) {
                cache.store(w, "SilentNet Guard - bestätigte Original-Dateien (SHA-512)");
            }
        } catch (IOException ignored) {
            // Cache ist optional
        }
    }

    private static void flatten(ScanResult r, List<ScanResult> out) {
        out.add(r);
        r.children.forEach(c -> flatten(c, out));
    }
}
