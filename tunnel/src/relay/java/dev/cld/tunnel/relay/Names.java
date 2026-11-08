package dev.cld.tunnel.relay;

// Имена миров для адреса: два слова латиницей и число, например tihiy-les-42.
// Имя берётся из хэша id мира, поэтому у мира оно всегда одно и то же.
final class Names {
    private static final String[] A = {
        "tihiy", "bystry", "zeleny", "sinij", "bely", "chorny", "zolotoj", "serebry",
        "dikij", "dobry", "hrabry", "veseliy", "dalniy", "vysoky", "gluboky", "tyoply",
        "holodny", "lesnoj", "gorny", "morskoj", "rechnoj", "zvyozdny", "lunny", "solnechny",
        "staryj", "novy", "yasny", "tumanny", "snezhny", "ognenny", "ryzhiy", "sedoj",
        "medny", "zhelezny", "kamenny", "derevyanny", "hitry", "mudry", "smely", "shustry",
        "sonny", "bodry", "redkiy", "vernyj", "gromkiy", "myagkiy", "krepkiy", "legkiy",
        "pestry", "rozovy", "lilovy", "alyj", "izumrud", "yantarny", "vetreny", "dozhdlivy",
        "osenniy", "vesenniy", "letniy", "zimniy", "polyarny", "stepnoj", "pustynny", "bolotny"
    };
    private static final String[] B = {
        "les", "bor", "dub", "klen", "kedr", "elka", "bereza", "ivushka",
        "rechka", "ozero", "more", "zaliv", "ostrov", "gora", "holm", "utes",
        "lis", "volk", "medved", "zayac", "yozh", "barsuk", "los", "olen",
        "sokol", "orel", "filin", "voron", "drozd", "chizh", "snegir", "aist",
        "kit", "delfin", "som", "shchuka", "karas", "rak", "krab", "tyulen",
        "kot", "pyos", "kon", "byk", "kozel", "baran", "gus", "petuh",
        "grib", "yagoda", "kamen", "almaz", "rubin", "kristall", "fakel", "fonar",
        "zamok", "bashnya", "most", "melnica", "kuznica", "shahta", "kolodec", "mayak"
    };

    private Names() {}

    // attempt больше нуля, если имя уже занято другим миром: берём следующее.
    static String of(byte[] hash, int attempt) {
        int a = (hash[0] & 0xFF) % A.length;
        int b = (hash[1] & 0xFF) % B.length;
        int n = ((hash[2] & 0xFF) << 8 | (hash[3] & 0xFF)) % 90 + 10;
        if (attempt > 0) n = (n + attempt * 7) % 90 + 10;
        String s = A[a] + "-" + B[b] + "-" + n;
        if (attempt >= 90) s = s + "-" + attempt;
        return s;
    }
}
