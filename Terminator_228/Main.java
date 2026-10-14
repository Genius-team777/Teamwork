import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/*
 * Клуб знакомств молодых книголюбов (упрощённая версия)
 *
 * В проекте 4 простых паттерна:
 *   1. Singleton — ClubDatabase: одна общая база пользователей
 *   2. Factory   — NotifierFactory: создаёт нужный способ уведомления
 *   3. Strategy  — MatchStrategy: разные способы подбора пары
 *   4. Observer  — NewsBoard: рассылка новостей клуба подписчикам
 */

// ======================= ГЛАВНЫЙ КЛАСС ========================

public class Main {
    public static void main(String[] args) {
        ClubDatabase db = ClubDatabase.getInstance();

        // --- Создаём пользователей ---
        User rustem = new User("Rustem", 19, "Талгар", "telegram", "@rustem_reads");
        rustem.addGenre("фэнтези");
        rustem.addGenre("классика");
        rustem.addGenre("поэзия");

        User erasyl = new User("Erasyl", 21, "Кайназар", "email", "erasyl@mail.kz");
        erasyl.addGenre("фэнтези");
        erasyl.addGenre("фантастика");
        erasyl.addGenre("классика");

        User marlen = new User("Marlen", 19, "Чималган", "sms", "+77010000000");
        marlen.addGenre("детектив");
        marlen.addGenre("романтика");

        User bakytzhan = new User("Bakytzhan", 19, "Каскелен", "email", "bakytzhan@mail.kz");
        bakytzhan.addGenre("фантастика");
        bakytzhan.addGenre("классика");



        // --- 1. Singleton: регистрация в общей базе ---
        System.out.println("=== 1. Регистрация (Singleton) ===");
        db.register(rustem);
        db.register(erasyl);
        db.register(marlen);
        db.register(bakytzhan);// не пройдёт: возраст больше 30
        System.out.println("Всего участников: " + db.getUsers().size());

        ClubDatabase sameDb = ClubDatabase.getInstance();
        System.out.println("Это та же самая база? " + (db == sameDb));

        // --- 2. Strategy: подбор пары разными способами ---
        System.out.println();
        System.out.println("=== 2. Подбор пары для Rustem (Strategy) ===");
        Matchmaker matchmaker = new Matchmaker(new GenreMatchStrategy());
        matchmaker.printMatches(rustem, db.getUsers());

        matchmaker.setStrategy(new CityAgeMatchStrategy());
        matchmaker.printMatches(rustem, db.getUsers());

        // --- 3. Factory: уведомления через разные каналы ---
        System.out.println();
        System.out.println("=== 3. Уведомления о знакомстве (Factory) ===");
        User best = matchmaker.findBest(rustem, db.getUsers());
        if (best != null) {
            Notifier n1 = NotifierFactory.create(rustem.getChannel());
            n1.send(rustem, "Познакомьтесь с " + best.getName() + "!");

            Notifier n2 = NotifierFactory.create(best.getChannel());
            n2.send(best, "Познакомьтесь с " + rustem.getName() + "!");
        }

        // --- 4. Observer: новости клуба ---
        System.out.println();
        System.out.println("=== 4. Новости клуба (Observer) ===");
        NewsBoard board = new NewsBoard();
        for (User u : db.getUsers()) {
            board.subscribe(u);
        }
        board.publish("Встреча клуба: обсуждаем «Мастер и Маргарита», суббота 18:00");

        board.unsubscribe(marlen);
        board.publish("Новая книга месяца: «Маленький принц»");
    }
}

// ======================= МОДЕЛЬ ================================

class User {
    private String name;
    private int age;
    private String city;
    private String channel;   // "email", "telegram" или "sms"
    private String contact;
    private ArrayList<String> genres = new ArrayList<>();

    public User(String name, int age, String city, String channel, String contact) {
        this.name = name;
        this.age = age;
        this.city = city;
        this.channel = channel;
        this.contact = contact;
    }

    public void addGenre(String genre) {
        genres.add(genre);
    }

    public String getName()              { return name; }
    public int getAge()                  { return age; }
    public String getCity()              { return city; }
    public String getChannel()           { return channel; }
    public String getContact()           { return contact; }
    public ArrayList<String> getGenres() { return genres; }

