package app.revanced.extension.chmate;

import java.util.Locale;

final class IntegrationStrings {
    final String revancedSettings;
    final String advancedNg;
    final String imageBoardSources;
    final String sourceDescription;
    final String sourceHint;
    final String save;
    final String saved;
    final String invalidSource;
    final String noBbsMenuSlot;

    private IntegrationStrings(String revancedSettings, String advancedNg,
            String imageBoardSources, String sourceDescription, String sourceHint,
            String save, String saved, String invalidSource, String noBbsMenuSlot) {
        this.revancedSettings = revancedSettings;
        this.advancedNg = advancedNg;
        this.imageBoardSources = imageBoardSources;
        this.sourceDescription = sourceDescription;
        this.sourceHint = sourceHint;
        this.save = save;
        this.saved = saved;
        this.invalidSource = invalidSource;
        this.noBbsMenuSlot = noBbsMenuSlot;
    }

    static IntegrationStrings current() {
        String language = Locale.getDefault().getLanguage();
        switch (language) {
            case "ja": return of("ChMate ReVanced", "高度なNGルール", "画像掲示板の板",
                    "4chan・ふたば・jschanの板またはスレッドのHTTPS URLを1行ずつ入力し、保存します。下の「板を開く」から一覧を表示できます。ホームの「板一覧」にも追加するには、「掲示板・BBSMENU」（旧版は「掲示板設定」）で「板一覧を取得/更新」を実行してください。jschanの /板名/thread/番号.html は、登録前でもChMateの「URLを開く」から直接開けます。",
                    "https://boards.4chan.org/po/\nhttps://dat.2chan.net/t/futaba.htm\nhttps://sportschan.org/librejp/",
                    "保存", "保存しました", "対応するHTTPS板URLではありません: ",
                    "BBSMENU URLが5件すべて使用中です。空きを1件作ってください。");
            case "zh": return of("ChMate ReVanced 设置", "高级屏蔽规则", "图片板块",
                    "每行输入一个4chan、Futaba或jschan板块或主题的HTTPS网址并保存。使用下方按钮打开板块；更新ChMate板块列表可将其添加到列表。jschan的 /板块/thread/编号.html 可直接通过ChMate的打开网址功能打开。",
                    "每行一个 HTTPS URL", "保存", "已保存", "不支持的板块URL: ", "请先空出一个BBSMENU URL栏位。");
            case "hi": return of("ChMate ReVanced सेटिंग", "उन्नत NG नियम", "इमेजबोर्ड बोर्ड",
                    "हर पंक्ति में 4chan, Futaba या jschan बोर्ड या थ्रेड का HTTPS URL डालकर सहेजें। नीचे के बटन से बोर्ड खोलें; बोर्ड सूची में जोड़ने के लिए ChMate की सूची अपडेट करें। jschan /board/thread/id.html को ChMate में सीधे URL से खोल सकते हैं।",
                    "हर पंक्ति में एक HTTPS URL", "सहेजें", "सहेजा गया", "असमर्थित बोर्ड URL: ", "एक BBSMENU URL स्लॉट खाली करें।");
            case "es": return of("Ajustes de ChMate ReVanced", "Reglas NG avanzadas", "Tableros de imágenes",
                    "Guarda una URL HTTPS de tablero o hilo de 4chan, Futaba o jschan por línea. Abre el tablero con el botón inferior o actualiza la lista de ChMate. Los hilos jschan /board/thread/id.html también se abren directamente por URL.",
                    "Una URL HTTPS por línea", "Guardar", "Guardado", "URL de tablero no compatible: ", "Libera una ranura de URL BBSMENU.");
            case "fr": return of("Paramètres ChMate ReVanced", "Règles NG avancées", "Forums d’images",
                    "Enregistrez une URL HTTPS de forum ou de fil 4chan, Futaba ou jschan par ligne. Ouvrez le forum avec le bouton ci-dessous ou actualisez la liste ChMate. Les fils jschan /board/thread/id.html s’ouvrent aussi directement par URL.",
                    "Une URL HTTPS par ligne", "Enregistrer", "Enregistré", "URL non prise en charge : ", "Libérez un emplacement d’URL BBSMENU.");
            case "ar": return of("إعدادات ChMate ReVanced", "قواعد حجب متقدمة", "لوحات الصور",
                    "احفظ رابط HTTPS للوحة أو موضوع 4chan أو Futaba أو jschan في كل سطر. افتح اللوحة بالزر أدناه أو حدّث قائمة ChMate. يمكن فتح موضوع jschan بصيغة /board/thread/id.html مباشرة من خيار فتح الرابط.",
                    "رابط HTTPS واحد في كل سطر", "حفظ", "تم الحفظ", "رابط لوحة غير مدعوم: ", "أفرغ خانة واحدة من روابط BBSMENU.");
            case "pt": return of("Configurações do ChMate ReVanced", "Regras NG avançadas", "Fóruns de imagens",
                    "Salve uma URL HTTPS de fórum ou tópico de 4chan, Futaba ou jschan por linha. Abra o fórum pelo botão abaixo ou atualize a lista do ChMate. Tópicos jschan /board/thread/id.html também abrem diretamente por URL.",
                    "Uma URL HTTPS por linha", "Salvar", "Salvo", "URL não compatível: ", "Libere um campo de URL BBSMENU.");
            case "bn": return of("ChMate ReVanced সেটিংস", "উন্নত NG নিয়ম", "ইমেজবোর্ড বোর্ড",
                    "প্রতি লাইনে 4chan, Futaba বা jschan বোর্ড বা থ্রেডের HTTPS URL লিখে সংরক্ষণ করুন। নিচের বোতামে বোর্ড খুলুন; তালিকায় যোগ করতে ChMate বোর্ড তালিকা হালনাগাদ করুন। jschan /board/thread/id.html সরাসরি URL দিয়েও খোলা যায়।",
                    "প্রতি লাইনে একটি HTTPS URL", "সংরক্ষণ", "সংরক্ষিত", "অসমর্থিত বোর্ড URL: ", "একটি BBSMENU URL ঘর খালি করুন।");
            case "ru": return of("Настройки ChMate ReVanced", "Расширенные правила NG", "Имиджборды",
                    "Сохраните по одному HTTPS URL доски или треда 4chan, Futaba или jschan в строке. Откройте доску кнопкой ниже или обновите список ChMate. Треды jschan /board/thread/id.html можно открыть напрямую по URL.",
                    "Один HTTPS URL в строке", "Сохранить", "Сохранено", "Неподдерживаемый URL: ", "Освободите одно поле URL BBSMENU.");
            case "ur": return of("ChMate ReVanced ترتیبات", "اعلیٰ NG قواعد", "امیج بورڈز",
                    "ہر سطر میں 4chan، Futaba یا jschan بورڈ یا تھریڈ کا HTTPS URL درج کرکے محفوظ کریں۔ نیچے کے بٹن سے بورڈ کھولیں؛ فہرست میں شامل کرنے کے لیے ChMate کی بورڈ فہرست اپ ڈیٹ کریں۔ jschan /board/thread/id.html براہ راست URL سے بھی کھلتا ہے۔",
                    "ہر سطر میں ایک HTTPS URL", "محفوظ کریں", "محفوظ ہوگیا", "غیر معاون بورڈ URL: ", "ایک BBSMENU URL خانہ خالی کریں۔");
            default: return of("ChMate ReVanced", "Advanced NG rules", "Imageboard boards",
                    "Save one HTTPS board or thread URL for 4chan, Futaba, or jschan per line. Open a board with the buttons below; refresh ChMate's board list to add it there. Jschan /board/thread/id.html links also open directly through ChMate's Open URL command.",
                    "One HTTPS URL per line", "Save", "Saved", "Unsupported board URL: ", "Free one BBSMENU URL slot first.");
        }
    }

    String openBoard() {
        switch (Locale.getDefault().getLanguage()) {
            case "ja": return "板を開く";
            case "zh": return "打开板块";
            case "hi": return "बोर्ड खोलें";
            case "es": return "Abrir tablero";
            case "fr": return "Ouvrir le forum";
            case "ar": return "فتح اللوحة";
            case "pt": return "Abrir fórum";
            case "bn": return "বোর্ড খুলুন";
            case "ru": return "Открыть доску";
            case "ur": return "بورڈ کھولیں";
            default: return "Open board";
        }
    }

    private static IntegrationStrings of(String a, String b, String c, String d, String e,
            String f, String g, String h, String i) {
        return new IntegrationStrings(a, b, c, d, e, f, g, h, i);
    }
}
