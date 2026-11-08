package dev.cld.tunnel.relay;

import dev.cld.tunnel.core.Proto;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

// Запуск: java -jar cld_tunnel-relay.jar [relay.properties]
// Если файла нет, создаём пример с новым ключом и выходим.
public final class RelayMain {
    private RelayMain() {}

    public static void main(String[] args) throws Exception {
        // на сервере часто нет локали, а лог по-русски
        System.setOut(new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), true, StandardCharsets.UTF_8));
        if (args.length > 0 && args[0].equals("--key")) {
            System.out.println(Proto.randomHex(24));
            return;
        }
        Path file = Path.of(args.length > 0 ? args[0] : "relay.properties");
        if (!Files.exists(file)) {
            Files.writeString(file, sample(Proto.randomHex(24)), StandardCharsets.UTF_8);
            System.out.println("Создал " + file.toAbsolutePath() + ", впишите domain или address и запустите снова.");
            return;
        }
        RelayConfig cfg = RelayConfig.load(file);
        Relay relay = new Relay(cfg);
        relay.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Relay.log("relay остановлен");
            relay.stop();
        }));
        Thread.currentThread().join();
    }

    static String sample(String key) {
        return """
            # Relay cld_tunnel: открытые одиночные миры доступны друзьям по интернету.
            # Порт для модов (хосты подключаются сюда), открыть в файрволе
            control=0.0.0.0:25580
            # Общий порт для игроков. Мир выбирается по адресу имя.domain, нужна DNS запись *.domain на этот сервер
            public=0.0.0.0:25565
            # Домен для адресов миров, например mc.example.ru. Пусто: адресов по имени нет
            domain=
            # Без домена: у каждого мира свой порт на этом IP или имени, например 81.2.3.4
            address=
            ports=25601-25650
            # Ключ: такой же должен стоять в настройках мода. Пусто: открыть мир может любой
            key=%s
            maxHosts=50
            maxPlayersPerHost=20
            state=relay-state.txt
            """.formatted(key);
    }
}