    // Observer: пользователь получает новость
    public void receiveNews(String news) {
        Notifier notifier = NotifierFactory.create(channel);
        notifier.send(this, news);
    }

    @Override
    public String toString() {
        return name + ", " + age + ", " + city;
    }
}

// ======================= 1. SINGLETON ==========================

// Единая база участников в Firebase Realtime Database.
// Работает через REST API (никаких библиотек, нужна только Java 11+).
// Консольная программа (Main) и окно администратора (AdminGUI) подключаются
// к одной и той же облачной базе, поэтому видят одни и те же данные.
//
// Структура в Firebase:
//   /users/<ключ-из-контакта> = { name, age, city, channel, contact, genres, ts }
class ClubDatabase {
    private static final int MIN_AGE = 14;
    private static final int MAX_AGE = 30;
    private static final List<String> CHANNELS = Arrays.asList("email", "telegram", "sms");

    // >>> ВСТАВЬТЕ АДРЕС СВОЕЙ БАЗЫ (Firebase Console -> Realtime Database) <<<
    // Либо задайте переменную окружения FIREBASE_DB_URL.
    private static final String DEFAULT_URL = "https://bookbase-9941d-default-rtdb.europe-west1.firebasedatabase.app";
    private static final String DB_URL = envOr("FIREBASE_DB_URL", DEFAULT_URL);
    // Firebase Authentication: задайте эти значения в переменных окружения.
    // Не храните email/пароль пользователя прямо в исходном коде.
    private static final String FIREBASE_API_KEY = envOr("FIREBASE_API_KEY", "AIzaSyDj050pztdUH5VNdc5qlSzR3Xl3UaHIDGU");
    private static final String FIREBASE_EMAIL = envOr("FIREBASE_EMAIL", "");
    private static final String FIREBASE_PASSWORD = envOr("FIREBASE_PASSWORD", "");
    private static final String AUTH_SIGN_IN_URL =
            "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=";
    private static final String AUTH_REFRESH_URL =
            "https://securetoken.googleapis.com/v1/token?key=";
    private static final long POLL_MS = 2000;   // как часто проверять изменения в облаке

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final ArrayList<User> users = new ArrayList<>();
    private final Map<String, Long> created = new HashMap<>();   // контакт -> время создания (для порядка)
    private volatile boolean changed = false;    // есть ли новые данные для GUI
    private volatile boolean online = true;      // чтобы не спамить сообщениями об ошибке
    private volatile int writeVersion = 0;       // защита от устаревших ответов опроса
    private String lastSnapshot = null;

    // Краткоживущий ID-токен и refresh-токен Firebase Authentication.
    // Токены хранятся только в памяти и не записываются в файл.
    private volatile String idToken;
    private volatile String refreshToken;
    private volatile long tokenExpiresAt;

    // Защита Singleton от повторного создания через Reflection.
    private static final Object INSTANCE_LOCK = new Object();
    private static boolean instanceCreated = false;

    // Закрытый конструктор: загружаем участников и запускаем фоновое слежение за облаком
    private ClubDatabase() {
        synchronized (INSTANCE_LOCK) {
            if (instanceCreated) {
                throw new IllegalStateException(
                        "Экземпляр ClubDatabase уже создан. Используйте getInstance().");
            }
            instanceCreated = true;
        }

        if (DB_URL.contains("YOUR-PROJECT")) {
            System.out.println("ВНИМАНИЕ: не указан адрес Firebase. Задайте DEFAULT_URL в ClubDatabase "
                    + "или переменную окружения FIREBASE_DB_URL.");
        }
        pull();
        Thread poller = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(POLL_MS);
                } catch (InterruptedException e) {
                    return;
                }
                pull();
            }
        }, "firebase-poller");
        poller.setDaemon(true);   // не мешает завершению консольной программы
        poller.start();
    }

    // Holder: объект создаётся при первом getInstance(), потокобезопасно
    private static class Holder {
        private static final ClubDatabase INSTANCE = new ClubDatabase();
    }

    public static ClubDatabase getInstance() {
        return Holder.INSTANCE;
    }

    public static String getDatabaseUrl() {
        return DB_URL;
    }

    // ---------- Проверка данных ----------

    // Возвращает причину отказа или null, если данные в порядке
    public static String validationError(User u) {
        if (u == null || u.getName() == null || u.getName().trim().isEmpty()) {
            return "пустое имя";
        }
        if (u.getAge() < MIN_AGE || u.getAge() > MAX_AGE) {
            return "клуб для молодёжи от " + MIN_AGE + " до " + MAX_AGE + " лет";
        }
        if (!CHANNELS.contains(u.getChannel())) {
            return "неизвестный канал связи «" + u.getChannel() + "»";
        }
        if (u.getContact() == null || u.getContact().trim().isEmpty()) {
            return "не указан контакт";
        }
        return null;
    }

    public static List<String> getChannels() {
        return CHANNELS;
    }

    // ---------- Регистрация, изменение, удаление ----------

    public synchronized boolean register(User user) {
        String error = validationError(user);
        if (error != null) {
            System.out.println("Отказ: " + (user == null ? "?" : user.getName()) + " — " + error);
            return false;
        }
        pull();   // свежие данные из облака
        int i = indexOfContact(user.getContact());
        if (i >= 0) {
            if (users.get(i).getName().equals(user.getName())) {
                // тот же человек уже есть в базе: обновляем запись
                long ts = created.get(user.getContact());
                if (!put(user, ts)) {
                    return false;
                }
                users.set(i, user);
                System.out.println("Уже в базе (данные обновлены): " + user);
                return true;
            }
            System.out.println("Отказ: " + user.getName() + " — контакт "
                    + user.getContact() + " уже зарегистрирован");
            return false;
        }
        long ts = System.currentTimeMillis();
        if (!put(user, ts)) {
            return false;
        }
        users.add(user);
        created.put(user.getContact(), ts);
        System.out.println("Зарегистрирован: " + user);
        return true;
    }

    // Замена записи обновлённой (для редактирования)
    public synchronized boolean replaceUser(User old, User updated) {
        if (old == null || validationError(updated) != null) {
            return false;
        }
        pull();
        int i = indexOfContact(old.getContact());
        if (i < 0) {
            return false;
        }
        int other = indexOfContact(updated.getContact());
        if (other >= 0 && other != i) {
            return false;   // новый контакт уже занят другим
        }
        long ts = created.get(old.getContact());
        if (!put(updated, ts)) {
            return false;
        }
        if (!old.getContact().equals(updated.getContact())) {
            // контакт изменился -> это новый ключ в Firebase, старый удаляем
            if (!delete(old.getContact())) {
                return false;
            }
            created.remove(old.getContact());
            created.put(updated.getContact(), ts);
        }
        users.set(i, updated);
        return true;
    }

    public synchronized boolean remove(String contact) {
        pull();
        int i = indexOfContact(contact);
        if (i < 0) {
            return false;
        }
        if (!delete(contact)) {
            return false;
        }
        User u = users.remove(i);
        created.remove(contact);
        System.out.println("Удалён из базы: " + u.getName());
        return true;
    }

    public synchronized void clearUsers() {
        try {
            request("DELETE", "users.json", null);
            writeVersion++;
            users.clear();
            created.clear();
        } catch (Exception e) {
            reportError(e);
        }
    }

    // ---------- Поиск ----------

    public synchronized User findByContact(String contact) {
        int i = indexOfContact(contact);
        return i < 0 ? null : users.get(i);
    }

    public synchronized User findByName(String name) {
        for (User u : users) {
            if (u.getName().equalsIgnoreCase(name)) {
                return u;
            }
        }
        return null;
    }

    public synchronized ArrayList<User> findByCity(String city) {
        ArrayList<User> result = new ArrayList<>();
        for (User u : users) {
            if (u.getCity().equalsIgnoreCase(city)) {
                result.add(u);
            }
        }
        return result;
    }

    public synchronized ArrayList<User> findByGenre(String genre) {
        ArrayList<User> result = new ArrayList<>();
        for (User u : users) {
            if (u.getGenres().contains(genre)) {
                result.add(u);
            }
        }
        return result;
    }

    // ---------- Доступ к данным ----------

    // Копия списка: снаружи базу не испортить
    public synchronized ArrayList<User> getUsers() {
        return new ArrayList<>(users);
    }

    public synchronized int count() {
        return users.size();
    }

    // true, если с прошлого вызова в облаке появились новые данные (сеть не используется)
    public boolean reloadIfChanged() {
        boolean c = changed;
        changed = false;
        return c;
    }

    // ---------- Работа с Firebase ----------

    private int indexOfContact(String contact) {
        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).getContact().equals(contact)) {
                return i;
            }
        }
        return -1;
    }

    // Ключ в Firebase не может содержать . $ # [ ] /  — кодируем контакт в Base64
    private static String key(String contact) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(contact.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean sameData(User a, User b) {
        return a.getName().equals(b.getName()) && a.getAge() == b.getAge()
                && a.getCity().equals(b.getCity()) && a.getChannel().equals(b.getChannel())
                && a.getGenres().equals(b.getGenres());
    }

    private static String envOr(String name, String fallback) {
        String v = System.getenv(name);
        return (v == null || v.trim().isEmpty()) ? fallback : v.trim();
    }

    // Получает ID-токен через Firebase Authentication и обновляет его по refresh-токену.
    private synchronized String getValidIdToken() throws IOException, InterruptedException {
        if (FIREBASE_API_KEY.isEmpty() || FIREBASE_EMAIL.isEmpty() || FIREBASE_PASSWORD.isEmpty()) {
            throw new IOException("Не настроена Firebase Authentication. Задайте FIREBASE_API_KEY, "
                    + "FIREBASE_EMAIL и FIREBASE_PASSWORD в переменных окружения.");
        }

        // Обновляем токен заранее, за минуту до истечения срока действия.
        if (idToken != null && System.currentTimeMillis() < tokenExpiresAt - 60_000L) {
            return idToken;
        }

        if (refreshToken != null) {
            try {
                String form = "grant_type=refresh_token&refresh_token="
                        + URLEncoder.encode(refreshToken, StandardCharsets.UTF_8);
                HttpRequest req = HttpRequest.newBuilder(URI.create(
                                AUTH_REFRESH_URL + URLEncoder.encode(FIREBASE_API_KEY, StandardCharsets.UTF_8)))
                        .timeout(Duration.ofSeconds(8))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8))
                        .build();
                HttpResponse<String> resp = http.send(req,
                        HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (resp.statusCode() / 100 == 2) {
                    Map<?, ?> data = (Map<?, ?>) MiniJson.parse(resp.body());
                    idToken = String.valueOf(data.get("access_token"));
                    refreshToken = String.valueOf(data.get("refresh_token"));
                    long expiresIn = Long.parseLong(String.valueOf(data.get("expires_in")));
                    tokenExpiresAt = System.currentTimeMillis() + expiresIn * 1000L;
                    return idToken;
                }
                // Если refresh-токен отозван, ниже выполняется новый вход по email/паролю.
                refreshToken = null;
                idToken = null;
            } catch (RuntimeException ex) {
                refreshToken = null;
                idToken = null;
            }
        }

        String payload = "{\"email\":" + esc(FIREBASE_EMAIL)
                + ",\"password\":" + esc(FIREBASE_PASSWORD)
                + ",\"returnSecureToken\":true}";
        HttpRequest req = HttpRequest.newBuilder(URI.create(
                        AUTH_SIGN_IN_URL + URLEncoder.encode(FIREBASE_API_KEY, StandardCharsets.UTF_8)))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = http.send(req,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("Firebase Authentication: вход не выполнен (HTTP "
                    + resp.statusCode() + "). Проверьте email, пароль и настройки Email/Password.");
        }
        Map<?, ?> data = (Map<?, ?>) MiniJson.parse(resp.body());
        idToken = String.valueOf(data.get("idToken"));
        refreshToken = String.valueOf(data.get("refreshToken"));
        long expiresIn = Long.parseLong(String.valueOf(data.get("expiresIn")));
        tokenExpiresAt = System.currentTimeMillis() + expiresIn * 1000L;
        return idToken;
    }

    private String request(String method, String path, String json) throws IOException, InterruptedException {
        String base = DB_URL.endsWith("/") ? DB_URL.substring(0, DB_URL.length() - 1) : DB_URL;
        String token = getValidIdToken();
        String url = base + "/" + path + "?auth="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);
        HttpRequest.BodyPublisher body = json == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json; charset=UTF-8")
                .method(method, body)
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() / 100 != 2) {
            // Не выводим URL или токен в сообщение об ошибке.
            throw new IOException("Firebase Realtime Database вернула HTTP " + resp.statusCode()
                    + ". Проверьте правила доступа и права пользователя.");
        }
        return resp.body();
    }

    private void reportError(Exception e) {
        System.out.println("Ошибка связи с Firebase: "
                + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
    }

    private static String esc(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    private static String toJson(User u, long ts) {
        return "{\"name\":" + esc(u.getName())
                + ",\"age\":" + u.getAge()
                + ",\"city\":" + esc(u.getCity())
                + ",\"channel\":" + esc(u.getChannel())
                + ",\"contact\":" + esc(u.getContact())
                + ",\"genres\":" + esc(String.join(",", u.getGenres()))
                + ",\"ts\":" + ts + "}";
    }

    // Запись одного участника (PUT /users/<ключ>.json)
    private boolean put(User u, long ts) {
        try {
            request("PUT", "users/" + key(u.getContact()) + ".json", toJson(u, ts));
            writeVersion++;
            return true;
        } catch (Exception e) {
            reportError(e);
            return false;
        }
    }

    private boolean delete(String contact) {
        try {
            request("DELETE", "users/" + key(contact) + ".json", null);
            writeVersion++;
            return true;
        } catch (Exception e) {
            reportError(e);
            return false;
        }
    }

    // Скачивает всех участников из облака и обновляет локальную копию.
    // Сеть вызывается БЕЗ блокировки, чтобы окно не зависало.
    private boolean pull() {
        int version = writeVersion;
        String body;
        try {
            body = request("GET", "users.json", null).trim();
            if (!online) {
                online = true;
                System.out.println("Связь с Firebase восстановлена");
            }
        } catch (Exception e) {
            if (online) {
                online = false;
                reportError(e);
            }
            return false;
        }

        synchronized (this) {
            if (version != writeVersion || body.equals(lastSnapshot)) {
                return true;   // за время запроса были наши записи / ничего нового
            }
            try {
                ArrayList<User> loaded = new ArrayList<>();
                Map<String, Long> loadedTs = new HashMap<>();
                // уже известные объекты: если данные не изменились, оставляем тот же объект,
                // чтобы у остального кода (например, проверка other != user) ничего не ломалось
                Map<String, User> known = new HashMap<>();
                for (User old : users) {
                    known.put(old.getContact(), old);
                }
                Object root = MiniJson.parse(body);
                if (root instanceof Map) {
                    for (Object val : ((Map<?, ?>) root).values()) {
                        if (!(val instanceof Map)) continue;
                        Map<?, ?> m = (Map<?, ?>) val;
                        User u = new User(
                                String.valueOf(m.get("name")),
                                ((Number) m.get("age")).intValue(),
                                String.valueOf(m.get("city")),
                                String.valueOf(m.get("channel")),
                                String.valueOf(m.get("contact")));
                        Object g = m.get("genres");
                        if (g != null) {
                            for (String x : String.valueOf(g).split(",")) {
                                if (!x.trim().isEmpty()) u.addGenre(x.trim());
                            }
                        }
                        User prev = known.get(u.getContact());
                        if (prev != null && sameData(prev, u)) {
                            u = prev;
                        }
                        loaded.add(u);
                        Object t = m.get("ts");
                        loadedTs.put(u.getContact(), t instanceof Number ? ((Number) t).longValue() : 0L);
                    }
                }
                // Firebase отдаёт записи в порядке ключей — возвращаем порядок регистрации
                loaded.sort(Comparator.comparingLong(u -> loadedTs.get(u.getContact())));
                users.clear();
                users.addAll(loaded);
                created.clear();
                created.putAll(loadedTs);
                lastSnapshot = body;
                changed = true;
                return true;
            } catch (RuntimeException e) {
                System.out.println("Не удалось разобрать данные Firebase: " + e);
                return false;
            }
        }
    }
}

// Минимальный разбор JSON (чтобы не подключать сторонние библиотеки)
final class MiniJson {
    private final String s;
    private int i = 0;

    private MiniJson(String s) { this.s = s; }

    static Object parse(String text) {
        return new MiniJson(text).value();
    }

    private void ws() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private Object value() {
        ws();
        char c = s.charAt(i);
        if (c == '{') return object();
        if (c == '[') return array();
        if (c == '"') return string();
        if (s.startsWith("null", i))  { i += 4; return null; }
        if (s.startsWith("true", i))  { i += 4; return Boolean.TRUE; }
        if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
        int start = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        return Double.valueOf(s.substring(start, i));
    }

    private Map<String, Object> object() {
        Map<String, Object> map = new LinkedHashMap<>();
        i++;   // {
        ws();
        if (s.charAt(i) == '}') { i++; return map; }
        while (true) {
            ws();
            String k = string();
            ws();
            i++;   // :
            map.put(k, value());
            ws();
            char c = s.charAt(i++);
            if (c == '}') return map;
        }
    }

    private List<Object> array() {
        List<Object> list = new ArrayList<>();
        i++;   // [
        ws();
        if (s.charAt(i) == ']') { i++; return list; }
        while (true) {
            list.add(value());
            ws();
            char c = s.charAt(i++);
            if (c == ']') return list;
        }
    }

    private String string() {
        StringBuilder sb = new StringBuilder();
        i++;   // открывающая кавычка
        while (true) {
            char c = s.charAt(i++);
            if (c == '"') return sb.toString();
            if (c == '\\') {
                char e = s.charAt(i++);
                switch (e) {
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'u':
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                        break;
                    default:  sb.append(e);   // \" \\ \/
                }
            } else {
                sb.append(c);
            }
        }
    }
}

// ======================= 2. FACTORY ============================

interface Notifier {
    void send(User to, String message);
}

class EmailNotifier implements Notifier {
    public void send(User to, String message) {
        System.out.println("  [Email " + to.getContact() + "] " + message);
    }
}

class TelegramNotifier implements Notifier {
    public void send(User to, String message) {
        System.out.println("  [Telegram " + to.getContact() + "] " + message);
    }
}

class SmsNotifier implements Notifier {
    public void send(User to, String message) {
        System.out.println("  [SMS " + to.getContact() + "] " + message);
    }
}

class NotifierFactory {
    public static Notifier create(String channel) {
        if (channel.equals("telegram")) {
            return new TelegramNotifier();
        } else if (channel.equals("sms")) {
            return new SmsNotifier();
        } else {
            return new EmailNotifier();
        }
    }
}

// ======================= 3. STRATEGY ===========================

interface MatchStrategy {
    int score(User a, User b);   // сколько баллов совместимости
    String getName();
}

// Совместимость по общим жанрам: 10 баллов за каждый общий жанр
class GenreMatchStrategy implements MatchStrategy {
    public int score(User a, User b) {
        int points = 0;
        for (String genre : a.getGenres()) {
            if (b.getGenres().contains(genre)) {
                points += 10;
            }
        }
        return points;
    }

    public String getName() { return "по жанрам"; }
}

// Совместимость по городу и возрасту
class CityAgeMatchStrategy implements MatchStrategy {
    public int score(User a, User b) {
        int points = 0;
        if (a.getCity().equals(b.getCity())) {
            points += 20;
        }
        int ageDiff = Math.abs(a.getAge() - b.getAge());
        if (ageDiff <= 2) {
            points += 10;
        } else if (ageDiff <= 5) {
            points += 5;
        }
        return points;
    }

    public String getName() { return "по городу и возрасту"; }
}

// Класс, который использует стратегию
class Matchmaker {
    private MatchStrategy strategy;

    public Matchmaker(MatchStrategy strategy) {
        this.strategy = strategy;
    }

    public void setStrategy(MatchStrategy strategy) {
        this.strategy = strategy;
    }

    public void printMatches(User user, ArrayList<User> all) {
        System.out.println("Подбор " + strategy.getName() + ":");
        for (User other : all) {
            if (other != user) {
                int points = strategy.score(user, other);
                System.out.println("  " + other.getName() + " — " + points + " баллов");
            }
        }
    }

    public User findBest(User user, ArrayList<User> all) {
        User best = null;
        int bestScore = 0;
        for (User other : all) {
            if (other != user) {
                int points = strategy.score(user, other);
                if (points > bestScore) {
                    bestScore = points;
                    best = other;
                }
            }
        }
        return best;
    }
}

// ======================= 4. OBSERVER ===========================

class NewsBoard {
    private ArrayList<User> subscribers = new ArrayList<>();

    public void subscribe(User user) {
        subscribers.add(user);
    }

    public void unsubscribe(User user) {
        subscribers.remove(user);
        System.out.println(user.getName() + " отписался(ась) от новостей");
    }

    public void publish(String news) {
        System.out.println("Новость: " + news);
        for (User user : subscribers) {
            user.receiveNews(news);
        }
    }
}